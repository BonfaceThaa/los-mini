package com.credvenn.lm.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.bootstrap.super-admin")
public record BootstrapSuperAdminProperties(
        String username,
        String email,
        String password) {
}
