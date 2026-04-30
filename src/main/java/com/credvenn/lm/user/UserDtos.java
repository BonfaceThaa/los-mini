package com.credvenn.lm.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Set;

public final class UserDtos {

    private UserDtos() {
    }

    @Schema(name = "CreateUserRequest")
    public record CreateUserRequest(
            @NotBlank @Size(max = 100) String username,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 8, max = 100) String password,
            @NotEmpty Set<String> roleIds) {
    }

    @Schema(name = "UpdateUserRequest")
    public record UpdateUserRequest(
            @NotBlank @Size(max = 100) String username,
            @NotBlank @Email @Size(max = 255) String email) {
    }

    @Schema(name = "AssignRolesRequest")
    public record AssignRolesRequest(@NotEmpty Set<String> roleIds) {
    }

    @Schema(name = "UpdateUserStatusRequest")
    public record UpdateUserStatusRequest(boolean active) {
    }

    @Schema(name = "UpdatePasswordRequest")
    public record UpdatePasswordRequest(@NotBlank @Size(min = 8, max = 100) String password) {
    }

    @Schema(name = "UserRoleSummary")
    public record UserRoleSummary(String id, String code, String name, boolean active) {
    }

    @Schema(name = "UserResponse")
    public record UserResponse(
            String id,
            String tenantId,
            String username,
            String email,
            boolean active,
            Instant createdAt,
            Instant updatedAt,
            Instant lastLoginAt,
            Set<UserRoleSummary> roles) {
    }
}
