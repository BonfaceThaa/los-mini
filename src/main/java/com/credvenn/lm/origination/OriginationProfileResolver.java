package com.credvenn.lm.origination;

import com.credvenn.lm.common.exception.BadRequestException;
import com.credvenn.lm.common.exception.ConflictException;
import com.credvenn.lm.common.exception.ForbiddenOperationException;
import com.credvenn.lm.common.exception.NotFoundException;
import com.credvenn.lm.tenant.TenantRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Internal creation collaborator; tenant ID comes from the authenticated application service caller. */
@Service
@RequiredArgsConstructor
public class OriginationProfileResolver {
    private final TenantRepository tenants;
    private final OriginationProfileRepository profiles;
    private final OriginationProfileValidator validator;
    private final ObjectMapper json;

    @Transactional(propagation = Propagation.MANDATORY)
    public String resolveForApplication(String tenantId, String requestedCode) {
        if (tenantId == null || tenantId.isBlank())
            throw new ForbiddenOperationException("A tenant user is required to create an application");
        // Profile CRUD takes this same lock first; hold it through application persistence.
        var tenant = tenants.findForOriginationUpdate(tenantId)
                .orElseThrow(() -> new NotFoundException("Tenant not found"));
        OriginationProfile profile;
        if (requestedCode != null) {
            String code = validator.normalizeCode(requestedCode);
            profile = profiles.findForApplicationByTenantIdAndCodeIgnoreCase(tenantId, code)
                    .orElseThrow(() -> new NotFoundException("Origination profile not found"));
        } else {
            String defaultId = tenant.getDefaultOriginationProfileId();
            if (defaultId == null)
                throw new BadRequestException("Provide originationProfileCode or configure an active tenant default origination profile");
            profile = profiles.findForApplicationByTenantIdAndId(tenantId, defaultId)
                    .orElseThrow(() -> new ConflictException("Tenant default origination profile is unavailable"));
        }
        if (!profile.isActive()) throw new BadRequestException("Origination profile is inactive");
        // Until workflow execution supports other journeys, reject unsupported stored configurations too.
        try {
            Map<OriginationProfileDtos.Stage, List<OriginationProfileDtos.Requirement>> requirements =
                    json.readValue(profile.getRequirementsJson(), new TypeReference<>() {});
            var configuration = profile.getConfigurationJson() == null ? null
                    : json.readValue(profile.getConfigurationJson(), OriginationProfileDtos.Configuration.class);
            validator.validate(requirements, configuration, true);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new ConflictException("Origination profile configuration is invalid");
        }
        return profile.getId();
    }
}
