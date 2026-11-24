package com.credvenn.stmt.analyzer.tests;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;

/** Entirely synthetic provisional table layout, not a customer statement. */
public final class StatementPdfFixtures {
    private static final float[] X = {20, 90, 190, 440, 510, 570, 650};
    private StatementPdfFixtures() { }

    public static void main(String[] args) throws Exception {
        Path path = Path.of(args.length == 0 ? "analyzer-tests/target/fixtures/mpesa-table-v1.pdf" : args[0]);
        Files.createDirectories(path.toAbsolutePath().getParent());
        Files.write(path, args.length > 1 ? (args[1].equals("included-receipts") ? includedReceipts() : args[1].equals("timestamp-boundary") ? timestampBoundary() : args[1].equals("reverse-overdraft") ? reverseOverdraft() : financial(args[1])) : create("wrapped"));
        System.out.println("Synthetic statement fixture written.");
    }

    /** Synthetic month spanning all receipt channels, lender overrides and Sun King. */
    public static byte[] includedReceipts() throws Exception {
        String[] details = {
            "Funds received from - SYN",
            "Business Payment from 300600 - SYN via API. Original conversation ID is SYN",
            "Salary Payment from 504900 - SYN via API",
            "Transfer from Bank 517819 - SYN to Customer via API",
            "Deposit of Funds at Agent Till 2011418 - SYN",
            "Offnet B2C Transfer by 966888 SYN via API to - SYN",
            "Receive International Zero Rated Transfer From 4133831 - SYN",
            "Promotion Payment from 3033815 - SYN via API",
            "Merchant Customer Payment from 9541476 - SYN",
            "Salary Payment from 546476 - SYN via API",
            "Business Payment from 979988 - SYN via API",
            "OverDraft of Credit Party",
            "OD Loan Repayment to 3201003 - SYN",
            "Pay Bill Fuliza M-Pesa to 323458 - SYN Acc. SYN",
            "KCB M-PESA Loan Repayment",
            "Merchant Payment Fuliza M-Pesa to 8314985 - SYN",
            "Pay Bill Online to 1234567 - SYN Acc. SYN",
            "Airtime Purchase",
            "Pay Merchant Charge",
            "Customer Withdrawal At Agent Till 386106 - SYN Next to Faulu Microfinance"};
        try (var doc = new PDDocument()) {
            var page = new PDPage(new PDRectangle(792, 1000)); doc.addPage(page);
            String[] metadata = {"M-PESA STATEMENT", "Statement Period: 2026-10-01 to 2026-10-31", "Currency: KES",
                "Opening Balance: 1000.00", "Closing Balance: 12200.00", "Total Credits: 12000.00", "Total Debits: 800.00", "Transaction Count: 20"};
            float y=980;
            for (String line : metadata) { text(doc,page,20,y,line); y-=15; }
            row(doc,page,y,"Receipt No.","Completion Time","Details","Transaction Status","Paid In","Withdrawn","Balance"); y-=20;
            var balance=new java.math.BigDecimal("1000.00");
            for (int i=0;i<details.length;i++) {
                boolean credit=i<12;
                var amount=new java.math.BigDecimal(credit?"1000.00":"100.00");
                balance=credit?balance.add(amount):balance.subtract(amount);
                String full=details[i]; int split=full.length()>48?full.lastIndexOf(' ',48):-1;
                String first=split<0?full:full.substring(0,split);
                row(doc,page,y,"NEW"+i,"2026-10-05 10:00:00",first,"Completed",credit?"1000.00":"-",credit?"-":"100.00",balance.toPlainString());
                y-=15;
                if(split>=0) { text(doc,page,190,y,full.substring(split+1)); y-=15; }
            }
            text(doc,page,20,20,"Page 1 of 1");
            var out=new ByteArrayOutputStream(); doc.save(out); return out.toByteArray();
        }
    }

    /** Same timestamp rows are intentionally not in their balance order. */
    public static byte[] timestampBoundary() throws Exception {
        try(var doc=new PDDocument()) {
            var p=new PDPage(new PDRectangle(792,612));doc.addPage(p);
            String[] lines={"M-PESA STATEMENT","Statement Period: 2026-03-01 to 2026-03-31","Currency: KES",
                "Opening Balance: 0.00","Closing Balance: 567.72","Total Credits: 1000.00","Total Debits: 432.28","Transaction Count: 3"};
            float y=590;for(var line:lines){text(doc,p,20,y,line);y-=15;}
            row(doc,p,450,"Receipt No.","Completion Time","Details","Transaction Status","Paid In","Withdrawn","Balance");
            row(doc,p,430,"GROUPREP","2026-03-22 12:50:27","OD Loan Repayment to SYN","Completed","-","252.28","747.72");
            row(doc,p,410,"GROUPIN","2026-03-22 12:50:27","Funds received from - SYN","Completed","1000.00","-","1000.00");
            row(doc,p,390,"GROUPBUY","2026-03-22 13:39:32","Merchant Payment to SYN","Completed","-","180.00","567.72");
            text(doc,p,20,20,"Page 1 of 1");var out=new ByteArrayOutputStream();doc.save(out);return out.toByteArray();
        }
    }

    public static byte[] reverseOverdraft() throws Exception {
        try(var doc=new PDDocument()) {
            var p=new PDPage(new PDRectangle(792,612));doc.addPage(p);
            String[] lines={"M-PESA STATEMENT","Statement Period: 2026-06-01 to 2026-06-30","Currency: KES",
                "Opening Balance: 138.81","Closing Balance: 900.00","Total Credits: 931.19","Total Debits: 170.00","Transaction Count: 3"};
            float y=590;for(var line:lines){text(doc,p,20,y,line);y-=15;}
            row(doc,p,450,"Receipt No.","Completion Time","Details","Transaction Status","Paid In","Withdrawn","Balance");
            row(doc,p,430,"ODPAIR","2026-06-07 07:45:31","Merchant Payment Fuliza M-Pesa to SYN","Completed","-","170.00","0.00");
            row(doc,p,410,"ODPAIR","2026-06-07 07:45:31","OverDraft of Credit Party","Completed","31.19","-","170.00");
            row(doc,p,390,"ODDEPOSIT","2026-06-07 08:00:00","Deposit of Funds at Agent Till SYN","Completed","900.00","-","900.00");
            text(doc,p,20,20,"Page 1 of 1");var out=new ByteArrayOutputStream();doc.save(out);return out.toByteArray();
        }
    }

    public static byte[] financial(String scenario) throws Exception {
        String[] details = {"Business Payment from SYN via API", "Funds received from - SYN",
            "Unit Trust Withdraw From SYN ZIIDI MMF", "OverDraft of Credit Party",
            "Airtime Purchase with Fuliza", "Merchant Payment Fuliza M-Pesa to SYN",
            "OD Loan Repayment to SYN M-PESA Overdraw",
            "Customer Send Money to Micro SME Business with Fuliza MPesa to - SYN",
            "Customer Transfer of Funds Charge"};
        String[] amounts = {"13500.00", "5000.00", "8700.00", "4876.81", "500.00", "2000.00", "3000.00", "1000.00", "100.00"};
        if (scenario.equals("negative")) amounts[4] = "20000.00";
        if (scenario.equals("zero")) amounts[4] = "12400.00";
        if (scenario.equals("unknown")) details[0] = "Unsupported synthetic receipt";
        java.math.BigDecimal credits = new java.math.BigDecimal("32076.81");
        java.math.BigDecimal debits = java.math.BigDecimal.ZERO;
        for (int i = 4; i < amounts.length; i++) debits = debits.add(new java.math.BigDecimal(amounts[i]));
        var opening = new java.math.BigDecimal("1000.00");
        var closing = opening.add(credits).subtract(debits);
        String period = scenario.equals("partial") ? "2026-10-05 to 2026-10-31"
                : scenario.equals("cross-month") ? "2026-05-01 to 2026-11-30"
                : scenario.equals("zero-month") ? "2026-10-01 to 2026-11-30" : "2026-10-01 to 2026-10-31";
        try (var document = new PDDocument()) {
            var page = new PDPage(new PDRectangle(792, 612)); document.addPage(page);
            text(document, page, 20, 590, "M-PESA STATEMENT");
            text(document, page, 20, 575, "Statement Period: " + period);
            text(document, page, 20, 560, "Currency: KES");
            if (!scenario.equals("no-opening")) text(document, page, 20, 545, "Opening Balance: 1000.00");
            text(document, page, 20, 530, "Closing Balance: " + closing.toPlainString());
            text(document, page, 20, 515, "Total Credits: " + (scenario.equals("totals-mismatch") ? "32077.81" : "32076.81"));
            text(document, page, 20, 500, "Total Debits: " + debits.toPlainString());
            text(document, page, 20, 485, "Transaction Count: 9");
            row(document, page, 450, "Receipt No.", "Completion Time", "Details", "Transaction Status", "Paid In", "Withdrawn", "Balance");
            var order = new java.util.ArrayList<Integer>();
            if (scenario.equals("cross-month")) {
                for (int i = 4; i < 9; i++) order.add(i);
                for (int i = 0; i < 4; i++) order.add(i);
            } else for (int i = 0; i < 9; i++) order.add(i);
            var balance = opening;
            float y = 430;
            for (int index : order) {
                var amount = new java.math.BigDecimal(amounts[index]);
                balance = index < 4 ? balance.add(amount) : balance.subtract(amount);
                if (scenario.equals("missing-row") && index == 2) continue;
                String detailsFirst = details[index], continuation = null;
                if (detailsFirst.length() > 55) {
                    int split = detailsFirst.lastIndexOf(' ', 50);
                    continuation = detailsFirst.substring(split + 1); detailsFirst = detailsFirst.substring(0, split);
                }
                String date = scenario.equals("cross-month") && index >= 4 ? "2026-05-05" : "2026-10-05";
                String receipt = scenario.equals("duplicate") && index == 1 ? "FIN0" : "FIN" + index;
                String running = scenario.equals("no-opening") || scenario.equals("missing-balances") ? "-"
                        : scenario.equals("balance-mismatch") && index == 3 ? balance.add(java.math.BigDecimal.ONE).toPlainString() : balance.toPlainString();
                row(document, page, y, receipt, date + (scenario.equals("balance-mismatch") ? " 10:00:0" + index : " 10:00:00"), detailsFirst, "Completed",
                        index < 4 ? amounts[index] : "-", index >= 4 ? amounts[index] : "-", running);
                y -= 15;
                if (continuation != null) { text(document, page, 190, y, continuation); y -= 15; }
            }
            text(document, page, 20, 20, "Page 1 of 1");
            var out = new ByteArrayOutputStream(); document.save(out); return out.toByteArray();
        }
    }

    public static byte[] create(String scenario) throws Exception {
        String period = scenario.equals("year-boundary") ? "2026-12-01 to 2027-01-31"
                : scenario.equals("ambiguous-year") ? "2025-01-01 to 2026-12-31" : "2026-10-01 to 2026-10-31";
        String firstDate = scenario.equals("year-boundary") ? "31/12 09:00:00"
                : scenario.equals("ambiguous-year") || scenario.equals("missing-period") ? "05/10 09:00:00"
                : scenario.equals("invalid-date") ? "2026-02-30 09:00:00"
                : scenario.equals("date-only") ? "2026-10-05" : "2026-10-05 09:00:00";
        String secondDate = scenario.equals("year-boundary") ? "01/01 10:00:00" : "2026-10-06 10:00:00";
        String thirdDate = scenario.equals("year-boundary") ? "02/01 11:00:00" : "2026-10-07 11:00:00";
        try (var document = new PDDocument()) {
            var page1 = page(document, period, scenario);
            String credit = scenario.equals("bad-amount") ? "1,00.00"
                    : scenario.equals("negative-amount") ? "-1,000.00" : "1,000.00";
            if (scenario.equals("orphan")) text(document, page1, 190, 510, "Unattached details");
            row(document, page1, 490, scenario.equals("missing-reference") ? "-" : "SYN00001", firstDate,
                    "Funds received from -", "Completed", credit, scenario.equals("both-directions") ? "20.00" : "-", "1,100.00");
            var page2 = page(document, period, scenario);
            text(document, page2, 190, 510, "SYNTHETIC SENDER");
            row(document, page2, 490, "SYN00002", secondDate, "Business Payment from", "Completed", "1,000.00", "-", "2,100.00");
            text(document, page2, 190, 475, "SYNTHETIC MERCHANT via API");
            row(document, page2, 450, "SYN00003", thirdDate, "Airtime Purchase with Fuliza",
                    "Completed", "-", "50.00", scenario.equals("negative-balance") ? "-50.00" : "2,050.00");
            var out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    private static PDPage page(PDDocument document, String period, String scenario) throws Exception {
        var page = new PDPage(new PDRectangle(792, 612));
        document.addPage(page);
        text(document, page, 20, 590, "M-PESA STATEMENT");
        if (!scenario.equals("missing-period")) text(document, page, 20, 575, "Statement Period: " + period);
        if (!scenario.equals("missing-currency")) text(document, page, 20, 560, "Currency: KES");
        if (!scenario.equals("unsupported")) row(document, page, 530, "Receipt No.", "Completion Time",
                "Details", "Transaction Status", "Paid In", "Withdrawn", "Balance");
        text(document, page, 20, 20, "Page " + document.getNumberOfPages() + " of 2");
        return page;
    }

    private static void row(PDDocument doc, PDPage page, float y, String... cells) throws Exception {
        for (int i = 0; i < cells.length; i++) text(doc, page, X[i], y, cells[i]);
    }

    private static void text(PDDocument doc, PDPage page, float x, float y, String value) throws Exception {
        try (var stream = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true)) {
            stream.beginText();
            stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 7);
            stream.newLineAtOffset(x, y);
            stream.showText(value);
            stream.endText();
        }
    }
}
