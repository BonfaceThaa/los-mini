package com.credvenn.lm.devicecontrol;

public record LoanRepaymentPostedEvent(
        String tenantId,
        String applicationId,
        String fineractLoanId,
        String receiptId) {
}
