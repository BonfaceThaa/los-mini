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
import java.util.UUID;

@Entity
@Table(name = "tenant_device_control_configs")
public class TenantDeviceControlConfig extends AuditableEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "tenant_id", nullable = false, length = 36, unique = true)
    private String tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private DeviceControlProvider provider;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "base_url", nullable = false, length = 500)
    private String baseUrl;

    @Column(name = "client_code", nullable = false, length = 100)
    private String clientCode;

    @Column(name = "encrypted_username", nullable = false, columnDefinition = "TEXT")
    private String encryptedUsername;

    @Column(name = "encrypted_password", nullable = false, columnDefinition = "TEXT")
    private String encryptedPassword;

    @Column(name = "channel_code", length = 100)
    private String channelCode;

    @Column(name = "payment_link_template", length = 1000)
    private String paymentLinkTemplate;

    @Column(name = "lock_enabled", nullable = false)
    private boolean lockEnabled = true;

    @Column(name = "unlock_enabled", nullable = false)
    private boolean unlockEnabled = true;

    @Column(name = "reminders_enabled", nullable = false)
    private boolean remindersEnabled = true;

    @Column(name = "nudges_enabled", nullable = false)
    private boolean nudgesEnabled = true;

    @Column(name = "offline_pin_enabled", nullable = false)
    private boolean offlinePinEnabled = true;

    @Column(name = "overdue_lock_cadence_minutes", nullable = false)
    private Integer overdueLockCadenceMinutes = 60;

    @Column(name = "predue_cadence_minutes", nullable = false)
    private Integer predueCadenceMinutes = 1440;

    @Column(name = "last_overdue_lock_run_at")
    private Instant lastOverdueLockRunAt;

    @Column(name = "last_predue_run_at")
    private Instant lastPredueRunAt;

    @PrePersist
    void assignId() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
    }

    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public DeviceControlProvider getProvider() { return provider; }
    public void setProvider(DeviceControlProvider provider) { this.provider = provider; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getClientCode() { return clientCode; }
    public void setClientCode(String clientCode) { this.clientCode = clientCode; }
    public String getEncryptedUsername() { return encryptedUsername; }
    public void setEncryptedUsername(String encryptedUsername) { this.encryptedUsername = encryptedUsername; }
    public String getEncryptedPassword() { return encryptedPassword; }
    public void setEncryptedPassword(String encryptedPassword) { this.encryptedPassword = encryptedPassword; }
    public String getChannelCode() { return channelCode; }
    public void setChannelCode(String channelCode) { this.channelCode = channelCode; }
    public String getPaymentLinkTemplate() { return paymentLinkTemplate; }
    public void setPaymentLinkTemplate(String paymentLinkTemplate) { this.paymentLinkTemplate = paymentLinkTemplate; }
    public boolean isLockEnabled() { return lockEnabled; }
    public void setLockEnabled(boolean lockEnabled) { this.lockEnabled = lockEnabled; }
    public boolean isUnlockEnabled() { return unlockEnabled; }
    public void setUnlockEnabled(boolean unlockEnabled) { this.unlockEnabled = unlockEnabled; }
    public boolean isRemindersEnabled() { return remindersEnabled; }
    public void setRemindersEnabled(boolean remindersEnabled) { this.remindersEnabled = remindersEnabled; }
    public boolean isNudgesEnabled() { return nudgesEnabled; }
    public void setNudgesEnabled(boolean nudgesEnabled) { this.nudgesEnabled = nudgesEnabled; }
    public boolean isOfflinePinEnabled() { return offlinePinEnabled; }
    public void setOfflinePinEnabled(boolean offlinePinEnabled) { this.offlinePinEnabled = offlinePinEnabled; }
    public Integer getOverdueLockCadenceMinutes() { return overdueLockCadenceMinutes; }
    public void setOverdueLockCadenceMinutes(Integer overdueLockCadenceMinutes) { this.overdueLockCadenceMinutes = overdueLockCadenceMinutes; }
    public Integer getPredueCadenceMinutes() { return predueCadenceMinutes; }
    public void setPredueCadenceMinutes(Integer predueCadenceMinutes) { this.predueCadenceMinutes = predueCadenceMinutes; }
    public Instant getLastOverdueLockRunAt() { return lastOverdueLockRunAt; }
    public void setLastOverdueLockRunAt(Instant lastOverdueLockRunAt) { this.lastOverdueLockRunAt = lastOverdueLockRunAt; }
    public Instant getLastPredueRunAt() { return lastPredueRunAt; }
    public void setLastPredueRunAt(Instant lastPredueRunAt) { this.lastPredueRunAt = lastPredueRunAt; }
}
