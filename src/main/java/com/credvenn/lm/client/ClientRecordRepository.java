package com.credvenn.lm.client;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientRecordRepository extends JpaRepository<ClientRecord, String> {

    Optional<ClientRecord> findByTenantIdAndFineractClientId(String tenantId, String fineractClientId);

    Optional<ClientRecord> findByTenantIdAndNationalId(String tenantId, String nationalId);

    Page<ClientRecord> findAllByTenantId(String tenantId, Pageable pageable);
}
