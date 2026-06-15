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
@Table(name = "deposit_payments")
public class DepositPayment extends AuditableEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "business_short_code", nullable = false, length = 50)
    private String businessShortCode;

    @Column(name = "bill_ref_number", nullable = false, length = 100)
    private String billRefNumber;

    @Column(name = "normalized_phone_number", length = 50)
    private String normalizedPhoneNumber;

    @Column(name = "transaction_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal transactionAmount;

    @Column(name = "transaction_time", nullable = false)
    private Instant transactionTime;

    @Column(name = "mpesa_receipt_number", nullable = false, unique = true, length = 100)
    private String mpesaReceiptNumber;

    @Column(length = 50)
    private String msisdn;

    @Column(name = "payer_first_name")
    private String payerFirstName;

    @Column(name = "payer_middle_name")
    private String payerMiddleName;

    @Column(name = "payer_last_name")
    private String payerLastName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private DepositPaymentStatus status;

    @Column(name = "matched_application_id", length = 36)
    private String matchedApplicationId;

    @Column(name = "matched_fineract_client_id", length = 100)
    private String matchedFineractClientId;

    @Column(name = "raw_payload", columnDefinition = "TEXT")
    private String rawPayload;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @PrePersist
    void assignId() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
    }

    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getBusinessShortCode() { return businessShortCode; }
    public void setBusinessShortCode(String businessShortCode) { this.businessShortCode = businessShortCode; }
    public String getBillRefNumber() { return billRefNumber; }
    public void setBillRefNumber(String billRefNumber) { this.billRefNumber = billRefNumber; }
    public String getNormalizedPhoneNumber() { return normalizedPhoneNumber; }
    public void setNormalizedPhoneNumber(String normalizedPhoneNumber) { this.normalizedPhoneNumber = normalizedPhoneNumber; }
    public BigDecimal getTransactionAmount() { return transactionAmount; }
    public void setTransactionAmount(BigDecimal transactionAmount) { this.transactionAmount = transactionAmount; }
    public Instant getTransactionTime() { return transactionTime; }
    public void setTransactionTime(Instant transactionTime) { this.transactionTime = transactionTime; }
    public String getMpesaReceiptNumber() { return mpesaReceiptNumber; }
    public void setMpesaReceiptNumber(String mpesaReceiptNumber) { this.mpesaReceiptNumber = mpesaReceiptNumber; }
    public String getMsisdn() { return msisdn; }
    public void setMsisdn(String msisdn) { this.msisdn = msisdn; }
    public String getPayerFirstName() { return payerFirstName; }
    public void setPayerFirstName(String payerFirstName) { this.payerFirstName = payerFirstName; }
    public String getPayerMiddleName() { return payerMiddleName; }
    public void setPayerMiddleName(String payerMiddleName) { this.payerMiddleName = payerMiddleName; }
    public String getPayerLastName() { return payerLastName; }
    public void setPayerLastName(String payerLastName) { this.payerLastName = payerLastName; }
    public DepositPaymentStatus getStatus() { return status; }
    public void setStatus(DepositPaymentStatus status) { this.status = status; }
    public String getMatchedApplicationId() { return matchedApplicationId; }
    public void setMatchedApplicationId(String matchedApplicationId) { this.matchedApplicationId = matchedApplicationId; }
    public String getMatchedFineractClientId() { return matchedFineractClientId; }
    public void setMatchedFineractClientId(String matchedFineractClientId) { this.matchedFineractClientId = matchedFineractClientId; }
    public String getRawPayload() { return rawPayload; }
    public void setRawPayload(String rawPayload) { this.rawPayload = rawPayload; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
}
