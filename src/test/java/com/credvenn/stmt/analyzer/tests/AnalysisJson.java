package com.credvenn.stmt.analyzer.tests;

import java.lang.reflect.RecordComponent;
import java.util.*;
import java.util.stream.Collectors;

/** Local result export only; API serialization belongs to the HTTP adapter in step 7. */
public final class AnalysisJson {
    private AnalysisJson() { }
    public static String write(Object value) {
        if (value == null) return "null";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof List<?> list) return list.stream().map(AnalysisJson::write).collect(Collectors.joining(",", "[", "]"));
        if (value.getClass().isRecord()) {
            var fields = new ArrayList<String>();
            for (RecordComponent component : value.getClass().getRecordComponents()) {
                try { fields.add(quote(component.getName()) + ":" + write(component.getAccessor().invoke(value))); }
                catch (ReflectiveOperationException e) { throw new IllegalStateException("Result export failed"); }
            }
            return String.join(",", fields.stream().toList()).transform(s -> "{" + s + "}");
        }
        return quote(value.toString());
    }
    private static String quote(String value) {
        var output = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> output.append("\\\"");
                case '\\' -> output.append("\\\\");
                case '\n' -> output.append("\\n");
                case '\r' -> output.append("\\r");
                case '\t' -> output.append("\\t");
                default -> { if (c < 32) output.append(String.format("\\u%04x", (int) c)); else output.append(c); }
            }
        }
        return output.append('"').toString();
    }
}
