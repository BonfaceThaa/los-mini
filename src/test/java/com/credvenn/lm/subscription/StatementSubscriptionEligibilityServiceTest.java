package com.credvenn.lm.subscription;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.credvenn.lm.common.exception.ForbiddenOperationException;
import com.credvenn.lm.statement.StatementAnalysis;
import com.credvenn.lm.statement.StatementAnalysisRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class StatementSubscriptionEligibilityServiceTest {
    private final TenantSubscriptionRepository subscriptions = mock(TenantSubscriptionRepository.class);
    private final SubscriptionPlanService plans = mock(SubscriptionPlanService.class);
    private final StatementAnalysisRepository analyses = mock(StatementAnalysisRepository.class);
    private final com.credvenn.lm.document.ApplicationDocumentRepository documents = mock(com.credvenn.lm.document.ApplicationDocumentRepository.class);
    private final TenantSubscriptionLedgerRepository ledger = mock(TenantSubscriptionLedgerRepository.class);
    private final StatementSubscriptionEligibilityService service = new StatementSubscriptionEligibilityService(subscriptions, plans, analyses, documents, ledger);

    StatementSubscriptionEligibilityServiceTest() {
        when(documents.findByIdAndTenantIdForUpdate(any(), any())).thenReturn(Optional.of(new com.credvenn.lm.document.ApplicationDocument()));
    }

    @Test
    void deniesMissingOrOutOfPeriodSubscription() {
        assertThrows(ForbiddenOperationException.class, () -> service.captureForSubmission("tenant-1", new StatementAnalysis()));
        var sub = subscription("tenant-1", "sub-1");
        sub.setCurrentPeriodEnd(Instant.now().minusSeconds(1));
        when(subscriptions.findTopByTenantIdAndStatusOrderByCreatedAtDesc("tenant-1", TenantSubscriptionStatus.ACTIVE)).thenReturn(Optional.of(sub));
        assertThrows(ForbiddenOperationException.class, () -> service.captureForSubmission("tenant-1", new StatementAnalysis()));
        sub.setCurrentPeriodStart(Instant.now().plusSeconds(30));
        sub.setCurrentPeriodEnd(Instant.now().plusSeconds(60));
        assertThrows(ForbiddenOperationException.class, () -> service.captureForSubmission("tenant-1", new StatementAnalysis()));
        verifyNoInteractions(plans, analyses);
    }

    @Test
    void snapshotsTermsAndHonorsTenantOverridesEvenIfBalanceIsNegative() {
        var sub = subscription("tenant-1", "sub-1");
        sub.setPrepaidBalance(new BigDecimal("-100.00"));
        sub.setStatementAllowNegativeOverride(true);
        sub.setStatementRepeatPolicyOverride(StatementRepeatChargePolicy.CHARGE_ONCE_PER_DOCUMENT);
        var plan = plan(false);
        stub(sub, plan);
        var analysis = new StatementAnalysis();
        service.captureForSubmission("tenant-1", analysis);
        assertEquals("sub-1", analysis.getBillingSubscriptionId());
        assertEquals("plan-1", analysis.getBillingPlanId());
        assertEquals(new BigDecimal("25.00"), analysis.getBillingAmount());
        assertEquals("KES", analysis.getBillingCurrency());
        assertTrue(analysis.getBillingAllowNegative());
        assertEquals(StatementRepeatChargePolicy.CHARGE_ONCE_PER_DOCUMENT, analysis.getBillingRepeatPolicy());
        assertNotNull(analysis.getBillingCapturedAt());
        plan.setStatementSuccessCost(new BigDecimal("90.00"));
        plan.setCurrency("USD");
        sub.setStatus(TenantSubscriptionStatus.CANCELLED);
        assertEquals(new BigDecimal("25.00"), analysis.getBillingAmount());
        assertEquals("KES", analysis.getBillingCurrency());
        verifyNoInteractions(analyses);
    }

    @Test
    void blocksInsufficientFundsIncludingOtherPendingAnalyses() {
        var sub = subscription("tenant-1", "sub-1");
        sub.setPrepaidBalance(new BigDecimal("40.00"));
        var plan = plan(false);
        stub(sub, plan);
        var pending = new StatementAnalysis();
        pending.setBillingAmount(new BigDecimal("20.00"));
        pending.setBillingCurrency("KES");
        when(analyses.findOutstandingForUpdate(eq("tenant-1"), eq("sub-1"), anySet())).thenReturn(List.of(pending));
        var result = new StatementAnalysis();
        assertThrows(ForbiddenOperationException.class, () -> service.captureForSubmission("tenant-1", result));
        assertNull(result.getBillingSubscriptionId());
        sub.setPrepaidBalance(new BigDecimal("45.00"));
        service.captureForSubmission("tenant-1", result);
        assertFalse(result.getBillingAllowNegative());
        assertEquals(StatementRepeatChargePolicy.CHARGE_EVERY_ANALYSIS, result.getBillingRepeatPolicy());
    }

    @Test
    void policiesInheritPlanAndCanBeOverriddenIndependentlyPerSubscription() {
        var plan = plan(false);
        plan.setStatementRepeatPolicy(StatementRepeatChargePolicy.CHARGE_ONCE_PER_DOCUMENT);
        var first = subscription("tenant-1", "sub-1");
        var second = subscription("tenant-2", "sub-2");
        first.setStatementAllowNegativeOverride(true);
        second.setStatementRepeatPolicyOverride(StatementRepeatChargePolicy.CHARGE_EVERY_ANALYSIS);
        assertTrue(StatementSubscriptionEligibilityService.effectiveAllowNegative(first, plan));
        assertFalse(StatementSubscriptionEligibilityService.effectiveAllowNegative(second, plan));
        assertEquals(StatementRepeatChargePolicy.CHARGE_ONCE_PER_DOCUMENT, StatementSubscriptionEligibilityService.effectiveRepeatPolicy(first, plan));
        assertEquals(StatementRepeatChargePolicy.CHARGE_EVERY_ANALYSIS, StatementSubscriptionEligibilityService.effectiveRepeatPolicy(second, plan));
        first.setStatementAllowNegativeOverride(null);
        assertFalse(StatementSubscriptionEligibilityService.effectiveAllowNegative(first, plan));
    }

    @Test
    void previouslyChargedDocumentCanBeQueuedWithoutFundsForOncePerDocument() {
        var sub = subscription("tenant-1", "sub-1");
        sub.setPrepaidBalance(BigDecimal.ZERO);
        sub.setStatementRepeatPolicyOverride(StatementRepeatChargePolicy.CHARGE_ONCE_PER_DOCUMENT);
        var plan = plan(false);
        stub(sub, plan);
        var previous = new TenantSubscriptionLedger();
        when(ledger.findDocumentChargesForUpdate("tenant-1", "doc-1")).thenReturn(List.of(previous));
        var analysis = new StatementAnalysis();
        analysis.setSourceDocumentId("doc-1");
        service.captureForSubmission("tenant-1", analysis);
        assertEquals(StatementBillingStatus.WAIVER_RESERVED, analysis.getBillingStatus());
        assertEquals(new BigDecimal("25.00"), analysis.getBillingAmount());
        verifyNoInteractions(analyses);
    }

    private void stub(TenantSubscription sub, SubscriptionPlan plan) {
        when(subscriptions.findTopByTenantIdAndStatusOrderByCreatedAtDesc(sub.getTenantId(), TenantSubscriptionStatus.ACTIVE)).thenReturn(Optional.of(sub));
        when(plans.getRequiredEntity("plan-1")).thenReturn(plan);
    }

    private TenantSubscription subscription(String tenantId, String id) {
        var sub = new TenantSubscription();
        ReflectionTestUtils.setField(sub, "id", id);
        sub.setTenantId(tenantId);
        sub.setSubscriptionPlanId("plan-1");
        sub.setStatus(TenantSubscriptionStatus.ACTIVE);
        sub.setCurrentPeriodStart(Instant.now().minus(1, ChronoUnit.DAYS));
        sub.setCurrentPeriodEnd(Instant.now().plus(1, ChronoUnit.DAYS));
        sub.setPrepaidBalance(BigDecimal.ZERO);
        return sub;
    }

    private SubscriptionPlan plan(boolean negative) {
        var plan = new SubscriptionPlan();
        ReflectionTestUtils.setField(plan, "id", "plan-1");
        plan.setStatementSuccessCost(new BigDecimal("25.00"));
        plan.setCurrency("KES");
        plan.setStatementAllowNegative(negative);
        return plan;
    }
}
