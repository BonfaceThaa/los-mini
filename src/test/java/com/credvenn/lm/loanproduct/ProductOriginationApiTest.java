package com.credvenn.lm.loanproduct;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.credvenn.lm.application.*;
import com.credvenn.lm.common.exception.GlobalExceptionHandler;
import com.credvenn.lm.fineract.*;
import com.credvenn.lm.fineracttemplate.GlAccountTemplateRepository;
import com.credvenn.lm.origination.*;
import com.credvenn.lm.security.*;
import com.credvenn.lm.tenant.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ProductOriginationApiTest {
    private AnnotationConfigApplicationContext context;
    private MockMvc mvc;
    private LoanProductMappingRepository products;
    private LoanRequestApplicationRepository applications;
    private OriginationProfileRepository profiles;
    private LoanProductMapping mapping;
    private Tenant tenant;
    private static final String PATH = "/api/v1/loan-products/PHONE/origination-profile";
    private static final String BODY = """
            {"originationProfileCode":"LOGBOOK"}
            """;
    @BeforeEach void setup() {
        context = new AnnotationConfigApplicationContext(Config.class);
        products = context.getBean(LoanProductMappingRepository.class);
        applications = context.getBean(LoanRequestApplicationRepository.class);
        profiles = context.getBean(OriginationProfileRepository.class);
        mvc = MockMvcBuilders.standaloneSetup(context.getBean(ProductOriginationController.class), context.getBean(FineractLoanProductController.class))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper().findAndRegisterModules())).build();
        mapping = new LoanProductMapping(); mapping.setTenantId("a"); mapping.setProductCode("PHONE");
        mapping.setOriginationProfileId("phone-profile"); mapping.setFineractProductId(7L);
        org.springframework.test.util.ReflectionTestUtils.setField(mapping, "id", "product-a");
        when(products.findByTenantIdAndProductCodeIgnoreCase("a", "PHONE")).thenReturn(Optional.of(mapping));
        when(products.findForUpdateByTenantIdAndId("a", "product-a")).thenReturn(Optional.of(mapping));
        when(products.save(any())).thenAnswer(call -> call.getArgument(0));
        var logbook = new OriginationProfile(); logbook.setId("logbook-profile"); logbook.setTenantId("a");
        when(profiles.findByTenantIdAndCodeIgnoreCase("a", "LOGBOOK")).thenReturn(Optional.of(logbook));
        var phone = new OriginationProfile(); phone.setId("phone-profile"); phone.setActive(true);
        when(profiles.findByTenantIdAndId("a", "phone-profile")).thenReturn(Optional.of(phone));
        tenant = new Tenant(); tenant.setDefaultOriginationProfileId("phone-profile");
        when(context.getBean(TenantRepository.class).findById("a")).thenReturn(Optional.of(tenant));
        when(context.getBean(TenantService.class).getRequiredTenant("a")).thenReturn(tenant);
        login("LOAN_PRODUCT_UPDATE", "LOAN_CREATE");
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); context.close(); }
    private void login(String... permissions) {
        var authorities = Arrays.stream(permissions).map(SimpleGrantedAuthority::new).toList();
        var user = new AuthenticatedUser("u", "a", "admin", "a@example.test", List.of(), authorities);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, authorities));
    }
    @Test void associateIsLocalAndCanPrepareDraftProfile() throws Exception {
        mvc.perform(put(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.originationProfileId").value("logbook-profile"));
        assertEquals("admin", mapping.getUpdatedBy());
        verifyNoInteractions(context.getBean(FineractGateway.class));
    }
    @Test void usedProductCannotMoveAndIdempotentAssociationRemainsAllowed() throws Exception {
        when(applications.findProductReferencesForUpdate("a", "product-a", "7")).thenReturn(List.of(new LoanRequestApplication()));
        mvc.perform(put(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isConflict());
        assertEquals("phone-profile", mapping.getOriginationProfileId());
        mapping.setOriginationProfileId("logbook-profile");
        mvc.perform(put(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isOk());
        verify(products, never()).save(any());
    }
    @Test void legacyUnclassifiedProductRequiresCompatibleApplicationProfiles() throws Exception {
        mapping.setOriginationProfileId(null);
        var application = new LoanRequestApplication(); application.setOriginationProfileId("phone-profile");
        when(applications.findProductReferencesForUpdate("a", "product-a", "7")).thenReturn(List.of(application));
        mvc.perform(put(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isConflict());
        application.setOriginationProfileId("logbook-profile");
        mvc.perform(put(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isOk());
    }
    @Test void rejectsForeignProfileAndMissingUpdatePermission() throws Exception {
        mvc.perform(put(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY.replace("LOGBOOK", "FOREIGN")))
                .andExpect(status().isNotFound());
        login("LOAN_VIEW");
        mvc.perform(put(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isForbidden());
        verify(products, never()).save(any());
    }
    @Test void creationResolvesDefaultAndExplicitProfilesBeforeFineract() throws Exception {
        var gateway = context.getBean(FineractGateway.class);
        when(gateway.createLoanProduct(any(), any())).thenReturn("8");
        mvc.perform(post("/api/v1/loan-products").contentType(MediaType.APPLICATION_JSON).content(creation(null)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.originationProfileId").value("phone-profile"));
        mvc.perform(post("/api/v1/loan-products").contentType(MediaType.APPLICATION_JSON).content(creation("LOGBOOK")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.originationProfileId").value("logbook-profile"));
        verify(gateway, times(2)).createLoanProduct(any(), any());
    }
    @Test void invalidProfileOrMissingDefaultStopsCreationBeforeRemoteCalls() throws Exception {
        mvc.perform(post("/api/v1/loan-products").contentType(MediaType.APPLICATION_JSON).content(creation("FOREIGN")))
                .andExpect(status().isNotFound());
        tenant.setDefaultOriginationProfileId(null);
        mvc.perform(post("/api/v1/loan-products").contentType(MediaType.APPLICATION_JSON).content(creation(null)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(context.getBean(FineractGateway.class)); verify(products, never()).save(any());
    }
    @Test void exactSwaggerLogbookExampleResolvesTenantAccountsAndCreatesDraft() throws Exception {
        var purposes = List.of("LOAN_PORTFOLIO", "BANK_SETTLEMENT", "INTEREST_INCOME", "FEE_INCOME",
                "PENALTY_INCOME", "INCOME_FROM_RECOVERY", "WRITE_OFF_EXPENSE_MAIN", "SUSPENSE_ACCOUNT", "OVERPAYMENT_HOLDING");
        var templates = new ArrayList<com.credvenn.lm.fineracttemplate.GlAccountTemplate>();
        for (int i = 0; i < purposes.size(); i++) {
            var template = new com.credvenn.lm.fineracttemplate.GlAccountTemplate();
            template.setTenantId("a"); template.setBusinessPurpose(purposes.get(i));
            template.setFineractGlAccountId((long) i + 1); template.setActive(true); templates.add(template);
        }
        when(context.getBean(GlAccountTemplateRepository.class).findAllByTenantIdOrderByTemplateCodeAsc("a")).thenReturn(templates);
        var gateway = context.getBean(FineractGateway.class);
        when(gateway.createLoanProduct(any(), any())).thenReturn("8");
        mvc.perform(post("/api/v1/loan-products").contentType(MediaType.APPLICATION_JSON).content(LoanProductCatalogDtos.LOGBOOK_EXAMPLE))
                .andExpect(status().isOk()).andExpect(jsonPath("$.originationProfileId").value("logbook-profile"))
                .andExpect(jsonPath("$.active").value(false));
        var sent = org.mockito.ArgumentCaptor.forClass(FineractGateway.CreateLoanProductRequest.class);
        verify(gateway).createLoanProduct(eq(tenant), sent.capture());
        assertEquals(0, sent.getValue().interestType());
        assertEquals(2, sent.getValue().repaymentFrequencyType());
        assertEquals(1L, sent.getValue().loanPortfolioAccountId());
        assertEquals(9L, sent.getValue().overpaymentLiabilityAccountId());
    }
    @Test void exactSwaggerExampleWithoutTenantAccountsReturns400BeforeFineract() throws Exception {
        mvc.perform(post("/api/v1/loan-products").contentType(MediaType.APPLICATION_JSON).content(LoanProductCatalogDtos.LOGBOOK_EXAMPLE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Required GL account template is missing for business purpose LOAN_PORTFOLIO"));
        verifyNoInteractions(context.getBean(FineractGateway.class));
    }
    private String creation(String code) {
        return """
            {"productCode":"NEW_PRODUCT","displayName":"New product","shortName":"NEW","currencyCode":"KES",
             "principal":{"min":1000,"defaultAmount":10000,"max":50000},
             "term":{"numberOfRepayments":4,"repaymentEvery":1,"repaymentFrequency":"MONTHS"},
             "interest":{"ratePerPeriod":0,"interestType":"FLAT","calculationPeriodType":"SAME_AS_REPAYMENT_PERIOD","rateFrequency":"MONTHS"},
             "amortizationType":"EQUAL_INSTALLMENTS","accountingTemplateCode":"STANDARD","active":true,
             "accountingAccounts":{"loanPortfolioAccountId":1,"fundSourceAccountId":2,"interestOnLoanAccountId":3,
             "incomeFromFeeAccountId":4,"incomeFromPenaltyAccountId":5,"incomeFromRecoveryAccountId":6,
             "writeOffAccountId":7,"transfersInSuspenseAccountId":8,"overpaymentLiabilityAccountId":9},
             "originationProfileCode":%s}
            """.formatted(code == null ? "null" : "\"" + code + "\"");
    }
    @Configuration @EnableMethodSecurity static class Config {
        @Bean GlAccountTemplateRepository glTemplates() { return mock(GlAccountTemplateRepository.class); }
        @Bean LoanProductMappingRepository products() { return mock(LoanProductMappingRepository.class); }
        @Bean LoanRequestApplicationRepository applications() { return mock(LoanRequestApplicationRepository.class); }
        @Bean OriginationProfileRepository profiles() { return mock(OriginationProfileRepository.class); }
        @Bean TenantRepository tenants() { return mock(TenantRepository.class); }
        @Bean CurrentActorService actors() { return new CurrentActorService(); }
        @Bean FineractGateway gateway() { return mock(FineractGateway.class); }
        @Bean TenantService tenantService() { return mock(TenantService.class); }
        @Bean ProductOriginationService origination(OriginationProfileRepository p, TenantRepository t,
                LoanProductMappingRepository products, LoanRequestApplicationRepository a, CurrentActorService actors) {
            return new ProductOriginationService(p, new OriginationProfileValidator(), t, products, a, actors, mock(jakarta.persistence.EntityManager.class));
        }
        @Bean LoanProductCatalogService catalog(LoanProductMappingRepository p, CurrentActorService a,
                TenantService t, FineractGateway g, ProductOriginationService o) {
            return new LoanProductCatalogService(p, glTemplates(), a, t, g, o);
        }
        @Bean ProductOriginationController controller(ProductOriginationService s) { return new ProductOriginationController(s); }
        @Bean FineractLoanProductController catalogController(LoanProductCatalogService s) { return new FineractLoanProductController(s); }
    }
}
