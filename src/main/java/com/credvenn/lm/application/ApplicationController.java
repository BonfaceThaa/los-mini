package com.credvenn.lm.application;

import com.credvenn.lm.common.api.PagedResponse;
import com.credvenn.lm.fineract.FineractDtos;
import com.credvenn.lm.security.CurrentActorService;
import com.credvenn.lm.statement.StatementAnalysisService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/applications")
@Tag(name = "Loan Applications")
@SecurityRequirement(name = "bearerAuth")
public class ApplicationController {

    private final ApplicationService applicationService;
    private final ApplicationStatementOtpService applicationStatementOtpService;
    private final StatementAnalysisService statementAnalysisService;
    private final CurrentActorService currentActorService;

    public ApplicationController(
            ApplicationService applicationService,
            ApplicationStatementOtpService applicationStatementOtpService,
            StatementAnalysisService statementAnalysisService,
            CurrentActorService currentActorService) {
        this.applicationService = applicationService;
        this.applicationStatementOtpService = applicationStatementOtpService;
        this.statementAnalysisService = statementAnalysisService;
        this.currentActorService = currentActorService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('LOAN_CREATE')")
    @Operation(summary = "Create a loan request application",
            description = "Resolves an active originationProfileCode or tenant default, then validates and stores tenant-defined applicationVariables in the same transaction before the asynchronous KYC workflow starts.")
    public ResponseEntity<ApplicationDtos.LoanRequestApplicationResponse> create(
            @Valid @RequestBody ApplicationDtos.CreateLoanRequestApplicationRequest request) {
        var actor = currentActorService.requireCurrentUser();
        return ResponseEntity.ok(applicationService.create(actor.tenantId(), actor.username(), request));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('LOAN_VIEW')")
    @Operation(summary = "List loan applications for the authenticated tenant")
    public ResponseEntity<PagedResponse<ApplicationDtos.LoanRequestApplicationResponse>> list(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        var actor = currentActorService.requireCurrentUser();
        return ResponseEntity.ok(applicationService.list(actor.tenantId(), page, size, sortBy, sortDir));
    }

    @GetMapping("/{applicationId}")
    @PreAuthorize("hasAuthority('LOAN_VIEW')")
    @Operation(summary = "Get a loan request application")
    public ResponseEntity<ApplicationDtos.LoanRequestApplicationResponse> get(@PathVariable String applicationId) {
        var actor = currentActorService.requireCurrentUser();
        return ResponseEntity.ok(applicationService.get(actor.tenantId(), applicationId));
    }

    @PostMapping("/{applicationId}/statement-otps")
    @PreAuthorize("hasAuthority('LOAN_CREATE')")
    @Operation(summary = "Add more statement OTP candidates to an application")
    public ResponseEntity<ApplicationDtos.AddStatementOtpsResponse> addStatementOtps(
            @PathVariable String applicationId,
            @Valid @RequestBody ApplicationDtos.AddStatementOtpsRequest request) {
        var actor = currentActorService.requireCurrentUser();
        applicationService.getRequired(actor.tenantId(), applicationId);
        List<ApplicationStatementOtp> added = applicationStatementOtpService.addOtps(actor.tenantId(), applicationId, request.otps());
        boolean retryQueued = statementAnalysisService.queueRetryIfEligible(actor.tenantId(), applicationId, actor.username());
        List<ApplicationDtos.StatementOtpResponse> otps = applicationStatementOtpService.listViews(actor.tenantId(), applicationId).stream()
                .map(ApplicationController::toStatementOtpResponse)
                .toList();
        String message = added.isEmpty()
                ? "No new statement OTPs were added"
                : retryQueued
                        ? "Statement OTPs saved and statement analysis retry queued"
                        : "Statement OTPs saved";
        return ResponseEntity.ok(new ApplicationDtos.AddStatementOtpsResponse(
                added.size(),
                retryQueued,
                message,
                otps));
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

    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Application with selected local mapping ID", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ApplicationDtos.LoanRequestApplicationResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid selectors, unmet prerequisites, ineligible product, missing application profile or a prohibited product change", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.credvenn.lm.common.exception.ApiError.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Missing LOAN_CREATE permission", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.credvenn.lm.common.exception.ApiError.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Application not found in the authenticated tenant", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.credvenn.lm.common.exception.ApiError.class)))
    })
    @PostMapping("/{applicationId}/offers/select")
    @PreAuthorize("hasAuthority('LOAN_CREATE')")
    @Operation(summary = "Select an eligible loan product offer by product code", description = "Requires LOAN_CREATE. Supply exactly one of productCode (preferred) or deprecated fineractProductId. Checks KYC/client/statement readiness, tenant, saved application profile, active product and inclusive amount limits. Saves the local mapping ID and resolves the remote ID internally. A different product cannot replace calculated financing.")
    public ResponseEntity<ApplicationDtos.LoanRequestApplicationResponse> selectOffer(
            @PathVariable String applicationId,
            @Valid @RequestBody ApplicationDtos.SelectOfferRequest request) {
        var actor = currentActorService.requireCurrentUser();
        return ResponseEntity.ok(applicationService.selectOffer(actor.tenantId(), applicationId, actor.username(), request));
    }

    @GetMapping("/{applicationId}/eligible-products")
    @PreAuthorize("hasAuthority('LOAN_VIEW')")
    @Operation(summary = "List eligible products for the application profile", description = "Requires LOAN_VIEW. Returns readiness checks and active same-tenant/profile products within the requested amount bounds. Products include productCode, loanProductMappingId and originationProfileId. An unready application returns an empty products list.")
    public ResponseEntity<ApplicationDtos.EligibleProductsResponse> eligibleProducts(@PathVariable String applicationId) {
        var actor = currentActorService.requireCurrentUser();
        return ResponseEntity.ok(applicationService.getEligibleProducts(actor.tenantId(), applicationId));
    }

    @GetMapping("/{applicationId}/active-loan-products")
    @PreAuthorize("hasAuthority('LOAN_VIEW')")
    @Operation(summary = "List active products for the application origination profile", description = "Requires LOAN_VIEW. Uses the saved application profile, not the current tenant default. This manual catalog does not filter by amount or readiness; offer selection still enforces those checks.")
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

    private static ApplicationDtos.StatementOtpResponse toStatementOtpResponse(ApplicationStatementOtpService.StatementOtpView otp) {
        return new ApplicationDtos.StatementOtpResponse(
                otp.id(),
                otp.otp(),
                otp.status(),
                otp.source(),
                otp.createdAt(),
                otp.testedAt(),
                otp.usedAt(),
                otp.failureReason());
    }
}
