package com.credvenn.lm.logbook;
import com.credvenn.lm.tenant.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
@ConditionalOnProperty(name="app.logbook.workflow-worker-enabled",havingValue="true",matchIfMissing=true)
public class LogbookLoanScheduler {
    private static final Logger log=LoggerFactory.getLogger(LogbookLoanScheduler.class);
    private final TenantRepository tenants;
    private final LogbookLoanOperationRepository operations;
    private final LogbookLoanProcessor processor;
    @Scheduled(fixedDelayString="${app.logbook.workflow-poll-ms:5000}",initialDelayString="${app.logbook.workflow-poll-ms:5000}")
    public void processQueued() {
        // Platform enumeration only; every business-record lookup and dispatch is tenant scoped.
        for(var tenant:tenants.findAll()) {
            if(!tenant.isActive())continue;
            for(var op:operations.findTop20ByTenantIdAndStateOrderByCreatedAtAsc(tenant.getId(),LogbookLoanOperation.State.QUEUED)) {
                try {processor.process(tenant.getId(),op.getApplicationId(),op.getId());}
                catch(Exception ex){log.error("Cannot process logbook operation tenantId={} operationId={}",tenant.getId(),op.getId(),ex);}
            }
        }
    }
}
