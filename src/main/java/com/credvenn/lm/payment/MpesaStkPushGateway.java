package com.credvenn.lm.payment;

import java.math.BigDecimal;

public interface MpesaStkPushGateway {

    InitiationResult initiate(InitiationCommand command);

    record InitiationCommand(
            TenantMpesaIntegrationConfig config,
            String billReference,
            String normalizedPhoneNumber,
            BigDecimal amount,
            String transactionDescription) {
    }

    record InitiationResult(
            String merchantRequestId,
            String checkoutRequestId,
            String responseCode,
            String responseDescription,
            String customerMessage,
            String rawResponse) {
    }
}
