package com.credvenn.lm.devicecontrol;

import com.credvenn.lm.common.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "loan_device_control_states")
public class LoanDeviceControlState extends AuditableEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "application_id", nullable = false, length = 36, unique = true)
    private String applicationId;

    @Column(name = "fineract_loan_id", nullable = false, length = 100)
    private String fineractLoanId;

    @Column(name = "device_id", nullable = false, length = 36)
    private String deviceId;

    @Column(name = "imei1", nullable = false, length = 100)
    private String imei1;

    @Column(name = "imei2", length = 100)
    private String imei2;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_state", nullable = false, length = 50)
    private LoanDeviceControlCurrentState currentState = LoanDeviceControlCurrentState.CLEAR;

    @Column(name = "last_due_date")
    private LocalDate lastDueDate;

    @Column(name = "next_due_date")
    private LocalDate nextDueDate;

    @Column(name = "has_overdue_installment", nullable = false)
    private boolean hasOverdueInstallment;

    @Column(name = "days_overdue")
    private Long daysOverdue;

    @Column(name = "last_collections_evaluated_at")
    private Instant lastCollectionsEvaluatedAt;

    @Column(name = "last_lock_action_at")
    private Instant lastLockActionAt;

    @Column(name = "last_unlock_action_at")
    private Instant lastUnlockActionAt;

    @Column(name = "last_notification_action_at")
    private Instant lastNotificationActionAt;

    @Column(name = "last_nudge_action_at")
    private Instant lastNudgeActionAt;

    @Column(name = "last_provider_reference", length = 255)
    private String lastProviderReference;

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
    public String getFineractLoanId() { return fineractLoanId; }
    public void setFineractLoanId(String fineractLoanId) { this.fineractLoanId = fineractLoanId; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getImei1() { return imei1; }
    public void setImei1(String imei1) { this.imei1 = imei1; }
    public String getImei2() { return imei2; }
    public void setImei2(String imei2) { this.imei2 = imei2; }
    public LoanDeviceControlCurrentState getCurrentState() { return currentState; }
    public void setCurrentState(LoanDeviceControlCurrentState currentState) { this.currentState = currentState; }
    public LocalDate getLastDueDate() { return lastDueDate; }
    public void setLastDueDate(LocalDate lastDueDate) { this.lastDueDate = lastDueDate; }
    public LocalDate getNextDueDate() { return nextDueDate; }
    public void setNextDueDate(LocalDate nextDueDate) { this.nextDueDate = nextDueDate; }
    public boolean isHasOverdueInstallment() { return hasOverdueInstallment; }
    public void setHasOverdueInstallment(boolean hasOverdueInstallment) { this.hasOverdueInstallment = hasOverdueInstallment; }
    public Long getDaysOverdue() { return daysOverdue; }
    public void setDaysOverdue(Long daysOverdue) { this.daysOverdue = daysOverdue; }
    public Instant getLastCollectionsEvaluatedAt() { return lastCollectionsEvaluatedAt; }
    public void setLastCollectionsEvaluatedAt(Instant lastCollectionsEvaluatedAt) { this.lastCollectionsEvaluatedAt = lastCollectionsEvaluatedAt; }
    public Instant getLastLockActionAt() { return lastLockActionAt; }
    public void setLastLockActionAt(Instant lastLockActionAt) { this.lastLockActionAt = lastLockActionAt; }
    public Instant getLastUnlockActionAt() { return lastUnlockActionAt; }
    public void setLastUnlockActionAt(Instant lastUnlockActionAt) { this.lastUnlockActionAt = lastUnlockActionAt; }
    public Instant getLastNotificationActionAt() { return lastNotificationActionAt; }
    public void setLastNotificationActionAt(Instant lastNotificationActionAt) { this.lastNotificationActionAt = lastNotificationActionAt; }
    public Instant getLastNudgeActionAt() { return lastNudgeActionAt; }
    public void setLastNudgeActionAt(Instant lastNudgeActionAt) { this.lastNudgeActionAt = lastNudgeActionAt; }
    public String getLastProviderReference() { return lastProviderReference; }
    public void setLastProviderReference(String lastProviderReference) { this.lastProviderReference = lastProviderReference; }
}
