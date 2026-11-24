package com.credvenn.stmt.analyzer.core.analysis;

import com.credvenn.stmt.analyzer.core.AnalysisResult.*;
import java.io.IOException;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/** Included-receipts policy; verification denotes policy acceptance, not employment evidence. */
public final class NarrationClassifier {
    public static final String VERSION = "narration-v4";
    private static final Pattern PAYEE = Pattern.compile("\\b(?:TO|FROM(?:\\s+BUSINESS)?|BY)\\s+(\\d{5,8})(?!\\d)");
    private static final Set<String> LENDER_CREDITS = Set.of("3031389","546476","979988","4033285","3028197","733755");
    private static final Set<String> LENDER_DEBITS = Set.of("7034211","6010640","650880","333222","4087825","979988");
    private static final Map<String,String> MERCHANTS = merchants();
    private static final List<String[]> PURPOSE_WORDS = List.of(
        new String[]{"Food","RESTAURANT|GROCER|BUTCHER"},
        new String[]{"Health","PHARMACY|CHEMIST|PHARMACEUTICAL|MEDICAL|HOSPITAL|CLINIC"},
        new String[]{"Fuel","PETROLEUM|PETROL|FILLING STATION|TOTALENERGIES|\\bSHELL\\b"},
        new String[]{"Supermarket","SUPERMARKET|\\bNAIVAS\\b|CLEANSHELF|\\bMAGUNAS\\b"},
        new String[]{"Entertainment","\\bBAR\\b|LOUNGE|FOOD COURT"},
        new String[]{"Church","\\bCHURCH\\b"}, new String[]{"Gambling","SPORTYBET|SPORTPESA|BETIKA"},
        new String[]{"Education","UNIVERSITY|\\bSCHOOL\\b|COLLEGE"}, new String[]{"Water","\\bWATER\\b"},
        new String[]{"Travel","TOURS AND TRAVELS|MADARAKA EXPRESS|DREAMLINE EXPRESS"},
        new String[]{"Airtime","\\bAIRTIME\\b"},new String[]{"Energy","KPLC|M-GAS"},
        new String[]{"Beauty","COSMETIC|BARBERSHOP|DREADLOCK|\\bSALON\\b"},
        new String[]{"Stationery / books","BOOKSHOP|STATIONARY|STATIONERY"});
    private static Map<String,String> merchants() {
        var values = new Properties();
        try (var in = NarrationClassifier.class.getResourceAsStream("/merchant-categories.properties")) {
            if (in == null) throw new IllegalStateException("Missing merchant category rules");
            values.load(in);
        } catch (IOException e) { throw new IllegalStateException("Cannot load merchant category rules"); }
        var result = new HashMap<String,String>();
        values.forEach((k,v) -> result.put((String) k, (String) v));
        return Map.copyOf(result);
    }
    private static String code(String value) {
        return value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_|_$", "");
    }
    private static boolean starts(String s, String... prefixes) {
        return Arrays.stream(prefixes).anyMatch(s::startsWith);
    }
    private static Classification result(Direction d, String text, String type, String category,
            String main, String sub, String purpose, BalanceEligibility eligibility, String explanation) {
        var verification = d == Direction.DEBIT ? ReceiptVerification.NOT_APPLICABLE
                : eligibility == BalanceEligibility.ELIGIBLE ? ReceiptVerification.VERIFIED : ReceiptVerification.UNVERIFIED;
        return new Classification(type, category, eligibility, verification, VERSION + ":" + purpose,
                explanation, main, sub, purpose, text.contains("FULIZA") || text.startsWith("OD LOAN REPAYMENT") ? "FULIZA" : null);
    }
    private static Classification income(Direction d, String s, String type, String category, String sub) {
        return result(d,s,type,category,"Income",sub,type,BalanceEligibility.ELIGIBLE,
                "Included statement receipt; not independent evidence of earned income. Known lender rules take precedence.");
    }
    private static Classification spending(Direction d, String s, String type, String category, String sub, String purpose) {
        return result(d,s,type,category,"Spending",sub,purpose,BalanceEligibility.ELIGIBLE,
                "Deduct spending once; a more specific purchase purpose is not required. Fuliza records funding only.");
    }
    private static Classification excluded(Direction d, String s, String type, String category, String main, String sub) {
        return result(d,s,type,category,main,sub,type,BalanceEligibility.INELIGIBLE,"Excluded movement under the included-receipts policy.");
    }
    private static Classification repayment(Direction d, String s, String type, String sub) {
        return result(d,s,type,"DEBT_REPAYMENT","Loans",sub,type,BalanceEligibility.ELIGIBLE,
                "Deduct loan repayment once; known lender rule takes precedence over generic payment wording.");
    }
    public Classification classify(Direction direction, String narration) {
        return classify(direction, narration, null);
    }
    /** Status is required for the user-approved empty-Details repayment assumption. */
    public Classification classify(Direction direction, String narration, String transactionStatus) {
        Objects.requireNonNull(direction);
        String s = normalize(narration);
        if (s.isBlank() && direction == Direction.DEBIT && "Completed".equalsIgnoreCase(transactionStatus)) {
            return new Classification("OVERDRAFT_REPAYMENT", "DEBT_REPAYMENT", BalanceEligibility.ELIGIBLE,
                    ReceiptVerification.NOT_APPLICABLE, VERSION + ":COMPLETED_DEBIT_EMPTY_DETAILS",
                    "Policy assumption: completed debit with empty Details is treated as Fuliza repayment; original empty narration retained.",
                    "Loans", "Repayment - overdraft", "OVERDRAFT_REPAYMENT", "FULIZA", "Fuliza");
        }
        if(direction==Direction.DEBIT && starts(s,"PAY BILL CHARGE","PAY MERCHANT CHARGE","WITHDRAWAL CHARGE","CUSTOMER TRANSFER OF FUNDS CHARGE"))
            return spending(direction,s,"TRANSACTION_FEE","FINANCIAL_FEE","Transaction fees","TRANSACTION_FEE");
        var classified = classifyNormalized(direction, s);
        var payeeMatcher=PAYEE.matcher(s);
        String payee=payeeMatcher.find()?payeeMatcher.group(1):"";
        boolean payment=direction==Direction.DEBIT && !starts(s,"PAY BILL CHARGE","PAY MERCHANT CHARGE","WITHDRAWAL CHARGE","CUSTOMER TRANSFER OF FUNDS CHARGE") && starts(s,"PAY BILL","MERCHANT PAYMENT","SMALL BUSINESS PAY BILL",
                "CUSTOMER SEND MONEY TO MICRO SME BUSINESS","CUSTOMER PAYMENT TO SMALL BUSINESS");
        boolean receipt=direction==Direction.CREDIT && starts(s,"BUSINESS PAYMENT FROM","SALARY PAYMENT FROM","FUNDS RECEIVED FROM BUSINESS");
        var provider=LoanProviders.match(payee,s,payment);
        if(payment && provider!=null) {
            classified=repayment(direction,s,"LOAN_REPAYMENT","Repayment - other lender");
            return withProvider(classified,provider.name(),"Matched "+provider.basis()+"; deduct repayment once.");
        }
        if(receipt && provider!=null) {
            classified=excluded(direction,s,"LOAN_DISBURSEMENT","BORROWED_FUNDS","Loans","Disbursement - other lender");
            return withProvider(classified,provider.name(),"Matched lender recipient-name alias; excluded borrowing, including salary/business-labelled receipts.");
        }
        if(classified.subCategory().equals("Disbursement - overdraft")) return withProvider(classified,"Fuliza",classified.explanation());
        if(classified.subCategory().equals("Disbursement - M-Shwari")) return withProvider(classified,"M-Shwari",classified.explanation());
        if(classified.economicCategory().equals("DEBT_REPAYMENT")) {
            String name=classified.transactionType().equals("OVERDRAFT_REPAYMENT")?"Fuliza"
                    :s.startsWith("KCB M-PESA LOAN REPAYMENT")?"KCB M-PESA"
                    :s.startsWith("M-SHWARI LOAN REPAYMENT")?"M-Shwari"
                    :s.startsWith("TIMIZA LOAN REPAYMENT")?"Timiza"
                    :payee.equals("323458")?"Sun King":null;
            return withProvider(classified,name,classified.explanation());
        }
        if(payment && LoanProviders.genericLender(s))return new Classification("POSSIBLE_LOAN_REPAYMENT",
                "POSSIBLE_DEBT_REPAYMENT",BalanceEligibility.UNDETERMINED,ReceiptVerification.NOT_APPLICABLE,
                VERSION+":POSSIBLE_LENDER","Recipient contains Credit or Microfinance; provider/purpose requires review.",
                "Review","Possible loan repayment","POSSIBLE_LOAN_REPAYMENT",classified.fundingSource(),"Unidentified credit provider");
        return classified;
    }
    private static Classification withProvider(Classification c,String name,String explanation) {
        return new Classification(c.transactionType(),c.economicCategory(),c.balanceEligibility(),c.receiptVerification(),
                c.ruleId(),explanation,c.mainCategory(),c.subCategory(),c.purposeClassification(),c.fundingSource(),name);
    }
    static String normalize(String narration) {
        return Normalizer.normalize(Objects.requireNonNull(narration), Normalizer.Form.NFKC)
                .replaceAll("[\\u2010-\\u2014\\u2212]", "-").replaceAll("(?U)\\s+", " ").strip().toUpperCase(Locale.ROOT);
    }
    private Classification classifyNormalized(Direction direction, String s) {
        var matcher = PAYEE.matcher(s);
        String payee = matcher.find() ? matcher.group(1) : "";
        if (direction == Direction.CREDIT) {
            if (s.startsWith("OVERDRAFT OF CREDIT PARTY")) return excluded(direction,s,"OVERDRAFT_DISBURSEMENT","BORROWED_FUNDS","Loans","Disbursement - overdraft");
            if (s.startsWith("M-SHWARI LOAN DISBURSE")) return excluded(direction,s,"LOAN_DISBURSEMENT","BORROWED_FUNDS","Loans","Disbursement - M-Shwari");
            if (LENDER_CREDITS.contains(payee)) return excluded(direction,s,"LOAN_DISBURSEMENT","BORROWED_FUNDS","Loans","Disbursement - other lender");
            if (s.startsWith("UNIT TRUST WITHDRAW FROM") && s.contains("ZIIDI MMF")) return excluded(direction,s,"INVESTMENT_WITHDRAWAL","OWN_ASSET_TRANSFER","Transfers","Investment withdrawal");
            if (s.startsWith("REVERSAL")) return excluded(direction,s,"REVERSAL_CREDIT","REVERSAL","Adjustments","Reversal");
            if (starts(s,"FUNDS RECEIVED FROM -","RECEIVE FUNDS FROM")) return income(direction,s,"P2P_RECEIPT","PERSONAL_TRANSFER","Personal receipts");
            if (starts(s,"BUSINESS PAYMENT FROM","FUNDS RECEIVED FROM BUSINESS")) return income(direction,s,"B2C_BUSINESS_PAYMENT","BUSINESS_RECEIPT","Business receipts");
            if (s.startsWith("SALARY PAYMENT FROM")) return income(direction,s,"B2C_SALARY_PAYMENT","POTENTIAL_SALARY","Salary-labelled receipts");
            if (s.startsWith("TRANSFER FROM BANK")) return income(direction,s,"BANK_TRANSFER_IN","BANK_RECEIPT","Bank transfers in");
            if (starts(s,"DEPOSIT OF FUNDS AT AGENT TILL","PLB MERCHANT BUSINESS DEPOSIT OF FUNDS")) return income(direction,s,"CASH_DEPOSIT","CASH_RECEIPT","Cash deposits");
            if (s.startsWith("OFFNET B2C TRANSFER")) return income(direction,s,"OFFNET_RECEIPT","OTHER_NETWORK_RECEIPT","Other-network receipts");
            if (s.startsWith("RECEIVE INTERNATIONAL ZERO RATED TRANSFER")) return income(direction,s,"INTERNATIONAL_REMITTANCE","REMITTANCE","International remittances");
            if (s.startsWith("PROMOTION PAYMENT FROM")) return income(direction,s,"PROMOTION_PAYMENT","PROMOTION_COMMISSION","Promotions and commissions");
            if (starts(s,"MERCHANT CUSTOMER PAYMENT FROM","CUSTOMER PAYMENT TO SMALL BUSINESS FROM")) return income(direction,s,"MERCHANT_BUSINESS_RECEIPT","BUSINESS_RECEIPT","Merchant and small-business receipts");
        } else {
            if (s.startsWith("OD LOAN REPAYMENT TO")) return repayment(direction,s,"OVERDRAFT_REPAYMENT","Repayment - overdraft");
            if (starts(s,"M-SHWARI LOAN REPAYMENT","TIMIZA LOAN REPAYMENT")) return repayment(direction,s,"LOAN_REPAYMENT","Repayment - other lender");
            if (s.startsWith("KCB M-PESA LOAN REPAYMENT")) return repayment(direction,s,"LOAN_REPAYMENT","Repayment - KCB M-Pesa");
            if (payee.equals("323458")) return repayment(direction,s,"LOAN_REPAYMENT","Repayment - Sun King");
            if (LENDER_DEBITS.contains(payee)) return repayment(direction,s,"LOAN_REPAYMENT","Repayment - other lender");
            if (s.startsWith("CUSTOMER WITHDRAWAL AT AGENT TILL")) return excluded(direction,s,"CASH_WITHDRAWAL","CASH_MOVEMENT","Transfers","Cash withdrawals");
            if (s.startsWith("SMALL BUSINESS WITHDRAWAL FROM MPESA ACCOUNT TO BUSINESS ACCOUNT")) return excluded(direction,s,"BUSINESS_ACCOUNT_TRANSFER","OWN_ASSET_TRANSFER","Transfers","Business account transfers");
            if (starts(s,"CUSTOMER TRANSFER OF FUNDS CHARGE","PAY BILL CHARGE","PAY MERCHANT CHARGE","WITHDRAWAL CHARGE")) return spending(direction,s,"TRANSACTION_FEE","FINANCIAL_FEE","Transaction fees","TRANSACTION_FEE");
            if (s.startsWith("CUSTOMER BUNDLE PURCHASE")) return spending(direction,s,"MOBILE_DATA_PURCHASE","TELECOM_EXPENSE","Mobile data and bundles","MOBILE_DATA_PURCHASE");
            if (s.startsWith("AIRTIME PURCHASE")) return spending(direction,s,"AIRTIME_PURCHASE","TELECOM_EXPENSE","Airtime","AIRTIME_PURCHASE");
            if (starts(s,"CUSTOMER TRANSFER TO -","CUSTOMER TRANSFER FULIZA MPESA TO -")) return excluded(direction,s,"P2P_TRANSFER","UNVERIFIED_OUTGOING_TRANSFER","Transfers","Personal transfers out");
            if (s.startsWith("OFFNET C2B TRANSFER")) return excluded(direction,s,"OFFNET_TRANSFER","OTHER_NETWORK_TRANSFER","Transfers","Other-network transfers out");
            if (starts(s,"CUSTOMER SEND MONEY TO MICRO SME BUSINESS","CUSTOMER PAYMENT TO SMALL BUSINESS TO -")) return spending(direction,s,"MICRO_SME_PAYMENT","UNVERIFIED_BUSINESS_OR_PERSONAL_SPEND","Small-business payments - unspecified","MICRO_SME_PAYMENT");
            if (starts(s,"MERCHANT PAYMENT","PAY BILL","SMALL BUSINESS PAY BILL")) {
                String sub = MERCHANTS.get(payee);
                boolean exact = sub != null;
                if (sub == null) for (var rule : PURPOSE_WORDS) {
                    if (Pattern.compile(rule[1]).matcher(s).find()) { sub = rule[0]; break; }
                }
                if (sub == null) return spending(direction,s,"MERCHANT_PAYMENT","MERCHANT_SPEND","Unspecified merchant or bill outflow","UNSPECIFIED_SPENDING");
                String purpose = sub.equals("Airtime") ? "AIRTIME_PURCHASE" : "SPENDING_" + code(sub);
                var classified = spending(direction,s,"MERCHANT_PAYMENT",code(sub)+"_SPEND",sub,purpose);
                return new Classification(classified.transactionType(),classified.economicCategory(),classified.balanceEligibility(),classified.receiptVerification(),
                        classified.ruleId(), exact ? "Matched supplied merchant payee category; deduct once." : "Suggested merchant purpose from narration; spending is included even if subcategory is refined.",
                        classified.mainCategory(),classified.subCategory(),classified.purposeClassification(),classified.fundingSource());
            }
        }
        return result(direction,s,"UNKNOWN",direction==Direction.CREDIT?"UNKNOWN_SOURCE":"UNKNOWN_SPEND","Review","Missing or unsupported details","UNKNOWN",
                BalanceEligibility.UNDETERMINED,"No supported rule; inclusion requires review. Do not replace missing facts with zero.");
    }
}
