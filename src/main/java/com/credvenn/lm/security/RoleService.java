package com.credvenn.lm.security;

import com.credvenn.lm.common.exception.BadRequestException;
import com.credvenn.lm.common.exception.ConflictException;
import com.credvenn.lm.common.exception.ForbiddenOperationException;
import com.credvenn.lm.common.exception.NotFoundException;
import com.credvenn.lm.tenant.TenantRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleService {

    private static final Set<String> HIDDEN_TENANT_PERMISSION_CODES = Set.of(
            PermissionCatalog.TENANT_CREATE.code(),
            PermissionCatalog.TENANT_MANAGE_ALL.code(),
            PermissionCatalog.TENANT_SUBSCRIPTION_MANAGE.code(),
            PermissionCatalog.TENANT_VIEW_ALL.code());

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final CurrentActorService currentActorService;
    private final TenantRepository tenantRepository;

    public RoleService(
            RoleRepository roleRepository,
            PermissionRepository permissionRepository,
            CurrentActorService currentActorService,
            TenantRepository tenantRepository) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.currentActorService = currentActorService;
        this.tenantRepository = tenantRepository;
    }

    @Transactional(readOnly = true)
    public List<RoleDtos.RoleResponse> listCurrentTenantRoles() {
        AuthenticatedUser actor = currentActorService.requireCurrentUser();
        ensureTenantBound(actor);
        return listRolesForTenant(actor.tenantId());
    }

    @Transactional(readOnly = true)
    public List<RoleDtos.RoleResponse> listRolesForTenant(String tenantId) {
        assertTenantExists(tenantId);
        return roleRepository.findAllByTenantIdOrderByNameAsc(tenantId).stream().map(RoleService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public RoleDtos.RoleResponse getCurrentTenantRole(String roleId) {
        AuthenticatedUser actor = currentActorService.requireCurrentUser();
        ensureTenantBound(actor);
        return toResponse(getRequiredTenantRole(actor.tenantId(), roleId));
    }

    @Transactional(readOnly = true)
    public RoleDtos.RoleResponse getRoleForTenant(String tenantId, String roleId) {
        return toResponse(getRequiredTenantRole(tenantId, roleId));
    }

    @Transactional
    public RoleDtos.RoleResponse createCurrentTenantRole(RoleDtos.CreateRoleRequest request) {
        AuthenticatedUser actor = currentActorService.requireCurrentUser();
        ensureTenantBound(actor);
        return createRole(actor.tenantId(), request);
    }

    @Transactional
    public RoleDtos.RoleResponse createRole(String tenantId, RoleDtos.CreateRoleRequest request) {
        assertTenantExists(tenantId);
        if (roleRepository.existsByTenantIdAndCodeIgnoreCase(tenantId, request.code().trim())) {
            throw new ConflictException("Role code already exists in this tenant");
        }
        Role role = new Role();
        role.setTenantId(tenantId);
        role.setCode(normalizeCode(request.code()));
        role.setName(request.name().trim());
        role.setDescription(request.description());
        role.setScope(RoleScope.TENANT);
        role.setSystemRole(false);
        role.setActive(true);
        role.getPermissions().addAll(resolvePermissions(request.permissionCodes()));
        return toResponse(roleRepository.save(role));
    }

    @Transactional
    public RoleDtos.RoleResponse updateCurrentTenantRole(String roleId, RoleDtos.UpdateRoleRequest request) {
        AuthenticatedUser actor = currentActorService.requireCurrentUser();
        ensureTenantBound(actor);
        return updateRole(actor.tenantId(), roleId, request);
    }

    @Transactional
    public RoleDtos.RoleResponse updateRole(String tenantId, String roleId, RoleDtos.UpdateRoleRequest request) {
        Role role = getRequiredTenantRole(tenantId, roleId);
        role.setName(request.name().trim());
        role.setDescription(request.description());
        return toResponse(role);
    }

    @Transactional
    public RoleDtos.RoleResponse assignPermissionsCurrentTenant(String roleId, RoleDtos.AssignPermissionsRequest request) {
        AuthenticatedUser actor = currentActorService.requireCurrentUser();
        ensureTenantBound(actor);
        return assignPermissions(actor.tenantId(), roleId, request);
    }

    @Transactional
    public RoleDtos.RoleResponse assignPermissions(String tenantId, String roleId, RoleDtos.AssignPermissionsRequest request) {
        Role role = getRequiredTenantRole(tenantId, roleId);
        role.getPermissions().clear();
        role.getPermissions().addAll(resolvePermissions(request.permissionCodes()));
        return toResponse(role);
    }

    @Transactional
    public RoleDtos.RoleResponse deactivateCurrentTenantRole(String roleId) {
        AuthenticatedUser actor = currentActorService.requireCurrentUser();
        ensureTenantBound(actor);
        return deactivateRole(actor.tenantId(), roleId);
    }

    @Transactional
    public RoleDtos.RoleResponse deactivateRole(String tenantId, String roleId) {
        Role role = getRequiredTenantRole(tenantId, roleId);
        if (role.isSystemRole()) {
            throw new ForbiddenOperationException("System roles cannot be deactivated");
        }
        role.setActive(false);
        return toResponse(role);
    }

    @Transactional(readOnly = true)
    public List<RoleDtos.PermissionResponse> listPermissions() {
        return permissionRepository.findAllByOrderByCodeAsc().stream()
                .filter(permission -> !HIDDEN_TENANT_PERMISSION_CODES.contains(permission.getCode()))
                .map(permission -> new RoleDtos.PermissionResponse(permission.getCode(), permission.getDescription()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Role> resolveTenantRoles(String tenantId, Set<String> roleIds) {
        List<Role> roles = roleRepository.findAllByTenantIdAndIdIn(tenantId, roleIds);
        if (roles.size() != roleIds.size()) {
            throw new BadRequestException("One or more roles do not belong to the tenant");
        }
        if (roles.stream().anyMatch(role -> !role.isActive())) {
            throw new BadRequestException("Inactive roles cannot be assigned");
        }
        return roles;
    }

    private Set<Permission> resolvePermissions(Set<String> permissionCodes) {
        Set<String> normalized = permissionCodes.stream().map(String::trim).map(String::toUpperCase).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (!PermissionCatalog.allCodes().containsAll(normalized)) {
            throw new BadRequestException("Only fixed seeded permissions are allowed");
        }
        if (normalized.stream().anyMatch(HIDDEN_TENANT_PERMISSION_CODES::contains)) {
            throw new BadRequestException("One or more requested permissions are not assignable to tenant roles");
        }
        List<Permission> permissions = permissionRepository.findAllByCodeIn(normalized);
        if (permissions.size() != normalized.size()) {
            throw new BadRequestException("One or more permissions do not exist");
        }
        return new LinkedHashSet<>(permissions);
    }

    private Role getRequiredTenantRole(String tenantId, String roleId) {
        assertTenantExists(tenantId);
        return roleRepository.findByIdAndTenantId(roleId, tenantId)
                .orElseThrow(() -> new NotFoundException("Role not found"));
    }

    private void assertTenantExists(String tenantId) {
        if (!tenantRepository.existsById(tenantId)) {
            throw new NotFoundException("Tenant not found");
        }
    }

    private void ensureTenantBound(AuthenticatedUser actor) {
        if (actor.tenantId() == null) {
            throw new ForbiddenOperationException("Tenant context is required for this operation");
        }
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase().replace(' ', '_');
    }

    static RoleDtos.RoleResponse toResponse(Role role) {
        return new RoleDtos.RoleResponse(
                role.getId(),
                role.getTenantId(),
                role.getCode(),
                role.getName(),
                role.getDescription(),
                role.getScope(),
                role.isActive(),
                role.isSystemRole(),
                role.getCreatedAt(),
                role.getUpdatedAt(),
                role.getPermissions().stream()
                        .filter(permission -> !HIDDEN_TENANT_PERMISSION_CODES.contains(permission.getCode()))
                        .map(permission -> new RoleDtos.PermissionResponse(permission.getCode(), permission.getDescription()))
                        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
    }
}
