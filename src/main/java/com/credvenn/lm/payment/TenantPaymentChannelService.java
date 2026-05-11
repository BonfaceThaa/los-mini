package com.credvenn.lm.payment;

import com.credvenn.lm.common.exception.ConflictException;
import com.credvenn.lm.tenant.TenantService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantPaymentChannelService {

    private final TenantPaymentChannelRepository repository;
    private final TenantService tenantService;

    public TenantPaymentChannelService(TenantPaymentChannelRepository repository, TenantService tenantService) {
        this.repository = repository;
        this.tenantService = tenantService;
    }

    @Transactional
    public PaymentDtos.TenantPaymentChannelResponse create(String tenantId, PaymentDtos.CreateTenantPaymentChannelRequest request) {
        String shortCode = request.shortCode().trim();
        repository.findByShortCodeAndActiveTrue(shortCode).ifPresent(existing -> {
            throw new ConflictException("Short code is already mapped to a tenant");
        });
        tenantService.getRequiredTenant(tenantId);
        TenantPaymentChannel channel = new TenantPaymentChannel();
        channel.setTenantId(tenantId);
        channel.setChannelType(PaymentChannelType.MPESA_PAYBILL);
        channel.setShortCode(shortCode);
        channel.setActive(true);
        channel.setDescription(request.description() == null ? null : request.description().trim());
        return toResponse(repository.save(channel));
    }

    @Transactional(readOnly = true)
    public List<PaymentDtos.TenantPaymentChannelResponse> list(String tenantId) {
        tenantService.getRequiredTenant(tenantId);
        return repository.findAllByTenantIdOrderByShortCodeAsc(tenantId).stream()
                .map(TenantPaymentChannelService::toResponse)
                .toList();
    }

    static PaymentDtos.TenantPaymentChannelResponse toResponse(TenantPaymentChannel channel) {
        return new PaymentDtos.TenantPaymentChannelResponse(
                channel.getId(),
                channel.getTenantId(),
                channel.getChannelType(),
                channel.getShortCode(),
                channel.isActive(),
                channel.getDescription(),
                channel.getCreatedAt(),
                channel.getUpdatedAt());
    }
}
