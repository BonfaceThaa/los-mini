package com.credvenn.lm.statementinbox;

import com.credvenn.lm.common.exception.ConflictException;
import com.credvenn.lm.tenant.TenantService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantStatementInboxService {

    private final TenantStatementInboxRepository repository;
    private final TenantService tenantService;

    public TenantStatementInboxService(TenantStatementInboxRepository repository, TenantService tenantService) {
        this.repository = repository;
        this.tenantService = tenantService;
    }

    @Transactional
    public TenantStatementInboxDtos.TenantStatementInboxResponse create(String tenantId, TenantStatementInboxDtos.CreateTenantStatementInboxRequest request) {
        String email = request.emailAddress().trim().toLowerCase();
        repository.findByEmailAddressIgnoreCaseAndActiveTrue(email).ifPresent(existing -> {
            throw new ConflictException("Statement inbox email is already mapped to a tenant");
        });
        tenantService.getRequiredTenant(tenantId);
        TenantStatementInbox inbox = new TenantStatementInbox();
        inbox.setTenantId(tenantId);
        inbox.setEmailAddress(email);
        inbox.setDescription(request.description() == null ? null : request.description().trim());
        inbox.setActive(true);
        return toResponse(repository.save(inbox));
    }

    @Transactional(readOnly = true)
    public List<TenantStatementInboxDtos.TenantStatementInboxResponse> listByTenant(String tenantId) {
        tenantService.getRequiredTenant(tenantId);
        return repository.findAllByTenantIdOrderByEmailAddressAsc(tenantId).stream()
                .map(TenantStatementInboxService::toResponse)
                .toList();
    }

    static TenantStatementInboxDtos.TenantStatementInboxResponse toResponse(TenantStatementInbox inbox) {
        return new TenantStatementInboxDtos.TenantStatementInboxResponse(
                inbox.getId(),
                inbox.getTenantId(),
                inbox.getEmailAddress(),
                inbox.isActive(),
                inbox.getDescription(),
                inbox.getCreatedAt(),
                inbox.getUpdatedAt());
    }
}
