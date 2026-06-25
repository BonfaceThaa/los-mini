package com.credvenn.lm.statementinbox;

public enum InboundStatementMatchStatus {
    RECEIVED,
    PROCESSING,
    MATCHED,
    WAITING_FOR_APPLICATION,
    BLOCKED_MISSING_STATEMENT_OTP,
    UNMATCHED,
    AMBIGUOUS,
    MANUALLY_RESOLVED,
    FAILED
}
