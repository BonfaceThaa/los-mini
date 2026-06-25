package com.credvenn.lm.payment;

import com.credvenn.lm.common.exception.BadRequestException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpDarajaAccessTokenGateway implements DarajaAccessTokenGateway {

    private static final Logger log = LoggerFactory.getLogger(HttpDarajaAccessTokenGateway.class);

    private final RestClient.Builder restClientBuilder;

    public HttpDarajaAccessTokenGateway(RestClient.Builder restClientBuilder) {
        this.restClientBuilder = restClientBuilder;
    }

    @Override
    public String fetchAccessToken(TenantMpesaIntegrationConfig config) {
        String baseUrl = baseUrl(config.environment());
        return fetchAccessToken(config, baseUrl, "/oauth/v1/generate?grant_type=client_credentials");
    }

    @Override
    public String fetchC2bRegistrationAccessToken(TenantMpesaIntegrationConfig config) {
        return fetchAccessToken(
                config,
                "https://api.safaricom.co.ke",
                "/oauth/v2/generate?grant_type=client_credentials");
    }

    static String baseUrl(DarajaEnvironment environment) {
        return environment == DarajaEnvironment.PRODUCTION
                ? "https://api.safaricom.co.ke"
                : "https://sandbox.safaricom.co.ke";
    }

    private String fetchAccessToken(TenantMpesaIntegrationConfig config, String baseUrl, String uri) {
        String credentials = config.encryptedConsumerKey() + ":" + config.encryptedConsumerSecret();
        String url = baseUrl + uri;
        log.info("Daraja access token request method=GET url={} httpRequestBody=null", url);
        Map<?, ?> response = restClientBuilder
                .baseUrl(baseUrl)
                .build()
                .get()
                .uri(uri)
                .headers(headers -> {
                    headers.set(HttpHeaders.AUTHORIZATION, "Basic " + Base64.getEncoder()
                            .encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
                    headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
                })
                .retrieve()
                .body(Map.class);
        log.info("Daraja access token response url={} httpResponseBody={}", url, response);
        Object accessToken = response == null ? null : response.get("access_token");
        if (accessToken == null) {
            throw new BadRequestException("Daraja access token response did not include access_token");
        }
        return String.valueOf(accessToken);
    }
}
