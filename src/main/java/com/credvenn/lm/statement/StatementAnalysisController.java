package com.credvenn.lm.statement;

import com.credvenn.lm.security.CurrentActorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/applications/{applicationId}/statement-analysis")
@Tag(name = "Credit Check")
@SecurityRequirement(name = "bearerAuth")
public class StatementAnalysisController {

    private final StatementAnalysisService statementAnalysisService;
    private final CurrentActorService currentActorService;

    public StatementAnalysisController(StatementAnalysisService statementAnalysisService, CurrentActorService currentActorService) {
        this.statementAnalysisService = statementAnalysisService;
        this.currentActorService = currentActorService;
    }

    @PostMapping("/run")
    @PreAuthorize("hasAuthority('CREDIT_CHECK_RUN')")
    @Operation(summary = "Run statement analysis asynchronously")
    public ResponseEntity<StatementDtos.StatementAnalysisResponse> run(
            @PathVariable String applicationId,
            @RequestParam("documentId") String documentId,
            @RequestParam(value = "simulateOutcome", required = false) String simulateOutcome) {
        var actor = currentActorService.requireCurrentUser();
        return ResponseEntity.ok(statementAnalysisService.run(
                actor.tenantId(),
                applicationId,
                documentId,
                actor.username(),
                simulateOutcome));
    }

    @PostMapping("/manual-pass")
    @PreAuthorize("hasAuthority('CREDIT_MANUAL_APPROVE')")
    @Operation(summary = "Manually pass statement analysis without an uploaded document")
    public ResponseEntity<StatementDtos.StatementAnalysisResponse> manualPass(
            @PathVariable String applicationId,
            @Valid @RequestBody StatementDtos.ManualStatementPassRequest request) {
        var actor = currentActorService.requireCurrentUser();
        return ResponseEntity.ok(statementAnalysisService.manualPass(
                actor.tenantId(),
                applicationId,
                actor.username(),
                request));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CREDIT_CHECK_VIEW')")
    @Operation(summary = "Get the latest statement analysis result")
    public ResponseEntity<StatementDtos.StatementAnalysisResponse> get(@PathVariable String applicationId) {
        var actor = currentActorService.requireCurrentUser();
        return ResponseEntity.ok(statementAnalysisService.get(actor.tenantId(), applicationId));
    }
}
