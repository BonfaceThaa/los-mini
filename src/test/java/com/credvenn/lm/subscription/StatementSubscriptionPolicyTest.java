package com.credvenn.lm.subscription;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.credvenn.lm.security.AuthenticatedUser;
import com.credvenn.lm.security.CurrentActorService;
import com.credvenn.lm.tenant.TenantRepository;
import java.util.List;
import java.util.Optional;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.util.ReflectionTestUtils;

class StatementSubscriptionPolicyTest {
    @Test
    void tenantOverrideCanBeSetAndClearedAndDoesNotChangePlan() {
        var repository = mock(TenantSubscriptionRepository.class);
        var plans = mock(SubscriptionPlanService.class);
        var tenants = mock(TenantRepository.class);
        var actors = mock(CurrentActorService.class);
        when(actors.requireCurrentUser()).thenReturn(new AuthenticatedUser("id", "platform", "admin", "admin@example.test", List.of(), List.of()));
        when(tenants.existsById("tenant-1")).thenReturn(true);
        var subscription = new TenantSubscription();
        ReflectionTestUtils.setField(subscription, "id", "sub-1");
        subscription.setTenantId("tenant-1");
        subscription.setSubscriptionPlanId("plan-1");
        var plan = new SubscriptionPlan();
        plan.setStatementAllowNegative(false);
        when(repository.findByIdAndTenantIdForUpdate("sub-1", "tenant-1")).thenReturn(Optional.of(subscription));
        when(plans.getRequiredEntity("plan-1")).thenReturn(plan);
        var service = new TenantSubscriptionService(repository, plans, tenants, mock(SubscriptionPolicy.class), actors, mock(SubscriptionBillingService.class));
        var result = service.updateStatementPolicy("tenant-1", "sub-1",
                new SubscriptionDtos.StatementSubscriptionPolicyRequest(true, StatementRepeatChargePolicy.CHARGE_ONCE_PER_DOCUMENT));
        assertTrue(result.effectiveStatementAllowNegative());
        assertEquals(StatementRepeatChargePolicy.CHARGE_ONCE_PER_DOCUMENT, result.effectiveStatementRepeatPolicy());
        assertFalse(plan.isStatementAllowNegative());
        result = service.updateStatementPolicy("tenant-1", "sub-1", new SubscriptionDtos.StatementSubscriptionPolicyRequest(null, null));
        assertNull(result.statementAllowNegativeOverride());
        assertFalse(result.effectiveStatementAllowNegative());
        assertEquals(StatementRepeatChargePolicy.CHARGE_EVERY_ANALYSIS, result.effectiveStatementRepeatPolicy());
    }

    @Test
    void administrativeEndpointsRequireManagementAuthorities() throws Exception {
        var subscription = TenantSubscriptionController.class.getMethod("updateStatementPolicy", String.class, String.class,
                SubscriptionDtos.StatementSubscriptionPolicyRequest.class).getAnnotation(PreAuthorize.class);
        var plan = SubscriptionPlanController.class.getMethod("updateStatementPolicy", String.class,
                SubscriptionDtos.StatementPlanPolicyRequest.class).getAnnotation(PreAuthorize.class);
        assertEquals("hasAuthority('TENANT_SUBSCRIPTION_MANAGE')", subscription.value());
        assertEquals("hasAuthority('SUBSCRIPTION_PLAN_MANAGE')", plan.value());
        var retry = TenantSubscriptionController.class.getMethod("retryStatementBilling", String.class, String.class,
                String.class).getAnnotation(PreAuthorize.class);
        assertEquals("hasAuthority('TENANT_SUBSCRIPTION_MANAGE')", retry.value());
    }

    @Test
    void changingPlanClearsOverridesOnThatSubscription() {
        var repository = mock(TenantSubscriptionRepository.class);
        var plans = mock(SubscriptionPlanService.class);
        var tenants = mock(TenantRepository.class);
        var actors = mock(CurrentActorService.class);
        when(actors.requireCurrentUser()).thenReturn(new AuthenticatedUser("id", "platform", "admin", "admin@example.test", List.of(), List.of()));
        when(tenants.existsById("tenant-1")).thenReturn(true);
        var subscription = new TenantSubscription();
        ReflectionTestUtils.setField(subscription, "id", "sub-1");
        subscription.setTenantId("tenant-1");
        subscription.setSubscriptionPlanId("old-plan");
        subscription.setStatementAllowNegativeOverride(true);
        subscription.setStatementRepeatPolicyOverride(StatementRepeatChargePolicy.CHARGE_ONCE_PER_DOCUMENT);
        var plan = new SubscriptionPlan();
        ReflectionTestUtils.setField(plan, "id", "new-plan");
        plan.setStatementAllowNegative(false);
        when(repository.findByIdAndTenantIdForUpdate("sub-1", "tenant-1")).thenReturn(Optional.of(subscription));
        when(plans.getRequiredEntity("new-plan")).thenReturn(plan);
        var service = new TenantSubscriptionService(repository, plans, tenants, mock(SubscriptionPolicy.class), actors, mock(SubscriptionBillingService.class));
        var response = service.update("tenant-1", "sub-1", new SubscriptionDtos.UpdateTenantSubscriptionRequest(
                "new-plan", Instant.now(), Instant.now().plusSeconds(3600), TenantSubscriptionStatus.ACTIVE, null));
        assertNull(response.statementAllowNegativeOverride());
        assertNull(response.statementRepeatPolicyOverride());
        assertFalse(response.effectiveStatementAllowNegative());
    }
}
