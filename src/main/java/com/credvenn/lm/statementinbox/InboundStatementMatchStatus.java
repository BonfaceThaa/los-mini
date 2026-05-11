package com.credvenn.lm.statementinbox;

public enum InboundStatementMatchStatus {
    RECEIVED,
    PROCESSING,
    MATCHED,
    UNMATCHED,
    AMBIGUOUS,
    MANUALLY_RESOLVED,
    FAILED
}
