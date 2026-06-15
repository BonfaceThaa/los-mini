package com.credvenn.lm.payment;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepositPaymentRepository extends JpaRepository<DepositPayment, String> {

    boolean existsByMpesaReceiptNumber(String mpesaReceiptNumber);

    boolean existsByTenantIdAndMatchedApplicationIdAndStatusIn(
            String tenantId,
            String matchedApplicationId,
            Collection<DepositPaymentStatus> statuses);

    Optional<DepositPayment> findByIdAndTenantId(String id, String tenantId);

    List<DepositPayment> findAllByTenantIdOrderByTransactionTimeDescCreatedAtDesc(String tenantId);

    Page<DepositPayment> findAllByTenantId(String tenantId, Pageable pageable);

    Page<DepositPayment> findAllByTenantIdAndMatchedApplicationId(String tenantId, String matchedApplicationId, Pageable pageable);

    List<DepositPayment> findAllByTenantIdAndMatchedApplicationIdOrderByTransactionTimeDescCreatedAtDesc(
            String tenantId,
            String matchedApplicationId);

    Page<DepositPayment> findAllByTenantIdAndMatchedFineractClientId(
            String tenantId,
            String matchedFineractClientId,
            Pageable pageable);

    List<DepositPayment> findAllByTenantIdAndMatchedFineractClientIdOrderByTransactionTimeDescCreatedAtDesc(
            String tenantId,
            String matchedFineractClientId);
}
