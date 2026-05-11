package com.credvenn.lm.kyc;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.integration.kyc")
public record KycProviderProperties(String provider) {
}
