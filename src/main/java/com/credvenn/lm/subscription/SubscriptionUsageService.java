package com.credvenn.lm.subscription;

import com.credvenn.lm.user.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriptionUsageService {

    private final TenantSubscriptionService tenantSubscriptionService;
    private final SubscriptionPlanService subscriptionPlanService;
    private final AppUserRepository appUserRepository;

    public SubscriptionUsageService(
            TenantSubscriptionService tenantSubscriptionService,
            SubscriptionPlanService subscriptionPlanService,
            AppUserRepository appUserRepository) {
        this.tenantSubscriptionService = tenantSubscriptionService;
        this.subscriptionPlanService = subscriptionPlanService;
        this.appUserRepository = appUserRepository;
    }

    @Transactional(readOnly = true)
    public SubscriptionDtos.SubscriptionUsageResponse getCurrentUsage(String tenantId) {
        TenantSubscription subscription = tenantSubscriptionService.getRequiredEntity(
                tenantId,
                tenantSubscriptionService.getCurrent(tenantId).id());
        SubscriptionPlan plan = subscriptionPlanService.getRequiredEntity(subscription.getSubscriptionPlanId());
        long activeUsers = appUserRepository.countByTenantIdAndActiveTrue(tenantId);
        return new SubscriptionDtos.SubscriptionUsageResponse(
                tenantId,
                subscription.getId(),
                plan.getId(),
                plan.getPlanCode(),
                activeUsers,
                plan.getMaxUsers(),
                0L,
                plan.getMaxBranches(),
                0L,
                plan.getMonthlyApplicationLimit(),
                subscription.getCurrentPeriodStart(),
                subscription.getCurrentPeriodEnd());
    }
}
