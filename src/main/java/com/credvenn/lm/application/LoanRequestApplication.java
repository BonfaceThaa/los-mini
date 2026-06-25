package com.credvenn.lm.application;

import com.credvenn.lm.common.domain.AuditableEntity;
import com.credvenn.lm.inventory.DepositType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "loan_request_applications")
public class LoanRequestApplication extends AuditableEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "applicant_first_name", nullable = false)
    private String applicantFirstName;

    @Column(name = "applicant_middle_name")
    private String applicantMiddleName;

    @Column(name = "applicant_last_name", nullable = false)
    private String applicantLastName;

    @Column(name = "phone_number", nullable = false, length = 50)
    private String phoneNumber;

    @Column(name = "national_id", nullable = false, length = 100)
    private String nationalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "applicant_id_type", nullable = false, length = 30)
    private ApplicantIdType applicantIdType;

    @Column(name = "dob")
    private LocalDate dob;

    @Column(name = "gender", length = 50)
    private String gender;

    @Column(name = "statement_otp", length = 100)
    private String statementOtp;

    @Column(name = "requested_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal requestedAmount;

    @Column(name = "requested_term_months")
    private Integer requestedTermMonths;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ApplicationStatus status;

    @Column(name = "fineract_client_id", length = 100)
    private String fineractClientId;

    @Column(name = "fineract_loan_id", length = 100)
    private String fineractLoanId;

    @Column(name = "selected_fineract_product_id", length = 100)
    private String selectedFineractProductId;

    @Column(name = "selected_fineract_product_name")
    private String selectedFineractProductName;

    @Column(name = "selected_offer_at")
    private Instant selectedOfferAt;

    @Column(name = "consent_captured", nullable = false)
    private boolean consentCaptured;

    @Column(name = "consent_captured_by")
    private String consentCapturedBy;

    @Column(name = "consent_captured_at")
    private Instant consentCapturedAt;

    @Column(name = "consent_text_version", length = 100)
    private String consentTextVersion;

    @Column(name = "internal_approved", nullable = false)
    private boolean internalApproved;

    @Column(name = "approved_by")
    private String approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "approval_reason", length = 1000)
    private String approvalReason;

    @Column(name = "approved_amount", precision = 19, scale = 2)
    private BigDecimal approvedAmount;

    @Column(name = "approved_term_months")
    private Integer approvedTermMonths;

    @Column(name = "approved_fineract_product_id", length = 100)
    private String approvedFineractProductId;

    @Column(name = "approved_fineract_product_name")
    private String approvedFineractProductName;

    @Column(name = "assigned_device_id", length = 36)
    private String assignedDeviceId;

    @Column(name = "assigned_device_name")
    private String assignedDeviceName;

    @Column(name = "assigned_device_imei1", length = 100)
    private String assignedDeviceImei1;

    @Column(name = "assigned_device_imei2", length = 100)
    private String assignedDeviceImei2;

    @Column(name = "assigned_device_cash_price", precision = 19, scale = 2)
    private BigDecimal assignedDeviceCashPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "deposit_type", length = 30)
    private DepositType depositType;

    @Column(name = "deposit_value", precision = 19, scale = 2)
    private BigDecimal depositValue;

    @Column(name = "deposit_amount", precision = 19, scale = 2)
    private BigDecimal depositAmount;

    @Column(name = "installment_amount", precision = 19, scale = 2)
    private BigDecimal installmentAmount;

    @Column(name = "total_repayments", precision = 19, scale = 2)
    private BigDecimal totalRepayments;

    @Column(name = "total_payment", precision = 19, scale = 2)
    private BigDecimal totalPayment;

    @Column(name = "margin_amount", precision = 19, scale = 2)
    private BigDecimal marginAmount;

    @PrePersist
    void assignId() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
    }

    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getApplicantFirstName() { return applicantFirstName; }
    public void setApplicantFirstName(String applicantFirstName) { this.applicantFirstName = applicantFirstName; }
    public String getApplicantMiddleName() { return applicantMiddleName; }
    public void setApplicantMiddleName(String applicantMiddleName) { this.applicantMiddleName = applicantMiddleName; }
    public String getApplicantLastName() { return applicantLastName; }
    public void setApplicantLastName(String applicantLastName) { this.applicantLastName = applicantLastName; }
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    public String getNationalId() { return nationalId; }
    public void setNationalId(String nationalId) { this.nationalId = nationalId; }
    public ApplicantIdType getApplicantIdType() { return applicantIdType; }
    public void setApplicantIdType(ApplicantIdType applicantIdType) { this.applicantIdType = applicantIdType; }
    public LocalDate getDob() { return dob; }
    public void setDob(LocalDate dob) { this.dob = dob; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public String getStatementOtp() { return statementOtp; }
    public void setStatementOtp(String statementOtp) { this.statementOtp = statementOtp; }
    public BigDecimal getRequestedAmount() { return requestedAmount; }
    public void setRequestedAmount(BigDecimal requestedAmount) { this.requestedAmount = requestedAmount; }
    public Integer getRequestedTermMonths() { return requestedTermMonths; }
    public void setRequestedTermMonths(Integer requestedTermMonths) { this.requestedTermMonths = requestedTermMonths; }
    public ApplicationStatus getStatus() { return status; }
    public void setStatus(ApplicationStatus status) { this.status = status; }
    public String getFineractClientId() { return fineractClientId; }
    public void setFineractClientId(String fineractClientId) { this.fineractClientId = fineractClientId; }
    public String getFineractLoanId() { return fineractLoanId; }
    public void setFineractLoanId(String fineractLoanId) { this.fineractLoanId = fineractLoanId; }
    public String getSelectedFineractProductId() { return selectedFineractProductId; }
    public void setSelectedFineractProductId(String selectedFineractProductId) { this.selectedFineractProductId = selectedFineractProductId; }
    public String getSelectedFineractProductName() { return selectedFineractProductName; }
    public void setSelectedFineractProductName(String selectedFineractProductName) { this.selectedFineractProductName = selectedFineractProductName; }
    public Instant getSelectedOfferAt() { return selectedOfferAt; }
    public void setSelectedOfferAt(Instant selectedOfferAt) { this.selectedOfferAt = selectedOfferAt; }
    public boolean isConsentCaptured() { return consentCaptured; }
    public void setConsentCaptured(boolean consentCaptured) { this.consentCaptured = consentCaptured; }
    public String getConsentCapturedBy() { return consentCapturedBy; }
    public void setConsentCapturedBy(String consentCapturedBy) { this.consentCapturedBy = consentCapturedBy; }
    public Instant getConsentCapturedAt() { return consentCapturedAt; }
    public void setConsentCapturedAt(Instant consentCapturedAt) { this.consentCapturedAt = consentCapturedAt; }
    public String getConsentTextVersion() { return consentTextVersion; }
    public void setConsentTextVersion(String consentTextVersion) { this.consentTextVersion = consentTextVersion; }
    public boolean isInternalApproved() { return internalApproved; }
    public void setInternalApproved(boolean internalApproved) { this.internalApproved = internalApproved; }
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
    public Instant getApprovedAt() { return approvedAt; }
    public void setApprovedAt(Instant approvedAt) { this.approvedAt = approvedAt; }
    public String getApprovalReason() { return approvalReason; }
    public void setApprovalReason(String approvalReason) { this.approvalReason = approvalReason; }
    public BigDecimal getApprovedAmount() { return approvedAmount; }
    public void setApprovedAmount(BigDecimal approvedAmount) { this.approvedAmount = approvedAmount; }
    public Integer getApprovedTermMonths() { return approvedTermMonths; }
    public void setApprovedTermMonths(Integer approvedTermMonths) { this.approvedTermMonths = approvedTermMonths; }
    public String getApprovedFineractProductId() { return approvedFineractProductId; }
    public void setApprovedFineractProductId(String approvedFineractProductId) { this.approvedFineractProductId = approvedFineractProductId; }
    public String getApprovedFineractProductName() { return approvedFineractProductName; }
    public void setApprovedFineractProductName(String approvedFineractProductName) { this.approvedFineractProductName = approvedFineractProductName; }
    public String getAssignedDeviceId() { return assignedDeviceId; }
    public void setAssignedDeviceId(String assignedDeviceId) { this.assignedDeviceId = assignedDeviceId; }
    public String getAssignedDeviceName() { return assignedDeviceName; }
    public void setAssignedDeviceName(String assignedDeviceName) { this.assignedDeviceName = assignedDeviceName; }
    public String getAssignedDeviceImei1() { return assignedDeviceImei1; }
    public void setAssignedDeviceImei1(String assignedDeviceImei1) { this.assignedDeviceImei1 = assignedDeviceImei1; }
    public String getAssignedDeviceImei2() { return assignedDeviceImei2; }
    public void setAssignedDeviceImei2(String assignedDeviceImei2) { this.assignedDeviceImei2 = assignedDeviceImei2; }
    public BigDecimal getAssignedDeviceCashPrice() { return assignedDeviceCashPrice; }
    public void setAssignedDeviceCashPrice(BigDecimal assignedDeviceCashPrice) { this.assignedDeviceCashPrice = assignedDeviceCashPrice; }
    public DepositType getDepositType() { return depositType; }
    public void setDepositType(DepositType depositType) { this.depositType = depositType; }
    public BigDecimal getDepositValue() { return depositValue; }
    public void setDepositValue(BigDecimal depositValue) { this.depositValue = depositValue; }
    public BigDecimal getDepositAmount() { return depositAmount; }
    public void setDepositAmount(BigDecimal depositAmount) { this.depositAmount = depositAmount; }
    public BigDecimal getInstallmentAmount() { return installmentAmount; }
    public void setInstallmentAmount(BigDecimal installmentAmount) { this.installmentAmount = installmentAmount; }
    public BigDecimal getTotalRepayments() { return totalRepayments; }
    public void setTotalRepayments(BigDecimal totalRepayments) { this.totalRepayments = totalRepayments; }
    public BigDecimal getTotalPayment() { return totalPayment; }
    public void setTotalPayment(BigDecimal totalPayment) { this.totalPayment = totalPayment; }
    public BigDecimal getMarginAmount() { return marginAmount; }
    public void setMarginAmount(BigDecimal marginAmount) { this.marginAmount = marginAmount; }
}
