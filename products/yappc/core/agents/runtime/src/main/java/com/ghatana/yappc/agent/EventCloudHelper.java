package com.ghatana.yappc.agent;

import com.ghatana.core.event.cloud.EventCloud;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.Map;

/**
 * Helper methods for publishing events to EventCloud from SDLC agents.
 *
 * <p>This helper delegates to EventCloud's publish() convenience methods which were added to
 * simplify event publishing.
 *
 * @doc.type class
 * @doc.purpose Helper for simplified event publishing
 * @doc.layer product
 * @doc.pattern Utility
 */
public final class EventCloudHelper {

  private EventCloudHelper() {
    // Utility class
  }

  /**
   * Publish an event to EventCloud with simplified parameters.
   *
   * @param eventCloud the event cloud client
   * @param topic the event topic/stream
   * @param payload the event payload
   * @return promise of void
   */
  public static Promise<Void> publish(
      EventCloud eventCloud, String topic, Map<String, Object> payload) {

    return eventCloud.publish(topic, payload);
  }

  /**
   * Publish an event to EventCloud with tenant ID.
   *
   * @param eventCloud the event cloud client
   * @param topic the event topic/stream
   * @param tenantId the tenant ID
   * @param payload the event payload
   * @return promise of void
   */
  public static Promise<Void> publish(
      EventCloud eventCloud, String topic, String tenantId, Map<String, Object> payload) {

    return eventCloud.publish(topic, tenantId, payload);
  }

  /**
   * Publish an event with full control over all parameters.
   *
   * @param eventCloud the event cloud client
   * @param topic the event topic/stream
   * @param tenantId the tenant ID
   * @param runId the run/correlation ID
   * @param category the event category
   * @param step the step name
   * @param payload the event payload
   * @param timestamp the event timestamp
   * @return promise of void
   */
  public static Promise<Void> publish(
      EventCloud eventCloud,
      String topic,
      String tenantId,
      String runId,
      String category,
      String step,
      Map<String, Object> payload,
      Instant timestamp) {

    return eventCloud.publish(topic, tenantId, runId, category, step, payload, timestamp);
  }
}
