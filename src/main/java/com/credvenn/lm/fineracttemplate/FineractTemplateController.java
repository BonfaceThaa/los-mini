package com.credvenn.lm.fineracttemplate;

import com.credvenn.lm.common.api.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform")
@Tag(name = "Fineract Standard Templates")
@SecurityRequirement(name = "bearerAuth")
public class FineractTemplateController {

    private final FineractTemplateService fineractTemplateService;

    public FineractTemplateController(FineractTemplateService fineractTemplateService) {
        this.fineractTemplateService = fineractTemplateService;
    }

    @PostMapping("/gl-account-templates")
    @PreAuthorize("hasAuthority('GL_ACCOUNT_CREATE')")
    @Operation(summary = "Create a Fineract GL account and register it as a Mini-LOS standard template")
    public ResponseEntity<FineractTemplateDtos.GlAccountTemplateResponse> createGlAccountTemplate(
            @Valid @RequestBody FineractTemplateDtos.CreateGlAccountTemplateRequest request) {
        return ResponseEntity.ok(fineractTemplateService.createGlAccountTemplate(request));
    }

    @PostMapping("/tenants/{tenantId}/gl-account-templates/register-existing")
    @PreAuthorize("hasAuthority('TENANT_MANAGE_ALL')")
    @Operation(summary = "Register existing Fineract GL accounts as Mini-LOS standard templates")
    public ResponseEntity<List<FineractTemplateDtos.GlAccountTemplateResponse>> registerExistingGlAccountTemplates(
            @PathVariable String tenantId,
            @Valid @RequestBody FineractTemplateDtos.RegisterExistingGlAccountTemplatesRequest request) {
        return ResponseEntity.ok(fineractTemplateService.registerExistingGlAccountTemplates(tenantId, request));
    }

    @GetMapping("/gl-account-templates")
    @PreAuthorize("hasAuthority('GL_ACCOUNT_VIEW')")
    @Operation(summary = "List registered Mini-LOS GL account templates")
    public ResponseEntity<PagedResponse<FineractTemplateDtos.GlAccountTemplateResponse>> listGlAccountTemplates(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(defaultValue = "templateCode") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        return ResponseEntity.ok(fineractTemplateService.listGlAccountTemplates(page, size, sortBy, sortDir));
    }

    @GetMapping("/gl-account-templates/{templateCode}")
    @PreAuthorize("hasAuthority('GL_ACCOUNT_VIEW')")
    @Operation(summary = "Get a registered Mini-LOS GL account template")
    public ResponseEntity<FineractTemplateDtos.GlAccountTemplateResponse> getGlAccountTemplate(@PathVariable String templateCode) {
        return ResponseEntity.ok(fineractTemplateService.getGlAccountTemplate(templateCode));
    }

    @PostMapping("/accounting-rule-templates")
    @PreAuthorize("hasAuthority('ACCOUNTING_RULE_CREATE')")
    @Operation(summary = "Create a Fineract accounting rule and register it as a Mini-LOS standard template")
    public ResponseEntity<FineractTemplateDtos.AccountingRuleTemplateResponse> createAccountingRuleTemplate(
            @Valid @RequestBody FineractTemplateDtos.CreateAccountingRuleTemplateRequest request) {
        return ResponseEntity.ok(fineractTemplateService.createAccountingRuleTemplate(request));
    }

    @PostMapping("/tenants/{tenantId}/accounting-rule-templates/register-existing")
    @PreAuthorize("hasAuthority('TENANT_MANAGE_ALL')")
    @Operation(summary = "Register existing Fineract accounting rules as Mini-LOS standard templates")
    public ResponseEntity<List<FineractTemplateDtos.AccountingRuleTemplateResponse>> registerExistingAccountingRuleTemplates(
            @PathVariable String tenantId,
            @Valid @RequestBody FineractTemplateDtos.RegisterExistingAccountingRuleTemplatesRequest request) {
        return ResponseEntity.ok(fineractTemplateService.registerExistingAccountingRuleTemplates(tenantId, request));
    }

    @GetMapping("/accounting-rule-templates")
    @PreAuthorize("hasAuthority('ACCOUNTING_RULE_VIEW')")
    @Operation(summary = "List registered Mini-LOS accounting rule templates")
    public ResponseEntity<PagedResponse<FineractTemplateDtos.AccountingRuleTemplateResponse>> listAccountingRuleTemplates(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(defaultValue = "templateCode") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        return ResponseEntity.ok(fineractTemplateService.listAccountingRuleTemplates(page, size, sortBy, sortDir));
    }

    @GetMapping("/accounting-rule-templates/{templateCode}")
    @PreAuthorize("hasAuthority('ACCOUNTING_RULE_VIEW')")
    @Operation(summary = "Get a registered Mini-LOS accounting rule template")
    public ResponseEntity<FineractTemplateDtos.AccountingRuleTemplateResponse> getAccountingRuleTemplate(@PathVariable String templateCode) {
        return ResponseEntity.ok(fineractTemplateService.getAccountingRuleTemplate(templateCode));
    }
}
