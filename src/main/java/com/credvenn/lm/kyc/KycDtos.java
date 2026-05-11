package com.credvenn.lm.kyc;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class KycDtos {

    private KycDtos() {
    }

    @Schema(name = "ManualKycReviewRequest")
    public record ManualKycReviewRequest(
            boolean approved,
            @NotBlank @Size(max = 1000) String reason) {
    }

    @Schema(name = "KycCheckResponse")
    public record KycCheckResponse(
            String id,
            String applicationId,
            String provider,
            KycStatus status,
            String providerReference,
            String summary,
            String reviewedBy,
            String reviewReason,
            Instant reviewedAt,
            Instant createdAt,
            Instant updatedAt) {
    }
}
