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
                        PermissionCatalog.LOAN_PRODUCT_UPDATE.code(),
                        PermissionCatalog.GL_ACCOUNT_VIEW.code(),
                        PermissionCatalog.GL_ACCOUNT_CREATE.code(),
                        PermissionCatalog.ACCOUNTING_RULE_VIEW.code(),
                        PermissionCatalog.ACCOUNTING_RULE_CREATE.code(),
                        PermissionCatalog.KYC_VIEW.code(),
                        PermissionCatalog.KYC_RUN.code(),
                        PermissionCatalog.KYC_MANUAL_REVIEW.code(),
                        PermissionCatalog.CREDIT_CHECK_VIEW.code(),
                        PermissionCatalog.CREDIT_CHECK_RUN.code(),
                        PermissionCatalog.CREDIT_MANUAL_APPROVE.code(),
                        PermissionCatalog.DOCUMENT_VIEW.code(),
                        PermissionCatalog.DOCUMENT_UPLOAD.code(),
                        PermissionCatalog.DOCUMENT_DELETE.code(),
                        PermissionCatalog.STATEMENT_INBOX_VIEW.code(),
                        PermissionCatalog.STATEMENT_INBOX_RESOLVE.code(),
                        PermissionCatalog.TENANT_SUBSCRIPTION_VIEW.code(),
                        PermissionCatalog.INVENTORY_VIEW.code(),
                        PermissionCatalog.INVENTORY_MANAGE.code(),
                        PermissionCatalog.DEVICE_CONTROL_CONFIG_VIEW.code(),
                        PermissionCatalog.DEVICE_CONTROL_CONFIG_MANAGE.code(),
                        PermissionCatalog.DEVICE_CONTROL_ACTION_VIEW.code(),
                        PermissionCatalog.DEVICE_CONTROL_ACTION_MANAGE.code(),
                        PermissionCatalog.DEVICE_OFFLINE_PIN_VIEW.code(),
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
                        PermissionCatalog.KYC_VIEW.code(),
                        PermissionCatalog.KYC_RUN.code(),
                        PermissionCatalog.CREDIT_CHECK_VIEW.code(),
                        PermissionCatalog.CREDIT_CHECK_RUN.code(),
                        PermissionCatalog.DOCUMENT_VIEW.code(),
                        PermissionCatalog.DOCUMENT_UPLOAD.code(),
                        PermissionCatalog.INVENTORY_VIEW.code(),
                        PermissionCatalog.DEVICE_CONTROL_ACTION_VIEW.code(),
                        PermissionCatalog.TASK_CREATE.code(),
                        PermissionCatalog.TASK_COMPLETE.code()));

        createOrUpdateSystemTenantRole(
                tenantId,
                "KYC_OFFICER",
                "KYC Officer",
                "KYC review access",
                Set.of(
                        PermissionCatalog.CLIENT_VIEW.code(),
                        PermissionCatalog.KYC_VIEW.code(),
                        PermissionCatalog.KYC_MANUAL_REVIEW.code(),
                        PermissionCatalog.KYC_APPROVE.code(),
                        PermissionCatalog.KYC_REVOKE.code()));

        createOrUpdateSystemTenantRole(
                tenantId,
                "READ_ONLY",
                "Read Only",
                "Read only tenant access",
                Set.of(
                        PermissionCatalog.CLIENT_VIEW.code(),
                        PermissionCatalog.LOAN_VIEW.code(),
                        PermissionCatalog.ROLE_VIEW.code(),
                        PermissionCatalog.USER_VIEW.code(),
                        PermissionCatalog.TENANT_SUBSCRIPTION_VIEW.code(),
                        PermissionCatalog.DOCUMENT_VIEW.code(),
                        PermissionCatalog.KYC_VIEW.code(),
                        PermissionCatalog.CREDIT_CHECK_VIEW.code(),
                        PermissionCatalog.INVENTORY_VIEW.code(),
                        PermissionCatalog.DEVICE_CONTROL_CONFIG_VIEW.code(),
                        PermissionCatalog.DEVICE_CONTROL_ACTION_VIEW.code(),
                        PermissionCatalog.ACCOUNTING_RULE_VIEW.code()));
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
