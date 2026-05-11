package com.credvenn.lm.payment;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class PaymentDtos {

    private PaymentDtos() {
    }

    @Schema(name = "CreateTenantPaymentChannelRequest")
    public record CreateTenantPaymentChannelRequest(
            @NotBlank String shortCode,
            String description) {
    }

    @Schema(name = "TenantPaymentChannelResponse")
    public record TenantPaymentChannelResponse(
            String id,
            String tenantId,
            PaymentChannelType channelType,
            String shortCode,
            boolean active,
            String description,
            Instant createdAt,
            Instant updatedAt) {
    }

    @Schema(name = "DarajaCallbackRequest")
    public record DarajaCallbackRequest(
            @NotBlank String TransactionType,
            @NotBlank String TransID,
            @NotBlank String TransTime,
            @NotNull BigDecimal TransAmount,
            @NotBlank String BusinessShortCode,
            @NotBlank String BillRefNumber,
            String InvoiceNumber,
            String OrgAccountBalance,
            String ThirdPartyTransID,
            String MSISDN,
            String FirstName,
            String MiddleName,
            String LastName) {
    }

    @Schema(name = "DarajaCallbackAcknowledgement")
    public record DarajaCallbackAcknowledgement(
            int ResultCode,
            String ResultDesc) {
    }

    @Schema(name = "MpesaPaymentReceiptResponse")
    public record MpesaPaymentReceiptResponse(
            String id,
            String tenantId,
            String businessShortCode,
            String billRefNumber,
            String normalizedPhoneNumber,
            BigDecimal transactionAmount,
            Instant transactionTime,
            String mpesaReceiptNumber,
            String msisdn,
            String payerFirstName,
            String payerMiddleName,
            String payerLastName,
            MpesaPaymentProcessingStatus processingStatus,
            String matchedApplicationId,
            String matchedFineractClientId,
            String matchedFineractLoanId,
            String fineractTransactionId,
            String failureReason,
            Instant processingStartedAt,
            Instant processedAt,
            Instant createdAt,
            Instant updatedAt) {
    }

    @Schema(name = "MpesaPaymentReceiptListResponse")
    public record MpesaPaymentReceiptListResponse(List<MpesaPaymentReceiptResponse> receipts) {
    }

    @Schema(name = "RetryMpesaPaymentReceiptResponse")
    public record RetryMpesaPaymentReceiptResponse(
            String receiptId,
            MpesaPaymentProcessingStatus processingStatus,
            String message) {
    }
}
