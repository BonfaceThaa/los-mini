package com.credvenn.lm.statementinbox;

import com.credvenn.lm.common.exception.BadRequestException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class LocalInboundStatementStorageService implements InboundStatementStorageService {

    private final InboundStatementProperties properties;

    public LocalInboundStatementStorageService(InboundStatementProperties properties) {
        this.properties = properties;
    }

    @Override
    public StoredInboundStatement store(String receiptId, MultipartFile file) throws IOException {
        String originalName = file.getOriginalFilename() == null ? "upload.bin" : file.getOriginalFilename();
        if (!originalName.toLowerCase().endsWith(".pdf")) {
            throw new BadRequestException("Only PDF inbound statements are supported");
        }
        String extension = originalName.substring(originalName.lastIndexOf('.'));
        String storedFilename = UUID.randomUUID() + extension;
        Path rootPath = Path.of(properties.local().rootPath()).toAbsolutePath().normalize();
        Path destinationDirectory = rootPath.resolve("pending").resolve(receiptId).normalize();
        Files.createDirectories(destinationDirectory);
        Path destinationFile = destinationDirectory.resolve(storedFilename).normalize();
        Files.copy(file.getInputStream(), destinationFile, StandardCopyOption.REPLACE_EXISTING);
        String relativePath = rootPath.relativize(destinationFile).toString().replace('\\', '/');
        return new StoredInboundStatement(storedFilename, relativePath, file.getSize(), file.getContentType());
    }

    @Override
    public Resource read(String relativePath) throws IOException {
        Path rootPath = Path.of(properties.local().rootPath()).toAbsolutePath().normalize();
        Path fullPath = rootPath.resolve(relativePath).normalize();
        return new UrlResource(fullPath.toUri());
    }
}
