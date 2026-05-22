package com.credvenn.lm.statementinbox;

import com.credvenn.lm.security.CurrentServiceActorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.time.Instant;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/service/statements/mpesa")
@Tag(name = "Inbound Statements")
@SecurityRequirement(name = "bearerAuth")
public class ServiceStatementUploadController {

    private final InboundStatementService inboundStatementService;
    private final CurrentServiceActorService currentServiceActorService;

    public ServiceStatementUploadController(
            InboundStatementService inboundStatementService,
            CurrentServiceActorService currentServiceActorService) {
        this.inboundStatementService = inboundStatementService;
        this.currentServiceActorService = currentServiceActorService;
    }

    @PostMapping("/upload")
    @PreAuthorize("hasAuthority('SERVICE_STATEMENT_UPLOAD')")
    @Operation(summary = "Accept an inbound Mpesa statement from a service integration")
    public ResponseEntity<InboundStatementDtos.InboundReceiptAcceptedResponse> acceptInboundStatement(
            @RequestParam("destinationEmail") String destinationEmail,
            @RequestParam(value = "messageId", required = false) String messageId,
            @RequestParam(value = "receivedAt", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant receivedAt,
            @RequestPart("file") MultipartFile file) throws IOException {
        var actor = currentServiceActorService.requireCurrentService();
        return ResponseEntity.ok(inboundStatementService.acceptInboundStatement(
                destinationEmail,
                messageId,
                receivedAt,
                file,
                actor.serviceName()));
    }
}
