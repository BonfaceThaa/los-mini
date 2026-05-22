package com.credvenn.lm.payment;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MpesaPaymentReceiptRepository extends JpaRepository<MpesaPaymentReceipt, String> {

    boolean existsByMpesaReceiptNumber(String mpesaReceiptNumber);

    Optional<MpesaPaymentReceipt> findByMpesaReceiptNumber(String mpesaReceiptNumber);

    Optional<MpesaPaymentReceipt> findByMatchedApplicationIdAndMpesaReceiptNumber(String matchedApplicationId, String mpesaReceiptNumber);

    List<MpesaPaymentReceipt> findAllByProcessingStatusInOrderByCreatedAtDesc(Collection<MpesaPaymentProcessingStatus> statuses);
}
