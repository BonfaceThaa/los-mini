package com.credvenn.lm.devicecontrol;

import com.credvenn.lm.common.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "tenant_device_control_custom_notification_rules")
public class TenantDeviceControlCustomNotificationRule extends AuditableEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "tenant_config_id", nullable = false, length = 36)
    private String tenantConfigId;

    @Column(name = "days_before_due", nullable = false)
    private Integer daysBeforeDue;

    @Column(name = "notification_code", nullable = false, length = 100)
    private String notificationCode;

    @Column(length = 255)
    private String name;

    @Column(nullable = false)
    private boolean active = true;

    @PrePersist
    void assignId() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
    }

    public String getId() { return id; }
    public String getTenantConfigId() { return tenantConfigId; }
    public void setTenantConfigId(String tenantConfigId) { this.tenantConfigId = tenantConfigId; }
    public Integer getDaysBeforeDue() { return daysBeforeDue; }
    public void setDaysBeforeDue(Integer daysBeforeDue) { this.daysBeforeDue = daysBeforeDue; }
    public String getNotificationCode() { return notificationCode; }
    public void setNotificationCode(String notificationCode) { this.notificationCode = notificationCode; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
