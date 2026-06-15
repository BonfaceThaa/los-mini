package com.credvenn.lm.devicecontrol;

import com.credvenn.lm.common.exception.BadRequestException;
import com.credvenn.lm.common.exception.NotFoundException;
import com.credvenn.lm.security.SecretsEncryptionService;
import com.credvenn.lm.tenant.TenantService;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class TenantDeviceControlConfigService {

    private final TenantDeviceControlConfigRepository configRepository;
    private final TenantDeviceControlNotificationRuleRepository notificationRuleRepository;
    private final TenantDeviceControlCustomNotificationRuleRepository customNotificationRuleRepository;
    private final TenantDeviceControlCustomNotificationRuleFieldRepository customNotificationRuleFieldRepository;
    private final TenantDeviceControlNudgeRuleRepository nudgeRuleRepository;
    private final SecretsEncryptionService encryptionService;
    private final TenantService tenantService;
    private final DeviceControlGateway gateway;

    public TenantDeviceControlConfigService(
            TenantDeviceControlConfigRepository configRepository,
            TenantDeviceControlNotificationRuleRepository notificationRuleRepository,
            TenantDeviceControlCustomNotificationRuleRepository customNotificationRuleRepository,
            TenantDeviceControlCustomNotificationRuleFieldRepository customNotificationRuleFieldRepository,
            TenantDeviceControlNudgeRuleRepository nudgeRuleRepository,
            SecretsEncryptionService encryptionService,
            TenantService tenantService,
            DeviceControlGateway gateway) {
        this.configRepository = configRepository;
        this.notificationRuleRepository = notificationRuleRepository;
        this.customNotificationRuleRepository = customNotificationRuleRepository;
        this.customNotificationRuleFieldRepository = customNotificationRuleFieldRepository;
        this.nudgeRuleRepository = nudgeRuleRepository;
        this.encryptionService = encryptionService;
        this.tenantService = tenantService;
        this.gateway = gateway;
    }

    @Transactional(readOnly = true)
    public DeviceControlDtos.TenantDeviceControlConfigResponse get(String tenantId) {
        TenantDeviceControlConfig config = configRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new NotFoundException("Tenant device-control configuration not found"));
        return toResponse(config);
    }

    @Transactional
    public DeviceControlDtos.TenantDeviceControlConfigResponse upsert(String tenantId, DeviceControlDtos.UpsertTenantDeviceControlConfigRequest request) {
        tenantService.getRequiredTenant(tenantId);
        TenantDeviceControlConfig config = configRepository.findByTenantId(tenantId).orElseGet(TenantDeviceControlConfig::new);
        config.setTenantId(tenantId);
        config.setProvider(DeviceControlProvider.DATACULTR);
        config.setEnabled(request.enabled());
        config.setBaseUrl(request.baseUrl().trim());
        config.setClientCode(request.clientCode().trim());
        config.setEncryptedUsername(encryptionService.encrypt(request.username().trim()));
        config.setEncryptedPassword(encryptionService.encrypt(request.password().trim()));
        config.setChannelCode(trimToNull(request.channelCode()));
        config.setPaymentLinkTemplate(trimToNull(request.paymentLinkTemplate()));
        config.setLockEnabled(request.lockEnabled());
        config.setUnlockEnabled(request.unlockEnabled());
        config.setRemindersEnabled(request.remindersEnabled());
        config.setNudgesEnabled(request.nudgesEnabled());
        config.setOfflinePinEnabled(request.offlinePinEnabled());
        config.setOverdueLockCadenceMinutes(request.overdueLockCadenceMinutes());
        config.setPredueCadenceMinutes(request.predueCadenceMinutes());
        return toResponse(configRepository.save(config));
    }

    @Transactional(readOnly = true)
    public List<DeviceControlDtos.NotificationRuleResponse> listNotificationRules(String tenantId) {
        TenantDeviceControlConfig config = requireConfig(tenantId);
        return notificationRuleRepository.findAllByTenantConfigIdOrderByDaysBeforeDueAscIdAsc(config.getId()).stream()
                .map(TenantDeviceControlConfigService::toResponse)
                .toList();
    }

    @Transactional
    public DeviceControlDtos.NotificationRuleResponse createNotificationRule(String tenantId, DeviceControlDtos.UpsertNotificationRuleRequest request) {
        TenantDeviceControlConfig config = requireConfig(tenantId);
        validateDayOffset(request.daysBeforeDue());
        TenantDeviceControlNotificationRule rule = new TenantDeviceControlNotificationRule();
        rule.setTenantConfigId(config.getId());
        rule.setDaysBeforeDue(request.daysBeforeDue());
        rule.setNotificationCode(request.notificationCode().trim());
        rule.setName(trimToNull(request.name()));
        rule.setActive(request.active());
        return toResponse(notificationRuleRepository.save(rule));
    }

    @Transactional
    public DeviceControlDtos.NotificationRuleResponse updateNotificationRule(String tenantId, String ruleId, DeviceControlDtos.UpsertNotificationRuleRequest request) {
        TenantDeviceControlConfig config = requireConfig(tenantId);
        validateDayOffset(request.daysBeforeDue());
        TenantDeviceControlNotificationRule rule = notificationRuleRepository.findByIdAndTenantConfigId(ruleId, config.getId())
                .orElseThrow(() -> new NotFoundException("Notification rule not found"));
        rule.setDaysBeforeDue(request.daysBeforeDue());
        rule.setNotificationCode(request.notificationCode().trim());
        rule.setName(trimToNull(request.name()));
        rule.setActive(request.active());
        return toResponse(rule);
    }

    @Transactional
    public void deleteNotificationRule(String tenantId, String ruleId) {
        TenantDeviceControlConfig config = requireConfig(tenantId);
        TenantDeviceControlNotificationRule rule = notificationRuleRepository.findByIdAndTenantConfigId(ruleId, config.getId())
                .orElseThrow(() -> new NotFoundException("Notification rule not found"));
        notificationRuleRepository.delete(rule);
    }

    @Transactional(readOnly = true)
    public List<DeviceControlDtos.CustomNotificationRuleResponse> listCustomNotificationRules(String tenantId) {
        TenantDeviceControlConfig config = requireConfig(tenantId);
        return customNotificationRuleRepository.findAllByTenantConfigIdOrderByDaysBeforeDueAscIdAsc(config.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public DeviceControlDtos.CustomNotificationRuleResponse createCustomNotificationRule(
            String tenantId,
            DeviceControlDtos.UpsertCustomNotificationRuleRequest request) {
        TenantDeviceControlConfig config = requireConfig(tenantId);
        validateDayOffset(request.daysBeforeDue());

        TenantDeviceControlCustomNotificationRule rule = new TenantDeviceControlCustomNotificationRule();
        rule.setTenantConfigId(config.getId());
        applyCustomRule(rule, request);
        TenantDeviceControlCustomNotificationRule savedRule = customNotificationRuleRepository.save(rule);
        replaceCustomFieldMappings(savedRule.getId(), request.fieldMappings());
        return toResponse(savedRule);
    }

    @Transactional
    public DeviceControlDtos.CustomNotificationRuleResponse updateCustomNotificationRule(
            String tenantId,
            String ruleId,
            DeviceControlDtos.UpsertCustomNotificationRuleRequest request) {
        TenantDeviceControlConfig config = requireConfig(tenantId);
        validateDayOffset(request.daysBeforeDue());

        TenantDeviceControlCustomNotificationRule rule = customNotificationRuleRepository.findByIdAndTenantConfigId(ruleId, config.getId())
                .orElseThrow(() -> new NotFoundException("Custom notification rule not found"));
        applyCustomRule(rule, request);
        replaceCustomFieldMappings(rule.getId(), request.fieldMappings());
        return toResponse(rule);
    }

    @Transactional
    public void deleteCustomNotificationRule(String tenantId, String ruleId) {
        TenantDeviceControlConfig config = requireConfig(tenantId);
        TenantDeviceControlCustomNotificationRule rule = customNotificationRuleRepository.findByIdAndTenantConfigId(ruleId, config.getId())
                .orElseThrow(() -> new NotFoundException("Custom notification rule not found"));
        customNotificationRuleFieldRepository.deleteAllByCustomRuleId(rule.getId());
        customNotificationRuleRepository.delete(rule);
    }

    @Transactional(readOnly = true)
    public List<DeviceControlDtos.NudgeRuleResponse> listNudgeRules(String tenantId) {
        TenantDeviceControlConfig config = requireConfig(tenantId);
        return nudgeRuleRepository.findAllByTenantConfigIdOrderByDaysBeforeDueAscIdAsc(config.getId()).stream()
                .map(TenantDeviceControlConfigService::toResponse)
                .toList();
    }

    @Transactional
    public DeviceControlDtos.NudgeRuleResponse createNudgeRule(String tenantId, DeviceControlDtos.UpsertNudgeRuleRequest request) {
        TenantDeviceControlConfig config = requireConfig(tenantId);
        validateDayOffset(request.daysBeforeDue());
        TenantDeviceControlNudgeRule rule = new TenantDeviceControlNudgeRule();
        rule.setTenantConfigId(config.getId());
        rule.setDaysBeforeDue(request.daysBeforeDue());
        rule.setNudgeCode(request.nudgeCode().trim());
        rule.setName(trimToNull(request.name()));
        rule.setActive(request.active());
        return toResponse(nudgeRuleRepository.save(rule));
    }

    @Transactional
    public DeviceControlDtos.NudgeRuleResponse updateNudgeRule(String tenantId, String ruleId, DeviceControlDtos.UpsertNudgeRuleRequest request) {
        TenantDeviceControlConfig config = requireConfig(tenantId);
        validateDayOffset(request.daysBeforeDue());
        TenantDeviceControlNudgeRule rule = nudgeRuleRepository.findByIdAndTenantConfigId(ruleId, config.getId())
                .orElseThrow(() -> new NotFoundException("Nudge rule not found"));
        rule.setDaysBeforeDue(request.daysBeforeDue());
        rule.setNudgeCode(request.nudgeCode().trim());
        rule.setName(trimToNull(request.name()));
        rule.setActive(request.active());
        return toResponse(rule);
    }

    @Transactional
    public void deleteNudgeRule(String tenantId, String ruleId) {
        TenantDeviceControlConfig config = requireConfig(tenantId);
        TenantDeviceControlNudgeRule rule = nudgeRuleRepository.findByIdAndTenantConfigId(ruleId, config.getId())
                .orElseThrow(() -> new NotFoundException("Nudge rule not found"));
        nudgeRuleRepository.delete(rule);
    }

    @Transactional(readOnly = true)
    public DeviceControlDtos.ProviderCatalogResponse fetchNotifications(String tenantId) {
        DeviceControlGateway.RuntimeConfig runtimeConfig = runtimeConfig(requireConfig(tenantId));
        log.info("fetchNotifications ....");
        log.info(tenantId);
        List<DeviceControlDtos.ProviderCatalogItemResponse> items = gateway.fetchNotifications(runtimeConfig).stream()
                .map(item -> new DeviceControlDtos.ProviderCatalogItemResponse(item.code(), item.title()))
                .toList();
        return new DeviceControlDtos.ProviderCatalogResponse("Fetched Datacultr notifications", items);
    }

    @Transactional(readOnly = true)
    public DeviceControlDtos.ProviderCatalogResponse fetchNudges(String tenantId) {
        DeviceControlGateway.RuntimeConfig runtimeConfig = runtimeConfig(requireConfig(tenantId));
        List<DeviceControlDtos.ProviderCatalogItemResponse> items = gateway.fetchNudges(runtimeConfig).stream()
                .map(item -> new DeviceControlDtos.ProviderCatalogItemResponse(item.code(), item.title()))
                .toList();
        return new DeviceControlDtos.ProviderCatalogResponse("Fetched Datacultr nudges", items);
    }

    @Transactional(readOnly = true)
    public DeviceControlGateway.RuntimeConfig getRuntimeConfig(String tenantId) {
        return runtimeConfig(requireConfig(tenantId));
    }

    @Transactional(readOnly = true)
    public TenantDeviceControlConfig requireConfig(String tenantId) {
        return configRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new NotFoundException("Tenant device-control configuration not found"));
    }

    private DeviceControlGateway.RuntimeConfig runtimeConfig(TenantDeviceControlConfig config) {
        return new DeviceControlGateway.RuntimeConfig(
                config.getTenantId(),
                config.getId(),
                config.getBaseUrl(),
                config.getClientCode(),
                encryptionService.decrypt(config.getEncryptedUsername()),
                encryptionService.decrypt(config.getEncryptedPassword()),
                config.getChannelCode(),
                config.getPaymentLinkTemplate());
    }

    private void validateDayOffset(Integer value) {
        if (value == null || value < 0) {
            throw new BadRequestException("daysBeforeDue must be zero or greater");
        }
    }

    private void applyCustomRule(
            TenantDeviceControlCustomNotificationRule rule,
            DeviceControlDtos.UpsertCustomNotificationRuleRequest request) {
        rule.setDaysBeforeDue(request.daysBeforeDue());
        rule.setNotificationCode(request.notificationCode().trim());
        rule.setName(trimToNull(request.name()));
        rule.setActive(request.active());
    }

    private void replaceCustomFieldMappings(
            String ruleId,
            List<DeviceControlDtos.UpsertCustomNotificationRuleFieldRequest> fieldMappings) {
        customNotificationRuleFieldRepository.deleteAllByCustomRuleId(ruleId);

        for (int i = 0; i < fieldMappings.size(); i++) {
            DeviceControlDtos.UpsertCustomNotificationRuleFieldRequest request = fieldMappings.get(i);
            TenantDeviceControlCustomNotificationRuleField field = new TenantDeviceControlCustomNotificationRuleField();
            field.setCustomRuleId(ruleId);
            field.setColumnName(request.columnName().trim());
            field.setSourceField(request.sourceField());
            field.setDisplayOrder(i + 1);
            customNotificationRuleFieldRepository.save(field);
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    static DeviceControlDtos.TenantDeviceControlConfigResponse toResponse(TenantDeviceControlConfig config) {
        return new DeviceControlDtos.TenantDeviceControlConfigResponse(
                config.getId(),
                config.getTenantId(),
                config.getProvider(),
                config.isEnabled(),
                config.getBaseUrl(),
                config.getClientCode(),
                config.getChannelCode(),
                config.getPaymentLinkTemplate(),
                config.isLockEnabled(),
                config.isUnlockEnabled(),
                config.isRemindersEnabled(),
                config.isNudgesEnabled(),
                config.isOfflinePinEnabled(),
                config.getOverdueLockCadenceMinutes(),
                config.getPredueCadenceMinutes(),
                config.getLastOverdueLockRunAt(),
                config.getLastPredueRunAt(),
                config.getEncryptedUsername() != null && !config.getEncryptedUsername().isBlank(),
                config.getCreatedAt(),
                config.getUpdatedAt());
    }

    static DeviceControlDtos.NotificationRuleResponse toResponse(TenantDeviceControlNotificationRule rule) {
        return new DeviceControlDtos.NotificationRuleResponse(
                rule.getId(),
                rule.getDaysBeforeDue(),
                rule.getNotificationCode(),
                rule.getName(),
                rule.isActive(),
                rule.getCreatedAt(),
                rule.getUpdatedAt());
    }

    DeviceControlDtos.CustomNotificationRuleResponse toResponse(TenantDeviceControlCustomNotificationRule rule) {
        List<DeviceControlDtos.CustomNotificationRuleFieldResponse> fieldMappings =
                customNotificationRuleFieldRepository.findAllByCustomRuleIdOrderByDisplayOrderAscIdAsc(rule.getId()).stream()
                        .map(TenantDeviceControlConfigService::toResponse)
                        .toList();
        return new DeviceControlDtos.CustomNotificationRuleResponse(
                rule.getId(),
                rule.getDaysBeforeDue(),
                rule.getNotificationCode(),
                rule.getName(),
                rule.isActive(),
                fieldMappings,
                rule.getCreatedAt(),
                rule.getUpdatedAt());
    }

    static DeviceControlDtos.CustomNotificationRuleFieldResponse toResponse(TenantDeviceControlCustomNotificationRuleField field) {
        return new DeviceControlDtos.CustomNotificationRuleFieldResponse(
                field.getId(),
                field.getColumnName(),
                field.getSourceField(),
                field.getDisplayOrder());
    }

    static DeviceControlDtos.NudgeRuleResponse toResponse(TenantDeviceControlNudgeRule rule) {
        return new DeviceControlDtos.NudgeRuleResponse(
                rule.getId(),
                rule.getDaysBeforeDue(),
                rule.getNudgeCode(),
                rule.getName(),
                rule.isActive(),
                rule.getCreatedAt(),
                rule.getUpdatedAt());
    }
}
