package com.credvenn.lm.logbook;

import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class LogbookDtos {
    private LogbookDtos() {}
    public enum ValuationStatus { PENDING, APPROVED, REJECTED, REVOKED }
    public enum VerificationKind { OWNERSHIP, INSURANCE, SECURITY_REGISTRATION }
    public enum VerificationStatus { VERIFIED, REJECTED, REVOKED }
    @Schema(description="Full vehicle replacement. Omit expectedVersion only on first capture. Any replacement invalidates prior evidence for readiness.")
    public record VehicleRequest(@PositiveOrZero Long expectedVersion,
        @NotBlank @Size(max=20) String registrationNumber, @NotBlank @Size(max=50) String chassisNumber,
        @NotBlank @Size(max=50) String engineNumber, @NotBlank @Size(max=100) String make,
        @NotBlank @Size(max=100) String model, @Min(1900) int manufactureYear,
        @NotBlank @Size(max=255) String registeredOwner, @NotBlank @Size(max=100) String logbookNumber) {}
    @Schema(description="Valuer submission in KES. Report document must belong to this tenant and application. Values cannot be edited; submit a replacement valuation.")
    public record ValuationRequest(@NotNull @PositiveOrZero Long expectedVersion,
        @NotNull @DecimalMin("0.01") @Digits(integer=17, fraction=2) BigDecimal marketValue,
        @NotNull @DecimalMin("0.01") @Digits(integer=17, fraction=2) BigDecimal forcedSaleValue,
        @NotNull @PastOrPresent LocalDate valuedOn, @NotBlank @Size(max=255) String valuerOrganization,
        @NotBlank @Size(max=36) String reportDocumentId) {}
    public record ReviewRequest(@NotNull @PositiveOrZero Long expectedVersion,
        @NotNull ValuationStatus decision, @NotBlank @Size(max=1000) String reason) {}
    @Schema(description="Append-only manual verification evidence. INSURANCE requires inclusive validFrom/validUntil dates. These are staff attestations, not automatic registry or insurer checks.")
    public record VerificationRequest(@NotNull @PositiveOrZero Long expectedVersion,
        @NotNull VerificationStatus status, @NotBlank @Size(max=255) String referenceNumber,
        @NotBlank @Size(max=36) String documentId, LocalDate validFrom, LocalDate validUntil,
        @NotBlank @Size(max=1000) String notes) {}
    public record Readiness(boolean vehicleCaptured, boolean ownershipVerified, boolean valuationApproved,
        boolean insuranceValid, boolean securityRegistrationConfirmed, String currencyCode,
        BigDecimal maximumSecuredAmount, boolean requestedAmountWithinLimit, List<String> missingCapabilities) {}
    @Schema(description="Records and computed capability readiness only. This endpoint does not approve or disburse a loan. Use vehicle.version as expectedVersion for the next write.")
    public record Summary(LogbookVehicle vehicle, List<LogbookValuation> valuations,
        List<LogbookVerification> verifications, Readiness readiness) {}
}
