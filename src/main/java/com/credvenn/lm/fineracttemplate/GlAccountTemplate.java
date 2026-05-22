package com.credvenn.lm.fineracttemplate;

import com.credvenn.lm.common.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "gl_account_templates")
public class GlAccountTemplate extends AuditableEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "template_code", nullable = false, unique = true, length = 100)
    private String templateCode;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "business_purpose", nullable = false, length = 100)
    private String businessPurpose;

    @Column(name = "source_type", nullable = false, length = 50)
    private String sourceType;

    @Column(name = "management_mode", nullable = false, length = 50)
    private String managementMode;

    @Column(name = "fineract_gl_account_id", nullable = false)
    private Long fineractGlAccountId;

    @Column(name = "fineract_gl_code", nullable = false, length = 100)
    private String fineractGlCode;

    @Column(name = "account_category", nullable = false, length = 50)
    private String accountCategory;

    @Column(name = "usage_type", nullable = false, length = 50)
    private String usageType;

    @Column(name = "manual_entries_allowed", nullable = false)
    private boolean manualEntriesAllowed;

    @Column(length = 1000)
    private String description;

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
    public String getTemplateCode() { return templateCode; }
    public void setTemplateCode(String templateCode) { this.templateCode = templateCode; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getBusinessPurpose() { return businessPurpose; }
    public void setBusinessPurpose(String businessPurpose) { this.businessPurpose = businessPurpose; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getManagementMode() { return managementMode; }
    public void setManagementMode(String managementMode) { this.managementMode = managementMode; }
    public Long getFineractGlAccountId() { return fineractGlAccountId; }
    public void setFineractGlAccountId(Long fineractGlAccountId) { this.fineractGlAccountId = fineractGlAccountId; }
    public String getFineractGlCode() { return fineractGlCode; }
    public void setFineractGlCode(String fineractGlCode) { this.fineractGlCode = fineractGlCode; }
    public String getAccountCategory() { return accountCategory; }
    public void setAccountCategory(String accountCategory) { this.accountCategory = accountCategory; }
    public String getUsageType() { return usageType; }
    public void setUsageType(String usageType) { this.usageType = usageType; }
    public boolean isManualEntriesAllowed() { return manualEntriesAllowed; }
    public void setManualEntriesAllowed(boolean manualEntriesAllowed) { this.manualEntriesAllowed = manualEntriesAllowed; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
