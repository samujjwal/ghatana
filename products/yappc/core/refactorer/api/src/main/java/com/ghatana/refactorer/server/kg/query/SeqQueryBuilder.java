package com.ghatana.refactorer.server.kg.query;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Builder for constructing SEQ (sequence) patterns.

 *

 * <p>SEQ patterns detect ordered sequences of events occurring within a time window.

 *

 * <p>Example:

 * ```java

 * String pattern = SeqQueryBuilder.create("login", "data-access", "logout")

 * .within(Duration.ofMinutes(30))

 * .confidence(0.75)

 * .build();

 * // Returns: SEQ(login, data-access, logout)[30m]@0.75

 * ```

 *

 * <p>Binding Decision #11: Query builders provide type-safe DSL for KG pattern construction,

 * eliminating string concatenation errors and supporting pattern validation.

 *

 * @doc.type class

 * @doc.purpose Assemble optimized graph traversal queries used by analytics endpoints.

 * @doc.layer product

 * @doc.pattern Builder

 */

public final class SeqQueryBuilder {
  private final List<String> eventTypes;
  private Duration timeWindow = Duration.ofMinutes(5);
  private double confidence = 0.5;
  private String description;

  private SeqQueryBuilder(String... eventTypes) {
    this.eventTypes = new ArrayList<>();
    for (String eventType : eventTypes) {
      this.eventTypes.add(eventType);
    }
  }

  /**
   * Creates a SEQ pattern builder with the given event types in order.
   *
   * @param eventTypes Event types in sequence order
   * @return SEQ pattern builder
   */
  public static SeqQueryBuilder create(String... eventTypes) {
    if (eventTypes.length < 2) {
      throw new IllegalArgumentException("SEQ pattern requires at least 2 event types");
    }
    return new SeqQueryBuilder(eventTypes);
  }

  /**
   * Sets the time window for the sequence.
   *
   * @param timeWindow Time window (default: 5 minutes)
   * @return This builder for chaining
   */
  public SeqQueryBuilder within(Duration timeWindow) {
    this.timeWindow = timeWindow;
    return this;
  }

  /**
   * Sets the confidence threshold for the pattern.
   *
   * @param confidence Confidence threshold 0-1 (default: 0.5)
   * @return This builder for chaining
   */
  public SeqQueryBuilder confidence(double confidence) {
    if (confidence < 0 || confidence > 1) {
      throw new IllegalArgumentException("Confidence must be between 0 and 1");
    }
    this.confidence = confidence;
    return this;
  }

  /**
   * Sets the description for documentation.
   *
   * @param description Human-readable description
   * @return This builder for chaining
   */
  public SeqQueryBuilder description(String description) {
    this.description = description;
    return this;
  }

  /**
   * Builds the pattern specification string.
   *
   * @return Pattern spec like "SEQ(event1, event2)[timeWindow]@confidence"
   */
  public String build() {
    String eventList = String.join(", ", eventTypes);
    long minutes = timeWindow.toMinutes();
    String windowSpec = minutes > 0 ? String.format("[%dm]", minutes) : "[5m]";
    int confidencePercent = (int) (confidence * 100);
    return String.format("SEQ(%s)%s@%d", eventList, windowSpec, confidencePercent);
  }

  /**
   * Gets the list of event types in this sequence.
   *
   * @return Unmodifiable list of event types
   */
  public List<String> getEventTypes() {
    return List.copyOf(eventTypes);
  }

  /**
   * Gets the time window specification.
   *
   * @return Duration for the sequence window
   */
  public Duration getTimeWindow() {
    return timeWindow;
  }

  /**
   * Gets the confidence threshold.
   *
   * @return Confidence value 0-1
   */
  public double getConfidence() {
    return confidence;
  }
}
