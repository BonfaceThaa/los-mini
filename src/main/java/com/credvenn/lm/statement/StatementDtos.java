package com.credvenn.lm.statement;

import io.swagger.v3.oas.annotations.media.Schema;
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
            String recommendation,
            String summary,
            Instant createdAt,
            Instant updatedAt) {
    }
}
