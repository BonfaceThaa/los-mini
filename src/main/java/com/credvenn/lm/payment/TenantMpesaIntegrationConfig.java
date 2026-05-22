package com.credvenn.lm.payment;

public record TenantMpesaIntegrationConfig(
        DarajaEnvironment environment,
        String businessShortCode,
        String callbackUrl,
        String encryptedConsumerKey,
        String encryptedConsumerSecret,
        String encryptedPasskey) {

    public boolean hasEncryptedCredentials() {
        return encryptedConsumerKey != null && encryptedConsumerSecret != null && encryptedPasskey != null;
    }
}
