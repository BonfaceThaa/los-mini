package com.credvenn.lm.devicecontrol;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class LoanRepaymentPostedListener {

    private final DeviceControlCollectionsService collectionsService;

    public LoanRepaymentPostedListener(DeviceControlCollectionsService collectionsService) {
        this.collectionsService = collectionsService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRepaymentPosted(LoanRepaymentPostedEvent event) {
        collectionsService.handleRepaymentPosted(event.tenantId(), event.applicationId(), event.fineractLoanId(), event.receiptId());
    }
}
