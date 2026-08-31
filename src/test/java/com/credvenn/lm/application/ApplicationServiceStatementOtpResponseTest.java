package com.credvenn.lm.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.credvenn.lm.client.ClientRecordService;
import com.credvenn.lm.fineract.FineractGateway;
import com.credvenn.lm.inventory.InventoryDeviceAssignmentRepository;
import com.credvenn.lm.kyc.KycCheckRepository;
import com.credvenn.lm.loanproduct.LoanProductMappingRepository;
import com.credvenn.lm.payment.DepositPaymentRepository;
import com.credvenn.lm.statement.StatementAnalysisRepository;
import com.credvenn.lm.statement.StatementReviewRepository;
import com.credvenn.lm.subscription.SubscriptionBillingService;
import com.credvenn.lm.subscription.SubscriptionGuardService;
import com.credvenn.lm.tenant.TenantService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class ApplicationServiceStatementOtpResponseTest {

    @Test
    void getIncludesRawStatementOtps() {
        LoanRequestApplicationRepository applicationRepository = mock(LoanRequestApplicationRepository.class);
        ApplicationStatusHistoryRepository statusHistoryRepository = mock(ApplicationStatusHistoryRepository.class);
        ApplicationStatementOtpService applicationStatementOtpService = mock(ApplicationStatementOtpService.class);

        ApplicationService service = new ApplicationService(
                applicationRepository,
                statusHistoryRepository,
                mock(KycCheckRepository.class),
                mock(StatementReviewRepository.class),
                mock(StatementAnalysisRepository.class),
                mock(TenantService.class),
                mock(FineractGateway.class),
                mock(LoanProductMappingRepository.class),
                mock(DepositPaymentRepository.class),
                mock(ClientRecordService.class),
                mock(InventoryDeviceAssignmentRepository.class),
                applicationStatementOtpService,
                mock(ApplicationEventPublisher.class),
                mock(SubscriptionGuardService.class),
                mock(SubscriptionBillingService.class));

        LoanRequestApplication application = new LoanRequestApplication();
        setId(application, "app-otp-1");
        application.setTenantId("tenant-1");
        application.setApplicantFirstName("Jane");
        application.setApplicantLastName("Doe");
        application.setPhoneNumber("254700000000");
        application.setNationalId("32162157");
        application.setApplicantIdType(ApplicantIdType.NATIONAL_ID);
        application.setStatementOtp("432198");
        application.setRequestedAmount(new BigDecimal("1000.00"));
        application.setRequestedTermMonths(12);
        application.setStatus(ApplicationStatus.PENDING_KYC);

        ApplicationStatementOtpService.StatementOtpView otp = new ApplicationStatementOtpService.StatementOtpView(
                "otp-1",
                "123456",
                ApplicationStatementOtpStatus.PENDING,
                ApplicationStatementOtpSource.MANUAL_ADD,
                Instant.parse("2026-08-29T10:14:00Z"),
                Instant.parse("2026-08-29T10:15:30Z"),
                null,
                null);

        when(applicationRepository.findByIdAndTenantId("app-otp-1", "tenant-1")).thenReturn(Optional.of(application));
        when(statusHistoryRepository.findAllByApplicationIdOrderByIdAsc("app-otp-1")).thenReturn(List.of());
        when(applicationStatementOtpService.listViews("tenant-1", "app-otp-1")).thenReturn(List.of(otp));

        ApplicationDtos.LoanRequestApplicationResponse response = service.get("tenant-1", "app-otp-1");

        assertEquals("432198", response.statementOtp());
        assertEquals(1, response.statementOtps().size());
        assertEquals("otp-1", response.statementOtps().get(0).id());
        assertEquals("123456", response.statementOtps().get(0).otp());
        assertEquals(ApplicationStatementOtpStatus.PENDING, response.statementOtps().get(0).status());
        assertEquals(ApplicationStatementOtpSource.MANUAL_ADD, response.statementOtps().get(0).source());
        assertEquals(Instant.parse("2026-08-29T10:15:30Z"), response.statementOtps().get(0).testedAt());
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
}
