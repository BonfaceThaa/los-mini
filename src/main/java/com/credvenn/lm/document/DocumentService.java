package com.credvenn.lm.document;

import com.credvenn.lm.application.LoanRequestApplicationRepository;
import com.credvenn.lm.common.logging.LoggingContext;
import com.credvenn.lm.common.exception.NotFoundException;
import com.credvenn.lm.statement.StatementDocumentUploadedEvent;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final ApplicationDocumentRepository documentRepository;
    private final DocumentStorageService documentStorageService;
    private final LoanRequestApplicationRepository applicationRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    public DocumentService(
            ApplicationDocumentRepository documentRepository,
            DocumentStorageService documentStorageService,
            LoanRequestApplicationRepository applicationRepository,
            ApplicationEventPublisher applicationEventPublisher) {
        this.documentRepository = documentRepository;
        this.documentStorageService = documentStorageService;
        this.applicationRepository = applicationRepository;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Transactional
    public DocumentDtos.ApplicationDocumentResponse upload(
            String tenantId,
            String applicationId,
            String documentType,
            String actor,
            MultipartFile file) throws IOException {
        try (LoggingContext.Scope ignored = LoggingContext.withTenantAndApplication(tenantId, applicationId)) {
            applicationRepository.findByIdAndTenantId(applicationId, tenantId)
                    .orElseThrow(() -> new NotFoundException("Loan request application not found"));
            return createDocumentRecord(
                    tenantId,
                    applicationId,
                    documentType,
                    actor,
                    file.getOriginalFilename() == null ? "upload.bin" : file.getOriginalFilename(),
                    documentStorageService.store(tenantId, applicationId, documentType, file));
        }
    }

    @Transactional
    public DocumentDtos.ApplicationDocumentResponse createFromStoredContent(
            String tenantId,
            String applicationId,
            String documentType,
            String actor,
            String originalFilename,
            String contentType,
            long fileSize,
            String sourceFilename,
            String sourceRelativePath,
            String sourceLabel) throws IOException {
        try (LoggingContext.Scope ignored = LoggingContext.withTenantAndApplication(tenantId, applicationId)) {
            applicationRepository.findByIdAndTenantId(applicationId, tenantId)
                    .orElseThrow(() -> new NotFoundException("Loan request application not found"));
            try (InputStream inputStream = documentStorageService.read(sourceRelativePath).getInputStream()) {
                return createDocumentRecord(
                        tenantId,
                        applicationId,
                        documentType,
                        actor,
                        originalFilename,
                        documentStorageService.store(
                                tenantId,
                                applicationId,
                                documentType,
                                sourceFilename,
                                contentType,
                                inputStream,
                                fileSize));
            } finally {
                log.info("Creating application document from source={} sourceRelativePath={}", sourceLabel, sourceRelativePath);
            }
        }
    }

    @Transactional
    public DocumentDtos.ApplicationDocumentResponse createFromInputStream(
            String tenantId,
            String applicationId,
            String documentType,
            String actor,
            String originalFilename,
            String contentType,
            long fileSize,
            String sourceFilename,
            InputStream inputStream,
            String sourceLabel) throws IOException {
        try (LoggingContext.Scope ignored = LoggingContext.withTenantAndApplication(tenantId, applicationId)) {
            applicationRepository.findByIdAndTenantId(applicationId, tenantId)
                    .orElseThrow(() -> new NotFoundException("Loan request application not found"));
            try {
                return createDocumentRecord(
                        tenantId,
                        applicationId,
                        documentType,
                        actor,
                        originalFilename,
                        documentStorageService.store(
                                tenantId,
                                applicationId,
                                documentType,
                                sourceFilename,
                                contentType,
                                inputStream,
                                fileSize));
            } finally {
                log.info("Creating application document from source={}", sourceLabel);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<DocumentDtos.ApplicationDocumentResponse> list(String tenantId, String applicationId) {
        applicationRepository.findByIdAndTenantId(applicationId, tenantId)
                .orElseThrow(() -> new NotFoundException("Loan request application not found"));
        return documentRepository.findAllByApplicationIdOrderByCreatedAtDesc(applicationId).stream().map(DocumentService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ApplicationDocument getRequired(String tenantId, String documentId) {
        return documentRepository.findByIdAndTenantId(documentId, tenantId)
                .orElseThrow(() -> new NotFoundException("Document not found"));
    }

    @Transactional(readOnly = true)
    public Resource loadContent(String tenantId, String documentId) throws IOException {
        ApplicationDocument document = getRequired(tenantId, documentId);
        log.debug("Loading authenticated document content documentId={} applicationId={}", documentId, document.getApplicationId());
        return documentStorageService.read(document.getRelativePath());
    }

    static DocumentDtos.ApplicationDocumentResponse toResponse(ApplicationDocument document) {
        return new DocumentDtos.ApplicationDocumentResponse(
                document.getId(),
                document.getApplicationId(),
                document.getDocumentType(),
                document.getOriginalFilename(),
                document.getContentType(),
                document.getFileSize(),
                document.getPublicUrl(),
                document.getCreatedAt());
    }

    private DocumentDtos.ApplicationDocumentResponse createDocumentRecord(
            String tenantId,
            String applicationId,
            String documentType,
            String actor,
            String originalFilename,
            DocumentStorageService.StoredDocument stored) {
        ApplicationDocument document = new ApplicationDocument();
        document.setTenantId(tenantId);
        document.setApplicationId(applicationId);
        document.setDocumentType(documentType);
        document.setOriginalFilename(originalFilename);
        document.setStoredFilename(stored.storedFilename());
        document.setRelativePath(stored.relativePath());
        document.setContentType(stored.contentType());
        document.setFileSize(stored.fileSize());
        document.setPublicUrl("/api/v1/documents/pending/content");
        document.setCreatedBy(actor);
        document = documentRepository.save(document);
        document.setPublicUrl("/api/v1/documents/%s/content".formatted(document.getId()));
        documentRepository.save(document);
        log.info(
                "Stored application document documentId={} documentType={} contentType={} fileSize={} relativePath={}",
                document.getId(),
                documentType,
                document.getContentType(),
                document.getFileSize(),
                document.getRelativePath());
        applicationEventPublisher.publishEvent(new StatementDocumentUploadedEvent(
                tenantId,
                applicationId,
                document.getId(),
                documentType,
                actor));
        return toResponse(document);
    }
}
