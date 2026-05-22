package com.credvenn.lm.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public record AppEncryptionProperties(String encryptionKey) {
}
