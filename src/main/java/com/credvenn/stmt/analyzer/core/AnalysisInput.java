package com.credvenn.stmt.analyzer.core;

/** PDF bytes are copied on input/output; neither content nor password is printed. */
public record AnalysisInput(byte[] pdfContent, String password, String documentType, String correlationId) {
    public AnalysisInput {
        pdfContent = pdfContent == null ? null : pdfContent.clone();
    }

    @Override
    public byte[] pdfContent() {
        return pdfContent == null ? null : pdfContent.clone();
    }

    public int contentLength() {
        return pdfContent == null ? 0 : pdfContent.length;
    }

    @Override
    public String toString() {
        return "AnalysisInput[content=REDACTED, password=REDACTED, documentType=REDACTED, correlationId=REDACTED]";
    }
}
