package com.credvenn.lm.config;

import com.credvenn.lm.document.DocumentStorageProperties;
import com.credvenn.lm.fineract.FineractProperties;
import com.credvenn.lm.kyc.KycProviderProperties;
import com.credvenn.lm.security.AppSecurityProperties;
import com.credvenn.lm.security.BootstrapSuperAdminProperties;
import com.credvenn.lm.statementinbox.InboundStatementProperties;
import com.credvenn.lm.statement.StatementProviderProperties;
import java.util.Map;
import java.util.concurrent.Executor;
import org.slf4j.MDC;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

@Configuration
@EnableAsync
@EnableConfigurationProperties({
        AppSecurityProperties.class,
        BootstrapSuperAdminProperties.class,
        DocumentStorageProperties.class,
        KycProviderProperties.class,
        StatementProviderProperties.class,
        FineractProperties.class,
        InboundStatementProperties.class
})
public class AsyncIntegrationConfig {

    @Bean
    RestClient fineractRestClient(RestClient.Builder builder, FineractProperties properties) {
        return builder.baseUrl(properties.baseUrl()).build();
    }

    @Bean
    TaskDecorator mdcTaskDecorator() {
        return runnable -> {
            Map<String, String> contextMap = MDC.getCopyOfContextMap();
            return () -> {
                Map<String, String> previous = MDC.getCopyOfContextMap();
                try {
                    if (contextMap == null || contextMap.isEmpty()) {
                        MDC.clear();
                    } else {
                        MDC.setContextMap(contextMap);
                    }
                    runnable.run();
                } finally {
                    if (previous == null || previous.isEmpty()) {
                        MDC.clear();
                    } else {
                        MDC.setContextMap(previous);
                    }
                }
            };
        };
    }

    @Bean(name = "taskExecutor")
    Executor taskExecutor(TaskDecorator mdcTaskDecorator) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("lm-async-");
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(15);
        executor.setTaskDecorator(mdcTaskDecorator);
        executor.initialize();
        return executor;
    }
}
