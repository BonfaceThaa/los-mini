package com.credvenn.lm.security;

import com.credvenn.lm.common.api.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/roles")
@Tag(name = "Tenant Roles")
@SecurityRequirement(name = "bearerAuth")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_VIEW')")
    @Operation(summary = "List roles for the authenticated tenant")
    public ResponseEntity<PagedResponse<RoleDtos.RoleResponse>> listRoles(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        return ResponseEntity.ok(roleService.listCurrentTenantRoles(page, size, sortBy, sortDir));
    }

    @GetMapping("/{roleId}")
    @PreAuthorize("hasAuthority('ROLE_VIEW')")
    @Operation(summary = "Get a tenant role by id")
    public ResponseEntity<RoleDtos.RoleResponse> getRole(@PathVariable String roleId) {
        return ResponseEntity.ok(roleService.getCurrentTenantRole(roleId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_CREATE')")
    @Operation(summary = "Create a custom role within the authenticated tenant")
    public ResponseEntity<RoleDtos.RoleResponse> createRole(@Valid @RequestBody RoleDtos.CreateRoleRequest request) {
        return ResponseEntity.ok(roleService.createCurrentTenantRole(request));
    }

    @PatchMapping("/{roleId}")
    @PreAuthorize("hasAuthority('ROLE_UPDATE')")
    @Operation(summary = "Update a tenant role's metadata")
    public ResponseEntity<RoleDtos.RoleResponse> updateRole(
            @PathVariable String roleId,
            @Valid @RequestBody RoleDtos.UpdateRoleRequest request) {
        return ResponseEntity.ok(roleService.updateCurrentTenantRole(roleId, request));
    }

    @PutMapping("/{roleId}/permissions")
    @PreAuthorize("hasAuthority('ROLE_ASSIGN_PERMISSIONS')")
    @Operation(summary = "Replace the permissions assigned to a tenant role")
    public ResponseEntity<RoleDtos.RoleResponse> assignPermissions(
            @PathVariable String roleId,
            @Valid @RequestBody RoleDtos.AssignPermissionsRequest request) {
        return ResponseEntity.ok(roleService.assignPermissionsCurrentTenant(roleId, request));
    }

    @PatchMapping("/{roleId}/deactivate")
    @PreAuthorize("hasAuthority('ROLE_DEACTIVATE')")
    @Operation(summary = "Deactivate a tenant role without deleting it")
    public ResponseEntity<RoleDtos.RoleResponse> deactivateRole(@PathVariable String roleId) {
        return ResponseEntity.ok(roleService.deactivateCurrentTenantRole(roleId));
    }
}
