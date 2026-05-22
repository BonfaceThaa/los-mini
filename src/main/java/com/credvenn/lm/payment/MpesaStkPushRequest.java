package com.credvenn.lm.payment;

import com.credvenn.lm.common.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "mpesa_stk_push_requests")
public class MpesaStkPushRequest extends AuditableEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "application_id", length = 36)
    private String applicationId;

    @Column(name = "fineract_loan_id", length = 100)
    private String fineractLoanId;

    @Column(name = "requested_phone_number", nullable = false, length = 50)
    private String requestedPhoneNumber;

    @Column(name = "normalized_phone_number", nullable = false, length = 50)
    private String normalizedPhoneNumber;

    @Column(name = "installment_amount", precision = 19, scale = 2)
    private BigDecimal installmentAmount;

    @Column(name = "business_short_code", length = 50)
    private String businessShortCode;

    @Column(name = "bill_ref_number", length = 100)
    private String billRefNumber;

    @Column(name = "service_name", nullable = false, length = 100)
    private String serviceName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private MpesaStkPushRequestStatus status;

    @Column(name = "merchant_request_id", length = 100)
    private String merchantRequestId;

    @Column(name = "checkout_request_id", length = 100)
    private String checkoutRequestId;

    @Column(name = "provider_response_code", length = 50)
    private String providerResponseCode;

    @Column(name = "provider_response_description", length = 500)
    private String providerResponseDescription;

    @Column(name = "provider_customer_message", length = 500)
    private String providerCustomerMessage;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "initiated_at")
    private Instant initiatedAt;

    @Column(name = "raw_provider_response", columnDefinition = "TEXT")
    private String rawProviderResponse;

    @PrePersist
    void assignId() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
    }

    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getApplicationId() { return applicationId; }
    public void setApplicationId(String applicationId) { this.applicationId = applicationId; }
    public String getFineractLoanId() { return fineractLoanId; }
    public void setFineractLoanId(String fineractLoanId) { this.fineractLoanId = fineractLoanId; }
    public String getRequestedPhoneNumber() { return requestedPhoneNumber; }
    public void setRequestedPhoneNumber(String requestedPhoneNumber) { this.requestedPhoneNumber = requestedPhoneNumber; }
    public String getNormalizedPhoneNumber() { return normalizedPhoneNumber; }
    public void setNormalizedPhoneNumber(String normalizedPhoneNumber) { this.normalizedPhoneNumber = normalizedPhoneNumber; }
    public BigDecimal getInstallmentAmount() { return installmentAmount; }
    public void setInstallmentAmount(BigDecimal installmentAmount) { this.installmentAmount = installmentAmount; }
    public String getBusinessShortCode() { return businessShortCode; }
    public void setBusinessShortCode(String businessShortCode) { this.businessShortCode = businessShortCode; }
    public String getBillRefNumber() { return billRefNumber; }
    public void setBillRefNumber(String billRefNumber) { this.billRefNumber = billRefNumber; }
    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
    public MpesaStkPushRequestStatus getStatus() { return status; }
    public void setStatus(MpesaStkPushRequestStatus status) { this.status = status; }
    public String getMerchantRequestId() { return merchantRequestId; }
    public void setMerchantRequestId(String merchantRequestId) { this.merchantRequestId = merchantRequestId; }
    public String getCheckoutRequestId() { return checkoutRequestId; }
    public void setCheckoutRequestId(String checkoutRequestId) { this.checkoutRequestId = checkoutRequestId; }
    public String getProviderResponseCode() { return providerResponseCode; }
    public void setProviderResponseCode(String providerResponseCode) { this.providerResponseCode = providerResponseCode; }
    public String getProviderResponseDescription() { return providerResponseDescription; }
    public void setProviderResponseDescription(String providerResponseDescription) { this.providerResponseDescription = providerResponseDescription; }
    public String getProviderCustomerMessage() { return providerCustomerMessage; }
    public void setProviderCustomerMessage(String providerCustomerMessage) { this.providerCustomerMessage = providerCustomerMessage; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public Instant getInitiatedAt() { return initiatedAt; }
    public void setInitiatedAt(Instant initiatedAt) { this.initiatedAt = initiatedAt; }
    public String getRawProviderResponse() { return rawProviderResponse; }
    public void setRawProviderResponse(String rawProviderResponse) { this.rawProviderResponse = rawProviderResponse; }
}
