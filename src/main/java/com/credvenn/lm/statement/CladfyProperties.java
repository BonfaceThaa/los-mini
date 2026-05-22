package com.credvenn.lm.statement;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.integration.cladfy")
public record CladfyProperties(
        String baseUrl,
        String apiKey,
        String webhookBaseUrl) {
}
