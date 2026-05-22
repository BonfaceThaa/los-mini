package com.credvenn.lm.devicecontrol;

import com.credvenn.lm.common.exception.BadRequestException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class DatacultrDeviceControlGateway implements DeviceControlGateway {

    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;
    private final Map<String, CachedToken> tokenCache = new ConcurrentHashMap<>();

    public DatacultrDeviceControlGateway(RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this.restClientBuilder = restClientBuilder;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<ProviderCatalogItem> fetchNotifications(RuntimeConfig config) {

        log.info("DatacultrDeviceControlGateway-fetchNotifications ...");
        log.info(config.toString());
        Object response = client(config).get()
                .uri("/v2/lifecycle/dem_{client}/get_all_notifications/", config.clientCode())
                .headers(headers -> headers.setBearerAuth(accessToken(config)))
                .retrieve()
                .body(Object.class);
        log.info("reponse ...");
        log.info(response.toString());
        if (!(response instanceof Map<?, ?> map) || !(map.get("notifications") instanceof List<?> notifications)) {
            return List.of();
        }
        List<ProviderCatalogItem> items = new ArrayList<>();
        for (Object item : notifications) {
            if (item instanceof Map<?, ?> notification) {
                items.add(new ProviderCatalogItem(text(notification.get("notification_code")), text(notification.get("title"))));
            }
        }
        return items;
    }

    @Override
    public List<ProviderCatalogItem> fetchNudges(RuntimeConfig config) {
        Object response = client(config).get()
                .uri("/v2/lifecycle/dem_{client}/get_nudges/", config.clientCode())
                .headers(headers -> headers.setBearerAuth(accessToken(config)))
                .retrieve()
                .body(Object.class);
        if (!(response instanceof Map<?, ?> map) || !(map.get("nudges") instanceof List<?> nudges)) {
            return List.of();
        }
        List<ProviderCatalogItem> items = new ArrayList<>();
        for (Object item : nudges) {
            if (item instanceof Map<?, ?> nudge) {
                items.add(new ProviderCatalogItem(text(nudge.get("code")), text(nudge.get("title"))));
            }
        }
        return items;
    }

    @Override
    public BulkActionResult lock(RuntimeConfig config, String transactionId, List<BulkActionItem> items) {
        String csv = buildBulkCsv(items, true);
        MultiValueMap<String, Object> form = baseBulkForm(transactionId, csv, "DC_LOCK_%s_REQ.csv".formatted(timestamp()));
        Object response = client(config).put()
                .uri("/v3/lifecycle/dem_{client}/bulkapplylock/", config.clientCode())
                .headers(headers -> headers.setBearerAuth(accessToken(config)))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form)
                .retrieve()
                .body(Object.class);
        return new BulkActionResult(transactionId, csv, stringify(response));
    }

    @Override
    public BulkActionResult sendNotification(RuntimeConfig config, String transactionId, String notificationCode, List<BulkActionItem> items) {
        String csv = buildNotificationCsv(items);
        MultiValueMap<String, Object> form = baseBulkForm(transactionId, csv, "DCNOTIF_%s_REQ.csv".formatted(timestamp()));
        form.add("code", notificationCode);
        Object response = client(config).post()
                .uri("/v3/lifecycle/dem_{client}/bulk_custom_notification/", config.clientCode())
                .headers(headers -> headers.setBearerAuth(accessToken(config)))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form)
                .retrieve()
                .body(Object.class);
        return new BulkActionResult(transactionId, csv, stringify(response));
    }

    @Override
    public BulkActionResult sendNudge(RuntimeConfig config, String transactionId, List<BulkActionItem> items) {
        String csv = buildBulkCsv(items, false);
        MultiValueMap<String, Object> form = baseBulkForm(transactionId, csv, "DCNOTIF_%s_REQ.csv".formatted(timestamp()));
        Object response = client(config).put()
                .uri("/v3/lifecycle/dem_{client}/bulkapplynudge/", config.clientCode())
                .headers(headers -> headers.setBearerAuth(accessToken(config)))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form)
                .retrieve()
                .body(Object.class);
        return new BulkActionResult(transactionId, csv, stringify(response));
    }

    @Override
    public ActionResult unlock(RuntimeConfig config, String imei1, String triggerId) {
        Map<String, Object> payload = Map.of(
                "imei1", imei1,
                "TriggerID", triggerId);
        Object response = client(config).post()
                .uri("/v3/lifecycle/dem_{client}/applyunlock/", config.clientCode())
                .headers(headers -> headers.setBearerAuth(accessToken(config)))
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(Object.class);
        return new ActionResult(triggerId, stringify(payload), stringify(response));
    }

    @Override
    public OfflinePinResult getOfflinePin(RuntimeConfig config, String passKey) {
        Map<String, Object> payload = Map.of("pass_key", passKey);
        Object response = client(config).post()
                .uri("/v2/lifecycle/dem_{client}/get_device_passcode/", config.clientCode())
                .headers(headers -> headers.setBearerAuth(accessToken(config)))
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(Object.class);
        String message = null;
        String passcode = null;
        if (response instanceof Map<?, ?> map) {
            message = text(map.get("message"));
            passcode = text(map.get("passcode"));
        }
        return new OfflinePinResult(message, passcode, stringify(payload), stringify(response));
    }

    private RestClient client(RuntimeConfig config) {
        return restClientBuilder.baseUrl(trimTrailingSlash(config.baseUrl())).build();
    }

    private String accessToken(RuntimeConfig config) {
        CachedToken cached = tokenCache.get(config.configId());
        if (cached != null && cached.expiresAt().isAfter(Instant.now().plusSeconds(60))) {
            return cached.accessToken();
        }
        return login(config);
    }

    private String login(RuntimeConfig config) {
        Object response = client(config).post()
                .uri("/token/")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "username", config.username(),
                        "password", config.password()))
                .retrieve()
                .body(Object.class);
        if (!(response instanceof Map<?, ?> map)) {
            throw new BadRequestException("Datacultr login did not return a token payload");
        }
        String access = text(map.get("access"));
        if (access == null || access.isBlank()) {
            throw new BadRequestException("Datacultr login did not return an access token");
        }
        tokenCache.put(config.configId(), new CachedToken(access, Instant.now().plusSeconds(1800)));
        return access;
    }

    private MultiValueMap<String, Object> baseBulkForm(String transactionId, String csv, String filename) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("TransactionId", transactionId);
        form.add("TransactionID", transactionId);
        form.add("file", namedResource(csv, filename));
        return form;
    }

    private InputStreamResource namedResource(String content, String filename) {
        return new InputStreamResource(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))) {
            @Override
            public String getFilename() {
                return filename;
            }

            @Override
            public long contentLength() {
                return content.getBytes(StandardCharsets.UTF_8).length;
            }

            @Override
            public InputStream getInputStream() {
                return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
            }
        };
    }

    private String buildBulkCsv(List<BulkActionItem> items, boolean includeOptionalColumns) {
        StringBuilder builder = new StringBuilder();
        if (includeOptionalColumns) {
            builder.append("IMEI,TriggerID,Channel_Code,Link\n");
            for (BulkActionItem item : items) {
                builder.append(csv(item.imei()))
                        .append(',')
                        .append(csv(item.triggerId()))
                        .append(',')
                        .append(csv(item.channelCode()))
                        .append(',')
                        .append(csv(item.link()))
                        .append('\n');
            }
            return builder.toString();
        }
        builder.append("IMEI,TriggerID\n");
        for (BulkActionItem item : items) {
            builder.append(csv(item.imei()))
                    .append(',')
                    .append(csv(item.triggerId()))
                    .append('\n');
        }
        return builder.toString();
    }

    private String buildNotificationCsv(List<BulkActionItem> items) {
        if (items.isEmpty() || items.get(0).templateFields() == null || items.get(0).templateFields().isEmpty()) {
            return buildBulkCsv(items, false);
        }
        List<String> dynamicHeaders = new ArrayList<>(items.get(0).templateFields().keySet());
        StringBuilder builder = new StringBuilder();
        builder.append("IMEI,TriggerID");
        for (String header : dynamicHeaders) {
            builder.append(',').append(csv(header));
        }
        builder.append('\n');
        for (BulkActionItem item : items) {
            builder.append(csv(item.imei()))
                    .append(',')
                    .append(csv(item.triggerId()));
            Map<String, String> fields = item.templateFields() == null ? Map.of() : item.templateFields();
            for (String header : dynamicHeaders) {
                builder.append(',').append(csv(fields.get(header)));
            }
            builder.append('\n');
        }
        return builder.toString();
    }

    private String csv(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        return '"' + escaped + '"';
    }

    private String stringify(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return String.valueOf(value);
        }
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String timestamp() {
        return DateTimeFormats.BASIC_TIMESTAMP.format(Instant.now());
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private record CachedToken(String accessToken, Instant expiresAt) {
    }

    private static final class DateTimeFormats {
        private static final java.time.format.DateTimeFormatter BASIC_TIMESTAMP =
                java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                        .withZone(java.time.ZoneId.of("Africa/Nairobi"));

        private DateTimeFormats() {
        }
    }
}
