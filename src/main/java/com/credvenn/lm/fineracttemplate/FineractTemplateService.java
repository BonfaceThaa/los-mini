package com.credvenn.lm.fineracttemplate;

import com.credvenn.lm.common.api.PagedResponse;
import com.credvenn.lm.common.api.PaginationSupport;
import com.credvenn.lm.common.exception.BadRequestException;
import com.credvenn.lm.common.exception.ConflictException;
import com.credvenn.lm.common.exception.NotFoundException;
import com.credvenn.lm.fineract.FineractGateway;
import com.credvenn.lm.fineract.FineractProperties;
import com.credvenn.lm.security.CurrentActorService;
import com.credvenn.lm.tenant.TenantService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FineractTemplateService {

    private static final String SOURCE_FINERACT_CREATED = "FINERACT_CREATED";
    private static final String SOURCE_FINERACT_EXISTING = "FINERACT_EXISTING";
    private static final Map<String, String> GL_TEMPLATE_SORTS = new LinkedHashMap<>();
    private static final Map<String, String> ACCOUNTING_RULE_TEMPLATE_SORTS = new LinkedHashMap<>();

    static {
        GL_TEMPLATE_SORTS.put("templateCode", "templateCode");
        GL_TEMPLATE_SORTS.put("displayName", "displayName");
        GL_TEMPLATE_SORTS.put("businessPurpose", "businessPurpose");
        GL_TEMPLATE_SORTS.put("active", "active");
        GL_TEMPLATE_SORTS.put("createdAt", "createdAt");
        GL_TEMPLATE_SORTS.put("updatedAt", "updatedAt");

        ACCOUNTING_RULE_TEMPLATE_SORTS.put("templateCode", "templateCode");
        ACCOUNTING_RULE_TEMPLATE_SORTS.put("displayName", "displayName");
        ACCOUNTING_RULE_TEMPLATE_SORTS.put("businessEvent", "businessEvent");
        ACCOUNTING_RULE_TEMPLATE_SORTS.put("active", "active");
        ACCOUNTING_RULE_TEMPLATE_SORTS.put("createdAt", "createdAt");
        ACCOUNTING_RULE_TEMPLATE_SORTS.put("updatedAt", "updatedAt");
    }

    private final GlAccountTemplateRepository glAccountTemplateRepository;
    private final AccountingRuleTemplateRepository accountingRuleTemplateRepository;
    private final CurrentActorService currentActorService;
    private final FineractGateway fineractGateway;
    private final TenantService tenantService;
    private final FineractProperties properties;

    public FineractTemplateService(
            GlAccountTemplateRepository glAccountTemplateRepository,
            AccountingRuleTemplateRepository accountingRuleTemplateRepository,
            CurrentActorService currentActorService,
            FineractGateway fineractGateway,
            TenantService tenantService,
            FineractProperties properties) {
        this.glAccountTemplateRepository = glAccountTemplateRepository;
        this.accountingRuleTemplateRepository = accountingRuleTemplateRepository;
        this.currentActorService = currentActorService;
        this.fineractGateway = fineractGateway;
        this.tenantService = tenantService;
        this.properties = properties;
    }

    @Transactional
    public FineractTemplateDtos.GlAccountTemplateResponse createGlAccountTemplate(
            FineractTemplateDtos.CreateGlAccountTemplateRequest request) {
        var currentUser = currentActorService.requireCurrentUser();
        String actor = currentUser.username();
        String tenantId = currentUser.tenantId();
        String templateCode = normalizeCode(request.templateCode());
        if (glAccountTemplateRepository.existsByTemplateCodeIgnoreCaseAndTenantId(templateCode, tenantId)) {
            throw new ConflictException("GL account template already exists for template code " + templateCode);
        }
        FineractGateway.CreatedGlAccount createdGlAccount = fineractGateway.createGlAccount(
                tenantService.getRequiredTenant(tenantId),
                new FineractGateway.CreateGlAccountRequest(
                        request.fineractAccount().name().trim(),
                        request.fineractAccount().glCode().trim(),
                        request.fineractAccount().manualEntriesAllowed(),
                        request.fineractAccount().type(),
                        request.fineractAccount().usage(),
                        request.fineractAccount().parentId(),
                        request.fineractAccount().tagId(),
                        trimToNull(request.description())));
        GlAccountTemplate template = new GlAccountTemplate();
        template.setTenantId(tenantId);
        template.setTemplateCode(templateCode);
        template.setDisplayName(request.displayName().trim());
        template.setBusinessPurpose(normalizeCode(request.businessPurpose()));
        template.setSourceType(SOURCE_FINERACT_CREATED);
        template.setManagementMode(normalizeCode(request.managementMode()));
        template.setFineractGlAccountId(createdGlAccount.id());
        template.setFineractGlCode(createdGlAccount.glCode());
        template.setAccountCategory(accountCategory(request.fineractAccount().type()));
        template.setUsageType(usageType(request.fineractAccount().usage()));
        template.setManualEntriesAllowed(request.fineractAccount().manualEntriesAllowed());
        template.setDescription(trimToNull(request.description()));
        template.setActive(true);
        template.setCreatedBy(actor);
        template.setUpdatedBy(actor);
        return FineractTemplateDtos.GlAccountTemplateResponse.from(glAccountTemplateRepository.save(template));
    }

    @Transactional
    public FineractTemplateDtos.AccountingRuleTemplateResponse createAccountingRuleTemplate(
            FineractTemplateDtos.CreateAccountingRuleTemplateRequest request) {
        var currentUser = currentActorService.requireCurrentUser();
        String actor = currentUser.username();
        String tenantId = currentUser.tenantId();
        String templateCode = normalizeCode(request.templateCode());
        if (accountingRuleTemplateRepository.findByTemplateCodeIgnoreCaseAndTenantId(templateCode, tenantId).isPresent()) {
            throw new ConflictException("Accounting rule template already exists for template code " + templateCode);
        }
        GlAccountTemplate debitTemplate = getRequiredGlAccountTemplate(tenantId, request.posting().debitTemplateCode());
        GlAccountTemplate creditTemplate = getRequiredGlAccountTemplate(tenantId, request.posting().creditTemplateCode());
        if (!debitTemplate.isActive()) {
            throw new BadRequestException("Debit GL account template is inactive: " + debitTemplate.getTemplateCode());
        }
        if (!creditTemplate.isActive()) {
            throw new BadRequestException("Credit GL account template is inactive: " + creditTemplate.getTemplateCode());
        }
        if (properties.defaultOfficeId() == null) {
            throw new ConflictException("Fineract default officeId is not configured");
        }
        FineractGateway.CreatedAccountingRule createdAccountingRule = fineractGateway.createAccountingRule(
                tenantService.getRequiredTenant(tenantId),
                new FineractGateway.CreateAccountingRuleRequest(
                        request.displayName().trim(),
                        properties.defaultOfficeId().longValue(),
                        debitTemplate.getFineractGlAccountId(),
                        creditTemplate.getFineractGlAccountId(),
                        trimToNull(request.description())));
        AccountingRuleTemplate template = new AccountingRuleTemplate();
        template.setTenantId(tenantId);
        template.setTemplateCode(templateCode);
        template.setDisplayName(request.displayName().trim());
        template.setBusinessEvent(normalizeCode(request.businessEvent()));
        template.setSourceType(SOURCE_FINERACT_CREATED);
        template.setManagementMode(normalizeCode(request.managementMode()));
        template.setFineractRuleId(createdAccountingRule.id());
        template.setFineractRuleName(createdAccountingRule.name());
        template.setDebitGlAccountTemplateCode(normalizeCode(request.posting().debitTemplateCode()));
        template.setCreditGlAccountTemplateCode(normalizeCode(request.posting().creditTemplateCode()));
        template.setDescription(trimToNull(request.description()));
        template.setActive(true);
        template.setCreatedBy(actor);
        template.setUpdatedBy(actor);
        return FineractTemplateDtos.AccountingRuleTemplateResponse.from(accountingRuleTemplateRepository.save(template));
    }

    @Transactional
    public List<FineractTemplateDtos.GlAccountTemplateResponse> registerExistingGlAccountTemplates(
            String tenantId,
            FineractTemplateDtos.RegisterExistingGlAccountTemplatesRequest request) {
        validateSource(request.source());
        tenantService.getRequiredTenant(tenantId);
        String actor = currentActorService.requireCurrentUser().username();
        return request.templates().stream()
                .map(item -> upsertGlAccountTemplate(tenantId, request.source(), item, actor))
                .map(FineractTemplateDtos.GlAccountTemplateResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FineractTemplateDtos.GlAccountTemplateResponse> listGlAccountTemplates() {
        String tenantId = currentActorService.requireCurrentUser().tenantId();
        return glAccountTemplateRepository.findAllByTenantIdOrderByTemplateCodeAsc(tenantId).stream()
                .map(FineractTemplateDtos.GlAccountTemplateResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PagedResponse<FineractTemplateDtos.GlAccountTemplateResponse> listGlAccountTemplates(
            Integer page,
            Integer size,
            String sortBy,
            String sortDir) {
        Pageable pageable = PaginationSupport.pageable(page, size, sortBy, sortDir, GL_TEMPLATE_SORTS, "templateCode");
        String normalizedSortBy = PaginationSupport.normalizeSortBy(sortBy, GL_TEMPLATE_SORTS, "templateCode");
        String normalizedSortDir = PaginationSupport.normalizeDirectionValue(sortDir);
        String tenantId = currentActorService.requireCurrentUser().tenantId();
        var resultPage = glAccountTemplateRepository.findAllByTenantId(tenantId, pageable).map(FineractTemplateDtos.GlAccountTemplateResponse::from);
        return PagedResponse.fromPage(resultPage, normalizedSortBy, normalizedSortDir);
    }

    @Transactional(readOnly = true)
    public FineractTemplateDtos.GlAccountTemplateResponse getGlAccountTemplate(String templateCode) {
        String tenantId = currentActorService.requireCurrentUser().tenantId();
        return FineractTemplateDtos.GlAccountTemplateResponse.from(getRequiredGlAccountTemplate(tenantId, templateCode));
    }

    @Transactional
    public List<FineractTemplateDtos.AccountingRuleTemplateResponse> registerExistingAccountingRuleTemplates(
            String tenantId,
            FineractTemplateDtos.RegisterExistingAccountingRuleTemplatesRequest request) {
        validateSource(request.source());
        tenantService.getRequiredTenant(tenantId);
        String actor = currentActorService.requireCurrentUser().username();
        return request.templates().stream()
                .map(item -> upsertAccountingRuleTemplate(tenantId, request.source(), item, actor))
                .map(FineractTemplateDtos.AccountingRuleTemplateResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FineractTemplateDtos.AccountingRuleTemplateResponse> listAccountingRuleTemplates() {
        String tenantId = currentActorService.requireCurrentUser().tenantId();
        return accountingRuleTemplateRepository.findAllByTenantIdOrderByTemplateCodeAsc(tenantId).stream()
                .map(FineractTemplateDtos.AccountingRuleTemplateResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PagedResponse<FineractTemplateDtos.AccountingRuleTemplateResponse> listAccountingRuleTemplates(
            Integer page,
            Integer size,
            String sortBy,
            String sortDir) {
        Pageable pageable = PaginationSupport.pageable(page, size, sortBy, sortDir, ACCOUNTING_RULE_TEMPLATE_SORTS, "templateCode");
        String normalizedSortBy = PaginationSupport.normalizeSortBy(sortBy, ACCOUNTING_RULE_TEMPLATE_SORTS, "templateCode");
        String normalizedSortDir = PaginationSupport.normalizeDirectionValue(sortDir);
        String tenantId = currentActorService.requireCurrentUser().tenantId();
        var resultPage = accountingRuleTemplateRepository.findAllByTenantId(tenantId, pageable).map(FineractTemplateDtos.AccountingRuleTemplateResponse::from);
        return PagedResponse.fromPage(resultPage, normalizedSortBy, normalizedSortDir);
    }

    @Transactional(readOnly = true)
    public FineractTemplateDtos.AccountingRuleTemplateResponse getAccountingRuleTemplate(String templateCode) {
        String tenantId = currentActorService.requireCurrentUser().tenantId();
        return FineractTemplateDtos.AccountingRuleTemplateResponse.from(getRequiredAccountingRuleTemplate(tenantId, templateCode));
    }

    private GlAccountTemplate upsertGlAccountTemplate(
            String tenantId,
            String source,
            FineractTemplateDtos.RegisterExistingGlAccountTemplateItem item,
            String actor) {
        String templateCode = normalizeCode(item.templateCode());
        GlAccountTemplate template = glAccountTemplateRepository.findByTemplateCodeIgnoreCaseAndTenantId(templateCode, tenantId)
                .orElseGet(GlAccountTemplate::new);
        glAccountTemplateRepository.findByFineractGlAccountIdAndTenantId(item.fineractAccount().id(), tenantId)
                .filter(existing -> !existing.getTemplateCode().equalsIgnoreCase(templateCode))
                .ifPresent(existing -> {
                    throw new ConflictException("Fineract GL account is already registered under template code " + existing.getTemplateCode());
                });
        template.setTenantId(tenantId);
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
            String tenantId,
            String source,
            FineractTemplateDtos.RegisterExistingAccountingRuleTemplateItem item,
            String actor) {
        String templateCode = normalizeCode(item.templateCode());
        String debitTemplateCode = normalizeCode(item.posting().debitTemplateCode());
        String creditTemplateCode = normalizeCode(item.posting().creditTemplateCode());
        if (!glAccountTemplateRepository.existsByTemplateCodeIgnoreCaseAndTenantId(debitTemplateCode, tenantId)) {
            throw new NotFoundException("Debit GL account template not found: " + debitTemplateCode);
        }
        if (!glAccountTemplateRepository.existsByTemplateCodeIgnoreCaseAndTenantId(creditTemplateCode, tenantId)) {
            throw new NotFoundException("Credit GL account template not found: " + creditTemplateCode);
        }
        AccountingRuleTemplate template = accountingRuleTemplateRepository.findByTemplateCodeIgnoreCaseAndTenantId(templateCode, tenantId)
                .orElseGet(AccountingRuleTemplate::new);
        accountingRuleTemplateRepository.findByFineractRuleIdAndTenantId(item.fineractRule().id(), tenantId)
                .filter(existing -> !existing.getTemplateCode().equalsIgnoreCase(templateCode))
                .ifPresent(existing -> {
                    throw new ConflictException("Fineract accounting rule is already registered under template code " + existing.getTemplateCode());
                });
        template.setTenantId(tenantId);
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

    private GlAccountTemplate getRequiredGlAccountTemplate(String tenantId, String templateCode) {
        return glAccountTemplateRepository.findByTemplateCodeIgnoreCaseAndTenantId(templateCode, tenantId)
                .orElseThrow(() -> new NotFoundException("GL account template not found"));
    }

    private AccountingRuleTemplate getRequiredAccountingRuleTemplate(String tenantId, String templateCode) {
        return accountingRuleTemplateRepository.findByTemplateCodeIgnoreCaseAndTenantId(templateCode, tenantId)
                .orElseThrow(() -> new NotFoundException("Accounting rule template not found"));
    }

    private void validateSource(String source) {
        if (!SOURCE_FINERACT_EXISTING.equalsIgnoreCase(source == null ? "" : source.trim())) {
            throw new ConflictException("Only FINERACT_EXISTING source is supported for this endpoint");
        }
    }

    private String accountCategory(Integer type) {
        return switch (type == null ? -1 : type) {
            case 1 -> "ASSET";
            case 2 -> "LIABILITY";
            case 3 -> "EQUITY";
            case 4 -> "INCOME";
            case 5 -> "EXPENSE";
            default -> throw new ConflictException("Unsupported Fineract GL account type: " + type);
        };
    }

    private String usageType(Integer usage) {
        return switch (usage == null ? -1 : usage) {
            case 1 -> "DETAIL";
            case 2 -> "HEADER";
            default -> throw new ConflictException("Unsupported Fineract GL account usage: " + usage);
        };
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
