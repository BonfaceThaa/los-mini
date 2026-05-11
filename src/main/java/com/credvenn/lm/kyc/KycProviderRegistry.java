package com.credvenn.lm.kyc;

import com.credvenn.lm.common.exception.BadRequestException;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class KycProviderRegistry {

    private final List<KycProvider> providers;
    private final KycProviderProperties properties;

    public KycProviderRegistry(List<KycProvider> providers, KycProviderProperties properties) {
        this.providers = providers;
        this.properties = properties;
    }

    public KycProvider currentProvider() {
        return providers.stream()
                .filter(provider -> provider.providerCode().equalsIgnoreCase(properties.provider()))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Unsupported KYC provider: " + properties.provider()));
    }
}
