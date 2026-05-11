package com.credvenn.lm.inventory;

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
@Table(name = "inventory_devices")
public class InventoryDevice extends AuditableEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "serial_number", nullable = false, length = 100)
    private String serialNumber;

    @Column(name = "device_name", nullable = false)
    private String deviceName;

    @Column(name = "imei1", length = 100)
    private String imei1;

    @Column(name = "imei2", length = 100)
    private String imei2;

    @Column(name = "cash_price", precision = 19, scale = 2)
    private BigDecimal cashPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "deposit_type", length = 30)
    private DepositType depositType;

    @Column(name = "deposit_value", precision = 19, scale = 2)
    private BigDecimal depositValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private InventoryDeviceStatus status;

    @PrePersist
    void assignId() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
    }

    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }
    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }
    public String getImei1() { return imei1; }
    public void setImei1(String imei1) { this.imei1 = imei1; }
    public String getImei2() { return imei2; }
    public void setImei2(String imei2) { this.imei2 = imei2; }
    public BigDecimal getCashPrice() { return cashPrice; }
    public void setCashPrice(BigDecimal cashPrice) { this.cashPrice = cashPrice; }
    public DepositType getDepositType() { return depositType; }
    public void setDepositType(DepositType depositType) { this.depositType = depositType; }
    public BigDecimal getDepositValue() { return depositValue; }
    public void setDepositValue(BigDecimal depositValue) { this.depositValue = depositValue; }
    public InventoryDeviceStatus getStatus() { return status; }
    public void setStatus(InventoryDeviceStatus status) { this.status = status; }
}
