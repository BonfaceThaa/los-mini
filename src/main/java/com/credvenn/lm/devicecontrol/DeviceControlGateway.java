package com.credvenn.lm.devicecontrol;

import java.util.List;
import java.util.Map;

public interface DeviceControlGateway {

    record RuntimeConfig(
            String tenantId,
            String configId,
            String baseUrl,
            String clientCode,
            String username,
            String password,
            String channelCode,
            String paymentLinkTemplate) {
    }

    record ProviderCatalogItem(
            String code,
            String title) {
    }

    record BulkActionItem(
            String imei,
            String triggerId,
            String channelCode,
            String link,
            Map<String, String> templateFields) {
    }

    record BulkActionResult(
            String transactionId,
            String requestPayload,
            String responsePayload) {
    }

    record ActionResult(
            String providerReference,
            String requestPayload,
            String responsePayload) {
    }

    record OfflinePinResult(
            String message,
            String passcode,
            String requestPayload,
            String responsePayload) {
    }

    List<ProviderCatalogItem> fetchNotifications(RuntimeConfig config);

    List<ProviderCatalogItem> fetchNudges(RuntimeConfig config);

    BulkActionResult lock(RuntimeConfig config, String transactionId, List<BulkActionItem> items);

    BulkActionResult sendNotification(RuntimeConfig config, String transactionId, String notificationCode, List<BulkActionItem> items);

    BulkActionResult sendNudge(RuntimeConfig config, String transactionId, List<BulkActionItem> items);

    ActionResult unlock(RuntimeConfig config, String imei1, String triggerId);

    OfflinePinResult getOfflinePin(RuntimeConfig config, String passKey);
}
