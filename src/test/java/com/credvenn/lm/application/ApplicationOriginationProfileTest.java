package com.credvenn.lm.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.credvenn.lm.applicationvariable.ApplicationVariableService;
import com.credvenn.lm.client.ClientRecordService;
import com.credvenn.lm.common.exception.*;
import com.credvenn.lm.fineract.FineractGateway;
import com.credvenn.lm.inventory.InventoryDeviceAssignmentRepository;
import com.credvenn.lm.kyc.KycCheckRepository;
import com.credvenn.lm.loanproduct.LoanProductMappingRepository;
import com.credvenn.lm.origination.*;
import com.credvenn.lm.payment.DepositPaymentRepository;
import com.credvenn.lm.statement.*;
import com.credvenn.lm.subscription.*;
import com.credvenn.lm.tenant.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ApplicationOriginationProfileTest {
    @Mock LoanRequestApplicationRepository applications;
    @Mock ApplicationStatusHistoryRepository history;
    @Mock KycCheckRepository kyc;
    @Mock StatementReviewRepository reviews;
    @Mock StatementAnalysisRepository analyses;
    @Mock TenantService tenantService;
    @Mock FineractGateway fineract;
    @Mock LoanProductMappingRepository products;
    @Mock DepositPaymentRepository deposits;
    @Mock ClientRecordService clients;
    @Mock InventoryDeviceAssignmentRepository assignments;
    @Mock ApplicationStatementOtpService otps;
    @Mock ApplicationEventPublisher events;
    @Mock SubscriptionGuardService subscriptions;
    @Mock SubscriptionBillingService billing;
    @Mock ApplicationVariableService variables;
    @Mock TenantRepository tenants;
    @Mock OriginationProfileRepository profiles;
    @Mock jakarta.persistence.EntityManager entityManager;
    @InjectMocks ApplicationService service;
    private Tenant tenant;
    private OriginationProfile profile;
    private final Map<String, LoanRequestApplication> stored = new HashMap<>();

    @BeforeEach void setup() {
        ReflectionTestUtils.setField(service, "originationProfileResolver",
                new OriginationProfileResolver(tenants, profiles, new OriginationProfileValidator(), new ObjectMapper()));
        tenant = new Tenant(); tenant.setDefaultOriginationProfileId("profile-default");
        profile = new OriginationProfile(); profile.setId("profile-explicit"); profile.setTenantId("tenant-1");
        profile.setCode("CUSTOM_PHONE"); profile.setActive(true);
        profile.setRequirementsJson("""
            {"OFFER_SELECTION":["KYC_APPROVED","CLIENT_PROVISIONED","STATEMENT_ACCEPTED"],
             "INTERNAL_APPROVAL":["CONSENT_CAPTURED","CLIENT_PROVISIONED","DEVICE_ASSIGNED","FINANCING_CALCULATED","ACTIVE_PRODUCT_SELECTED"],
             "DISBURSEMENT":["PENDING_LOAN_CREATED","DEVICE_ASSIGNED","DEPOSIT_MATCHED"]}
            """);
    }

    private void tenant() { when(tenants.findForOriginationUpdate("tenant-1")).thenReturn(Optional.of(tenant)); }
    private void persistence() {
        when(applications.save(any())).thenAnswer(call -> {
            LoanRequestApplication application = call.getArgument(0); application.assignId();
            assertNotNull(application.getOriginationProfileId());
            stored.put(application.getId(), application); return application;
        });
        when(applications.findByIdAndTenantId(anyString(), eq("tenant-1")))
                .thenAnswer(call -> Optional.ofNullable(stored.get(call.getArgument(0))));
    }
    private ApplicationDtos.CreateLoanRequestApplicationRequest request(String code) {
        return new ApplicationDtos.CreateLoanRequestApplicationRequest("Mary", null, "Test", "0700000000", "12345678",
                ApplicantIdType.NATIONAL_ID, null, null, null, new BigDecimal("20000"), null, List.of(), code);
    }
    private void noSideEffects() {
        verifyNoInteractions(applications, history, variables, otps, clients, events, fineract, billing);
    }

    @ParameterizedTest @NullSource @ValueSource(strings = {"CUSTOM_PHONE", " custom_phone "})
    void resolvesAndSavesProfileBeforePublishingKycEvent(String code) {
        tenant(); persistence();
        if (code == null) {
            profile.setId("profile-default");
            when(profiles.findForApplicationByTenantIdAndId("tenant-1", "profile-default")).thenReturn(Optional.of(profile));
        } else when(profiles.findForApplicationByTenantIdAndCodeIgnoreCase("tenant-1", "CUSTOM_PHONE")).thenReturn(Optional.of(profile));
        var response = service.create("tenant-1", "officer", request(code));
        verify(variables).prepare("tenant-1", profile.getId(), List.of());
        assertEquals(profile.getId(), response.originationProfileId());
        assertEquals(ApplicationStatus.PENDING_KYC, response.status());
        assertEquals(profile.getId(), stored.get(response.id()).getOriginationProfileId());
        var order = inOrder(tenants, profiles, applications, events);
        order.verify(tenants).findForOriginationUpdate("tenant-1");
        if (code == null) order.verify(profiles).findForApplicationByTenantIdAndId("tenant-1", "profile-default");
        else order.verify(profiles).findForApplicationByTenantIdAndCodeIgnoreCase("tenant-1", "CUSTOM_PHONE");
        order.verify(applications).save(any());
        order.verify(events).publishEvent(any(ApplicationCreatedEvent.class));
        verifyNoInteractions(fineract);
        tenant.setDefaultOriginationProfileId("different-default");
        assertEquals(profile.getId(), service.get("tenant-1", response.id()).originationProfileId());
        if (code != null) verify(profiles, never()).findForApplicationByTenantIdAndId(anyString(), anyString());
    }

    @Test void missingDefaultRejectsBeforeApplicationOrQuestionnaireWrites() {
        tenant(); tenant.setDefaultOriginationProfileId(null);
        assertTrue(assertThrows(BadRequestException.class, () -> service.create("tenant-1", "officer", request(null)))
                .getMessage().contains("originationProfileCode"));
        verifyNoInteractions(profiles); noSideEffects();
    }

    @Test void explicitProfileWorksWithoutADefault() {
        tenant(); persistence(); tenant.setDefaultOriginationProfileId(null);
        when(profiles.findForApplicationByTenantIdAndCodeIgnoreCase("tenant-1", "CUSTOM_PHONE")).thenReturn(Optional.of(profile));
        assertEquals("profile-explicit", service.create("tenant-1", "officer", request("CUSTOM_PHONE")).originationProfileId());
    }

    @Test void otherTenantOrUnknownCodeDoesNotFallBackToDefault() {
        tenant();
        when(profiles.findForApplicationByTenantIdAndCodeIgnoreCase("tenant-1", "OTHER_TENANT_PROFILE")).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.create("tenant-1", "officer", request("OTHER_TENANT_PROFILE")));
        verify(profiles, never()).findForApplicationByTenantIdAndId(anyString(), anyString()); noSideEffects();
    }

    @ParameterizedTest @NullSource @ValueSource(strings = {"CUSTOM_PHONE"})
    void inactiveProfileIsRejectedForBothResolutionPaths(String code) {
        tenant(); profile.setActive(false);
        if (code == null) when(profiles.findForApplicationByTenantIdAndId("tenant-1", "profile-default")).thenReturn(Optional.of(profile));
        else when(profiles.findForApplicationByTenantIdAndCodeIgnoreCase("tenant-1", code)).thenReturn(Optional.of(profile));
        assertEquals("Origination profile is inactive", assertThrows(BadRequestException.class,
                () -> service.create("tenant-1", "officer", request(code))).getMessage());
        noSideEffects();
    }

    @ParameterizedTest @ValueSource(strings = {"", "   ", "PHONE-INVALID"})
    void invalidExplicitCodeDoesNotUseDefault(String code) {
        tenant();
        assertThrows(BadRequestException.class, () -> service.create("tenant-1", "officer", request(code)));
        verifyNoInteractions(profiles); noSideEffects();
    }

    @Test void missingOrCrossTenantDefaultIsRejected() {
        tenant();
        when(profiles.findForApplicationByTenantIdAndId("tenant-1", "profile-default")).thenReturn(Optional.empty());
        assertThrows(ConflictException.class, () -> service.create("tenant-1", "officer", request(null)));
        noSideEffects();
    }

    @Test void unsupportedActiveConfigurationCannotEnterPhoneWorkflow() {
        tenant(); profile.setRequirementsJson("{}");
        when(profiles.findForApplicationByTenantIdAndCodeIgnoreCase("tenant-1", "CUSTOM_PHONE")).thenReturn(Optional.of(profile));
        assertThrows(BadRequestException.class, () -> service.create("tenant-1", "officer", request("CUSTOM_PHONE")));
        noSideEffects();
    }

    @Test void tenantlessCallerIsRejected() {
        assertThrows(ForbiddenOperationException.class, () -> service.create(null, "platform-admin", request("CUSTOM_PHONE")));
        verifyNoInteractions(tenants, profiles); noSideEffects();
    }

    @Test void olderJsonPayloadDeserializesWithNoProfileCode() throws Exception {
        var request = new ObjectMapper().readValue("""
                {"applicantFirstName":"Mary","applicantLastName":"Test","phoneNumber":"0700000000",
                 "nationalId":"12345678","applicantIdType":"NATIONAL_ID","requestedAmount":20000}
                """, ApplicationDtos.CreateLoanRequestApplicationRequest.class);
        assertNull(request.originationProfileCode());
    }
}
