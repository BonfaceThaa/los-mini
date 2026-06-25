package com.credvenn.lm.payment;

import com.credvenn.lm.common.api.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import com.credvenn.lm.security.CurrentActorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final TenantPaymentChannelService channelService;
    private final MpesaPaymentService mpesaPaymentService;
    private final DepositPaymentService depositPaymentService;
    private final CurrentActorService currentActorService;

    public PaymentController(
            TenantPaymentChannelService channelService,
            MpesaPaymentService mpesaPaymentService,
            DepositPaymentService depositPaymentService,
            CurrentActorService currentActorService) {
        this.channelService = channelService;
        this.mpesaPaymentService = mpesaPaymentService;
        this.depositPaymentService = depositPaymentService;
        this.currentActorService = currentActorService;
    }

    @PostMapping("/api/v1/internal/tenants/{tenantId}/payment-channels/mpesa")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasAuthority('TENANT_MANAGE_ALL')")
    @Tag(name = "Payments")
    @Operation(summary = "Create an Mpesa paybill mapping for a tenant")
    public ResponseEntity<PaymentDtos.TenantPaymentChannelResponse> createChannel(
            @PathVariable String tenantId,
            @Valid @RequestBody PaymentDtos.CreateTenantPaymentChannelRequest request) {
        TenantPaymentChannel channel = channelService.create(
                tenantId,
                request.shortCode(),
                request.description(),
                request.integration().environment(),
                request.integration().businessShortCode(),
                request.integration().callbackUrl(),
                request.integration().consumerKey(),
                request.integration().consumerSecret(),
                request.integration().passkey());
        return ResponseEntity.ok(PaymentDtos.TenantPaymentChannelResponse.from(
                channel,
                channelService.getConfiguredIntegration(channel)));
    }

    @PatchMapping("/api/v1/internal/tenants/{tenantId}/payment-channels/mpesa/{channelId}")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasAuthority('TENANT_MANAGE_ALL')")
    @Tag(name = "Payments")
    @Operation(summary = "Update tenant Mpesa channel credentials or active status")
    public ResponseEntity<PaymentDtos.TenantPaymentChannelResponse> updateChannel(
            @PathVariable String tenantId,
            @PathVariable String channelId,
            @Valid @RequestBody PaymentDtos.UpdateTenantPaymentChannelRequest request) {
        TenantPaymentChannel channel = channelService.update(
                tenantId,
                channelId,
                request.shortCode(),
                request.description(),
                request.integration().environment(),
                request.integration().businessShortCode(),
                request.integration().callbackUrl(),
                request.integration().consumerKey(),
                request.integration().consumerSecret(),
                request.integration().passkey(),
                request.active());
        return ResponseEntity.ok(PaymentDtos.TenantPaymentChannelResponse.from(
                channel,
                channelService.getConfiguredIntegration(channel)));
    }

    @GetMapping("/api/v1/internal/tenants/{tenantId}/payment-channels")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasAuthority('TENANT_VIEW_ALL')")
    @Tag(name = "Payments")
    @Operation(summary = "List payment channels for a tenant")
    public ResponseEntity<List<PaymentDtos.TenantPaymentChannelResponse>> listChannels(@PathVariable String tenantId) {
        return ResponseEntity.ok(channelService.list(tenantId).stream()
                .map(channel -> PaymentDtos.TenantPaymentChannelResponse.from(
                        channel,
                        channelService.getConfiguredIntegration(channel)))
                .toList());
    }

    @PostMapping("/api/v1/internal/tenants/{tenantId}/payment-channels/mpesa/{channelId}/c2b/register")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasAuthority('TENANT_MANAGE_ALL')")
    @Tag(name = "Payments")
    @Operation(summary = "Register Daraja C2B confirmation and optional validation URLs for a tenant paybill")
    public ResponseEntity<PaymentDtos.RegisterTenantC2bUrlsResponse> registerC2bUrls(
            @PathVariable String tenantId,
            @PathVariable String channelId,
            @Valid @RequestBody PaymentDtos.RegisterTenantC2bUrlsRequest request) {
        return ResponseEntity.ok(channelService.registerC2bUrls(
                tenantId,
                channelId,
                request.confirmationUrl(),
                request.validationUrl(),
                request.responseType()));
    }

    @GetMapping("/api/v1/internal/payments/mpesa/receipts")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasAuthority('TENANT_VIEW_ALL')")
    @Tag(name = "Payments")
    @Operation(summary = "List Mpesa payment receipts that need operational review")
    public ResponseEntity<List<PaymentDtos.MpesaPaymentReceiptResponse>> listReceipts() {
        return ResponseEntity.ok(mpesaPaymentService.listReceipts().stream()
                .map(PaymentDtos.MpesaPaymentReceiptResponse::from)
                .toList());
    }

    @GetMapping("/api/v1/internal/payments/mpesa/receipts/{receiptId}")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasAuthority('TENANT_VIEW_ALL')")
    @Tag(name = "Payments")
    @Operation(summary = "Get a single Mpesa payment receipt")
    public ResponseEntity<PaymentDtos.MpesaPaymentReceiptResponse> getReceipt(@PathVariable String receiptId) {
        return ResponseEntity.ok(PaymentDtos.MpesaPaymentReceiptResponse.from(mpesaPaymentService.getReceipt(receiptId)));
    }

    @PostMapping("/api/v1/internal/payments/mpesa/receipts/{receiptId}/retry")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasAuthority('TENANT_MANAGE_ALL')")
    @Tag(name = "Payments")
    @Operation(summary = "Retry background processing for an Mpesa payment receipt")
    public ResponseEntity<PaymentDtos.RetryMpesaPaymentReceiptResponse> retryReceipt(@PathVariable String receiptId) {
        MpesaPaymentReceipt receipt = mpesaPaymentService.retryReceipt(receiptId);
        String message = receipt.getProcessingStatus() == MpesaPaymentProcessingStatus.REPAYMENT_POSTED
                ? "Repayment was already posted for this receipt."
                : "Receipt queued for background retry.";
        return ResponseEntity.ok(new PaymentDtos.RetryMpesaPaymentReceiptResponse(
                receipt.getId(),
                receipt.getProcessingStatus(),
                message));
    }

    @PostMapping("/api/v1/public/payments/mpesa/callback")
    @Tag(name = "Payments")
    @Operation(summary = "Receive Daraja payment confirmations and queue repayment processing")
    public ResponseEntity<PaymentDtos.DarajaCallbackAcknowledgement> callback(@Valid @RequestBody PaymentDtos.DarajaCallbackRequest request) {
        log.info(
                "Received Mpesa callback transId={} shortCode={} billRef={} amount={}",
                request.TransID(),
                request.BusinessShortCode(),
                request.BillRefNumber(),
                request.TransAmount());
        mpesaPaymentService.acceptDarajaCallback(
                request.TransID(),
                request.TransTime(),
                request.TransAmount(),
                request.BusinessShortCode(),
                request.BillRefNumber(),
                request.MSISDN(),
                request.FirstName(),
                request.MiddleName(),
                request.LastName(),
                request.toString());
        return ResponseEntity.ok(new PaymentDtos.DarajaCallbackAcknowledgement(0, "Accepted"));
    }

    @PostMapping("/api/v1/public/payments/mpesa/deposits/callback")
    @Tag(name = "Payments")
    @Operation(summary = "Receive Daraja deposit confirmations for pre-loan deposits")
    public ResponseEntity<PaymentDtos.DarajaCallbackAcknowledgement> depositCallback(@Valid @RequestBody PaymentDtos.DarajaCallbackRequest request) {
        handleDepositCallback(null, request);
        return ResponseEntity.ok(new PaymentDtos.DarajaCallbackAcknowledgement(0, "Accepted"));
    }

    @PostMapping("/api/v1/public/tenants/{tenantId}/collections/c2b/deposits/callback")
    @Tag(name = "Payments")
    @Operation(summary = "Receive tenant-specific Daraja deposit confirmations for pre-loan deposits")
    public ResponseEntity<PaymentDtos.DarajaCallbackAcknowledgement> tenantDepositCallback(
            @PathVariable String tenantId,
            @Valid @RequestBody PaymentDtos.DarajaCallbackRequest request) {
        handleDepositCallback(tenantId, request);
        return ResponseEntity.ok(new PaymentDtos.DarajaCallbackAcknowledgement(0, "Accepted"));
    }

    @PostMapping("/api/v1/public/tenants/{tenantId}/collections/c2b/deposits/validate")
    @Tag(name = "Payments")
    @Operation(summary = "Validate whether a tenant deposit payment should be accepted before confirmation")
    public ResponseEntity<PaymentDtos.DarajaValidationResponse> validateTenantDepositCallback(
            @PathVariable String tenantId,
            @Valid @RequestBody PaymentDtos.DarajaCallbackRequest request) {
        var decision = depositPaymentService.validateDepositCallback(
                tenantId,
                request.BusinessShortCode(),
                request.BillRefNumber());
        return ResponseEntity.ok(new PaymentDtos.DarajaValidationResponse(
                decision.resultCode(),
                decision.resultDesc()));
    }

    @PostMapping("/api/v1/public/tenants/{tenantId}/payments/mpesa/stk/callback")
    @Tag(name = "Payments")
    @Operation(summary = "Receive tenant-specific Daraja STK callback")
    public ResponseEntity<PaymentDtos.DarajaCallbackAcknowledgement> stkCallback(
            @PathVariable String tenantId,
            @Valid @RequestBody PaymentDtos.DarajaStkCallbackRequest request) {
        List<java.util.Map.Entry<String, Object>> metadata = request.Body().stkCallback().CallbackMetadata() == null
                ? List.of()
                : request.Body().stkCallback().CallbackMetadata().stream()
                        .map(item -> java.util.Map.entry(item.Name(), item.Value()))
                        .toList();
        mpesaPaymentService.acceptStkCallback(
                tenantId,
                request.Body().stkCallback().CheckoutRequestID(),
                request.Body().stkCallback().ResultCode(),
                request.Body().stkCallback().ResultDesc(),
                metadata,
                request.toString());
        return ResponseEntity.ok(new PaymentDtos.DarajaCallbackAcknowledgement(0, "Accepted"));
    }

    @GetMapping("/api/v1/payments/deposits")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasAuthority('LOAN_VIEW')")
    @Tag(name = "Payments")
    @Operation(summary = "List deposit payments for the authenticated tenant")
    public ResponseEntity<PagedResponse<PaymentDtos.DepositPaymentResponse>> listDeposits(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(defaultValue = "transactionTime") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        var actor = currentActorService.requireCurrentUser();
        return ResponseEntity.ok(depositPaymentService.listTenantDeposits(actor.tenantId(), page, size, sortBy, sortDir));
    }

    @GetMapping("/api/v1/payments/deposits/{depositId}")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasAuthority('LOAN_VIEW')")
    @Tag(name = "Payments")
    @Operation(summary = "Get a deposit payment for the authenticated tenant")
    public ResponseEntity<PaymentDtos.DepositPaymentResponse> getDeposit(@PathVariable String depositId) {
        var actor = currentActorService.requireCurrentUser();
        return ResponseEntity.ok(PaymentDtos.DepositPaymentResponse.from(
                depositPaymentService.getTenantDeposit(actor.tenantId(), depositId)));
    }

    @GetMapping("/api/v1/applications/{applicationId}/deposits")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasAuthority('LOAN_VIEW')")
    @Tag(name = "Payments")
    @Operation(summary = "List deposit payments for an application in the authenticated tenant")
    public ResponseEntity<PagedResponse<PaymentDtos.DepositPaymentResponse>> listApplicationDepositsPaged(
            @PathVariable String applicationId,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(defaultValue = "transactionTime") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        var actor = currentActorService.requireCurrentUser();
        return ResponseEntity.ok(depositPaymentService.listTenantDepositsByApplication(
                actor.tenantId(),
                applicationId,
                page,
                size,
                sortBy,
                sortDir));
    }

    @GetMapping("/api/v1/clients/{fineractClientId}/deposits")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasAuthority('CLIENT_VIEW')")
    @Tag(name = "Payments")
    @Operation(summary = "List deposit payments for a client in the authenticated tenant")
    public ResponseEntity<PagedResponse<PaymentDtos.DepositPaymentResponse>> listClientDepositsPaged(
            @PathVariable String fineractClientId,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(defaultValue = "transactionTime") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        var actor = currentActorService.requireCurrentUser();
        return ResponseEntity.ok(depositPaymentService.listTenantDepositsByClient(
                actor.tenantId(),
                fineractClientId,
                page,
                size,
                sortBy,
                sortDir));
    }

    private void handleDepositCallback(String tenantId, PaymentDtos.DarajaCallbackRequest request) {
        log.info(
                "Received Mpesa deposit callback tenantId={} transId={} shortCode={} billRef={} amount={}",
                tenantId,
                request.TransID(),
                request.BusinessShortCode(),
                request.BillRefNumber(),
                request.TransAmount());
        if (tenantId == null || tenantId.isBlank()) {
            depositPaymentService.acceptDepositCallback(
                    request.TransactionType(),
                    request.TransID(),
                    request.TransTime(),
                    request.TransAmount(),
                    request.BusinessShortCode(),
                    request.BillRefNumber(),
                    request.MSISDN(),
                    request.FirstName(),
                    request.MiddleName(),
                    request.LastName(),
                    request.toString());
            return;
        }
        depositPaymentService.acceptDepositCallback(
                tenantId,
                request.TransactionType(),
                request.TransID(),
                request.TransTime(),
                request.TransAmount(),
                request.BusinessShortCode(),
                request.BillRefNumber(),
                request.MSISDN(),
                request.FirstName(),
                request.MiddleName(),
                request.LastName(),
                request.toString());
    }
}
