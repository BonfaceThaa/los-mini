package com.credvenn.lm.statementinbox;

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
@Table(name = "inbound_statement_receipts")
public class InboundStatementReceipt extends AuditableEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "tenant_id", length = 36)
    private String tenantId;

    @Column(name = "destination_email", nullable = false)
    private String destinationEmail;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "stored_filename", nullable = false)
    private String storedFilename;

    @Column(name = "relative_path", nullable = false, length = 1000)
    private String relativePath;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "message_id")
    private String messageId;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "extracted_phone_token", length = 100)
    private String extractedPhoneToken;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_status", nullable = false, length = 50)
    private InboundStatementMatchStatus matchStatus;

    @Column(name = "matched_application_id", length = 36)
    private String matchedApplicationId;

    @Column(name = "matched_document_id", length = 36)
    private String matchedDocumentId;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "review_notes", length = 1000)
    private String reviewNotes;

    @Column(name = "resolved_by")
    private String resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "processing_started_at")
    private Instant processingStartedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "background_error", length = 2000)
    private String backgroundError;

    @PrePersist
    void assignId() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getDestinationEmail() { return destinationEmail; }
    public void setDestinationEmail(String destinationEmail) { this.destinationEmail = destinationEmail; }
    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }
    public String getStoredFilename() { return storedFilename; }
    public void setStoredFilename(String storedFilename) { this.storedFilename = storedFilename; }
    public String getRelativePath() { return relativePath; }
    public void setRelativePath(String relativePath) { this.relativePath = relativePath; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }
    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }
    public Instant getReceivedAt() { return receivedAt; }
    public void setReceivedAt(Instant receivedAt) { this.receivedAt = receivedAt; }
    public String getExtractedPhoneToken() { return extractedPhoneToken; }
    public void setExtractedPhoneToken(String extractedPhoneToken) { this.extractedPhoneToken = extractedPhoneToken; }
    public InboundStatementMatchStatus getMatchStatus() { return matchStatus; }
    public void setMatchStatus(InboundStatementMatchStatus matchStatus) { this.matchStatus = matchStatus; }
    public String getMatchedApplicationId() { return matchedApplicationId; }
    public void setMatchedApplicationId(String matchedApplicationId) { this.matchedApplicationId = matchedApplicationId; }
    public String getMatchedDocumentId() { return matchedDocumentId; }
    public void setMatchedDocumentId(String matchedDocumentId) { this.matchedDocumentId = matchedDocumentId; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public String getReviewNotes() { return reviewNotes; }
    public void setReviewNotes(String reviewNotes) { this.reviewNotes = reviewNotes; }
    public String getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(String resolvedBy) { this.resolvedBy = resolvedBy; }
    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }
    public Instant getProcessingStartedAt() { return processingStartedAt; }
    public void setProcessingStartedAt(Instant processingStartedAt) { this.processingStartedAt = processingStartedAt; }
    public Instant getProcessedAt() { return processedAt; }
    public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }
    public String getBackgroundError() { return backgroundError; }
    public void setBackgroundError(String backgroundError) { this.backgroundError = backgroundError; }
}
