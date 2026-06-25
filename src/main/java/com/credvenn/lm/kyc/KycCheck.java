package com.credvenn.lm.kyc;

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
@Table(name = "application_kyc_checks")
public class KycCheck extends AuditableEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "application_id", nullable = false, length = 36)
    private String applicationId;

    @Column(nullable = false, length = 100)
    private String provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private KycStatus status;

    @Column(name = "provider_reference")
    private String providerReference;

    @Column(length = 1000)
    private String summary;

    @Column(name = "action_names", length = 100)
    private String actionNames;

    @Column(name = "action_first_name", length = 100)
    private String actionFirstName;

    @Column(name = "action_last_name", length = 100)
    private String actionLastName;

    @Column(name = "action_other_names", length = 100)
    private String actionOtherNames;

    @Column(name = "action_dob", length = 100)
    private String actionDob;

    @Column(name = "action_gender", length = 100)
    private String actionGender;

    @Column(name = "action_phone_number", length = 100)
    private String actionPhoneNumber;

    @Column(name = "action_verify_id_number", length = 100)
    private String actionVerifyIdNumber;

    @Column(name = "action_id_verification", length = 100)
    private String actionIdVerification;

    @Column(name = "reviewed_by")
    private String reviewedBy;

    @Column(name = "review_reason", length = 1000)
    private String reviewReason;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

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
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public KycStatus getStatus() { return status; }
    public void setStatus(KycStatus status) { this.status = status; }
    public String getProviderReference() { return providerReference; }
    public void setProviderReference(String providerReference) { this.providerReference = providerReference; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getActionNames() { return actionNames; }
    public void setActionNames(String actionNames) { this.actionNames = actionNames; }
    public String getActionFirstName() { return actionFirstName; }
    public void setActionFirstName(String actionFirstName) { this.actionFirstName = actionFirstName; }
    public String getActionLastName() { return actionLastName; }
    public void setActionLastName(String actionLastName) { this.actionLastName = actionLastName; }
    public String getActionOtherNames() { return actionOtherNames; }
    public void setActionOtherNames(String actionOtherNames) { this.actionOtherNames = actionOtherNames; }
    public String getActionDob() { return actionDob; }
    public void setActionDob(String actionDob) { this.actionDob = actionDob; }
    public String getActionGender() { return actionGender; }
    public void setActionGender(String actionGender) { this.actionGender = actionGender; }
    public String getActionPhoneNumber() { return actionPhoneNumber; }
    public void setActionPhoneNumber(String actionPhoneNumber) { this.actionPhoneNumber = actionPhoneNumber; }
    public String getActionVerifyIdNumber() { return actionVerifyIdNumber; }
    public void setActionVerifyIdNumber(String actionVerifyIdNumber) { this.actionVerifyIdNumber = actionVerifyIdNumber; }
    public String getActionIdVerification() { return actionIdVerification; }
    public void setActionIdVerification(String actionIdVerification) { this.actionIdVerification = actionIdVerification; }
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    public String getReviewReason() { return reviewReason; }
    public void setReviewReason(String reviewReason) { this.reviewReason = reviewReason; }
    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }
}
