package com.credvenn.lm.fineracttemplate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

public final class FineractTemplateDtos {

    private FineractTemplateDtos() {
    }

    @Schema(name = "RegisterExistingGlAccountTemplatesRequest")
    public record RegisterExistingGlAccountTemplatesRequest(
            @NotBlank String source,
            @NotEmpty List<@Valid RegisterExistingGlAccountTemplateItem> templates) {
    }

    @Schema(name = "RegisterExistingGlAccountTemplateItem")
    public record RegisterExistingGlAccountTemplateItem(
            @NotBlank String templateCode,
            @NotBlank String displayName,
            @NotBlank String businessPurpose,
            @NotBlank String managementMode,
            @NotNull @Valid ExistingFineractGlAccount fineractAccount,
            @NotNull @Valid GlAccountDefaults defaults,
            String description) {
    }

    @Schema(name = "ExistingFineractGlAccount")
    public record ExistingFineractGlAccount(
            @NotNull Long id,
            @NotBlank String glCode) {
    }

    @Schema(name = "GlAccountDefaults")
    public record GlAccountDefaults(
            @NotBlank String accountCategory,
            @NotBlank String usageType,
            @NotNull Boolean manualEntriesAllowed) {
    }

    @Schema(name = "GlAccountTemplateResponse")
    public record GlAccountTemplateResponse(
            String id,
            String templateCode,
            String displayName,
            String businessPurpose,
            String sourceType,
            String managementMode,
            Long fineractGlAccountId,
            String fineractGlCode,
            String accountCategory,
            String usageType,
            boolean manualEntriesAllowed,
            String description,
            boolean active,
            String createdBy,
            String updatedBy,
            Instant createdAt,
            Instant updatedAt) {

        static GlAccountTemplateResponse from(GlAccountTemplate template) {
            return new GlAccountTemplateResponse(
                    template.getId(),
                    template.getTemplateCode(),
                    template.getDisplayName(),
                    template.getBusinessPurpose(),
                    template.getSourceType(),
                    template.getManagementMode(),
                    template.getFineractGlAccountId(),
                    template.getFineractGlCode(),
                    template.getAccountCategory(),
                    template.getUsageType(),
                    template.isManualEntriesAllowed(),
                    template.getDescription(),
                    template.isActive(),
                    template.getCreatedBy(),
                    template.getUpdatedBy(),
                    template.getCreatedAt(),
                    template.getUpdatedAt());
        }
    }

    @Schema(name = "RegisterExistingAccountingRuleTemplatesRequest")
    public record RegisterExistingAccountingRuleTemplatesRequest(
            @NotBlank String source,
            @NotEmpty List<@Valid RegisterExistingAccountingRuleTemplateItem> templates) {
    }

    @Schema(name = "RegisterExistingAccountingRuleTemplateItem")
    public record RegisterExistingAccountingRuleTemplateItem(
            @NotBlank String templateCode,
            @NotBlank String displayName,
            @NotBlank String businessEvent,
            @NotBlank String managementMode,
            @NotNull @Valid ExistingFineractAccountingRule fineractRule,
            @NotNull @Valid AccountingRulePosting posting,
            String description) {
    }

    @Schema(name = "ExistingFineractAccountingRule")
    public record ExistingFineractAccountingRule(
            @NotNull Long id,
            @NotBlank String name) {
    }

    @Schema(name = "AccountingRulePosting")
    public record AccountingRulePosting(
            @NotBlank String debitTemplateCode,
            @NotBlank String creditTemplateCode) {
    }

    @Schema(name = "AccountingRuleTemplateResponse")
    public record AccountingRuleTemplateResponse(
            String id,
            String templateCode,
            String displayName,
            String businessEvent,
            String sourceType,
            String managementMode,
            Long fineractRuleId,
            String fineractRuleName,
            String debitGlAccountTemplateCode,
            String creditGlAccountTemplateCode,
            String description,
            boolean active,
            String createdBy,
            String updatedBy,
            Instant createdAt,
            Instant updatedAt) {

        static AccountingRuleTemplateResponse from(AccountingRuleTemplate template) {
            return new AccountingRuleTemplateResponse(
                    template.getId(),
                    template.getTemplateCode(),
                    template.getDisplayName(),
                    template.getBusinessEvent(),
                    template.getSourceType(),
                    template.getManagementMode(),
                    template.getFineractRuleId(),
                    template.getFineractRuleName(),
                    template.getDebitGlAccountTemplateCode(),
                    template.getCreditGlAccountTemplateCode(),
                    template.getDescription(),
                    template.isActive(),
                    template.getCreatedBy(),
                    template.getUpdatedBy(),
                    template.getCreatedAt(),
                    template.getUpdatedAt());
        }
    }
}
