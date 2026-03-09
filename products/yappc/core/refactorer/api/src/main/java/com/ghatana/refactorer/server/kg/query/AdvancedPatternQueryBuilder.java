package com.ghatana.refactorer.server.kg.query;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Advanced builder for constructing patterns with temporal constraints.

 *

 * <p>Supports complex temporal relationships like "after", "before", "between", and "not"

 * (negation) patterns.

 *

 * <p>Example:

 * ```java

 * String pattern = AdvancedPatternQueryBuilder.create()

 * .event("login")

 * .after(Duration.ofSeconds(30))

 * .event("suspicious-activity")

 * .before(Duration.ofMinutes(5))

 * .not("admin-approval")

 * .build();

 * // Returns: login AFTER 30s, suspicious-activity BEFORE 5m, NOT admin-approval

 * ```

 *

 * <p>Binding Decision #11: Advanced builder enables expressive temporal queries with

 * constraint specification, supporting CEP's complex timing requirements.

 *

 * @doc.type class

 * @doc.purpose Assemble optimized graph traversal queries used by analytics endpoints.

 * @doc.layer product

 * @doc.pattern Builder

 */

public final class AdvancedPatternQueryBuilder {
  private final List<TemporalConstraint> constraints;
  private double confidence = 0.5;
  private String description;

  private AdvancedPatternQueryBuilder() {
    this.constraints = new ArrayList<>();
  }

  /**
 * Temporal constraint types. */
  private enum ConstraintType {
    EVENT("EVENT"),
    AFTER("AFTER"),
    BEFORE("BEFORE"),
    WITHIN("WITHIN"),
    NOT("NOT");

    private final String symbol;

    ConstraintType(String symbol) {
      this.symbol = symbol;
    }

    public String getSymbol() {
      return symbol;
    }
  }

  /**
 * Immutable temporal constraint. */
  private static class TemporalConstraint {
    final ConstraintType type;
    final String eventType;
    final Duration duration;

    TemporalConstraint(ConstraintType type, String eventType, Duration duration) {
      this.type = type;
      this.eventType = eventType;
      this.duration = duration;
    }

    @Override
    public String toString() {
      if (duration == null) {
        return String.format("%s(%s)", type.getSymbol(), eventType);
      }
      return String.format("%s(%s)[%d]", type.getSymbol(), eventType, duration.getSeconds());
    }
  }

  /**
   * Creates a new advanced pattern query builder.
   *
   * @return New builder instance
   */
  public static AdvancedPatternQueryBuilder create() {
    return new AdvancedPatternQueryBuilder();
  }

  /**
   * Adds an event occurrence constraint.
   *
   * @param eventType Event type to match
   * @return This builder for chaining
   */
  public AdvancedPatternQueryBuilder event(String eventType) {
    if (eventType == null || eventType.isBlank()) {
      throw new IllegalArgumentException("Event type cannot be null or empty");
    }
    constraints.add(new TemporalConstraint(ConstraintType.EVENT, eventType, null));
    return this;
  }

  /**
   * Adds an "after" temporal constraint.
   *
   * <p>Specifies that the next event must occur at least this duration after the previous event.
   *
   * @param duration Minimum time delay
   * @return This builder for chaining
   */
  public AdvancedPatternQueryBuilder after(Duration duration) {
    if (constraints.isEmpty()) {
      throw new IllegalStateException("Cannot add 'after' constraint without a preceding event");
    }
    // Add a marker constraint for temporal reasoning (not a standalone event)
    TemporalConstraint lastConstraint = constraints.get(constraints.size() - 1);
    constraints.add(new TemporalConstraint(ConstraintType.AFTER, lastConstraint.eventType, duration));
    return this;
  }

  /**
   * Adds a "before" temporal constraint.
   *
   * <p>Specifies that the next event must occur no more than this duration after the previous
   * event.
   *
   * @param duration Maximum time delay
   * @return This builder for chaining
   */
  public AdvancedPatternQueryBuilder before(Duration duration) {
    if (constraints.isEmpty()) {
      throw new IllegalStateException("Cannot add 'before' constraint without a preceding event");
    }
    TemporalConstraint lastConstraint = constraints.get(constraints.size() - 1);
    constraints.add(new TemporalConstraint(ConstraintType.BEFORE, lastConstraint.eventType, duration));
    return this;
  }

  /**
   * Adds a "within" temporal constraint.
   *
   * <p>Specifies that the event must occur within this duration from the start.
   *
   * @param duration Time window
   * @return This builder for chaining
   */
  public AdvancedPatternQueryBuilder within(Duration duration) {
    if (constraints.isEmpty()) {
      throw new IllegalStateException("Cannot add 'within' constraint without a preceding event");
    }
    TemporalConstraint lastConstraint = constraints.get(constraints.size() - 1);
    constraints.add(new TemporalConstraint(ConstraintType.WITHIN, lastConstraint.eventType, duration));
    return this;
  }

  /**
   * Adds a negation constraint.
   *
   * <p>Specifies that an event should NOT occur. Negations are typically used to exclude
   * scenarios.
   *
   * @param eventType Event type to exclude
   * @return This builder for chaining
   */
  public AdvancedPatternQueryBuilder not(String eventType) {
    if (eventType == null || eventType.isBlank()) {
      throw new IllegalArgumentException("Event type cannot be null or empty");
    }
    constraints.add(new TemporalConstraint(ConstraintType.NOT, eventType, null));
    return this;
  }

  /**
   * Sets the confidence threshold for the pattern.
   *
   * @param confidence Confidence threshold 0-1 (default: 0.5)
   * @return This builder for chaining
   */
  public AdvancedPatternQueryBuilder confidence(double confidence) {
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
  public AdvancedPatternQueryBuilder description(String description) {
    this.description = description;
    return this;
  }

  /**
   * Builds the pattern specification string.
   *
   * @return Pattern spec with temporal constraints
   */
  public String build() {
    if (constraints.isEmpty()) {
      throw new IllegalStateException("At least one event constraint must be added before building");
    }
    List<String> parts = new ArrayList<>();
    for (TemporalConstraint constraint : constraints) {
      parts.add(constraint.toString());
    }
    String constraintList = String.join(", ", parts);
    int confidencePercent = (int) (confidence * 100);
    return String.format("ADVANCED(%s)@%d", constraintList, confidencePercent);
  }

  /**
   * Gets the confidence threshold.
   *
   * @return Confidence value 0-1
   */
  public double getConfidence() {
    return confidence;
  }

  /**
   * Gets the number of constraints.
   *
   * @return Number of temporal constraints added
   */
  public int getConstraintCount() {
    return constraints.size();
  }
}
