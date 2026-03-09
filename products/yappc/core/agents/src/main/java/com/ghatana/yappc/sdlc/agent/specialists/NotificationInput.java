package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for Notification agent.
 *
 * @doc.type record
 * @doc.purpose Integration bridge agent for multi-channel notifications input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record NotificationInput(@NotNull String channel, @NotNull String recipientId, @NotNull String message, @NotNull Map<String, Object> templateData) {
  public NotificationInput {
    if (channel == null || channel.isEmpty()) {
      throw new IllegalArgumentException("channel cannot be null or empty");
    }
    if (recipientId == null || recipientId.isEmpty()) {
      throw new IllegalArgumentException("recipientId cannot be null or empty");
    }
    if (message == null || message.isEmpty()) {
      throw new IllegalArgumentException("message cannot be null or empty");
    }
    if (templateData == null) {
      templateData = Map.of();
    }
  }
}
