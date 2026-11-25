package com.credvenn.lm.subscription;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.credvenn.lm.application.ApplicationStatusHistoryRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SubscriptionBillingLockTest {
    @Test
    void topUpAndExistingUsageChargeUseLockedTenantSubscription() {
        var subscriptions = mock(TenantSubscriptionRepository.class);
        var plans = mock(SubscriptionPlanService.class);
        var ledger = mock(TenantSubscriptionLedgerRepository.class);
        var subscription = new TenantSubscription();
        ReflectionTestUtils.setField(subscription, "id", "sub-1");
        subscription.setTenantId("tenant-1");
        subscription.setSubscriptionPlanId("plan-1");
        subscription.setPrepaidBalance(new BigDecimal("10.00"));
        subscription.setTotalCredited(BigDecimal.ZERO);
        subscription.setTotalDebited(BigDecimal.ZERO);
        var plan = new SubscriptionPlan();
        plan.setCurrency("KES");
        plan.setKycSuccessCost(new BigDecimal("3.00"));
        when(plans.getRequiredEntity("plan-1")).thenReturn(plan);
        when(subscriptions.findByIdAndTenantIdForUpdate("sub-1", "tenant-1")).thenReturn(Optional.of(subscription));
        when(subscriptions.findTopByTenantIdAndStatusOrderByCreatedAtDesc("tenant-1", TenantSubscriptionStatus.ACTIVE)).thenReturn(Optional.of(subscription));
        when(ledger.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var service = new SubscriptionBillingService(subscriptions, plans, ledger, mock(ApplicationStatusHistoryRepository.class));
        service.topUpPrepaid("tenant-1", "sub-1", new BigDecimal("5.00"), null, "admin");
        service.chargeKycSuccess("tenant-1", "kyc-1", "system");
        assertEquals(new BigDecimal("12.00"), subscription.getPrepaidBalance());
        verify(subscriptions).findByIdAndTenantIdForUpdate("sub-1", "tenant-1");
        verify(subscriptions).findTopByTenantIdAndStatusOrderByCreatedAtDesc("tenant-1", TenantSubscriptionStatus.ACTIVE);
        verify(ledger, times(2)).save(any());
    }
}
