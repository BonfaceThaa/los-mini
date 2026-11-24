package com.credvenn.stmt.analyzer.tests;

import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;

/** Invented values and identities; reproduces only the detailed table geometry. */
public final class DetailedStatementFixture {
    private DetailedStatementFixture() { }
    public static void main(String[] args) throws Exception {
        Files.write(Path.of(args[0]), create(args.length > 1));
    }
    public static byte[] create(boolean missingDetails) throws Exception {
        return create(missingDetails, false);
    }
    public static byte[] create(boolean missingDetails, boolean missingCreditDetails) throws Exception {
        try (var doc = new PDDocument()) {
            var first = page(doc, 1);
            text(doc, first, 38, 151.5f, "Statement Period:", 7);
            text(doc, first, 194.9f, 150.5f, "01 Oct 2026 - 31 Oct 2026", 7);
            text(doc, first, 38, 117, "Mobile Number: SYNTHETIC", 7);
            text(doc, first, 270, 207.5f, "SUMMARY", 7);
            text(doc, first, 38, 221.5f, "TRANSACTION TYPE", 7);
            text(doc, first, 38, 302.5f, "TOTAL:", 7);
            text(doc, first, 382, 302.5f, "1,000.00", 7);
            text(doc, first, 487, 302.5f, "30.00", 7);
            text(doc, first, 235.4f, 326, "DETAILED STATEMENT", 7);
            header(doc, first, 340);
            row(doc, first, 350, "SYNFEE0001", "2026-10-07 11:00:00",
                    missingDetails ? "" : "Transfer fee", "", "-10.00", "1,070.00");
            row(doc, first, 710, "SYNBUY0001", "2026-10-06 10:00:00", "Merchant Payment", "", "-20.00", "1,080.00");
            var second = page(doc, 2);
            header(doc, second, 63);
            text(doc, second, 177.5f, 75, "SYNTHETIC SHOP", 6);
            row(doc, second, 90, "SYNREC0001", "2026-10-05 09:00:00", missingCreditDetails ? "" : missingDetails ? "Funds received from -" : "Funds received from", "1,000.00", "", "1,100.00");
            if (!missingCreditDetails) text(doc, second, 177.5f, 96, "SYNTHETIC SENDER", 6);
            var out = new ByteArrayOutputStream(); doc.save(out); return out.toByteArray();
        }
    }
    private static PDPage page(PDDocument doc, int number) throws Exception {
        var p = new PDPage(new PDRectangle(595, 848)); doc.addPage(p);
        text(doc, p, 521, 44, "Page " + number + " of 2", 6);
        text(doc, p, 38, 730.5f, "Disclaimer: Synthetic fixture only", 7);
        text(doc, p, 38, 741, "Never include this footer in a transaction", 7);
        text(doc, p, 38, 760, "Synthetic footer", 7);
        return p;
    }
    private static void header(PDDocument doc, PDPage p, float y) throws Exception {
        float[] x = {50.7f,110.8f,215.6f,282.2f,372.9f,435.5f,510.2f};
        String[] labels = {"Receipt No.","Completion Time","Details","Transaction Status","Paid In","Withdrawn","Balance"};
        for (int i=0;i<7;i++) text(doc,p,x[i],y,labels[i],7);
    }
    private static void row(PDDocument doc, PDPage p, float y, String ref, String date,
            String details, String credit, String debit, String balance) throws Exception {
        text(doc,p,38,y,ref,6); text(doc,p,107.7f,y,date,6); text(doc,p,177.5f,y,details,6);
        text(doc,p,282.1f,y,"Completed",6);
        right(doc,p,419,y,credit); right(doc,p,489,y,debit); right(doc,p,559,y,balance);
    }
    private static void right(PDDocument doc, PDPage p, float edge, float y, String s) throws Exception {
        var font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        text(doc,p,edge-font.getStringWidth(s)*6/1000,y,s,6);
    }
    private static void text(PDDocument doc, PDPage p, float x, float y, String s, float size) throws Exception {
        try(var stream = new PDPageContentStream(doc,p,PDPageContentStream.AppendMode.APPEND,true)) {
            stream.beginText();stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),size);
            stream.newLineAtOffset(x,p.getMediaBox().getHeight()-y);stream.showText(s);stream.endText();
        }
    }
}
