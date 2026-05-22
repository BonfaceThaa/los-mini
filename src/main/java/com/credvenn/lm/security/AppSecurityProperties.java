package com.credvenn.lm.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.jwt")
public record AppSecurityProperties(
        String secret,
        String issuer,
        long accessTokenExpirationSeconds,
        long refreshTokenExpirationSeconds,
        String serviceSecret,
        String serviceIssuer) {

    public AppSecurityProperties {
        if (serviceSecret == null || serviceSecret.isBlank()) {
            serviceSecret = secret;
        }
        if (serviceIssuer == null || serviceIssuer.isBlank()) {
            serviceIssuer = issuer;
        }
    }
}
