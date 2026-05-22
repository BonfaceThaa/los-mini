package com.credvenn.lm.subscription;

import com.credvenn.lm.application.ApplicationStatus;
import com.credvenn.lm.application.ApplicationStatusHistoryRepository;
import com.credvenn.lm.common.exception.BadRequestException;
import com.credvenn.lm.common.exception.NotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriptionBillingService {

    private final TenantSubscriptionRepository tenantSubscriptionRepository;
    private final SubscriptionPlanService subscriptionPlanService;
    private final TenantSubscriptionLedgerRepository ledgerRepository;
    private final ApplicationStatusHistoryRepository statusHistoryRepository;

    public SubscriptionBillingService(
            TenantSubscriptionRepository tenantSubscriptionRepository,
            SubscriptionPlanService subscriptionPlanService,
            TenantSubscriptionLedgerRepository ledgerRepository,
            ApplicationStatusHistoryRepository statusHistoryRepository) {
        this.tenantSubscriptionRepository = tenantSubscriptionRepository;
        this.subscriptionPlanService = subscriptionPlanService;
        this.ledgerRepository = ledgerRepository;
        this.statusHistoryRepository = statusHistoryRepository;
    }

    @Transactional
    public SubscriptionDtos.TenantSubscriptionLedgerResponse topUpPrepaid(
            String tenantId,
            String subscriptionId,
            BigDecimal amount,
            String notes,
            String actor) {
        if (amount == null || amount.signum() <= 0) {
            throw new BadRequestException("Top-up amount must be greater than zero");
        }
        TenantSubscription subscription = requireSubscription(tenantId, subscriptionId);
        SubscriptionPlan plan = subscriptionPlanService.getRequiredEntity(subscription.getSubscriptionPlanId());
        TenantSubscriptionLedger entry = newEntry(
                subscription,
                plan,
                SubscriptionLedgerEntryType.CREDIT,
                SubscriptionChargeType.MANUAL_TOP_UP,
                amount,
                SubscriptionReferenceType.MANUAL,
                null,
                notes,
                actor);
        subscription.setPrepaidBalance(scale(subscription.getPrepaidBalance()).add(entry.getAmount()));
        subscription.setTotalCredited(scale(subscription.getTotalCredited()).add(entry.getAmount()));
        entry.setBalanceAfter(scale(subscription.getPrepaidBalance()));
        ledgerRepository.save(entry);
        return SubscriptionUsageService.toLedgerResponse(entry);
    }

    @Transactional
    public void chargeKycSuccess(String tenantId, String kycCheckId, String actor) {
        chargeSuccess(
                tenantId,
                kycCheckId,
                SubscriptionChargeType.KYC_SUCCESS,
                SubscriptionReferenceType.KYC_CHECK,
                subscriptionPlan -> subscriptionPlan.getKycSuccessCost(),
                "Successful KYC charge",
                actor);
    }

    @Transactional
    public void chargeStatementSuccess(String tenantId, String statementAnalysisId, String actor) {
        chargeSuccess(
                tenantId,
                statementAnalysisId,
                SubscriptionChargeType.STATEMENT_SUCCESS,
                SubscriptionReferenceType.STATEMENT_ANALYSIS,
                subscriptionPlan -> subscriptionPlan.getStatementSuccessCost(),
                "Successful statement analysis charge",
                actor);
    }

    @Transactional
    public void evaluateNextCyclePricingMode(String tenantId) {
        TenantSubscription subscription = getActiveSubscription(tenantId).orElse(null);
        if (subscription == null || subscription.getPricingMode() != SubscriptionPricingMode.FIXED_MONTHLY) {
            return;
        }
        SubscriptionPlan plan = subscriptionPlanService.getRequiredEntity(subscription.getSubscriptionPlanId());
        if (plan.getApprovedApplicationThreshold() <= 0 || subscription.getNextPricingMode() != null) {
            return;
        }
        long approvedCount = statusHistoryRepository.countDistinctApplicationIdByTenantIdAndToStatusAndCreatedAtBetween(
                tenantId,
                ApplicationStatus.FINERACT_LOAN_ACTIVATED.name(),
                subscription.getCreatedAt(),
                Instant.now());
        if (approvedCount >= plan.getApprovedApplicationThreshold()) {
            subscription.setNextPricingMode(SubscriptionPricingMode.INTEREST_SHARE);
            subscription.setNextPricingModeEffectiveAt(subscription.getCurrentPeriodEnd());
        }
    }

    @Transactional(readOnly = true)
    public List<SubscriptionDtos.TenantSubscriptionLedgerResponse> listLedger(String tenantId, String subscriptionId) {
        TenantSubscription subscription = requireSubscription(tenantId, subscriptionId);
        return ledgerRepository.findAllBySubscriptionIdOrderByIdDesc(subscription.getId()).stream()
                .map(SubscriptionUsageService::toLedgerResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SubscriptionDtos.TenantSubscriptionLedgerResponse> listCurrentLedger(String tenantId) {
        TenantSubscription subscription = requireActiveSubscription(tenantId);
        return listLedger(tenantId, subscription.getId());
    }

    @Transactional(readOnly = true)
    public TenantSubscription requireActiveSubscription(String tenantId) {
        return getActiveSubscription(tenantId)
                .orElseThrow(() -> new NotFoundException("Active tenant subscription not found"));
    }

    private void chargeSuccess(
            String tenantId,
            String referenceId,
            SubscriptionChargeType chargeType,
            SubscriptionReferenceType referenceType,
            java.util.function.Function<SubscriptionPlan, BigDecimal> amountExtractor,
            String notes,
            String actor) {
        TenantSubscription subscription = getActiveSubscription(tenantId).orElse(null);
        if (subscription == null) {
            return;
        }
        if (ledgerRepository.existsBySubscriptionIdAndEntryTypeAndChargeTypeAndReferenceId(
                subscription.getId(),
                SubscriptionLedgerEntryType.DEBIT,
                chargeType,
                referenceId)) {
            return;
        }
        SubscriptionPlan plan = subscriptionPlanService.getRequiredEntity(subscription.getSubscriptionPlanId());
        BigDecimal amount = scale(amountExtractor.apply(plan));
        TenantSubscriptionLedger entry = newEntry(
                subscription,
                plan,
                SubscriptionLedgerEntryType.DEBIT,
                chargeType,
                amount,
                referenceType,
                referenceId,
                notes,
                actor);
        subscription.setPrepaidBalance(scale(subscription.getPrepaidBalance()).subtract(amount));
        subscription.setTotalDebited(scale(subscription.getTotalDebited()).add(amount));
        entry.setBalanceAfter(scale(subscription.getPrepaidBalance()));
        ledgerRepository.save(entry);
    }

    private TenantSubscriptionLedger newEntry(
            TenantSubscription subscription,
            SubscriptionPlan plan,
            SubscriptionLedgerEntryType entryType,
            SubscriptionChargeType chargeType,
            BigDecimal amount,
            SubscriptionReferenceType referenceType,
            String referenceId,
            String notes,
            String actor) {
        TenantSubscriptionLedger entry = new TenantSubscriptionLedger();
        entry.setTenantId(subscription.getTenantId());
        entry.setSubscriptionId(subscription.getId());
        entry.setEntryType(entryType);
        entry.setChargeType(chargeType);
        entry.setAmount(scale(amount));
        entry.setCurrency(plan.getCurrency());
        entry.setReferenceType(referenceType);
        entry.setReferenceId(referenceId);
        entry.setBalanceBefore(scale(subscription.getPrepaidBalance()));
        entry.setBalanceAfter(scale(subscription.getPrepaidBalance()));
        entry.setNotes(notes);
        entry.setCreatedBy(actor);
        return entry;
    }

    private java.util.Optional<TenantSubscription> getActiveSubscription(String tenantId) {
        return tenantSubscriptionRepository.findFirstByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, TenantSubscriptionStatus.ACTIVE);
    }

    private TenantSubscription requireSubscription(String tenantId, String subscriptionId) {
        return tenantSubscriptionRepository.findById(subscriptionId)
                .filter(subscription -> subscription.getTenantId().equals(tenantId))
                .orElseThrow(() -> new NotFoundException("Tenant subscription not found"));
    }

    private BigDecimal scale(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }
}
