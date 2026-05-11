package com.credvenn.lm.statement;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.integration.statement")
public record StatementProviderProperties(String provider) {
}
