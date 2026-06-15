package com.credvenn.lm.fineract;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.integration.fineract")
public record FineractProperties(
        String baseUrl,
        String username,
        String password,
        Integer defaultOfficeId,
        Integer legalFormId,
        String locale,
        String dateFormat,
        String transactionProcessingStrategyCode) {

    @Override
    public Integer defaultOfficeId() {
        return defaultOfficeId == null ? 1 : defaultOfficeId;
    }

    public Integer resolvedLegalFormId() {
        return legalFormId == null ? 1 : legalFormId;
    }
}
