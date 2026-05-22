package com.credvenn.lm.statement;

import com.credvenn.lm.application.LoanRequestApplication;
import com.credvenn.lm.common.exception.BadRequestException;
import com.credvenn.lm.document.ApplicationDocument;
import com.credvenn.lm.document.DocumentService;
import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpCladfyGateway implements CladfyGateway {

    private final RestClient cladfyRestClient;
    private final CladfyProperties properties;
    private final DocumentService documentService;
    private final StatementProviderProperties statementProviderProperties;

    public HttpCladfyGateway(
            @Qualifier("cladfyRestClient") RestClient cladfyRestClient,
            CladfyProperties properties,
            DocumentService documentService,
            StatementProviderProperties statementProviderProperties) {
        this.cladfyRestClient = cladfyRestClient;
        this.properties = properties;
        this.documentService = documentService;
        this.statementProviderProperties = statementProviderProperties;
    }

    @Override
    public StatementAnalysisSubmission submit(LoanRequestApplication application, ApplicationDocument document) {
        String providerCode = resolveProviderCode(document.getDocumentType());
        CladfyDtos.ClientResponse client = cladfyRestClient.post()
                .uri("/clients")
                .headers(this::applyApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CladfyDtos.CreateClientRequest(
                        application.getApplicantFirstName(),
                        application.getApplicantLastName(),
                        application.getPhoneNumber(),
                        application.getNationalId()))
                .retrieve()
                .body(CladfyDtos.ClientResponse.class);
        if (client == null || client.id() == null) {
            throw new BadRequestException("Cladfy client response did not include id");
        }

        try {
            Resource resource = documentService.loadContent(application.getTenantId(), document.getId());
            MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
            bodyBuilder.part("file", resource)
                    .filename(document.getOriginalFilename())
                    .contentType(document.getContentType() == null
                            ? MediaType.APPLICATION_PDF
                            : MediaType.parseMediaType(document.getContentType()));
            bodyBuilder.part("client_id", String.valueOf(client.id()));
            bodyBuilder.part("provider", providerCode);
            bodyBuilder.part("webhook", buildWebhookUrl());
            bodyBuilder.part("national_id", application.getNationalId());

            CladfyDtos.DocumentUploadResponse upload = cladfyRestClient.post()
                    .uri("/documents")
                    .headers(this::applyApiKey)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(bodyBuilder.build())
                    .retrieve()
                    .body(CladfyDtos.DocumentUploadResponse.class);
            if (upload == null || upload.id() == null) {
                throw new BadRequestException("Cladfy document response did not include id");
            }
            return new StatementAnalysisSubmission(
                    "CLADFY",
                    upload.status(),
                    String.valueOf(client.id()),
                    String.valueOf(upload.id()),
                    null,
                    "Statement submitted to Cladfy for analysis",
                    upload.toString());
        } catch (IOException ex) {
            throw new BadRequestException("Unable to read stored statement document");
        }
    }

    @Override
    public CladfyDtos.AnalysisResultsResponse fetchAnalysisResults(String clientId) {
        return cladfyRestClient.get()
                .uri("/clients/analysis_results")
                .headers(headers -> {
                    applyApiKey(headers);
                    headers.set("X-Client-Id", clientId);
                })
                .retrieve()
                .body(CladfyDtos.AnalysisResultsResponse.class);
    }

    @Override
    public CladfyDtos.CreditScoreResponse fetchCreditScore(String clientId) {
        return cladfyRestClient.get()
                .uri("/clients/{clientId}/scoring", clientId)
                .headers(this::applyApiKey)
                .retrieve()
                .body(CladfyDtos.CreditScoreResponse.class);
    }

    private void applyApiKey(HttpHeaders headers) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new BadRequestException("Cladfy API key is not configured");
        }
        headers.set("X-API-Key", properties.apiKey());
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
    }

    private String resolveProviderCode(String documentType) {
        String providerCode = statementProviderProperties.documentTypeProviders().get(documentType);
        if (providerCode == null || providerCode.isBlank()) {
            throw new BadRequestException("No Cladfy provider mapping configured for document type " + documentType);
        }
        return providerCode;
    }

    private String buildWebhookUrl() {
        String baseUrl = properties.webhookBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new BadRequestException("Cladfy webhook base URL is not configured");
        }
        return baseUrl.replaceAll("/$", "") + "/api/v1/public/integrations/cladfy/webhook";
    }
}
