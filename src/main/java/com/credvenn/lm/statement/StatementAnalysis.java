package com.credvenn.lm.statement;

import com.credvenn.lm.common.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "application_statement_analyses")
public class StatementAnalysis extends AuditableEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "application_id", nullable = false, length = 36)
    private String applicationId;

    @Column(nullable = false, length = 100)
    private String provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private StatementAnalysisStatus status;

    @Column(name = "source_document_id", nullable = false, length = 36)
    private String sourceDocumentId;

    @Column(length = 1000)
    private String summary;

    @Column(name = "average_monthly_inflow", precision = 19, scale = 2)
    private BigDecimal averageMonthlyInflow;

    @Column(name = "average_monthly_outflow", precision = 19, scale = 2)
    private BigDecimal averageMonthlyOutflow;

    @Column(name = "affordability_score", precision = 10, scale = 2)
    private BigDecimal affordabilityScore;

    @Column(length = 100)
    private String recommendation;

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
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public StatementAnalysisStatus getStatus() { return status; }
    public void setStatus(StatementAnalysisStatus status) { this.status = status; }
    public String getSourceDocumentId() { return sourceDocumentId; }
    public void setSourceDocumentId(String sourceDocumentId) { this.sourceDocumentId = sourceDocumentId; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public BigDecimal getAverageMonthlyInflow() { return averageMonthlyInflow; }
    public void setAverageMonthlyInflow(BigDecimal averageMonthlyInflow) { this.averageMonthlyInflow = averageMonthlyInflow; }
    public BigDecimal getAverageMonthlyOutflow() { return averageMonthlyOutflow; }
    public void setAverageMonthlyOutflow(BigDecimal averageMonthlyOutflow) { this.averageMonthlyOutflow = averageMonthlyOutflow; }
    public BigDecimal getAffordabilityScore() { return affordabilityScore; }
    public void setAffordabilityScore(BigDecimal affordabilityScore) { this.affordabilityScore = affordabilityScore; }
    public String getRecommendation() { return recommendation; }
    public void setRecommendation(String recommendation) { this.recommendation = recommendation; }
}
