package com.credvenn.lm.payment;

public interface DarajaAccessTokenGateway {

    String fetchAccessToken(TenantMpesaIntegrationConfig config);
}
