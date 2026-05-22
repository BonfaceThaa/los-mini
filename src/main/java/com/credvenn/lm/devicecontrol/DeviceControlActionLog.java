package com.credvenn.lm.devicecontrol;

import com.credvenn.lm.common.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "device_control_action_logs")
public class DeviceControlActionLog extends AuditableEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "application_id", nullable = false, length = 36)
    private String applicationId;

    @Column(name = "fineract_loan_id", nullable = false, length = 100)
    private String fineractLoanId;

    @Column(name = "device_id", nullable = false, length = 36)
    private String deviceId;

    @Column(name = "imei1", nullable = false, length = 100)
    private String imei1;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 50)
    private DeviceControlActionType actionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 50)
    private DeviceControlTriggerType triggerType;

    @Column(name = "rule_id", length = 36)
    private String ruleId;

    @Column(name = "rule_day_offset")
    private Integer ruleDayOffset;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "provider_transaction_id", length = 255)
    private String providerTransactionId;

    @Column(name = "request_payload", columnDefinition = "LONGTEXT")
    private String requestPayload;

    @Column(name = "response_payload", columnDefinition = "LONGTEXT")
    private String responsePayload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private DeviceControlActionStatus status = DeviceControlActionStatus.PENDING;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "requested_by")
    private String requestedBy;

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
    public DeviceControlActionType getActionType() { return actionType; }
    public void setActionType(DeviceControlActionType actionType) { this.actionType = actionType; }
    public DeviceControlTriggerType getTriggerType() { return triggerType; }
    public void setTriggerType(DeviceControlTriggerType triggerType) { this.triggerType = triggerType; }
    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }
    public Integer getRuleDayOffset() { return ruleDayOffset; }
    public void setRuleDayOffset(Integer ruleDayOffset) { this.ruleDayOffset = ruleDayOffset; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public String getProviderTransactionId() { return providerTransactionId; }
    public void setProviderTransactionId(String providerTransactionId) { this.providerTransactionId = providerTransactionId; }
    public String getRequestPayload() { return requestPayload; }
    public void setRequestPayload(String requestPayload) { this.requestPayload = requestPayload; }
    public String getResponsePayload() { return responsePayload; }
    public void setResponsePayload(String responsePayload) { this.responsePayload = responsePayload; }
    public DeviceControlActionStatus getStatus() { return status; }
    public void setStatus(DeviceControlActionStatus status) { this.status = status; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }
}
