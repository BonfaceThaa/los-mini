package com.credvenn.lm.statementinbox;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;

public final class InboundStatementDtos {

    private InboundStatementDtos() {
    }

    public record InboundReceiptAcceptedResponse(
            String receiptId,
            InboundStatementMatchStatus status,
            String message) {
    }

    public record InboundStatementReceiptResponse(
            String id,
            String tenantId,
            String destinationEmail,
            String originalFilename,
            String contentType,
            long fileSize,
            String messageId,
            Instant receivedAt,
            String extractedPhoneToken,
            InboundStatementMatchStatus matchStatus,
            String matchedApplicationId,
            String matchedDocumentId,
            String failureReason,
            String reviewNotes,
            String resolvedBy,
            Instant resolvedAt,
            Instant processingStartedAt,
            Instant processedAt,
            String contentUrl,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record ResolveInboundStatementReceiptRequest(
            @NotBlank String applicationId,
            String notes) {
    }

    public record StatementInboxResponse(
            List<InboundStatementReceiptResponse> receipts) {
    }
}
