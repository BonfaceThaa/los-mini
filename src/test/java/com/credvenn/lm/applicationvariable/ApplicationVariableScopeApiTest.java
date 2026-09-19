package com.credvenn.lm.applicationvariable;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.credvenn.lm.common.exception.GlobalExceptionHandler;
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

class ApplicationVariableScopeApiTest {
    private AnnotationConfigApplicationContext context;
    private MockMvc mvc;
    private ApplicationVariableDefinitionRepository definitions;
    private OriginationProfileRepository profiles;
    private OriginationProfileResolver resolver;
    private static final String ROOT = "/api/v1/application-variable-definitions";
    private static final String BODY = """
        {"code":"REGISTRATION","label":"Registration number","fieldType":"TEXT","required":true,
         "originationProfileCode":" logbook "}
        """;

    @BeforeEach void setup() {
        context = new AnnotationConfigApplicationContext(Config.class);
        definitions = context.getBean(ApplicationVariableDefinitionRepository.class);
        profiles = context.getBean(OriginationProfileRepository.class);
        resolver = context.getBean(OriginationProfileResolver.class);
        mvc = MockMvcBuilders.standaloneSetup(context.getBean(ApplicationVariableController.class))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper().findAndRegisterModules())).build();
        when(context.getBean(TenantRepository.class).findForOriginationUpdate("tenant-1")).thenReturn(Optional.of(new Tenant()));
        when(definitions.save(any())).thenAnswer(call -> call.getArgument(0));
        var profile = new OriginationProfile(); profile.setId("logbook-id"); profile.setTenantId("tenant-1");
        profile.setCode("LOGBOOK"); profile.setActive(false);
        when(profiles.findByTenantIdAndCodeIgnoreCase("tenant-1", "LOGBOOK")).thenReturn(Optional.of(profile));
        login("APPLICATION_VARIABLE_MANAGE");
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); context.close(); }
    private void login(String permission) {
        var authorities = List.of(new SimpleGrantedAuthority(permission));
        var user = new AuthenticatedUser("user-1", "tenant-1", "tester", "tester@example.test", List.of(), authorities);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, authorities));
    }

    @Test void createsScopedQuestionForDraftProfileAndReturnsScope() throws Exception {
        mvc.perform(post(ROOT).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.originationProfileId").value("logbook-id"))
                .andExpect(jsonPath("$.definitionVersion").value(1));
        verify(profiles).findByTenantIdAndCodeIgnoreCase("tenant-1", "LOGBOOK");
    }
    @Test void legacyCreateIsSharedAndScopeCanBeMovedOrClearedByPut() throws Exception {
        mvc.perform(post(ROOT).contentType(MediaType.APPLICATION_JSON).content("""
                {"code":"SHARED","label":"Shared","fieldType":"TEXT"}
                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.originationProfileId").isEmpty());
        var existing = question();
        when(definitions.findByIdAndTenantId("question-1", "tenant-1")).thenReturn(Optional.of(existing));
        mvc.perform(put(ROOT + "/question-1").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.originationProfileId").value("logbook-id"))
                .andExpect(jsonPath("$.definitionVersion").value(2));
        mvc.perform(put(ROOT + "/question-1").contentType(MediaType.APPLICATION_JSON)
                .content(BODY.replace("\" logbook \"", "null")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.originationProfileId").isEmpty())
                .andExpect(jsonPath("$.definitionVersion").value(3));
    }
    @Test void unknownOrOtherTenantProfileCannotScopeDefinition() throws Exception {
        mvc.perform(post(ROOT).contentType(MediaType.APPLICATION_JSON).content(BODY.replace(" logbook ", "OTHER")))
                .andExpect(status().isNotFound());
        verify(definitions, never()).save(any());
    }
    @Test void questionIdsCannotBeUpdatedAcrossTenants() throws Exception {
        mvc.perform(put(ROOT + "/other-question").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound());
        verify(definitions, never()).save(any());
    }
    @Test void defaultAndExplicitFormRequestsPassResolvedScope() throws Exception {
        login("LOAN_CREATE");
        when(resolver.resolveForApplication("tenant-1", null)).thenReturn("phone-id");
        when(resolver.resolveForApplication("tenant-1", "CUSTOM_PHONE")).thenReturn("custom-id");
        when(definitions.findApplicableForCreation("tenant-1", "phone-id")).thenReturn(List.of(question()));
        mvc.perform(get(ROOT)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get(ROOT).param("originationProfileCode", "CUSTOM_PHONE")).andExpect(status().isOk());
        verify(definitions).findApplicableForCreation("tenant-1", "custom-id");
        verify(definitions, never()).findAllByTenantIdAndActiveTrueOrderByDisplayOrderAsc(anyString());
    }
    @Test void adminCanListAllOrFilterDraftProfile() throws Exception {
        mvc.perform(get(ROOT + "/all")).andExpect(status().isOk());
        verify(definitions).findAllByTenantIdOrderByDisplayOrderAsc("tenant-1");
        mvc.perform(get(ROOT + "/all").param("originationProfileCode", "LOGBOOK")).andExpect(status().isOk());
        verify(definitions).findApplicable("tenant-1", "logbook-id", false);
        verifyNoInteractions(resolver);
    }
    @Test void loanOfficerCannotAccessAllScopesOrChangeDefinitions() throws Exception {
        login("LOAN_CREATE");
        mvc.perform(get(ROOT + "/all")).andExpect(status().isForbidden());
        mvc.perform(post(ROOT).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isForbidden());
        verifyNoInteractions(definitions);
    }
    private static ApplicationVariableDefinition question() {
        var d = new ApplicationVariableDefinition(); d.setTenantId("tenant-1"); d.setCode("REGISTRATION");
        d.setLabel("Registration number"); d.setFieldType(ApplicationVariableFieldType.TEXT); return d;
    }
    @Configuration @EnableMethodSecurity static class Config {
        @Bean ApplicationVariableDefinitionRepository definitions() { return mock(ApplicationVariableDefinitionRepository.class); }
        @Bean ApplicationVariableRepository variables() { return mock(ApplicationVariableRepository.class); }
        @Bean OriginationProfileRepository profiles() { return mock(OriginationProfileRepository.class); }
        @Bean OriginationProfileResolver resolver() { return mock(OriginationProfileResolver.class); }
        @Bean TenantRepository tenants() { return mock(TenantRepository.class); }
        @Bean ApplicationVariableService service(ApplicationVariableDefinitionRepository d, ApplicationVariableRepository v,
                OriginationProfileRepository p, OriginationProfileResolver r, TenantRepository t) {
            return new ApplicationVariableService(d, v, new ObjectMapper(), p, r, new OriginationProfileValidator(), t);
        }
        @Bean ApplicationVariableController controller(ApplicationVariableService s) { return new ApplicationVariableController(s, new CurrentActorService()); }
    }
}
