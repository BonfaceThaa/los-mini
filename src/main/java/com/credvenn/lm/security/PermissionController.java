package com.credvenn.lm.security;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/permissions")
@Tag(name = "Permissions")
@SecurityRequirement(name = "bearerAuth")
public class PermissionController {

    private final RoleService roleService;

    public PermissionController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_VIEW') or hasAuthority('TENANT_VIEW_ALL')")
    @Operation(summary = "List the fixed seeded permission catalog")
    public ResponseEntity<List<RoleDtos.PermissionResponse>> listPermissions() {
        return ResponseEntity.ok(roleService.listPermissions());
    }
}
