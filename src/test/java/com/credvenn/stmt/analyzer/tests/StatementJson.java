package com.credvenn.stmt.analyzer.tests;

import com.credvenn.stmt.analyzer.core.parsing.ParsedStatement;
import java.util.stream.Collectors;

/** Small deterministic fixture projection, not a production API serializer. */
public final class StatementJson {
    private StatementJson() { }
    public static String write(ParsedStatement result) {
        String rows = result.transactions().stream().map(row -> "{"
                + "\"sourceRowKey\":" + quote(row.sourceRowKey())
                + ",\"reference\":" + quote(row.sourceTransactionId())
                + ",\"date\":" + quote(row.transactionDate())
                + ",\"timestamp\":" + quote(row.transactionAt())
                + ",\"direction\":" + quote(row.direction())
                + ",\"amount\":" + quote(row.amount())
                + ",\"currency\":" + quote(row.currency())
                + ",\"rawNarration\":" + quote(row.rawNarration())
                + ",\"normalizedNarration\":" + quote(row.normalizedNarration())
                + ",\"runningBalance\":" + quote(row.runningBalance())
                + ",\"sourcePages\":[" + row.sourceLines().stream().map(line -> "" + line.page())
                        .collect(Collectors.joining(",")) + "]}"
        ).collect(Collectors.joining(","));
        return "{\"parserVersion\":" + quote(result.parserVersion())
                + ",\"status\":" + quote(result.status())
                + ",\"periodStart\":" + quote(result.period() == null ? null : result.period().start())
                + ",\"periodEnd\":" + quote(result.period() == null ? null : result.period().end())
                + ",\"currency\":" + quote(result.currency())
                + ",\"transactions\":[" + rows + "]"
                + ",\"rejectedCodes\":[" + result.rejectedRows().stream().map(row -> quote(row.reasonCode()))
                        .collect(Collectors.joining(",")) + "]"
                + ",\"warningCodes\":[" + result.warnings().stream().map(warning -> quote(warning.code()))
                        .collect(Collectors.joining(",")) + "]}";
    }
    private static String quote(Object value) {
        if (value == null) return "null";
        var result = new StringBuilder("\"");
        for (char c : value.toString().toCharArray()) {
            switch (c) {
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (c < 32) result.append(String.format("\\u%04x", (int) c));
                    else result.append(c);
                }
            }
        }
        return result.append('"').toString();
    }
}
