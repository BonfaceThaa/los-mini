package com.credvenn.lm.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.jwt")
public record AppSecurityProperties(
        String secret,
        String issuer,
        long accessTokenExpirationSeconds,
        long refreshTokenExpirationSeconds) {
}
