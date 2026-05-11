package com.credvenn.lm.kyc;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KycCheckRepository extends JpaRepository<KycCheck, String> {

    Optional<KycCheck> findFirstByApplicationIdOrderByCreatedAtDesc(String applicationId);
}
