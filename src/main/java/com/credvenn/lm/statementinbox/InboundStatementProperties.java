package com.credvenn.lm.statementinbox;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.statement-inbox")
public record InboundStatementProperties(
        Local local) {

    public InboundStatementProperties {
        local = local == null ? new Local("./data/inbound-statements") : local;
    }

    public record Local(String rootPath) {
        public Local {
            rootPath = (rootPath == null || rootPath.isBlank()) ? "./data/inbound-statements" : rootPath;
        }
    }
}
