package com.credvenn.lm.statement;

import com.credvenn.lm.common.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "application_statement_reviews")
public class StatementReview extends AuditableEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "application_id", nullable = false, length = 36)
    private String applicationId;

    @Column(name = "statement_analysis_id", length = 36)
    private String statementAnalysisId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private StatementReviewDecision decision;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision_source", nullable = false, length = 20)
    private StatementReviewSource decisionSource;

    @Column(length = 1000)
    private String reason;

    @Column(name = "reviewed_by", nullable = false, length = 100)
    private String reviewedBy;

    @Column(name = "reviewed_at", nullable = false)
    private Instant reviewedAt;

    @PrePersist
    void assignId() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
    }

    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getApplicationId() { return applicationId; }
    public void setApplicationId(String applicationId) { this.applicationId = applicationId; }
    public String getStatementAnalysisId() { return statementAnalysisId; }
    public void setStatementAnalysisId(String statementAnalysisId) { this.statementAnalysisId = statementAnalysisId; }
    public StatementReviewDecision getDecision() { return decision; }
    public void setDecision(StatementReviewDecision decision) { this.decision = decision; }
    public StatementReviewSource getDecisionSource() { return decisionSource; }
    public void setDecisionSource(StatementReviewSource decisionSource) { this.decisionSource = decisionSource; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }
}
