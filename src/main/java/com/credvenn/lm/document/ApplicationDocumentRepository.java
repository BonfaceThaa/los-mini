package com.credvenn.lm.document;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationDocumentRepository extends JpaRepository<ApplicationDocument, String> {

    List<ApplicationDocument> findAllByApplicationIdOrderByCreatedAtDesc(String applicationId);

    Optional<ApplicationDocument> findByIdAndTenantId(String id, String tenantId);
}
