package com.credvenn.lm.fineracttemplate;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GlAccountTemplateRepository extends JpaRepository<GlAccountTemplate, String> {

    Optional<GlAccountTemplate> findByTemplateCodeIgnoreCaseAndTenantId(String templateCode, String tenantId);

    Optional<GlAccountTemplate> findByFineractGlAccountIdAndTenantId(Long fineractGlAccountId, String tenantId);

    boolean existsByTemplateCodeIgnoreCaseAndTenantId(String templateCode, String tenantId);

    List<GlAccountTemplate> findAllByTenantIdOrderByTemplateCodeAsc(String tenantId);

    Page<GlAccountTemplate> findAllByTenantId(String tenantId, Pageable pageable);
}
