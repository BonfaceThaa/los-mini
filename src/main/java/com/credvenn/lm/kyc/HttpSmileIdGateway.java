package com.credvenn.lm.kyc;

import com.credvenn.lm.application.LoanRequestApplication;
import com.credvenn.lm.common.exception.BadRequestException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class HttpSmileIdGateway implements SmileIdGateway {

    private static final Logger log = LoggerFactory.getLogger(HttpSmileIdGateway.class);
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .withZone(ZoneOffset.UTC);

    private final RestClient smileIdRestClient;
    private final KycProviderProperties properties;
    private final ObjectMapper objectMapper;

    public HttpSmileIdGateway(
            @Qualifier("smileIdRestClient") RestClient smileIdRestClient,
            KycProviderProperties properties,
            ObjectMapper objectMapper) {
        this.smileIdRestClient = smileIdRestClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public SmileIdDtos.VerifyResponse verifyBasicKyc(LoanRequestApplication application) {
        KycProviderProperties.SmileIdProperties smileId = requireSmileIdProperties();
        String partnerId = smileId.partnerId().trim();
        String timestamp = TIMESTAMP_FORMATTER.format(Instant.now().truncatedTo(ChronoUnit.MILLIS));
        String path = "/v2/verify";
        SmileIdDtos.VerifyRequest request = new SmileIdDtos.VerifyRequest(
                "rest_api",
                smileId.sourceSdkVersion(),
                partnerId,
                generateSignature(resolveSignatureApiKey(smileId), partnerId, timestamp),
                timestamp,
                "KE",
                application.getApplicantIdType().name(),
                application.getNationalId(),
                application.getApplicantFirstName(),
                application.getApplicantMiddleName(),
                application.getApplicantLastName(),
                application.getDob() == null ? null : application.getDob().toString(),
                application.getGender(),
                application.getPhoneNumber(),
                5,
                new SmileIdDtos.PartnerParams(
                        application.getId(),
                        application.getTenantId() + ":" + application.getNationalId()));
        return logAndParseBody(
                "Smile ID basic KYC",
                HttpMethod.POST,
                absoluteUrl(smileId.baseUrl(), path),
                request,
                smileIdRestClient.post()
                        .uri(path)
                        .headers(this::applyHeaders)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request),
                SmileIdDtos.VerifyResponse.class);
    }

    private void applyHeaders(HttpHeaders headers) {
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
    }

    private KycProviderProperties.SmileIdProperties requireSmileIdProperties() {
        KycProviderProperties.SmileIdProperties smileId = properties.smileId();
        if (smileId == null) {
            throw new BadRequestException("Smile ID KYC configuration is missing");
        }
        if (isBlank(smileId.baseUrl())
                || isBlank(smileId.partnerId())
                || isBlank(smileId.sourceSdkVersion())) {
            throw new BadRequestException("Smile ID KYC configuration is incomplete");
        }
        if (isBlank(resolveSignatureApiKey(smileId))) {
            throw new BadRequestException("Smile ID signature API key is not configured");
        }
        return smileId;
    }

    private String resolveSignatureApiKey(KycProviderProperties.SmileIdProperties smileId) {
        if (!isBlank(smileId.signatureApiKey())) {
            return smileId.signatureApiKey().trim();
        }
        return isBlank(smileId.apiKey()) ? null : smileId.apiKey().trim();
    }

    private String generateSignature(String apiKey, String partnerId, String timestamp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(apiKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update(timestamp.getBytes(StandardCharsets.UTF_8));
            mac.update(partnerId.getBytes(StandardCharsets.UTF_8));
            mac.update("sid_request".getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(mac.doFinal());
        } catch (GeneralSecurityException ex) {
            throw new BadRequestException("Smile ID signature generation failed");
        }
    }

    private <T> T logAndParseBody(
            String operation,
            HttpMethod method,
            String url,
            Object requestBody,
            RestClient.RequestHeadersSpec<?> requestSpec,
            Class<T> responseType) {
        try {
            log.info(
                    "{} method={} url={} httpRequestBody={}",
                    operation,
                    method.name(),
                    url,
                    serialize(requestBody));
            ResponseEntity<String> response = requestSpec.retrieve().toEntity(String.class);
            String body = response.getBody();
            log.info(
                    "{} httpStatus={} httpResponseBody={}",
                    operation,
                    response.getStatusCode().value(),
                    body);
            if (body == null || body.isBlank()) {
                throw new BadRequestException(operation + " returned an empty response body");
            }
            return objectMapper.readValue(body, responseType);
        } catch (RestClientResponseException ex) {
            log.warn(
                    "{} failed method={} url={} status={} httpResponseBody={}",
                    operation,
                    method.name(),
                    url,
                    ex.getStatusCode().value(),
                    ex.getResponseBodyAsString());
            throw ex;
        } catch (Exception ex) {
            if (ex instanceof BadRequestException badRequestException) {
                throw badRequestException;
            }
            throw new BadRequestException(operation + " response could not be parsed");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return String.valueOf(value);
        }
    }

    private String absoluteUrl(String baseUrl, String path) {
        if (baseUrl.endsWith("/") && path.startsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1) + path;
        }
        if (!baseUrl.endsWith("/") && !path.startsWith("/")) {
            return baseUrl + "/" + path;
        }
        return baseUrl + path;
    }
}
