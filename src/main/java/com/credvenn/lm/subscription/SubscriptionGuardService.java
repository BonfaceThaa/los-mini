package com.credvenn.lm.subscription;

import com.credvenn.lm.common.exception.ForbiddenOperationException;
import com.credvenn.lm.user.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriptionGuardService {

    private final TenantSubscriptionRepository tenantSubscriptionRepository;
    private final SubscriptionPlanService subscriptionPlanService;
    private final AppUserRepository appUserRepository;

    public SubscriptionGuardService(
            TenantSubscriptionRepository tenantSubscriptionRepository,
            SubscriptionPlanService subscriptionPlanService,
            AppUserRepository appUserRepository) {
        this.tenantSubscriptionRepository = tenantSubscriptionRepository;
        this.subscriptionPlanService = subscriptionPlanService;
        this.appUserRepository = appUserRepository;
    }

    @Transactional(readOnly = true)
    public void assertCanCreateUser(String tenantId) {
        TenantSubscription subscription = tenantSubscriptionRepository.findFirstByTenantIdAndStatusOrderByCreatedAtDesc(
                        tenantId,
                        TenantSubscriptionStatus.ACTIVE)
                .orElseThrow(() -> new ForbiddenOperationException("Tenant does not have an active subscription"));
        SubscriptionPlan plan = subscriptionPlanService.getRequiredEntity(subscription.getSubscriptionPlanId());
        long activeUsers = appUserRepository.countByTenantIdAndActiveTrue(tenantId);
        if (activeUsers >= plan.getMaxUsers()) {
            throw new ForbiddenOperationException("Tenant has reached the active user limit for the current subscription plan");
        }
    }
}
