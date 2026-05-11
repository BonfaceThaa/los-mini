package com.credvenn.lm.statementinbox;

import com.credvenn.lm.common.exception.NotFoundException;
import com.credvenn.lm.common.logging.LoggingContext;
import com.credvenn.lm.security.AuthenticatedUser;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class InboundStatementService {

    private static final Logger log = LoggerFactory.getLogger(InboundStatementService.class);

    private static final Set<InboundStatementMatchStatus> REVIEWABLE_STATUSES = Set.of(
            InboundStatementMatchStatus.UNMATCHED,
            InboundStatementMatchStatus.AMBIGUOUS,
            InboundStatementMatchStatus.FAILED);

    private final InboundStatementReceiptRepository receiptRepository;
    private final InboundStatementStorageService storageService;
    private final InboundStatementProcessor processor;
    private final ApplicationEventPublisher applicationEventPublisher;

    public InboundStatementService(
            InboundStatementReceiptRepository receiptRepository,
            InboundStatementStorageService storageService,
            InboundStatementProcessor processor,
            ApplicationEventPublisher applicationEventPublisher) {
        this.receiptRepository = receiptRepository;
        this.storageService = storageService;
        this.processor = processor;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Transactional
    public InboundStatementDtos.InboundReceiptAcceptedResponse acceptInboundStatement(
            String destinationEmail,
            String messageId,
            Instant receivedAt,
            MultipartFile file,
            String actor) throws IOException {
        InboundStatementReceipt receipt = new InboundStatementReceipt();
        receipt.setId(UUID.randomUUID().toString());
        receipt.setDestinationEmail(destinationEmail.trim().toLowerCase());
        receipt.setOriginalFilename(file.getOriginalFilename() == null ? "statement.pdf" : file.getOriginalFilename());
        receipt.setMessageId(messageId == null || messageId.isBlank() ? null : messageId.trim());
        receipt.setReceivedAt(receivedAt == null ? Instant.now() : receivedAt);
        receipt.setMatchStatus(InboundStatementMatchStatus.RECEIVED);

        InboundStatementStorageService.StoredInboundStatement stored = storageService.store(receipt.getId(), file);
        receipt.setStoredFilename(stored.storedFilename());
        receipt.setRelativePath(stored.relativePath());
        receipt.setContentType(stored.contentType());
        receipt.setFileSize(stored.fileSize());
        receipt = receiptRepository.save(receipt);

        try (LoggingContext.Scope ignored = LoggingContext.withApplication(receipt.getId())) {
            log.info("Accepted inbound statement destinationEmail={} filename={} receiptId={}",
                    receipt.getDestinationEmail(), receipt.getOriginalFilename(), receipt.getId());
            applicationEventPublisher.publishEvent(new InboundStatementAcceptedEvent(receipt.getId(), actor));
        }
        return new InboundStatementDtos.InboundReceiptAcceptedResponse(
                receipt.getId(),
                receipt.getMatchStatus(),
                "Inbound statement accepted for background processing.");
    }

    @Transactional(readOnly = true)
    public List<InboundStatementDtos.InboundStatementReceiptResponse> listReviewQueue(AuthenticatedUser actor) {
        List<InboundStatementReceipt> receipts = actor.tenantId() == null
                ? receiptRepository.findAllByMatchStatusInOrderByCreatedAtDesc(REVIEWABLE_STATUSES)
                : receiptRepository.findAllByTenantIdAndMatchStatusInOrderByCreatedAtDesc(actor.tenantId(), REVIEWABLE_STATUSES);
        return receipts.stream().map(InboundStatementService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public InboundStatementDtos.InboundStatementReceiptResponse getReceipt(AuthenticatedUser actor, String receiptId) {
        return toResponse(getRequiredReceipt(actor, receiptId));
    }

    @Transactional
    public InboundStatementDtos.InboundStatementReceiptResponse resolveReceipt(
            AuthenticatedUser actor,
            String receiptId,
            InboundStatementDtos.ResolveInboundStatementReceiptRequest request) {
        if (actor.tenantId() == null) {
            InboundStatementReceipt receipt = receiptRepository.findById(receiptId)
                    .orElseThrow(() -> new NotFoundException("Inbound statement receipt not found"));
            return toResponse(processor.resolveReceipt(
                    receipt.getTenantId(),
                    receiptId,
                    request.applicationId(),
                    actor.username(),
                    request.notes()));
        }
        return toResponse(processor.resolveReceipt(
                actor.tenantId(),
                receiptId,
                request.applicationId(),
                actor.username(),
                request.notes()));
    }

    @Transactional(readOnly = true)
    public Resource loadReceiptContent(AuthenticatedUser actor, String receiptId) throws IOException {
        InboundStatementReceipt receipt = getRequiredReceipt(actor, receiptId);
        return storageService.read(receipt.getRelativePath());
    }

    @Transactional(readOnly = true)
    public InboundStatementReceipt getRequiredReceipt(AuthenticatedUser actor, String receiptId) {
        if (actor.tenantId() == null) {
            return receiptRepository.findById(receiptId)
                    .orElseThrow(() -> new NotFoundException("Inbound statement receipt not found"));
        }
        return receiptRepository.findByIdAndTenantId(receiptId, actor.tenantId())
                .orElseThrow(() -> new NotFoundException("Inbound statement receipt not found"));
    }

    static InboundStatementDtos.InboundStatementReceiptResponse toResponse(InboundStatementReceipt receipt) {
        return new InboundStatementDtos.InboundStatementReceiptResponse(
                receipt.getId(),
                receipt.getTenantId(),
                receipt.getDestinationEmail(),
                receipt.getOriginalFilename(),
                receipt.getContentType(),
                receipt.getFileSize(),
                receipt.getMessageId(),
                receipt.getReceivedAt(),
                receipt.getExtractedPhoneToken(),
                receipt.getMatchStatus(),
                receipt.getMatchedApplicationId(),
                receipt.getMatchedDocumentId(),
                receipt.getFailureReason(),
                receipt.getReviewNotes(),
                receipt.getResolvedBy(),
                receipt.getResolvedAt(),
                receipt.getProcessingStartedAt(),
                receipt.getProcessedAt(),
                "/api/v1/statements/mpesa/inbox/%s/content".formatted(receipt.getId()),
                receipt.getCreatedAt(),
                receipt.getUpdatedAt());
    }
}
