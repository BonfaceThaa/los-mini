package com.credvenn.lm.document;

import java.io.IOException;
import java.io.InputStream;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface DocumentStorageService {

    StoredDocument store(String tenantId, String applicationId, String documentType, MultipartFile file) throws IOException;

    StoredDocument store(
            String tenantId,
            String applicationId,
            String documentType,
            String originalFilename,
            String contentType,
            InputStream inputStream,
            long fileSize) throws IOException;

    Resource read(String relativePath) throws IOException;

    record StoredDocument(String storedFilename, String relativePath, long fileSize, String contentType) {
    }
}
