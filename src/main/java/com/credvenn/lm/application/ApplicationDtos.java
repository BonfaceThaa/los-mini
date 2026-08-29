package com.credvenn.lm.application;

import com.credvenn.lm.fineract.FineractDtos;
import com.credvenn.lm.inventory.DepositType;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ApplicationDtos {

    private ApplicationDtos() {
    }

    @Schema(name = "CreateLoanRequestApplicationRequest")
    public record CreateLoanRequestApplicationRequest(
            @NotBlank @Size(max = 255) String applicantFirstName,
            @Size(max = 255) String applicantMiddleName,
            @NotBlank @Size(max = 255) String applicantLastName,
            @NotBlank @Size(max = 50) String phoneNumber,
            @NotBlank @Size(max = 100) String nationalId,
            @NotNull ApplicantIdType applicantIdType,
            LocalDate dob,
            @Size(max = 50) String gender,
            @Size(max = 100) String statementOtp,
            @NotNull @Positive BigDecimal requestedAmount,
            @Positive Integer requestedTermMonths) {
    }

    @Schema(name = "CaptureConsentRequest")
    public record CaptureConsentRequest(
            boolean accepted,
            @NotBlank @Size(max = 100) String consentTextVersion) {
    }

    @Schema(name = "SelectOfferRequest")
    public record SelectOfferRequest(@NotBlank String fineractProductId) {
    }

    @Schema(name = "InternalApprovalRequest")
    public record InternalApprovalRequest(@NotBlank @Size(max = 1000) String reason) {
    }

    @Schema(name = "AddStatementOtpsRequest")
    public record AddStatementOtpsRequest(
            @NotNull @Size(min = 1, max = 10) List<@NotBlank @Size(max = 100) String> otps) {
    }

    @Schema(name = "StatementOtpResponse", description = "Statement OTP candidate metadata for a single application")
    public record StatementOtpResponse(
            @Schema(description = "Unique statement OTP identifier", example = "otp-1") String id,
            @Schema(description = "Full OTP value returned for client-side masking", example = "123456") String otp,
            @Schema(description = "Current lifecycle status of this OTP candidate", example = "PENDING") ApplicationStatementOtpStatus status,
            @Schema(description = "How the OTP candidate was captured", example = "MANUAL_ADD") ApplicationStatementOtpSource source,
            @Schema(description = "When the OTP candidate was created", example = "2026-08-29T10:14:00Z") Instant createdAt,
            @Schema(description = "When the OTP candidate was last tested or reserved for submission", example = "2026-08-29T10:15:30Z") Instant testedAt,
            @Schema(description = "When the OTP candidate was successfully used", example = "2026-08-29T10:16:10Z") Instant usedAt,
            @Schema(description = "Failure reason if the OTP candidate failed validation or submission", example = "Invalid OTP") String failureReason) {
    }

    @Schema(name = "AddStatementOtpsResponse")
    public record AddStatementOtpsResponse(
            int addedCount,
            boolean retryQueued,
            String message,
            List<StatementOtpResponse> otps) {
    }

    @Schema(name = "ApplicationStatusHistoryResponse")
    public record ApplicationStatusHistoryResponse(
            String fromStatus,
            String toStatus,
            String changedBy,
            String reason) {
    }

    @Schema(name = "EligibleProductRequirementsResponse")
    public record EligibleProductRequirementsResponse(
            boolean kycApproved,
            boolean fineractClientCreated,
            boolean statementApproved) {
    }

    @Schema(name = "EligibleProductsResponse")
    public record EligibleProductsResponse(
            ApplicationStatus applicationStatus,
            boolean offersReady,
            String message,
            EligibleProductRequirementsResponse requirements,
            List<FineractDtos.LoanProductResponse> products) {
    }

    @Schema(name = "LoanRequestApplicationResponse", description = "Loan request application details for the authenticated tenant")
    public record LoanRequestApplicationResponse(
            String id,
            String tenantId,
            String applicantFirstName,
            String applicantMiddleName,
            String applicantLastName,
            String phoneNumber,
            String nationalId,
            ApplicantIdType applicantIdType,
            LocalDate dob,
            String gender,
            @Schema(description = "Legacy single statement OTP field retained for backward compatibility", example = "432198") String statementOtp,
            @ArraySchema(schema = @Schema(implementation = StatementOtpResponse.class), arraySchema = @Schema(description = "All statement OTP candidates currently stored for this application"))
            List<StatementOtpResponse> statementOtps,
            BigDecimal requestedAmount,
            Integer requestedTermMonths,
            ApplicationStatus status,
            String fineractClientId,
            String fineractLoanId,
            String selectedFineractProductId,
            String selectedFineractProductName,
            boolean consentCaptured,
            String consentTextVersion,
            boolean internalApproved,
            String approvedBy,
            Instant approvedAt,
            String approvedFineractProductId,
            String approvedFineractProductName,
            BigDecimal approvedAmount,
            Integer approvedTermMonths,
            String assignedDeviceId,
            String assignedDeviceName,
            String assignedDeviceImei1,
            String assignedDeviceImei2,
            BigDecimal assignedDeviceCashPrice,
            DepositType depositType,
            BigDecimal depositValue,
            BigDecimal depositAmount,
            BigDecimal installmentAmount,
            BigDecimal totalRepayments,
            BigDecimal totalPayment,
            BigDecimal marginAmount,
            Instant createdAt,
            Instant updatedAt,
            List<ApplicationStatusHistoryResponse> statusHistory) {
    }
}
