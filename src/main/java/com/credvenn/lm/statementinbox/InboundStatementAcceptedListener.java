package com.credvenn.lm.statementinbox;

import com.credvenn.lm.common.logging.LoggingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class InboundStatementAcceptedListener {

    private static final Logger log = LoggerFactory.getLogger(InboundStatementAcceptedListener.class);

    private final InboundStatementProcessor processor;

    public InboundStatementAcceptedListener(InboundStatementProcessor processor) {
        this.processor = processor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInboundStatementAccepted(InboundStatementAcceptedEvent event) {
        try (LoggingContext.Scope ignored = LoggingContext.withApplication(event.receiptId())) {
            log.info("Received inbound-statement-accepted event and starting background routing");
            processor.process(event.receiptId(), event.actor());
        }
    }
}
