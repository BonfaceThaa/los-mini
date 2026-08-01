package com.credvenn.lm.statement;

import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StatementReviewService {

    private final StatementReviewRepository statementReviewRepository;

    public StatementReviewService(StatementReviewRepository statementReviewRepository) {
        this.statementReviewRepository = statementReviewRepository;
    }

    @Transactional
    public StatementReview recordDecision(
            String tenantId,
            String applicationId,
            String statementAnalysisId,
            StatementReviewDecision decision,
            StatementReviewSource decisionSource,
            String actor,
            String reason) {
        StatementReview review = new StatementReview();
        review.setTenantId(tenantId);
        review.setApplicationId(applicationId);
        review.setStatementAnalysisId(statementAnalysisId);
        review.setDecision(decision);
        review.setDecisionSource(decisionSource);
        review.setReviewedBy(actor);
        review.setReviewedAt(Instant.now());
        review.setReason(trimToNull(reason));
        return statementReviewRepository.save(review);
    }

    @Transactional(readOnly = true)
    public Optional<StatementReview> getLatestForAnalysis(String statementAnalysisId) {
        if (statementAnalysisId == null || statementAnalysisId.isBlank()) {
            return Optional.empty();
        }
        return statementReviewRepository.findFirstByStatementAnalysisIdOrderByCreatedAtDesc(statementAnalysisId);
    }

    @Transactional(readOnly = true)
    public Optional<StatementReview> getLatestForApplication(String applicationId) {
        return statementReviewRepository.findFirstByApplicationIdOrderByCreatedAtDesc(applicationId);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
