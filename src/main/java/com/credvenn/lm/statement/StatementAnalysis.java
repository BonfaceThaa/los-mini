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
import java.time.Instant;
import java.util.UUID;
import com.credvenn.lm.subscription.StatementRepeatChargePolicy;
import com.credvenn.lm.subscription.StatementBillingStatus;

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

    @Column(name = "statement_otp_id", length = 36)
    private String statementOtpId;

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

    @Column(name = "raw_provider_response", columnDefinition = "LONGTEXT")
    private String rawProviderResponse;

    @Column(name = "next_status_check_at")
    private Instant nextStatusCheckAt;

    @Column(name = "last_status_check_at")
    private Instant lastStatusCheckAt;

    @Column(name = "status_check_attempts", nullable = false)
    private int statusCheckAttempts;

    @Column(name = "completion_source", length = 50)
    private String completionSource;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "billing_subscription_id", length = 36)
    private String billingSubscriptionId;
    @Column(name = "billing_plan_id", length = 36)
    private String billingPlanId;
    @Column(name = "billing_amount", precision = 19, scale = 2)
    private BigDecimal billingAmount;
    @Column(name = "billing_currency", length = 10)
    private String billingCurrency;
    @Column(name = "billing_allow_negative")
    private Boolean billingAllowNegative;
    @Enumerated(EnumType.STRING)
    @Column(name = "billing_repeat_policy", length = 40)
    private StatementRepeatChargePolicy billingRepeatPolicy;
    @Column(name = "billing_captured_at")
    private Instant billingCapturedAt;
    @Enumerated(EnumType.STRING)
    @Column(name = "billing_status", length = 30)
    private StatementBillingStatus billingStatus;

    public StatementBillingStatus getBillingStatus() { return billingStatus; }
    public void setBillingStatus(StatementBillingStatus value) { billingStatus = value; }

    public String getBillingSubscriptionId() { return billingSubscriptionId; }
    public void setBillingSubscriptionId(String value) { billingSubscriptionId = value; }
    public String getBillingPlanId() { return billingPlanId; }
    public void setBillingPlanId(String value) { billingPlanId = value; }
    public BigDecimal getBillingAmount() { return billingAmount; }
    public void setBillingAmount(BigDecimal value) { billingAmount = value; }
    public String getBillingCurrency() { return billingCurrency; }
    public void setBillingCurrency(String value) { billingCurrency = value; }
    public Boolean getBillingAllowNegative() { return billingAllowNegative; }
    public void setBillingAllowNegative(Boolean value) { billingAllowNegative = value; }
    public StatementRepeatChargePolicy getBillingRepeatPolicy() { return billingRepeatPolicy; }
    public void setBillingRepeatPolicy(StatementRepeatChargePolicy value) { billingRepeatPolicy = value; }
    public Instant getBillingCapturedAt() { return billingCapturedAt; }
    public void setBillingCapturedAt(Instant value) { billingCapturedAt = value; }

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
    public String getStatementOtpId() { return statementOtpId; }
    public void setStatementOtpId(String statementOtpId) { this.statementOtpId = statementOtpId; }
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
    public Instant getNextStatusCheckAt() { return nextStatusCheckAt; }
    public void setNextStatusCheckAt(Instant nextStatusCheckAt) { this.nextStatusCheckAt = nextStatusCheckAt; }
    public Instant getLastStatusCheckAt() { return lastStatusCheckAt; }
    public void setLastStatusCheckAt(Instant lastStatusCheckAt) { this.lastStatusCheckAt = lastStatusCheckAt; }
    public int getStatusCheckAttempts() { return statusCheckAttempts; }
    public void setStatusCheckAttempts(int statusCheckAttempts) { this.statusCheckAttempts = statusCheckAttempts; }
    public String getCompletionSource() { return completionSource; }
    public void setCompletionSource(String completionSource) { this.completionSource = completionSource; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}

