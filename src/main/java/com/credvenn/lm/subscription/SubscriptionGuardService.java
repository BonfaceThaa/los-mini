package com.credvenn.lm.subscription;

import com.credvenn.lm.common.exception.ForbiddenOperationException;
import com.credvenn.lm.application.LoanRequestApplicationRepository;
import com.credvenn.lm.user.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriptionGuardService {

    private final TenantSubscriptionRepository tenantSubscriptionRepository;
    private final SubscriptionPlanService subscriptionPlanService;
    private final AppUserRepository appUserRepository;
    private final LoanRequestApplicationRepository applicationRepository;

    public SubscriptionGuardService(
            TenantSubscriptionRepository tenantSubscriptionRepository,
            SubscriptionPlanService subscriptionPlanService,
            AppUserRepository appUserRepository,
            LoanRequestApplicationRepository applicationRepository) {
        this.tenantSubscriptionRepository = tenantSubscriptionRepository;
        this.subscriptionPlanService = subscriptionPlanService;
        this.appUserRepository = appUserRepository;
        this.applicationRepository = applicationRepository;
    }

    @Transactional(readOnly = true)
    public void assertCanCreateUser(String tenantId) {
        TenantSubscription subscription = requireActiveSubscription(tenantId);
        SubscriptionPlan plan = subscriptionPlanService.getRequiredEntity(subscription.getSubscriptionPlanId());
        long activeUsers = appUserRepository.countByTenantIdAndActiveTrue(tenantId);
        if (activeUsers >= plan.getMaxUsers()) {
            throw new ForbiddenOperationException("Tenant has reached the active user limit for the current subscription plan");
        }
    }

    @Transactional(readOnly = true)
    public void assertCanCreateApplication(String tenantId) {
        TenantSubscription subscription = requireActiveSubscription(tenantId);
        SubscriptionPlan plan = subscriptionPlanService.getRequiredEntity(subscription.getSubscriptionPlanId());
        long createdApplications = applicationRepository.countByTenantIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                tenantId,
                subscription.getCurrentPeriodStart(),
                subscription.getCurrentPeriodEnd());
        if (createdApplications >= plan.getMonthlyApplicationLimit()) {
            throw new ForbiddenOperationException("Tenant has reached the application limit for the current subscription period");
        }
    }

    private TenantSubscription requireActiveSubscription(String tenantId) {
        return tenantSubscriptionRepository.findFirstByTenantIdAndStatusOrderByCreatedAtDesc(
                        tenantId,
                        TenantSubscriptionStatus.ACTIVE)
                .orElseThrow(() -> new ForbiddenOperationException("Tenant does not have an active subscription"));
    }
}
