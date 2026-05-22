package com.credvenn.lm.tenant;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class TenantBrandingDtos {

    private TenantBrandingDtos() {
    }

    @Schema(name = "UpsertTenantBrandingRequest")
    public record UpsertTenantBrandingRequest(
            @Size(max = 255) String displayName,
            @Size(max = 1000) String logoUrl,
            @Size(max = 50) String supportPhone,
            @Size(max = 2000) String paymentInstructions) {
    }

    @Schema(name = "TenantBrandingResponse")
    public record TenantBrandingResponse(
            String tenantId,
            String displayName,
            String logoUrl,
            String supportPhone,
            String paymentInstructions,
            Instant createdAt,
            Instant updatedAt) {
    }
}
