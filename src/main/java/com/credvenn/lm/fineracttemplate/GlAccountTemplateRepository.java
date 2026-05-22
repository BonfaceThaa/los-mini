package com.credvenn.lm.fineracttemplate;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GlAccountTemplateRepository extends JpaRepository<GlAccountTemplate, String> {

    Optional<GlAccountTemplate> findByTemplateCodeIgnoreCase(String templateCode);

    Optional<GlAccountTemplate> findByFineractGlAccountId(Long fineractGlAccountId);

    boolean existsByTemplateCodeIgnoreCase(String templateCode);

    List<GlAccountTemplate> findAllByOrderByTemplateCodeAsc();
}
