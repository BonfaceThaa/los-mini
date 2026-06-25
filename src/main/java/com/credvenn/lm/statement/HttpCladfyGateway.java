package com.credvenn.lm.statement;

import com.credvenn.lm.application.LoanRequestApplication;
import com.credvenn.lm.common.exception.BadRequestException;
import com.credvenn.lm.document.ApplicationDocument;
import com.credvenn.lm.document.DocumentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class HttpCladfyGateway implements CladfyGateway {

    private static final Logger log = LoggerFactory.getLogger(HttpCladfyGateway.class);

    private final RestClient cladfyRestClient;
    private final CladfyProperties properties;
    private final DocumentService documentService;
    private final StatementProviderProperties statementProviderProperties;
    private final ObjectMapper objectMapper;

    public HttpCladfyGateway(
            @Qualifier("cladfyRestClient") RestClient cladfyRestClient,
            CladfyProperties properties,
            DocumentService documentService,
            StatementProviderProperties statementProviderProperties,
            ObjectMapper objectMapper) {
        this.cladfyRestClient = cladfyRestClient;
        this.properties = properties;
        this.documentService = documentService;
        this.statementProviderProperties = statementProviderProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public StatementAnalysisSubmission submit(LoanRequestApplication application, ApplicationDocument document) {
        String providerCode = resolveProviderCode(document.getDocumentType());
        CladfyDtos.ClientResponse client = createOrReuseClient(application);
        if (client == null || client.id() == null) {
            throw new BadRequestException("Cladfy client response did not include id");
        }
        log.info(
                "Cladfy client ready clientId={} phoneNumber={} nationalId={}",
                client.id(),
                client.phone_number(),
                application.getNationalId());

        try {
            Resource resource = documentService.loadContent(application.getTenantId(), document.getId());
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            HttpHeaders fileHeaders = new HttpHeaders();
            fileHeaders.setContentType(document.getContentType() == null
                    ? MediaType.APPLICATION_PDF
                    : MediaType.parseMediaType(document.getContentType()));
            fileHeaders.setContentDispositionFormData("file", document.getOriginalFilename());
            body.add("file", new HttpEntity<>(resource, fileHeaders));
            body.add("client_id", String.valueOf(client.id()));
            body.add("provider", providerCode);
            body.add("webhook", buildWebhookUrl());
            body.add("national_id", application.getNationalId());
            if (application.getStatementOtp() != null && !application.getStatementOtp().isBlank()) {
                body.add("password", application.getStatementOtp());
            }

            CladfyDtos.DocumentUploadResponse upload = logAndParseBody(
                    "Cladfy document upload",
                    HttpMethod.POST,
                    "/documents",
                    requestPath -> cladfyRestClient.post()
                            .uri(requestPath)
                            .headers(this::applyApiKey)
                            .contentType(MediaType.MULTIPART_FORM_DATA)
                            .body(body),
                    CladfyDtos.DocumentUploadResponse.class);
            log.info(
                    "Cladfy document upload response clientId={} documentId={} status={} provider={}",
                    client.id(),
                    upload == null ? null : upload.id(),
                    upload == null ? null : upload.status(),
                    upload == null ? null : upload.provider());
            if (upload == null || upload.id() == null) {
                throw new BadRequestException("Cladfy document response did not include id");
            }
            return new StatementAnalysisSubmission(
                    "CLADFY",
                    upload.status() == null ? null : String.valueOf(upload.status()),
                    String.valueOf(client.id()),
                    String.valueOf(upload.id()),
                    null,
                    "Statement submitted to Cladfy for analysis",
                    upload.toString(),
                    upload.client() == null || upload.client().scoring() == null ? null : upload.client().scoring().score(),
                    upload.client() == null || upload.client().scoring() == null || upload.client().scoring().risk_tier() == null
                            ? null
                            : upload.client().scoring().risk_tier().tier(),
                    resolveUploadScoredAt(upload));
        } catch (IOException ex) {
            throw new BadRequestException("Unable to read stored statement document");
        }
    }

    @Override
    public CladfyDtos.DocumentStatusResponse fetchDocumentStatus(String documentId) {
        CladfyDtos.DocumentStatusResponse response = logAndParseBody(
                "Cladfy document status",
                HttpMethod.GET,
                "/documents/{documentId}/status",
                requestPath -> cladfyRestClient.get()
                        .uri(requestPath, documentId)
                        .headers(this::applyApiKey),
                CladfyDtos.DocumentStatusResponse.class);
        log.info(
                "Cladfy document status response documentId={} status={} failReason={} passwordProvided={}",
                documentId,
                response == null ? null : response.status(),
                response == null ? null : response.fail_reason(),
                response == null ? null : response.password_provided());
        return response;
    }

    @Override
    public CladfyDtos.AnalysisResultsResponse fetchAnalysisResults(String clientId) {
        CladfyDtos.AnalysisResultsResponse response = logAndParseBody(
                "Cladfy analysis results",
                HttpMethod.GET,
                "/clients/analysis_results",
                requestPath -> cladfyRestClient.get()
                        .uri(requestPath)
                        .headers(headers -> {
                            applyApiKey(headers);
                            headers.set("X-Client-Id", clientId);
                        }),
                CladfyDtos.AnalysisResultsResponse.class);
        log.info(
                "Cladfy analysis results response clientId={} documentId={} transactionCount={} totalIn={} totalOut={}",
                clientId,
                response == null || response.document() == null ? null : response.document().id(),
                response == null || response.summary() == null ? null : response.summary().transaction_count(),
                response == null || response.summary() == null ? null : response.summary().total_in(),
                response == null || response.summary() == null ? null : response.summary().total_out());
        return response;
    }

    @Override
    public CladfyDtos.CreditScoreResponse fetchCreditScore(String clientId) {
        CladfyDtos.CreditScoreResponse response = logAndParseBody(
                "Cladfy credit score",
                HttpMethod.GET,
                "/clients/{clientId}/scoring",
                requestPath -> cladfyRestClient.get()
                        .uri(requestPath, clientId)
                        .headers(this::applyApiKey),
                CladfyDtos.CreditScoreResponse.class);
        log.info(
                "Cladfy credit score response clientId={} score={} riskTier={} scoredAt={}",
                clientId,
                response == null ? null : response.score(),
                response == null || response.risk_tier() == null ? null : response.risk_tier().tier(),
                response == null ? null : response.scored_at());
        return response;
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

    private CladfyDtos.ClientResponse createOrReuseClient(LoanRequestApplication application) {
        try {
            CladfyDtos.ClientResponse response = logAndParseBody(
                    "Cladfy create client",
                    HttpMethod.POST,
                    "/clients",
                    requestPath -> cladfyRestClient.post()
                            .uri(requestPath)
                            .headers(this::applyApiKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(new CladfyDtos.CreateClientRequest(
                                    application.getApplicantFirstName(),
                                    application.getApplicantLastName(),
                                    application.getPhoneNumber(),
                                    application.getNationalId())),
                    CladfyDtos.ClientResponse.class);
            log.info(
                    "Cladfy create client response clientId={} fullName={} phoneNumber={}",
                    response == null ? null : response.id(),
                    response == null ? null : response.full_name(),
                    response == null ? null : response.phone_number());
            return response;
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() == HttpStatus.CONFLICT) {
                CladfyDtos.CreateClientConflictResponse conflict = parseConflictResponse(ex.getResponseBodyAsString());
                if (conflict != null && conflict.existing_client() != null && conflict.existing_client().id() != null) {
                    CladfyDtos.ExistingClient existing = conflict.existing_client();
                    log.info(
                            "Cladfy create client conflict reusedExistingClientId={} detail={} message={}",
                            existing.id(),
                            conflict.detail(),
                            conflict.message());
                    return new CladfyDtos.ClientResponse(
                            existing.id(),
                            existing.first_name(),
                            existing.last_name(),
                            existing.full_name(),
                            existing.phone_number());
                }
            }
            log.warn(
                    "Cladfy create client failed status={} responseBody={}",
                    ex.getStatusCode().value(),
                    ex.getResponseBodyAsString());
            throw ex;
        }
    }

    private CladfyDtos.CreateClientConflictResponse parseConflictResponse(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(body, CladfyDtos.CreateClientConflictResponse.class);
        } catch (Exception ex) {
            return null;
        }
    }

    private <T> T logAndParseBody(
            String operation,
            HttpMethod method,
            String path,
            Function<String, RestClient.RequestHeadersSpec<?>> requestFactory,
            Class<T> responseType) {
        return executeRequest(operation, method, path, requestFactory, responseType, true);
    }

    private <T> T executeRequest(
            String operation,
            HttpMethod method,
            String path,
            Function<String, RestClient.RequestHeadersSpec<?>> requestFactory,
            Class<T> responseType,
            boolean allowTrailingSlashRetry) {
        try {
            ResponseEntity<String> response = requestFactory.apply(path).retrieve().toEntity(String.class);
            String body = response.getBody();
            HttpStatusCode statusCode = response.getStatusCode();
            String location = response.getHeaders().getFirst(HttpHeaders.LOCATION);
            if (statusCode.is3xxRedirection()) {
                log.warn(
                        "{} redirected method={} path={} httpStatus={} location={} httpResponseBody={}",
                        operation,
                        method.name(),
                        path,
                        statusCode.value(),
                        location,
                        body);
                if (allowTrailingSlashRetry && shouldRetryWithTrailingSlash(path, location)) {
                    String retryPath = path + "/";
                    log.info(
                            "{} retrying with trailing slash method={} originalPath={} retryPath={}",
                            operation,
                            method.name(),
                            path,
                            retryPath);
                    return executeRequest(operation, method, retryPath, requestFactory, responseType, false);
                }
                throw new BadRequestException(
                        operation + " returned redirect status "
                                + statusCode.value()
                                + (location == null || location.isBlank() ? "" : " to " + location));
            }
            log.info(
                    "{} httpStatus={} httpResponseBody={}",
                    operation,
                    statusCode.value(),
                    body);
            if (body == null || body.isBlank()) {
                return null;
            }
            return objectMapper.readValue(body, responseType);
        } catch (RestClientResponseException ex) {
            log.warn(
                    "{} failed method={} path={} status={} httpResponseBody={}",
                    operation,
                    method.name(),
                    path,
                    ex.getStatusCode().value(),
                    ex.getResponseBodyAsString());
            throw ex;
        } catch (IOException ex) {
            throw new BadRequestException(operation + " response could not be parsed");
        }
    }

    private boolean shouldRetryWithTrailingSlash(String path, String location) {
        if (path == null || path.isBlank() || path.endsWith("/")) {
            return false;
        }
        if (location == null || location.isBlank()) {
            return true;
        }
        return location.endsWith(path + "/");
    }

    private Instant resolveUploadScoredAt(CladfyDtos.DocumentUploadResponse upload) {
        if (upload == null || upload.client() == null || upload.client().scoring() == null) {
            return null;
        }
        CladfyDtos.UploadScoring scoring = upload.client().scoring();
        Instant direct = parseInstant(scoring.scored_at());
        if (direct != null) {
            return direct;
        }
        return scoring.features() == null ? null : parseInstant(scoring.features().scored_at());
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        try {
            return Instant.parse(trimmed);
        } catch (Exception ignored) {
        }
        try {
            return java.time.OffsetDateTime.parse(trimmed).toInstant();
        } catch (Exception ignored) {
        }
        try {
            return LocalDateTime.parse(trimmed).toInstant(ZoneOffset.UTC);
        } catch (Exception ignored) {
        }
        return null;
    }
}
