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

    @Schema(name = "KycActionResponse")
    public record KycActionResponse(
            String names,
            String firstName,
            String lastName,
            String otherNames,
            String dob,
            String gender,
            String phoneNumber,
            String verifyIdNumber,
            String idVerification) {
    }

    @Schema(name = "KycCheckResponse")
    public record KycCheckResponse(
            String id,
            String applicationId,
            String provider,
            KycStatus status,
            String providerReference,
            String summary,
            KycActionResponse actions,
            String reviewedBy,
            String reviewReason,
            Instant reviewedAt,
            Instant createdAt,
            Instant updatedAt) {
    }
}
