package com.credvenn.lm.applicationvariable;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public final class ApplicationVariableDtos {
    private ApplicationVariableDtos() {}

    @Schema(name = "ApplicationVariableOption")
    public record Option(
            @NotBlank @Size(max = 100) @Schema(example = "PARENT") String value,
            @NotBlank @Size(max = 255) @Schema(example = "Parent") String label) {}

    @Schema(name = "UpsertApplicationVariableDefinitionRequest")
    public record DefinitionRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]*") @Size(max = 100) @Schema(example = "NEXT_OF_KIN_RELATIONSHIP") String code,
            @NotBlank @Size(max = 255) @Schema(example = "What is your relationship with your next of kin?") String label,
            @Size(max = 255) @Schema(example = "Next of Kin") String sectionName,
            @NotNull @Schema(example = "SINGLE_SELECT") ApplicationVariableFieldType fieldType,
            boolean required,
            @PositiveOrZero Integer displayOrder,
            @PositiveOrZero Integer minimumSelections,
            @Positive Integer maximumSelections,
            @Valid List<Option> options) {}

    @Schema(name = "ApplicationVariableDefinitionResponse")
    public record DefinitionResponse(String id, String code, String label, String sectionName,
            ApplicationVariableFieldType fieldType, boolean required, boolean active, int displayOrder,
            int definitionVersion, Integer minimumSelections, Integer maximumSelections, List<Option> options) {}

    @Schema(name = "ApplicationVariableAnswerRequest")
    public record AnswerRequest(
            @NotBlank String definitionId,
            @Size(max = 10000) @Schema(example = "John Doe", description = "Used only for TEXT and TEXTAREA questions") String textValue,
            @Schema(description = "Option values; exactly one for SINGLE_SELECT and one or more for MULTI_SELECT") List<@NotBlank @Size(max = 100) String> selectedValues) {}

    @Schema(name = "ApplicationVariableAnswerResponse")
    public record AnswerResponse(String definitionId, int definitionVersion, String code, String label,
            String sectionName, ApplicationVariableFieldType fieldType, String textValue,
            List<Option> selectedValues) {}
}