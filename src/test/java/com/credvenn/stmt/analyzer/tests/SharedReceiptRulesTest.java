package com.credvenn.stmt.analyzer.tests;

import com.credvenn.stmt.analyzer.core.AnalysisResult.*;
import com.credvenn.stmt.analyzer.core.analysis.*;
import com.credvenn.stmt.analyzer.core.parsing.ParsedStatement;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.junit.jupiter.api.Assertions.*;

class SharedReceiptRulesTest {
    static ParsedStatement.TransactionDraft row(String key,String ref,Direction dir,String amount,String balance,String text,int second) {
        var at=OffsetDateTime.parse("2026-10-05T10:00:00+03:00").plusSeconds(second);
        return new ParsedStatement.TransactionDraft(key,ref,at.toLocalDate(),at,dir,new BigDecimal(amount),"KES",text,text.toUpperCase(Locale.ROOT),"Completed",balance==null?null:new BigDecimal(balance),List.of(new ParsedStatement.SourceLine(1,1,"synthetic",20,20,100,8)));
    }
    static ReconciliationResult reconcile(List<ParsedStatement.TransactionDraft> rows) {
        BigDecimal credits=new BigDecimal("0.00"),debits=credits;
        for(var r:rows)if(r.direction()==Direction.CREDIT)credits=credits.add(r.amount());else debits=debits.add(r.amount());
        return new StatementReconciler().reconcile(FinancialAnalysisTest.statement(rows,
                new ParsedStatement.Summary(rows.isEmpty() || rows.getFirst().runningBalance()==null ? null : rows.getFirst().runningBalance().subtract(rows.getFirst().direction()==Direction.CREDIT?rows.getFirst().amount():rows.getFirst().amount().negate()),null,credits,debits,rows.size()),
                new StatementPeriod(LocalDate.of(2026,10,1),LocalDate.of(2026,10,31))));
    }
    static boolean duplicate(ReconciliationResult r) {return r.errors().stream().anyMatch(e->e.code().equals("DUPLICATE_REFERENCE"));}
    @ParameterizedTest @ValueSource(booleans={false,true})
    void sameReceiptAndTimeFeeIsAcceptedBeforeOrAfterPayment(boolean feeFirst) {
        var payment=row("payment","REF",Direction.DEBIT,"100.00",feeFirst?"95.00":"100.00","Merchant Payment to SYN",0);
        var fee=row("fee","REF",Direction.DEBIT,"5.00",feeFirst?"195.00":"95.00","Customer Transfer of Funds Charge",0);
        var rows=feeFirst?List.of(fee,payment):List.of(payment,fee);
        var r=reconcile(rows);
        assertTrue(r.reconciled());assertFalse(duplicate(r));assertEquals(new BigDecimal("105.00"),r.totalDebits());
        assertEquals(2,rows.size());
    }
    @ParameterizedTest @ValueSource(strings={"Pay Bill Charge","Pay Merchant Charge","Withdrawal Charge"})
    void otherRecognizedFeeTypesAreAccepted(String details) {
        var r=reconcile(List.of(row("payment","REF",Direction.CREDIT,"100.00","100.00","Funds received from - SYN",0),row("fee","REF",Direction.DEBIT,"5.00","95.00",details,0)));
        assertTrue(r.reconciled());assertFalse(duplicate(r));
    }
    @ParameterizedTest @ValueSource(strings={
        "Airtime Purchase with Fuliza", "Merchant Payment Fuliza M-Pesa to SYN", "Merchant Payment Fuliza M-Pesa Online to SYN",
        "Customer Send Money to Micro SME Business with Fuliza MPesa to - SYN",
        "Pay Bill Fuliza M-Pesa to SYN", "Pay Bill Online Fuliza M-Pesa to SYN",
        "Customer Transfer Fuliza MPesa to - SYN", "Customer Withdrawal at Agent Till with Fuliza to SYN"})
    void overdraftFollowedByFulizaDebitIsAnAllowedSharedReceipt(String details) {
        var r=reconcile(List.of(row("advance","REF",Direction.CREDIT,"20.00","100.00","OverDraft of Credit Party",0),row("payment","REF",Direction.DEBIT,"20.00","80.00",details,0)));
        assertTrue(r.reconciled());assertFalse(duplicate(r));
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void airtimePairPreservesClassificationAndSurplusInEitherOrder(boolean debitFirst) {
        var advance=row("advance","REF",Direction.CREDIT,"150.00","150.00","OverDraft of Credit Party",0);
        var debit=row("payment","REF",Direction.DEBIT,"150.00","0.00","Airtime Purchase with Fuliza",0);
        var rows=debitFirst?List.of(debit,advance):List.of(advance,debit);
        var parsed=FinancialAnalysisTest.statement(rows,new ParsedStatement.Summary(BigDecimal.ZERO,BigDecimal.ZERO,
                new BigDecimal("150.00"),new BigDecimal("150.00"),2),
                new StatementPeriod(LocalDate.of(2026,10,1),LocalDate.of(2026,10,31)));
        var result=new StatementAnalysisPipeline().analyzeParsed(parsed);
        assertEquals(Status.COMPLETE,result.status());assertTrue(result.reconciliationErrors().isEmpty());
        assertEquals(2,result.transactions().size());
        var month=result.monthlyBreakdown().getFirst();
        FinancialAnalysisTest.amount("0.00",month.eligibleInflows());
        FinancialAnalysisTest.amount("150.00",month.eligibleSpending());
        FinancialAnalysisTest.amount("-150.00",month.monthlyBalance());
        assertEquals("AIRTIME_PURCHASE",result.transactions().stream().filter(t->t.direction()==Direction.DEBIT)
                .findFirst().orElseThrow().classification().transactionType());
    }
    @ParameterizedTest @ValueSource(strings={"ordinary","pending","extra-debit","extra-credit","unrelated-credit"})
    void airtimeExceptionDoesNotHideOtherReferenceProblems(String scenario) {
        var advance=row("advance","REF",Direction.CREDIT,"150.00",null,
                scenario.equals("unrelated-credit")?"Funds received from - SYN":"OverDraft of Credit Party",0);
        var debit=row("payment","REF",Direction.DEBIT,"150.00",null,
                scenario.equals("ordinary")?"Airtime Purchase":"Airtime Purchase with Fuliza",0);
        if(scenario.equals("pending"))debit=SameTimeBalanceTest.status(debit,"Pending");
        var rows=new ArrayList<>(List.of(advance,debit));
        if(scenario.equals("extra-debit"))rows.add(row("extra","REF",Direction.DEBIT,"150.00",null,"Airtime Purchase with Fuliza",0));
        if(scenario.equals("extra-credit"))rows.add(row("extra","REF",Direction.CREDIT,"150.00",null,"OverDraft of Credit Party",0));
        assertTrue(duplicate(reconcile(rows)));
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void referenceExceptionsAllowDifferentOrMissingCompletionTimes(boolean missingTime) {
        var advance=missingTime ? FinancialAnalysisTest.row("one","REF","2026-10-05",Direction.CREDIT,"20.00",null,"OverDraft of Credit Party")
                : row("one","REF",Direction.CREDIT,"20.00",null,"OverDraft of Credit Party",0);
        var payment=missingTime ? FinancialAnalysisTest.row("two","REF","2026-10-05",Direction.DEBIT,"20.00",null,"Merchant Payment Fuliza M-Pesa to SYN")
                : row("two","REF",Direction.DEBIT,"20.00",null,"Merchant Payment Fuliza M-Pesa to SYN",5);
        var fee=missingTime ? FinancialAnalysisTest.row("fee","REF","2026-10-05",Direction.DEBIT,"5.00",null,"Pay Merchant Charge")
                : row("fee","REF",Direction.DEBIT,"5.00",null,"Pay Merchant Charge",10);
        assertFalse(duplicate(reconcile(List.of(advance,payment,fee))));
        assertEquals(new BigDecimal("25.00"),reconcile(List.of(advance,payment,fee)).totalDebits());
    }
    @Test void associatedFeeDoesNotBreakTheOverdraftPair() {
        var r=reconcile(List.of(row("advance","REF",Direction.CREDIT,"20.00","100.00","OverDraft of Credit Party",0),row("fee","REF",Direction.DEBIT,"5.00","95.00","Pay Merchant Charge",0),row("payment","REF",Direction.DEBIT,"20.00","75.00","Merchant Payment Fuliza M-Pesa to SYN",0)));
        assertTrue(r.reconciled());assertFalse(duplicate(r));assertEquals(new BigDecimal("25.00"),r.totalDebits());
    }
    @ParameterizedTest @ValueSource(strings={"fee-only","non-fuliza","not-overdraft","identical-fee","ordinary-payments"})
    void unrelatedSharedReferencesStillFail(String scenario) {
        var advance=row("one","REF",Direction.CREDIT,"20.00",null,"OverDraft of Credit Party",0);
        var payment=row("two","REF",Direction.DEBIT,"20.00",null,"Merchant Payment Fuliza M-Pesa to SYN",0);
        var fee=row("fee","REF",Direction.DEBIT,"5.00",null,"Pay Merchant Charge",0);
        List<ParsedStatement.TransactionDraft> rows=switch(scenario) {
            case "fee-only" -> List.of(fee,row("fee2","REF",Direction.DEBIT,"5.00",null,"Pay Merchant Charge",0));
            case "non-fuliza" -> List.of(advance,row("two","REF",Direction.DEBIT,"20.00",null,"Merchant Payment to SYN",0));
            case "not-overdraft" -> List.of(row("one","REF",Direction.CREDIT,"20.00",null,"Funds received from - SYN",0),payment);
            case "identical-fee" -> List.of(advance,fee,row("fee2","REF",Direction.DEBIT,"5.00",null,"Pay Merchant Charge",0));
            case "ordinary-payments" -> List.of(payment,row("three","REF",Direction.DEBIT,"20.00",null,"Merchant Payment Fuliza M-Pesa to SYN",0));
            default -> throw new IllegalArgumentException("Unknown scenario");
        };
        assertTrue(duplicate(reconcile(rows)),scenario);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void receiptPairAcceptsDebitBeforeCreditAndInterveningUnrelatedReferences(boolean reversed) {
        var advance=row("advance","REF",Direction.CREDIT,"20.00",null,"OverDraft of Credit Party",0);
        var payment=row("payment","REF",Direction.DEBIT,"20.00",null,"Merchant Payment Fuliza M-Pesa to SYN",1);
        var other=row("other","OTHER",Direction.CREDIT,"10.00",null,"Funds received from - SYN",0);
        var rows=reversed?List.of(payment,other,advance):List.of(advance,other,payment);
        assertFalse(duplicate(reconcile(rows)));
    }
    @Test void partialOverdraftFundingDoesNotRequireEqualAmounts() {
        var debit=row("payment","REF",Direction.DEBIT,"170.00","0.00","Merchant Payment Fuliza M-Pesa to SYN",0);
        var advance=row("advance","REF",Direction.CREDIT,"31.19","170.00","OverDraft of Credit Party",0);
        var parsed=FinancialAnalysisTest.statement(List.of(debit,advance),new ParsedStatement.Summary(new BigDecimal("138.81"),BigDecimal.ZERO,new BigDecimal("31.19"),new BigDecimal("170.00"),2),new StatementPeriod(java.time.LocalDate.of(2026,10,1),java.time.LocalDate.of(2026,10,31)));
        assertTrue(new StatementReconciler().reconcile(parsed).reconciled());
    }
    @ParameterizedTest @ValueSource(strings={"extra-payment","extra-advance","extra-unrelated","pending-payment"})
    void additionalOrIncompleteComponentsDoNotReceiveThePairExemption(String scenario) {
        var advance=row("advance","REF",Direction.CREDIT,"20.00",null,"OverDraft of Credit Party",0);
        var payment=row("payment","REF",Direction.DEBIT,"20.00",null,"Merchant Payment Fuliza M-Pesa to SYN",0);
        var extra=switch(scenario) {
            case "extra-payment" -> row("extra","REF",Direction.DEBIT,"5.00",null,"Merchant Payment Fuliza M-Pesa to SYN",0);
            case "extra-advance" -> row("extra","REF",Direction.CREDIT,"5.00",null,"OverDraft of Credit Party",0);
            default -> row("extra","REF",Direction.CREDIT,"5.00",null,"Funds received from - SYN",0);
        };
        var rows=scenario.equals("pending-payment")?List.of(advance,SameTimeBalanceTest.status(payment,"Pending")):List.of(payment,advance,extra);
        assertTrue(duplicate(reconcile(rows)));
    }
    @Test void allowedReferenceReuseDoesNotDisableLaterBalanceOrSourceKeyChecks() {
        var one=row("one","REF",Direction.CREDIT,"20.00","100.00","OverDraft of Credit Party",0);
        var two=row("two","REF",Direction.DEBIT,"20.00","80.00","Merchant Payment Fuliza M-Pesa to SYN",0);
        var later=row("later","LATER",Direction.CREDIT,"5.00","4.00","Funds received from - SYN",1);
        var r=reconcile(List.of(one,two,later));
        assertFalse(duplicate(r));assertTrue(java.util.stream.Stream.concat(r.errors().stream(),r.warnings().stream()).anyMatch(e->e.code().equals("RUNNING_BALANCE_MISMATCH") && e.transactionId().equals("later")));
        var reused=row("one","REF",Direction.DEBIT,"20.00","80.00","Merchant Payment Fuliza M-Pesa to SYN",0);
        assertTrue(reconcile(List.of(one,reused)).errors().stream().anyMatch(e->e.code().equals("DUPLICATE_SOURCE_ROW")));
    }
    @ParameterizedTest @CsvSource({"DEBIT,Completed,true","DEBIT,completed,true","DEBIT,Pending,false","DEBIT,Failed,false","CREDIT,Completed,false"})
    void emptyDetailsAssumptionRequiresCompletedDebit(Direction direction,String status,boolean repayment) {
        var c=new NarrationClassifier().classify(direction," \n\t ",status);
        assertEquals(repayment?"OVERDRAFT_REPAYMENT":"UNKNOWN",c.transactionType());
        if(repayment){assertEquals("FULIZA",c.fundingSource());assertTrue(c.explanation().contains("Policy assumption"));assertTrue(c.ruleId().endsWith(":COMPLETED_DEBIT_EMPTY_DETAILS"));}
        assertEquals("UNKNOWN",new NarrationClassifier().classify(direction,"").transactionType());
    }
}
