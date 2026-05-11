package com.credvenn.lm.statementinbox;

import com.credvenn.lm.security.CurrentActorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@SecurityRequirement(name = "bearerAuth")
public class InboundStatementController {

    private final InboundStatementService inboundStatementService;
    private final CurrentActorService currentActorService;

    public InboundStatementController(InboundStatementService inboundStatementService, CurrentActorService currentActorService) {
        this.inboundStatementService = inboundStatementService;
        this.currentActorService = currentActorService;
    }

    @PostMapping("/api/v1/internal/statements/mpesa/inbound")
    @PreAuthorize("hasAuthority('STATEMENT_INBOUND_INGEST')")
    @Tag(name = "Inbound Statements")
    @Operation(summary = "Accept an inbound Mpesa statement and process it asynchronously")
    public ResponseEntity<InboundStatementDtos.InboundReceiptAcceptedResponse> acceptInboundStatement(
            @RequestParam("destinationEmail") String destinationEmail,
            @RequestParam(value = "messageId", required = false) String messageId,
            @RequestParam(value = "receivedAt", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant receivedAt,
            @RequestPart("file") MultipartFile file) throws IOException {
        var actor = currentActorService.requireCurrentUser();
        return ResponseEntity.ok(inboundStatementService.acceptInboundStatement(
                destinationEmail,
                messageId,
                receivedAt,
                file,
                actor.username()));
    }

    @GetMapping("/api/v1/statements/mpesa/inbox")
    @PreAuthorize("hasAuthority('STATEMENT_INBOX_VIEW')")
    @Tag(name = "Inbound Statements")
    @Operation(summary = "List inbound statement receipts requiring review")
    public ResponseEntity<List<InboundStatementDtos.InboundStatementReceiptResponse>> listInbox() {
        var actor = currentActorService.requireCurrentUser();
        return ResponseEntity.ok(inboundStatementService.listReviewQueue(actor));
    }

    @GetMapping("/api/v1/statements/mpesa/inbox/{receiptId}")
    @PreAuthorize("hasAuthority('STATEMENT_INBOX_VIEW')")
    @Tag(name = "Inbound Statements")
    @Operation(summary = "Get an inbound statement receipt")
    public ResponseEntity<InboundStatementDtos.InboundStatementReceiptResponse> getReceipt(@PathVariable String receiptId) {
        var actor = currentActorService.requireCurrentUser();
        return ResponseEntity.ok(inboundStatementService.getReceipt(actor, receiptId));
    }

    @GetMapping("/api/v1/statements/mpesa/inbox/{receiptId}/content")
    @PreAuthorize("hasAuthority('STATEMENT_INBOX_VIEW')")
    @Tag(name = "Inbound Statements")
    @Operation(summary = "Download the inbound statement PDF content")
    public ResponseEntity<Resource> getReceiptContent(@PathVariable String receiptId) throws IOException {
        var actor = currentActorService.requireCurrentUser();
        InboundStatementReceipt receipt = inboundStatementService.getRequiredReceipt(actor, receiptId);
        Resource resource = inboundStatementService.loadReceiptContent(actor, receiptId);
        MediaType mediaType = receipt.getContentType() == null ? MediaType.APPLICATION_PDF : MediaType.parseMediaType(receipt.getContentType());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + receipt.getOriginalFilename() + "\"")
                .contentType(mediaType)
                .body(resource);
    }

    @PostMapping("/api/v1/statements/mpesa/inbox/{receiptId}/resolve")
    @PreAuthorize("hasAuthority('STATEMENT_INBOX_RESOLVE')")
    @Tag(name = "Inbound Statements")
    @Operation(summary = "Resolve an inbound statement receipt to a loan request application")
    public ResponseEntity<InboundStatementDtos.InboundStatementReceiptResponse> resolveReceipt(
            @PathVariable String receiptId,
            @Valid @org.springframework.web.bind.annotation.RequestBody InboundStatementDtos.ResolveInboundStatementReceiptRequest request) {
        var actor = currentActorService.requireCurrentUser();
        return ResponseEntity.ok(inboundStatementService.resolveReceipt(actor, receiptId, request));
    }
}
