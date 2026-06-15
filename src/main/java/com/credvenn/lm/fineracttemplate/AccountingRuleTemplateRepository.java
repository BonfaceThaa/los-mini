package com.credvenn.lm.fineracttemplate;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountingRuleTemplateRepository extends JpaRepository<AccountingRuleTemplate, String> {

    Optional<AccountingRuleTemplate> findByTemplateCodeIgnoreCaseAndTenantId(String templateCode, String tenantId);

    Optional<AccountingRuleTemplate> findByFineractRuleIdAndTenantId(Long fineractRuleId, String tenantId);

    List<AccountingRuleTemplate> findAllByTenantIdOrderByTemplateCodeAsc(String tenantId);

    Page<AccountingRuleTemplate> findAllByTenantId(String tenantId, Pageable pageable);
}
