package com.credvenn.lm.origination;

import static com.credvenn.lm.origination.OriginationProfileDtos.*;
import static com.credvenn.lm.origination.OriginationProfileDtos.Requirement.*;
import com.credvenn.lm.common.exception.BadRequestException;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class OriginationProfileValidator {
    private static final Map<Stage, Set<Requirement>> LEGACY_PHONE = Map.of(
        Stage.OFFER_SELECTION, Set.of(KYC_APPROVED, CLIENT_PROVISIONED, STATEMENT_ACCEPTED),
        Stage.INTERNAL_APPROVAL, Set.of(CONSENT_CAPTURED, DEVICE_ASSIGNED, FINANCING_ASSESSED),
        Stage.DISBURSEMENT, Set.of(INTERNAL_APPROVAL_VALID, DEVICE_ASSIGNED, DEPOSIT_MATCHED));
    // Describes the current public service prerequisites, including the checks repeated at approval.
    private static final Map<Stage, Set<Requirement>> PHONE = Map.of(
        Stage.OFFER_SELECTION, Set.of(KYC_APPROVED, CLIENT_PROVISIONED, STATEMENT_ACCEPTED),
        Stage.INTERNAL_APPROVAL, Set.of(CONSENT_CAPTURED, CLIENT_PROVISIONED, DEVICE_ASSIGNED,
                FINANCING_CALCULATED, ACTIVE_PRODUCT_SELECTED),
        Stage.DISBURSEMENT, Set.of(PENDING_LOAN_CREATED, DEVICE_ASSIGNED, DEPOSIT_MATCHED));
    private static final Map<Stage, Set<Requirement>> ALLOWED = Map.of(
        Stage.OFFER_SELECTION, Set.of(KYC_APPROVED, CLIENT_PROVISIONED, STATEMENT_ACCEPTED,
                VEHICLE_OWNERSHIP_VERIFIED, VALUATION_APPROVED),
        Stage.INTERNAL_APPROVAL, Set.of(KYC_APPROVED, CLIENT_PROVISIONED, STATEMENT_ACCEPTED,
                CONSENT_CAPTURED, DEVICE_ASSIGNED, FINANCING_ASSESSED, FINANCING_CALCULATED, ACTIVE_PRODUCT_SELECTED, VEHICLE_OWNERSHIP_VERIFIED, VALUATION_APPROVED),
        Stage.DISBURSEMENT, Set.of(INTERNAL_APPROVAL_VALID, PENDING_LOAN_CREATED, DEVICE_ASSIGNED, DEPOSIT_MATCHED,
                VEHICLE_OWNERSHIP_VERIFIED, VALUATION_APPROVED, SECURITY_REGISTRATION_CONFIRMED, INSURANCE_VALID));

    public String normalizeCode(String code) {
        if (code == null || !code.trim().matches("[A-Za-z][A-Za-z0-9_]{0,99}"))
            throw new BadRequestException("Profile code must start with a letter and contain only letters, digits and underscores");
        return code.trim().toUpperCase(Locale.ROOT);
    }

    public void validate(Map<Stage, List<Requirement>> requirements, Configuration configuration, boolean active) {
        if (requirements == null || !requirements.keySet().equals(EnumSet.allOf(Stage.class)))
            throw new BadRequestException("Requirements must contain OFFER_SELECTION, INTERNAL_APPROVAL and DISBURSEMENT");
        for (var entry : requirements.entrySet()) {
            var values = entry.getValue();
            if (values == null || values.isEmpty() || values.stream().anyMatch(Objects::isNull)
                    || new HashSet<>(values).size() != values.size() || !ALLOWED.get(entry.getKey()).containsAll(values))
                throw new BadRequestException("Invalid, duplicate or misplaced requirements for " + entry.getKey());
        }
        boolean valuation = requirements.values().stream().anyMatch(items -> items.contains(VALUATION_APPROVED));
        boolean hasConfig = configuration != null && (configuration.valuationBasis() != null
                || configuration.maxLtvRatio() != null || configuration.valuationValidityDays() != null);
        if (valuation) {
            if (!hasConfig || configuration.valuationBasis() == null || configuration.maxLtvRatio() == null
                    || configuration.maxLtvRatio().signum() <= 0 || configuration.maxLtvRatio().compareTo(java.math.BigDecimal.ONE) > 0
                    || configuration.valuationValidityDays() == null || configuration.valuationValidityDays() < 1
                    || configuration.valuationValidityDays() > 3650)
                throw new BadRequestException("Valuation requires a basis, LTV ratio greater than 0 and at most 1, and validity of 1-3650 days");
        } else if (hasConfig) {
            throw new BadRequestException("Valuation configuration requires VALUATION_APPROVED");
        }
        // Until profile-driven execution is implemented, only the existing phone journey is operational.
        if (active && (hasConfig || (!matches(requirements, PHONE) && !matches(requirements, LEGACY_PHONE))))
            throw new BadRequestException("Only the existing phone-finance requirement set can be activated; other workflows are not implemented yet");
    }
    private boolean matches(Map<Stage, List<Requirement>> requirements, Map<Stage, Set<Requirement>> supported) {
        return supported.entrySet().stream()
                .allMatch(entry -> new HashSet<>(requirements.get(entry.getKey())).equals(entry.getValue()));
    }
}
