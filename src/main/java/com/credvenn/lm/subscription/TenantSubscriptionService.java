package com.credvenn.lm.subscription;

import com.credvenn.lm.common.exception.NotFoundException;
import com.credvenn.lm.security.CurrentActorService;
import com.credvenn.lm.tenant.TenantRepository;
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

    public TenantSubscriptionService(
            TenantSubscriptionRepository tenantSubscriptionRepository,
            SubscriptionPlanService subscriptionPlanService,
            TenantRepository tenantRepository,
            SubscriptionPolicy subscriptionPolicy,
            CurrentActorService currentActorService) {
        this.tenantSubscriptionRepository = tenantSubscriptionRepository;
        this.subscriptionPlanService = subscriptionPlanService;
        this.tenantRepository = tenantRepository;
        this.subscriptionPolicy = subscriptionPolicy;
        this.currentActorService = currentActorService;
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
        subscription.setOperationalNotes(request.operationalNotes());
        subscription.setCreatedBy(actor);
        subscription.setUpdatedBy(actor);
        return toResponse(tenantSubscriptionRepository.save(subscription), plan);
    }

    @Transactional
    public SubscriptionDtos.TenantSubscriptionResponse update(String tenantId, String subscriptionId, SubscriptionDtos.UpdateTenantSubscriptionRequest request) {
        assertTenantExists(tenantId);
        SubscriptionPlan plan = subscriptionPlanService.getRequiredEntity(request.subscriptionPlanId());
        subscriptionPolicy.validateBillingPeriod(request.currentPeriodStart(), request.currentPeriodEnd());
        String actor = currentActorService.requireCurrentUser().username();
        TenantSubscription subscription = getRequiredEntity(tenantId, subscriptionId);
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
                subscription.getCurrentPeriodStart(),
                subscription.getCurrentPeriodEnd(),
                subscription.getSwitchedToInterestShareAt(),
                subscription.getOperationalNotes(),
                subscription.getCreatedBy(),
                subscription.getUpdatedBy(),
                subscription.getCreatedAt(),
                subscription.getUpdatedAt());
    }
}
