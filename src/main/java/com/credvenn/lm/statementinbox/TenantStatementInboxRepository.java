package com.credvenn.lm.statementinbox;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantStatementInboxRepository extends JpaRepository<TenantStatementInbox, String> {

    Optional<TenantStatementInbox> findByEmailAddressIgnoreCaseAndActiveTrue(String emailAddress);

    List<TenantStatementInbox> findAllByTenantIdOrderByEmailAddressAsc(String tenantId);
}
