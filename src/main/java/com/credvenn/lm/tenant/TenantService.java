package com.credvenn.lm.tenant;

import com.credvenn.lm.common.exception.ConflictException;
import com.credvenn.lm.common.exception.NotFoundException;
import com.credvenn.lm.security.Role;
import com.credvenn.lm.security.RoleTemplateService;
import com.credvenn.lm.user.AppUser;
import com.credvenn.lm.user.AppUserRepository;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantService {

    private final TenantRepository tenantRepository;
    private final RoleTemplateService roleTemplateService;
    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public TenantService(
            TenantRepository tenantRepository,
            RoleTemplateService roleTemplateService,
            AppUserRepository userRepository,
            PasswordEncoder passwordEncoder) {
        this.tenantRepository = tenantRepository;
        this.roleTemplateService = roleTemplateService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public TenantDtos.TenantResponse createTenant(TenantDtos.CreateTenantRequest request) {
        if (tenantRepository.existsByCodeIgnoreCase(request.code())) {
            throw new ConflictException("Tenant code already exists");
        }

        Tenant tenant = new Tenant();
        tenant.setCode(request.code().trim());
        tenant.setName(request.name().trim());
        tenant.setFineractTenantId(request.fineractTenantId().trim());
        tenant.setActive(true);
        tenant.setKycMode(request.kycMode() == null ? TenantKycMode.AUTO : request.kycMode());
        tenant.setStatementAnalysisMode(request.statementAnalysisMode() == null
                ? TenantStatementAnalysisMode.AUTO
                : request.statementAnalysisMode());
        tenant = tenantRepository.save(tenant);

        roleTemplateService.provisionTenantRoles(tenant.getId());
        Role tenantAdminRole = roleTemplateService.getRequiredTenantRole(tenant.getId(), "TENANT_ADMIN");

        AppUser admin = new AppUser();
        admin.setTenantId(tenant.getId());
        admin.setUsername(request.initialAdmin().username().trim());
        admin.setEmail(request.initialAdmin().email().trim().toLowerCase());
        admin.setPasswordHash(passwordEncoder.encode(request.initialAdmin().password()));
        admin.getRoles().add(tenantAdminRole);
        userRepository.save(admin);

        return toResponse(tenant);
    }

    @Transactional(readOnly = true)
    public List<TenantDtos.TenantResponse> listTenants() {
        return tenantRepository.findAllByOrderByNameAsc().stream().map(TenantService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public TenantDtos.TenantResponse getTenant(String tenantId) {
        return toResponse(getRequiredTenant(tenantId));
    }

    @Transactional
    public TenantDtos.TenantResponse updateStatus(String tenantId, TenantDtos.UpdateTenantStatusRequest request) {
        Tenant tenant = getRequiredTenant(tenantId);
        tenant.setActive(request.active());
        return toResponse(tenant);
    }

    @Transactional
    public TenantDtos.TenantResponse updateKycMode(String tenantId, TenantDtos.UpdateTenantKycModeRequest request) {
        Tenant tenant = getRequiredTenant(tenantId);
        tenant.setKycMode(request.kycMode());
        return toResponse(tenant);
    }

    @Transactional
    public TenantDtos.TenantResponse updateStatementAnalysisMode(
            String tenantId,
            TenantDtos.UpdateTenantStatementAnalysisModeRequest request) {
        Tenant tenant = getRequiredTenant(tenantId);
        tenant.setStatementAnalysisMode(request.statementAnalysisMode());
        return toResponse(tenant);
    }

    @Transactional(readOnly = true)
    public Tenant requireActiveTenantByCode(String code) {
        Tenant tenant = tenantRepository.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new NotFoundException("Tenant not found"));
        if (!tenant.isActive()) {
            throw new ConflictException("Tenant is inactive");
        }
        return tenant;
    }

    public Tenant getRequiredTenant(String tenantId) {
        return tenantRepository.findById(tenantId).orElseThrow(() -> new NotFoundException("Tenant not found"));
    }

    static TenantDtos.TenantResponse toResponse(Tenant tenant) {
        return new TenantDtos.TenantResponse(
                tenant.getId(),
                tenant.getCode(),
                tenant.getName(),
                tenant.getFineractTenantId(),
                tenant.isActive(),
                tenant.getKycMode(),
                tenant.getStatementAnalysisMode(),
                tenant.getCreatedAt(),
                tenant.getUpdatedAt());
    }
}
