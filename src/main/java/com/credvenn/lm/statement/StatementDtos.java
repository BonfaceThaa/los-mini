package com.credvenn.lm.statement;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public final class StatementDtos {

    private StatementDtos() {
    }

    @Schema(name = "StatementAnalysisResponse")
    public record StatementAnalysisResponse(
            String id,
            String applicationId,
            String provider,
            StatementAnalysisStatus status,
            String sourceDocumentId,
            BigDecimal averageMonthlyInflow,
            BigDecimal averageMonthlyOutflow,
            BigDecimal affordabilityScore,
            Integer creditScore,
            String riskTier,
            String recommendation,
            String summary,
            Instant createdAt,
            Instant updatedAt) {
    }

    @Schema(name = "ManualStatementPassRequest")
    public record ManualStatementPassRequest(@Size(max = 1000) String summary) {
    }
}
