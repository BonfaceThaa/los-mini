package com.credvenn.lm.payment;

import com.credvenn.lm.common.logging.LoggingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class MpesaPaymentAcceptedListener {

    private static final Logger log = LoggerFactory.getLogger(MpesaPaymentAcceptedListener.class);

    private final MpesaPaymentProcessor processor;

    public MpesaPaymentAcceptedListener(MpesaPaymentProcessor processor) {
        this.processor = processor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAccepted(MpesaPaymentAcceptedEvent event) {
        try (LoggingContext.Scope ignored = LoggingContext.withApplication(event.receiptId())) {
            log.info("Received mpesa-payment-accepted event and starting repayment processing");
            processor.process(event.receiptId());
        }
    }
}
