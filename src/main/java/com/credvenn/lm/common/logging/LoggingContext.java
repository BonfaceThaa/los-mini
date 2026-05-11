package com.credvenn.lm.common.logging;

import java.util.HashMap;
import java.util.Map;
import org.slf4j.MDC;

public final class LoggingContext {

    public static final String CORRELATION_ID = "correlationId";
    public static final String TENANT_ID = "tenantId";
    public static final String APPLICATION_ID = "applicationId";
    public static final String USER_ID = "userId";
    public static final String USERNAME = "username";

    private LoggingContext() {
    }

    public static Scope withCorrelationId(String correlationId) {
        return with(Map.of(CORRELATION_ID, correlationId));
    }

    public static Scope withTenant(String tenantId) {
        return with(Map.of(TENANT_ID, tenantId));
    }

    public static Scope withApplication(String applicationId) {
        return with(Map.of(APPLICATION_ID, applicationId));
    }

    public static Scope withTenantAndApplication(String tenantId, String applicationId) {
        Map<String, String> values = new HashMap<>();
        values.put(TENANT_ID, tenantId);
        values.put(APPLICATION_ID, applicationId);
        return with(values);
    }

    public static Scope withUser(String userId, String username) {
        Map<String, String> values = new HashMap<>();
        values.put(USER_ID, userId);
        values.put(USERNAME, username);
        return with(values);
    }

    public static String maskNationalId(String nationalId) {
        if (nationalId == null || nationalId.isBlank()) {
            return "n/a";
        }
        String trimmed = nationalId.trim();
        if (trimmed.length() <= 4) {
            return "****";
        }
        return "*".repeat(trimmed.length() - 4) + trimmed.substring(trimmed.length() - 4);
    }

    public static String maskPhone(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return "n/a";
        }
        String trimmed = phoneNumber.trim();
        if (trimmed.length() <= 4) {
            return "****";
        }
        return "*".repeat(trimmed.length() - 4) + trimmed.substring(trimmed.length() - 4);
    }

    private static Scope with(Map<String, String> values) {
        Map<String, String> previous = new HashMap<>();
        values.forEach((key, value) -> {
            previous.put(key, MDC.get(key));
            if (value == null || value.isBlank()) {
                MDC.remove(key);
            } else {
                MDC.put(key, value);
            }
        });
        return () -> values.keySet().forEach(key -> {
            String previousValue = previous.get(key);
            if (previousValue == null) {
                MDC.remove(key);
            } else {
                MDC.put(key, previousValue);
            }
        });
    }

    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
