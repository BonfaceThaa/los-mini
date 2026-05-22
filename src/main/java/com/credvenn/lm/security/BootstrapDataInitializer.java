package com.credvenn.lm.security;

import com.credvenn.lm.tenant.TenantRepository;
import com.credvenn.lm.user.AppUser;
import com.credvenn.lm.user.AppUserRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BootstrapDataInitializer implements ApplicationRunner {

    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final BootstrapSuperAdminProperties bootstrapSuperAdminProperties;
    private final TenantRepository tenantRepository;
    private final RoleTemplateService roleTemplateService;

    public BootstrapDataInitializer(
            PermissionRepository permissionRepository,
            RoleRepository roleRepository,
            AppUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            BootstrapSuperAdminProperties bootstrapSuperAdminProperties,
            TenantRepository tenantRepository,
            RoleTemplateService roleTemplateService) {
        this.permissionRepository = permissionRepository;
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.bootstrapSuperAdminProperties = bootstrapSuperAdminProperties;
        this.tenantRepository = tenantRepository;
        this.roleTemplateService = roleTemplateService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedPermissions();
        Role superAdminRole = ensurePlatformRole(
                "SUPER_ADMIN",
                "Super Admin",
                "Platform super administrator",
                PermissionCatalog.allCodes());
        ensurePlatformRole(
                "INTERNAL_STAFF",
                "Internal Staff",
                "Internal staff allowed to onboard tenants and view tenant subscriptions",
                Set.of(
                        PermissionCatalog.TENANT_CREATE.code(),
                        PermissionCatalog.TENANT_VIEW_ALL.code(),
                        PermissionCatalog.TENANT_SUBSCRIPTION_VIEW.code(),
                        PermissionCatalog.STATEMENT_INBOX_VIEW.code(),
                        PermissionCatalog.STATEMENT_INBOX_RESOLVE.code(),
                        PermissionCatalog.STATEMENT_INBOUND_INGEST.code()));
        tenantRepository.findAll().forEach(tenant -> roleTemplateService.provisionTenantRoles(tenant.getId()));
        ensureBootstrapSuperAdmin(superAdminRole);
    }

    private void seedPermissions() {
        for (PermissionCatalog entry : PermissionCatalog.values()) {
            permissionRepository.findByCode(entry.code()).orElseGet(() -> {
                Permission permission = new Permission();
                permission.setCode(entry.code());
                permission.setDescription(entry.description());
                return permissionRepository.save(permission);
            });
        }
    }

    private Role ensurePlatformRole(String code, String name, String description, Set<String> permissionCodes) {
        Role role = roleRepository.findByCodeIgnoreCaseAndTenantIdIsNull(code).orElseGet(Role::new);
        role.setCode(code);
        role.setName(name);
        role.setDescription(description);
        role.setScope(RoleScope.PLATFORM);
        role.setActive(true);
        role.setSystemRole(true);
        role.setTenantId(null);
        role.getPermissions().clear();
        role.getPermissions().addAll(new LinkedHashSet<>(permissionRepository.findAllByCodeIn(permissionCodes)));
        return roleRepository.save(role);
    }

    private void ensureBootstrapSuperAdmin(Role superAdminRole) {
        boolean exists = userRepository.existsByTenantIdIsNullAndEmailIgnoreCase(bootstrapSuperAdminProperties.email())
                || userRepository.existsByTenantIdIsNullAndUsernameIgnoreCase(bootstrapSuperAdminProperties.username());
        if (exists) {
            return;
        }
        AppUser user = new AppUser();
        user.setTenantId(null);
        user.setUsername(bootstrapSuperAdminProperties.username());
        user.setEmail(bootstrapSuperAdminProperties.email());
        user.setPasswordHash(passwordEncoder.encode(bootstrapSuperAdminProperties.password()));
        user.getRoles().add(superAdminRole);
        userRepository.save(user);
    }
}
