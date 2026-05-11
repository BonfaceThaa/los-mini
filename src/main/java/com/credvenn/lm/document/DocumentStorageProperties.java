package com.credvenn.lm.document;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.document.storage")
public record DocumentStorageProperties(
        String provider,
        LocalStorageProperties local) {

    public record LocalStorageProperties(String rootPath) {
    }
}
