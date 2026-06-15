package com.credvenn.lm.client;

import com.credvenn.lm.common.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "client_records")
public class ClientRecord extends AuditableEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "application_id", nullable = false, length = 36)
    private String applicationId;

    @Column(name = "fineract_client_id", nullable = false, length = 100)
    private String fineractClientId;

    @Column(name = "account_no", length = 100)
    private String accountNo;

    @Column(name = "external_id", length = 100)
    private String externalId;

    @Column(name = "status", length = 100)
    private String status;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "firstname", length = 255)
    private String firstname;

    @Column(name = "middlename", length = 255)
    private String middlename;

    @Column(name = "lastname", length = 255)
    private String lastname;

    @Column(name = "display_name", length = 255)
    private String displayName;

    @Column(name = "mobile_no", length = 100)
    private String mobileNo;

    @Column(name = "office_name", length = 255)
    private String officeName;

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
    public String getFineractClientId() { return fineractClientId; }
    public void setFineractClientId(String fineractClientId) { this.fineractClientId = fineractClientId; }
    public String getAccountNo() { return accountNo; }
    public void setAccountNo(String accountNo) { this.accountNo = accountNo; }
    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getFirstname() { return firstname; }
    public void setFirstname(String firstname) { this.firstname = firstname; }
    public String getMiddlename() { return middlename; }
    public void setMiddlename(String middlename) { this.middlename = middlename; }
    public String getLastname() { return lastname; }
    public void setLastname(String lastname) { this.lastname = lastname; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getMobileNo() { return mobileNo; }
    public void setMobileNo(String mobileNo) { this.mobileNo = mobileNo; }
    public String getOfficeName() { return officeName; }
    public void setOfficeName(String officeName) { this.officeName = officeName; }
}
