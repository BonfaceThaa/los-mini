package com.credvenn.lm.fineract;

import com.credvenn.lm.application.LoanRequestApplication;
import com.credvenn.lm.tenant.Tenant;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

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

    record UpdateLoanProductRequest(
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

    record CreateGlAccountRequest(
            String name,
            String glCode,
            Boolean manualEntriesAllowed,
            Integer type,
            Integer usage,
            Long parentId,
            Long tagId,
            String description) {
    }

    record CreatedGlAccount(
            Long id,
            String glCode) {
    }

    record CreateAccountingRuleRequest(
            String name,
            Long officeId,
            Long accountToDebit,
            Long accountToCredit,
            String description) {
    }

    record CreatedAccountingRule(
            Long id,
            String name) {
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

    record RepaymentScheduleCurrency(
            String code,
            String name,
            Integer decimalPlaces,
            Integer inMultiplesOf,
            String displaySymbol,
            String nameCode,
            String displayLabel) {
    }

    record RepaymentSchedulePeriod(
            Integer period,
            LocalDate fromDate,
            LocalDate dueDate,
            Boolean complete,
            Integer daysInPeriod,
            BigDecimal principalDisbursed,
            BigDecimal principalOriginalDue,
            BigDecimal principalDue,
            BigDecimal principalPaid,
            BigDecimal principalWrittenOff,
            BigDecimal principalOutstanding,
            BigDecimal principalLoanBalanceOutstanding,
            BigDecimal interestOriginalDue,
            BigDecimal interestDue,
            BigDecimal interestPaid,
            BigDecimal interestWaived,
            BigDecimal interestWrittenOff,
            BigDecimal interestOutstanding,
            BigDecimal feeChargesDue,
            BigDecimal feeChargesPaid,
            BigDecimal feeChargesWaived,
            BigDecimal feeChargesWrittenOff,
            BigDecimal feeChargesOutstanding,
            BigDecimal penaltyChargesDue,
            BigDecimal penaltyChargesPaid,
            BigDecimal penaltyChargesWaived,
            BigDecimal penaltyChargesWrittenOff,
            BigDecimal penaltyChargesOutstanding,
            BigDecimal totalOriginalDueForPeriod,
            BigDecimal totalDueForPeriod,
            BigDecimal totalPaidForPeriod,
            BigDecimal totalPaidInAdvanceForPeriod,
            BigDecimal totalPaidLateForPeriod,
            BigDecimal totalWaivedForPeriod,
            BigDecimal totalWrittenOffForPeriod,
            BigDecimal totalOutstandingForPeriod,
            BigDecimal totalOverdue,
            BigDecimal totalActualCostOfLoanForPeriod,
            BigDecimal totalInstallmentAmountForPeriod,
            BigDecimal totalCredits,
            BigDecimal totalAccruedInterest,
            Boolean downPaymentPeriod) {
    }

    record RepaymentSchedule(
            RepaymentScheduleCurrency currency,
            Integer loanTermInDays,
            BigDecimal totalPrincipalDisbursed,
            BigDecimal totalPrincipalExpected,
            BigDecimal totalPrincipalPaid,
            BigDecimal totalInterestCharged,
            BigDecimal totalFeeChargesCharged,
            BigDecimal totalPenaltyChargesCharged,
            BigDecimal totalWaived,
            BigDecimal totalWrittenOff,
            BigDecimal totalRepaymentExpected,
            BigDecimal totalRepayment,
            BigDecimal totalPaidInAdvance,
            BigDecimal totalPaidLate,
            BigDecimal totalOutstanding,
            BigDecimal totalCredits,
            List<RepaymentSchedulePeriod> periods) {
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

    record JournalEntryCodeValue(
            Integer id,
            String code,
            String value) {
    }

    record JournalEntry(
            Long id,
            Integer officeId,
            String officeName,
            String glAccountName,
            Long glAccountId,
            String glAccountCode,
            JournalEntryCodeValue glAccountType,
            LocalDate transactionDate,
            JournalEntryCodeValue entryType,
            BigDecimal amount,
            String transactionId,
            Boolean manualEntry,
            JournalEntryCodeValue entityType,
            Long entityId,
            Long createdByUserId,
            LocalDate createdDate,
            String createdByUserName,
            Boolean reversed,
            BigDecimal runningBalance,
            Map<String, Object> additionalDetails) {
    }

    record JournalEntryPage(
            Integer totalFilteredRecords,
            List<JournalEntry> pageItems) {
    }

    record JournalEntryQuery(
            Integer offset,
            Integer limit,
            String orderBy,
            String sortBy,
            Integer officeId,
            Long glAccountId,
            Boolean manualEntriesOnly,
            LocalDate fromDate,
            LocalDate toDate,
            String transactionId,
            Boolean transactionDetails,
            Boolean runningBalance,
            Long loanId) {
    }

    String createClient(Tenant tenant, LoanRequestApplication application);

    List<FineractLoanProduct> fetchActiveLoanProducts(Tenant tenant);

    List<FineractClient> fetchClients(Tenant tenant);

    FineractClient fetchClient(Tenant tenant, String fineractClientId);

    String createLoanProduct(Tenant tenant, CreateLoanProductRequest request);

    void updateLoanProduct(Tenant tenant, String fineractProductId, UpdateLoanProductRequest request);

    CreatedGlAccount createGlAccount(Tenant tenant, CreateGlAccountRequest request);

    CreatedAccountingRule createAccountingRule(Tenant tenant, CreateAccountingRuleRequest request);

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

    RepaymentSchedule fetchLoanRepaymentSchedule(Tenant tenant, String fineractLoanId);

    JournalEntryPage fetchJournalEntries(Tenant tenant, JournalEntryQuery query);

    String postLoanRepayment(Tenant tenant, String fineractLoanId, LoanRepaymentRequest request);
}
