package com.credvenn.lm.subscription;

import com.credvenn.lm.common.exception.NotFoundException;
import com.credvenn.lm.security.CurrentActorService;
import com.credvenn.lm.tenant.TenantRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantSubscriptionService {

    private final TenantSubscriptionRepository tenantSubscriptionRepository;
    private final SubscriptionPlanService subscriptionPlanService;
    private final TenantRepository tenantRepository;
    private final SubscriptionPolicy subscriptionPolicy;
    private final CurrentActorService currentActorService;
    private final SubscriptionBillingService subscriptionBillingService;

    public TenantSubscriptionService(
            TenantSubscriptionRepository tenantSubscriptionRepository,
            SubscriptionPlanService subscriptionPlanService,
            TenantRepository tenantRepository,
            SubscriptionPolicy subscriptionPolicy,
            CurrentActorService currentActorService,
            SubscriptionBillingService subscriptionBillingService) {
        this.tenantSubscriptionRepository = tenantSubscriptionRepository;
        this.subscriptionPlanService = subscriptionPlanService;
        this.tenantRepository = tenantRepository;
        this.subscriptionPolicy = subscriptionPolicy;
        this.currentActorService = currentActorService;
        this.subscriptionBillingService = subscriptionBillingService;
    }

    @Transactional
    public SubscriptionDtos.TenantSubscriptionResponse assign(String tenantId, SubscriptionDtos.AssignTenantSubscriptionRequest request) {
        assertTenantExists(tenantId);
        SubscriptionPlan plan = subscriptionPlanService.getRequiredEntity(request.subscriptionPlanId());
        subscriptionPolicy.validateBillingPeriod(request.currentPeriodStart(), request.currentPeriodEnd());
        String actor = currentActorService.requireCurrentUser().username();

        tenantSubscriptionRepository.findFirstByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, TenantSubscriptionStatus.ACTIVE)
                .ifPresent(existing -> {
                    existing.setStatus(TenantSubscriptionStatus.CANCELLED);
                    existing.setUpdatedBy(actor);
                });

        TenantSubscription subscription = new TenantSubscription();
        subscription.setTenantId(tenantId);
        subscription.setSubscriptionPlanId(plan.getId());
        subscription.setStatus(TenantSubscriptionStatus.ACTIVE);
        subscription.setPricingMode(SubscriptionPricingMode.FIXED_MONTHLY);
        subscription.setCurrentPeriodStart(request.currentPeriodStart());
        subscription.setCurrentPeriodEnd(request.currentPeriodEnd());
        subscription.setPrepaidBalance(BigDecimal.ZERO);
        subscription.setTotalCredited(BigDecimal.ZERO);
        subscription.setTotalDebited(BigDecimal.ZERO);
        subscription.setOperationalNotes(request.operationalNotes());
        subscription.setCreatedBy(actor);
        subscription.setUpdatedBy(actor);
        subscription = tenantSubscriptionRepository.save(subscription);
        if (request.initialPrepaidAmount().signum() > 0) {
            subscriptionBillingService.topUpPrepaid(
                    tenantId,
                    subscription.getId(),
                    request.initialPrepaidAmount(),
                    "Initial prepaid funding",
                    actor);
        }
        return toResponse(subscription, plan);
    }

    @Transactional
    public SubscriptionDtos.TenantSubscriptionResponse update(String tenantId, String subscriptionId, SubscriptionDtos.UpdateTenantSubscriptionRequest request) {
        assertTenantExists(tenantId);
        SubscriptionPlan plan = subscriptionPlanService.getRequiredEntity(request.subscriptionPlanId());
        subscriptionPolicy.validateBillingPeriod(request.currentPeriodStart(), request.currentPeriodEnd());
        String actor = currentActorService.requireCurrentUser().username();
        TenantSubscription subscription = getRequiredEntity(tenantId, subscriptionId);
        SubscriptionPricingMode scheduledPricingMode = subscription.getNextPricingMode();
        java.time.Instant scheduledEffectiveAt = subscription.getNextPricingModeEffectiveAt();
        if (request.status() == TenantSubscriptionStatus.ACTIVE) {
            tenantSubscriptionRepository.findFirstByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, TenantSubscriptionStatus.ACTIVE)
                    .filter(existing -> !existing.getId().equals(subscriptionId))
                    .ifPresent(existing -> {
                        existing.setStatus(TenantSubscriptionStatus.CANCELLED);
                        existing.setUpdatedBy(actor);
                    });
        }
        subscription.setSubscriptionPlanId(plan.getId());
        subscription.setCurrentPeriodStart(request.currentPeriodStart());
        subscription.setCurrentPeriodEnd(request.currentPeriodEnd());
        subscription.setStatus(request.status());
        subscription.setOperationalNotes(request.operationalNotes());
        subscription.setUpdatedBy(actor);
        if (scheduledPricingMode != null
                && scheduledEffectiveAt != null
                && !request.currentPeriodStart().isBefore(scheduledEffectiveAt)) {
            subscription.setPricingMode(scheduledPricingMode);
            subscription.setSwitchedToInterestShareAt(request.currentPeriodStart());
            subscription.setNextPricingMode(null);
            subscription.setNextPricingModeEffectiveAt(null);
        } else {
            subscription.setNextPricingMode(scheduledPricingMode);
            subscription.setNextPricingModeEffectiveAt(scheduledEffectiveAt);
        }
        return toResponse(subscription, plan);
    }

    @Transactional
    public SubscriptionDtos.TenantSubscriptionResponse updateNotes(String tenantId, String subscriptionId, SubscriptionDtos.UpdateSubscriptionNotesRequest request) {
        assertTenantExists(tenantId);
        TenantSubscription subscription = getRequiredEntity(tenantId, subscriptionId);
        subscription.setOperationalNotes(request.operationalNotes());
        subscription.setUpdatedBy(currentActorService.requireCurrentUser().username());
        return toResponse(subscription, subscriptionPlanService.getRequiredEntity(subscription.getSubscriptionPlanId()));
    }

    @Transactional(readOnly = true)
    public SubscriptionDtos.TenantSubscriptionResponse getCurrent(String tenantId) {
        assertTenantExists(tenantId);
        TenantSubscription subscription = tenantSubscriptionRepository.findFirstByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, TenantSubscriptionStatus.ACTIVE)
                .orElseThrow(() -> new NotFoundException("Active tenant subscription not found"));
        return toResponse(subscription, subscriptionPlanService.getRequiredEntity(subscription.getSubscriptionPlanId()));
    }

    @Transactional(readOnly = true)
    public List<SubscriptionDtos.TenantSubscriptionResponse> history(String tenantId) {
        assertTenantExists(tenantId);
        return tenantSubscriptionRepository.findAllByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(subscription -> toResponse(subscription, subscriptionPlanService.getRequiredEntity(subscription.getSubscriptionPlanId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public TenantSubscription getRequiredEntity(String tenantId, String subscriptionId) {
        assertTenantExists(tenantId);
        return tenantSubscriptionRepository.findById(subscriptionId)
                .filter(subscription -> subscription.getTenantId().equals(tenantId))
                .orElseThrow(() -> new NotFoundException("Tenant subscription not found"));
    }

    private void assertTenantExists(String tenantId) {
        if (!tenantRepository.existsById(tenantId)) {
            throw new NotFoundException("Tenant not found");
        }
    }

    static SubscriptionDtos.TenantSubscriptionResponse toResponse(TenantSubscription subscription, SubscriptionPlan plan) {
        return new SubscriptionDtos.TenantSubscriptionResponse(
                subscription.getId(),
                subscription.getTenantId(),
                subscription.getSubscriptionPlanId(),
                plan.getPlanCode(),
                plan.getName(),
                subscription.getStatus(),
                subscription.getPricingMode(),
                subscription.getNextPricingMode(),
                subscription.getCurrentPeriodStart(),
                subscription.getCurrentPeriodEnd(),
                subscription.getSwitchedToInterestShareAt(),
                subscription.getNextPricingModeEffectiveAt(),
                subscription.getPrepaidBalance(),
                subscription.getTotalCredited(),
                subscription.getTotalDebited(),
                subscription.getOperationalNotes(),
                subscription.getCreatedBy(),
                subscription.getUpdatedBy(),
                subscription.getCreatedAt(),
                subscription.getUpdatedAt());
    }
}
