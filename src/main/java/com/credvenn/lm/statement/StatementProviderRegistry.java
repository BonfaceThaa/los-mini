package com.credvenn.lm.statement;

import com.credvenn.lm.common.exception.BadRequestException;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class StatementProviderRegistry {

    private final List<StatementAnalysisProvider> providers;
    private final StatementProviderProperties properties;

    public StatementProviderRegistry(List<StatementAnalysisProvider> providers, StatementProviderProperties properties) {
        this.providers = providers;
        this.properties = properties;
    }

    public StatementAnalysisProvider currentProvider() {
        return providers.stream()
                .filter(provider -> provider.providerCode().equalsIgnoreCase(properties.provider()))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Unsupported statement analysis provider: " + properties.provider()));
    }
}
