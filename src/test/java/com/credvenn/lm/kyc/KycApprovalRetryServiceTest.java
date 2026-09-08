package com.credvenn.lm.kyc;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.resilience.annotation.EnableResilientMethods;

class KycApprovalRetryServiceTest {

    @Test
    void retriesLockFailuresWithFourTotalAttemptsWithoutRepeatingProviderWork() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(RetryTestConfiguration.class)) {
            KycApprovalService approvalService = context.getBean(KycApprovalService.class);
            KycApprovalRetryService retryService = context.getBean(KycApprovalRetryService.class);
            KycApprovedEvent event = new KycApprovedEvent(
                    "tenant-1", "app-1", "officer", "kyc-1", "KYC passed");
            doThrow(new CannotAcquireLockException("deadlock-1"))
                    .doThrow(new CannotAcquireLockException("deadlock-2"))
                    .doThrow(new CannotAcquireLockException("deadlock-3"))
                    .doNothing()
                    .when(approvalService)
                    .approveAndRequestClientProvisioning(
                            "tenant-1", "app-1", "officer", "kyc-1", "KYC passed");

            retryService.finalizeApprovedKyc(event);

            verify(approvalService, times(4)).approveAndRequestClientProvisioning(
                    "tenant-1", "app-1", "officer", "kyc-1", "KYC passed");
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableResilientMethods
    static class RetryTestConfiguration {

        @Bean
        KycApprovalService approvalService() {
            return mock(KycApprovalService.class);
        }

        @Bean
        KycApprovalRetryService retryService(KycApprovalService approvalService) {
            return new KycApprovalRetryService(approvalService);
        }
    }
}