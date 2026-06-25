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
            boolean hasCredentials,
            C2bRegistrationSummary c2bRegistration) {
    }

    @Schema(name = "C2bRegistrationSummary")
    public record C2bRegistrationSummary(
            String confirmationUrl,
            String validationUrl,
            String responseType,
            Instant lastRegisteredAt,
            Instant lastRequestedAt,
            String lastResponseCode,
            String lastResponseDescription,
            String lastOriginatorConversationId) {

        public static C2bRegistrationSummary from(TenantMpesaIntegrationConfig config) {
            if (config == null) {
                return null;
            }
            if (config.c2bConfirmationUrl() == null
                    && config.c2bValidationUrl() == null
                    && config.c2bResponseType() == null
                    && config.c2bLastRegisteredAt() == null
                    && config.c2bLastRequestedAt() == null
                    && config.c2bLastResponseCode() == null
                    && config.c2bLastResponseDescription() == null
                    && config.c2bLastOriginatorConversationId() == null) {
                return null;
            }
            return new C2bRegistrationSummary(
                    config.c2bConfirmationUrl(),
                    config.c2bValidationUrl(),
                    config.c2bResponseType(),
                    config.c2bLastRegisteredAt(),
                    config.c2bLastRequestedAt(),
                    config.c2bLastResponseCode(),
                    config.c2bLastResponseDescription(),
                    config.c2bLastOriginatorConversationId());
        }
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

        public static TenantPaymentChannelResponse from(TenantPaymentChannel channel, TenantMpesaIntegrationConfig config) {
            return new TenantPaymentChannelResponse(
                    channel.getId(),
                    channel.getTenantId(),
                    channel.getChannelType(),
                    channel.getShortCode(),
                    channel.isActive(),
                    channel.getDescription(),
                    config == null
                            ? null
                            : new TenantMpesaIntegrationConfigSummary(
                                    config.environment(),
                                    config.businessShortCode(),
                                    config.callbackUrl(),
                                    config.hasEncryptedCredentials(),
                                    C2bRegistrationSummary.from(config)),
                    channel.getCreatedAt(),
                    channel.getUpdatedAt());
        }
    }

    @Schema(name = "RegisterTenantC2bUrlsRequest")
    public record RegisterTenantC2bUrlsRequest(
            @NotBlank String confirmationUrl,
            String validationUrl,
            @NotBlank
            @Pattern(regexp = "^(Completed|Cancelled)$", message = "responseType must be Completed or Cancelled")
            String responseType) {
    }

    @Schema(name = "RegisterTenantC2bUrlsResponse")
    public record RegisterTenantC2bUrlsResponse(
            String tenantId,
            String channelId,
            String shortCode,
            String confirmationUrl,
            String validationUrl,
            String responseType,
            Instant requestedAt,
            Instant registeredAt,
            String responseCode,
            String responseDescription,
            String originatorConversationId) {
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

    @Schema(name = "DarajaValidationResponse")
    public record DarajaValidationResponse(
            String ResultCode,
            String ResultDesc) {
    }

    @Schema(name = "DepositPaymentResponse")
    public record DepositPaymentResponse(
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
            DepositPaymentStatus status,
            String matchedApplicationId,
            String matchedFineractClientId,
            String failureReason,
            Instant createdAt,
            Instant updatedAt) {

        public static DepositPaymentResponse from(DepositPayment payment) {
            return new DepositPaymentResponse(
                    payment.getId(),
                    payment.getTenantId(),
                    payment.getBusinessShortCode(),
                    payment.getBillRefNumber(),
                    payment.getNormalizedPhoneNumber(),
                    payment.getTransactionAmount(),
                    payment.getTransactionTime(),
                    payment.getMpesaReceiptNumber(),
                    payment.getMsisdn(),
                    payment.getPayerFirstName(),
                    payment.getPayerMiddleName(),
                    payment.getPayerLastName(),
                    payment.getStatus(),
                    payment.getMatchedApplicationId(),
                    payment.getMatchedFineractClientId(),
                    payment.getFailureReason(),
                    payment.getCreatedAt(),
                    payment.getUpdatedAt());
        }
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

        public static MpesaPaymentReceiptResponse from(MpesaPaymentReceipt receipt) {
            return new MpesaPaymentReceiptResponse(
                    receipt.getId(),
                    receipt.getTenantId(),
                    receipt.getBusinessShortCode(),
                    receipt.getBillRefNumber(),
                    receipt.getNormalizedPhoneNumber(),
                    receipt.getTransactionAmount(),
                    receipt.getTransactionTime(),
                    receipt.getMpesaReceiptNumber(),
                    receipt.getMsisdn(),
                    receipt.getPayerFirstName(),
                    receipt.getPayerMiddleName(),
                    receipt.getPayerLastName(),
                    receipt.getProcessingStatus(),
                    receipt.getMatchedApplicationId(),
                    receipt.getMatchedFineractClientId(),
                    receipt.getMatchedFineractLoanId(),
                    receipt.getFineractTransactionId(),
                    receipt.getFailureReason(),
                    receipt.getProcessingStartedAt(),
                    receipt.getProcessedAt(),
                    receipt.getCreatedAt(),
                    receipt.getUpdatedAt());
        }
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
