package com.credvenn.lm.statementinbox;

import java.io.IOException;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface InboundStatementStorageService {

    StoredInboundStatement store(String receiptId, MultipartFile file) throws IOException;

    Resource read(String relativePath) throws IOException;

    record StoredInboundStatement(String storedFilename, String relativePath, long fileSize, String contentType) {
    }
}
