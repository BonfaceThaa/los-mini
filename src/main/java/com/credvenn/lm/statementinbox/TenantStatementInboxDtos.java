package com.credvenn.lm.statementinbox;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

public final class TenantStatementInboxDtos {

    private TenantStatementInboxDtos() {
    }

    public record CreateTenantStatementInboxRequest(
            @NotBlank @Email String emailAddress,
            String description) {
    }

    public record TenantStatementInboxResponse(
            String id,
            String tenantId,
            String emailAddress,
            boolean active,
            String description,
            Instant createdAt,
            Instant updatedAt) {
    }
}
