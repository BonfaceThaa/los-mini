package com.credvenn.lm.scoring;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.credvenn.lm.applicationvariable.*;
import com.credvenn.lm.common.exception.*;
import com.credvenn.lm.origination.*;
import com.credvenn.lm.security.*;
import com.credvenn.lm.tenant.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ScorecardServiceTest {
    @Test void publishCopiesQuestionsAndRejectsEditsToPublishedVersion() {
        var versions = mock(ScorecardVersionRepository.class);
        var profiles = mock(OriginationProfileRepository.class);
        var tenants = mock(TenantRepository.class);
        var questions = mock(ApplicationVariableService.class);
        var actors = mock(CurrentActorService.class);
        var jdbc = mock(JdbcTemplate.class);
        var actor = new AuthenticatedUser("user", "tenant", "admin", null, List.of(), List.of());
        when(actors.requireCurrentUser()).thenReturn(actor);
        when(tenants.findForOriginationUpdate("tenant")).thenReturn(Optional.of(new Tenant()));
        var profile = new OriginationProfile(); profile.setId("profile"); profile.setCode("PHONE");
        when(profiles.findByTenantIdAndCodeIgnoreCase("tenant", "PHONE")).thenReturn(Optional.of(profile));
        var version = new ScorecardVersion(); version.setId("v1"); version.setTenantId("tenant");
        version.setOriginationProfileId("profile"); version.setVersionNumber(1); version.setStatus("DRAFT");
        version.setCreatedBy("admin"); version.setUpdatedBy("admin");
        var json = new ObjectMapper();
        var validator = mock(StatementPolicyValidator.class);
        var draft = new ScorecardDtos.Draft(new BigDecimal("60"), new BigDecimal("40"),
                List.of(new ScorecardDtos.QuestionRule("q1", null)));
        try { version.setDraftJson(json.writeValueAsString(draft)); } catch (Exception e) { throw new AssertionError(e); }
        when(versions.findByTenantIdAndOriginationProfileIdAndId("tenant", "profile", "v1")).thenReturn(Optional.of(version));
        var option = new ApplicationVariableDtos.Option("YES", "Yes", new BigDecimal("90"));
        var q = new ApplicationVariableDtos.DefinitionResponse("q1", "Q1", "Question", null,
                ApplicationVariableFieldType.SINGLE_SELECT, true, true, 0, 1, null, null, List.of(option), "profile", BigDecimal.TEN);
        when(questions.listAll("tenant", true, "PHONE")).thenReturn(List.of(q));
        when(jdbc.update(startsWith("UPDATE"), any(), any(), any())).thenReturn(1);
        var service = new ScorecardService(versions, profiles, tenants, questions, actors, json, jdbc, validator);
        var result = service.publish("PHONE", "v1");
        assertEquals("PUBLISHED", result.status());
        assertEquals(new BigDecimal("90"), result.snapshot().questions().get(0).options().get(0).points());
        assertThrows(ConflictException.class, () -> service.update("PHONE", "v1", draft));
        assertThrows(NotFoundException.class, () -> service.get("OTHER_TENANT_PROFILE", "v1"));
    }
}
