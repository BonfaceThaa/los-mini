package com.credvenn.lm.logbook;

import static com.credvenn.lm.logbook.LogbookLoanOperation.*;
import com.credvenn.lm.application.*;
import com.credvenn.lm.common.exception.BadRequestException;
import com.credvenn.lm.common.logging.LoggingContext;
import com.credvenn.lm.fineract.FineractGateway;
import com.credvenn.lm.origination.OriginationProfileDtos.Stage;
import com.credvenn.lm.subscription.SubscriptionBillingService;
import com.credvenn.lm.tenant.TenantService;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service @RequiredArgsConstructor
public class LogbookLoanProcessor {
    private static final Logger log=LoggerFactory.getLogger(LogbookLoanProcessor.class);
    private final PlatformTransactionManager transactionManager;
    private final LogbookWorkflowService workflow;
    private final LoanRequestApplicationRepository applications;
    private final LogbookLoanOperationRepository operations;
    private final TenantService tenants;
    private final FineractGateway gateway;
    private final SubscriptionBillingService billing;
    private final ApplicationEventPublisher events;

    public void process(String tenantId,String applicationId,String operationId) {
        var transaction=new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        final int[] claimedAttempt = {0};
        try(var ignored=LoggingContext.withTenantAndApplication(tenantId,applicationId)) {
            Integer attempt=transaction.execute(status -> {
                workflow.lock(tenantId,applicationId);
                var op=operations.findForUpdateByTenantIdAndApplicationIdAndId(tenantId,applicationId,operationId).orElseThrow();
                if(op.getState()!=State.QUEUED)return null;
                op.setState(State.RUNNING);op.setStartedAt(Instant.now());op.setAttempts(op.getAttempts()+1);return op.getAttempts();
            });
            if(attempt==null)return;
            claimedAttempt[0] = attempt;
            transaction.executeWithoutResult(status -> dispatch(tenantId,applicationId,operationId,attempt));
        } catch(Exception failure) {
            log.error("Logbook loan operation failed operationId={}",operationId,failure);
            transaction.executeWithoutResult(status -> {
                var app=applications.findForLogbookUpdateByTenantIdAndId(tenantId,applicationId).orElseThrow();
                var op=operations.findForUpdateByTenantIdAndApplicationIdAndId(tenantId,applicationId,operationId).orElseThrow();
                if(op.getState()==State.SUCCEEDED || (claimedAttempt[0] > 0 && (op.getAttempts() != claimedAttempt[0] || op.getState()!=State.RUNNING)))return;
                op.setState(failure instanceof BadRequestException?State.BLOCKED:State.REVIEW_REQUIRED);
                op.setLastError(failure instanceof BadRequestException ? safe(failure.getMessage())
                    : "External loan operation failed. Inspect server logs, then retry to reconcile remote state.");
                op.setCompletedAt(Instant.now());
                if(app.getStatus()!=ApplicationStatus.LOAN_CLOSED && app.getStatus()!=ApplicationStatus.REJECTED)
                    workflow.transition(app,ApplicationStatus.LOAN_OPERATION_REVIEW_REQUIRED,op.getRequestedBy(),"Logbook operation requires review; see workflow operation status");
            });
        }
    }
    private void dispatch(String tenantId,String applicationId,String operationId,int attempt) {
        var app=workflow.lock(tenantId,applicationId);
        var op=operations.findForUpdateByTenantIdAndApplicationIdAndId(tenantId,applicationId,operationId).orElseThrow();
        if(op.getState()!=State.RUNNING || op.getAttempts()!=attempt)return;
        var tenant=tenants.getRequiredTenant(tenantId);
        var existing=gateway.findSecuredLoan(tenant,app);
        if(existing.isPresent() && !existing.get().active()
            && !"loanStatusType.submitted.and.pending.approval".equals(existing.get().statusCode())
            && !"loanStatusType.approved".equals(existing.get().statusCode()))
            throw new BadRequestException("Remote loan is not pending, approved or active; manual reconciliation required");
        if(op.getKind()==Kind.CREATE_LOAN) {
            if(existing.isPresent()) {
                app.setFineractLoanId(existing.get().id());
            } else {
                var product=workflow.lockProduct(app);workflow.require(app,Stage.INTERNAL_APPROVAL);
                app.setFineractLoanId(gateway.createSecuredPendingLoan(tenant,app,workflow.remoteProduct(product),product.getTransactionProcessingStrategyCode()));
            }
            if(existing.isPresent() && existing.get().active()) activated(app,op);
            else workflow.transition(app,ApplicationStatus.FINERACT_LOAN_CREATED_PENDING_SECURITY,op.getRequestedBy(),"Pending logbook loan created or reconciled");
        } else {
            if(existing.isEmpty() || !existing.get().id().equals(app.getFineractLoanId()))
                throw new BadRequestException("Pending loan does not match the application's remote reference");
            if(!existing.get().active()) {
                workflow.lockProduct(app);workflow.require(app,Stage.INTERNAL_APPROVAL);workflow.require(app,Stage.DISBURSEMENT);
                gateway.activateLoan(tenant,app);
            }
            activated(app,op);
        }
        op.setState(State.SUCCEEDED);op.setLastError(null);op.setCompletedAt(Instant.now());
    }
    private void activated(LoanRequestApplication app,LogbookLoanOperation op) {
        boolean changed=app.getStatus()!=ApplicationStatus.FINERACT_LOAN_ACTIVATED;
        workflow.transition(app,ApplicationStatus.FINERACT_LOAN_ACTIVATED,op.getRequestedBy(),"Logbook loan activated or remote activation reconciled");
        if(changed) {
            billing.evaluateNextCyclePricingMode(app.getTenantId());
            events.publishEvent(new LoanActivatedEvent(app.getTenantId(),app.getId(),app.getFineractLoanId(),op.getRequestedBy()));
        }
    }
    private String safe(String message) { return message==null?"Logbook prerequisites are not satisfied":message.substring(0,Math.min(message.length(),1000)); }
}
