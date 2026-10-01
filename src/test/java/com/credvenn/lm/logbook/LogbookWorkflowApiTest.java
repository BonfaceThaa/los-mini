package com.credvenn.lm.logbook;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.credvenn.lm.application.*;
import com.credvenn.lm.common.exception.GlobalExceptionHandler;
import com.credvenn.lm.kyc.KycCheckRepository;
import com.credvenn.lm.loanproduct.LoanProductMappingRepository;
import com.credvenn.lm.origination.OriginationProfileRepository;
import com.credvenn.lm.security.*;
import com.credvenn.lm.statement.*;
import com.credvenn.lm.tenant.TenantService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.util.List;
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

class LogbookWorkflowApiTest {
    AnnotationConfigApplicationContext context;
    MockMvc mvc;
    static final String ROOT="/api/v1/applications/foreign-app/logbook";
    @BeforeEach void setup() {
        context=new AnnotationConfigApplicationContext(Config.class);
        mvc=MockMvcBuilders.standaloneSetup(new LogbookWorkflowController(context.getBean(LogbookWorkflowService.class)))
            .setControllerAdvice(new LogbookExceptionHandler(),new GlobalExceptionHandler())
            .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper().findAndRegisterModules())).build();
    }
    @AfterEach void cleanup(){SecurityContextHolder.clearContext();context.close();}
    void login(String permission){
        var authorities=List.of(new SimpleGrantedAuthority(permission));
        var actor=new AuthenticatedUser("user","tenant-a","user","test@example.test",List.of(),authorities);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor,null,authorities));
    }
    @Test void requiresLoanView() throws Exception {login("LOGBOOK_VIEW");mvc.perform(get(ROOT+"/workflow")).andExpect(status().isForbidden());}
    @Test void requiresLoanCreateForFinancing() throws Exception {
        login("LOAN_VIEW");mvc.perform(post(ROOT+"/financing").contentType(MediaType.APPLICATION_JSON)
            .content("{\"expectedApplicationVersion\":0,\"amount\":300000}")).andExpect(status().isForbidden());
    }
    @Test void requiresManualApprovalForRetry() throws Exception {
        login("LOAN_CREATE");mvc.perform(post(ROOT+"/operations/op/retry")).andExpect(status().isForbidden());
    }
    @Test void hidesOtherTenantApplication() throws Exception {
        login("LOAN_VIEW");mvc.perform(get(ROOT+"/workflow")).andExpect(status().isNotFound());
    }
    @Test void rejectsMissingVersionAndInvalidAmount() throws Exception {
        login("LOAN_CREATE");mvc.perform(post(ROOT+"/financing").contentType(MediaType.APPLICATION_JSON)
            .content("{\"amount\":0}")).andExpect(status().isBadRequest());
    }
    @Configuration @EnableMethodSecurity static class Config {
        @Bean LogbookWorkflowService workflow(){return new LogbookWorkflowService(mock(LogbookWorkflowPolicy.class),
            mock(LogbookService.class),mock(LoanRequestApplicationRepository.class),mock(OriginationProfileRepository.class),
            mock(LoanProductMappingRepository.class),mock(KycCheckRepository.class),mock(StatementReviewRepository.class),
            mock(StatementAnalysisRepository.class),mock(TenantService.class),mock(LogbookLoanOperationRepository.class),
            mock(ApplicationStatusHistoryRepository.class),new CurrentActorService(),mock(EntityManager.class));}
    }
}
