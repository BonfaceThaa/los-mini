package com.credvenn.stmt.analyzer.core.parsing;

import com.credvenn.stmt.analyzer.core.AnalysisResult.Diagnostic;
import com.credvenn.stmt.analyzer.core.AnalysisResult.Direction;
import com.credvenn.stmt.analyzer.core.AnalysisResult.StatementPeriod;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** Unclassified, unreconciled drafts, never a financial assessment or persistence entity. */
public record ParsedStatement(String correlationId, String parserVersion, Status status,
        StatementPeriod period, String currency, List<TransactionDraft> transactions,
        List<RejectedRow> rejectedRows, List<Diagnostic> warnings, Summary summary) {
    public ParsedStatement(String correlationId, String parserVersion, Status status,
            StatementPeriod period, String currency, List<TransactionDraft> transactions,
            List<RejectedRow> rejectedRows, List<Diagnostic> warnings) {
        this(correlationId, parserVersion, status, period, currency, transactions, rejectedRows, warnings,
                new Summary(null, null, null, null, null));
    }
    public ParsedStatement {
        transactions = List.copyOf(transactions);
        rejectedRows = List.copyOf(rejectedRows);
        warnings = List.copyOf(warnings);
    }
    public enum Status { PARSED, REQUIRES_REVIEW }
    /** Optional statement-declared values; never inferred from parsed row totals. */
    public record Summary(BigDecimal openingBalance, BigDecimal closingBalance,
            BigDecimal totalCredits, BigDecimal totalDebits, Integer transactionCount) { }
    @Override public String toString() {
        return "ParsedStatement[status=" + status + ", transactionCount=" + transactions.size() + "]";
    }
    public record SourceLine(int page, int row, String rawText, float x, float y, float width, float height) {
        @Override public String toString() { return "SourceLine[page=" + page + ", row=" + row + "]"; }
    }
    public record TransactionDraft(String sourceRowKey, String sourceTransactionId,
            LocalDate transactionDate, OffsetDateTime transactionAt, Direction direction,
            BigDecimal amount, String currency, String rawNarration, String normalizedNarration,
            String transactionStatus, BigDecimal runningBalance, List<SourceLine> sourceLines) {
        public TransactionDraft { sourceLines = List.copyOf(sourceLines); }
        @Override public String toString() { return "TransactionDraft[sourceRowKey=" + sourceRowKey + "]"; }
    }
    public record RejectedRow(String reasonCode, List<SourceLine> sourceLines) {
        public RejectedRow { sourceLines = List.copyOf(sourceLines); }
        @Override public String toString() { return "RejectedRow[reasonCode=" + reasonCode + "]"; }
    }
}
