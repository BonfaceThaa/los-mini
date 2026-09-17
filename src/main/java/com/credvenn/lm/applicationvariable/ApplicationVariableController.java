package com.credvenn.lm.applicationvariable;

import com.credvenn.lm.security.CurrentActorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/application-variable-definitions")
@Tag(name = "Application Questionnaire", description = "Tenant-managed additional questions used when creating loan applications")
@SecurityRequirement(name = "bearerAuth")
public class ApplicationVariableController {
    private final ApplicationVariableService service;
    private final CurrentActorService currentActorService;

    public ApplicationVariableController(ApplicationVariableService service, CurrentActorService currentActorService) {
        this.service = service; this.currentActorService = currentActorService;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('LOAN_CREATE','APPLICATION_VARIABLE_MANAGE')")
    @Operation(summary = "Get the tenant application questionnaire", description = "Returns questions in display order. Use activeOnly=true to render a loan request form.")
    public ResponseEntity<List<ApplicationVariableDtos.DefinitionResponse>> list(
            @Parameter(description = "Exclude inactive questions") @RequestParam(defaultValue = "true") boolean activeOnly) {
        return ResponseEntity.ok(service.list(currentActorService.requireCurrentUser().tenantId(), activeOnly));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('APPLICATION_VARIABLE_MANAGE')")
    @Operation(summary = "Create an application question")
    public ResponseEntity<ApplicationVariableDtos.DefinitionResponse> create(
            @Valid @RequestBody ApplicationVariableDtos.DefinitionRequest request) {
        return ResponseEntity.ok(service.create(currentActorService.requireCurrentUser().tenantId(), request));
    }

    @PutMapping("/{definitionId}")
    @PreAuthorize("hasAuthority('APPLICATION_VARIABLE_MANAGE')")
    @Operation(summary = "Update an application question", description = "The stable question code cannot change. Updating increments the definition version; submitted answers retain snapshots.")
    public ResponseEntity<ApplicationVariableDtos.DefinitionResponse> update(@PathVariable String definitionId,
            @Valid @RequestBody ApplicationVariableDtos.DefinitionRequest request) {
        return ResponseEntity.ok(service.update(currentActorService.requireCurrentUser().tenantId(), definitionId, request));
    }

    @PostMapping("/{definitionId}/activate")
    @PreAuthorize("hasAuthority('APPLICATION_VARIABLE_MANAGE')")
    @Operation(summary = "Activate an application question")
    public ResponseEntity<ApplicationVariableDtos.DefinitionResponse> activate(@PathVariable String definitionId) {
        return ResponseEntity.ok(service.setActive(currentActorService.requireCurrentUser().tenantId(), definitionId, true));
    }

    @PostMapping("/{definitionId}/deactivate")
    @PreAuthorize("hasAuthority('APPLICATION_VARIABLE_MANAGE')")
    @Operation(summary = "Deactivate an application question")
    public ResponseEntity<ApplicationVariableDtos.DefinitionResponse> deactivate(@PathVariable String definitionId) {
        return ResponseEntity.ok(service.setActive(currentActorService.requireCurrentUser().tenantId(), definitionId, false));
    }
}