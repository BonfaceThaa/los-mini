package com.credvenn.lm.payment;

public enum MpesaPaymentProcessingStatus {
    RECEIVED,
    PROCESSING,
    MATCHED,
    LOAN_NOT_FOUND,
    REPAYMENT_POSTED,
    REPAYMENT_FAILED,
    FAILED
}
