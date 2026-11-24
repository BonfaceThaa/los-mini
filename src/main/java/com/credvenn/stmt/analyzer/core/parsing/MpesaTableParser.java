package com.credvenn.stmt.analyzer.core.parsing;

import com.credvenn.stmt.analyzer.core.AnalysisProcessingException;
import com.credvenn.stmt.analyzer.core.AnalysisResult.Diagnostic;
import com.credvenn.stmt.analyzer.core.AnalysisResult.Direction;
import com.credvenn.stmt.analyzer.core.AnalysisResult.StatementPeriod;
import com.credvenn.stmt.analyzer.core.ProcessingErrorCode;
import com.credvenn.stmt.analyzer.core.extraction.ExtractedDocument;
import com.credvenn.stmt.analyzer.core.extraction.ExtractedDocument.Glyph;
import com.credvenn.stmt.analyzer.core.parsing.ParsedStatement.*;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.regex.Pattern;

/** Seven-column tables, including the Safaricom detailed statement layout. */
public final class MpesaTableParser implements StatementParser {
    public static final String VERSION = "mpesa-table-v4";
    private static final List<String> HEADERS = List.of("Receipt No.", "Completion Time", "Details",
            "Transaction Status", "Paid In", "Withdrawn", "Balance");
    private static final Pattern PERIOD = Pattern.compile(
            "Statement Period: (\\d{4}-\\d{2}-\\d{2}) to (\\d{4}-\\d{2}-\\d{2})");
    private static final Pattern MONEY = Pattern.compile("-?(?:\\d+|\\d{1,3}(?:,\\d{3})+)\\.\\d{2}");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("uuuu-MM-dd")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final ZoneId ZONE = ZoneId.of("Africa/Nairobi");
    private final float lineTolerance;

    public MpesaTableParser() { this(2f); }
    public MpesaTableParser(float lineTolerance) {
        if (!Float.isFinite(lineTolerance) || lineTolerance <= 0 || lineTolerance > 5) {
            throw new IllegalArgumentException("Line tolerance must be in (0, 5] points");
        }
        this.lineTolerance = lineTolerance;
    }

    @Override
    public ParsedStatement parse(ExtractedDocument document) {
        if (document == null || document.correlationId() == null || document.correlationId().isBlank()) {
            throw new AnalysisProcessingException(ProcessingErrorCode.INVALID_INPUT,
                    document == null ? null : document.correlationId());
        }
        var warnings = new ArrayList<>(document.warnings());
        var transactions = new ArrayList<TransactionDraft>();
        var rejected = new ArrayList<RejectedRow>();
        var pageLines = new ArrayList<List<Line>>();
        var declared = new HashMap<String, String>();
        var summaryNames = List.of("Opening Balance", "Closing Balance", "Total Credits", "Total Debits", "Transaction Count");
        // This domestic Safaricom template declares its period above a summary and
        // uses centered headings. Its denomination is KES by template contract.
        boolean detailed = document.pages().stream().anyMatch(p -> p.text().replaceAll("\\s+", "")
                .contains("DETAILEDSTATEMENT")) && document.pages().stream()
                .allMatch(p -> Math.abs(p.width() - 595) < 2 && Math.abs(p.height() - 848) < 2);
        StatementPeriod period = null;
        boolean periodConflict = false;
        String currency = detailed ? "KES" : null;
        boolean currencyConflict = false;
        for (var page : document.pages()) {
            if (page.rotation() != 0 || page.glyphs().stream().anyMatch(g -> g.direction() != 0)) {
                throw unsupported(document);
            }
            var lines = lines(page);
            pageLines.add(lines);
            for (var line : lines) {
                for (String name : summaryNames) {
                    if (line.text().startsWith(name + ":")) {
                        String value = line.text().substring(name.length() + 1).strip();
                        if (declared.containsKey(name) && !declared.get(name).equals(value)) {
                            warn(warnings, "CONFLICTING_SUMMARY", line.source());
                        }
                        declared.put(name, value);
                    }
                }
                if (detailed && line.text().matches("(?i)TOTAL:? .*")) {
                    var totals = Pattern.compile("(?:\\d{1,3}(?:,\\d{3})+|\\d+)\\.\\d{2}").matcher(line.text());
                    var values = new ArrayList<String>();
                    while (totals.find()) values.add(totals.group());
                    if (values.size() == 2) {
                        declared.put("Total Credits", values.get(0));
                        declared.put("Total Debits", values.get(1));
                    }
                }
                var matcher = PERIOD.matcher(line.text());
                if (line.text().startsWith("Statement Period:")) {
                    try {
                        StatementPeriod candidate;
                        if (matcher.matches()) {
                            candidate = new StatementPeriod(LocalDate.parse(matcher.group(1), DATE),
                                    LocalDate.parse(matcher.group(2), DATE));
                        } else if (detailed) {
                            var dates = line.text().substring("Statement Period:".length()).strip().split(" - ");
                            if (dates.length != 2) throw new IllegalArgumentException();
                            var format = DateTimeFormatter.ofPattern("dd MMM uuuu", Locale.ENGLISH)
                                    .withResolverStyle(ResolverStyle.STRICT);
                            candidate = new StatementPeriod(LocalDate.parse(dates[0], format), LocalDate.parse(dates[1], format));
                        } else throw new IllegalArgumentException();
                        if (period != null && !period.equals(candidate)) periodConflict = true;
                        period = candidate;
                    } catch (IllegalArgumentException | DateTimeException e) { periodConflict = true; }
                }
                if (line.text().startsWith("Currency:")) {
                    String candidate = line.text().substring("Currency:".length()).strip();
                    if (!candidate.equals("KES") || currency != null && !currency.equals(candidate)) currencyConflict = true;
                    currency = candidate;
                }
            }
        }
        if (periodConflict || period == null) {
            period = null;
            warn(warnings, "STATEMENT_PERIOD_UNRESOLVED", null);
        }
        if (currencyConflict || !"KES".equals(currency)) {
            currency = null;
            warn(warnings, "CURRENCY_UNRESOLVED", null);
        }
        Builder pending = null;
        boolean anyHeader = false;
        for (var lines : pageLines) {
            float[] anchors = null;
            for (var line : lines) {
                float[] header = header(line.glyphs());
                if (header != null) {
                    // Column borders, rather than the centered label starts, delimit cells.
                    if (detailed) {
                        float[] expected = {50.7f, 110.8f, 215.6f, 282.2f, 372.9f, 435.5f, 510.2f};
                        for (int i = 0; i < header.length; i++) {
                            if (Math.abs(header[i] - expected[i]) > 3) throw unsupported(document);
                        }
                        anchors = new float[]{38, 107.7f, 177.5f, 282.1f, 352, 421.8f, 491.6f};
                    } else anchors = header;
                    anyHeader = true; continue;
                }
                if (line.text().isBlank() || metadata(line.text())) continue;
                if (detailed && (anchors == null || line.source().y() >= 725)) continue;
                if (anchors == null) {
                    if (!line.text().isBlank()) {
                        warn(warnings, "UNRECOGNIZED_LINE", line.source());
                        rejected.add(new RejectedRow("UNRECOGNIZED_LINE", List.of(line.source())));
                    }
                    continue;
                }
                String[] cells = cells(line.glyphs(), anchors);
                boolean continuation = !cells[2].isBlank();
                for (int i = 0; i < cells.length; i++) if (i != 2 && !cells[i].isBlank()) continuation = false;
                if (continuation && pending != null) {
                    pending.narration.add(cells[2]); pending.sources.add(line.source());
                } else {
                    if (pending != null) finish(pending, period, currency, transactions, rejected, warnings, detailed);
                    pending = new Builder(cells, line.source());
                }
            }
        }
        if (!anyHeader) throw unsupported(document);
        if (pending != null) finish(pending, period, currency, transactions, rejected, warnings, detailed);
        if (detailed && transactions.size() > 1) {
            boolean descending = true, strictlyDescending = false;
            for (int i = 1; i < transactions.size(); i++) {
                var prior = transactions.get(i - 1).transactionAt();
                var current = transactions.get(i).transactionAt();
                if (prior == null || current == null || current.isAfter(prior)) descending = false;
                if (prior != null && current != null && current.isBefore(prior)) strictlyDescending = true;
            }
            // Reverse the entire sequence, including timestamp ties, preserving each row's provenance.
            // Reconciliation checks the resulting running balances; mixed order is never sorted away.
            if (descending && strictlyDescending) Collections.reverse(transactions);
        }
        Summary summary;
        try {
            BigDecimal opening = declared.containsKey("Opening Balance") ? money(declared.get("Opening Balance"), false) : null;
            BigDecimal closing = declared.containsKey("Closing Balance") ? money(declared.get("Closing Balance"), false) : null;
            BigDecimal credits = declared.containsKey("Total Credits") ? money(declared.get("Total Credits"), false) : null;
            BigDecimal debits = declared.containsKey("Total Debits") ? money(declared.get("Total Debits"), false) : null;
            Integer count = declared.containsKey("Transaction Count") ? Integer.valueOf(declared.get("Transaction Count")) : null;
            if (credits != null && credits.signum() < 0 || debits != null && debits.signum() < 0
                    || count != null && count < 0) throw new IllegalArgumentException();
            summary = new Summary(opening, closing, credits, debits, count);
        } catch (IllegalArgumentException | RowError e) {
            warn(warnings, "INVALID_SUMMARY", null);
            summary = new Summary(null, null, null, null, null);
        }
        if (transactions.isEmpty() && !(summary.transactionCount() != null && summary.transactionCount() == 0
                && summary.totalCredits() != null && summary.totalCredits().signum() == 0
                && summary.totalDebits() != null && summary.totalDebits().signum() == 0)) {
            warn(warnings, "NO_PARSED_TRANSACTIONS", null);
        }
        return new ParsedStatement(document.correlationId(), VERSION,
                warnings.isEmpty() && rejected.isEmpty() ? Status.PARSED : Status.REQUIRES_REVIEW,
                period, currency, transactions, rejected, warnings, summary);
    }

    private void finish(Builder row, StatementPeriod period, String currency,
            List<TransactionDraft> transactions, List<RejectedRow> rejected, List<Diagnostic> warnings, boolean signedWithdrawals) {
        try {
            if (currency == null) throw new RowError("CURRENCY_UNRESOLVED");
            if (!row.cells[3].equalsIgnoreCase("Completed")) throw new RowError("UNSUPPORTED_TRANSACTION_STATUS");
            var timestamp = timestamp(row.cells[1], period);
            if (period != null && (timestamp.date().isBefore(period.start())
                    || timestamp.date().isAfter(period.end()))) throw new RowError("DATE_OUTSIDE_PERIOD");
            BigDecimal credit = money(row.cells[4], true), debit = money(row.cells[5], true);
            if (signedWithdrawals) debit = debit.abs();
            if (credit.signum() < 0 || debit.signum() < 0 || (credit.signum() > 0) == (debit.signum() > 0)) {
                throw new RowError("AMBIGUOUS_DIRECTION");
            }
            BigDecimal balance = row.cells[6].isBlank() || row.cells[6].equals("-") ? null : money(row.cells[6], false);
            String raw = String.join("\n", row.narration);
            if (raw.isBlank() && debit.signum() == 0) {
                if (!signedWithdrawals) throw new RowError("MISSING_NARRATION");
                warn(warnings, "MISSING_NARRATION", row.sources.getFirst());
            }
            // Completed debits with empty Details are retained without a blocking parser
            // warning. The versioned classifier records the Fuliza repayment assumption.
            String reference = row.cells[0].isBlank() || row.cells[0].equals("-") ? null : row.cells[0];
            var first = row.sources.getFirst();
            transactions.add(new TransactionDraft("p" + first.page() + ":r" + first.row(), reference,
                    timestamp.date(), timestamp.time() == null ? null : timestamp.time().atZone(ZONE).toOffsetDateTime(),
                    credit.signum() > 0 ? Direction.CREDIT : Direction.DEBIT,
                    credit.signum() > 0 ? credit : debit, currency, raw,
                    Normalizer.normalize(raw, Normalizer.Form.NFKC).replaceAll("\\s+", " ").strip().toUpperCase(Locale.ROOT),
                    row.cells[3], balance, row.sources));
        } catch (RowError e) {
            rejected.add(new RejectedRow(e.getMessage(), row.sources));
            warn(warnings, e.getMessage(), row.sources.getFirst());
        }
    }

    private static DateValue timestamp(String value, StatementPeriod period) {
        try {
            if (value.matches("\\d{4}-\\d{2}-\\d{2}( \\d{2}:\\d{2}:\\d{2})?")) {
                if (value.length() == 10) return new DateValue(LocalDate.parse(value, DATE), null);
                var time = LocalDateTime.parse(value, DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")
                        .withResolverStyle(ResolverStyle.STRICT));
                return new DateValue(time.toLocalDate(), time);
            }
            if (value.matches("\\d{2}/\\d{2} \\d{2}:\\d{2}:\\d{2}")) {
                if (period == null || period.end().getYear() - period.start().getYear() > 2) {
                    throw new RowError("AMBIGUOUS_YEAR");
                }
                var candidates = new ArrayList<LocalDateTime>();
                for (int year = period.start().getYear(); year <= period.end().getYear(); year++) {
                    try {
                        var candidate = LocalDateTime.parse(year + "/" + value,
                                DateTimeFormatter.ofPattern("uuuu/dd/MM HH:mm:ss").withResolverStyle(ResolverStyle.STRICT));
                        if (!candidate.toLocalDate().isBefore(period.start()) && !candidate.toLocalDate().isAfter(period.end())) {
                            candidates.add(candidate);
                        }
                    } catch (DateTimeException ignored) { }
                }
                if (candidates.size() != 1) throw new RowError("AMBIGUOUS_YEAR");
                return new DateValue(candidates.getFirst().toLocalDate(), candidates.getFirst());
            }
        } catch (DateTimeException e) { throw new RowError("INVALID_DATE"); }
        throw new RowError("INVALID_DATE");
    }

    private static BigDecimal money(String value, boolean blankIsZero) {
        if (blankIsZero && (value.isBlank() || value.equals("-"))) return new BigDecimal("0.00");
        if (!MONEY.matcher(value).matches()) throw new RowError("INVALID_AMOUNT");
        return new BigDecimal(value.replace(",", ""));
    }

    private List<Line> lines(ExtractedDocument.Page page) {
        var sorted = page.glyphs().stream().sorted(Comparator.comparingDouble(Glyph::y).thenComparingDouble(Glyph::x)).toList();
        var groups = new ArrayList<List<Glyph>>();
        for (var glyph : sorted) {
            if (groups.isEmpty() || Math.abs(groups.getLast().getFirst().y() - glyph.y()) > lineTolerance) {
                groups.add(new ArrayList<>());
            }
            groups.getLast().add(glyph);
        }
        var result = new ArrayList<Line>();
        for (var group : groups) {
            group.sort(Comparator.comparingDouble(Glyph::x));
            String raw = text(group);
            var first = group.getFirst();
            float right = group.stream().map(g -> g.x() + g.width()).max(Float::compare).orElse(first.x());
            float height = group.stream().map(Glyph::height).max(Float::compare).orElse(0f);
            result.add(new Line(raw, group, new SourceLine(page.number(), result.size() + 1,
                    raw, first.x(), first.y(), right - first.x(), height)));
        }
        return result;
    }

    private static float[] header(List<Glyph> glyphs) {
        var groups = new ArrayList<List<Glyph>>();
        Glyph previous = null;
        for (var glyph : glyphs) {
            if (previous == null || glyph.x() - previous.x() - previous.width() > 8) groups.add(new ArrayList<>());
            groups.getLast().add(glyph); previous = glyph;
        }
        if (groups.size() != HEADERS.size()) return null;
        float[] anchors = new float[HEADERS.size()];
        for (int i = 0; i < anchors.length; i++) {
            if (!text(groups.get(i)).equalsIgnoreCase(HEADERS.get(i))) return null;
            anchors[i] = groups.get(i).getFirst().x();
        }
        return anchors;
    }

    private static String[] cells(List<Glyph> glyphs, float[] anchors) {
        var buckets = new ArrayList<List<Glyph>>();
        for (int i = 0; i < anchors.length; i++) buckets.add(new ArrayList<>());
        for (var glyph : glyphs) {
            int column = 0;
            while (column + 1 < anchors.length && glyph.x() >= anchors[column + 1] - 2) column++;
            buckets.get(column).add(glyph);
        }
        return buckets.stream().map(MpesaTableParser::text).toArray(String[]::new);
    }

    private static String text(List<Glyph> glyphs) {
        var result = new StringBuilder();
        Glyph previous = null;
        for (var glyph : glyphs) {
            if (previous != null && glyph.x() - previous.x() - previous.width() > 1.5
                    && !result.isEmpty() && result.charAt(result.length() - 1) != ' ') result.append(' ');
            result.append(glyph.text()); previous = glyph;
        }
        return result.toString().strip();
    }

    private static boolean metadata(String text) {
        return text.equals("M-PESA STATEMENT") || text.startsWith("Statement Period:")
                || text.startsWith("Currency:") || text.matches("Page \\d+ of \\d+")
                || List.of("Opening Balance:", "Closing Balance:", "Total Credits:", "Total Debits:", "Transaction Count:")
                        .stream().anyMatch(text::startsWith);
    }
    private static void warn(List<Diagnostic> warnings, String code, SourceLine source) {
        warnings.add(new Diagnostic(code, "Parser requires review: " + code, null, source == null ? null : source.page()));
    }
    private static AnalysisProcessingException unsupported(ExtractedDocument document) {
        return new AnalysisProcessingException(ProcessingErrorCode.UNSUPPORTED_LAYOUT, document.correlationId());
    }
    private record DateValue(LocalDate date, LocalDateTime time) { }
    private record Line(String text, List<Glyph> glyphs, SourceLine source) { }
    private static final class Builder {
        private final String[] cells;
        private final List<String> narration = new ArrayList<>();
        private final List<SourceLine> sources = new ArrayList<>();
        Builder(String[] cells, SourceLine source) {
            this.cells = cells; narration.add(cells[2]); sources.add(source);
        }
    }
    private static final class RowError extends RuntimeException {
        RowError(String code) { super(code); }
    }
}

