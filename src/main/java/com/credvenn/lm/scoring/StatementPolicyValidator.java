package com.credvenn.lm.scoring;

import com.credvenn.lm.common.exception.BadRequestException;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class StatementPolicyValidator {
    private static final Set<String> REQUIRED = Set.of("DEPOSIT_COUNT", "WITHDRAWAL_COUNT", "MONTHLY_SURPLUS", "TRANSACTION_COUNT", "FULIZA_DISBURSEMENT_COUNT");
    private static final Set<String> ALL = Set.of("DEPOSIT_COUNT", "WITHDRAWAL_COUNT", "MONTHLY_SURPLUS", "TRANSACTION_COUNT", "FULIZA_DISBURSEMENT_COUNT", "DEBT_PAYMENTS");
    private static final Map<String, String> OUTCOMES = Map.of("EXCELLENT", "APPROVE", "GOOD", "APPROVE", "FAIR", "REVIEW", "POOR", "DECLINE", "VERY POOR", "DECLINE");
    private static final Map<String, List<Integer>> RANGES = Map.of("EXCELLENT", List.of(85, 100), "GOOD", List.of(70, 84), "FAIR", List.of(55, 69), "POOR", List.of(40, 54), "VERY POOR", List.of(0, 39));

    public void validate(ScorecardDtos.StatementPolicy policy) {
        if (policy == null || policy.minimumCompleteMonths() == null || policy.minimumCompleteMonths() < 4
                || !points(policy.uncertainClassificationScore()) || policy.factors() == null || policy.tiers() == null)
            throw new BadRequestException("Statement policy requires at least four complete months, a low uncertainty score, factors and tiers");
        var factors = new HashMap<String, ScorecardDtos.StatementFactor>();
        BigDecimal expectedWeight = null;
        for (var factor : policy.factors()) {
            if (factor == null || factor.code() == null || !ALL.contains(factor.code()) || factors.putIfAbsent(factor.code(), factor) != null)
                throw new BadRequestException("Unknown or duplicate statement factor");
            if (!factor.enabled()) {
                if (REQUIRED.contains(factor.code())) throw new BadRequestException("Required factor must be enabled: " + factor.code());
                continue;
            }
            if (factor.weight() == null || factor.weight().signum() <= 0) throw new BadRequestException("Enabled factor weight must be positive");
            if ("DEBT_PAYMENTS".equals(factor.code())
                    && !"AVERAGE_MONTHLY_AMOUNT".equals(factor.measure()) && !"PERCENT_ELIGIBLE_INFLOWS".equals(factor.measure()))
                throw new BadRequestException("Enabled debt factor needs an approved measure");
            if (expectedWeight == null) expectedWeight = factor.weight();
            else if (expectedWeight.compareTo(factor.weight()) != 0) throw new BadRequestException("Enabled statement factors must have equal weight");
            validateBands(factor);
        }
        if (!factors.keySet().containsAll(REQUIRED)) throw new BadRequestException("All five required statement factors must be configured");
        var tiers = new ArrayList<>(policy.tiers());
        if (tiers.size() != 5 || tiers.stream().anyMatch(Objects::isNull)) throw new BadRequestException("Five tiers are required");
        tiers.sort(Comparator.comparingInt(ScorecardDtos.Tier::minimum));
        int next = 0;
        var names = new HashSet<String>();
        for (var tier : tiers) {
            if (tier.tier() == null || !names.add(tier.tier()) || !Objects.equals(OUTCOMES.get(tier.tier()), tier.recommendation())
                    || !Objects.equals(RANGES.get(tier.tier()), List.of(tier.minimum(), tier.maximum()))
                    || tier.minimum() != next || tier.maximum() < tier.minimum() || tier.maximum() > 100)
                throw new BadRequestException("Invalid or overlapping tier ranges or recommendation");
            next = tier.maximum() + 1;
        }
        if (next != 101) throw new BadRequestException("Tier ranges must cover scores from 0 to 100");
    }

    private void validateBands(ScorecardDtos.StatementFactor factor) {
        var bands = factor.bands();
        if (bands == null || bands.isEmpty()) throw new BadRequestException("Factor requires bands: " + factor.code());
        boolean surplus = "MONTHLY_SURPLUS".equals(factor.code());
        BigDecimal expected = surplus ? null : BigDecimal.ZERO;
        for (int i = 0; i < bands.size(); i++) {
            var band = bands.get(i);
            if (band == null || !points(band.points()) || !equal(expected, band.lower())
                    || (band.upper() != null && band.lower() != null && band.upper().compareTo(band.lower()) <= 0)
                    || (band.upper() == null && i != bands.size() - 1))
                throw new BadRequestException("Factor bands must be ordered, contiguous and have valid points: " + factor.code());
            expected = band.upper();
        }
        if (expected != null) throw new BadRequestException("Final factor band must be unbounded: " + factor.code());
    }
    private boolean points(BigDecimal value) { return value != null && value.signum() >= 0 && value.compareTo(BigDecimal.valueOf(100)) <= 0; }
    private boolean equal(BigDecimal a, BigDecimal b) { return a == null ? b == null : b != null && a.compareTo(b) == 0; }
}
