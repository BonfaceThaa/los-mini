package com.credvenn.lm.payment;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MpesaStkPushRequestRepository extends JpaRepository<MpesaStkPushRequest, String> {

    java.util.Optional<MpesaStkPushRequest> findByCheckoutRequestId(String checkoutRequestId);
}
