package com.credvenn.stmt.analyzer.tests;

import com.credvenn.stmt.analyzer.core.AnalysisResult.*;
import com.credvenn.stmt.analyzer.core.analysis.NarrationClassifier;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class ClassificationTest {
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "CREDIT|OverDraft of Credit Party|OVERDRAFT_DISBURSEMENT|BORROWED_FUNDS|INELIGIBLE|UNVERIFIED",
        "CREDIT|Unit Trust Withdraw From SYN ZIIDI MMF|INVESTMENT_WITHDRAWAL|OWN_ASSET_TRANSFER|INELIGIBLE|UNVERIFIED",
        "CREDIT|Business Payment from SYN via API|B2C_BUSINESS_PAYMENT|BUSINESS_RECEIPT|ELIGIBLE|VERIFIED",
        "CREDIT|Salary Payment from SYN|B2C_SALARY_PAYMENT|POTENTIAL_SALARY|ELIGIBLE|VERIFIED",
        "CREDIT|Funds received from - SYN|P2P_RECEIPT|PERSONAL_TRANSFER|ELIGIBLE|VERIFIED",
        "CREDIT|Reversal SYN|REVERSAL_CREDIT|REVERSAL|INELIGIBLE|UNVERIFIED",
        "DEBIT|Airtime Purchase with Fuliza|AIRTIME_PURCHASE|TELECOM_EXPENSE|ELIGIBLE|NOT_APPLICABLE",
        "DEBIT|Customer Transfer Fuliza MPesa to - SYN|P2P_TRANSFER|UNVERIFIED_OUTGOING_TRANSFER|INELIGIBLE|NOT_APPLICABLE",
        "DEBIT|Merchant Payment Fuliza M-Pesa to SYN|MERCHANT_PAYMENT|MERCHANT_SPEND|ELIGIBLE|NOT_APPLICABLE",
        "DEBIT|OD Loan Repayment to SYN M-PESA Overdraw|OVERDRAFT_REPAYMENT|DEBT_REPAYMENT|ELIGIBLE|NOT_APPLICABLE",
        "DEBIT|Customer Transfer of Funds Charge|TRANSACTION_FEE|FINANCIAL_FEE|ELIGIBLE|NOT_APPLICABLE",
        "DEBIT|Customer Send Money to Micro SME Business with Fuliza MPesa to - SYN|MICRO_SME_PAYMENT|UNVERIFIED_BUSINESS_OR_PERSONAL_SPEND|ELIGIBLE|NOT_APPLICABLE",
        "CREDIT|Unexpected funds|UNKNOWN|UNKNOWN_SOURCE|UNDETERMINED|UNVERIFIED",
        "DEBIT|Unexpected spending|UNKNOWN|UNKNOWN_SPEND|UNDETERMINED|NOT_APPLICABLE"
    })
    void everyRuleHasIndependentClassificationAxes(Direction direction, String narration, String type,
            String category, BalanceEligibility eligibility, ReceiptVerification verification) {
        for (String value : new String[]{narration, "  " + narration.toLowerCase(java.util.Locale.ROOT).replace(" ", " \t ") + "  "}) {
            var result = new NarrationClassifier().classify(direction, value);
            assertEquals(type, result.transactionType()); assertEquals(category, result.economicCategory());
            assertEquals(eligibility, result.balanceEligibility()); assertEquals(verification, result.receiptVerification());
            assertTrue(result.ruleId().startsWith(NarrationClassifier.VERSION));
        }
    }
}
