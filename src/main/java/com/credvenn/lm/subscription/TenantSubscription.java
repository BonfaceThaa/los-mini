package com.credvenn.lm.subscription;

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
import java.math.BigDecimal;

@Entity
@Table(name = "tenant_subscriptions")
public class TenantSubscription extends AuditableEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "subscription_plan_id", nullable = false, length = 36)
    private String subscriptionPlanId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TenantSubscriptionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "pricing_mode", nullable = false, length = 20)
    private SubscriptionPricingMode pricingMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "next_pricing_mode", length = 20)
    private SubscriptionPricingMode nextPricingMode;

    @Column(name = "current_period_start", nullable = false)
    private Instant currentPeriodStart;

    @Column(name = "current_period_end", nullable = false)
    private Instant currentPeriodEnd;

    @Column(name = "switched_to_interest_share_at")
    private Instant switchedToInterestShareAt;

    @Column(name = "next_pricing_mode_effective_at")
    private Instant nextPricingModeEffectiveAt;

    @Column(name = "operational_notes", length = 1000)
    private String operationalNotes;

    @Column(name = "prepaid_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal prepaidBalance;

    @Column(name = "total_credited", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalCredited;

    @Column(name = "total_debited", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalDebited;

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

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getSubscriptionPlanId() {
        return subscriptionPlanId;
    }

    public void setSubscriptionPlanId(String subscriptionPlanId) {
        this.subscriptionPlanId = subscriptionPlanId;
    }

    public TenantSubscriptionStatus getStatus() {
        return status;
    }

    public void setStatus(TenantSubscriptionStatus status) {
        this.status = status;
    }

    public SubscriptionPricingMode getPricingMode() {
        return pricingMode;
    }

    public void setPricingMode(SubscriptionPricingMode pricingMode) {
        this.pricingMode = pricingMode;
    }

    public SubscriptionPricingMode getNextPricingMode() {
        return nextPricingMode;
    }

    public void setNextPricingMode(SubscriptionPricingMode nextPricingMode) {
        this.nextPricingMode = nextPricingMode;
    }

    public Instant getCurrentPeriodStart() {
        return currentPeriodStart;
    }

    public void setCurrentPeriodStart(Instant currentPeriodStart) {
        this.currentPeriodStart = currentPeriodStart;
    }

    public Instant getCurrentPeriodEnd() {
        return currentPeriodEnd;
    }

    public void setCurrentPeriodEnd(Instant currentPeriodEnd) {
        this.currentPeriodEnd = currentPeriodEnd;
    }

    public Instant getSwitchedToInterestShareAt() {
        return switchedToInterestShareAt;
    }

    public void setSwitchedToInterestShareAt(Instant switchedToInterestShareAt) {
        this.switchedToInterestShareAt = switchedToInterestShareAt;
    }

    public Instant getNextPricingModeEffectiveAt() {
        return nextPricingModeEffectiveAt;
    }

    public void setNextPricingModeEffectiveAt(Instant nextPricingModeEffectiveAt) {
        this.nextPricingModeEffectiveAt = nextPricingModeEffectiveAt;
    }

    public String getOperationalNotes() {
        return operationalNotes;
    }

    public void setOperationalNotes(String operationalNotes) {
        this.operationalNotes = operationalNotes;
    }

    public BigDecimal getPrepaidBalance() {
        return prepaidBalance;
    }

    public void setPrepaidBalance(BigDecimal prepaidBalance) {
        this.prepaidBalance = prepaidBalance;
    }

    public BigDecimal getTotalCredited() {
        return totalCredited;
    }

    public void setTotalCredited(BigDecimal totalCredited) {
        this.totalCredited = totalCredited;
    }

    public BigDecimal getTotalDebited() {
        return totalDebited;
    }

    public void setTotalDebited(BigDecimal totalDebited) {
        this.totalDebited = totalDebited;
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
