package com.credvenn.lm.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.credvenn.lm.application.LoanRequestApplication;
import com.credvenn.lm.application.LoanRequestApplicationRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DepositPaymentServiceTest {

    @Test
    void validateDepositCallbackAcceptsMatchedApplicationWithoutDeposit() {
        TestContext context = new TestContext();
        TenantPaymentChannel channel = activeChannel("tenant-1", "600111");
        LoanRequestApplication application = application("app-1", "tenant-1", "0700000000");
        when(context.channelRepository.findByTenantIdAndShortCodeAndActiveTrue("tenant-1", "600111"))
                .thenReturn(Optional.of(channel));
        when(context.applicationRepository.findAllByTenantIdOrderByCreatedAtDesc("tenant-1"))
                .thenReturn(List.of(application));
        when(context.depositPaymentRepository.existsByTenantIdAndMatchedApplicationIdAndStatusIn(
                eq("tenant-1"),
                eq("app-1"),
                any()))
                .thenReturn(false);

        DepositPaymentService.C2bValidationDecision decision =
                context.service.validateDepositCallback("tenant-1", "600111", "0700000000");

        assertTrue(decision.accepted());
        assertEquals("0", decision.resultCode());
        assertEquals("Accepted", decision.resultDesc());
        assertEquals("app-1", decision.matchedApplicationId());
    }

    @Test
    void validateDepositCallbackRejectsWhenMatchedApplicationAlreadyHasDeposit() {
        TestContext context = new TestContext();
        TenantPaymentChannel channel = activeChannel("tenant-1", "600111");
        LoanRequestApplication application = application("app-1", "tenant-1", "254700000000");
        when(context.channelRepository.findByTenantIdAndShortCodeAndActiveTrue("tenant-1", "600111"))
                .thenReturn(Optional.of(channel));
        when(context.applicationRepository.findAllByTenantIdOrderByCreatedAtDesc("tenant-1"))
                .thenReturn(List.of(application));
        when(context.depositPaymentRepository.existsByTenantIdAndMatchedApplicationIdAndStatusIn(
                eq("tenant-1"),
                eq("app-1"),
                any()))
                .thenReturn(true);

        DepositPaymentService.C2bValidationDecision decision =
                context.service.validateDepositCallback("tenant-1", "600111", "254700000000");

        assertFalse(decision.accepted());
        assertEquals("C2B00011", decision.resultCode());
        assertEquals("Rejected", decision.resultDesc());
        assertEquals("Matched application already has a deposit", decision.reason());
    }

    @Test
    void validateDepositCallbackRejectsWhenNoApplicationMatches() {
        TestContext context = new TestContext();
        TenantPaymentChannel channel = activeChannel("tenant-1", "600111");
        when(context.channelRepository.findByTenantIdAndShortCodeAndActiveTrue("tenant-1", "600111"))
                .thenReturn(Optional.of(channel));
        when(context.applicationRepository.findAllByTenantIdOrderByCreatedAtDesc("tenant-1"))
                .thenReturn(List.of());

        DepositPaymentService.C2bValidationDecision decision =
                context.service.validateDepositCallback("tenant-1", "600111", "0700000000");

        assertFalse(decision.accepted());
        assertEquals("C2B00011", decision.resultCode());
        assertEquals("Rejected", decision.resultDesc());
        verify(context.depositPaymentRepository, org.mockito.Mockito.never())
                .existsByTenantIdAndMatchedApplicationIdAndStatusIn(any(), any(), any());
    }

    private static TenantPaymentChannel activeChannel(String tenantId, String shortCode) {
        TenantPaymentChannel channel = new TenantPaymentChannel();
        channel.setTenantId(tenantId);
        channel.setShortCode(shortCode);
        channel.setActive(true);
        channel.setChannelType(PaymentChannelType.MPESA_PAYBILL);
        return channel;
    }

    private static LoanRequestApplication application(String id, String tenantId, String phoneNumber) {
        LoanRequestApplication application = new LoanRequestApplication();
        setId(application, id);
        application.setTenantId(tenantId);
        application.setPhoneNumber(phoneNumber);
        return application;
    }

    private static void setId(LoanRequestApplication application, String id) {
        try {
            var field = LoanRequestApplication.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(application, id);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static final class TestContext {
        private final DepositPaymentRepository depositPaymentRepository = mock(DepositPaymentRepository.class);
        private final TenantPaymentChannelRepository channelRepository = mock(TenantPaymentChannelRepository.class);
        private final LoanRequestApplicationRepository applicationRepository = mock(LoanRequestApplicationRepository.class);
        private final DepositPaymentService service = new DepositPaymentService(
                depositPaymentRepository,
                channelRepository,
                applicationRepository);
    }
}
