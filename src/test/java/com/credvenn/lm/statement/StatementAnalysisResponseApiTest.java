package com.credvenn.lm.statement;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.credvenn.lm.security.AuthenticatedUser;
import com.credvenn.lm.security.CurrentActorService;
import com.credvenn.stmt.analyzer.core.AnalysisInput;
import com.credvenn.stmt.analyzer.core.DefaultStatementAnalyzer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class StatementAnalysisResponseApiTest {
    @Test
    void sameEndpointReturnsInternalSectionsAndPreservesCladfyShape() throws Exception {
        var service = mock(StatementAnalysisService.class);
        var actors = mock(CurrentActorService.class);
        when(actors.requireCurrentUser()).thenReturn(new AuthenticatedUser("user", "tenant-1", "officer", "user@example.test", List.of(), List.of()));
        var mvc = MockMvcBuilders.standaloneSetup(new StatementAnalysisController(service, actors)).build();
        var analysis = new StatementAnalysis();
        analysis.setProvider("INTERNAL_MPESA");
        analysis.setStatus(StatementAnalysisStatus.MANUAL_REVIEW_REQUIRED);
        org.springframework.test.util.ReflectionTestUtils.setField(analysis, "id", "analysis-1");
        try (var pdf = getClass().getResourceAsStream("/fixtures/balance-example.pdf")) {
            var result = new DefaultStatementAnalyzer().analyze(new AnalysisInput(pdf.readAllBytes(), null, "MPESA", "analysis-1"));
            when(service.get("tenant-1", "app-1")).thenReturn(new StatementDtos.StatementAssessmentResponse(
                    StatementAnalysisService.toAnalysisResponse(analysis, null, List.of(), result), null, StatementEffectiveOutcome.MANUAL_REVIEW_REQUIRED));
        }
        var response = mvc.perform(get("/api/v1/applications/app-1/statement-analysis")).andExpect(status().isOk()).andReturn();
        var body = new ObjectMapper().readTree(response.getResponse().getContentAsString());
        assertEquals("32076.81", body.path("analysis").path("statement").path("totalInflow").decimalValue().toPlainString());
        assertTrue(body.path("analysis").path("categories").path("categories").isArray());
        assertTrue(body.path("analysis").path("monthlyMetrics").isArray());
        verify(service).get("tenant-1", "app-1");

        analysis.setProvider("CLADFY");
        when(service.get("tenant-1", "app-1")).thenReturn(new StatementDtos.StatementAssessmentResponse(
                StatementAnalysisService.toAnalysisResponse(analysis, null, List.of(), null), null, StatementEffectiveOutcome.APPROVED));
        response = mvc.perform(get("/api/v1/applications/app-1/statement-analysis")).andExpect(status().isOk()).andReturn();
        body = new ObjectMapper().readTree(response.getResponse().getContentAsString());
        assertFalse(body.path("analysis").has("statement"));
        assertFalse(body.path("analysis").has("categories"));
        assertFalse(body.path("analysis").has("monthlyMetrics"));
        assertTrue(body.path("analysis").has("transactions"));
        var authorize = StatementAnalysisController.class.getMethod("get", String.class).getAnnotation(PreAuthorize.class);
        assertEquals("hasAuthority('CREDIT_CHECK_VIEW')", authorize.value());
    }
}
