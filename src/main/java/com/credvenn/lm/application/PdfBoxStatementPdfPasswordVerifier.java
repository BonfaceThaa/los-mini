package com.credvenn.lm.application;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.springframework.stereotype.Component;

@Component
public class PdfBoxStatementPdfPasswordVerifier implements StatementPdfPasswordVerifier {

    @Override
    public boolean canOpen(byte[] pdfBytes, String otp) {
        if (pdfBytes == null || pdfBytes.length == 0 || otp == null || otp.isBlank()) {
            return false;
        }
        try (PDDocument document = PDDocument.load(pdfBytes, otp)) {
            return document.isEncrypted();
        } catch (InvalidPasswordException ex) {
            return false;
        } catch (Exception ex) {
            return false;
        }
    }
}
