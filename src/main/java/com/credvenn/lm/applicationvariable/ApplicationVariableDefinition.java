package com.credvenn.lm.applicationvariable;

import com.credvenn.lm.common.domain.AuditableEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "application_variable_definitions")
public class ApplicationVariableDefinition extends AuditableEntity {
    @Id @Column(length = 36) private String id;
    @Column(name = "tenant_id", nullable = false, length = 36) private String tenantId;
    @Column(name = "origination_profile_id", length = 36) private String originationProfileId;
    @Column(nullable = false, length = 100) private String code;
    @Column(nullable = false) private String label;
    @Column(name = "section_name") private String sectionName;
    @Enumerated(EnumType.STRING) @Column(name = "field_type", nullable = false, length = 30) private ApplicationVariableFieldType fieldType;
    @Column(nullable = false) private boolean required;
    @Column(nullable = false) private boolean active = true;
    @Column(name = "display_order", nullable = false) private int displayOrder;
    @Column(name = "definition_version", nullable = false) private int definitionVersion = 1;
    @Column(name = "minimum_selections") private Integer minimumSelections;
    @Column(name = "maximum_selections") private Integer maximumSelections;
    @Column(name = "options_json", columnDefinition = "TEXT") private String optionsJson;

    @PrePersist void assignId() { if (id == null) id = UUID.randomUUID().toString(); }
    public String getId() { return id; }
    public String getTenantId() { return tenantId; } public void setTenantId(String value) { tenantId = value; }
    public String getOriginationProfileId() { return originationProfileId; }
    public void setOriginationProfileId(String value) { originationProfileId = value; }
    public String getCode() { return code; } public void setCode(String value) { code = value; }
    public String getLabel() { return label; } public void setLabel(String value) { label = value; }
    public String getSectionName() { return sectionName; } public void setSectionName(String value) { sectionName = value; }
    public ApplicationVariableFieldType getFieldType() { return fieldType; } public void setFieldType(ApplicationVariableFieldType value) { fieldType = value; }
    public boolean isRequired() { return required; } public void setRequired(boolean value) { required = value; }
    public boolean isActive() { return active; } public void setActive(boolean value) { active = value; }
    public int getDisplayOrder() { return displayOrder; } public void setDisplayOrder(int value) { displayOrder = value; }
    public int getDefinitionVersion() { return definitionVersion; } public void setDefinitionVersion(int value) { definitionVersion = value; }
    public Integer getMinimumSelections() { return minimumSelections; } public void setMinimumSelections(Integer value) { minimumSelections = value; }
    public Integer getMaximumSelections() { return maximumSelections; } public void setMaximumSelections(Integer value) { maximumSelections = value; }
    public String getOptionsJson() { return optionsJson; } public void setOptionsJson(String value) { optionsJson = value; }
}