package com.ghatana.refactorer.server.kg.query;

import java.util.ArrayList;
import java.util.List;

/**
 * Builder for constructing composite patterns with logical operators.

 *

 * <p>PatternQueryBuilder supports AND, OR operators to combine multiple pattern specifications

 * or builders into more complex pattern expressions.

 *

 * <p>Example:

 * ```java

 * String pattern = PatternQueryBuilder.create()

 * .operator(LogicalOperator.AND)

 * .pattern(SeqQueryBuilder.create("login", "access").within(...).build())

 * .pattern(SeqQueryBuilder.create("data-read", "logout").within(...).build())

 * .build();

 * // Returns: AND(SEQ(...), SEQ(...))

 * ```

 *

 * <p>Binding Decision #11: Composite builders enable complex pattern expressions with

 * type-safe operator combinations, supporting nested patterns for sophisticated CEP queries.

 *

 * @doc.type class

 * @doc.purpose Assemble optimized graph traversal queries used by analytics endpoints.

 * @doc.layer product

 * @doc.pattern Builder

 */

public final class PatternQueryBuilder {
  private final List<String> patterns;
  private LogicalOperator operator = LogicalOperator.AND;
  private double confidence = 0.5;
  private String description;

  private PatternQueryBuilder() {
    this.patterns = new ArrayList<>();
  }

  /**
 * Logical operators for pattern composition. */
  public enum LogicalOperator {
    AND("AND"),
    OR("OR");

    private final String symbol;

    LogicalOperator(String symbol) {
      this.symbol = symbol;
    }

    public String getSymbol() {
      return symbol;
    }
  }

  /**
   * Creates a new pattern query builder.
   *
   * @return New builder instance
   */
  public static PatternQueryBuilder create() {
    return new PatternQueryBuilder();
  }

  /**
   * Sets the logical operator for combining patterns.
   *
   * @param operator AND or OR (default: AND)
   * @return This builder for chaining
   */
  public PatternQueryBuilder operator(LogicalOperator operator) {
    this.operator = operator;
    return this;
  }

  /**
   * Adds a pattern specification string.
   *
   * <p>Patterns are typically produced by builder classes like SeqQueryBuilder.
   *
   * @param patternSpec Pattern specification string
   * @return This builder for chaining
   */
  public PatternQueryBuilder pattern(String patternSpec) {
    if (patternSpec == null || patternSpec.isBlank()) {
      throw new IllegalArgumentException("Pattern specification cannot be null or empty");
    }
    patterns.add(patternSpec);
    return this;
  }

  /**
   * Adds a pattern using a builder's build() result.
   *
   * <p>Convenience method for adding patterns from SeqQueryBuilder or nested
   * PatternQueryBuilder instances.
   *
   * @param builder Builder instance
   * @return This builder for chaining
   */
  public PatternQueryBuilder patternFromBuilder(Object builder) {
    if (builder instanceof SeqQueryBuilder seq) {
      return pattern(seq.build());
    } else if (builder instanceof PatternQueryBuilder pqb) {
      return pattern(pqb.build());
    } else {
      throw new IllegalArgumentException("Builder must be SeqQueryBuilder or PatternQueryBuilder");
    }
  }

  /**
   * Sets the confidence threshold for the composite pattern.
   *
   * @param confidence Confidence threshold 0-1 (default: 0.5)
   * @return This builder for chaining
   */
  public PatternQueryBuilder confidence(double confidence) {
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
  public PatternQueryBuilder description(String description) {
    this.description = description;
    return this;
  }

  /**
   * Builds the composite pattern specification string.
   *
   * @return Pattern spec like "AND(pattern1, pattern2)@confidence" or "OR(...)"
   */
  public String build() {
    if (patterns.isEmpty()) {
      throw new IllegalStateException("At least one pattern must be added before building");
    }
    String patternList = String.join(", ", patterns);
    int confidencePercent = (int) (confidence * 100);
    return String.format("%s(%s)@%d", operator.getSymbol(), patternList, confidencePercent);
  }

  /**
   * Gets the logical operator.
   *
   * @return The operator (AND or OR)
   */
  public LogicalOperator getOperator() {
    return operator;
  }

  /**
   * Gets the list of pattern specifications.
   *
   * @return Unmodifiable list of pattern specs
   */
  public List<String> getPatterns() {
    return List.copyOf(patterns);
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
