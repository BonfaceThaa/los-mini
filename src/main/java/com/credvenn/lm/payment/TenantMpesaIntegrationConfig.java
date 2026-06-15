package com.credvenn.lm.payment;

import java.time.Instant;

public record TenantMpesaIntegrationConfig(
        DarajaEnvironment environment,
        String businessShortCode,
        String callbackUrl,
        String encryptedConsumerKey,
        String encryptedConsumerSecret,
        String encryptedPasskey,
        String c2bConfirmationUrl,
        String c2bValidationUrl,
        String c2bResponseType,
        Instant c2bLastRegisteredAt,
        Instant c2bLastRequestedAt,
        String c2bLastResponseCode,
        String c2bLastResponseDescription,
        String c2bLastOriginatorConversationId) {

    public boolean hasEncryptedCredentials() {
        return encryptedConsumerKey != null && encryptedConsumerSecret != null && encryptedPasskey != null;
    }
}
