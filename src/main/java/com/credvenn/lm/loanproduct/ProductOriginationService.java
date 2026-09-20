package com.credvenn.lm.loanproduct;

import com.credvenn.lm.application.LoanRequestApplicationRepository;
import com.credvenn.lm.common.exception.*;
import com.credvenn.lm.origination.*;
import com.credvenn.lm.security.CurrentActorService;
import com.credvenn.lm.tenant.TenantRepository;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductOriginationService {
    private final OriginationProfileRepository profiles;
    private final OriginationProfileValidator validator;
    private final TenantRepository tenants;
    private final LoanProductMappingRepository products;
    private final LoanRequestApplicationRepository applications;
    private final CurrentActorService actors;
    private final jakarta.persistence.EntityManager entityManager;

    @Transactional(readOnly = true)
    public String resolveProfile(String tenantId, String code) {
        if (tenantId == null || tenantId.isBlank()) throw new ForbiddenOperationException("A tenant user is required");
        if (code != null) return profiles.findByTenantIdAndCodeIgnoreCase(tenantId, validator.normalizeCode(code))
                .orElseThrow(() -> new NotFoundException("Origination profile not found")).getId();
        var tenant = tenants.findById(tenantId).orElseThrow(() -> new NotFoundException("Tenant not found"));
        if (tenant.getDefaultOriginationProfileId() == null)
            throw new BadRequestException("Provide originationProfileCode or configure a tenant default profile");
        var profile = profiles.findByTenantIdAndId(tenantId, tenant.getDefaultOriginationProfileId())
                .orElseThrow(() -> new ConflictException("Tenant default origination profile is unavailable"));
        if (!profile.isActive()) throw new BadRequestException("Tenant default origination profile is inactive");
        return profile.getId();
    }

    public void assertCanDelete(LoanProductMapping mapping) {
        if (!applications.findProductReferencesForUpdate(mapping.getTenantId(), mapping.getId(), String.valueOf(mapping.getFineractProductId())).isEmpty())
            throw new ConflictException("A product selected by an application cannot be deleted; deactivate it instead");
    }

    @Transactional
    @PreAuthorize("hasAuthority('LOAN_PRODUCT_UPDATE')")
    public LoanProductCatalogDtos.LoanProductCatalogResponse associate(String productCode, String profileCode) {
        var actor = actors.requireCurrentUser();
        String profileId = resolveProfile(actor.tenantId(), profileCode);
        var found = products.findByTenantIdAndProductCodeIgnoreCase(actor.tenantId(), productCode.trim())
                .orElseThrow(() -> new NotFoundException("Loan product not found"));
        var mapping = products.findForUpdateByTenantIdAndId(actor.tenantId(), found.getId())
                .orElseThrow(() -> new NotFoundException("Loan product not found"));
        entityManager.refresh(mapping, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (!Objects.equals(mapping.getOriginationProfileId(), profileId)) {
            var references = applications.findProductReferencesForUpdate(actor.tenantId(), mapping.getId(), String.valueOf(mapping.getFineractProductId()));
            if (mapping.getOriginationProfileId() != null && !references.isEmpty())
                throw new ConflictException("A selected product cannot change origination profile; create a separate loan product");
            if (mapping.getOriginationProfileId() == null && references.stream()
                    .anyMatch(a -> !Objects.equals(a.getOriginationProfileId(), profileId)))
                throw new ConflictException("Historical product selections have a missing or different application profile; reconcile them before association");
            mapping.setOriginationProfileId(profileId);
            mapping.setUpdatedBy(actor.username());
            products.save(mapping);
        }
        return LoanProductCatalogDtos.LoanProductCatalogResponse.from(mapping);
    }
}
