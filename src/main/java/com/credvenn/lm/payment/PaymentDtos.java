package com.credvenn.lm.payment;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class PaymentDtos {

    private PaymentDtos() {
    }

    @Schema(name = "CreateTenantPaymentChannelRequest")
    public record CreateTenantPaymentChannelRequest(
            @NotBlank String shortCode,
            String description,
            @NotNull TenantMpesaIntegrationConfigRequest integration) {
    }

    @Schema(name = "TenantMpesaIntegrationConfigRequest")
    public record TenantMpesaIntegrationConfigRequest(
            @NotNull DarajaEnvironment environment,
            @NotBlank String businessShortCode,
            @NotBlank String callbackUrl,
            @NotBlank String consumerKey,
            @NotBlank String consumerSecret,
            @NotBlank String passkey) {
    }

    @Schema(name = "UpdateTenantPaymentChannelRequest")
    public record UpdateTenantPaymentChannelRequest(
            @NotBlank String shortCode,
            String description,
            @NotNull TenantMpesaIntegrationConfigRequest integration,
            boolean active) {
    }

    @Schema(name = "TenantMpesaIntegrationConfigSummary")
    public record TenantMpesaIntegrationConfigSummary(
            DarajaEnvironment environment,
            String businessShortCode,
            String callbackUrl,
            boolean hasCredentials) {
    }

    @Schema(name = "TenantPaymentChannelResponse")
    public record TenantPaymentChannelResponse(
            String id,
            String tenantId,
            PaymentChannelType channelType,
            String shortCode,
            boolean active,
            String description,
            TenantMpesaIntegrationConfigSummary integration,
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

    @Schema(name = "PaymentPageBrandingResponse")
    public record PaymentPageBrandingResponse(
            String displayName,
            String logoUrl,
            String supportPhone,
            String paymentInstructions) {
    }

    @Schema(name = "InitiatePaymentPageStkPushRequest")
    public record InitiatePaymentPageStkPushRequest(
            @NotBlank
            @Pattern(regexp = "^[0-9+()\\-\\s]{7,20}$", message = "phoneNumber must be a valid phone number")
            String phoneNumber) {
    }

    @Schema(name = "PaymentPageAcknowledgementResponse")
    public record PaymentPageAcknowledgementResponse(String message) {
    }

    @Schema(name = "DarajaStkCallbackRequest")
    public record DarajaStkCallbackRequest(@NotNull Body Body) {

        public record Body(@NotNull StkCallback stkCallback) {
        }

        public record StkCallback(
                String MerchantRequestID,
                String CheckoutRequestID,
                Integer ResultCode,
                String ResultDesc,
                java.util.List<CallbackMetadataItem> CallbackMetadata) {
        }

        public record CallbackMetadataItem(
                String Name,
                Object Value) {
        }
    }
}
