package com.credvenn.lm.origination;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class OriginationProfileDtos {
    private OriginationProfileDtos() {}
    public enum Stage { OFFER_SELECTION, INTERNAL_APPROVAL, DISBURSEMENT }
    public enum Requirement {
        KYC_APPROVED, CLIENT_PROVISIONED, STATEMENT_ACCEPTED,
        CONSENT_CAPTURED, DEVICE_ASSIGNED, FINANCING_CALCULATED, ACTIVE_PRODUCT_SELECTED,
        PENDING_LOAN_CREATED, DEPOSIT_MATCHED,
        // Retained for stored profiles and older API clients; not aliases for the new checks.
        FINANCING_ASSESSED, INTERNAL_APPROVAL_VALID,
        VEHICLE_OWNERSHIP_VERIFIED, VALUATION_APPROVED,
        SECURITY_REGISTRATION_CONFIRMED, INSURANCE_VALID
    }
    public enum ValuationBasis { MARKET_VALUE, FORCED_SALE_VALUE }
    public record Configuration(ValuationBasis valuationBasis,
            @DecimalMin(value = "0", inclusive = false) @DecimalMax("1") BigDecimal maxLtvRatio,
            @Min(1) @Max(3650) Integer valuationValidityDays) {}
    public record CreateRequest(@NotBlank @Size(max = 100) String code,
            @NotBlank @Size(max = 255) String displayName, @Size(max = 1000) String description,
            @NotNull Map<Stage, List<Requirement>> requirements, @Valid Configuration configuration) {}
    // Null patch fields are unchanged; an empty description clears it. Supplied objects replace in full.
    public record UpdateRequest(@NotNull @PositiveOrZero Long expectedVersion,
            @Size(max = 255) String displayName, @Size(max = 1000) String description,
            Boolean active, Map<Stage, List<Requirement>> requirements, @Valid Configuration configuration) {}
    public record DefaultRequest(@NotBlank @Size(max = 100) String originationProfileCode) {}
    public record DefaultResponse(String defaultOriginationProfileCode) {}
    public record ProfileResponse(String id, String code, String displayName, String description,
            boolean active, boolean isDefault, long version, Map<Stage, List<Requirement>> requirements,
            Configuration configuration, String createdBy, String updatedBy, Instant createdAt, Instant updatedAt) {}
    public record ProfileList(List<ProfileResponse> items) {}
    public record AuditResponse(String action, String changedBy, Instant createdAt,
            String beforeJson, String afterJson) {}
}
