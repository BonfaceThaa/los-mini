package com.credvenn.lm.document;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface ApplicationDocumentRepository extends JpaRepository<ApplicationDocument, String> {

    List<ApplicationDocument> findAllByApplicationIdOrderByCreatedAtDesc(String applicationId);

    Optional<ApplicationDocument> findByIdAndTenantId(String id, String tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from ApplicationDocument d where d.id = :id and d.tenantId = :tenantId")
    Optional<ApplicationDocument> findByIdAndTenantIdForUpdate(@Param("id") String id, @Param("tenantId") String tenantId);

    Optional<ApplicationDocument> findFirstByApplicationIdAndDocumentTypeOrderByCreatedAtDesc(String applicationId, String documentType);
}
