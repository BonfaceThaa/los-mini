package com.credvenn.lm.statement;

public record StatementDocumentUploadedEvent(
        String tenantId,
        String applicationId,
        String documentId,
        String documentType,
        String actor) {
}
