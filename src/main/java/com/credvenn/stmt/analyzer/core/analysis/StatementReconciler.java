package com.credvenn.stmt.analyzer.core.analysis;

import com.credvenn.stmt.analyzer.core.AnalysisResult.*;
import com.credvenn.stmt.analyzer.core.parsing.ParsedStatement;
import java.math.*;
import java.util.*;

/** Exact-cent checks. Keeps every row; repeated references are review errors, not deletions. */
public final class StatementReconciler {
    public static final String VERSION = "reconciliation-v8";
    private final int maxMismatches;
    private final BigDecimal maxDiscrepancy;
    public StatementReconciler() { this(1, new BigDecimal("100.00")); }
    /** Zero thresholds disable tolerance. Amount is the sum of absolute discrepancies in KES. */
    public StatementReconciler(int maxMismatches, BigDecimal maxDiscrepancy) {
        if (maxMismatches < 0 || maxDiscrepancy == null || maxDiscrepancy.signum() < 0)
            throw new IllegalArgumentException("Reconciliation thresholds must be nonnegative");
        this.maxMismatches = maxMismatches; this.maxDiscrepancy = maxDiscrepancy;
    }
    public ReconciliationResult reconcile(ParsedStatement parsed) {
        var errors = new ArrayList<Diagnostic>();
        var warnings = new ArrayList<Diagnostic>();
        BigDecimal credits = new BigDecimal("0.00"), debits = credits;
        var references = new HashSet<String>();
        var pairedFees = pairedFees(parsed.transactions());
        var allowedOverdraftReferences = allowedOverdraftReferences(parsed.transactions());
        var feeFacts = new HashSet<FeeFact>();
        var keys = new HashSet<String>();
        var summary = parsed.summary();
        java.time.LocalDateTime previousTime = null;
        for (var row : parsed.transactions()) {
            if (!keys.add(row.sourceRowKey())) errors.add(issue("DUPLICATE_SOURCE_ROW", row.sourceRowKey()));
            if (row.sourceTransactionId() != null) {
                if (pairedFees.contains(row.sourceRowKey())) {
                    // Reference reuse is expected for a fee, but an identical repeated fee
                    // still requires review. No rows are deleted; timestamp-based balance
                    // exceptions are applied independently below.
                    var fact = new FeeFact(row.sourceTransactionId(), row.transactionAt() == null ? null : row.transactionAt().toInstant(),
                            row.amount().stripTrailingZeros(), NarrationClassifier.normalize(row.rawNarration()),
                            row.runningBalance() == null ? null : row.runningBalance().stripTrailingZeros());
                    if (!feeFacts.add(fact)) errors.add(issue("DUPLICATE_REFERENCE", row.sourceRowKey()));
                } else if (!references.add(row.sourceTransactionId()) && !allowedOverdraftReferences.contains(row.sourceTransactionId())) {
                    errors.add(issue("DUPLICATE_REFERENCE", row.sourceRowKey()));
                }
            }
            if (row.direction() == Direction.CREDIT) credits = credits.add(row.amount());
            else debits = debits.add(row.amount());
            var time = row.transactionAt() == null ? row.transactionDate().atStartOfDay()
                    : row.transactionAt().atZoneSameInstant(java.time.ZoneId.of("Africa/Nairobi")).toLocalDateTime();
            if (previousTime != null && time.isBefore(previousTime)) errors.add(issue("TRANSACTION_ORDER_UNRESOLVED", row.sourceRowKey()));
            previousTime = time;
        }
        var balances = new TimestampBalanceReconciler().check(parsed.transactions(),summary.openingBalance(),errors,warnings);
        BigDecimal previous = balances.closing();
        boolean completeChain = balances.completeChain();
        compare(summary.totalCredits(), credits, "CREDIT_TOTAL_MISMATCH", errors);
        compare(summary.totalDebits(), debits, "DEBIT_TOTAL_MISMATCH", errors);
        if (summary.transactionCount() != null && summary.transactionCount() != parsed.transactions().size()) {
            errors.add(issue("TRANSACTION_COUNT_MISMATCH", null));
        }
        if (summary.openingBalance() != null && summary.closingBalance() != null) {
            compare(summary.closingBalance(), summary.openingBalance().add(credits).subtract(debits),
                    "CLOSING_BALANCE_MISMATCH", errors);
        }
        if (previous != null) compare(summary.closingBalance(), previous, "FINAL_ROW_BALANCE_MISMATCH", errors);
        if (!parsed.rejectedRows().isEmpty() || !parsed.warnings().isEmpty()) errors.add(issue("PARSING_REQUIRES_REVIEW", null));
        boolean totalsAvailable = summary.totalCredits() != null && summary.totalDebits() != null;
        if (!totalsAvailable && !completeChain) warnings.add(issue("INSUFFICIENT_RECONCILIATION_EVIDENCE", null));
        var classifier = new NarrationClassifier();
        boolean classificationsResolved = parsed.transactions().stream().allMatch(r ->
                classifier.classify(r.direction(), r.rawNarration(), r.transactionStatus()).balanceEligibility()
                        != BalanceEligibility.UNDETERMINED);
        boolean tolerable = !errors.isEmpty() && errors.size() <= maxMismatches
                && errors.stream().allMatch(e -> e.code().equals("RUNNING_BALANCE_MISMATCH"))
                && maxDiscrepancy.signum() > 0 && balances.absoluteDiscrepancy().compareTo(maxDiscrepancy) <= 0
                && "KES".equals(parsed.currency()) && totalsAvailable && classificationsResolved;
        if (tolerable) {
            warnings.addAll(errors); errors.clear();
            warnings.add(new Diagnostic("RECONCILIATION_TOLERANCE_APPLIED",
                    "Accepted with warnings under " + VERSION + ": absolute discrepancy KES "
                    + balances.absoluteDiscrepancy() + "; configured limits: count=" + maxMismatches
                    + ", amount KES " + maxDiscrepancy + ". No amounts adjusted.", null, null));
        }
        int accepted = parsed.transactions().size(), rejected = parsed.rejectedRows().size();
        // A descriptive accepted-row ratio, not a learned probability of correctness.
        BigDecimal confidence = accepted + rejected == 0 ? null
                : BigDecimal.valueOf(accepted).divide(BigDecimal.valueOf((long) accepted + rejected), 4, RoundingMode.HALF_UP);
        return new ReconciliationResult(errors.isEmpty() && (totalsAvailable || completeChain),
                credits, debits, confidence, errors, warnings);
    }
    private record FeeFact(String reference, java.time.Instant time, BigDecimal amount, String narration, BigDecimal balance) { }
    private static boolean completed(ParsedStatement.TransactionDraft row) {
        return "Completed".equalsIgnoreCase(row.transactionStatus());
    }
    private static boolean fee(ParsedStatement.TransactionDraft row) {
        if (row.direction() != Direction.DEBIT || !completed(row)) return false;
        String text = NarrationClassifier.normalize(row.rawNarration());
        return text.startsWith("CUSTOMER TRANSFER OF FUNDS CHARGE") || text.startsWith("PAY BILL CHARGE")
                || text.startsWith("PAY MERCHANT CHARGE") || text.startsWith("WITHDRAWAL CHARGE");
    }
    private static Set<String> pairedFees(List<ParsedStatement.TransactionDraft> rows) {
        var primaryReferences = new HashSet<String>();
        for (var row : rows) if (!fee(row) && completed(row) && row.sourceTransactionId() != null) {
            primaryReferences.add(row.sourceTransactionId());
        }
        var result = new HashSet<String>();
        for (var row : rows) if (fee(row) && row.sourceTransactionId() != null
                && primaryReferences.contains(row.sourceTransactionId())) {
            result.add(row.sourceRowKey());
        }
        return result;
    }
    /** Exactly one completed overdraft credit and one qualifying completed debit,
     * plus recognized associated fees, may share a receipt in either display order. */
    private static Set<String> allowedOverdraftReferences(List<ParsedStatement.TransactionDraft> rows) {
        var groups = new HashMap<String,List<ParsedStatement.TransactionDraft>>();
        for (var row : rows) if (row.sourceTransactionId()!=null && !fee(row)) {
            groups.computeIfAbsent(row.sourceTransactionId(),k->new ArrayList<>()).add(row);
        }
        var result = new HashSet<String>();
        for (var entry : groups.entrySet()) {
            var group = entry.getValue();
            if (group.size()!=2 || group.stream().anyMatch(r->!completed(r))) continue;
            long advances = group.stream().filter(r->r.direction()==Direction.CREDIT
                    && NarrationClassifier.normalize(r.rawNarration()).startsWith("OVERDRAFT OF CREDIT PARTY")).count();
            long payments = group.stream().filter(StatementReconciler::fulizaPayment).count();
            if (advances==1 && payments==1) result.add(entry.getKey());
        }
        return result;
    }
    private static boolean fulizaPayment(ParsedStatement.TransactionDraft row) {
        if (row.direction() != Direction.DEBIT) return false;
        String text = NarrationClassifier.normalize(row.rawNarration());
        return text.equals("AIRTIME PURCHASE WITH FULIZA")
                || text.startsWith("MERCHANT PAYMENT FULIZA M-PESA")
                || text.startsWith("CUSTOMER SEND MONEY TO MICRO SME BUSINESS WITH FULIZA")
                || text.matches("PAY BILL(?: ONLINE)? FULIZA M-PESA(?: .*|$)")
                || text.startsWith("CUSTOMER TRANSFER FULIZA")
                || text.startsWith("CUSTOMER WITHDRAWAL AT AGENT TILL WITH FULIZA");
    }
    private static void compare(BigDecimal declared, BigDecimal observed, String code, List<Diagnostic> errors) {
        if (declared != null && declared.compareTo(observed) != 0) errors.add(issue(code, null));
    }
    static Diagnostic issue(String code, String transactionId) {
        return new Diagnostic(code, code, transactionId, null);
    }
}
