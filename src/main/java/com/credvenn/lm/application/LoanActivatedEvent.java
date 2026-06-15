package com.credvenn.lm.application;

public record LoanActivatedEvent(
        String tenantId,
        String applicationId,
        String fineractLoanId,
        String actor) {
}
