package com.credvenn.stmt.analyzer.tests;
import com.credvenn.stmt.analyzer.core.AnalysisResult.*;
import com.credvenn.stmt.analyzer.core.analysis.NarrationClassifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;
class LoanProviderTest {
    private final NarrationClassifier classifier=new NarrationClassifier();
    @ParameterizedTest @CsvSource({"7034211,MOGO","333222,M-KOPA","851900,Tala","998608,Branch","650880,Watu","932900,Ngao Credit","4073865,LendPlus"})
    void verifiedRepaymentPaybillsOverrideGenericMerchantPurpose(String paybill,String provider) {
        var c=classifier.classify(Direction.DEBIT,"Pay Bill Fuliza M-Pesa to "+paybill+" - SYN Acc. SYN");
        assertEquals(provider,c.loanProvider());assertEquals("DEBT_REPAYMENT",c.economicCategory());
        assertEquals(BalanceEligibility.ELIGIBLE,c.balanceEligibility());assertEquals("FULIZA",c.fundingSource());
    }
    @ParameterizedTest @CsvSource({"MOGO AUTO LIMITED,MOGO","M KOPA,M-KOPA","MKOPA,M-KOPA","TALA MOBILE,Tala",
        "BRANCH MICROFINANCE BANK,Branch","ZENKA DIGITAL LIMITED,Zenka","MYCREDIT LIMITED,MyCredit","JIJENGE CREDIT LIMITED,Jijenge Credit","LITTLE PESA LIMITED,Little Pesa"})
    void recognizedRecipientAliasesIdentifyProvider(String alias,String provider) {
        var c=classifier.classify(Direction.DEBIT,"Merchant Payment to 123456 - "+alias+" Acc. SYN");
        assertEquals(provider,c.loanProvider());assertEquals("DEBT_REPAYMENT",c.economicCategory());
    }
    @Test void aliasCreditsOverrideMisleadingSalaryButReversalsRemainReversals() {
        var c=classifier.classify(Direction.CREDIT,"Salary Payment from 123456 - MOGO AUTO LIMITED via API");
        assertEquals("MOGO",c.loanProvider());assertEquals(BalanceEligibility.INELIGIBLE,c.balanceEligibility());
        assertEquals("LOAN_DISBURSEMENT",c.transactionType());
        assertEquals("REVERSAL_CREDIT",classifier.classify(Direction.CREDIT,"Reversal from 123456 - MOGO AUTO LIMITED").transactionType());
    }
    @ParameterizedTest @CsvSource({"UNKNOWN CREDIT LIMITED","SYN MICROFINANCE"})
    void genericKeywordsAreCandidatesRatherThanVerifiedDebt(String recipient) {
        var c=classifier.classify(Direction.DEBIT,"Pay Bill to 123456 - "+recipient);
        assertEquals(BalanceEligibility.UNDETERMINED,c.balanceEligibility());
        assertEquals("POSSIBLE_LOAN_REPAYMENT",c.transactionType());
    }
    @ParameterizedTest @CsvSource({"Customer Withdrawal at Agent Till 123456 - SYN next to MOGO AUTO LIMITED",
        "Pay Bill to 123456 - STATIONERY Acc. MOGO CREDIT 851900",
        "Pay Bill to 300067 - TIMIZA Acc. SYN","Pay Bill to 522522 - KCB Acc. SYN",
        "Merchant Payment to 123456 - BRANCH STORE","Merchant Payment to 123456 - MOGOLAND",
        "Pay Bill to 4107195 - SYN"})
    void nearbyLendersAccountReferencesWalletDepositsAndExtensionFeesDoNotBecomeRepayments(String narration) {
        var c=classifier.classify(Direction.DEBIT,narration);
        assertNull(c.loanProvider());assertNotEquals("DEBT_REPAYMENT",c.economicCategory());
    }
    @Test void paybillWinsOverConflictingAliasAndExplicitProductsHaveProviders() {
        assertEquals("MOGO",classifier.classify(Direction.DEBIT,"Pay Bill to 7034211 - M-KOPA").loanProvider());
        assertEquals("Timiza",classifier.classify(Direction.DEBIT,"Timiza Loan Repayment").loanProvider());
        assertEquals("M-Shwari",classifier.classify(Direction.DEBIT,"M-Shwari Loan Repayment").loanProvider());
        assertEquals("KCB M-PESA",classifier.classify(Direction.DEBIT,"KCB M-PESA Loan Repayment").loanProvider());
        assertEquals("Fuliza",classifier.classify(Direction.DEBIT,"OD Loan Repayment to 3201003 - SYN").loanProvider());
    }
    @Test void feesAndBorrowingDoNotUseRepaymentPaybillAsProviderEvidence() {
        assertNull(classifier.classify(Direction.CREDIT,"Business Payment from 851900 - SYN").loanProvider());
        var fee=classifier.classify(Direction.DEBIT,"Pay Bill Charge to 7034211 - MOGO AUTO LIMITED");
        assertNull(fee.loanProvider());assertEquals("TRANSACTION_FEE",fee.transactionType());
    }
}
