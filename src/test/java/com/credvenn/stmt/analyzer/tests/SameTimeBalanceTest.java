package com.credvenn.stmt.analyzer.tests;

import com.credvenn.stmt.analyzer.core.AnalysisResult.*;
import com.credvenn.stmt.analyzer.core.analysis.*;
import com.credvenn.stmt.analyzer.core.parsing.ParsedStatement;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class SameTimeBalanceTest {
    static ParsedStatement.TransactionDraft row(String key,int second,String balance) {
        return SharedReceiptRulesTest.row(key,key,Direction.CREDIT,"10.00",balance,"Funds received from - SYN",second);
    }
    static ParsedStatement.TransactionDraft status(ParsedStatement.TransactionDraft r,String status) {
        return new ParsedStatement.TransactionDraft(r.sourceRowKey(),r.sourceTransactionId(),r.transactionDate(),r.transactionAt(),r.direction(),r.amount(),r.currency(),r.rawNarration(),r.normalizedNarration(),status,r.runningBalance(),r.sourceLines());
    }
    static ReconciliationResult check(List<ParsedStatement.TransactionDraft> rows,String opening,boolean totals) {
        BigDecimal credits=BigDecimal.ZERO,debits=BigDecimal.ZERO;
        for(var r:rows)if(r.direction()==Direction.CREDIT)credits=credits.add(r.amount());else debits=debits.add(r.amount());
        var p=FinancialAnalysisTest.statement(rows,new ParsedStatement.Summary(opening==null?null:new BigDecimal(opening),null,totals?credits:null,totals?debits:null,rows.size()),new StatementPeriod(LocalDate.of(2026,10,1),LocalDate.of(2026,10,31)));
        return new StatementReconciler().reconcile(p);
    }
    @Test void receiptRepaymentGroupCarriesItsNetEndingBalanceInsteadOfLastParsedBalance() {
        var repay=SharedReceiptRulesTest.row("repay","R",Direction.DEBIT,"252.28","747.72","OD Loan Repayment to SYN",0);
        var receipt=SharedReceiptRulesTest.row("receipt","I",Direction.CREDIT,"1000.00","1000.00","Funds received from - SYN",0);
        var purchase=SharedReceiptRulesTest.row("purchase","P",Direction.DEBIT,"180.00","567.72","Merchant Payment to SYN",1);
        var r=check(List.of(repay,receipt,purchase),"0.00",true);
        assertTrue(r.reconciled());assertTrue(r.errors().isEmpty());
        assertTrue(r.warnings().stream().anyMatch(w->w.code().equals("TIMESTAMP_GROUP_BALANCE_RESOLVED")));
    }
    @Test void overdraftPaymentFeeCycleCarriesZeroInsteadOfIntermediateFeeBalance() {
        var advance=SharedReceiptRulesTest.row("advance","REF",Direction.CREDIT,"187.00","187.00","OverDraft of Credit Party",0);
        var payment=SharedReceiptRulesTest.row("payment","REF",Direction.DEBIT,"180.00","0.00","Customer Send Money to Micro SME Business with Fuliza MPesa to - SYN",0);
        var fee=SharedReceiptRulesTest.row("fee","REF",Direction.DEBIT,"7.00","180.00","Pay Merchant Charge",0);
        var deposit=SharedReceiptRulesTest.row("deposit","DEP",Direction.CREDIT,"900.00","900.00","Deposit of Funds at Agent Till SYN",1);
        assertTrue(check(List.of(advance,payment,fee,deposit),"0.00",true).reconciled());
    }
    @ParameterizedTest @ValueSource(strings={"Pending","Failed"})
    void incompleteTransactionDoesNotJoinItsNeighborGroup(String status) {
        var r=check(List.of(row("one",0,"100.00"),status(row("two",0,"999.00"),status)),null,true);
        assertTrue(r.errors().stream().anyMatch(e->e.code().equals("RUNNING_BALANCE_MISMATCH")));
    }
    @Test void sameClockTimeOnDifferentDatesAndDateOnlyRowsRemainIndependent() {
        var first=row("one",0,"100.00");var r=row("two",0,"999.00");
        var nextDay=new ParsedStatement.TransactionDraft(r.sourceRowKey(),r.sourceTransactionId(),r.transactionDate().plusDays(1),r.transactionAt().plusDays(1),r.direction(),r.amount(),r.currency(),r.rawNarration(),r.normalizedNarration(),r.transactionStatus(),r.runningBalance(),r.sourceLines());
        assertTrue(check(List.of(first,nextDay),null,true).errors().stream().anyMatch(e->e.code().equals("RUNNING_BALANCE_MISMATCH")));
        var dateOnly=List.of(FinancialAnalysisTest.row("one","A","2026-10-05",Direction.CREDIT,"10.00","100.00","Funds received from - SYN"),FinancialAnalysisTest.row("two","B","2026-10-05",Direction.CREDIT,"10.00","999.00","Funds received from - SYN"));
        assertTrue(check(dateOnly,null,true).errors().stream().anyMatch(e->e.code().equals("RUNNING_BALANCE_MISMATCH")));
    }
    @Test void groupMismatchIsNotResolvedByLookingAheadToConvenientNextBalance() {
        var rows=List.of(row("one",0,"100.00"),row("two",0,"999.00"),row("next",1,"1009.00"));
        var r=check(rows,"0.00",true);
        assertTrue(r.errors().stream().anyMatch(e->e.code().equals("TIMESTAMP_GROUP_BALANCE_MISMATCH")));
        assertFalse(r.reconciled());
    }
    @Test void validGroupsCanSupplyBalanceChainEvidenceWithoutSummaryTotals() {
        // Display order is deliberately opposite to balance order.
        assertTrue(check(List.of(row("second",0,"20.00"),row("first",0,"10.00")),"0.00",false).reconciled());
    }
    @Test void unknownOpeningRequiresUniqueConnectedTrailRatherThanPickingMinimumBalance() {
        var good=check(List.of(row("second",0,"20.00"),row("first",0,"10.00"),row("later",1,"30.00")),null,true);
        assertTrue(good.reconciled());
        var disconnected=check(List.of(row("one",0,"10.00"),row("two",0,"100.00")),null,true);
        assertTrue(disconnected.errors().stream().anyMatch(e->e.code().equals("TIMESTAMP_GROUP_END_UNRESOLVED")));
        var credit=SharedReceiptRulesTest.row("credit","A",Direction.CREDIT,"10.00","10.00","Funds received from - SYN",0);
        var debit=SharedReceiptRulesTest.row("debit","B",Direction.DEBIT,"10.00","0.00","Merchant Payment to SYN",0);
        assertFalse(check(List.of(credit,debit),null,true).reconciled());
        assertTrue(check(List.of(credit,debit),"0.00",true).reconciled());
    }
    @Test void missingGroupBalancesAreNotIndependentEvidenceButCanUseSummaryTotals() {
        var rows=List.of(row("one",0,null),row("two",0,null));
        assertTrue(check(rows,null,true).reconciled());
        assertFalse(check(rows,"0.00",false).reconciled());
    }
    @Test void laterIndependentBalanceAndDeclaredClosingChecksRemainActive() {
        var rows=List.of(row("second",0,"20.00"),row("first",0,"10.00"),row("later",1,"999.00"));
        assertTrue(check(rows,"0.00",true).errors().stream().anyMatch(e->e.code().equals("RUNNING_BALANCE_MISMATCH")&&e.transactionId().equals("later")));
        var p=FinancialAnalysisTest.statement(rows,new ParsedStatement.Summary(BigDecimal.ZERO,new BigDecimal("1.00"),new BigDecimal("31.00"),BigDecimal.ZERO,4),new StatementPeriod(LocalDate.of(2026,10,1),LocalDate.of(2026,10,31)));
        var codes=new StatementReconciler().reconcile(p).errors().stream().map(Diagnostic::code).toList();
        assertTrue(codes.containsAll(List.of("CREDIT_TOTAL_MISMATCH","TRANSACTION_COUNT_MISMATCH","CLOSING_BALANCE_MISMATCH","FINAL_ROW_BALANCE_MISMATCH")));
    }
}
