package com.credvenn.lm.document;

import com.credvenn.lm.security.CurrentActorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@SecurityRequirement(name = "bearerAuth")
public class DocumentController {

    private final DocumentService documentService;
    private final CurrentActorService currentActorService;

    public DocumentController(DocumentService documentService, CurrentActorService currentActorService) {
        this.documentService = documentService;
        this.currentActorService = currentActorService;
    }

    @PostMapping("/api/v1/applications/{applicationId}/documents")
    @PreAuthorize("hasAuthority('DOCUMENT_UPLOAD')")
    @Tag(name = "Documents")
    @Operation(summary = "Upload an authenticated application document to local storage")
    public ResponseEntity<DocumentDtos.ApplicationDocumentResponse> upload(
            @PathVariable String applicationId,
            @RequestParam("documentType") String documentType,
            @RequestParam("file") MultipartFile file) throws IOException {
        var actor = currentActorService.requireCurrentUser();
        return ResponseEntity.ok(documentService.upload(actor.tenantId(), applicationId, documentType, actor.username(), file));
    }

    @GetMapping("/api/v1/applications/{applicationId}/documents")
    @PreAuthorize("hasAuthority('DOCUMENT_VIEW')")
    @Tag(name = "Documents")
    @Operation(summary = "List authenticated documents for a loan request application")
    public ResponseEntity<List<DocumentDtos.ApplicationDocumentResponse>> list(@PathVariable String applicationId) {
        var actor = currentActorService.requireCurrentUser();
        return ResponseEntity.ok(documentService.list(actor.tenantId(), applicationId));
    }

    @GetMapping("/api/v1/documents/{documentId}/content")
    @PreAuthorize("hasAuthority('DOCUMENT_VIEW')")
    @Tag(name = "Documents")
    @Operation(summary = "Download document content through an authenticated API URL")
    public ResponseEntity<Resource> content(@PathVariable String documentId) throws IOException {
        var actor = currentActorService.requireCurrentUser();
        ApplicationDocument document = documentService.getRequired(actor.tenantId(), documentId);
        Resource resource = documentService.loadContent(actor.tenantId(), documentId);
        MediaType mediaType = document.getContentType() == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(document.getContentType());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + document.getOriginalFilename() + "\"")
                .contentType(mediaType)
                .body(resource);
    }
}
