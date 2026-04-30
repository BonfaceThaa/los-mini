package com.credvenn.lm.security;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Set;

public final class RoleDtos {

    private RoleDtos() {
    }

    @Schema(name = "CreateRoleRequest")
    public record CreateRoleRequest(
            @NotBlank @Size(max = 100) String code,
            @NotBlank @Size(max = 255) String name,
            @Size(max = 255) String description,
            @NotEmpty Set<String> permissionCodes) {
    }

    @Schema(name = "UpdateRoleRequest")
    public record UpdateRoleRequest(
            @NotBlank @Size(max = 255) String name,
            @Size(max = 255) String description) {
    }

    @Schema(name = "AssignPermissionsRequest")
    public record AssignPermissionsRequest(@NotEmpty Set<String> permissionCodes) {
    }

    @Schema(name = "PermissionResponse")
    public record PermissionResponse(String code, String description) {
    }

    @Schema(name = "RoleResponse")
    public record RoleResponse(
            String id,
            String tenantId,
            String code,
            String name,
            String description,
            RoleScope scope,
            boolean active,
            boolean systemRole,
            Instant createdAt,
            Instant updatedAt,
            Set<PermissionResponse> permissions) {
    }
}
