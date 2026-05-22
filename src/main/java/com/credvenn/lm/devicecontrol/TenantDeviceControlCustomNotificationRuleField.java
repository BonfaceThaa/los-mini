package com.credvenn.lm.devicecontrol;

import com.credvenn.lm.common.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "tenant_device_control_custom_notification_rule_fields")
public class TenantDeviceControlCustomNotificationRuleField extends AuditableEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "custom_rule_id", nullable = false, length = 36)
    private String customRuleId;

    @Column(name = "column_name", nullable = false, length = 100)
    private String columnName;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_field", nullable = false, length = 50)
    private CustomNotificationSourceField sourceField;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @PrePersist
    void assignId() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
    }

    public String getId() { return id; }
    public String getCustomRuleId() { return customRuleId; }
    public void setCustomRuleId(String customRuleId) { this.customRuleId = customRuleId; }
    public String getColumnName() { return columnName; }
    public void setColumnName(String columnName) { this.columnName = columnName; }
    public CustomNotificationSourceField getSourceField() { return sourceField; }
    public void setSourceField(CustomNotificationSourceField sourceField) { this.sourceField = sourceField; }
    public Integer getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(Integer displayOrder) { this.displayOrder = displayOrder; }
}
