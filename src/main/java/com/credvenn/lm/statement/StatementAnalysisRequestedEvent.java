package com.credvenn.lm.statement;

public record StatementAnalysisRequestedEvent(
        String tenantId,
        String applicationId,
        String documentId,
        String analysisId,
        String actor,
        String simulateOutcome) {
}