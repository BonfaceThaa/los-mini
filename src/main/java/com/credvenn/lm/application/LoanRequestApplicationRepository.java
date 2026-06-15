package com.credvenn.lm.application;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanRequestApplicationRepository extends JpaRepository<LoanRequestApplication, String> {

    List<LoanRequestApplication> findAllByTenantIdOrderByCreatedAtDesc(String tenantId);

    Page<LoanRequestApplication> findAllByTenantId(String tenantId, Pageable pageable);

    Optional<LoanRequestApplication> findByIdAndTenantId(String id, String tenantId);

    Optional<LoanRequestApplication> findByTenantIdAndFineractLoanId(String tenantId, String fineractLoanId);

    List<LoanRequestApplication> findAllByTenantIdAndStatusInAndCreatedAtAfterOrderByCreatedAtDesc(
            String tenantId,
            Collection<ApplicationStatus> statuses,
            Instant createdAt);

    List<LoanRequestApplication> findAllByTenantIdAndStatusAndFineractLoanIdIsNotNullOrderByCreatedAtDesc(
            String tenantId,
            ApplicationStatus status);

    List<LoanRequestApplication> findAllByTenantIdAndFineractLoanIdIsNotNullAndInstallmentAmountIsNotNullOrderByCreatedAtAsc(
            String tenantId);

    List<LoanRequestApplication> findAllByTenantIdAndStatusAndAssignedDeviceIdIsNotNullAndAssignedDeviceImei1IsNotNullAndFineractLoanIdIsNotNullOrderByCreatedAtAsc(
            String tenantId,
            ApplicationStatus status);

    long countByTenantIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(String tenantId, Instant start, Instant end);
}
