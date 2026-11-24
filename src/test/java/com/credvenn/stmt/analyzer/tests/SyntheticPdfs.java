package com.credvenn.stmt.analyzer.tests;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.encryption.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;

/** Generated sanitized fixtures, not customer statements. Passwords exist only in memory. */
public final class SyntheticPdfs {
    private SyntheticPdfs() { }

    /** Optional manual fixture export; encrypted fixtures remain in memory with random passwords. */
    public static void main(String[] args) throws IOException {
        var directory = java.nio.file.Path.of("analyzer-tests/target/fixtures");
        java.nio.file.Files.createDirectories(directory);
        java.nio.file.Files.write(directory.resolve("plain.pdf"), text(2, null, true));
        java.nio.file.Files.write(directory.resolve("image-only.pdf"), withoutText(true));
        java.nio.file.Files.write(directory.resolve("blank.pdf"), withoutText(false));
        java.nio.file.Files.write(directory.resolve("mixed.pdf"), mixed());
        java.nio.file.Files.write(directory.resolve("malformed.pdf"),
                "%PDF-1.7\ninvalid synthetic body".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        System.out.println("Synthetic PDFs written to analyzer-tests/target/fixtures");
    }

    public static byte[] text(int pages, String password, boolean allowExtraction) throws IOException {
        try (var document = new PDDocument()) {
            for (int i = 1; i <= pages; i++) {
                var page = new PDPage();
                document.addPage(page);
                try (var stream = new PDPageContentStream(document, page)) {
                    stream.beginText();
                    stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    stream.newLineAtOffset(72, 720);
                    stream.showText("SYNTHETIC STATEMENT PAGE " + i);
                    stream.newLineAtOffset(0, -20);
                    stream.showText("TEST-001 Credit KES 100.00");
                    stream.endText();
                }
            }
            if (password != null) {
                var permissions = new AccessPermission();
                permissions.setCanExtractContent(allowExtraction);
                var protection = new StandardProtectionPolicy(java.util.UUID.randomUUID().toString(),
                        password, permissions);
                protection.setEncryptionKeyLength(128);
                document.protect(protection);
            }
            return save(document);
        }
    }

    public static byte[] withoutText(boolean image) throws IOException {
        try (var document = new PDDocument()) {
            var page = new PDPage();
            document.addPage(page);
            if (image) {
                var bitmap = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
                try (var stream = new PDPageContentStream(document, page)) {
                    stream.drawImage(LosslessFactory.createFromImage(document, bitmap), 72, 600, 100, 100);
                }
            }
            return save(document);
        }
    }

    public static byte[] mixed() throws IOException {
        try (var document = org.apache.pdfbox.Loader.loadPDF(text(1, null, true))) {
            document.addPage(new PDPage());
            return save(document);
        }
    }

    private static byte[] save(PDDocument document) throws IOException {
        var output = new ByteArrayOutputStream();
        document.save(output);
        return output.toByteArray();
    }
}
