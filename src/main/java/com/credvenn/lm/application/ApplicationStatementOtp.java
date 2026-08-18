package com.credvenn.lm.application;

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
@Table(name = "application_statement_otps")
public class ApplicationStatementOtp extends AuditableEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "application_id", nullable = false, length = 36)
    private String applicationId;

    @Column(name = "otp_encrypted", nullable = false, length = 512)
    private String otpEncrypted;

    @Column(name = "otp_masked", nullable = false, length = 32)
    private String otpMasked;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ApplicationStatementOtpStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ApplicationStatementOtpSource source;

    @Column(name = "used_for_document_id", length = 36)
    private String usedForDocumentId;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "tested_at")
    private Instant testedAt;

    @Column(name = "used_at")
    private Instant usedAt;

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
    public String getOtpEncrypted() { return otpEncrypted; }
    public void setOtpEncrypted(String otpEncrypted) { this.otpEncrypted = otpEncrypted; }
    public String getOtpMasked() { return otpMasked; }
    public void setOtpMasked(String otpMasked) { this.otpMasked = otpMasked; }
    public ApplicationStatementOtpStatus getStatus() { return status; }
    public void setStatus(ApplicationStatementOtpStatus status) { this.status = status; }
    public ApplicationStatementOtpSource getSource() { return source; }
    public void setSource(ApplicationStatementOtpSource source) { this.source = source; }
    public String getUsedForDocumentId() { return usedForDocumentId; }
    public void setUsedForDocumentId(String usedForDocumentId) { this.usedForDocumentId = usedForDocumentId; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public Instant getTestedAt() { return testedAt; }
    public void setTestedAt(Instant testedAt) { this.testedAt = testedAt; }
    public Instant getUsedAt() { return usedAt; }
    public void setUsedAt(Instant usedAt) { this.usedAt = usedAt; }
}
