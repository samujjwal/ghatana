package com.ghatana.yappc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from Notification agent.
 *
 * @doc.type record
 * @doc.purpose Integration bridge agent for multi-channel notifications output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record NotificationOutput(@NotNull String notificationId, @NotNull String deliveryStatus, @NotNull Map<String, Object> metadata) {
  public NotificationOutput {
    if (notificationId == null || notificationId.isEmpty()) {
      throw new IllegalArgumentException("notificationId cannot be null or empty");
    }
    if (deliveryStatus == null || deliveryStatus.isEmpty()) {
      throw new IllegalArgumentException("deliveryStatus cannot be null or empty");
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
