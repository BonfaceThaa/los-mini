package com.credvenn.lm.payment;

import com.credvenn.lm.common.exception.BadRequestException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class HttpDarajaC2bRegistrationGateway implements DarajaC2bRegistrationGateway {

    private static final Logger log = LoggerFactory.getLogger(HttpDarajaC2bRegistrationGateway.class);

    private final DarajaAccessTokenGateway accessTokenGateway;
    private final RestClient.Builder restClientBuilder;

    public HttpDarajaC2bRegistrationGateway(DarajaAccessTokenGateway accessTokenGateway, RestClient.Builder restClientBuilder) {
        this.accessTokenGateway = accessTokenGateway;
        this.restClientBuilder = restClientBuilder;
    }

    @Override
    public C2bRegistrationResult registerUrls(
            TenantMpesaIntegrationConfig config,
            String confirmationUrl,
            String validationUrl,
            String responseType) {
        String accessToken = accessTokenGateway.fetchC2bRegistrationAccessToken(config);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ShortCode", config.businessShortCode());
        payload.put("ResponseType", responseType);
        payload.put("ConfirmationURL", confirmationUrl);
        if (validationUrl != null && !validationUrl.isBlank()) {
            payload.put("ValidationURL", validationUrl);
        }
        String baseUrl = HttpDarajaAccessTokenGateway.baseUrl(config.environment());
        String url = baseUrl + "/mpesa/c2b/v2/registerurl";
        log.info("Daraja C2B register URL request method=POST url={} httpRequestBody={}", url, payload);

        Map<?, ?> response;
        try {
            response = restClientBuilder
                    .baseUrl(baseUrl)
                    .build()
                    .post()
                    .uri("/mpesa/c2b/v2/registerurl")
                    .headers(headers -> {
                        headers.setBearerAuth(accessToken);
                        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientResponseException ex) {
            log.warn(
                    "Daraja C2B register URL failed url={} status={} responseBody={}",
                    url,
                    ex.getStatusCode(),
                    ex.getResponseBodyAsString());
            throw ex;
        }
        log.info("Daraja C2B register URL response url={} httpResponseBody={}", url, response);

        String responseCode = text(response == null ? null : response.get("ResponseCode"));
        if (responseCode == null) {
            throw new BadRequestException("Daraja C2B register URL response did not include ResponseCode");
        }
        return new C2bRegistrationResult(
                text(response.get("OriginatorCoversationID")),
                responseCode,
                text(response.get("ResponseDescription")));
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
