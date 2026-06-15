package com.credvenn.lm.payment;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantPaymentChannelRepository extends JpaRepository<TenantPaymentChannel, String> {

    Optional<TenantPaymentChannel> findByShortCodeAndActiveTrue(String shortCode);

    Optional<TenantPaymentChannel> findByTenantIdAndShortCodeAndActiveTrue(String tenantId, String shortCode);

    List<TenantPaymentChannel> findAllByTenantIdOrderByShortCodeAsc(String tenantId);

    Optional<TenantPaymentChannel> findFirstByTenantIdAndChannelTypeAndActiveTrueOrderByCreatedAtAsc(
            String tenantId,
            PaymentChannelType channelType);
}
