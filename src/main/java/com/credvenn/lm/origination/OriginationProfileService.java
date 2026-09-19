package com.credvenn.lm.origination;

import static com.credvenn.lm.origination.OriginationProfileDtos.*;
import com.credvenn.lm.common.exception.*;
import com.credvenn.lm.security.AuthenticatedUser;
import com.credvenn.lm.security.CurrentActorService;
import com.credvenn.lm.tenant.Tenant;
import com.credvenn.lm.tenant.TenantRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
@Transactional(readOnly = true)
public class OriginationProfileService {
    private final OriginationProfileRepository profiles;
    private final OriginationProfileAuditRepository audits;
    private final TenantRepository tenants;
    private final CurrentActorService actors;
    private final OriginationProfileValidator validator;
    private final ObjectMapper json;

    @PreAuthorize("hasAuthority('ORIGINATION_PROFILE_CREATE')")
    @Transactional
    public ProfileResponse create(CreateRequest request) {
        var actor = actor();
        Tenant tenant = tenant(actor, true);
        String code = validator.normalizeCode(request.code());
        if (profiles.existsByTenantIdAndCodeIgnoreCase(actor.tenantId(), code))
            throw new ConflictException("Origination profile code already exists");
        String name = name(request.displayName());
        validator.validate(request.requirements(), request.configuration(), false);
        var profile = new OriginationProfile();
        profile.setTenantId(actor.tenantId());
        profile.setCode(code);
        profile.setDisplayName(name);
        profile.setDescription(description(request.description()));
        profile.setRequirementsJson(write(request.requirements()));
        profile.setConfigurationJson(write(request.configuration()));
        profile.setCreatedBy(actor.username());
        profile.setUpdatedBy(actor.username());
        profile = profiles.saveAndFlush(profile);
        var response = response(profile, tenant);
        audit(actor, profile, "CREATE", null, write(response));
        return response;
    }

    @PreAuthorize("hasAnyAuthority('ORIGINATION_PROFILE_VIEW','LOAN_VIEW','LOAN_CREATE')")
    public ProfileList list(Boolean active) {
        var actor = actor();
        var tenant = tenant(actor, false);
        var result = active == null ? profiles.findAllByTenantIdOrderByDisplayNameAsc(actor.tenantId())
                : profiles.findAllByTenantIdAndActiveOrderByDisplayNameAsc(actor.tenantId(), active);
        return new ProfileList(result.stream().map(p -> response(p, tenant)).toList());
    }

    @PreAuthorize("hasAnyAuthority('ORIGINATION_PROFILE_VIEW','LOAN_VIEW','LOAN_CREATE')")
    public ProfileResponse get(String code) {
        var actor = actor();
        return response(required(actor.tenantId(), code), tenant(actor, false));
    }

    @PreAuthorize("hasAuthority('ORIGINATION_PROFILE_UPDATE')")
    @Transactional
    public ProfileResponse update(String code, UpdateRequest request) {
        var actor = actor();
        var tenant = tenant(actor, true);
        var profile = required(actor.tenantId(), code);
        if (request.expectedVersion() == null || request.expectedVersion() != profile.getVersion())
            throw new ConflictException("Profile changed; reload it and retry with its current version");
        if (request.displayName() == null && request.description() == null && request.active() == null
                && request.requirements() == null && request.configuration() == null)
            throw new BadRequestException("No profile changes supplied");
        boolean active = request.active() == null ? profile.isActive() : request.active();
        if (!active && Objects.equals(profile.getId(), tenant.getDefaultOriginationProfileId()))
            throw new ConflictException("Select another default before deactivating this profile");
        var requirements = request.requirements() == null ? requirements(profile) : request.requirements();
        var configuration = request.configuration() == null ? configuration(profile) : request.configuration();
        validator.validate(requirements, configuration, active);
        String name = request.displayName() == null ? profile.getDisplayName() : name(request.displayName());
        String description = request.description() == null ? profile.getDescription() : description(request.description());
        String before = write(response(profile, tenant));
        profile.setDisplayName(name);
        profile.setDescription(description);
        profile.setActive(active);
        profile.setRequirementsJson(write(requirements));
        profile.setConfigurationJson(write(configuration));
        profile.setUpdatedBy(actor.username());
        profiles.saveAndFlush(profile);
        var response = response(profile, tenant);
        audit(actor, profile, "UPDATE", before, write(response));
        return response;
    }

    // Soft deletion retains references and audit evidence for existing applications.
    @PreAuthorize("hasAuthority('ORIGINATION_PROFILE_UPDATE')")
    @Transactional
    public void deactivate(String code, long expectedVersion) {
        update(code, new UpdateRequest(expectedVersion, null, null, false, null, null));
    }

    @PreAuthorize("hasAnyAuthority('ORIGINATION_PROFILE_VIEW','LOAN_VIEW','LOAN_CREATE')")
    public DefaultResponse getDefault() {
        var actor = actor();
        return defaultResponse(tenant(actor, false));
    }

    @PreAuthorize("hasAuthority('ORIGINATION_PROFILE_UPDATE')")
    @Transactional
    public DefaultResponse setDefault(DefaultRequest request) {
        var actor = actor();
        var tenant = tenant(actor, true);
        var profile = required(actor.tenantId(), request.originationProfileCode());
        if (!profile.isActive()) throw new BadRequestException("Default origination profile must be active");
        validator.validate(requirements(profile), configuration(profile), true);
        var before = defaultResponse(tenant);
        if (Objects.equals(profile.getId(), tenant.getDefaultOriginationProfileId())) return before;
        tenant.setDefaultOriginationProfileId(profile.getId());
        tenants.saveAndFlush(tenant);
        var after = new DefaultResponse(profile.getCode());
        audit(actor, profile, "SET_DEFAULT", write(before), write(after));
        return after;
    }

    @PreAuthorize("hasAuthority('ORIGINATION_PROFILE_VIEW')")
    public Page<AuditResponse> history(String code, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new BadRequestException("Page must be nonnegative and size must be 1-100");
        var actor = actor();
        var profile = required(actor.tenantId(), code);
        return audits.findAllByTenantIdAndProfileIdOrderByCreatedAtDesc(actor.tenantId(), profile.getId(), PageRequest.of(page, size))
                .map(a -> new AuditResponse(a.getAction(), a.getChangedBy(), a.getCreatedAt(), a.getBeforeJson(), a.getAfterJson()));
    }

    private AuthenticatedUser actor() {
        var actor = actors.requireCurrentUser();
        if (actor.tenantId() == null || actor.tenantId().isBlank())
            throw new ForbiddenOperationException("A tenant user is required for origination profile management");
        return actor;
    }

    private Tenant tenant(AuthenticatedUser actor, boolean lock) {
        // All mutations take the tenant lock first, serializing default changes and deactivation.
        return (lock ? tenants.findForOriginationUpdate(actor.tenantId()) : tenants.findById(actor.tenantId()))
                .orElseThrow(() -> new NotFoundException("Tenant not found"));
    }

    private OriginationProfile required(String tenantId, String code) {
        return profiles.findByTenantIdAndCodeIgnoreCase(tenantId, validator.normalizeCode(code))
                .orElseThrow(() -> new NotFoundException("Origination profile not found"));
    }

    private DefaultResponse defaultResponse(Tenant tenant) {
        if (tenant.getDefaultOriginationProfileId() == null) return new DefaultResponse(null);
        return new DefaultResponse(profiles.findByTenantIdAndId(tenant.getId(), tenant.getDefaultOriginationProfileId())
                .orElseThrow(() -> new ConflictException("Tenant default profile is unavailable")).getCode());
    }

    private ProfileResponse response(OriginationProfile p, Tenant tenant) {
        return new ProfileResponse(p.getId(), p.getCode(), p.getDisplayName(), p.getDescription(), p.isActive(),
                Objects.equals(p.getId(), tenant.getDefaultOriginationProfileId()), p.getVersion(), requirements(p), configuration(p),
                p.getCreatedBy(), p.getUpdatedBy(), p.getCreatedAt(), p.getUpdatedAt());
    }

    private Map<Stage, List<Requirement>> requirements(OriginationProfile p) {
        try { return json.readValue(p.getRequirementsJson(), new TypeReference<>() {}); }
        catch (Exception e) { throw new IllegalStateException("Stored profile requirements are invalid", e); }
    }

    private Configuration configuration(OriginationProfile p) {
        try { return p.getConfigurationJson() == null ? null : json.readValue(p.getConfigurationJson(), Configuration.class); }
        catch (Exception e) { throw new IllegalStateException("Stored profile configuration is invalid", e); }
    }

    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalStateException("Cannot serialize origination profile", e); }
    }

    private void audit(AuthenticatedUser actor, OriginationProfile profile, String action, String before, String after) {
        var audit = new OriginationProfileAudit();
        audit.setTenantId(actor.tenantId()); audit.setProfileId(profile.getId()); audit.setAction(action);
        audit.setChangedBy(actor.username()); audit.setBeforeJson(before); audit.setAfterJson(after);
        audits.save(audit);
    }

    private String name(String value) {
        if (value == null || value.isBlank() || value.length() > 255) throw new BadRequestException("Display name must contain 1-255 characters");
        return value.trim();
    }

    private String description(String value) {
        if (value != null && value.length() > 1000) throw new BadRequestException("Description must be at most 1000 characters");
        return value == null || value.isBlank() ? null : value.trim();
    }
}
