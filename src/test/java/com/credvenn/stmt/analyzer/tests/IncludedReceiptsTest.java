package com.credvenn.stmt.analyzer.tests;

import com.credvenn.stmt.analyzer.core.*;
import com.credvenn.stmt.analyzer.core.AnalysisResult.*;
import com.credvenn.stmt.analyzer.core.analysis.NarrationClassifier;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class IncludedReceiptsTest {
    @ParameterizedTest
    @CsvSource(delimiter='|',value={
        "CREDIT|Receive funds from SYN|Income|Personal receipts|P2P_RECEIPT|ELIGIBLE",
        "CREDIT|Business Payment from 300600 - SYN via API. Original conversation ID is SYN|Income|Business receipts|B2C_BUSINESS_PAYMENT|ELIGIBLE",
        "CREDIT|Salary Payment from 504900 - SYN|Income|Salary-labelled receipts|B2C_SALARY_PAYMENT|ELIGIBLE",
        "CREDIT|Transfer from Bank 517819 - SYN to Customer via API|Income|Bank transfers in|BANK_TRANSFER_IN|ELIGIBLE",
        "CREDIT|Deposit of Funds at Agent Till 2011418 - SYN|Income|Cash deposits|CASH_DEPOSIT|ELIGIBLE",
        "CREDIT|PLB Merchant Business Deposit of Funds at Agent Till SYN|Income|Cash deposits|CASH_DEPOSIT|ELIGIBLE",
        "CREDIT|Offnet B2C Transfer by 966888 SYN via API|Income|Other-network receipts|OFFNET_RECEIPT|ELIGIBLE",
        "CREDIT|Receive International Zero Rated Transfer From 4133831 - SYN|Income|International remittances|INTERNATIONAL_REMITTANCE|ELIGIBLE",
        "CREDIT|Promotion Payment from 3033815 - SYN|Income|Promotions and commissions|PROMOTION_PAYMENT|ELIGIBLE",
        "CREDIT|Merchant Customer Payment from 9541476 - SYN|Income|Merchant and small-business receipts|MERCHANT_BUSINESS_RECEIPT|ELIGIBLE",
        "CREDIT|Customer Payment to Small Business from - SYN|Income|Merchant and small-business receipts|MERCHANT_BUSINESS_RECEIPT|ELIGIBLE",
        "CREDIT|Salary Payment from 546476 - SYN via API|Loans|Disbursement - other lender|LOAN_DISBURSEMENT|INELIGIBLE",
        "CREDIT|Business Payment from 979988 - SYN via API|Loans|Disbursement - other lender|LOAN_DISBURSEMENT|INELIGIBLE",
        "CREDIT|Funds received from Business 3031389 - SYN|Loans|Disbursement - other lender|LOAN_DISBURSEMENT|INELIGIBLE",
        "CREDIT|Salary Payment from 4033285 - SYN|Loans|Disbursement - other lender|LOAN_DISBURSEMENT|INELIGIBLE",
        "CREDIT|Business Payment from 3028197 - SYN|Loans|Disbursement - other lender|LOAN_DISBURSEMENT|INELIGIBLE",
        "CREDIT|Business Payment from 733755 - SYN|Loans|Disbursement - other lender|LOAN_DISBURSEMENT|INELIGIBLE",
        "CREDIT|M-Shwari Loan Disburse|Loans|Disbursement - M-Shwari|LOAN_DISBURSEMENT|INELIGIBLE",
        "DEBIT|Pay Bill Fuliza M-Pesa to 323458 - SYN|Loans|Repayment - Sun King|LOAN_REPAYMENT|ELIGIBLE",
        "DEBIT|Pay Bill to 323458 - SYN|Loans|Repayment - Sun King|LOAN_REPAYMENT|ELIGIBLE",
        "DEBIT|KCB M-PESA Loan Repayment|Loans|Repayment - KCB M-Pesa|LOAN_REPAYMENT|ELIGIBLE",
        "DEBIT|Pay Bill to 650880 - SYN|Loans|Repayment - other lender|LOAN_REPAYMENT|ELIGIBLE",
        "DEBIT|Merchant Payment to 6010640 - SYN|Loans|Repayment - other lender|LOAN_REPAYMENT|ELIGIBLE",
        "DEBIT|Pay Bill to 7034211 - SYN|Loans|Repayment - other lender|LOAN_REPAYMENT|ELIGIBLE",
        "DEBIT|Pay Bill to 333222 - SYN|Loans|Repayment - other lender|LOAN_REPAYMENT|ELIGIBLE",
        "DEBIT|Pay Bill to 4087825 - SYN|Loans|Repayment - other lender|LOAN_REPAYMENT|ELIGIBLE",
        "DEBIT|Pay Bill to 979988 - SYN|Loans|Repayment - other lender|LOAN_REPAYMENT|ELIGIBLE",
        "DEBIT|Customer Withdrawal At Agent Till 386106 - SYN Next to Faulu Microfinance|Transfers|Cash withdrawals|CASH_WITHDRAWAL|INELIGIBLE",
        "DEBIT|Customer Withdrawal at Agent Till with Fuliza to 463251 - SYN opp yehu micro fin|Transfers|Cash withdrawals|CASH_WITHDRAWAL|INELIGIBLE",
        "DEBIT|Small Business Withdrawal from MPESA Account to Business Account by - SYN|Transfers|Business account transfers|BUSINESS_ACCOUNT_TRANSFER|INELIGIBLE",
        "DEBIT|Offnet C2B Transfer to SYN|Transfers|Other-network transfers out|OFFNET_TRANSFER|INELIGIBLE",
        "DEBIT|Customer Payment to Small Business to - SYN|Spending|Small-business payments - unspecified|MICRO_SME_PAYMENT|ELIGIBLE",
        "DEBIT|Airtime Purchase|Spending|Airtime|AIRTIME_PURCHASE|ELIGIBLE",
        "DEBIT|Customer Bundle Purchase to SYN|Spending|Mobile data and bundles|MOBILE_DATA_PURCHASE|ELIGIBLE",
        "DEBIT|Pay Bill Charge|Spending|Transaction fees|TRANSACTION_FEE|ELIGIBLE",
        "DEBIT|Pay Merchant Charge|Spending|Transaction fees|TRANSACTION_FEE|ELIGIBLE",
        "DEBIT|Withdrawal Charge|Spending|Transaction fees|TRANSACTION_FEE|ELIGIBLE",
        "DEBIT|Pay Bill to 4990390 - SYN|Spending|Unspecified merchant or bill outflow|UNSPECIFIED_SPENDING|ELIGIBLE",
        "DEBIT|Merchant Payment to 1234567 - SYN ENTERPRISES|Spending|Unspecified merchant or bill outflow|UNSPECIFIED_SPENDING|ELIGIBLE"
    })
    void newFamiliesAndOverrides(Direction direction,String narration,String main,String sub,String purpose,BalanceEligibility eligibility) {
        for(String text:new String[]{narration,"  "+narration.toLowerCase().replace(" ","\n ")+"  "}) {
            var c=new NarrationClassifier().classify(direction,text);
            assertEquals(main,c.mainCategory());assertEquals(sub,c.subCategory());assertEquals(purpose,c.purposeClassification());
            assertEquals(eligibility,c.balanceEligibility());
        }
    }
    @ParameterizedTest
    @CsvSource(delimiter='|',value={
        "8314985|Food", "7452695|Airtime", "7083054|Health", "888880|Energy", "573068|Supermarket",
        "809888|Travel", "222222|Government", "308900|Ticketing", "884621|Fuel", "7305816|Entertainment",
        "5486348|Church", "590555|Gambling", "4623230|Online business", "913350|Water",
        "7092021|Stationery / books", "7839177|Beauty", "4187807|Education"
    })
    void suppliedMerchantSubcategoriesHaveStablePurposeAndFunding(String payee,String sub) {
        var c=new NarrationClassifier().classify(Direction.DEBIT,"Merchant Payment Fuliza M-Pesa Online to "+payee+" - SYN");
        assertEquals("Spending",c.mainCategory());assertEquals(sub,c.subCategory());assertEquals("FULIZA",c.fundingSource());
        assertEquals(BalanceEligibility.ELIGIBLE,c.balanceEligibility());
    }
    @ParameterizedTest
    @CsvSource(delimiter='|',value={
        "SYN RESTAURANT|Food", "SYN CHEMIST|Health", "SYN PETROLEUM|Fuel", "SYN SUPERMARKET|Supermarket",
        "SYN LOUNGE|Entertainment", "SYN CHURCH|Church", "SPORTYBET SYN|Gambling", "SYN UNIVERSITY|Education",
        "SYN WATER|Water", "SYN TOURS AND TRAVELS|Travel", "SYN AIRTIME|Airtime", "KPLC SYN|Energy",
        "SYN COSMETICS|Beauty", "SYN BOOKSHOP|Stationery / books"
    })
    void purposeSuggestionsDoNotBlockSpendingWhenPayeeIsNotInExampleList(String name,String sub) {
        var c=new NarrationClassifier().classify(Direction.DEBIT,"Merchant Payment to 1234567 - "+name);
        assertEquals(sub,c.subCategory());assertEquals(BalanceEligibility.ELIGIBLE,c.balanceEligibility());
        assertTrue(c.explanation().startsWith("Suggested"));
    }
    @Test void fullPdfReturnsIncomeFormulaAndMutuallyExclusiveCategoryTotals() throws Exception {
        try(var pdf=getClass().getResourceAsStream("/fixtures/included-receipts.pdf");
                var expected=getClass().getResourceAsStream("/fixtures/included-receipts.expected.json")) {
            assertNotNull(pdf);assertNotNull(expected);
            var result=new DefaultStatementAnalyzer().analyze(new AnalysisInput(pdf.readAllBytes(),null,"MPESA","new-policy"));
            assertEquals(Status.COMPLETE,result.status());assertEquals("narration-v4",result.versions().classificationVersion());
            assertEquals("balance-v12",result.versions().metricPolicyVersion());
            assertEquals(new String(expected.readAllBytes(),StandardCharsets.UTF_8).strip(),AnalysisSmokeRunner.metricsProjection(result));
            var calculation=result.monthlyBreakdown().getFirst().incomeCalculation();
            assertEquals(0,new BigDecimal("9000.00").compareTo(calculation.includedReceipts()));
            assertEquals(0,new BigDecimal("300.00").compareTo(calculation.loanRepayments()));
            assertEquals(0,new BigDecimal("400.00").compareTo(calculation.spending()));
            assertEquals(0,new BigDecimal("8300.00").compareTo(calculation.monthlySurplus()));
            assertEquals(20,calculation.categoryBreakdown().stream().mapToInt(CategoryAmount::transactionCount).sum());
            assertEquals(9,calculation.categoryBreakdown().stream().filter(c->c.role()==CalculationRole.INFLOW).count());
            assertEquals(1,calculation.categoryBreakdown().stream().filter(c->c.subCategory().equals("Repayment - Sun King")&&c.role()==CalculationRole.DEBT_PAYMENT).count());
            assertEquals(0,calculation.categoryBreakdown().stream().filter(c->c.subCategory().equals("Energy")).count());
            assertTrue(calculation.formula().contains("Included receipts - Loan repayments - Spending"));
        }
    }
}
