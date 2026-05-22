package com.credvenn.lm.fineracttemplate;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountingRuleTemplateRepository extends JpaRepository<AccountingRuleTemplate, String> {

    Optional<AccountingRuleTemplate> findByTemplateCodeIgnoreCase(String templateCode);

    Optional<AccountingRuleTemplate> findByFineractRuleId(Long fineractRuleId);

    List<AccountingRuleTemplate> findAllByOrderByTemplateCodeAsc();
}
