package com.credvenn.lm.scoring;

import static org.junit.jupiter.api.Assertions.*;
import com.credvenn.lm.common.exception.BadRequestException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class StatementPolicyValidatorTest {
    private final StatementPolicyValidator validator = new StatementPolicyValidator();
    private ScorecardDtos.StatementFactor factor(String code, BigDecimal lower) {
        return new ScorecardDtos.StatementFactor(code, true, BigDecimal.ONE, null,
                List.of(new ScorecardDtos.FactorBand(lower, BigDecimal.TEN, BigDecimal.valueOf(50)),
                        new ScorecardDtos.FactorBand(BigDecimal.TEN, null, BigDecimal.valueOf(75))));
    }
    private List<ScorecardDtos.Tier> tiers() {
        return List.of(new ScorecardDtos.Tier("VERY POOR", 0, 39, "DECLINE"), new ScorecardDtos.Tier("POOR", 40, 54, "DECLINE"),
                new ScorecardDtos.Tier("FAIR", 55, 69, "REVIEW"), new ScorecardDtos.Tier("GOOD", 70, 84, "APPROVE"),
                new ScorecardDtos.Tier("EXCELLENT", 85, 100, "APPROVE"));
    }
    private ScorecardDtos.StatementPolicy policy() {
        return new ScorecardDtos.StatementPolicy(4, BigDecimal.TEN, List.of(factor("DEPOSIT_COUNT", BigDecimal.ZERO),
                factor("WITHDRAWAL_COUNT", BigDecimal.ZERO), factor("MONTHLY_SURPLUS", null),
                factor("TRANSACTION_COUNT", BigDecimal.ZERO), factor("FULIZA_DISBURSEMENT_COUNT", BigDecimal.ZERO)), tiers());
    }
    @Test void acceptsCompletePolicyWithOptionalDebtDisabled() { assertDoesNotThrow(() -> validator.validate(policy())); }
    @Test void rejectsInsufficientCoverageAndGapsInBands() {
        var p = policy();
        assertThrows(BadRequestException.class, () -> validator.validate(new ScorecardDtos.StatementPolicy(3, p.uncertainClassificationScore(), p.factors(), p.tiers())));
        var bad = new ScorecardDtos.StatementFactor("DEPOSIT_COUNT", true, BigDecimal.ONE, null,
                List.of(new ScorecardDtos.FactorBand(BigDecimal.ONE, null, BigDecimal.TEN)));
        var factors = new java.util.ArrayList<>(p.factors()); factors.set(0, bad);
        assertThrows(BadRequestException.class, () -> validator.validate(new ScorecardDtos.StatementPolicy(4, BigDecimal.TEN, factors, p.tiers())));
    }
    @Test void rejectsMissingDebtMeasureAndChangedTier() {
        var p = policy(); var factors = new java.util.ArrayList<>(p.factors());
        factors.add(new ScorecardDtos.StatementFactor("DEBT_PAYMENTS", true, BigDecimal.ONE, null,
                List.of(new ScorecardDtos.FactorBand(BigDecimal.ZERO, null, BigDecimal.TEN))));
        assertThrows(BadRequestException.class, () -> validator.validate(new ScorecardDtos.StatementPolicy(4, BigDecimal.TEN, factors, p.tiers())));
        var tiers = new java.util.ArrayList<>(p.tiers()); tiers.set(0, new ScorecardDtos.Tier("VERY POOR", 0, 38, "DECLINE"));
        assertThrows(BadRequestException.class, () -> validator.validate(new ScorecardDtos.StatementPolicy(4, BigDecimal.TEN, p.factors(), tiers)));
    }
}
