package com.credvenn.lm.application;

public interface StatementPdfPasswordVerifier {

    boolean canOpen(byte[] pdfBytes, String otp);
}
