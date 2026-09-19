package com.credvenn.lm.applicationvariable;

import com.credvenn.lm.common.exception.*;
import com.credvenn.lm.origination.*;
import com.credvenn.lm.tenant.TenantRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicationVariableService {
    private final ApplicationVariableDefinitionRepository definitions;
    private final ApplicationVariableRepository variables;
    private final ObjectMapper objectMapper;
    private final OriginationProfileRepository profiles;
    private final OriginationProfileResolver profileResolver;
    private final OriginationProfileValidator profileValidator;
    private final TenantRepository tenants;

    public ApplicationVariableService(ApplicationVariableDefinitionRepository definitions,
            ApplicationVariableRepository variables, ObjectMapper objectMapper,
            OriginationProfileRepository profiles, OriginationProfileResolver profileResolver,
            OriginationProfileValidator profileValidator, TenantRepository tenants) {
        this.definitions = definitions; this.variables = variables; this.objectMapper = objectMapper;
        this.profiles = profiles; this.profileResolver = profileResolver; this.profileValidator = profileValidator; this.tenants = tenants;
    }

    @Transactional(readOnly = true)
    public List<ApplicationVariableDtos.DefinitionResponse> listAll(String tenantId, boolean activeOnly, String profileCode) {
        if (profileCode != null) return definitions.findApplicable(tenantId, profileId(tenantId, profileCode), activeOnly)
                .stream().map(this::toDefinitionResponse).toList();
        var items = activeOnly ? definitions.findAllByTenantIdAndActiveTrueOrderByDisplayOrderAsc(tenantId)
                : definitions.findAllByTenantIdOrderByDisplayOrderAsc(tenantId);
        return items.stream().map(this::toDefinitionResponse).toList();
    }

    @Transactional
    public List<ApplicationVariableDtos.DefinitionResponse> list(String tenantId, boolean activeOnly, String profileCode) {
        String profileId = profileResolver.resolveForApplication(tenantId, profileCode);
        var items = activeOnly ? definitions.findApplicableForCreation(tenantId, profileId)
                : definitions.findApplicable(tenantId, profileId, false);
        return items.stream().map(this::toDefinitionResponse).toList();
    }

    @Transactional
    public ApplicationVariableDtos.DefinitionResponse create(String tenantId, ApplicationVariableDtos.DefinitionRequest request) {
        lockTenant(tenantId);
        String scope = profileId(tenantId, request.originationProfileCode());
        String code = normalizeCode(request.code());
        if (definitions.existsByTenantIdAndCodeIgnoreCase(tenantId, code)) throw new ConflictException("Application question code already exists");
        ApplicationVariableDefinition definition = new ApplicationVariableDefinition();
        definition.setTenantId(tenantId); definition.setCode(code); definition.setActive(true);
        definition.setOriginationProfileId(scope);
        apply(definition, request, false);
        return toDefinitionResponse(definitions.save(definition));
    }

    @Transactional
    public ApplicationVariableDtos.DefinitionResponse update(String tenantId, String id, ApplicationVariableDtos.DefinitionRequest request) {
        lockTenant(tenantId);
        ApplicationVariableDefinition definition = requiredDefinition(tenantId, id);
        if (!definition.getCode().equals(normalizeCode(request.code()))) throw new BadRequestException("Question code cannot be changed");
        String scope = profileId(tenantId, request.originationProfileCode());
        apply(definition, request, true);
        definition.setOriginationProfileId(scope);
        return toDefinitionResponse(definitions.save(definition));
    }

    @Transactional
    public ApplicationVariableDtos.DefinitionResponse setActive(String tenantId, String id, boolean active) {
        lockTenant(tenantId);
        ApplicationVariableDefinition definition = requiredDefinition(tenantId, id);
        definition.setActive(active); definition.setDefinitionVersion(definition.getDefinitionVersion() + 1);
        return toDefinitionResponse(definitions.save(definition));
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public List<PreparedAnswer> prepare(String tenantId, String profileId, List<ApplicationVariableDtos.AnswerRequest> input) {
        if (profileId == null || profileId.isBlank()) throw new BadRequestException("An origination profile is required for questionnaire validation");
        List<ApplicationVariableDtos.AnswerRequest> answers = input == null ? List.of() : input;
        List<ApplicationVariableDefinition> active = definitions.findApplicableForCreation(tenantId, profileId);
        Map<String, ApplicationVariableDefinition> byId = active.stream().collect(Collectors.toMap(ApplicationVariableDefinition::getId, Function.identity()));
        Set<String> submitted = new HashSet<>();
        List<PreparedAnswer> prepared = new ArrayList<>();
        for (var answer : answers) {
            if (!submitted.add(answer.definitionId())) throw new BadRequestException("Question was answered more than once: " + answer.definitionId());
            ApplicationVariableDefinition definition = byId.get(answer.definitionId());
            if (definition == null) throw new BadRequestException("Unknown, inactive, cross-tenant, or out-of-profile application question: " + answer.definitionId());
            prepared.add(validateAndPrepare(definition, answer));
        }
        for (ApplicationVariableDefinition definition : active) {
            if (definition.isRequired() && !submitted.contains(definition.getId())) throw new BadRequestException("Required application question is missing: " + definition.getCode());
        }
        return List.copyOf(prepared);
    }

    @Transactional
    public void savePrepared(String tenantId, String applicationId, List<PreparedAnswer> prepared) {
        List<ApplicationVariable> entities = prepared.stream().map(item -> {
            ApplicationVariable value = new ApplicationVariable();
            value.setTenantId(tenantId); value.setApplicationId(applicationId); value.setDefinitionId(item.definitionId());
            value.setDefinitionVersion(item.definitionVersion()); value.setCodeSnapshot(item.code()); value.setLabelSnapshot(item.label());
            value.setSectionSnapshot(item.sectionName()); value.setFieldTypeSnapshot(item.fieldType()); value.setAnswerValue(write(item.snapshot()));
            return value;
        }).toList();
        variables.saveAll(entities);
    }

    @Transactional(readOnly = true)
    public List<ApplicationVariableDtos.AnswerResponse> answers(String tenantId, String applicationId) {
        return variables.findAllByTenantIdAndApplicationIdOrderByIdAsc(tenantId, applicationId).stream()
                .map(this::toAnswerResponse)
                .toList();
    }

    private PreparedAnswer validateAndPrepare(ApplicationVariableDefinition definition, ApplicationVariableDtos.AnswerRequest answer) {
        String text = clean(answer.textValue());
        List<String> selected = answer.selectedValues() == null ? List.of() : answer.selectedValues();
        if (new HashSet<>(selected).size() != selected.size()) throw new BadRequestException("Duplicate selections for " + definition.getCode());
        List<ApplicationVariableDtos.Option> selectedOptions = new ArrayList<>();
        switch (definition.getFieldType()) {
            case TEXT, TEXTAREA -> {
                if (text == null || !selected.isEmpty()) throw new BadRequestException("Text answer required for " + definition.getCode());
            }
            case SINGLE_SELECT, MULTI_SELECT -> {
                if (text != null) throw new BadRequestException("Text is not allowed for " + definition.getCode());
                List<ApplicationVariableDtos.Option> options = readOptions(definition.getOptionsJson());
                Map<String, ApplicationVariableDtos.Option> byValue = options.stream().collect(Collectors.toMap(ApplicationVariableDtos.Option::value, Function.identity()));
                for (String value : selected) {
                    ApplicationVariableDtos.Option option = byValue.get(value);
                    if (option == null) throw new BadRequestException("Invalid option for " + definition.getCode() + ": " + value);
                    selectedOptions.add(option);
                }
                int configuredMinimum = Optional.ofNullable(definition.getMinimumSelections()).orElse(0);
                int minimum = definition.getFieldType() == ApplicationVariableFieldType.SINGLE_SELECT ? 1 : Math.max(configuredMinimum, definition.isRequired() ? 1 : 0);
                int maximum = definition.getFieldType() == ApplicationVariableFieldType.SINGLE_SELECT ? 1 : Optional.ofNullable(definition.getMaximumSelections()).orElse(Integer.MAX_VALUE);
                if (selected.size() < minimum || selected.size() > maximum) throw new BadRequestException("Invalid number of selections for " + definition.getCode());
            }
        }
        AnswerSnapshot snapshot = new AnswerSnapshot(text, List.copyOf(selectedOptions));
        return new PreparedAnswer(definition.getId(), definition.getDefinitionVersion(), definition.getCode(), definition.getLabel(), definition.getSectionName(), definition.getFieldType(), snapshot);
    }

    private void apply(ApplicationVariableDefinition definition, ApplicationVariableDtos.DefinitionRequest request, boolean incrementVersion) {
        List<ApplicationVariableDtos.Option> options = request.options() == null ? List.of() : request.options();
        boolean select = request.fieldType() == ApplicationVariableFieldType.SINGLE_SELECT || request.fieldType() == ApplicationVariableFieldType.MULTI_SELECT;
        if (select && options.isEmpty()) throw new BadRequestException("Select questions require at least one option");
        if (!select && (!options.isEmpty() || request.minimumSelections() != null || request.maximumSelections() != null)) throw new BadRequestException("Text questions cannot define selection options or limits");
        Set<String> optionValues = new HashSet<>();
        for (var option : options) if (!optionValues.add(option.value())) throw new BadRequestException("Option values must be unique");
        if (request.fieldType() == ApplicationVariableFieldType.SINGLE_SELECT && (request.minimumSelections() != null || request.maximumSelections() != null)) throw new BadRequestException("SINGLE_SELECT always requires exactly one selection");
        if (request.minimumSelections() != null && request.maximumSelections() != null && request.minimumSelections() > request.maximumSelections()) throw new BadRequestException("minimumSelections cannot exceed maximumSelections");
        if (request.maximumSelections() != null && request.maximumSelections() > options.size()) throw new BadRequestException("maximumSelections cannot exceed the number of options");
        definition.setLabel(request.label().trim()); definition.setSectionName(clean(request.sectionName())); definition.setFieldType(request.fieldType());
        definition.setRequired(request.required()); definition.setDisplayOrder(Optional.ofNullable(request.displayOrder()).orElse(0));
        definition.setMinimumSelections(request.minimumSelections()); definition.setMaximumSelections(request.maximumSelections()); definition.setOptionsJson(write(options));
        if (incrementVersion) definition.setDefinitionVersion(definition.getDefinitionVersion() + 1);
    }

    private void lockTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) throw new ForbiddenOperationException("A tenant user is required");
        tenants.findForOriginationUpdate(tenantId).orElseThrow(() -> new NotFoundException("Tenant not found"));
    }
    private String profileId(String tenantId, String code) {
        if (code == null) return null;
        return profiles.findByTenantIdAndCodeIgnoreCase(tenantId, profileValidator.normalizeCode(code))
                .orElseThrow(() -> new NotFoundException("Origination profile not found")).getId();
    }
    private ApplicationVariableDefinition requiredDefinition(String tenantId, String id) { return definitions.findByIdAndTenantId(id, tenantId).orElseThrow(() -> new NotFoundException("Application question not found")); }
    private ApplicationVariableDtos.DefinitionResponse toDefinitionResponse(ApplicationVariableDefinition d) { return new ApplicationVariableDtos.DefinitionResponse(d.getId(), d.getCode(), d.getLabel(), d.getSectionName(), d.getFieldType(), d.isRequired(), d.isActive(), d.getDisplayOrder(), d.getDefinitionVersion(), d.getMinimumSelections(), d.getMaximumSelections(), readOptions(d.getOptionsJson()), d.getOriginationProfileId()); }
    private ApplicationVariableDtos.AnswerResponse toAnswerResponse(ApplicationVariable v) { AnswerSnapshot a = read(v.getAnswerValue()); return new ApplicationVariableDtos.AnswerResponse(v.getDefinitionId(), v.getDefinitionVersion(), v.getCodeSnapshot(), v.getLabelSnapshot(), v.getSectionSnapshot(), v.getFieldTypeSnapshot(), a.textValue(), a.selectedValues()); }
    private String normalizeCode(String value) { return value.trim().toUpperCase(Locale.ROOT); }
    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String write(Object value) { try { return objectMapper.writeValueAsString(value); } catch (Exception ex) { throw new BadRequestException("Application variable JSON is invalid"); } }
    private List<ApplicationVariableDtos.Option> readOptions(String json) { try { return json == null ? List.of() : objectMapper.readValue(json, new TypeReference<>() {}); } catch (Exception ex) { throw new IllegalStateException("Stored application question options are invalid", ex); } }
    private AnswerSnapshot read(String json) { try { return objectMapper.readValue(json, AnswerSnapshot.class); } catch (Exception ex) { throw new IllegalStateException("Stored application answer is invalid", ex); } }

    public record AnswerSnapshot(String textValue, List<ApplicationVariableDtos.Option> selectedValues) {}
    public record PreparedAnswer(String definitionId, int definitionVersion, String code, String label, String sectionName, ApplicationVariableFieldType fieldType, AnswerSnapshot snapshot) {}
}