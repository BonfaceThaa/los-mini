package com.credvenn.lm.statement;

public record StatementAnalysisSubmission(
        String provider,
        String providerStatus,
        String externalClientId,
        String externalDocumentId,
        String externalBusinessId,
        String summary,
        String rawProviderResponse,
        Integer uploadCreditScore,
        String uploadRiskTier,
        java.time.Instant uploadScoredAt) {
}
