package com.credvenn.lm.application;

public record ApplicationCreatedEvent(
        String tenantId,
        String applicationId,
        String actor) {
}
