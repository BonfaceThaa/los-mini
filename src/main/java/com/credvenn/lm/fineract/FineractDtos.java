package com.credvenn.lm.fineract;

import io.swagger.v3.oas.annotations.media.Schema;
import com.credvenn.lm.loanproduct.LoanProductMapping;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class FineractDtos {

    private FineractDtos() {
    }

    @Schema(name = "LoanProductResponse")
    public record LoanProductResponse(
            String id,
            String productCode,
            String name,
            String shortName,
            String description,
            BigDecimal minPrincipal,
            BigDecimal maxPrincipal,
            Integer minNumberOfRepayments,
            Integer maxNumberOfRepayments,
            Integer loanType,
            Integer interestType,
            Integer interestCalculationPeriodType,
            BigDecimal interestRatePerPeriod,
            Integer amortizationType,
            Integer interestRateFrequencyType,
            Integer repaymentEvery,
            Integer repaymentFrequencyType,
            Integer numberOfRepayments,
            String currencyCode) {

        public static LoanProductResponse from(FineractLoanProduct product) {
            return new LoanProductResponse(
                    product.id(),
                    null,
                    product.name(),
                    product.shortName(),
                    null,
                    product.minPrincipal(),
                    product.maxPrincipal(),
                    product.minNumberOfRepayments(),
                    product.maxNumberOfRepayments(),
                    product.loanType(),
                    product.interestType(),
                    product.interestCalculationPeriodType(),
                    product.interestRatePerPeriod(),
                    product.amortizationType(),
                    product.interestRateFrequencyType(),
                    product.repaymentEvery(),
                    product.repaymentFrequencyType(),
                    product.numberOfRepayments(),
                    product.currencyCode());
        }

        public static LoanProductResponse from(LoanProductMapping mapping) {
            return new LoanProductResponse(
                    String.valueOf(mapping.getFineractProductId()),
                    mapping.getProductCode(),
                    mapping.getDisplayName(),
                    mapping.getShortName(),
                    mapping.getDescription(),
                    mapping.getPrincipalMin(),
                    mapping.getPrincipalMax(),
                    mapping.getNumberOfRepayments(),
                    mapping.getNumberOfRepayments(),
                    null,
                    interestTypeId(mapping.getInterestType()),
                    interestCalculationPeriodTypeId(mapping.getInterestCalculationPeriodType()),
                    mapping.getInterestRatePerPeriod(),
                    amortizationTypeId(mapping.getAmortizationType()),
                    interestRateFrequencyTypeId(mapping.getInterestRateFrequency()),
                    mapping.getRepaymentEvery(),
                    repaymentFrequencyTypeId(mapping.getRepaymentFrequency()),
                    mapping.getNumberOfRepayments(),
                    mapping.getCurrencyCode());
        }

        private static Integer interestTypeId(String value) {
            return switch (normalize(value)) {
                case "DECLINING_BALANCE" -> 0;
                case "FLAT" -> 1;
                default -> null;
            };
        }

        private static Integer interestCalculationPeriodTypeId(String value) {
            return switch (normalize(value)) {
                case "DAILY" -> 0;
                case "SAME_AS_REPAYMENT_PERIOD" -> 1;
                default -> null;
            };
        }

        private static Integer amortizationTypeId(String value) {
            return switch (normalize(value)) {
                case "EQUAL_PRINCIPAL_PAYMENTS" -> 0;
                case "EQUAL_INSTALLMENTS" -> 1;
                default -> null;
            };
        }

        private static Integer interestRateFrequencyTypeId(String value) {
            return switch (normalize(value)) {
                case "MONTHS" -> 2;
                case "YEARS" -> 3;
                default -> null;
            };
        }

        private static Integer repaymentFrequencyTypeId(String value) {
            return switch (normalize(value)) {
                case "DAYS" -> 0;
                case "WEEKS" -> 1;
                case "MONTHS" -> 2;
                case "YEARS" -> 3;
                default -> null;
            };
        }

        private static String normalize(String value) {
            return value == null ? "" : value.trim().toUpperCase();
        }
    }

    @Schema(name = "LoanRepaymentResponse")
    public record LoanRepaymentResponse(
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

        public static LoanRepaymentResponse from(FineractGateway.LoanRepayment repayment) {
            return new LoanRepaymentResponse(
                    repayment.id(),
                    repayment.transactionDate(),
                    repayment.amount(),
                    repayment.principalPortion(),
                    repayment.interestPortion(),
                    repayment.feeChargesPortion(),
                    repayment.penaltyChargesPortion(),
                    repayment.overpaymentPortion(),
                    repayment.outstandingLoanBalance(),
                    repayment.reversed(),
                    repayment.typeCode(),
                    repayment.typeValue());
        }
    }

    @Schema(name = "LoanRepaymentListResponse")
    public record LoanRepaymentListResponse(List<LoanRepaymentResponse> repayments) {
    }

    @Schema(name = "RepaymentScheduleCurrencyResponse")
    public record RepaymentScheduleCurrencyResponse(
            String code,
            String name,
            Integer decimalPlaces,
            Integer inMultiplesOf,
            String displaySymbol,
            String nameCode,
            String displayLabel) {

        public static RepaymentScheduleCurrencyResponse from(FineractGateway.RepaymentScheduleCurrency currency) {
            if (currency == null) {
                return null;
            }
            return new RepaymentScheduleCurrencyResponse(
                    currency.code(),
                    currency.name(),
                    currency.decimalPlaces(),
                    currency.inMultiplesOf(),
                    currency.displaySymbol(),
                    currency.nameCode(),
                    currency.displayLabel());
        }
    }

    @Schema(name = "RepaymentSchedulePeriodResponse")
    public record RepaymentSchedulePeriodResponse(
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

        public static RepaymentSchedulePeriodResponse from(FineractGateway.RepaymentSchedulePeriod period) {
            return new RepaymentSchedulePeriodResponse(
                    period.period(),
                    period.fromDate(),
                    period.dueDate(),
                    period.complete(),
                    period.daysInPeriod(),
                    period.principalDisbursed(),
                    period.principalOriginalDue(),
                    period.principalDue(),
                    period.principalPaid(),
                    period.principalWrittenOff(),
                    period.principalOutstanding(),
                    period.principalLoanBalanceOutstanding(),
                    period.interestOriginalDue(),
                    period.interestDue(),
                    period.interestPaid(),
                    period.interestWaived(),
                    period.interestWrittenOff(),
                    period.interestOutstanding(),
                    period.feeChargesDue(),
                    period.feeChargesPaid(),
                    period.feeChargesWaived(),
                    period.feeChargesWrittenOff(),
                    period.feeChargesOutstanding(),
                    period.penaltyChargesDue(),
                    period.penaltyChargesPaid(),
                    period.penaltyChargesWaived(),
                    period.penaltyChargesWrittenOff(),
                    period.penaltyChargesOutstanding(),
                    period.totalOriginalDueForPeriod(),
                    period.totalDueForPeriod(),
                    period.totalPaidForPeriod(),
                    period.totalPaidInAdvanceForPeriod(),
                    period.totalPaidLateForPeriod(),
                    period.totalWaivedForPeriod(),
                    period.totalWrittenOffForPeriod(),
                    period.totalOutstandingForPeriod(),
                    period.totalOverdue(),
                    period.totalActualCostOfLoanForPeriod(),
                    period.totalInstallmentAmountForPeriod(),
                    period.totalCredits(),
                    period.totalAccruedInterest(),
                    period.downPaymentPeriod());
        }
    }

    @Schema(name = "RepaymentScheduleResponse")
    public record RepaymentScheduleResponse(
            RepaymentScheduleCurrencyResponse currency,
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
            List<RepaymentSchedulePeriodResponse> periods) {

        public static RepaymentScheduleResponse from(FineractGateway.RepaymentSchedule schedule) {
            return new RepaymentScheduleResponse(
                    RepaymentScheduleCurrencyResponse.from(schedule.currency()),
                    schedule.loanTermInDays(),
                    schedule.totalPrincipalDisbursed(),
                    schedule.totalPrincipalExpected(),
                    schedule.totalPrincipalPaid(),
                    schedule.totalInterestCharged(),
                    schedule.totalFeeChargesCharged(),
                    schedule.totalPenaltyChargesCharged(),
                    schedule.totalWaived(),
                    schedule.totalWrittenOff(),
                    schedule.totalRepaymentExpected(),
                    schedule.totalRepayment(),
                    schedule.totalPaidInAdvance(),
                    schedule.totalPaidLate(),
                    schedule.totalOutstanding(),
                    schedule.totalCredits(),
                    schedule.periods().stream().map(RepaymentSchedulePeriodResponse::from).toList());
        }
    }

    @Schema(name = "FineractClientResponse")
    public record FineractClientResponse(
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

        public static FineractClientResponse from(FineractGateway.FineractClient client) {
            return new FineractClientResponse(
                    client.id(),
                    client.accountNo(),
                    client.externalId(),
                    client.status(),
                    client.active(),
                    client.firstname(),
                    client.middlename(),
                    client.lastname(),
                    client.displayName(),
                    client.mobileNo(),
                    client.officeName());
        }
    }

    @Schema(name = "JournalEntryCodeValueResponse")
    public record JournalEntryCodeValueResponse(
            Integer id,
            String code,
            String value) {

        public static JournalEntryCodeValueResponse from(FineractGateway.JournalEntryCodeValue value) {
            if (value == null) {
                return null;
            }
            return new JournalEntryCodeValueResponse(value.id(), value.code(), value.value());
        }
    }

    @Schema(name = "JournalEntryResponse")
    public record JournalEntryResponse(
            Long id,
            Integer officeId,
            String officeName,
            String glAccountName,
            Long glAccountId,
            String glAccountCode,
            JournalEntryCodeValueResponse glAccountType,
            LocalDate transactionDate,
            JournalEntryCodeValueResponse entryType,
            BigDecimal amount,
            String transactionId,
            Boolean manualEntry,
            JournalEntryCodeValueResponse entityType,
            Long entityId,
            Long createdByUserId,
            LocalDate createdDate,
            String createdByUserName,
            Boolean reversed,
            BigDecimal runningBalance,
            Map<String, Object> additionalDetails) {

        public static JournalEntryResponse from(FineractGateway.JournalEntry entry) {
            return new JournalEntryResponse(
                    entry.id(),
                    entry.officeId(),
                    entry.officeName(),
                    entry.glAccountName(),
                    entry.glAccountId(),
                    entry.glAccountCode(),
                    JournalEntryCodeValueResponse.from(entry.glAccountType()),
                    entry.transactionDate(),
                    JournalEntryCodeValueResponse.from(entry.entryType()),
                    entry.amount(),
                    entry.transactionId(),
                    entry.manualEntry(),
                    JournalEntryCodeValueResponse.from(entry.entityType()),
                    entry.entityId(),
                    entry.createdByUserId(),
                    entry.createdDate(),
                    entry.createdByUserName(),
                    entry.reversed(),
                    entry.runningBalance(),
                    entry.additionalDetails());
        }
    }

    @Schema(name = "JournalEntryListResponse")
    public record JournalEntryListResponse(
            Integer totalFilteredRecords,
            List<JournalEntryResponse> pageItems) {

        public static JournalEntryListResponse from(FineractGateway.JournalEntryPage page) {
            return new JournalEntryListResponse(
                    page.totalFilteredRecords(),
                    page.pageItems().stream().map(JournalEntryResponse::from).toList());
        }
    }
}
