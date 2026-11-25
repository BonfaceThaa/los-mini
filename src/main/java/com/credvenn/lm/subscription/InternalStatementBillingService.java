package com.credvenn.lm.subscription;

import com.credvenn.lm.common.exception.NotFoundException;
import com.credvenn.lm.common.exception.BadRequestException;
import com.credvenn.lm.document.ApplicationDocumentRepository;
import com.credvenn.lm.statement.StatementAnalysis;
import com.credvenn.lm.statement.StatementAnalysisRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InternalStatementBillingService {
    private final StatementAnalysisRepository analyses;
    private final ApplicationDocumentRepository documents;
    private final TenantSubscriptionRepository subscriptions;
    private final TenantSubscriptionLedgerRepository ledger;
    private final SubscriptionPlanService plans;
    private final EntityManager entityManager;

    public InternalStatementBillingService(StatementAnalysisRepository analyses, ApplicationDocumentRepository documents,
            TenantSubscriptionRepository subscriptions, TenantSubscriptionLedgerRepository ledger, SubscriptionPlanService plans,
            EntityManager entityManager) {
        this.analyses = analyses;
        this.documents = documents;
        this.subscriptions = subscriptions;
        this.ledger = ledger;
        this.plans = plans;
        this.entityManager = entityManager;
    }

    /** Acquire document, subscription, then analysis locks in that order. */
    @Transactional(propagation = Propagation.MANDATORY)
    public LockedBilling lockForAnalysis(String tenantId, String analysisId) {
        StatementAnalysis snapshot = analyses.findByIdAndTenantId(analysisId, tenantId)
                .orElseThrow(() -> new NotFoundException("Statement analysis not found"));
        documents.findByIdAndTenantIdForUpdate(snapshot.getSourceDocumentId(), tenantId)
                .orElseThrow(() -> new NotFoundException("Statement document not found"));
        TenantSubscription subscription = snapshot.getBillingSubscriptionId() == null ? null : subscriptions
                .findByIdAndTenantIdForUpdate(snapshot.getBillingSubscriptionId(), tenantId)
                .orElseThrow(() -> new NotFoundException("Captured subscription not found"));
        StatementAnalysis locked = analyses.findForSubmissionUpdate(analysisId, tenantId)
                .orElseThrow(() -> new NotFoundException("Statement analysis not found"));
        entityManager.refresh(locked, LockModeType.PESSIMISTIC_WRITE);
        return new LockedBilling(locked, subscription);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void charge(LockedBilling work, String actor) {
        StatementAnalysis analysis = work.analysis();
        if (analysis.getBillingStatus() == StatementBillingStatus.CHARGED || analysis.getBillingStatus() == StatementBillingStatus.WAIVED) return;
        if (work.subscription() == null || analysis.getBillingPlanId() == null || analysis.getBillingCapturedAt() == null
                || analysis.getBillingAmount() == null || analysis.getBillingAmount().signum() < 0 || analysis.getBillingCurrency() == null
                || analysis.getBillingAllowNegative() == null || analysis.getBillingRepeatPolicy() == null) {
            analysis.setBillingStatus(StatementBillingStatus.MISSING_TERMS);
            return;
        }
        var previous = ledger.findDocumentChargesForUpdate(analysis.getTenantId(), analysis.getSourceDocumentId());
        if (previous.stream().anyMatch(entry -> analysis.getId().equals(entry.getReferenceId()))) {
            analysis.setBillingStatus(StatementBillingStatus.CHARGED);
            return;
        }
        if (analysis.getBillingRepeatPolicy() == StatementRepeatChargePolicy.CHARGE_ONCE_PER_DOCUMENT && !previous.isEmpty()) {
            analysis.setBillingStatus(StatementBillingStatus.WAIVED);
            return;
        }
        TenantSubscription subscription = work.subscription();
        var currentPlan = plans.getRequiredEntity(subscription.getSubscriptionPlanId());
        if (!analysis.getBillingCurrency().equals(currentPlan.getCurrency())) {
            analysis.setBillingStatus(StatementBillingStatus.CURRENCY_MISMATCH);
            return;
        }
        BigDecimal amount = analysis.getBillingAmount().setScale(2, RoundingMode.UNNECESSARY);
        BigDecimal balance = value(subscription.getPrepaidBalance());
        if (!analysis.getBillingAllowNegative() && balance.compareTo(amount) < 0) {
            analysis.setBillingStatus(StatementBillingStatus.PENDING_FUNDS);
            return;
        }
        TenantSubscriptionLedger entry = new TenantSubscriptionLedger();
        entry.setTenantId(analysis.getTenantId());
        entry.setSubscriptionId(subscription.getId());
        entry.setEntryType(SubscriptionLedgerEntryType.DEBIT);
        entry.setChargeType(SubscriptionChargeType.STATEMENT_SUCCESS);
        entry.setReferenceType(SubscriptionReferenceType.STATEMENT_ANALYSIS);
        entry.setReferenceId(analysis.getId());
        entry.setAmount(amount);
        entry.setCurrency(analysis.getBillingCurrency());
        entry.setBalanceBefore(balance);
        entry.setBalanceAfter(balance.subtract(amount));
        entry.setNotes("Completed internal statement analysis charge");
        entry.setCreatedBy(actor);
        subscription.setPrepaidBalance(entry.getBalanceAfter());
        subscription.setTotalDebited(value(subscription.getTotalDebited()).add(amount));
        ledger.save(entry);
        analysis.setBillingStatus(StatementBillingStatus.CHARGED);
    }

    @Transactional
    public StatementBillingStatus retry(String tenantId, String subscriptionId, String analysisId, String actor) {
        LockedBilling work = lockForAnalysis(tenantId, analysisId);
        StatementAnalysis analysis = work.analysis();
        if (!"INTERNAL_MPESA".equals(analysis.getProvider()) || !subscriptionId.equals(analysis.getBillingSubscriptionId())) {
            throw new NotFoundException("Statement analysis for subscription not found");
        }
        if (analysis.getBillingStatus() == null || analysis.getBillingStatus() == StatementBillingStatus.MISSING_TERMS
                || analysis.getBillingStatus() == StatementBillingStatus.WAIVER_RESERVED) {
            throw new BadRequestException("Statement analysis has no retryable billing charge");
        }
        charge(work, actor);
        analyses.save(analysis);
        return analysis.getBillingStatus();
    }

    private BigDecimal value(BigDecimal amount) { return amount == null ? BigDecimal.ZERO.setScale(2) : amount.setScale(2, RoundingMode.UNNECESSARY); }

    public record LockedBilling(StatementAnalysis analysis, TenantSubscription subscription) { }
}
