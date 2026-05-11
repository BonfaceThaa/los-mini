package com.credvenn.lm.statementinbox;

public record InboundStatementAcceptedEvent(
        String receiptId,
        String actor) {
}
