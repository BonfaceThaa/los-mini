package com.credvenn.lm.tenant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantBrandingService {

    private static final String DEFAULT_PAYMENT_INSTRUCTIONS =
            "Enter your phone number and confirm the M-PESA prompt to pay your installment.";

    private final TenantRepository tenantRepository;
    private final TenantBrandingRepository brandingRepository;

    public TenantBrandingService(TenantRepository tenantRepository, TenantBrandingRepository brandingRepository) {
        this.tenantRepository = tenantRepository;
        this.brandingRepository = brandingRepository;
    }

    @Transactional(readOnly = true)
    public TenantBrandingDtos.TenantBrandingResponse getBranding(String tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new com.credvenn.lm.common.exception.NotFoundException("Tenant not found"));
        return brandingRepository.findByTenantId(tenantId)
                .map(branding -> toResponse(tenant, branding))
                .orElseGet(() -> defaultResponse(tenant));
    }

    @Transactional
    public TenantBrandingDtos.TenantBrandingResponse upsertBranding(
            String tenantId,
            TenantBrandingDtos.UpsertTenantBrandingRequest request) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new com.credvenn.lm.common.exception.NotFoundException("Tenant not found"));
        TenantBranding branding = brandingRepository.findByTenantId(tenantId).orElseGet(TenantBranding::new);
        branding.setTenantId(tenantId);
        branding.setDisplayName(normalize(request.displayName()));
        branding.setLogoUrl(normalize(request.logoUrl()));
        branding.setSupportPhone(normalize(request.supportPhone()));
        branding.setPaymentInstructions(normalize(request.paymentInstructions()));
        branding = brandingRepository.save(branding);
        return toResponse(tenant, branding);
    }

    private TenantBrandingDtos.TenantBrandingResponse toResponse(Tenant tenant, TenantBranding branding) {
        return new TenantBrandingDtos.TenantBrandingResponse(
                tenant.getId(),
                branding.getDisplayName() == null ? tenant.getName() : branding.getDisplayName(),
                branding.getLogoUrl(),
                branding.getSupportPhone(),
                branding.getPaymentInstructions() == null ? DEFAULT_PAYMENT_INSTRUCTIONS : branding.getPaymentInstructions(),
                branding.getCreatedAt(),
                branding.getUpdatedAt());
    }

    private TenantBrandingDtos.TenantBrandingResponse defaultResponse(Tenant tenant) {
        return new TenantBrandingDtos.TenantBrandingResponse(
                tenant.getId(),
                tenant.getName(),
                null,
                null,
                DEFAULT_PAYMENT_INSTRUCTIONS,
                tenant.getCreatedAt(),
                tenant.getUpdatedAt());
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }
}
