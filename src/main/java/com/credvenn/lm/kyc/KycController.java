package com.credvenn.lm.kyc;

import com.credvenn.lm.application.ApplicationDtos;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/applications/{applicationId}/kyc")
@Tag(name = "KYC")
@SecurityRequirement(name = "bearerAuth")
public class KycController {

    private final KycService kycService;
    private final CurrentActorService currentActorService;

    public KycController(KycService kycService, CurrentActorService currentActorService) {
        this.kycService = kycService;
        this.currentActorService = currentActorService;
    }

    @PostMapping("/run")
    @PreAuthorize("hasAuthority('KYC_RUN')")
    @Operation(summary = "Run KYC asynchronously using the configured provider")
    public ResponseEntity<KycDtos.KycCheckResponse> run(@PathVariable String applicationId) {
        var actor = currentActorService.requireCurrentUser();
        return ResponseEntity.ok(kycService.run(actor.tenantId(), applicationId, actor.username()));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('KYC_VIEW')")
    @Operation(summary = "Get the latest KYC result")
    public ResponseEntity<KycDtos.KycCheckResponse> get(@PathVariable String applicationId) {
        var actor = currentActorService.requireCurrentUser();
        return ResponseEntity.ok(kycService.get(actor.tenantId(), applicationId));
    }

    @PostMapping("/manual-review")
    @PreAuthorize("hasAuthority('KYC_MANUAL_REVIEW')")
    @Operation(summary = "Manually approve or reject a pending, in-progress, failed, or review-required KYC")
    public ResponseEntity<KycDtos.KycCheckResponse> manualReview(
            @PathVariable String applicationId,
            @Valid @RequestBody KycDtos.ManualKycReviewRequest request) {
        var actor = currentActorService.requireCurrentUser();
        return ResponseEntity.ok(kycService.manualReview(actor.tenantId(), applicationId, actor.username(), request));
    }

    @PostMapping("/retry")
    @PreAuthorize("hasAuthority('KYC_RUN')")
    @Operation(summary = "Retry KYC processing")
    public ResponseEntity<KycDtos.KycCheckResponse> retry(@PathVariable String applicationId) {
        var actor = currentActorService.requireCurrentUser();
        return ResponseEntity.ok(kycService.retry(actor.tenantId(), applicationId, actor.username()));
    }

    @PostMapping("/retry-client-creation")
    @PreAuthorize("hasAuthority('KYC_RUN')")
    @Operation(summary = "Retry Fineract client creation after KYC approval")
    public ResponseEntity<ApplicationDtos.LoanRequestApplicationResponse> retryClientCreation(@PathVariable String applicationId) {
        var actor = currentActorService.requireCurrentUser();
        return ResponseEntity.ok(kycService.retryClientCreation(actor.tenantId(), applicationId, actor.username()));
    }
}
