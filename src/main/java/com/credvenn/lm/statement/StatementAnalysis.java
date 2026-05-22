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

    @Column(name = "source_document_id", length = 36)
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

    @Column(name = "provider_status", length = 100)
    private String providerStatus;

    @Column(name = "external_client_id", length = 100)
    private String externalClientId;

    @Column(name = "external_document_id", length = 100)
    private String externalDocumentId;

    @Column(name = "external_business_id", length = 100)
    private String externalBusinessId;

    @Column(name = "credit_score")
    private Integer creditScore;

    @Column(name = "risk_tier", length = 100)
    private String riskTier;

    @Column(name = "raw_provider_response", columnDefinition = "TEXT")
    private String rawProviderResponse;

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
    public String getProviderStatus() { return providerStatus; }
    public void setProviderStatus(String providerStatus) { this.providerStatus = providerStatus; }
    public String getExternalClientId() { return externalClientId; }
    public void setExternalClientId(String externalClientId) { this.externalClientId = externalClientId; }
    public String getExternalDocumentId() { return externalDocumentId; }
    public void setExternalDocumentId(String externalDocumentId) { this.externalDocumentId = externalDocumentId; }
    public String getExternalBusinessId() { return externalBusinessId; }
    public void setExternalBusinessId(String externalBusinessId) { this.externalBusinessId = externalBusinessId; }
    public Integer getCreditScore() { return creditScore; }
    public void setCreditScore(Integer creditScore) { this.creditScore = creditScore; }
    public String getRiskTier() { return riskTier; }
    public void setRiskTier(String riskTier) { this.riskTier = riskTier; }
    public String getRawProviderResponse() { return rawProviderResponse; }
    public void setRawProviderResponse(String rawProviderResponse) { this.rawProviderResponse = rawProviderResponse; }
}
