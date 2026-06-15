package com.credvenn.lm.fineracttemplate;

import com.credvenn.lm.common.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "accounting_rule_templates")
public class AccountingRuleTemplate extends AuditableEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "tenant_id", length = 36)
    private String tenantId;

    @Column(name = "template_code", nullable = false, length = 100)
    private String templateCode;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "business_event", nullable = false, length = 100)
    private String businessEvent;

    @Column(name = "source_type", nullable = false, length = 50)
    private String sourceType;

    @Column(name = "management_mode", nullable = false, length = 50)
    private String managementMode;

    @Column(name = "fineract_rule_id", nullable = false)
    private Long fineractRuleId;

    @Column(name = "fineract_rule_name", nullable = false)
    private String fineractRuleName;

    @Column(name = "debit_gl_account_template_code", nullable = false, length = 100)
    private String debitGlAccountTemplateCode;

    @Column(name = "credit_gl_account_template_code", nullable = false, length = 100)
    private String creditGlAccountTemplateCode;

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
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getTemplateCode() { return templateCode; }
    public void setTemplateCode(String templateCode) { this.templateCode = templateCode; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getBusinessEvent() { return businessEvent; }
    public void setBusinessEvent(String businessEvent) { this.businessEvent = businessEvent; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getManagementMode() { return managementMode; }
    public void setManagementMode(String managementMode) { this.managementMode = managementMode; }
    public Long getFineractRuleId() { return fineractRuleId; }
    public void setFineractRuleId(Long fineractRuleId) { this.fineractRuleId = fineractRuleId; }
    public String getFineractRuleName() { return fineractRuleName; }
    public void setFineractRuleName(String fineractRuleName) { this.fineractRuleName = fineractRuleName; }
    public String getDebitGlAccountTemplateCode() { return debitGlAccountTemplateCode; }
    public void setDebitGlAccountTemplateCode(String debitGlAccountTemplateCode) { this.debitGlAccountTemplateCode = debitGlAccountTemplateCode; }
    public String getCreditGlAccountTemplateCode() { return creditGlAccountTemplateCode; }
    public void setCreditGlAccountTemplateCode(String creditGlAccountTemplateCode) { this.creditGlAccountTemplateCode = creditGlAccountTemplateCode; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
