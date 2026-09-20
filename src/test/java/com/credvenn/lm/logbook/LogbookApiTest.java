package com.credvenn.lm.logbook;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.credvenn.lm.application.*;
import com.credvenn.lm.common.exception.GlobalExceptionHandler;
import com.credvenn.lm.document.*;
import com.credvenn.lm.origination.*;
import com.credvenn.lm.security.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class LogbookApiTest {
    private AnnotationConfigApplicationContext context;
    private MockMvc mvc;
    private ObjectMapper json;
    private LogbookVehicle vehicle;
    private LoanRequestApplication application;
    private OriginationProfile profile;
    private final List<LogbookValuation> values = new ArrayList<>();
    private final List<LogbookVerification> checks = new ArrayList<>();
    private final List<LogbookAudit> history = new ArrayList<>();
    private static final String ROOT = "/api/v1/applications/app/logbook";
    private static final String VEHICLE = """
        {"registrationNumber":"KDA 123A","chassisNumber":"CHASSIS123","engineNumber":"ENGINE123",
         "make":"Toyota","model":"Fielder","manufactureYear":2016,"registeredOwner":"Example Borrower","logbookNumber":"LB123"}
        """;
    private static final String[] PERMISSIONS = {"LOGBOOK_VIEW","LOGBOOK_VEHICLE_MANAGE","LOGBOOK_VALUATION_SUBMIT","LOGBOOK_VALUATION_REVIEW","LOGBOOK_VERIFICATION_MANAGE"};
    private LocalDate today = LocalDate.now(ZoneId.of("Africa/Nairobi"));

    @BeforeEach void setup() {
        context = new AnnotationConfigApplicationContext(Config.class); json = context.getBean(ObjectMapper.class);
        mvc = MockMvcBuilders.standaloneSetup(new LogbookController(context.getBean(LogbookService.class)))
                .setControllerAdvice(new LogbookExceptionHandler(), new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(json)).build();
        application = new LoanRequestApplication(); ReflectionTestUtils.setField(application,"id","app");
        application.setTenantId("a"); application.setOriginationProfileId("profile"); application.setStatus(ApplicationStatus.SUBMITTED);
        application.setRequestedAmount(new BigDecimal("500000"));
        var apps = context.getBean(LoanRequestApplicationRepository.class);
        when(apps.findByIdAndTenantId("app","a")).thenReturn(Optional.of(application));
        when(apps.findForLogbookUpdateByTenantIdAndId("a","app")).thenReturn(Optional.of(application));
        profile = new OriginationProfile(); profile.setId("profile"); profile.setTenantId("a");
        profile.setRequirementsJson("{\"OFFER_SELECTION\":[\"VALUATION_APPROVED\"]}");
        profile.setConfigurationJson("{\"valuationBasis\":\"FORCED_SALE_VALUE\",\"maxLtvRatio\":0.6,\"valuationValidityDays\":30}");
        when(context.getBean(OriginationProfileRepository.class).findByTenantIdAndId("a","profile")).thenReturn(Optional.of(profile));
        var docs = context.getBean(ApplicationDocumentRepository.class);
        var report = new ApplicationDocument(); report.setTenantId("a"); report.setApplicationId("app");
        when(docs.findByIdAndTenantId("doc","a")).thenReturn(Optional.of(report));
        var vehicles = context.getBean(LogbookVehicleRepository.class);
        when(vehicles.findByTenantIdAndApplicationId("a","app")).thenAnswer(c -> Optional.ofNullable(vehicle));
        when(vehicles.saveAndFlush(any())).thenAnswer(c -> { var v = (LogbookVehicle)c.getArgument(0); if(vehicle != null) v.setVersion(v.getVersion()+1); vehicle=v; return v; });
        doAnswer(c -> { vehicle.setVersion(vehicle.getVersion()+1); return null; }).when(vehicles).flush();
        var valuations = context.getBean(LogbookValuationRepository.class);
        when(valuations.save(any())).thenAnswer(c -> { var v = (LogbookValuation)c.getArgument(0); if(!values.contains(v)) values.addFirst(v); return v; });
        when(valuations.findAllByTenantIdAndApplicationIdOrderByRecordedVersionDesc("a","app")).thenAnswer(c -> List.copyOf(values));
        when(valuations.findByTenantIdAndApplicationIdAndId(eq("a"),eq("app"),anyString()))
                .thenAnswer(c -> values.stream().filter(v -> v.getId().equals(c.getArgument(2))).findFirst());
        var verifications = context.getBean(LogbookVerificationRepository.class);
        when(verifications.save(any())).thenAnswer(c -> { var v = (LogbookVerification)c.getArgument(0); checks.addFirst(v); return v; });
        when(verifications.findAllByTenantIdAndApplicationIdOrderByRecordedVersionDesc("a","app")).thenAnswer(c -> List.copyOf(checks));
        when(context.getBean(LogbookAuditRepository.class).save(any())).thenAnswer(c -> { var a=(LogbookAudit)c.getArgument(0);history.add(a);return a; });
        login("valuer", "a", PERMISSIONS);
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); context.close(); }
    private void login(String user, String tenant, String... permissions) {
        var authorities=Arrays.stream(permissions).map(SimpleGrantedAuthority::new).toList();
        var actor=new AuthenticatedUser(user,tenant,user,"test@example.test",List.of(),authorities);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor,null,authorities));
    }
    private void capture() throws Exception { mvc.perform(put(ROOT+"/vehicle").contentType(MediaType.APPLICATION_JSON).content(VEHICLE)).andExpect(status().isOk()); }
    private String valuation(LocalDate date) { return """
        {"expectedVersion":%d,"marketValue":1000000,"forcedSaleValue":800000,"valuedOn":"%s","valuerOrganization":"Independent Valuers","reportDocumentId":"doc"}
        """.formatted(vehicle.getVersion(),date); }
    private void submit() throws Exception { mvc.perform(post(ROOT+"/valuations").contentType(MediaType.APPLICATION_JSON).content(valuation(today))).andExpect(status().isOk()); }
    private org.springframework.test.web.servlet.ResultActions review(String decision) throws Exception {
        return mvc.perform(post(ROOT+"/valuations/"+values.getFirst().getId()+"/review").contentType(MediaType.APPLICATION_JSON)
            .content("{\"expectedVersion\":"+vehicle.getVersion()+",\"decision\":\""+decision+"\",\"reason\":\"Report reviewed\"}"));
    }
    private String check(String dates) { return "{\"expectedVersion\":"+vehicle.getVersion()+",\"status\":\"VERIFIED\",\"referenceNumber\":\"REF1\",\"documentId\":\"doc\",\"notes\":\"Evidence reviewed\""+dates+"}"; }

    @Test void capturesValuationAndIndependentApprovalWithExactLtvBoundary() throws Exception {
        capture(); assertEquals("KDA123A", vehicle.getRegistrationNumber()); submit();
        review("APPROVED").andExpect(status().isForbidden());
        login("reviewer","a",PERMISSIONS);
        review("APPROVED").andExpect(status().isOk())
            .andExpect(jsonPath("$.readiness.maximumSecuredAmount").value(480000))
            .andExpect(jsonPath("$.readiness.requestedAmountWithinLimit").value(false));
        application.setRequestedAmount(new BigDecimal("480000"));
        mvc.perform(get(ROOT)).andExpect(jsonPath("$.readiness.requestedAmountWithinLimit").value(true));
        assertEquals(3, history.size()); assertTrue(history.getLast().getBeforeJson().contains("PENDING"));
        assertTrue(history.getLast().getAfterJson().contains("APPROVED"));
    }
    @Test void replacementVehicleInvalidatesApprovedValuationAndOwnership() throws Exception {
        capture(); submit(); login("reviewer","a",PERMISSIONS); review("APPROVED").andExpect(status().isOk());
        mvc.perform(post(ROOT+"/verifications/OWNERSHIP").contentType(MediaType.APPLICATION_JSON).content(check("")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.readiness.ownershipVerified").value(true));
        mvc.perform(put(ROOT+"/vehicle").contentType(MediaType.APPLICATION_JSON).content(VEHICLE.replace("{","{\"expectedVersion\":"+vehicle.getVersion()+",")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.readiness.ownershipVerified").value(false))
            .andExpect(jsonPath("$.readiness.valuationApproved").value(false));
        assertEquals(1,vehicle.getEvidenceRevision()); assertEquals(1,values.size()); assertEquals(1,checks.size());
    }
    @Test void expiredValuationCannotBeApprovedAndNewPendingReportSupersedesOld() throws Exception {
        capture(); mvc.perform(post(ROOT+"/valuations").contentType(MediaType.APPLICATION_JSON).content(valuation(today.minusDays(30)))).andExpect(status().isOk());
        login("reviewer","a",PERMISSIONS); review("APPROVED").andExpect(status().isBadRequest());
        login("valuer","a",PERMISSIONS); submit(); login("reviewer","a",PERMISSIONS); review("APPROVED").andExpect(status().isOk());
        login("valuer","a",PERMISSIONS); submit();
        mvc.perform(get(ROOT)).andExpect(jsonPath("$.readiness.valuationApproved").value(false));
    }
    @Test void readinessRechecksValuationExpiryAtReadTime() throws Exception {
        capture(); submit(); login("reviewer","a",PERMISSIONS); review("APPROVED").andExpect(status().isOk());
        values.getFirst().setValuedOn(today.minusDays(30));
        mvc.perform(get(ROOT)).andExpect(jsonPath("$.readiness.valuationApproved").value(false)).andExpect(jsonPath("$.readiness.maximumSecuredAmount").isEmpty());
    }
    @Test void verifiesCoverageDatesAndRevocationWithoutDiscardingHistory() throws Exception {
        capture();
        mvc.perform(post(ROOT+"/verifications/INSURANCE").contentType(MediaType.APPLICATION_JSON).content(check(""))).andExpect(status().isBadRequest());
        var dates=",\"validFrom\":\""+today+"\",\"validUntil\":\""+today+"\"";
        mvc.perform(post(ROOT+"/verifications/INSURANCE").contentType(MediaType.APPLICATION_JSON).content(check(dates)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.readiness.insuranceValid").value(true));
        mvc.perform(post(ROOT+"/verifications/INSURANCE").contentType(MediaType.APPLICATION_JSON).content(check(dates).replace("VERIFIED","REVOKED")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.readiness.insuranceValid").value(false));
        assertEquals(2, checks.size());
    }
    @Test void rejectsStaleVersionAndEvidenceFromAnotherApplication() throws Exception {
        capture(); submit();
        mvc.perform(post(ROOT+"/valuations").contentType(MediaType.APPLICATION_JSON).content(valuation(today).replace("\"expectedVersion\":1","\"expectedVersion\":0")))
            .andExpect(status().isConflict());
        var other=new ApplicationDocument(); other.setTenantId("a"); other.setApplicationId("other");
        when(context.getBean(ApplicationDocumentRepository.class).findByIdAndTenantId("other","a")).thenReturn(Optional.of(other));
        mvc.perform(post(ROOT+"/valuations").contentType(MediaType.APPLICATION_JSON).content(valuation(today).replace("\"doc\"","\"other\"")))
            .andExpect(status().isBadRequest());
    }
    @Test void tenantAndPermissionIsolationApplyAtServiceBoundary() throws Exception {
        login("reader","b",PERMISSIONS); mvc.perform(get(ROOT)).andExpect(status().isNotFound());
        login("reader","a","LOAN_VIEW"); mvc.perform(get(ROOT)).andExpect(status().isForbidden());
        login("reader","a","LOGBOOK_VIEW"); mvc.perform(put(ROOT+"/vehicle").contentType(MediaType.APPLICATION_JSON).content(VEHICLE)).andExpect(status().isForbidden());
        assertNull(vehicle);
    }
    @Test void rejectsPhoneProfileAndFreezesAfterApproval() throws Exception {
        profile.setRequirementsJson("{\"OFFER_SELECTION\":[\"KYC_APPROVED\"]}");
        mvc.perform(put(ROOT+"/vehicle").contentType(MediaType.APPLICATION_JSON).content(VEHICLE)).andExpect(status().isBadRequest());
        profile.setRequirementsJson("{\"OFFER_SELECTION\":[\"VALUATION_APPROVED\"]}"); application.setInternalApproved(true);
        mvc.perform(put(ROOT+"/vehicle").contentType(MediaType.APPLICATION_JSON).content(VEHICLE)).andExpect(status().isConflict());
    }
    @Test void rejectsInvalidAmountsDatesAndUnknownVerificationKind() throws Exception {
        capture();
        mvc.perform(post(ROOT+"/valuations").contentType(MediaType.APPLICATION_JSON).content(valuation(today).replace("800000","1100000"))).andExpect(status().isBadRequest());
        mvc.perform(post(ROOT+"/valuations").contentType(MediaType.APPLICATION_JSON).content(valuation(today.plusDays(1)))).andExpect(status().isBadRequest());
        mvc.perform(post(ROOT+"/valuations").contentType(MediaType.APPLICATION_JSON).content(valuation(today).replace("800000","0"))).andExpect(status().isBadRequest());
        mvc.perform(post(ROOT+"/verifications/UNKNOWN").contentType(MediaType.APPLICATION_JSON).content(check(""))).andExpect(status().isBadRequest());
    }
    @Test void revokeApprovedValuationRemovesLimitAndPreventsReapproval() throws Exception {
        capture(); submit(); login("reviewer","a",PERMISSIONS); review("APPROVED").andExpect(status().isOk());
        review("REVOKED").andExpect(status().isOk()).andExpect(jsonPath("$.readiness.maximumSecuredAmount").isEmpty());
        review("APPROVED").andExpect(status().isConflict()); assertEquals(4,history.size());
    }
    @Configuration @EnableMethodSecurity static class Config {
        @Bean CurrentActorService actors(){return new CurrentActorService();}
        @Bean ObjectMapper mapper(){return new ObjectMapper().findAndRegisterModules();}
        @Bean LoanRequestApplicationRepository apps(){return mock(LoanRequestApplicationRepository.class);}
        @Bean OriginationProfileRepository profiles(){return mock(OriginationProfileRepository.class);}
        @Bean ApplicationDocumentRepository documents(){return mock(ApplicationDocumentRepository.class);}
        @Bean LogbookVehicleRepository vehicles(){return mock(LogbookVehicleRepository.class);}
        @Bean LogbookValuationRepository values(){return mock(LogbookValuationRepository.class);}
        @Bean LogbookVerificationRepository checks(){return mock(LogbookVerificationRepository.class);}
        @Bean LogbookAuditRepository audits(){return mock(LogbookAuditRepository.class);}
        @Bean LogbookService service(){return new LogbookService(actors(),apps(),profiles(),documents(),vehicles(),values(),checks(),audits(),mapper());}
    }
}
