package com.credvenn.lm.fineract;

import com.credvenn.lm.application.LoanRequestApplication;
import com.credvenn.lm.tenant.Tenant;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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

    record FineractClient(
            String id,
            String accountNo,
            String externalId,
            String status,
            boolean active,
            String firstname,
            String middlename,
            String lastname,
            String displayName,
            String mobileNo,
            String officeName) {
    }

    record CreateLoanProductRequest(
            String name,
            String shortName,
            String description,
            String currencyCode,
            BigDecimal principal,
            BigDecimal minPrincipal,
            BigDecimal maxPrincipal,
            Integer numberOfRepayments,
            Integer repaymentEvery,
            Integer repaymentFrequencyType,
            BigDecimal interestRatePerPeriod,
            Integer interestRateFrequencyType,
            Integer interestType,
            Integer interestCalculationPeriodType,
            Integer amortizationType,
            String transactionProcessingStrategyCode,
            Long loanPortfolioAccountId,
            Long fundSourceAccountId,
            Long interestOnLoanAccountId,
            Long incomeFromFeeAccountId,
            Long incomeFromPenaltyAccountId,
            Long incomeFromRecoveryAccountId,
            Long writeOffAccountId,
            Long transfersInSuspenseAccountId,
            Long overpaymentLiabilityAccountId) {
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

    record LoanPageItem(
            String id,
            boolean active,
            String statusCode) {
    }

    record LoanPage(
            List<LoanPageItem> items,
            int offset,
            int limit,
            boolean hasNext) {
    }

    record InstallmentSnapshot(
            int installmentNumber,
            LocalDate dueDate,
            BigDecimal dueAmount,
            BigDecimal paidAmount,
            BigDecimal outstandingAmount,
            boolean overdue,
            boolean fullyPaid) {
    }

    record LoanCollectionsSnapshot(
            String fineractLoanId,
            boolean active,
            boolean hasOverdueInstallment,
            LocalDate oldestOverdueDate,
            long daysOverdue,
            LocalDate nextDueDate,
            List<InstallmentSnapshot> installments) {
    }

    String createClient(Tenant tenant, LoanRequestApplication application);

    List<FineractLoanProduct> fetchActiveLoanProducts(Tenant tenant);

    List<FineractClient> fetchClients(Tenant tenant);

    FineractClient fetchClient(Tenant tenant, String fineractClientId);

    String createLoanProduct(Tenant tenant, CreateLoanProductRequest request);

    String createPendingLoan(
            Tenant tenant,
            LoanRequestApplication application,
            FineractLoanProduct product,
            BigDecimal approvedAmount,
            Integer approvedTermMonths);

    void activateLoan(Tenant tenant, LoanRequestApplication application);

    LoanSummary getLoanSummary(Tenant tenant, String fineractLoanId);

    LoanPage fetchLoansPage(Tenant tenant, int offset, int limit);

    LoanCollectionsSnapshot fetchLoanCollectionsSnapshot(Tenant tenant, String fineractLoanId);

    List<LoanRepayment> fetchLoanRepayments(Tenant tenant, String fineractLoanId);

    String postLoanRepayment(Tenant tenant, String fineractLoanId, LoanRepaymentRequest request);
}
