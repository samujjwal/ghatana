package com.ghatana.yappc.agent.coordinator;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Request for platform delivery coordination.
 *
 * @param request high-level description of what to deliver
 * @param targetPhases SDLC phases to execute (e.g., "architecture", "implementation")
 * @param priority request priority level
 * @param metadata additional metadata
 * @doc.type record
 * @doc.purpose Input for platform delivery coordinator
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record DeliveryRequest(
    @NotNull String request,
    @NotNull List<String> targetPhases,
    @NotNull Priority priority,
    @NotNull Map<String, Object> metadata) {

  public DeliveryRequest {
    if (request == null || request.isEmpty()) {
      throw new IllegalArgumentException("request cannot be null or empty");
    }
    if (targetPhases == null || targetPhases.isEmpty()) {
      throw new IllegalArgumentException("targetPhases cannot be null or empty");
    }
    if (priority == null) {
      throw new IllegalArgumentException("priority cannot be null");
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }

  /** Creates a new request with default priority and empty metadata. */
  public DeliveryRequest(@NotNull String request, @NotNull List<String> targetPhases) {
    this(request, targetPhases, Priority.NORMAL, Map.of());
  }

  /** Request priority levels. */
  public enum Priority {
    LOW,
    NORMAL,
    HIGH,
    CRITICAL
  }
}
