package com.credvenn.lm.fineracttemplate;

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

    @PostMapping("/gl-account-templates/register-existing")
    @PreAuthorize("hasAuthority('TENANT_MANAGE_ALL')")
    @Operation(summary = "Register existing Fineract GL accounts as Mini-LOS standard templates")
    public ResponseEntity<List<FineractTemplateDtos.GlAccountTemplateResponse>> registerExistingGlAccountTemplates(
            @Valid @RequestBody FineractTemplateDtos.RegisterExistingGlAccountTemplatesRequest request) {
        return ResponseEntity.ok(fineractTemplateService.registerExistingGlAccountTemplates(request));
    }

    @GetMapping("/gl-account-templates")
    @PreAuthorize("hasAuthority('GL_ACCOUNT_VIEW')")
    @Operation(summary = "List registered Mini-LOS GL account templates")
    public ResponseEntity<List<FineractTemplateDtos.GlAccountTemplateResponse>> listGlAccountTemplates() {
        return ResponseEntity.ok(fineractTemplateService.listGlAccountTemplates());
    }

    @GetMapping("/gl-account-templates/{templateCode}")
    @PreAuthorize("hasAuthority('GL_ACCOUNT_VIEW')")
    @Operation(summary = "Get a registered Mini-LOS GL account template")
    public ResponseEntity<FineractTemplateDtos.GlAccountTemplateResponse> getGlAccountTemplate(@PathVariable String templateCode) {
        return ResponseEntity.ok(fineractTemplateService.getGlAccountTemplate(templateCode));
    }

    @PostMapping("/accounting-rule-templates/register-existing")
    @PreAuthorize("hasAuthority('TENANT_MANAGE_ALL')")
    @Operation(summary = "Register existing Fineract accounting rules as Mini-LOS standard templates")
    public ResponseEntity<List<FineractTemplateDtos.AccountingRuleTemplateResponse>> registerExistingAccountingRuleTemplates(
            @Valid @RequestBody FineractTemplateDtos.RegisterExistingAccountingRuleTemplatesRequest request) {
        return ResponseEntity.ok(fineractTemplateService.registerExistingAccountingRuleTemplates(request));
    }

    @GetMapping("/accounting-rule-templates")
    @PreAuthorize("hasAuthority('TENANT_MANAGE_ALL')")
    @Operation(summary = "List registered Mini-LOS accounting rule templates")
    public ResponseEntity<List<FineractTemplateDtos.AccountingRuleTemplateResponse>> listAccountingRuleTemplates() {
        return ResponseEntity.ok(fineractTemplateService.listAccountingRuleTemplates());
    }

    @GetMapping("/accounting-rule-templates/{templateCode}")
    @PreAuthorize("hasAuthority('TENANT_MANAGE_ALL')")
    @Operation(summary = "Get a registered Mini-LOS accounting rule template")
    public ResponseEntity<FineractTemplateDtos.AccountingRuleTemplateResponse> getAccountingRuleTemplate(@PathVariable String templateCode) {
        return ResponseEntity.ok(fineractTemplateService.getAccountingRuleTemplate(templateCode));
    }
}
