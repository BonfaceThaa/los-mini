package com.credvenn.lm.loanproduct;

import com.credvenn.lm.common.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "loan_product_mappings")
public class LoanProductMapping extends AuditableEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "product_code", nullable = false, length = 100)
    private String productCode;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "short_name", nullable = false, length = 100)
    private String shortName;

    @Column(length = 1000)
    private String description;

    @Column(name = "currency_code", nullable = false, length = 10)
    private String currencyCode;

    @Column(name = "principal_min", nullable = false, precision = 19, scale = 2)
    private BigDecimal principalMin;

    @Column(name = "principal_default_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal principalDefaultAmount;

    @Column(name = "principal_max", nullable = false, precision = 19, scale = 2)
    private BigDecimal principalMax;

    @Column(name = "number_of_repayments", nullable = false)
    private Integer numberOfRepayments;

    @Column(name = "repayment_every", nullable = false)
    private Integer repaymentEvery;

    @Column(name = "repayment_frequency", nullable = false, length = 50)
    private String repaymentFrequency;

    @Column(name = "interest_rate_per_period", nullable = false, precision = 10, scale = 4)
    private BigDecimal interestRatePerPeriod;

    @Column(name = "interest_type", nullable = false, length = 50)
    private String interestType;

    @Column(name = "interest_calculation_period_type", nullable = false, length = 50)
    private String interestCalculationPeriodType;

    @Column(name = "interest_rate_frequency", nullable = false, length = 50)
    private String interestRateFrequency;

    @Column(name = "amortization_type", nullable = false, length = 50)
    private String amortizationType;

    @Column(name = "transaction_processing_strategy_code", nullable = false, length = 100)
    private String transactionProcessingStrategyCode;

    @Column(name = "accounting_template_code", nullable = false, length = 100)
    private String accountingTemplateCode;

    @Column(name = "fineract_product_id", nullable = false)
    private Long fineractProductId;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "updated_by", nullable = false)
    private String updatedBy;

    @PrePersist
    void assignId() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
    }

    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getShortName() { return shortName; }
    public void setShortName(String shortName) { this.shortName = shortName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCurrencyCode() { return currencyCode; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }
    public BigDecimal getPrincipalMin() { return principalMin; }
    public void setPrincipalMin(BigDecimal principalMin) { this.principalMin = principalMin; }
    public BigDecimal getPrincipalDefaultAmount() { return principalDefaultAmount; }
    public void setPrincipalDefaultAmount(BigDecimal principalDefaultAmount) { this.principalDefaultAmount = principalDefaultAmount; }
    public BigDecimal getPrincipalMax() { return principalMax; }
    public void setPrincipalMax(BigDecimal principalMax) { this.principalMax = principalMax; }
    public Integer getNumberOfRepayments() { return numberOfRepayments; }
    public void setNumberOfRepayments(Integer numberOfRepayments) { this.numberOfRepayments = numberOfRepayments; }
    public Integer getRepaymentEvery() { return repaymentEvery; }
    public void setRepaymentEvery(Integer repaymentEvery) { this.repaymentEvery = repaymentEvery; }
    public String getRepaymentFrequency() { return repaymentFrequency; }
    public void setRepaymentFrequency(String repaymentFrequency) { this.repaymentFrequency = repaymentFrequency; }
    public BigDecimal getInterestRatePerPeriod() { return interestRatePerPeriod; }
    public void setInterestRatePerPeriod(BigDecimal interestRatePerPeriod) { this.interestRatePerPeriod = interestRatePerPeriod; }
    public String getInterestType() { return interestType; }
    public void setInterestType(String interestType) { this.interestType = interestType; }
    public String getInterestCalculationPeriodType() { return interestCalculationPeriodType; }
    public void setInterestCalculationPeriodType(String interestCalculationPeriodType) { this.interestCalculationPeriodType = interestCalculationPeriodType; }
    public String getInterestRateFrequency() { return interestRateFrequency; }
    public void setInterestRateFrequency(String interestRateFrequency) { this.interestRateFrequency = interestRateFrequency; }
    public String getAmortizationType() { return amortizationType; }
    public void setAmortizationType(String amortizationType) { this.amortizationType = amortizationType; }
    public String getTransactionProcessingStrategyCode() { return transactionProcessingStrategyCode; }
    public void setTransactionProcessingStrategyCode(String transactionProcessingStrategyCode) { this.transactionProcessingStrategyCode = transactionProcessingStrategyCode; }
    public String getAccountingTemplateCode() { return accountingTemplateCode; }
    public void setAccountingTemplateCode(String accountingTemplateCode) { this.accountingTemplateCode = accountingTemplateCode; }
    public Long getFineractProductId() { return fineractProductId; }
    public void setFineractProductId(Long fineractProductId) { this.fineractProductId = fineractProductId; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
