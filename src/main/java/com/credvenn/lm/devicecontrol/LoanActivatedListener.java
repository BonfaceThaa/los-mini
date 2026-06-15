package com.credvenn.lm.devicecontrol;

import com.credvenn.lm.application.LoanActivatedEvent;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class LoanActivatedListener {

    private final DeviceControlCollectionsService collectionsService;

    public LoanActivatedListener(DeviceControlCollectionsService collectionsService) {
        this.collectionsService = collectionsService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLoanActivated(LoanActivatedEvent event) {
        collectionsService.handleLoanActivated(
                event.tenantId(),
                event.applicationId(),
                event.fineractLoanId(),
                event.actor());
    }
}
