package com.credvenn.lm.payment;

import com.credvenn.lm.common.exception.BadRequestException;
import com.credvenn.lm.common.exception.ConflictException;
import com.credvenn.lm.common.exception.NotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.credvenn.lm.security.SecretsEncryptionService;
import com.credvenn.lm.tenant.TenantService;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantPaymentChannelService {

    private final TenantPaymentChannelRepository repository;
    private final TenantService tenantService;
    private final ObjectMapper objectMapper;
    private final SecretsEncryptionService secretsEncryptionService;
    private final DarajaC2bRegistrationGateway darajaC2bRegistrationGateway;

    public TenantPaymentChannelService(
            TenantPaymentChannelRepository repository,
            TenantService tenantService,
            ObjectMapper objectMapper,
            SecretsEncryptionService secretsEncryptionService,
            DarajaC2bRegistrationGateway darajaC2bRegistrationGateway) {
        this.repository = repository;
        this.tenantService = tenantService;
        this.objectMapper = objectMapper;
        this.secretsEncryptionService = secretsEncryptionService;
        this.darajaC2bRegistrationGateway = darajaC2bRegistrationGateway;
    }

    @Transactional
    public TenantPaymentChannel create(
            String tenantId,
            String shortCode,
            String description,
            DarajaEnvironment environment,
            String businessShortCode,
            String callbackUrl,
            String consumerKey,
            String consumerSecret,
            String passkey) {
        shortCode = shortCode.trim();
        validateShortCode(shortCode, businessShortCode);
        repository.findByShortCodeAndActiveTrue(shortCode).ifPresent(existing -> {
            throw new ConflictException("Short code is already mapped to a tenant");
        });
        tenantService.getRequiredTenant(tenantId);
        TenantPaymentChannel channel = new TenantPaymentChannel();
        channel.setTenantId(tenantId);
        channel.setChannelType(PaymentChannelType.MPESA_PAYBILL);
        channel.setShortCode(shortCode);
        channel.setActive(true);
        channel.setDescription(description == null ? null : description.trim());
        channel.setIntegrationConfig(serializeIntegrationConfig(new TenantMpesaIntegrationConfig(
                environment,
                businessShortCode.trim(),
                callbackUrl.trim(),
                secretsEncryptionService.encrypt(consumerKey.trim()),
                secretsEncryptionService.encrypt(consumerSecret.trim()),
                secretsEncryptionService.encrypt(passkey.trim()),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null)));
        return repository.save(channel);
    }

    @Transactional
    public TenantPaymentChannel update(
            String tenantId,
            String channelId,
            String shortCode,
            String description,
            DarajaEnvironment environment,
            String businessShortCode,
            String callbackUrl,
            String consumerKey,
            String consumerSecret,
            String passkey,
            boolean active) {
        tenantService.getRequiredTenant(tenantId);
        TenantPaymentChannel channel = repository.findById(channelId)
                .filter(existing -> tenantId.equals(existing.getTenantId()))
                .orElseThrow(() -> new NotFoundException("Tenant payment channel not found"));
        shortCode = shortCode.trim();
        validateShortCode(shortCode, businessShortCode);
        repository.findByShortCodeAndActiveTrue(shortCode)
                .filter(existing -> !existing.getId().equals(channelId))
                .ifPresent(existing -> {
                    throw new ConflictException("Short code is already mapped to a tenant");
                });
        channel.setShortCode(shortCode);
        channel.setDescription(description == null ? null : description.trim());
        channel.setActive(active);
        TenantMpesaIntegrationConfig existingConfig = deserializeIntegrationConfig(channel.getIntegrationConfig());
        channel.setIntegrationConfig(serializeIntegrationConfig(new TenantMpesaIntegrationConfig(
                environment,
                businessShortCode.trim(),
                callbackUrl.trim(),
                secretsEncryptionService.encrypt(consumerKey.trim()),
                secretsEncryptionService.encrypt(consumerSecret.trim()),
                secretsEncryptionService.encrypt(passkey.trim()),
                existingConfig == null ? null : existingConfig.c2bConfirmationUrl(),
                existingConfig == null ? null : existingConfig.c2bValidationUrl(),
                existingConfig == null ? null : existingConfig.c2bResponseType(),
                existingConfig == null ? null : existingConfig.c2bLastRegisteredAt(),
                existingConfig == null ? null : existingConfig.c2bLastRequestedAt(),
                existingConfig == null ? null : existingConfig.c2bLastResponseCode(),
                existingConfig == null ? null : existingConfig.c2bLastResponseDescription(),
                existingConfig == null ? null : existingConfig.c2bLastOriginatorConversationId())));
        return repository.save(channel);
    }

    @Transactional(readOnly = true)
    public List<TenantPaymentChannel> list(String tenantId) {
        tenantService.getRequiredTenant(tenantId);
        return repository.findAllByTenantIdOrderByShortCodeAsc(tenantId);
    }

    TenantMpesaIntegrationConfig getConfiguredIntegration(TenantPaymentChannel channel) {
        return deserializeIntegrationConfig(channel.getIntegrationConfig());
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
                secretsEncryptionService.decrypt(config.encryptedPasskey()),
                config.c2bConfirmationUrl(),
                config.c2bValidationUrl(),
                config.c2bResponseType(),
                config.c2bLastRegisteredAt(),
                config.c2bLastRequestedAt(),
                config.c2bLastResponseCode(),
                config.c2bLastResponseDescription(),
                config.c2bLastOriginatorConversationId());
    }

    @Transactional
    public PaymentDtos.RegisterTenantC2bUrlsResponse registerC2bUrls(
            String tenantId,
            String channelId,
            String confirmationUrl,
            String validationUrl,
            String responseType) {
        tenantService.getRequiredTenant(tenantId);
        TenantPaymentChannel channel = repository.findById(channelId)
                .filter(existing -> tenantId.equals(existing.getTenantId()))
                .orElseThrow(() -> new NotFoundException("Tenant payment channel not found"));
        TenantMpesaIntegrationConfig storedConfig = deserializeIntegrationConfig(channel.getIntegrationConfig());
        if (storedConfig == null || !storedConfig.hasEncryptedCredentials()) {
            throw new ConflictException("Tenant payment channel is missing stored M-PESA credentials");
        }

        String trimmedConfirmationUrl = confirmationUrl.trim();
        String trimmedValidationUrl = trimToNull(validationUrl);
        String trimmedResponseType = responseType.trim();
        TenantMpesaIntegrationConfig runtimeConfig = getRequiredIntegrationConfig(channel);
        Instant requestedAt = Instant.now();
        DarajaC2bRegistrationGateway.C2bRegistrationResult result = darajaC2bRegistrationGateway.registerUrls(
                runtimeConfig,
                trimmedConfirmationUrl,
                trimmedValidationUrl,
                trimmedResponseType);

        TenantMpesaIntegrationConfig updatedConfig = new TenantMpesaIntegrationConfig(
                storedConfig.environment(),
                storedConfig.businessShortCode(),
                storedConfig.callbackUrl(),
                storedConfig.encryptedConsumerKey(),
                storedConfig.encryptedConsumerSecret(),
                storedConfig.encryptedPasskey(),
                trimmedConfirmationUrl,
                trimmedValidationUrl,
                trimmedResponseType,
                isSuccessfulDarajaResponseCode(result.responseCode()) ? requestedAt : storedConfig.c2bLastRegisteredAt(),
                requestedAt,
                result.responseCode(),
                result.responseDescription(),
                result.originatorConversationId());
        channel.setIntegrationConfig(serializeIntegrationConfig(updatedConfig));
        repository.save(channel);

        return new PaymentDtos.RegisterTenantC2bUrlsResponse(
                tenantId,
                channel.getId(),
                channel.getShortCode(),
                updatedConfig.c2bConfirmationUrl(),
                updatedConfig.c2bValidationUrl(),
                updatedConfig.c2bResponseType(),
                updatedConfig.c2bLastRequestedAt(),
                updatedConfig.c2bLastRegisteredAt(),
                updatedConfig.c2bLastResponseCode(),
                updatedConfig.c2bLastResponseDescription(),
                updatedConfig.c2bLastOriginatorConversationId());
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

    private String serializeIntegrationConfig(TenantMpesaIntegrationConfig config) {
        try {
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

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isSuccessfulDarajaResponseCode(String responseCode) {
        if (responseCode == null) {
            return false;
        }
        String normalized = responseCode.trim();
        return "0".equals(normalized) || "00000000".equals(normalized);
    }
}
