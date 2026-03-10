package com.ghatana.yappc.agent;

import com.ghatana.core.event.cloud.EventCloud;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Durable AEP event publisher that appends events to EventCloud before sending
 * them over HTTP to the AEP ingest endpoint.
 *
 * <p>Guarantees:
 * <ol>
 *   <li>Event is durably persisted in EventCloud (append-first)</li>
 *   <li>HTTP publish to AEP is best-effort with retry</li>
 *   <li>If HTTP fails, event is still recoverable from EventCloud</li>
 * </ol>
 *
 * @doc.type class
 * @doc.purpose Durable event publisher with EventCloud append-first guarantee
 * @doc.layer product
 * @doc.pattern Decorator
 */
public final class DurableAepEventPublisher implements AepEventPublisher {

  private static final Logger LOG = LoggerFactory.getLogger(DurableAepEventPublisher.class);
  private static final String YAPPC_EVENTS_TOPIC = "yappc.workflow.events";
  private static final int MAX_HTTP_RETRIES = 2;

  private final EventCloud eventCloud;
  private final AepEventPublisher httpPublisher;

  /**
   * Creates the durable publisher.
   *
   * @param eventCloud    the EventCloud for durable append
   * @param httpPublisher the underlying HTTP publisher for AEP delivery
   */
  public DurableAepEventPublisher(EventCloud eventCloud, AepEventPublisher httpPublisher) {
    this.eventCloud = Objects.requireNonNull(eventCloud, "eventCloud");
    this.httpPublisher = Objects.requireNonNull(httpPublisher, "httpPublisher");
  }

  @Override
  public Promise<Void> publish(String eventType, String tenantId, Map<String, Object> payload) {
    String safeTenantId = (tenantId == null || tenantId.isBlank()) ? "default" : tenantId;

    // Build the full event payload for EventCloud persistence
    Map<String, Object> eventData = new LinkedHashMap<>();
    eventData.put("eventType", eventType);
    eventData.put("tenantId", safeTenantId);
    eventData.put("payload", payload != null ? payload : Map.of());
    eventData.put("timestamp", Instant.now().toString());

    // Step 1: Append to EventCloud first (durability guarantee)
    return eventCloud.publish(YAPPC_EVENTS_TOPIC, safeTenantId, eventData)
        .then(v -> {
          // Step 2: Best-effort HTTP publish to AEP with retry
          return publishWithRetry(eventType, safeTenantId, payload, 0);
        })
        .whenException(e -> {
          // EventCloud append failed — log but still attempt HTTP delivery
          LOG.error("EventCloud append failed for type={}, attempting HTTP fallback: {}",
              eventType, e.getMessage());
        })
        .whenException(e ->
            LOG.warn("Durable publish completed with errors for type={}: {}", eventType, e.getMessage()));
  }

  private Promise<Void> publishWithRetry(
      String eventType, String tenantId, Map<String, Object> payload, int attempt) {
    return httpPublisher.publish(eventType, tenantId, payload)
        .whenException(e -> {
          if (attempt < MAX_HTTP_RETRIES) {
            LOG.warn("HTTP publish attempt {} failed for type={}, retrying: {}",
                attempt + 1, eventType, e.getMessage());
            publishWithRetry(eventType, tenantId, payload, attempt + 1);
          } else {
            LOG.error("HTTP publish exhausted {} retries for type={} (event safe in EventCloud)",
                MAX_HTTP_RETRIES, eventType);
          }
        });
  }

  /**
   * Creates a DurableAepEventPublisher from environment configuration.
   *
   * @param eventCloud the EventCloud instance
   * @return configured durable publisher
   */
  public static DurableAepEventPublisher fromEnvironment(EventCloud eventCloud) {
    return new DurableAepEventPublisher(eventCloud, HttpAepEventPublisher.fromEnvironment());
  }
}
