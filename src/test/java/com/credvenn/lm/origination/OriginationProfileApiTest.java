package com.credvenn.lm.origination;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.credvenn.lm.origination.OriginationProfileDtos.*;

import com.credvenn.lm.common.exception.GlobalExceptionHandler;
import com.credvenn.lm.security.*;
import com.credvenn.lm.tenant.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.*;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Real controllers, JSON validation and method-security proxies; persistence is mocked. */
class OriginationProfileApiTest {
    private AnnotationConfigApplicationContext context;
    private MockMvc mvc;
    private OriginationProfileRepository profiles;
    private OriginationProfileAuditRepository audits;
    private TenantRepository tenants;
    private ObjectMapper json;
    private Tenant tenant;
    private OriginationProfile profile;
    static final String PHONE = """
        {"OFFER_SELECTION":["KYC_APPROVED","CLIENT_PROVISIONED","STATEMENT_ACCEPTED"],
         "INTERNAL_APPROVAL":["CONSENT_CAPTURED","CLIENT_PROVISIONED","DEVICE_ASSIGNED","FINANCING_CALCULATED","ACTIVE_PRODUCT_SELECTED"],
         "DISBURSEMENT":["PENDING_LOAN_CREATED","DEVICE_ASSIGNED","DEPOSIT_MATCHED"]}
        """;
    static final String ROOT = "/api/v1/origination-profiles";
    static final String DEFAULT = "/api/v1/tenant/origination-default";

    @BeforeEach void setup() {
        context = new AnnotationConfigApplicationContext(Config.class);
        profiles = context.getBean(OriginationProfileRepository.class);
        audits = context.getBean(OriginationProfileAuditRepository.class);
        tenants = context.getBean(TenantRepository.class);
        json = context.getBean(ObjectMapper.class);
        var service = context.getBean(OriginationProfileService.class);
        mvc = MockMvcBuilders.standaloneSetup(new OriginationProfileController(service), new OriginationDefaultController(service))
                .setControllerAdvice(new OriginationProfileExceptionHandler(), new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(json)).build();
        tenant = new Tenant(); tenant.setId("tenant-a");
        when(tenants.findById("tenant-a")).thenReturn(Optional.of(tenant));
        when(tenants.findForOriginationUpdate("tenant-a")).thenReturn(Optional.of(tenant));
        profile = new OriginationProfile();
        profile.setId("profile-a"); profile.setTenantId("tenant-a"); profile.setCode("PHONE_FINANCE");
        profile.setDisplayName("Phone finance"); profile.setRequirementsJson(PHONE);
        profile.setCreatedBy("admin"); profile.setUpdatedBy("admin");
        when(profiles.findByTenantIdAndCodeIgnoreCase("tenant-a", "PHONE_FINANCE")).thenReturn(Optional.of(profile));
        when(profiles.findByTenantIdAndId("tenant-a", "profile-a")).thenReturn(Optional.of(profile));
        when(profiles.saveAndFlush(any())).thenAnswer(i -> {
            OriginationProfile p = i.getArgument(0);
            if (p.getId() == null) p.setId("new-profile"); else p.setVersion(p.getVersion() + 1);
            return p;
        });
        login("tenant-a", "ORIGINATION_PROFILE_VIEW", "ORIGINATION_PROFILE_CREATE", "ORIGINATION_PROFILE_UPDATE");
    }

    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); context.close(); }

    private void login(String tenantId, String... permissions) {
        var authorities = Arrays.stream(permissions).map(SimpleGrantedAuthority::new).toList();
        var user = new AuthenticatedUser("user-a", tenantId, "admin", "admin@example.test", List.of("TENANT_ADMIN"), authorities);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, authorities));
    }

    private String creation(String code, String requirements) {
        return "{\"code\":\"" + code + "\",\"displayName\":\" Phone finance \",\"requirements\":" + requirements + "}";
    }

    @Test void createsNormalizedInactiveProfileAndAuditWithinAuthenticatedTenant() throws Exception {
        mvc.perform(post(ROOT).contentType(MediaType.APPLICATION_JSON).content(creation(" phone_new ", PHONE)))
                .andExpect(status().isCreated()).andExpect(header().string("Location", ROOT + "/PHONE_NEW"))
                .andExpect(jsonPath("$.code").value("PHONE_NEW"))
                .andExpect(jsonPath("$.displayName").value("Phone finance"))
                .andExpect(jsonPath("$.active").value(false));
        var capture = ArgumentCaptor.forClass(OriginationProfile.class);
        verify(profiles).saveAndFlush(capture.capture());
        assertEquals("tenant-a", capture.getValue().getTenantId());
        var audit = ArgumentCaptor.forClass(OriginationProfileAudit.class);
        verify(audits).save(audit.capture());
        assertEquals("CREATE", audit.getValue().getAction());
        assertEquals("tenant-a", audit.getValue().getTenantId());
        assertNull(audit.getValue().getBeforeJson());
    }

    @Test void duplicateCodeReturnsConflict() throws Exception {
        when(profiles.existsByTenantIdAndCodeIgnoreCase("tenant-a", "PHONE_FINANCE")).thenReturn(true);
        mvc.perform(post(ROOT).contentType(MediaType.APPLICATION_JSON).content(creation("phone_finance", PHONE)))
                .andExpect(status().isConflict());
        verify(profiles, never()).saveAndFlush(any());
    }

    @Test void listUsesTenantAndActiveFilter() throws Exception {
        when(profiles.findAllByTenantIdAndActiveOrderByDisplayNameAsc("tenant-a", true)).thenReturn(List.of(profile));
        mvc.perform(get(ROOT).param("active", "true")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].code").value("PHONE_FINANCE"));
        verify(profiles).findAllByTenantIdAndActiveOrderByDisplayNameAsc("tenant-a", true);
    }

    @Test void crossTenantCodeIsNotFoundAndCannotBeUpdatedOrMadeDefault() throws Exception {
        login("tenant-b", "ORIGINATION_PROFILE_VIEW", "ORIGINATION_PROFILE_UPDATE");
        var other = new Tenant(); other.setId("tenant-b");
        when(tenants.findForOriginationUpdate("tenant-b")).thenReturn(Optional.of(other));
        mvc.perform(get(ROOT + "/PHONE_FINANCE")).andExpect(status().isNotFound());
        mvc.perform(patch(ROOT + "/PHONE_FINANCE").contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":0,\"displayName\":\"Other\"}")).andExpect(status().isNotFound());
        mvc.perform(put(DEFAULT).contentType(MediaType.APPLICATION_JSON)
                .content("{\"originationProfileCode\":\"PHONE_FINANCE\"}")).andExpect(status().isNotFound());
        verify(profiles, never()).saveAndFlush(any());
        verify(tenants, never()).saveAndFlush(any());
    }

    @Test void viewPermissionDoesNotAllowWrites() throws Exception {
        login("tenant-a", "ORIGINATION_PROFILE_VIEW");
        mvc.perform(get(ROOT + "/PHONE_FINANCE")).andExpect(status().isOk());
        mvc.perform(post(ROOT).contentType(MediaType.APPLICATION_JSON).content(creation("PHONE", PHONE))).andExpect(status().isForbidden());
        mvc.perform(patch(ROOT + "/PHONE_FINANCE").contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":0,\"active\":true}")).andExpect(status().isForbidden());
        mvc.perform(delete(ROOT + "/PHONE_FINANCE").param("expectedVersion", "0")).andExpect(status().isForbidden());
        mvc.perform(put(DEFAULT).contentType(MediaType.APPLICATION_JSON)
                .content("{\"originationProfileCode\":\"PHONE_FINANCE\"}")).andExpect(status().isForbidden());
    }

    @Test void platformPrincipalWithoutTenantIsRejected() throws Exception {
        login(null, "ORIGINATION_PROFILE_VIEW");
        mvc.perform(get(ROOT)).andExpect(status().isForbidden());
        verifyNoInteractions(profiles);
    }

    @Test void activatesPhoneProfileWithVersionCheckAndAuditsOldAndNewValues() throws Exception {
        mvc.perform(patch(ROOT + "/PHONE_FINANCE").contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":0,\"active\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true)).andExpect(jsonPath("$.version").value(1));
        var audit = ArgumentCaptor.forClass(OriginationProfileAudit.class);
        verify(audits).save(audit.capture());
        assertFalse(json.readTree(audit.getValue().getBeforeJson()).get("active").asBoolean());
        assertTrue(json.readTree(audit.getValue().getAfterJson()).get("active").asBoolean());
        mvc.perform(patch(ROOT + "/PHONE_FINANCE").contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":0,\"displayName\":\"Stale\"}")).andExpect(status().isConflict());
        assertEquals("Phone finance", profile.getDisplayName());
    }

    @Test void defaultMustBeActiveAndCannotBeDeactivated() throws Exception {
        mvc.perform(get(DEFAULT)).andExpect(status().isOk()).andExpect(jsonPath("$.defaultOriginationProfileCode").isEmpty());
        mvc.perform(put(DEFAULT).contentType(MediaType.APPLICATION_JSON)
                .content("{\"originationProfileCode\":\"PHONE_FINANCE\"}")).andExpect(status().isBadRequest());
        profile.setActive(true);
        mvc.perform(put(DEFAULT).contentType(MediaType.APPLICATION_JSON)
                .content("{\"originationProfileCode\":\"PHONE_FINANCE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.defaultOriginationProfileCode").value("PHONE_FINANCE"));
        assertEquals("profile-a", tenant.getDefaultOriginationProfileId());
        mvc.perform(delete(ROOT + "/PHONE_FINANCE").param("expectedVersion", "0")).andExpect(status().isConflict());
        assertTrue(profile.isActive());
        mvc.perform(get(ROOT + "/PHONE_FINANCE")).andExpect(jsonPath("$.isDefault").value(true));
    }

    @Test void deletingNonDefaultOnlyDeactivatesAndRetainsRecord() throws Exception {
        profile.setActive(true);
        mvc.perform(delete(ROOT + "/PHONE_FINANCE").param("expectedVersion", "0")).andExpect(status().isNoContent());
        assertFalse(profile.isActive());
        verify(profiles, never()).delete(any());
        mvc.perform(get(ROOT + "/PHONE_FINANCE")).andExpect(status().isOk());
    }

    @Test void invalidPayloadsAndRequirementsFailBeforePersistence() throws Exception {
        for (String body : List.of(creation("PHONE", "{}"), creation("123INVALID", PHONE),
                creation("PHONE", PHONE.replace("KYC_APPROVED", "UNKNOWN_CHECK")),
                creation("PHONE", PHONE.replace("KYC_APPROVED", "KYC_APPROVED\",\"KYC_APPROVED")),
                creation("PHONE", PHONE.replace("KYC_APPROVED", "DEPOSIT_MATCHED")))) {
            mvc.perform(post(ROOT).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        mvc.perform(patch(ROOT + "/PHONE_FINANCE").contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":true}")).andExpect(status().isBadRequest());
        verify(profiles, never()).saveAndFlush(any());
        verifyNoInteractions(audits);
    }

    @Test void logbookDraftCanBeSavedButNotActivated() throws Exception {
        String requirements = PHONE.replace("DEVICE_ASSIGNED", "VALUATION_APPROVED");
        String configuration = "{\"valuationBasis\":\"FORCED_SALE_VALUE\",\"maxLtvRatio\":0.6,\"valuationValidityDays\":30}";
        String create = creation("LOGBOOK", requirements);
        create = create.substring(0, create.length() - 1) + ",\"configuration\":" + configuration + "}";
        mvc.perform(post(ROOT).contentType(MediaType.APPLICATION_JSON).content(create)).andExpect(status().isCreated());
        profile.setRequirementsJson(requirements); profile.setConfigurationJson(configuration);
        mvc.perform(patch(ROOT + "/PHONE_FINANCE").contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":0,\"active\":true}")).andExpect(status().isBadRequest());
        assertFalse(profile.isActive());
    }

    @Test void invalidValuationBoundsAndMissingConfigurationAreRejected() throws Exception {
        String requirements = PHONE.replace("DEVICE_ASSIGNED", "VALUATION_APPROVED");
        mvc.perform(post(ROOT).contentType(MediaType.APPLICATION_JSON).content(creation("LOGBOOK", requirements)))
                .andExpect(status().isBadRequest());
        for (String ratio : List.of("0", "-0.1", "1.1")) {
            String create = creation("LOGBOOK", requirements);
            create = create.substring(0, create.length() - 1) + ",\"configuration\":{\"valuationBasis\":\"MARKET_VALUE\",\"maxLtvRatio\":" + ratio + ",\"valuationValidityDays\":30}}";
            mvc.perform(post(ROOT).contentType(MediaType.APPLICATION_JSON).content(create)).andExpect(status().isBadRequest());
        }
        verify(profiles, never()).saveAndFlush(any());
    }

    @Test void auditHistoryIsTenantScopedAndPaged() throws Exception {
        when(audits.findAllByTenantIdAndProfileIdOrderByCreatedAtDesc(eq("tenant-a"), eq("profile-a"), any()))
                .thenReturn(new PageImpl<>(List.of()));
        mvc.perform(get(ROOT + "/PHONE_FINANCE/history")).andExpect(status().isOk());
        verify(audits).findAllByTenantIdAndProfileIdOrderByCreatedAtDesc(eq("tenant-a"), eq("profile-a"), any());
        mvc.perform(get(ROOT + "/PHONE_FINANCE/history").param("size", "101")).andExpect(status().isBadRequest());
    }

    @Test void legacyProfilesRemainReadableEditableAndActivatable() throws Exception {
        String legacy = PHONE.replace("\"CONSENT_CAPTURED\",\"CLIENT_PROVISIONED\"", "\"CONSENT_CAPTURED\"")
                .replace("\"FINANCING_CALCULATED\",\"ACTIVE_PRODUCT_SELECTED\"", "\"FINANCING_ASSESSED\"")
                .replace("PENDING_LOAN_CREATED", "INTERNAL_APPROVAL_VALID");
        profile.setRequirementsJson(legacy);
        mvc.perform(get(ROOT + "/PHONE_FINANCE")).andExpect(status().isOk())
                .andExpect(jsonPath("$.requirements.DISBURSEMENT[0]").value("INTERNAL_APPROVAL_VALID"));
        mvc.perform(patch(ROOT + "/PHONE_FINANCE").contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":0,\"active\":true,\"displayName\":\"Legacy phone\"}"))
                .andExpect(status().isOk());
        assertEquals(legacy.replaceAll("\\s", ""), profile.getRequirementsJson().replaceAll("\\s", ""));
        mvc.perform(put(DEFAULT).contentType(MediaType.APPLICATION_JSON)
                .content("{\"originationProfileCode\":\"PHONE_FINANCE\"}")).andExpect(status().isOk());
    }

    @Test void legacyRequirementsCanBeExplicitlyUpgradedWithAuditHistory() throws Exception {
        String legacy = PHONE.replace("\"CONSENT_CAPTURED\",\"CLIENT_PROVISIONED\"", "\"CONSENT_CAPTURED\"")
                .replace("\"FINANCING_CALCULATED\",\"ACTIVE_PRODUCT_SELECTED\"", "\"FINANCING_ASSESSED\"")
                .replace("PENDING_LOAN_CREATED", "INTERNAL_APPROVAL_VALID");
        profile.setRequirementsJson(legacy);
        profile.setActive(true);
        mvc.perform(patch(ROOT + "/PHONE_FINANCE").contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":0,\"requirements\":" + PHONE + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.requirements.DISBURSEMENT[0]").value("PENDING_LOAN_CREATED"));
        var audit = ArgumentCaptor.forClass(OriginationProfileAudit.class);
        verify(audits).save(audit.capture());
        assertTrue(audit.getValue().getBeforeJson().contains("FINANCING_ASSESSED"));
        assertTrue(audit.getValue().getAfterJson().contains("FINANCING_CALCULATED"));
        assertFalse(audit.getValue().getAfterJson().contains("INTERNAL_APPROVAL_VALID"));
    }

    @Test void correctedPhoneSetCannotActivateWithoutProductOrClientPrerequisites() throws Exception {
        for (String missing : List.of("CLIENT_PROVISIONED", "ACTIVE_PRODUCT_SELECTED")) {
            var requirements = json.readTree(PHONE);
            var approval = (com.fasterxml.jackson.databind.node.ArrayNode) requirements.get("INTERNAL_APPROVAL");
            for (int i = approval.size() - 1; i >= 0; i--) if (approval.get(i).asText().equals(missing)) approval.remove(i);
            mvc.perform(patch(ROOT + "/PHONE_FINANCE").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"expectedVersion\":0,\"active\":true,\"requirements\":" + requirements + "}"))
                    .andExpect(status().isBadRequest());
        }
        verify(profiles, never()).saveAndFlush(any());
    }

    @Test void newCodesAreRejectedInWrongStages() throws Exception {
        for (String misplaced : List.of("FINANCING_CALCULATED", "ACTIVE_PRODUCT_SELECTED", "PENDING_LOAN_CREATED")) {
            mvc.perform(post(ROOT).contentType(MediaType.APPLICATION_JSON)
                    .content(creation("PHONE", PHONE.replace("KYC_APPROVED", misplaced))))
                    .andExpect(status().isBadRequest());
        }
        verify(profiles, never()).saveAndFlush(any());
    }
    @org.springframework.context.annotation.Configuration @EnableMethodSecurity
    static class Config {
        @Bean OriginationProfileRepository profiles() { return mock(OriginationProfileRepository.class); }
        @Bean OriginationProfileAuditRepository audits() { return mock(OriginationProfileAuditRepository.class); }
        @Bean TenantRepository tenants() { return mock(TenantRepository.class); }
        @Bean CurrentActorService actors() { return new CurrentActorService(); }
        @Bean ObjectMapper json() { return new ObjectMapper().findAndRegisterModules(); }
        @Bean OriginationProfileValidator validator() { return new OriginationProfileValidator(); }
        @Bean OriginationProfileService service(OriginationProfileRepository p, OriginationProfileAuditRepository a,
                TenantRepository t, CurrentActorService actors, OriginationProfileValidator v, ObjectMapper j) {
            return new OriginationProfileService(p, a, t, actors, v, j);
        }
    }
}
