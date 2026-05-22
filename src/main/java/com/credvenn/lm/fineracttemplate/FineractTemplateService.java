package com.credvenn.lm.fineracttemplate;

import com.credvenn.lm.common.exception.ConflictException;
import com.credvenn.lm.common.exception.NotFoundException;
import com.credvenn.lm.security.CurrentActorService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FineractTemplateService {

    private static final String SOURCE_FINERACT_EXISTING = "FINERACT_EXISTING";

    private final GlAccountTemplateRepository glAccountTemplateRepository;
    private final AccountingRuleTemplateRepository accountingRuleTemplateRepository;
    private final CurrentActorService currentActorService;

    public FineractTemplateService(
            GlAccountTemplateRepository glAccountTemplateRepository,
            AccountingRuleTemplateRepository accountingRuleTemplateRepository,
            CurrentActorService currentActorService) {
        this.glAccountTemplateRepository = glAccountTemplateRepository;
        this.accountingRuleTemplateRepository = accountingRuleTemplateRepository;
        this.currentActorService = currentActorService;
    }

    @Transactional
    public List<FineractTemplateDtos.GlAccountTemplateResponse> registerExistingGlAccountTemplates(
            FineractTemplateDtos.RegisterExistingGlAccountTemplatesRequest request) {
        validateSource(request.source());
        String actor = currentActorService.requireCurrentUser().username();
        return request.templates().stream()
                .map(item -> upsertGlAccountTemplate(request.source(), item, actor))
                .map(FineractTemplateDtos.GlAccountTemplateResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FineractTemplateDtos.GlAccountTemplateResponse> listGlAccountTemplates() {
        return glAccountTemplateRepository.findAllByOrderByTemplateCodeAsc().stream()
                .map(FineractTemplateDtos.GlAccountTemplateResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public FineractTemplateDtos.GlAccountTemplateResponse getGlAccountTemplate(String templateCode) {
        return FineractTemplateDtos.GlAccountTemplateResponse.from(getRequiredGlAccountTemplate(templateCode));
    }

    @Transactional
    public List<FineractTemplateDtos.AccountingRuleTemplateResponse> registerExistingAccountingRuleTemplates(
            FineractTemplateDtos.RegisterExistingAccountingRuleTemplatesRequest request) {
        validateSource(request.source());
        String actor = currentActorService.requireCurrentUser().username();
        return request.templates().stream()
                .map(item -> upsertAccountingRuleTemplate(request.source(), item, actor))
                .map(FineractTemplateDtos.AccountingRuleTemplateResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FineractTemplateDtos.AccountingRuleTemplateResponse> listAccountingRuleTemplates() {
        return accountingRuleTemplateRepository.findAllByOrderByTemplateCodeAsc().stream()
                .map(FineractTemplateDtos.AccountingRuleTemplateResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public FineractTemplateDtos.AccountingRuleTemplateResponse getAccountingRuleTemplate(String templateCode) {
        return FineractTemplateDtos.AccountingRuleTemplateResponse.from(getRequiredAccountingRuleTemplate(templateCode));
    }

    private GlAccountTemplate upsertGlAccountTemplate(
            String source,
            FineractTemplateDtos.RegisterExistingGlAccountTemplateItem item,
            String actor) {
        String templateCode = normalizeCode(item.templateCode());
        GlAccountTemplate template = glAccountTemplateRepository.findByTemplateCodeIgnoreCase(templateCode)
                .orElseGet(GlAccountTemplate::new);
        glAccountTemplateRepository.findByFineractGlAccountId(item.fineractAccount().id())
                .filter(existing -> !existing.getTemplateCode().equalsIgnoreCase(templateCode))
                .ifPresent(existing -> {
                    throw new ConflictException("Fineract GL account is already registered under template code " + existing.getTemplateCode());
                });
        template.setTemplateCode(templateCode);
        template.setDisplayName(item.displayName().trim());
        template.setBusinessPurpose(normalizeCode(item.businessPurpose()));
        template.setSourceType(normalizeCode(source));
        template.setManagementMode(normalizeCode(item.managementMode()));
        template.setFineractGlAccountId(item.fineractAccount().id());
        template.setFineractGlCode(item.fineractAccount().glCode().trim());
        template.setAccountCategory(normalizeCode(item.defaults().accountCategory()));
        template.setUsageType(normalizeCode(item.defaults().usageType()));
        template.setManualEntriesAllowed(item.defaults().manualEntriesAllowed());
        template.setDescription(trimToNull(item.description()));
        template.setActive(true);
        if (template.getCreatedBy() == null) {
            template.setCreatedBy(actor);
        }
        template.setUpdatedBy(actor);
        return glAccountTemplateRepository.save(template);
    }

    private AccountingRuleTemplate upsertAccountingRuleTemplate(
            String source,
            FineractTemplateDtos.RegisterExistingAccountingRuleTemplateItem item,
            String actor) {
        String templateCode = normalizeCode(item.templateCode());
        String debitTemplateCode = normalizeCode(item.posting().debitTemplateCode());
        String creditTemplateCode = normalizeCode(item.posting().creditTemplateCode());
        if (!glAccountTemplateRepository.existsByTemplateCodeIgnoreCase(debitTemplateCode)) {
            throw new NotFoundException("Debit GL account template not found: " + debitTemplateCode);
        }
        if (!glAccountTemplateRepository.existsByTemplateCodeIgnoreCase(creditTemplateCode)) {
            throw new NotFoundException("Credit GL account template not found: " + creditTemplateCode);
        }
        AccountingRuleTemplate template = accountingRuleTemplateRepository.findByTemplateCodeIgnoreCase(templateCode)
                .orElseGet(AccountingRuleTemplate::new);
        accountingRuleTemplateRepository.findByFineractRuleId(item.fineractRule().id())
                .filter(existing -> !existing.getTemplateCode().equalsIgnoreCase(templateCode))
                .ifPresent(existing -> {
                    throw new ConflictException("Fineract accounting rule is already registered under template code " + existing.getTemplateCode());
                });
        template.setTemplateCode(templateCode);
        template.setDisplayName(item.displayName().trim());
        template.setBusinessEvent(normalizeCode(item.businessEvent()));
        template.setSourceType(normalizeCode(source));
        template.setManagementMode(normalizeCode(item.managementMode()));
        template.setFineractRuleId(item.fineractRule().id());
        template.setFineractRuleName(item.fineractRule().name().trim());
        template.setDebitGlAccountTemplateCode(debitTemplateCode);
        template.setCreditGlAccountTemplateCode(creditTemplateCode);
        template.setDescription(trimToNull(item.description()));
        template.setActive(true);
        if (template.getCreatedBy() == null) {
            template.setCreatedBy(actor);
        }
        template.setUpdatedBy(actor);
        return accountingRuleTemplateRepository.save(template);
    }

    private GlAccountTemplate getRequiredGlAccountTemplate(String templateCode) {
        return glAccountTemplateRepository.findByTemplateCodeIgnoreCase(templateCode)
                .orElseThrow(() -> new NotFoundException("GL account template not found"));
    }

    private AccountingRuleTemplate getRequiredAccountingRuleTemplate(String templateCode) {
        return accountingRuleTemplateRepository.findByTemplateCodeIgnoreCase(templateCode)
                .orElseThrow(() -> new NotFoundException("Accounting rule template not found"));
    }

    private void validateSource(String source) {
        if (!SOURCE_FINERACT_EXISTING.equalsIgnoreCase(source == null ? "" : source.trim())) {
            throw new ConflictException("Only FINERACT_EXISTING source is supported for this endpoint");
        }
    }

    private String normalizeCode(String value) {
        return value.trim().toUpperCase();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
