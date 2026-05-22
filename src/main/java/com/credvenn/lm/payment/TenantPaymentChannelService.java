package com.credvenn.lm.payment;

import com.credvenn.lm.common.exception.BadRequestException;
import com.credvenn.lm.common.exception.ConflictException;
import com.credvenn.lm.common.exception.NotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.credvenn.lm.security.SecretsEncryptionService;
import com.credvenn.lm.tenant.TenantService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantPaymentChannelService {

    private final TenantPaymentChannelRepository repository;
    private final TenantService tenantService;
    private final ObjectMapper objectMapper;
    private final SecretsEncryptionService secretsEncryptionService;

    public TenantPaymentChannelService(
            TenantPaymentChannelRepository repository,
            TenantService tenantService,
            ObjectMapper objectMapper,
            SecretsEncryptionService secretsEncryptionService) {
        this.repository = repository;
        this.tenantService = tenantService;
        this.objectMapper = objectMapper;
        this.secretsEncryptionService = secretsEncryptionService;
    }

    @Transactional
    public PaymentDtos.TenantPaymentChannelResponse create(String tenantId, PaymentDtos.CreateTenantPaymentChannelRequest request) {
        String shortCode = request.shortCode().trim();
        validateShortCode(shortCode, request.integration().businessShortCode());
        repository.findByShortCodeAndActiveTrue(shortCode).ifPresent(existing -> {
            throw new ConflictException("Short code is already mapped to a tenant");
        });
        tenantService.getRequiredTenant(tenantId);
        TenantPaymentChannel channel = new TenantPaymentChannel();
        channel.setTenantId(tenantId);
        channel.setChannelType(PaymentChannelType.MPESA_PAYBILL);
        channel.setShortCode(shortCode);
        channel.setActive(true);
        channel.setDescription(request.description() == null ? null : request.description().trim());
        channel.setIntegrationConfig(serializeIntegrationConfig(request.integration()));
        return toResponse(repository.save(channel));
    }

    @Transactional
    public PaymentDtos.TenantPaymentChannelResponse update(
            String tenantId,
            String channelId,
            PaymentDtos.UpdateTenantPaymentChannelRequest request) {
        tenantService.getRequiredTenant(tenantId);
        TenantPaymentChannel channel = repository.findById(channelId)
                .filter(existing -> tenantId.equals(existing.getTenantId()))
                .orElseThrow(() -> new NotFoundException("Tenant payment channel not found"));
        String shortCode = request.shortCode().trim();
        validateShortCode(shortCode, request.integration().businessShortCode());
        repository.findByShortCodeAndActiveTrue(shortCode)
                .filter(existing -> !existing.getId().equals(channelId))
                .ifPresent(existing -> {
                    throw new ConflictException("Short code is already mapped to a tenant");
                });
        channel.setShortCode(shortCode);
        channel.setDescription(request.description() == null ? null : request.description().trim());
        channel.setActive(request.active());
        channel.setIntegrationConfig(serializeIntegrationConfig(request.integration()));
        return toResponse(repository.save(channel));
    }

    @Transactional(readOnly = true)
    public List<PaymentDtos.TenantPaymentChannelResponse> list(String tenantId) {
        tenantService.getRequiredTenant(tenantId);
        return repository.findAllByTenantIdOrderByShortCodeAsc(tenantId).stream()
                .map(this::toResponse)
                .toList();
    }

    PaymentDtos.TenantPaymentChannelResponse toResponse(TenantPaymentChannel channel) {
        TenantMpesaIntegrationConfig config = deserializeIntegrationConfig(channel.getIntegrationConfig());
        return new PaymentDtos.TenantPaymentChannelResponse(
                channel.getId(),
                channel.getTenantId(),
                channel.getChannelType(),
                channel.getShortCode(),
                channel.isActive(),
                channel.getDescription(),
                config == null
                        ? null
                        : new PaymentDtos.TenantMpesaIntegrationConfigSummary(
                                config.environment(),
                                config.businessShortCode(),
                                config.callbackUrl(),
                                config.hasEncryptedCredentials()),
                channel.getCreatedAt(),
                channel.getUpdatedAt());
    }

    TenantMpesaIntegrationConfig getRequiredIntegrationConfig(TenantPaymentChannel channel) {
        TenantMpesaIntegrationConfig config = deserializeIntegrationConfig(channel.getIntegrationConfig());
        if (config == null || !config.hasEncryptedCredentials() || config.environment() == null
                || config.businessShortCode() == null || config.callbackUrl() == null) {
            throw new ConflictException("Tenant payment channel is missing M-PESA integration details");
        }
        return new TenantMpesaIntegrationConfig(
                config.environment(),
                config.businessShortCode(),
                config.callbackUrl(),
                secretsEncryptionService.decrypt(config.encryptedConsumerKey()),
                secretsEncryptionService.decrypt(config.encryptedConsumerSecret()),
                secretsEncryptionService.decrypt(config.encryptedPasskey()));
    }

    private TenantMpesaIntegrationConfig deserializeIntegrationConfig(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(value, TenantMpesaIntegrationConfig.class);
        } catch (JsonProcessingException ex) {
            throw new ConflictException("Tenant payment channel integration config is invalid");
        }
    }

    private String serializeIntegrationConfig(PaymentDtos.TenantMpesaIntegrationConfigRequest request) {
        try {
            TenantMpesaIntegrationConfig config = new TenantMpesaIntegrationConfig(
                    request.environment(),
                    request.businessShortCode().trim(),
                    request.callbackUrl().trim(),
                    secretsEncryptionService.encrypt(request.consumerKey().trim()),
                    secretsEncryptionService.encrypt(request.consumerSecret().trim()),
                    secretsEncryptionService.encrypt(request.passkey().trim()));
            return objectMapper.writeValueAsString(config);
        } catch (JsonProcessingException ex) {
            throw new ConflictException("Unable to store tenant payment channel integration details");
        }
    }

    private void validateShortCode(String shortCode, String businessShortCode) {
        if (businessShortCode == null || !shortCode.equals(businessShortCode.trim())) {
            throw new BadRequestException("shortCode must match integration.businessShortCode");
        }
    }
}
