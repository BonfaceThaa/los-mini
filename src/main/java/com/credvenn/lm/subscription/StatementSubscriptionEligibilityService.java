package com.credvenn.lm.subscription;

import com.credvenn.lm.common.exception.ForbiddenOperationException;
import com.credvenn.lm.statement.StatementAnalysis;
import com.credvenn.lm.statement.StatementAnalysisRepository;
import com.credvenn.lm.statement.StatementAnalysisStatus;
import com.credvenn.lm.document.ApplicationDocumentRepository;
import com.credvenn.lm.common.exception.NotFoundException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StatementSubscriptionEligibilityService {
    private final TenantSubscriptionRepository subscriptions;
    private final SubscriptionPlanService plans;
    private final StatementAnalysisRepository analyses;
    private final ApplicationDocumentRepository documents;
    private final TenantSubscriptionLedgerRepository ledger;

    public StatementSubscriptionEligibilityService(TenantSubscriptionRepository subscriptions,
            SubscriptionPlanService plans, StatementAnalysisRepository analyses,
            ApplicationDocumentRepository documents, TenantSubscriptionLedgerRepository ledger) {
        this.subscriptions = subscriptions;
        this.plans = plans;
        this.analyses = analyses;
        this.documents = documents;
        this.ledger = ledger;
    }

    /** The caller must save the analysis in this same transaction while the subscription lock is held. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void captureForSubmission(String tenantId, StatementAnalysis analysis) {
        documents.findByIdAndTenantIdForUpdate(analysis.getSourceDocumentId(), tenantId)
                .orElseThrow(() -> new NotFoundException("Statement document not found"));
        TenantSubscription subscription = subscriptions.findTopByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, TenantSubscriptionStatus.ACTIVE)
                .orElseThrow(() -> new ForbiddenOperationException("Tenant does not have an active subscription for the current period"));
        Instant now = Instant.now();
        if (now.isBefore(subscription.getCurrentPeriodStart()) || !now.isBefore(subscription.getCurrentPeriodEnd())) {
            throw new ForbiddenOperationException("Tenant does not have an active subscription for the current period");
        }
        SubscriptionPlan plan = plans.getRequiredEntity(subscription.getSubscriptionPlanId());
        BigDecimal amount = plan.getStatementSuccessCost();
        boolean allowNegative = effectiveAllowNegative(subscription, plan);
        var repeatPolicy = effectiveRepeatPolicy(subscription, plan);
        boolean waived = repeatPolicy == StatementRepeatChargePolicy.CHARGE_ONCE_PER_DOCUMENT
                && !ledger.findDocumentChargesForUpdate(tenantId, analysis.getSourceDocumentId()).isEmpty();
        if (!allowNegative && !waived) {
            var pending = analyses.findOutstandingForUpdate(tenantId, subscription.getId(),
                    Set.of(StatementAnalysisStatus.PENDING, StatementAnalysisStatus.IN_PROGRESS));
            if (pending.stream().anyMatch(a -> !plan.getCurrency().equals(a.getBillingCurrency()))) {
                throw new ForbiddenOperationException("Pending statement analyses use a different subscription currency");
            }
            BigDecimal outstanding = pending.stream().map(StatementAnalysis::getBillingAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal balance = subscription.getPrepaidBalance() == null ? BigDecimal.ZERO : subscription.getPrepaidBalance();
            if (balance.subtract(outstanding).compareTo(amount) < 0) {
                throw new ForbiddenOperationException("Insufficient subscription balance for statement analysis");
            }
        }
        analysis.setBillingSubscriptionId(subscription.getId());
        analysis.setBillingPlanId(plan.getId());
        analysis.setBillingAmount(amount);
        analysis.setBillingCurrency(plan.getCurrency());
        analysis.setBillingAllowNegative(allowNegative);
        analysis.setBillingRepeatPolicy(repeatPolicy);
        if (waived) analysis.setBillingStatus(StatementBillingStatus.WAIVER_RESERVED);
        analysis.setBillingCapturedAt(now);
    }

    public static boolean effectiveAllowNegative(TenantSubscription subscription, SubscriptionPlan plan) {
        return subscription.getStatementAllowNegativeOverride() == null
                ? plan.isStatementAllowNegative() : subscription.getStatementAllowNegativeOverride();
    }

    public static StatementRepeatChargePolicy effectiveRepeatPolicy(TenantSubscription subscription, SubscriptionPlan plan) {
        return subscription.getStatementRepeatPolicyOverride() == null
                ? plan.getStatementRepeatPolicy() : subscription.getStatementRepeatPolicyOverride();
    }
}
