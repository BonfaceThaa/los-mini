package com.credvenn.lm.document;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public final class DocumentDtos {

    private DocumentDtos() {
    }

    @Schema(name = "ApplicationDocumentResponse")
    public record ApplicationDocumentResponse(
            String id,
            String applicationId,
            String documentType,
            String originalFilename,
            String contentType,
            long fileSize,
            String contentUrl,
            Instant createdAt) {
    }
}
