package com.credvenn.lm.payment;

import com.credvenn.lm.common.exception.BadRequestException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpMpesaStkPushGateway implements MpesaStkPushGateway {

    private static final ZoneId MPESA_ZONE = ZoneId.of("Africa/Nairobi");
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final DarajaAccessTokenGateway accessTokenGateway;
    private final RestClient.Builder restClientBuilder;

    public HttpMpesaStkPushGateway(DarajaAccessTokenGateway accessTokenGateway, RestClient.Builder restClientBuilder) {
        this.accessTokenGateway = accessTokenGateway;
        this.restClientBuilder = restClientBuilder;
    }

    @Override
    public InitiationResult initiate(InitiationCommand command) {
        String accessToken = accessTokenGateway.fetchAccessToken(command.config());
        String timestamp = ZonedDateTime.now(MPESA_ZONE).format(TIMESTAMP_FORMAT);
        String password = Base64.getEncoder().encodeToString(
                (command.config().businessShortCode() + command.config().encryptedPasskey() + timestamp)
                        .getBytes(StandardCharsets.UTF_8));

        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("BusinessShortCode", command.config().businessShortCode());
        payload.put("Password", password);
        payload.put("Timestamp", timestamp);
        payload.put("TransactionType", "CustomerPayBillOnline");
        payload.put("Amount", command.amount().setScale(0, java.math.RoundingMode.HALF_UP).toPlainString());
        payload.put("PartyA", command.normalizedPhoneNumber());
        payload.put("PartyB", command.config().businessShortCode());
        payload.put("PhoneNumber", command.normalizedPhoneNumber());
        payload.put("CallBackURL", command.config().callbackUrl());
        payload.put("AccountReference", command.billReference());
        payload.put("TransactionDesc", command.transactionDescription());

        Map<?, ?> response = restClientBuilder
                .baseUrl(HttpDarajaAccessTokenGateway.baseUrl(command.config().environment()))
                .build()
                .post()
                .uri("/mpesa/stkpush/v1/processrequest")
                .headers(headers -> {
                    headers.setBearerAuth(accessToken);
                    headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
                })
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(Map.class);

        Object responseCode = response == null ? null : response.get("ResponseCode");
        if (responseCode == null) {
            throw new BadRequestException("Daraja STK response did not include ResponseCode");
        }
        return new InitiationResult(
                stringValue(response.get("MerchantRequestID")),
                stringValue(response.get("CheckoutRequestID")),
                String.valueOf(responseCode),
                stringValue(response.get("ResponseDescription")),
                stringValue(response.get("CustomerMessage")),
                response == null ? null : response.toString());
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
