package com.credvenn.lm.subscription;

import com.credvenn.lm.common.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "subscription_plans")
public class SubscriptionPlan extends AuditableEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "plan_code", nullable = false, unique = true, length = 100)
    private String planCode;

    @Column(nullable = false)
    private String name;

    @Column
    private String description;

    @Column(name = "max_users", nullable = false)
    private int maxUsers;

    @Column(name = "max_branches", nullable = false)
    private int maxBranches;

    @Column(name = "monthly_application_limit", nullable = false)
    private int monthlyApplicationLimit;

    @Column(name = "approved_application_threshold", nullable = false)
    private int approvedApplicationThreshold;

    @Column(name = "monthly_fee", nullable = false, precision = 19, scale = 2)
    private BigDecimal monthlyFee;

    @Column(name = "interest_share_percentage", nullable = false, precision = 10, scale = 2)
    private BigDecimal interestSharePercentage;

    @Column(name = "kyc_success_cost", nullable = false, precision = 19, scale = 2)
    private BigDecimal kycSuccessCost;

    @Column(name = "statement_success_cost", nullable = false, precision = 19, scale = 2)
    private BigDecimal statementSuccessCost;

    @Column(nullable = false, length = 10)
    private String currency;

    @Column(nullable = false)
    private boolean active = true;

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

    public String getId() {
        return id;
    }

    public String getPlanCode() {
        return planCode;
    }

    public void setPlanCode(String planCode) {
        this.planCode = planCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getMaxUsers() {
        return maxUsers;
    }

    public void setMaxUsers(int maxUsers) {
        this.maxUsers = maxUsers;
    }

    public int getMaxBranches() {
        return maxBranches;
    }

    public void setMaxBranches(int maxBranches) {
        this.maxBranches = maxBranches;
    }

    public int getMonthlyApplicationLimit() {
        return monthlyApplicationLimit;
    }

    public void setMonthlyApplicationLimit(int monthlyApplicationLimit) {
        this.monthlyApplicationLimit = monthlyApplicationLimit;
    }

    public int getApprovedApplicationThreshold() {
        return approvedApplicationThreshold;
    }

    public void setApprovedApplicationThreshold(int approvedApplicationThreshold) {
        this.approvedApplicationThreshold = approvedApplicationThreshold;
    }

    public BigDecimal getMonthlyFee() {
        return monthlyFee;
    }

    public void setMonthlyFee(BigDecimal monthlyFee) {
        this.monthlyFee = monthlyFee;
    }

    public BigDecimal getInterestSharePercentage() {
        return interestSharePercentage;
    }

    public void setInterestSharePercentage(BigDecimal interestSharePercentage) {
        this.interestSharePercentage = interestSharePercentage;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getKycSuccessCost() {
        return kycSuccessCost;
    }

    public void setKycSuccessCost(BigDecimal kycSuccessCost) {
        this.kycSuccessCost = kycSuccessCost;
    }

    public BigDecimal getStatementSuccessCost() {
        return statementSuccessCost;
    }

    public void setStatementSuccessCost(BigDecimal statementSuccessCost) {
        this.statementSuccessCost = statementSuccessCost;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }
}
