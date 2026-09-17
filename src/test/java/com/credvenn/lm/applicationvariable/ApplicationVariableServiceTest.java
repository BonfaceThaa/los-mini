package com.credvenn.lm.applicationvariable;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.credvenn.lm.common.exception.BadRequestException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ApplicationVariableServiceTest {
    private final ApplicationVariableDefinitionRepository definitions = mock(ApplicationVariableDefinitionRepository.class);
    private final ApplicationVariableRepository variables = mock(ApplicationVariableRepository.class);
    private final ObjectMapper json = new ObjectMapper();
    private final ApplicationVariableService service = new ApplicationVariableService(definitions, variables, json);

    @Test
    void preparesTextSingleAndMultipleAnswers() throws Exception {
        var name = definition("name", "NAME", ApplicationVariableFieldType.TEXT, true, "[]");
        var relationship = definition("relationship", "RELATIONSHIP", ApplicationVariableFieldType.SINGLE_SELECT, true,
                "[{\"value\":\"PARENT\",\"label\":\"Parent\"}]");
        var income = definition("income", "INCOME_SOURCES", ApplicationVariableFieldType.MULTI_SELECT, true,
                "[{\"value\":\"SALARY\",\"label\":\"Salary\"},{\"value\":\"FARMING\",\"label\":\"Farming\"}]");
        income.setMinimumSelections(1); income.setMaximumSelections(2);
        when(definitions.findAllByTenantIdAndActiveTrueOrderByDisplayOrderAsc("tenant-1")).thenReturn(List.of(name, relationship, income));

        var prepared = service.prepare("tenant-1", List.of(
                new ApplicationVariableDtos.AnswerRequest("name", " Jane Doe ", List.of()),
                new ApplicationVariableDtos.AnswerRequest("relationship", null, List.of("PARENT")),
                new ApplicationVariableDtos.AnswerRequest("income", null, List.of("SALARY", "FARMING"))));

        assertEquals("Jane Doe", prepared.get(0).snapshot().textValue());
        assertEquals(List.of("SALARY", "FARMING"), prepared.get(2).snapshot().selectedValues().stream().map(ApplicationVariableDtos.Option::value).toList());
    }

    @Test
    void rejectsMissingRequiredQuestion() {
        when(definitions.findAllByTenantIdAndActiveTrueOrderByDisplayOrderAsc("tenant-1"))
                .thenReturn(List.of(definition("required", "REQUIRED", ApplicationVariableFieldType.TEXT, true, "[]")));
        assertThrows(BadRequestException.class, () -> service.prepare("tenant-1", List.of()));
    }

    @Test
    void rejectsUnknownCrossTenantDefinition() {
        when(definitions.findAllByTenantIdAndActiveTrueOrderByDisplayOrderAsc("tenant-1")).thenReturn(List.of());
        assertThrows(BadRequestException.class, () -> service.prepare("tenant-1",
                List.of(new ApplicationVariableDtos.AnswerRequest("other-tenant-id", "value", List.of()))));
    }

    @Test
    void rejectsInvalidSingleAndMultiSelections() {
        var single = definition("single", "SINGLE", ApplicationVariableFieldType.SINGLE_SELECT, false,
                "[{\"value\":\"YES\",\"label\":\"Yes\"}]");
        var multi = definition("multi", "MULTI", ApplicationVariableFieldType.MULTI_SELECT, false,
                "[{\"value\":\"A\",\"label\":\"A\"},{\"value\":\"B\",\"label\":\"B\"}]");
        multi.setMaximumSelections(1);
        when(definitions.findAllByTenantIdAndActiveTrueOrderByDisplayOrderAsc("tenant-1")).thenReturn(List.of(single, multi));
        assertThrows(BadRequestException.class, () -> service.prepare("tenant-1",
                List.of(new ApplicationVariableDtos.AnswerRequest("single", null, List.of("YES", "NO")))));
        assertThrows(BadRequestException.class, () -> service.prepare("tenant-1",
                List.of(new ApplicationVariableDtos.AnswerRequest("multi", null, List.of("A", "B")))));
    }

    @Test
    void persistsDefinitionAndAnswerSnapshots() {
        var definition = definition("relationship", "RELATIONSHIP", ApplicationVariableFieldType.SINGLE_SELECT, true,
                "[{\"value\":\"PARENT\",\"label\":\"Parent\"}]");
        when(definitions.findAllByTenantIdAndActiveTrueOrderByDisplayOrderAsc("tenant-1")).thenReturn(List.of(definition));
        when(variables.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var prepared = service.prepare("tenant-1", List.of(
                new ApplicationVariableDtos.AnswerRequest("relationship", null, List.of("PARENT"))));

        service.savePrepared("tenant-1", "app-1", prepared);

        ArgumentCaptor<List<ApplicationVariable>> captor = ArgumentCaptor.forClass(List.class);
        verify(variables).saveAll(captor.capture());
        ApplicationVariable saved = captor.getValue().getFirst();
        assertEquals("tenant-1", saved.getTenantId());
        assertEquals("app-1", saved.getApplicationId());
        assertEquals("RELATIONSHIP", saved.getCodeSnapshot());
        assertTrue(saved.getAnswerValue().contains("Parent"));
    }

    private ApplicationVariableDefinition definition(String id, String code, ApplicationVariableFieldType type,
            boolean required, String optionsJson) {
        var value = new ApplicationVariableDefinition();
        org.springframework.test.util.ReflectionTestUtils.setField(value, "id", id);
        value.setTenantId("tenant-1"); value.setCode(code); value.setLabel(code); value.setFieldType(type);
        value.setRequired(required); value.setActive(true); value.setOptionsJson(optionsJson);
        return value;
    }
}