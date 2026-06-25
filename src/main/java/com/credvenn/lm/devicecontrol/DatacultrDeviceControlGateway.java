package com.credvenn.lm.devicecontrol;

import com.credvenn.lm.common.exception.BadRequestException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
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
        String url = absoluteUrl(config, "/v2/lifecycle/dem_%s/get_all_notifications/".formatted(config.clientCode()));
        log.info("Datacultr fetch notifications request method=GET url={} httpRequestBody=null", url);
        Object response = client(config).get()
                .uri("/v2/lifecycle/dem_{client}/get_all_notifications/", config.clientCode())
                .headers(headers -> headers.setBearerAuth(accessToken(config)))
                .retrieve()
                .body(Object.class);
        log.info("Datacultr fetch notifications response url={} httpResponseBody={}", url, stringify(response));
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
        String url = absoluteUrl(config, "/v2/lifecycle/dem_%s/get_nudges/".formatted(config.clientCode()));
        log.info("Datacultr fetch nudges request method=GET url={} httpRequestBody=null", url);
        Object response = client(config).get()
                .uri("/v2/lifecycle/dem_{client}/get_nudges/", config.clientCode())
                .headers(headers -> headers.setBearerAuth(accessToken(config)))
                .retrieve()
                .body(Object.class);
        log.info("Datacultr fetch nudges response url={} httpResponseBody={}", url, stringify(response));
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
        String url = absoluteUrl(config, "/v3/lifecycle/dem_%s/bulkapplylock/".formatted(config.clientCode()));
        log.info("Datacultr bulk lock request method=PUT url={} httpRequestBody={}", url, stringify(form));
        Object response = client(config).put()
                .uri("/v3/lifecycle/dem_{client}/bulkapplylock/", config.clientCode())
                .headers(headers -> headers.setBearerAuth(accessToken(config)))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form)
                .retrieve()
                .body(Object.class);
        log.info("Datacultr bulk lock response url={} httpResponseBody={}", url, stringify(response));
        return new BulkActionResult(transactionId, csv, stringify(response));
    }

    @Override
    public BulkActionResult sendNotification(RuntimeConfig config, String transactionId, String notificationCode, List<BulkActionItem> items) {
        String csv = buildNotificationCsv(items);
        MultiValueMap<String, Object> form = baseBulkForm(transactionId, csv, "DCNOTIF_%s_REQ.csv".formatted(timestamp()));
        form.add("code", notificationCode);
        String url = absoluteUrl(config, "/v3/lifecycle/dem_%s/bulk_custom_notification/".formatted(config.clientCode()));
        log.info("Datacultr bulk notification request method=POST url={} httpRequestBody={}", url, stringify(form));
        Object response = client(config).post()
                .uri("/v3/lifecycle/dem_{client}/bulk_custom_notification/", config.clientCode())
                .headers(headers -> headers.setBearerAuth(accessToken(config)))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form)
                .retrieve()
                .body(Object.class);
        log.info("Datacultr bulk notification response url={} httpResponseBody={}", url, stringify(response));
        return new BulkActionResult(transactionId, csv, stringify(response));
    }

    @Override
    public BulkActionResult sendNudge(RuntimeConfig config, String transactionId, List<BulkActionItem> items) {
        String csv = buildBulkCsv(items, false);
        MultiValueMap<String, Object> form = baseBulkForm(transactionId, csv, "DCNOTIF_%s_REQ.csv".formatted(timestamp()));
        String url = absoluteUrl(config, "/v3/lifecycle/dem_%s/bulkapplynudge/".formatted(config.clientCode()));
        log.info("Datacultr bulk nudge request method=PUT url={} httpRequestBody={}", url, stringify(form));
        Object response = client(config).put()
                .uri("/v3/lifecycle/dem_{client}/bulkapplynudge/", config.clientCode())
                .headers(headers -> headers.setBearerAuth(accessToken(config)))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form)
                .retrieve()
                .body(Object.class);
        log.info("Datacultr bulk nudge response url={} httpResponseBody={}", url, stringify(response));
        return new BulkActionResult(transactionId, csv, stringify(response));
    }

    @Override
    public BulkActionResult bulkUnlock(RuntimeConfig config, String transactionId, List<BulkActionItem> items) {
        String csv = buildBulkUnlockCsv(items);
        MultiValueMap<String, Object> form = baseBulkForm(transactionId, csv, "DCUNLOCK_%s_REQ.csv".formatted(timestamp()));
        String url = absoluteUrl(config, "/v3/lifecycle/dem_%s/bulkapplyunlock/".formatted(config.clientCode()));
        log.info("Datacultr bulk unlock request method=PUT url={} httpRequestBody={}", url, stringify(form));
        Object response = client(config).put()
                .uri("/v3/lifecycle/dem_{client}/bulkapplyunlock/", config.clientCode())
                .headers(headers -> headers.setBearerAuth(accessToken(config)))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form)
                .retrieve()
                .body(Object.class);
        log.info("Datacultr bulk unlock response url={} httpResponseBody={}", url, stringify(response));
        return new BulkActionResult(transactionId, csv, stringify(response));
    }

    @Override
    public BulkActionResult activateAutoLock(RuntimeConfig config, String transactionId, List<AutoLockItem> items) {
        String csv = buildAutoLockCsv(items);
        MultiValueMap<String, Object> form = baseBulkForm(transactionId, csv, "DCAUTOLOCK_%s_REQ.csv".formatted(timestamp()));
        String url = absoluteUrl(config, "/v3/dem_%s/auto_lock_activate/".formatted(config.clientCode()));
        log.info("Datacultr auto lock request method=PUT url={} httpRequestBody={}", url, stringify(form));
        Object response = client(config).put()
                .uri("/v3/dem_{client}/auto_lock_activate/", config.clientCode())
                .headers(headers -> headers.setBearerAuth(accessToken(config)))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form)
                .retrieve()
                .body(Object.class);
        log.info("Datacultr auto lock response url={} httpResponseBody={}", url, stringify(response));
        return new BulkActionResult(transactionId, csv, stringify(response));
    }

    @Override
    public ActionResult unlock(RuntimeConfig config, String imei1, String triggerId) {
        Map<String, Object> payload = Map.of(
                "imei1", imei1,
                "TriggerID", triggerId);
        String url = absoluteUrl(config, "/v3/lifecycle/dem_%s/applyunlock/".formatted(config.clientCode()));
        log.info("Datacultr unlock request method=POST url={} httpRequestBody={}", url, stringify(payload));
        Object response = client(config).post()
                .uri("/v3/lifecycle/dem_{client}/applyunlock/", config.clientCode())
                .headers(headers -> headers.setBearerAuth(accessToken(config)))
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(Object.class);
        log.info("Datacultr unlock response url={} httpResponseBody={}", url, stringify(response));
        return new ActionResult(triggerId, stringify(payload), stringify(response));
    }

    @Override
    public OfflinePinResult getOfflinePin(RuntimeConfig config, String passKey) {
        Map<String, Object> payload = Map.of("pass_key", passKey);
        String url = absoluteUrl(config, "/v2/lifecycle/dem_%s/get_device_passcode/".formatted(config.clientCode()));
        log.info("Datacultr offline PIN request method=POST url={} httpRequestBody={}", url, stringify(payload));
        Object response = client(config).post()
                .uri("/v2/lifecycle/dem_{client}/get_device_passcode/", config.clientCode())
                .headers(headers -> headers.setBearerAuth(accessToken(config)))
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(Object.class);
        log.info("Datacultr offline PIN response url={} httpResponseBody={}", url, stringify(response));
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
        Map<String, Object> payload = Map.of(
                "username", config.username(),
                "password", config.password());
        String url = absoluteUrl(config, "/token/");
        log.info("Datacultr login request method=POST url={} httpRequestBody={}", url, stringify(payload));
        Object response = client(config).post()
                .uri("/token/")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(Object.class);
        log.info("Datacultr login response url={} httpResponseBody={}", url, stringify(response));
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

    private String buildBulkUnlockCsv(List<BulkActionItem> items) {
        StringBuilder builder = new StringBuilder();
        builder.append("IMEI,TriggerID,Channel_Code\n");
        for (BulkActionItem item : items) {
            builder.append(csv(item.imei()))
                    .append(',')
                    .append(csv(item.triggerId()))
                    .append(',')
                    .append(csv(item.channelCode()))
                    .append('\n');
        }
        return builder.toString();
    }

    private String buildAutoLockCsv(List<AutoLockItem> items) {
        StringBuilder builder = new StringBuilder();
        builder.append("IMEI,DueDate,Time\n");
        for (AutoLockItem item : items) {
            builder.append(csv(item.imei()))
                    .append(',')
                    .append(csv(formatDueDate(item.dueDate())))
                    .append(',')
                    .append(csv(formatUtcTime(item.dueTimeUtc())))
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

    private String formatDueDate(LocalDate dueDate) {
        return dueDate == null ? null : DateTimeFormats.DATACULTR_DUE_DATE.format(dueDate);
    }

    private String formatUtcTime(LocalTime dueTimeUtc) {
        return dueTimeUtc == null ? null : DateTimeFormats.UTC_TIME.format(dueTimeUtc);
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String absoluteUrl(RuntimeConfig config, String path) {
        String baseUrl = trimTrailingSlash(config.baseUrl());
        return path.startsWith("/") ? baseUrl + path : baseUrl + "/" + path;
    }

    private record CachedToken(String accessToken, Instant expiresAt) {
    }

    private static final class DateTimeFormats {
        private static final java.time.format.DateTimeFormatter BASIC_TIMESTAMP =
                java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                        .withZone(java.time.ZoneId.of("Africa/Nairobi"));
        private static final DateTimeFormatter DATACULTR_DUE_DATE =
                DateTimeFormatter.ofPattern("dd/MM/yyyy");
        private static final DateTimeFormatter UTC_TIME =
                DateTimeFormatter.ofPattern("HH:mm");

        private DateTimeFormats() {
        }
    }
}
