package com.credvenn.lm.applicationvariable;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.credvenn.lm.security.*;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class ApplicationVariableScoringVisibilityTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void loanCreatorCannotReadAnswerPointsOrQuestionWeight() {
        var service = mock(ApplicationVariableService.class);
        var actors = mock(CurrentActorService.class);
        var user = new AuthenticatedUser("u", "tenant", "user", null, List.of(), List.of());
        when(actors.requireCurrentUser()).thenReturn(user);
        var option = new ApplicationVariableDtos.Option("SALARY", "Salary", BigDecimal.valueOf(90));
        var response = new ApplicationVariableDtos.DefinitionResponse("q", "INCOME", "Income", null,
                ApplicationVariableFieldType.SINGLE_SELECT, true, true, 0, 1, null, null, List.of(option), null, BigDecimal.TEN);
        when(service.list("tenant", true, null)).thenReturn(List.of(response));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null,
                List.of(new SimpleGrantedAuthority("LOAN_CREATE"))));
        var controller = new ApplicationVariableController(service, actors);
        var visible = controller.list(true, null).getBody().get(0);
        assertNull(visible.scoringWeight());
        assertNull(visible.options().get(0).points());
        assertEquals("SALARY", visible.options().get(0).value());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null,
                List.of(new SimpleGrantedAuthority("APPLICATION_VARIABLE_MANAGE"))));
        var administrator = controller.list(true, null).getBody().get(0);
        assertEquals(BigDecimal.TEN, administrator.scoringWeight());
        assertEquals(BigDecimal.valueOf(90), administrator.options().get(0).points());
    }
}
