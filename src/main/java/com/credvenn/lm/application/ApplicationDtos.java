package com.credvenn.lm.application;

import com.credvenn.lm.inventory.DepositType;
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

    @Schema(name = "ApplicationStatusHistoryResponse")
    public record ApplicationStatusHistoryResponse(
            String fromStatus,
            String toStatus,
            String changedBy,
            String reason) {
    }

    @Schema(name = "LoanRequestApplicationResponse")
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
            String statementOtp,
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
