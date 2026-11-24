package com.credvenn.lm.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.junit.jupiter.api.Test;

class PdfBoxStatementPdfPasswordVerifierTest {
    @Test
    void verifiesEncryptedPdfAndRejectsInvalidInputs() throws Exception {
        byte[] encrypted;
        byte[] unencrypted;
        try (PDDocument document = new PDDocument(); var output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(output);
            unencrypted = output.toByteArray();
            document.protect(new StandardProtectionPolicy("owner-password", "test-password", new AccessPermission()));
            output.reset();
            document.save(output);
            encrypted = output.toByteArray();
        }
        var verifier = new PdfBoxStatementPdfPasswordVerifier();
        assertTrue(verifier.canOpen(encrypted, "test-password"));
        assertFalse(verifier.canOpen(encrypted, "wrong"));
        assertFalse(verifier.canOpen(unencrypted, "test-password"));
        assertFalse(verifier.canOpen(new byte[] {1, 2, 3}, "test-password"));
        assertFalse(verifier.canOpen(encrypted, null));
    }
}
