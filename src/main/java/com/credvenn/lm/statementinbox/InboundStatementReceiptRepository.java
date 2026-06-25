package com.credvenn.lm.statementinbox;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InboundStatementReceiptRepository extends JpaRepository<InboundStatementReceipt, String> {

    List<InboundStatementReceipt> findAllByTenantIdAndExtractedPhoneTokenAndMatchStatusInOrderByCreatedAtAsc(
            String tenantId,
            String extractedPhoneToken,
            Collection<InboundStatementMatchStatus> statuses);

    List<InboundStatementReceipt> findAllByTenantIdAndMatchStatusInOrderByCreatedAtDesc(
            String tenantId,
            Collection<InboundStatementMatchStatus> statuses);

    List<InboundStatementReceipt> findAllByMatchStatusInOrderByCreatedAtDesc(Collection<InboundStatementMatchStatus> statuses);

    Optional<InboundStatementReceipt> findByIdAndTenantId(String id, String tenantId);
}
