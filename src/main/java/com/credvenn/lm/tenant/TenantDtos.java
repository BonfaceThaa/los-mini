package com.credvenn.lm.tenant;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class TenantDtos {

    private TenantDtos() {
    }

    @Schema(name = "CreateTenantRequest")
    public record CreateTenantRequest(
            @NotBlank @Size(max = 100) @Pattern(regexp = "^[a-zA-Z0-9_-]+$") String code,
            @NotBlank @Size(max = 255) String name,
            @NotBlank @Size(max = 100) String fineractTenantId,
            @NotNull @Valid InitialAdminRequest initialAdmin) {
    }

    @Schema(name = "InitialAdminRequest")
    public record InitialAdminRequest(
            @NotBlank @Size(max = 100) String username,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 8, max = 100) String password) {
    }

    @Schema(name = "UpdateTenantStatusRequest")
    public record UpdateTenantStatusRequest(boolean active) {
    }

    @Schema(name = "TenantResponse")
    public record TenantResponse(
            String id,
            String code,
            String name,
            String fineractTenantId,
            boolean active,
            Instant createdAt,
            Instant updatedAt) {
    }
}
