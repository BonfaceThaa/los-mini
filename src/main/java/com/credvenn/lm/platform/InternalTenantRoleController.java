package com.credvenn.lm.platform;

import com.credvenn.lm.security.RoleDtos;
import com.credvenn.lm.security.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/internal/tenants/{tenantId}/roles")
@Tag(name = "Platform Tenant Roles")
@SecurityRequirement(name = "bearerAuth")
public class InternalTenantRoleController {

    private final RoleService roleService;

    public InternalTenantRoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('TENANT_MANAGE_ALL')")
    @Operation(summary = "List roles for any tenant as a platform administrator")
    public ResponseEntity<List<RoleDtos.RoleResponse>> listRoles(@PathVariable String tenantId) {
        return ResponseEntity.ok(roleService.listRolesForTenant(tenantId));
    }

    @GetMapping("/{roleId}")
    @PreAuthorize("hasAuthority('TENANT_MANAGE_ALL')")
    @Operation(summary = "Get a tenant role by id as a platform administrator")
    public ResponseEntity<RoleDtos.RoleResponse> getRole(@PathVariable String tenantId, @PathVariable String roleId) {
        return ResponseEntity.ok(roleService.getRoleForTenant(tenantId, roleId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('TENANT_MANAGE_ALL')")
    @Operation(summary = "Create a role for any tenant as a platform administrator")
    public ResponseEntity<RoleDtos.RoleResponse> createRole(
            @PathVariable String tenantId,
            @Valid @RequestBody RoleDtos.CreateRoleRequest request) {
        return ResponseEntity.ok(roleService.createRole(tenantId, request));
    }

    @PatchMapping("/{roleId}")
    @PreAuthorize("hasAuthority('TENANT_MANAGE_ALL')")
    @Operation(summary = "Update a tenant role as a platform administrator")
    public ResponseEntity<RoleDtos.RoleResponse> updateRole(
            @PathVariable String tenantId,
            @PathVariable String roleId,
            @Valid @RequestBody RoleDtos.UpdateRoleRequest request) {
        return ResponseEntity.ok(roleService.updateRole(tenantId, roleId, request));
    }

    @PutMapping("/{roleId}/permissions")
    @PreAuthorize("hasAuthority('TENANT_MANAGE_ALL')")
    @Operation(summary = "Replace permissions for a tenant role as a platform administrator")
    public ResponseEntity<RoleDtos.RoleResponse> assignPermissions(
            @PathVariable String tenantId,
            @PathVariable String roleId,
            @Valid @RequestBody RoleDtos.AssignPermissionsRequest request) {
        return ResponseEntity.ok(roleService.assignPermissions(tenantId, roleId, request));
    }

    @PatchMapping("/{roleId}/deactivate")
    @PreAuthorize("hasAuthority('TENANT_MANAGE_ALL')")
    @Operation(summary = "Deactivate a tenant role as a platform administrator")
    public ResponseEntity<RoleDtos.RoleResponse> deactivateRole(@PathVariable String tenantId, @PathVariable String roleId) {
        return ResponseEntity.ok(roleService.deactivateRole(tenantId, roleId));
    }
}
