package com.credvenn.lm.security;

import com.credvenn.lm.common.exception.ConflictException;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleTemplateService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    public RoleTemplateService(RoleRepository roleRepository, PermissionRepository permissionRepository) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
    }

    @Transactional
    public void provisionTenantRoles(String tenantId) {
        createOrUpdateSystemTenantRole(
                tenantId,
                "TENANT_ADMIN",
                "Tenant Admin",
                "Tenant administrator with user and role management access",
                Set.of(
                        PermissionCatalog.USER_VIEW.code(),
                        PermissionCatalog.USER_CREATE.code(),
                        PermissionCatalog.USER_UPDATE.code(),
                        PermissionCatalog.USER_DEACTIVATE.code(),
                        PermissionCatalog.USER_ASSIGN_ROLES.code(),
                        PermissionCatalog.ROLE_VIEW.code(),
                        PermissionCatalog.ROLE_CREATE.code(),
                        PermissionCatalog.ROLE_UPDATE.code(),
                        PermissionCatalog.ROLE_DEACTIVATE.code(),
                        PermissionCatalog.ROLE_ASSIGN_PERMISSIONS.code(),
                        PermissionCatalog.CLIENT_VIEW.code(),
                        PermissionCatalog.CLIENT_CREATE.code(),
                        PermissionCatalog.LOAN_VIEW.code(),
                        PermissionCatalog.LOAN_CREATE.code(),
                        PermissionCatalog.KYC_APPROVE.code(),
                        PermissionCatalog.KYC_REVOKE.code(),
                        PermissionCatalog.TASK_CREATE.code(),
                        PermissionCatalog.TASK_COMPLETE.code(),
                        PermissionCatalog.DEVICE_ASSIGN.code()));

        createOrUpdateSystemTenantRole(
                tenantId,
                "OPERATIONS",
                "Operations",
                "Operations user with client and loan workflow access",
                Set.of(
                        PermissionCatalog.CLIENT_VIEW.code(),
                        PermissionCatalog.CLIENT_CREATE.code(),
                        PermissionCatalog.LOAN_VIEW.code(),
                        PermissionCatalog.LOAN_CREATE.code(),
                        PermissionCatalog.TASK_CREATE.code(),
                        PermissionCatalog.TASK_COMPLETE.code()));

        createOrUpdateSystemTenantRole(
                tenantId,
                "KYC_OFFICER",
                "KYC Officer",
                "KYC review access",
                Set.of(
                        PermissionCatalog.CLIENT_VIEW.code(),
                        PermissionCatalog.KYC_APPROVE.code(),
                        PermissionCatalog.KYC_REVOKE.code()));

        createOrUpdateSystemTenantRole(
                tenantId,
                "READ_ONLY",
                "Read Only",
                "Read only tenant access",
                Set.of(PermissionCatalog.CLIENT_VIEW.code(), PermissionCatalog.LOAN_VIEW.code(), PermissionCatalog.ROLE_VIEW.code(), PermissionCatalog.USER_VIEW.code()));
    }

    public Role getRequiredTenantRole(String tenantId, String code) {
        return roleRepository.findByCodeIgnoreCaseAndTenantId(code, tenantId)
                .orElseThrow(() -> new ConflictException("Tenant role %s was not provisioned".formatted(code)));
    }

    private void createOrUpdateSystemTenantRole(
            String tenantId,
            String code,
            String name,
            String description,
            Set<String> permissionCodes) {
        Role role = roleRepository.findByCodeIgnoreCaseAndTenantId(code, tenantId).orElseGet(Role::new);
        role.setTenantId(tenantId);
        role.setCode(code);
        role.setName(name);
        role.setDescription(description);
        role.setScope(RoleScope.TENANT);
        role.setSystemRole(true);
        role.setActive(true);
        role.getPermissions().clear();
        role.getPermissions().addAll(new LinkedHashSet<>(permissionRepository.findAllByCodeIn(permissionCodes)));
        roleRepository.save(role);
    }
}
