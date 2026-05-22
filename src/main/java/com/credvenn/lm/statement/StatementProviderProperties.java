package com.credvenn.lm.statement;

import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.integration.statement")
public record StatementProviderProperties(
        String provider,
        List<String> autoTriggerDocumentTypes,
        Map<String, String> documentTypeProviders) {

    public StatementProviderProperties {
        autoTriggerDocumentTypes = autoTriggerDocumentTypes == null ? List.of() : autoTriggerDocumentTypes.stream().map(String::trim).toList();
        documentTypeProviders = documentTypeProviders == null ? Map.of() : documentTypeProviders;
    }
}
