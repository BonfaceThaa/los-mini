package com.credvenn.lm.statementinbox;

import com.credvenn.lm.common.exception.BadRequestException;
import org.springframework.stereotype.Component;

@Component
public class InboundStatementFilenameParser {

    public String extractPhoneToken(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new BadRequestException("Inbound statement filename is required");
        }
        int extensionIndex = filename.lastIndexOf('.');
        int underscoreIndex = filename.lastIndexOf('_');
        if (extensionIndex <= 0 || underscoreIndex < 0 || underscoreIndex >= extensionIndex) {
            throw new BadRequestException("Inbound statement filename format is invalid");
        }
        String token = filename.substring(underscoreIndex + 1, extensionIndex).trim();
        if (token.isBlank()) {
            throw new BadRequestException("Inbound statement filename did not include a phone token");
        }
        return token;
    }
}
