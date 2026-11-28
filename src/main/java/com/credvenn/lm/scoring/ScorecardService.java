package com.credvenn.lm.scoring;

import com.credvenn.lm.applicationvariable.ApplicationVariableDtos;
import com.credvenn.lm.applicationvariable.ApplicationVariableFieldType;
import com.credvenn.lm.applicationvariable.ApplicationVariableService;
import com.credvenn.lm.common.exception.BadRequestException;
import com.credvenn.lm.common.exception.ConflictException;
import com.credvenn.lm.common.exception.NotFoundException;
import com.credvenn.lm.origination.OriginationProfile;
import com.credvenn.lm.origination.OriginationProfileRepository;
import com.credvenn.lm.security.CurrentActorService;
import com.credvenn.lm.tenant.TenantRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class ScorecardService {
    private final ScorecardVersionRepository versions;
    private final OriginationProfileRepository profiles;
    private final TenantRepository tenants;
    private final ApplicationVariableService questions;
    private final CurrentActorService actors;
    private final ObjectMapper json;
    private final JdbcTemplate jdbc;
    private final StatementPolicyValidator policyValidator;

    @Transactional
    public ScorecardDtos.Response create(String code, ScorecardDtos.Draft draft) {
        var actor = actors.requireCurrentUser();
        lockTenant(actor.tenantId());
        var profile = profile(actor.tenantId(), code);
        int number = versions.findFirstByTenantIdAndOriginationProfileIdOrderByVersionNumberDesc(actor.tenantId(), profile.getId())
                .map(v -> v.getVersionNumber() + 1).orElse(1);
        var version = new ScorecardVersion();
        version.setTenantId(actor.tenantId()); version.setOriginationProfileId(profile.getId());
        version.setVersionNumber(number); version.setStatus("DRAFT"); version.setDraftJson(write(draft));
        version.setCreatedBy(actor.username()); version.setUpdatedBy(actor.username());
        version = versions.saveAndFlush(version);
        audit(version, "CREATE", actor.username(), null, version.getDraftJson());
        return response(version, profile);
    }

    @Transactional
    public ScorecardDtos.Response update(String code, String id, ScorecardDtos.Draft draft) {
        var actor = actors.requireCurrentUser();
        lockTenant(actor.tenantId());
        var profile = profile(actor.tenantId(), code);
        var version = required(actor.tenantId(), profile.getId(), id);
        if (!"DRAFT".equals(version.getStatus())) throw new ConflictException("Published scorecards cannot be edited");
        String before = version.getDraftJson();
        version.setDraftJson(write(draft)); version.setUpdatedBy(actor.username());
        audit(version, "UPDATE", actor.username(), before, version.getDraftJson());
        return response(versions.save(version), profile);
    }

    @Transactional(readOnly = true)
    public List<ScorecardDtos.Response> list(String code) {
        var actor = actors.requireCurrentUser(); var profile = profile(actor.tenantId(), code);
        return versions.findAllByTenantIdAndOriginationProfileIdOrderByVersionNumberDesc(actor.tenantId(), profile.getId())
                .stream().map(v -> response(v, profile)).toList();
    }

    @Transactional(readOnly = true)
    public ScorecardDtos.Response get(String code, String id) {
        var actor = actors.requireCurrentUser(); var profile = profile(actor.tenantId(), code);
        return response(required(actor.tenantId(), profile.getId(), id), profile);
    }

    @Transactional
    public ScorecardDtos.Response publish(String code, String id) {
        var actor = actors.requireCurrentUser(); lockTenant(actor.tenantId());
        var profile = profile(actor.tenantId(), code); var version = required(actor.tenantId(), profile.getId(), id);
        if (!"DRAFT".equals(version.getStatus())) throw new ConflictException("Only drafts can be published");
        var draft = read(version.getDraftJson(), ScorecardDtos.Draft.class);
        var snapshot = snapshot(actor.tenantId(), profile, draft);
        version.setSnapshotJson(write(snapshot)); version.setStatus("PUBLISHED");
        version.setPublishedAt(Instant.now()); version.setUpdatedBy(actor.username());
        versions.saveAndFlush(version);
        int updated = jdbc.update("UPDATE scoring_active_scorecards SET scorecard_version_id = ? WHERE tenant_id = ? AND origination_profile_id = ?",
                version.getId(), actor.tenantId(), profile.getId());
        if (updated == 0) jdbc.update("INSERT INTO scoring_active_scorecards (tenant_id,origination_profile_id,scorecard_version_id) VALUES (?,?,?)",
                actor.tenantId(), profile.getId(), version.getId());
        audit(version, "PUBLISH", actor.username(), version.getDraftJson(), version.getSnapshotJson());
        return response(version, profile);
    }

    @Transactional
    public void validate(String code, String id) {
        var actor = actors.requireCurrentUser(); lockTenant(actor.tenantId());
        var profile = profile(actor.tenantId(), code);
        var version = required(actor.tenantId(), profile.getId(), id);
        if (!"DRAFT".equals(version.getStatus())) throw new ConflictException("Only drafts can be validated");
        snapshot(actor.tenantId(), profile, read(version.getDraftJson(), ScorecardDtos.Draft.class));
    }

    private ScorecardDtos.Snapshot snapshot(String tenantId, OriginationProfile profile, ScorecardDtos.Draft draft) {
        if (draft == null || draft.statementWeight() == null || draft.questionnaireWeight() == null
                || draft.statementWeight().add(draft.questionnaireWeight()).compareTo(BigDecimal.valueOf(100)) != 0)
            throw new BadRequestException("Component weights must total 100");
        if (draft.statementWeight().compareTo(BigDecimal.valueOf(60)) != 0
                || draft.questionnaireWeight().compareTo(BigDecimal.valueOf(40)) != 0)
            throw new BadRequestException("Statement and questionnaire weights must be 60 and 40");
        policyValidator.validate(draft.statementPolicy());
        if (draft.questions() == null || draft.questions().isEmpty()) throw new BadRequestException("At least one scoring question is required");
        Map<String, ApplicationVariableDtos.DefinitionResponse> available = questions.listAll(tenantId, true, profile.getCode())
                .stream().collect(Collectors.toMap(ApplicationVariableDtos.DefinitionResponse::id, Function.identity()));
        List<ApplicationVariableDtos.DefinitionResponse> selected = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (var rule : draft.questions()) {
            if (rule == null || !ids.add(rule.definitionId())) throw new BadRequestException("Duplicate or invalid question");
            var q = available.get(rule.definitionId());
            if (q == null) throw new BadRequestException("Question is inactive, unknown or not applicable to this profile");
            if (q.scoringWeight() == null || q.scoringWeight().signum() < 0 || q.options().isEmpty()
                    || (q.fieldType() != ApplicationVariableFieldType.SINGLE_SELECT && q.fieldType() != ApplicationVariableFieldType.MULTI_SELECT)
                    || q.options().stream().anyMatch(o -> o.points() == null || o.points().signum() < 0 || o.points().compareTo(BigDecimal.valueOf(100)) > 0))
                throw new BadRequestException("Scored questions need a weight and points on every select option: " + q.code());
            selected.add(q);
        }
        if (selected.stream().map(ApplicationVariableDtos.DefinitionResponse::scoringWeight).reduce(BigDecimal.ZERO, BigDecimal::add).signum() <= 0)
            throw new BadRequestException("Question weight total must be positive");
        var byCode = selected.stream().collect(Collectors.toMap(ApplicationVariableDtos.DefinitionResponse::code, Function.identity()));
        var conditions = draft.questions().stream().filter(r -> r.condition() != null)
                .collect(Collectors.toMap(r -> available.get(r.definitionId()).code(), ScorecardDtos.QuestionRule::condition));
        for (var entry : conditions.entrySet()) {
            var c = entry.getValue(); var parent = byCode.get(c.questionCode());
            if (parent == null || conditions.containsKey(c.questionCode()) || c.applicableValues() == null || c.applicableValues().isEmpty()
                    || !parent.options().stream().map(ApplicationVariableDtos.Option::value).collect(Collectors.toSet()).containsAll(c.applicableValues()))
                throw new BadRequestException("Invalid condition for " + entry.getKey());
        }
        if (selected.stream().filter(q -> !conditions.containsKey(q.code())).map(ApplicationVariableDtos.DefinitionResponse::scoringWeight)
                .reduce(BigDecimal.ZERO, BigDecimal::add).signum() <= 0)
            throw new BadRequestException("An unconditional question must have positive weight");
        return new ScorecardDtos.Snapshot(draft, List.copyOf(selected));
    }

    private void lockTenant(String tenant) { tenants.findForOriginationUpdate(tenant).orElseThrow(() -> new NotFoundException("Tenant not found")); }
    private void audit(ScorecardVersion v, String action, String actor, String before, String after) {
        jdbc.update("INSERT INTO scoring_scorecard_audit (id,tenant_id,origination_profile_id,scorecard_version_id,action,actor,before_json,after_json,created_at) VALUES (?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",
                UUID.randomUUID().toString(), v.getTenantId(), v.getOriginationProfileId(), v.getId(), action, actor, before, after);
    }
    private OriginationProfile profile(String tenant, String code) { return profiles.findByTenantIdAndCodeIgnoreCase(tenant, code).orElseThrow(() -> new NotFoundException("Origination profile not found")); }
    private ScorecardVersion required(String tenant, String profile, String id) { return versions.findByTenantIdAndOriginationProfileIdAndId(tenant, profile, id).orElseThrow(() -> new NotFoundException("Scorecard not found")); }
    private String write(Object value) { try { return json.writeValueAsString(value); } catch (Exception ex) { throw new BadRequestException("Invalid scorecard configuration"); } }
    private <T> T read(String value, Class<T> type) { try { return json.readValue(value, type); } catch (Exception ex) { throw new IllegalStateException("Invalid stored scorecard", ex); } }
    private ScorecardDtos.Response response(ScorecardVersion v, OriginationProfile p) { return new ScorecardDtos.Response(v.getId(), p.getCode(), v.getVersionNumber(), v.getStatus(), read(v.getDraftJson(), ScorecardDtos.Draft.class), v.getSnapshotJson() == null ? null : read(v.getSnapshotJson(), ScorecardDtos.Snapshot.class), v.getCreatedBy(), v.getUpdatedBy(), v.getPublishedAt()); }
}
