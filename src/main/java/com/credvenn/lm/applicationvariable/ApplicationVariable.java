package com.credvenn.lm.applicationvariable;

import com.credvenn.lm.common.domain.AuditableEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "application_variables")
public class ApplicationVariable extends AuditableEntity {
    @Id @Column(length = 36) private String id;
    @Column(name = "tenant_id", nullable = false, length = 36) private String tenantId;
    @Column(name = "application_id", nullable = false, length = 36) private String applicationId;
    @Column(name = "definition_id", nullable = false, length = 36) private String definitionId;
    @Column(name = "definition_version", nullable = false) private int definitionVersion;
    @Column(name = "code_snapshot", nullable = false, length = 100) private String codeSnapshot;
    @Column(name = "label_snapshot", nullable = false) private String labelSnapshot;
    @Column(name = "section_snapshot") private String sectionSnapshot;
    @Enumerated(EnumType.STRING) @Column(name = "field_type_snapshot", nullable = false, length = 30) private ApplicationVariableFieldType fieldTypeSnapshot;
    @Column(name = "answer_value", nullable = false, columnDefinition = "TEXT") private String answerValue;

    @PrePersist void assignId() { if (id == null) id = UUID.randomUUID().toString(); }
    public String getId() { return id; }
    public String getTenantId() { return tenantId; } public void setTenantId(String value) { tenantId = value; }
    public String getApplicationId() { return applicationId; } public void setApplicationId(String value) { applicationId = value; }
    public String getDefinitionId() { return definitionId; } public void setDefinitionId(String value) { definitionId = value; }
    public int getDefinitionVersion() { return definitionVersion; } public void setDefinitionVersion(int value) { definitionVersion = value; }
    public String getCodeSnapshot() { return codeSnapshot; } public void setCodeSnapshot(String value) { codeSnapshot = value; }
    public String getLabelSnapshot() { return labelSnapshot; } public void setLabelSnapshot(String value) { labelSnapshot = value; }
    public String getSectionSnapshot() { return sectionSnapshot; } public void setSectionSnapshot(String value) { sectionSnapshot = value; }
    public ApplicationVariableFieldType getFieldTypeSnapshot() { return fieldTypeSnapshot; } public void setFieldTypeSnapshot(ApplicationVariableFieldType value) { fieldTypeSnapshot = value; }
    public String getAnswerValue() { return answerValue; } public void setAnswerValue(String value) { answerValue = value; }
}