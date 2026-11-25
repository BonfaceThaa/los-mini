package com.credvenn.lm.subscription;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.credvenn.lm.document.ApplicationDocumentRepository;
import com.credvenn.lm.statement.StatementAnalysis;
import com.credvenn.lm.statement.StatementAnalysisRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class InternalStatementBillingServiceTest {
    private final StatementAnalysisRepository analyses = mock(StatementAnalysisRepository.class);
    private final ApplicationDocumentRepository documents = mock(ApplicationDocumentRepository.class);
    private final TenantSubscriptionRepository subscriptions = mock(TenantSubscriptionRepository.class);
    private final TenantSubscriptionLedgerRepository ledger = mock(TenantSubscriptionLedgerRepository.class);
    private final SubscriptionPlanService plans = mock(SubscriptionPlanService.class);
    private final InternalStatementBillingService service = new InternalStatementBillingService(analyses, documents, subscriptions, ledger, plans, mock(EntityManager.class));

    @Test
    void capturedTermsAreDebitedOnceEvenAfterSubscriptionCancelled() {
        var analysis = analysis();
        var subscription = subscription("100.00");
        subscription.setStatus(TenantSubscriptionStatus.CANCELLED);
        stubCurrency("KES");
        var work = new InternalStatementBillingService.LockedBilling(analysis, subscription);
        service.charge(work, "officer");
        assertEquals(StatementBillingStatus.CHARGED, analysis.getBillingStatus());
        assertEquals(new BigDecimal("75.00"), subscription.getPrepaidBalance());
        assertEquals(new BigDecimal("25.00"), subscription.getTotalDebited());
        var entry = ArgumentCaptor.forClass(TenantSubscriptionLedger.class);
        verify(ledger).save(entry.capture());
        assertEquals("analysis-1", entry.getValue().getReferenceId());
        assertEquals("sub-1", entry.getValue().getSubscriptionId());
        assertEquals("KES", entry.getValue().getCurrency());
        assertEquals(new BigDecimal("25.00"), entry.getValue().getAmount());
        service.charge(work, "officer");
        verify(ledger, times(1)).save(any());
    }

    @Test
    void oncePerDocumentWaivesPriorDebitEvenFromAnotherSubscription() {
        var analysis = analysis();
        analysis.setBillingRepeatPolicy(StatementRepeatChargePolicy.CHARGE_ONCE_PER_DOCUMENT);
        var previous = new TenantSubscriptionLedger();
        previous.setReferenceId("older-analysis");
        previous.setSubscriptionId("old-subscription");
        when(ledger.findDocumentChargesForUpdate("tenant-1", "doc-1")).thenReturn(List.of(previous));
        var subscription = subscription("100.00");
        service.charge(new InternalStatementBillingService.LockedBilling(analysis, subscription), "officer");
        assertEquals(StatementBillingStatus.WAIVED, analysis.getBillingStatus());
        assertEquals(new BigDecimal("100.00"), subscription.getPrepaidBalance());
        verify(ledger, never()).save(any());
    }

    @Test
    void persistedLedgerDebitMakesRetryIdempotentAfterInterruptedCompletion() {
        var analysis = analysis();
        var previous = new TenantSubscriptionLedger();
        previous.setReferenceId("analysis-1");
        when(ledger.findDocumentChargesForUpdate("tenant-1", "doc-1")).thenReturn(List.of(previous));
        var subscription = subscription("100.00");
        service.charge(new InternalStatementBillingService.LockedBilling(analysis, subscription), "officer");
        assertEquals(StatementBillingStatus.CHARGED, analysis.getBillingStatus());
        assertEquals(new BigDecimal("100.00"), subscription.getPrepaidBalance());
        verify(ledger, never()).save(any());
    }

    @Test
    void blockedBalanceAndCurrencyArePendingRatherThanIncorrectlyDebited() {
        var analysis = analysis();
        var subscription = subscription("5.00");
        stubCurrency("KES");
        var work = new InternalStatementBillingService.LockedBilling(analysis, subscription);
        service.charge(work, "officer");
        assertEquals(StatementBillingStatus.PENDING_FUNDS, analysis.getBillingStatus());
        assertEquals(new BigDecimal("5.00"), subscription.getPrepaidBalance());
        subscription.setPrepaidBalance(new BigDecimal("50.00"));
        service.charge(work, "officer");
        assertEquals(StatementBillingStatus.CHARGED, analysis.getBillingStatus());
        assertEquals(new BigDecimal("25.00"), subscription.getPrepaidBalance());
        var mismatch = analysis();
        stubCurrency("USD");
        service.charge(new InternalStatementBillingService.LockedBilling(mismatch, subscription), "officer");
        assertEquals(StatementBillingStatus.CURRENCY_MISMATCH, mismatch.getBillingStatus());
        verify(ledger, times(1)).save(any());
    }

    @Test
    void negativeBalanceCanBeAllowedAndMissingSnapshotIsNotSilentlyCharged() {
        var analysis = analysis();
        analysis.setBillingAllowNegative(true);
        var subscription = subscription("5.00");
        stubCurrency("KES");
        service.charge(new InternalStatementBillingService.LockedBilling(analysis, subscription), "officer");
        assertEquals(new BigDecimal("-20.00"), subscription.getPrepaidBalance());
        var legacy = analysis();
        legacy.setBillingAmount(null);
        service.charge(new InternalStatementBillingService.LockedBilling(legacy, subscription), "officer");
        assertEquals(StatementBillingStatus.MISSING_TERMS, legacy.getBillingStatus());
        verify(ledger, times(1)).save(any());
    }

    @Test
    void administratorCanRetryPendingChargeOnlyForCapturedTenantSubscription() {
        var analysis = analysis();
        analysis.setProvider("INTERNAL_MPESA");
        analysis.setBillingStatus(StatementBillingStatus.PENDING_FUNDS);
        var subscription = subscription("50.00");
        when(analyses.findByIdAndTenantId("analysis-1", "tenant-1")).thenReturn(Optional.of(analysis));
        when(analyses.findForSubmissionUpdate("analysis-1", "tenant-1")).thenReturn(Optional.of(analysis));
        when(documents.findByIdAndTenantIdForUpdate("doc-1", "tenant-1")).thenReturn(Optional.of(new com.credvenn.lm.document.ApplicationDocument()));
        when(subscriptions.findByIdAndTenantIdForUpdate("sub-1", "tenant-1")).thenReturn(Optional.of(subscription));
        stubCurrency("KES");
        assertThrows(com.credvenn.lm.common.exception.NotFoundException.class,
                () -> service.retry("tenant-1", "other-subscription", "analysis-1", "admin"));
        assertEquals(StatementBillingStatus.CHARGED, service.retry("tenant-1", "sub-1", "analysis-1", "admin"));
        assertEquals(StatementBillingStatus.CHARGED, service.retry("tenant-1", "sub-1", "analysis-1", "admin"));
        verify(ledger, times(1)).save(any());
        assertEquals(new BigDecimal("25.00"), subscription.getPrepaidBalance());
    }

    private void stubCurrency(String currency) {
        var plan = new SubscriptionPlan();
        plan.setCurrency(currency);
        when(plans.getRequiredEntity("current-plan")).thenReturn(plan);
    }

    private StatementAnalysis analysis() {
        var a = new StatementAnalysis();
        ReflectionTestUtils.setField(a, "id", "analysis-1");
        a.setTenantId("tenant-1");
        a.setSourceDocumentId("doc-1");
        a.setBillingSubscriptionId("sub-1");
        a.setBillingPlanId("original-plan");
        a.setBillingCapturedAt(java.time.Instant.now());
        a.setBillingAmount(new BigDecimal("25.00"));
        a.setBillingCurrency("KES");
        a.setBillingAllowNegative(false);
        a.setBillingRepeatPolicy(StatementRepeatChargePolicy.CHARGE_EVERY_ANALYSIS);
        return a;
    }

    private TenantSubscription subscription(String balance) {
        var s = new TenantSubscription();
        ReflectionTestUtils.setField(s, "id", "sub-1");
        s.setTenantId("tenant-1");
        s.setSubscriptionPlanId("current-plan");
        s.setPrepaidBalance(new BigDecimal(balance));
        s.setTotalDebited(BigDecimal.ZERO);
        return s;
    }
}
