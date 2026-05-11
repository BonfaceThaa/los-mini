package com.credvenn.lm.fineract;

import com.credvenn.lm.application.LoanRequestApplication;
import com.credvenn.lm.tenant.Tenant;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface FineractGateway {

    record LoanRepaymentRequest(
            String transactionDate,
            String transactionAmount,
            String paymentTypeId,
            String note,
            String accountNumber,
            String checkNumber,
            String routingCode,
            String receiptNumber,
            String bankNumber) {
    }

    record LoanSummary(String id, boolean active, String statusCode) {
    }

    record LoanRepayment(
            String id,
            Instant transactionDate,
            BigDecimal amount,
            BigDecimal principalPortion,
            BigDecimal interestPortion,
            BigDecimal feeChargesPortion,
            BigDecimal penaltyChargesPortion,
            BigDecimal overpaymentPortion,
            BigDecimal outstandingLoanBalance,
            boolean reversed,
            String typeCode,
            String typeValue) {
    }

    String createClient(Tenant tenant, LoanRequestApplication application);

    List<FineractLoanProduct> fetchActiveLoanProducts(Tenant tenant);

    String createPendingLoan(
            Tenant tenant,
            LoanRequestApplication application,
            FineractLoanProduct product,
            BigDecimal approvedAmount,
            Integer approvedTermMonths);

    void activateLoan(Tenant tenant, LoanRequestApplication application);

    LoanSummary getLoanSummary(Tenant tenant, String fineractLoanId);

    List<LoanRepayment> fetchLoanRepayments(Tenant tenant, String fineractLoanId);

    String postLoanRepayment(Tenant tenant, String fineractLoanId, LoanRepaymentRequest request);
}
