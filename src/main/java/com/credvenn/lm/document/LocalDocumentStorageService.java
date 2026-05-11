package com.credvenn.lm.document;

import com.credvenn.lm.common.exception.BadRequestException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class LocalDocumentStorageService implements DocumentStorageService {

    private final DocumentStorageProperties properties;

    public LocalDocumentStorageService(DocumentStorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public StoredDocument store(String tenantId, String applicationId, String documentType, MultipartFile file) throws IOException {
        return store(
                tenantId,
                applicationId,
                documentType,
                file.getOriginalFilename() == null ? "upload.bin" : file.getOriginalFilename(),
                file.getContentType(),
                file.getInputStream(),
                file.getSize());
    }

    @Override
    public StoredDocument store(
            String tenantId,
            String applicationId,
            String documentType,
            String originalName,
            String contentType,
            InputStream inputStream,
            long fileSize) throws IOException {
        if (!"LOCAL".equalsIgnoreCase(properties.provider())) {
            throw new BadRequestException("Only LOCAL document storage is currently implemented");
        }
        String extension = originalName.contains(".") ? originalName.substring(originalName.lastIndexOf('.')) : "";
        String storedFilename = UUID.randomUUID() + extension;
        Path rootPath = Path.of(properties.local().rootPath()).toAbsolutePath().normalize();
        Path destinationDirectory = rootPath.resolve(tenantId).resolve(applicationId).resolve(documentType).normalize();
        Files.createDirectories(destinationDirectory);
        Path destinationFile = destinationDirectory.resolve(storedFilename).normalize();
        Files.copy(inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING);
        String relativePath = rootPath.relativize(destinationFile).toString().replace('\\', '/');
        return new StoredDocument(storedFilename, relativePath, fileSize, contentType);
    }

    @Override
    public Resource read(String relativePath) throws IOException {
        Path rootPath = Path.of(properties.local().rootPath()).toAbsolutePath().normalize();
        Path fullPath = rootPath.resolve(relativePath).normalize();
        return new UrlResource(fullPath.toUri());
    }
}
