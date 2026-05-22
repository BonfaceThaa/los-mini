package com.credvenn.lm.payment;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final TenantPaymentChannelService channelService;
    private final MpesaPaymentService mpesaPaymentService;

    public PaymentController(TenantPaymentChannelService channelService, MpesaPaymentService mpesaPaymentService) {
        this.channelService = channelService;
        this.mpesaPaymentService = mpesaPaymentService;
    }

    @PostMapping("/api/v1/internal/tenants/{tenantId}/payment-channels/mpesa")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasAuthority('TENANT_MANAGE_ALL')")
    @Tag(name = "Payments")
    @Operation(summary = "Create an Mpesa paybill mapping for a tenant")
    public ResponseEntity<PaymentDtos.TenantPaymentChannelResponse> createChannel(
            @PathVariable String tenantId,
            @Valid @RequestBody PaymentDtos.CreateTenantPaymentChannelRequest request) {
        return ResponseEntity.ok(channelService.create(tenantId, request));
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
        return ResponseEntity.ok(channelService.update(tenantId, channelId, request));
    }

    @GetMapping("/api/v1/internal/tenants/{tenantId}/payment-channels")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasAuthority('TENANT_VIEW_ALL')")
    @Tag(name = "Payments")
    @Operation(summary = "List payment channels for a tenant")
    public ResponseEntity<List<PaymentDtos.TenantPaymentChannelResponse>> listChannels(@PathVariable String tenantId) {
        return ResponseEntity.ok(channelService.list(tenantId));
    }

    @GetMapping("/api/v1/internal/payments/mpesa/receipts")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasAuthority('TENANT_VIEW_ALL')")
    @Tag(name = "Payments")
    @Operation(summary = "List Mpesa payment receipts that need operational review")
    public ResponseEntity<List<PaymentDtos.MpesaPaymentReceiptResponse>> listReceipts() {
        return ResponseEntity.ok(mpesaPaymentService.listReceipts());
    }

    @GetMapping("/api/v1/internal/payments/mpesa/receipts/{receiptId}")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasAuthority('TENANT_VIEW_ALL')")
    @Tag(name = "Payments")
    @Operation(summary = "Get a single Mpesa payment receipt")
    public ResponseEntity<PaymentDtos.MpesaPaymentReceiptResponse> getReceipt(@PathVariable String receiptId) {
        return ResponseEntity.ok(mpesaPaymentService.getReceipt(receiptId));
    }

    @PostMapping("/api/v1/internal/payments/mpesa/receipts/{receiptId}/retry")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasAuthority('TENANT_MANAGE_ALL')")
    @Tag(name = "Payments")
    @Operation(summary = "Retry background processing for an Mpesa payment receipt")
    public ResponseEntity<PaymentDtos.RetryMpesaPaymentReceiptResponse> retryReceipt(@PathVariable String receiptId) {
        return ResponseEntity.ok(mpesaPaymentService.retryReceipt(receiptId));
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
        return ResponseEntity.ok(mpesaPaymentService.acceptDarajaCallback(request));
    }

    @PostMapping("/api/v1/public/tenants/{tenantId}/payments/mpesa/stk/callback")
    @Tag(name = "Payments")
    @Operation(summary = "Receive tenant-specific Daraja STK callback")
    public ResponseEntity<PaymentDtos.DarajaCallbackAcknowledgement> stkCallback(
            @PathVariable String tenantId,
            @Valid @RequestBody PaymentDtos.DarajaStkCallbackRequest request) {
        return ResponseEntity.ok(mpesaPaymentService.acceptStkCallback(tenantId, request));
    }
}
