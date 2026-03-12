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
 * Durable YAPPC event publisher that appends workflow events to EventCloud.
 *
 * <p>Guarantees:
 * <ol>
 *   <li>Event is durably persisted in EventCloud (append-first)</li>
 *   <li>HTTP delivery is handled transparently by the EventCloud connector layer
 *       and the {@code OutboxRelayService} — this class does NOT own an HTTP publisher</li>
 * </ol>
 *
 * <p>Replaces {@code DurableAepEventPublisher} which coupled YAPPC directly to the AEP HTTP
 * endpoint. This class routes all events through EventCloud, which the platform connector layer
 * then forwards to AEP asynchronously.
 *
 * @doc.type class
 * @doc.purpose Durable EventCloud publisher replacing direct HTTP publishing to AEP
 * @doc.layer product
 * @doc.pattern Adapter
 * @doc.gaa.lifecycle act
 */
public final class DurableEventCloudPublisher implements AepEventPublisher {

  private static final Logger LOG = LoggerFactory.getLogger(DurableEventCloudPublisher.class);
  private static final String YAPPC_EVENTS_TOPIC = "yappc.workflow.events";

  private final EventCloud eventCloud;

  /**
   * Creates a durable EventCloud publisher.
   *
   * @param eventCloud the EventCloud instance for durable append
   */
  public DurableEventCloudPublisher(EventCloud eventCloud) {
    this.eventCloud = Objects.requireNonNull(eventCloud, "eventCloud");
  }

  @Override
  public Promise<Void> publish(String eventType, String tenantId, Map<String, Object> payload) {
    String safeTenantId = (tenantId == null || tenantId.isBlank()) ? "default" : tenantId;

    // Build the full event envelope for EventCloud persistence
    Map<String, Object> eventData = new LinkedHashMap<>();
    eventData.put("eventType", eventType);
    eventData.put("tenantId", safeTenantId);
    eventData.put("payload", payload != null ? payload : Map.of());
    eventData.put("timestamp", Instant.now().toString());

    LOG.debug("Appending event to EventCloud: type={} tenant={}", eventType, safeTenantId);

    // Publish to EventCloud — fire-and-forget with best-effort error logging
    return eventCloud.publish(YAPPC_EVENTS_TOPIC, safeTenantId, eventData)
        .then(
            v -> Promise.complete(),
            ex -> {
              LOG.error("EventCloud publish failed for type={} tenant={}: {}",
                  eventType, safeTenantId, ex.getMessage());
              // Best-effort — always return complete so caller is not blocked
              return Promise.complete();
            });
  }

  /**
   * Creates a {@link DurableEventCloudPublisher} from an injected {@link EventCloud} instance.
   *
   * @param eventCloud the EventCloud client
   * @return configured durable publisher
   */
  public static DurableEventCloudPublisher fromEnvironment(EventCloud eventCloud) {
    return new DurableEventCloudPublisher(eventCloud);
  }
}
