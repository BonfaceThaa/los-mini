package com.credvenn.lm.payment;

import com.credvenn.lm.common.exception.BadRequestException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpDarajaC2bRegistrationGateway implements DarajaC2bRegistrationGateway {

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
        String accessToken = accessTokenGateway.fetchAccessToken(config);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ShortCode", config.businessShortCode());
        payload.put("ResponseType", responseType);
        payload.put("ConfirmationURL", confirmationUrl);
        payload.put("ValidationURL", validationUrl == null || validationUrl.isBlank() ? confirmationUrl : validationUrl);

        Map<?, ?> response = restClientBuilder
                .baseUrl(HttpDarajaAccessTokenGateway.baseUrl(config.environment()))
                .build()
                .post()
                .uri("/mpesa/c2b/v1/registerurl")
                .headers(headers -> {
                    headers.setBearerAuth(accessToken);
                    headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
                })
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(Map.class);

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
