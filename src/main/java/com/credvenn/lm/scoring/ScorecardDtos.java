package com.credvenn.lm.scoring;

import com.credvenn.lm.applicationvariable.ApplicationVariableDtos;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

public final class ScorecardDtos {
    private ScorecardDtos() {}
    public record Condition(@NotBlank String questionCode, @NotEmpty Set<@NotBlank String> applicableValues) {}
    public record QuestionRule(@NotBlank String definitionId, @Valid Condition condition) {}
    /** Bands are lower-inclusive and upper-exclusive; null bound means unbounded. */
    public record FactorBand(BigDecimal lower, BigDecimal upper, BigDecimal points) {}
    public record StatementFactor(String code, boolean enabled, BigDecimal weight, String measure, List<@Valid FactorBand> bands) {}
    /** Tiers use inclusive integer boundaries after final score rounding. */
    public record Tier(String tier, int minimum, int maximum, String recommendation) {}
    public record StatementPolicy(Integer minimumCompleteMonths, BigDecimal uncertainClassificationScore,
                                  List<@Valid StatementFactor> factors, List<@Valid Tier> tiers) {}
    public record Draft(@NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal statementWeight,
                        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal questionnaireWeight,
                        @NotNull @Size(max = 100) List<@Valid QuestionRule> questions,
                        @Valid StatementPolicy statementPolicy) {
        public Draft(BigDecimal statementWeight, BigDecimal questionnaireWeight, List<QuestionRule> questions) {
            this(statementWeight, questionnaireWeight, questions, null);
        }
    }
    public record Snapshot(Draft configuration, List<ApplicationVariableDtos.DefinitionResponse> questions) {}
    public record Response(String id, String profileCode, int version, String status, Draft draft,
                           Snapshot snapshot, String createdBy, String updatedBy, Instant publishedAt) {}
}
