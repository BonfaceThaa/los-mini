package com.credvenn.lm.payment;

public interface DarajaC2bRegistrationGateway {

    record C2bRegistrationResult(
            String originatorConversationId,
            String responseCode,
            String responseDescription) {
    }

    C2bRegistrationResult registerUrls(
            TenantMpesaIntegrationConfig config,
            String confirmationUrl,
            String validationUrl,
            String responseType);
}
