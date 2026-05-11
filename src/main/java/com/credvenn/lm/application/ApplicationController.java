package com.credvenn.lm.application;

import com.credvenn.lm.fineract.FineractDtos;
import com.credvenn.lm.security.CurrentActorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/applications")
@Tag(name = "Loan Applications")
@SecurityRequirement(name = "bearerAuth")
public class ApplicationController {

    private final ApplicationService applicationService;
    private final CurrentActorService currentActorService;

    public ApplicationController(ApplicationService applicationService, CurrentActorService currentActorService) {
        this.applicationService = applicationService;
        this.currentActorService = currentActorService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('LOAN_CREATE')")
    @Operation(summary = "Create a loan request application")
    public ResponseEntity<ApplicationDtos.LoanRequestApplicationResponse> create(
            @Valid @RequestBody ApplicationDtos.CreateLoanRequestApplicationRequest request) {
        var actor = currentActorService.requireCurrentUser();
        return ResponseEntity.ok(applicationService.create(actor.tenantId(), actor.username(), request));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('LOAN_VIEW')")
    @Operation(summary = "List loan applications for the authenticated tenant")
    public ResponseEntity<List<ApplicationDtos.LoanRequestApplicationResponse>> list() {
        var actor = currentActorService.requireCurrentUser();
        return ResponseEntity.ok(applicationService.list(actor.tenantId()));
    }

    @GetMapping("/{applicationId}")
    @PreAuthorize("hasAuthority('LOAN_VIEW')")
    @Operation(summary = "Get a loan request application")
    public ResponseEntity<ApplicationDtos.LoanRequestApplicationResponse> get(@PathVariable String applicationId) {
        var actor = currentActorService.requireCurrentUser();
        return ResponseEntity.ok(applicationService.get(actor.tenantId(), applicationId));
    }

    @PostMapping("/{applicationId}/consent")
    @PreAuthorize("hasAuthority('LOAN_CREATE')")
    @Operation(summary = "Capture customer consent")
    public ResponseEntity<ApplicationDtos.LoanRequestApplicationResponse> captureConsent(
            @PathVariable String applicationId,
            @Valid @RequestBody ApplicationDtos.CaptureConsentRequest request) {
        var actor = currentActorService.requireCurrentUser();
        return ResponseEntity.ok(applicationService.captureConsent(actor.tenantId(), applicationId, actor.username(), request));
    }

    @PostMapping("/{applicationId}/offers/select")
    @PreAuthorize("hasAuthority('LOAN_CREATE')")
    @Operation(summary = "Select an eligible loan product offer")
    public ResponseEntity<ApplicationDtos.LoanRequestApplicationResponse> selectOffer(
            @PathVariable String applicationId,
            @Valid @RequestBody ApplicationDtos.SelectOfferRequest request) {
        var actor = currentActorService.requireCurrentUser();
        return ResponseEntity.ok(applicationService.selectOffer(actor.tenantId(), applicationId, actor.username(), request));
    }

    @GetMapping("/{applicationId}/eligible-products")
    @PreAuthorize("hasAuthority('LOAN_VIEW')")
    @Operation(summary = "List Mini-LOS filtered eligible Fineract products")
    public ResponseEntity<List<FineractDtos.LoanProductResponse>> eligibleProducts(@PathVariable String applicationId) {
        var actor = currentActorService.requireCurrentUser();
        return ResponseEntity.ok(applicationService.getEligibleProducts(actor.tenantId(), applicationId));
    }

    @GetMapping("/{applicationId}/active-loan-products")
    @PreAuthorize("hasAuthority('LOAN_VIEW')")
    @Operation(summary = "List all active Fineract products for manual approval")
    public ResponseEntity<List<FineractDtos.LoanProductResponse>> activeProducts(@PathVariable String applicationId) {
        var actor = currentActorService.requireCurrentUser();
        return ResponseEntity.ok(applicationService.getAllActiveProducts(actor.tenantId(), applicationId));
    }

    @GetMapping("/{applicationId}/repayments")
    @PreAuthorize("hasAuthority('LOAN_VIEW')")
    @Operation(summary = "Fetch loan repayments from Fineract for the application's loan")
    public ResponseEntity<FineractDtos.LoanRepaymentListResponse> repayments(@PathVariable String applicationId) {
        var actor = currentActorService.requireCurrentUser();
        return ResponseEntity.ok(applicationService.getLoanRepayments(actor.tenantId(), applicationId));
    }

    @PostMapping("/{applicationId}/internal-approval")
    @PreAuthorize("hasAuthority('CREDIT_MANUAL_APPROVE')")
    @Operation(summary = "Perform internal approval and create a pending Fineract loan")
    public ResponseEntity<ApplicationDtos.LoanRequestApplicationResponse> internalApproval(
            @PathVariable String applicationId,
            @Valid @RequestBody ApplicationDtos.InternalApprovalRequest request) {
        var actor = currentActorService.requireCurrentUser();
        return ResponseEntity.ok(applicationService.internalApprove(actor.tenantId(), applicationId, actor.username(), request));
    }

    @PostMapping("/{applicationId}/activate-loan")
    @PreAuthorize("hasAuthority('LOAN_CREATE')")
    @Operation(summary = "Activate the Fineract loan after device assignment")
    public ResponseEntity<ApplicationDtos.LoanRequestApplicationResponse> activateLoan(@PathVariable String applicationId) {
        var actor = currentActorService.requireCurrentUser();
        return ResponseEntity.ok(applicationService.activateLoan(actor.tenantId(), applicationId, actor.username()));
    }
}
