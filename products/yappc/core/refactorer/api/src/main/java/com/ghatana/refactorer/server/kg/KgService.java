package com.ghatana.refactorer.server.kg;

import com.ghatana.refactorer.server.kg.query.AdvancedPatternQueryBuilder;
import com.ghatana.refactorer.server.kg.query.PatternQueryBuilder;
import com.ghatana.refactorer.server.kg.query.SeqQueryBuilder;
import io.activej.promise.Promise;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service interface for Knowledge Graph operations.

 *

 * <p>Provides abstraction for pattern management, querying, and learning operations.

 * This is the primary interface for interacting with the KG subsystem from the refactorer

 * service.

 *

 * <p>Binding Decision #11: Use core abstractions (not direct KG library imports) for

 * all KG operations. This allows the refactorer to interact with KG independently of

 * implementation details (protobuf, storage layer, learning algorithms).

 *

 * @doc.type interface

 * @doc.purpose Expose knowledge graph queries and updates for refactorer workflows.

 * @doc.layer product

 * @doc.pattern Service

 */

public interface KgService {
  /**
   * Submits a pattern specification to the Knowledge Graph for compilation and storage.
   *
   * @param tenantId Tenant identifier for multi-tenancy isolation
   * @param patternName Human-readable pattern name
   * @param patternSpec Pattern specification (typically SEQ/AND/OR operators)
   * @param metadata Additional metadata (tags, description, confidence threshold)
   * @return Promise containing compiled pattern ID and detection plan
   */
  Promise<CompiledPattern> submitPattern(
      String tenantId,
      String patternName,
      String patternSpec,
      Map<String, String> metadata);

  /**
   * Queries patterns matching given criteria.
   *
   * @param tenantId Tenant identifier for filtering
   * @param eventTypes Event types to match (empty = all)
   * @param minConfidence Minimum confidence threshold (0-1)
   * @return Promise containing list of matching patterns
   */
  Promise<List<KgPattern>> queryPatterns(
      String tenantId, List<String> eventTypes, double minConfidence);

  /**
   * Gets a specific pattern by ID.
   *
   * @param patternId Pattern identifier
   * @param tenantId Tenant identifier (for authorization)
   * @return Promise containing pattern details, or empty if not found
   */
  Promise<Optional<KgPattern>> getPattern(String patternId, String tenantId);

  /**
   * Activates a pattern for execution in the event processing pipeline.
   *
   * @param patternId Pattern identifier
   * @param tenantId Tenant identifier (for authorization)
   * @return Promise that resolves when activation is complete
   */
  Promise<Void> activatePattern(String patternId, String tenantId);

  /**
   * Deactivates a pattern to stop execution.
   *
   * @param patternId Pattern identifier
   * @param tenantId Tenant identifier (for authorization)
   * @return Promise that resolves when deactivation is complete
   */
  Promise<Void> deactivatePattern(String patternId, String tenantId);

  /**
   * Analyzes event sequences to discover correlated patterns.
   *
   * <p>This operation mines the event history for frequent sequences and temporal
   * correlations, suggesting new patterns that might be valuable for monitoring.
   *
   * @param tenantId Tenant identifier for scoped analysis
   * @param eventTypes Event types to analyze (empty = all)
   * @param timeWindowHours Time window for pattern mining
   * @param minSupport Minimum support threshold (0-1) for pattern frequency
   * @return Promise containing list of discovered patterns
   */
  Promise<List<DiscoveredPattern>> analyzePatterns(
      String tenantId,
      List<String> eventTypes,
      int timeWindowHours,
      double minSupport);

  /**
   * Gets statistics about patterns in the Knowledge Graph.
   *
   * @param tenantId Tenant identifier for filtering
   * @return Promise containing KG statistics
   */
  Promise<KgStatistics> getStatistics(String tenantId);

  /**
   * Creates a query builder for sequence (SEQ) pattern construction.
   *
   * <p>Example:
   * ```java
   * SeqQueryBuilder builder = kgService.seqPattern("login", "access", "logout")
   *     .within(Duration.ofMinutes(30))
   *     .confidence(0.8);
   * String spec = builder.build();
   * ```
   *
   * @param eventTypes Event types in sequence order (must be ≥ 2)
   * @return Builder for constructing the pattern
   */
  static SeqQueryBuilder seqPattern(String... eventTypes) {
    return SeqQueryBuilder.create(eventTypes);
  }

  /**
   * Creates a query builder for composite pattern construction.
   *
   * <p>Example:
   * ```java
   * String pattern = PatternQueryBuilder.create()
   *     .operator(LogicalOperator.AND)
   *     .pattern(seqPattern1)
   *     .pattern(seqPattern2)
   *     .confidence(0.75)
   *     .build();
   * ```
   *
   * @return Builder for constructing composite patterns
   */
  static PatternQueryBuilder compositePattern() {
    return PatternQueryBuilder.create();
  }

  /**
   * Creates a query builder for advanced pattern construction with temporal constraints.
   *
   * <p>Example:
   * ```java
   * String pattern = advancedPattern()
   *     .event("login")
   *     .after(Duration.ofSeconds(30))
   *     .event("suspicious-activity")
   *     .before(Duration.ofMinutes(5))
   *     .not("admin-approval")
   *     .build();
   * ```
   *
   * @return Builder for constructing advanced patterns with temporal constraints
   */
  static AdvancedPatternQueryBuilder advancedPattern() {
    return AdvancedPatternQueryBuilder.create();
  }

  /**
   * Value object representing a compiled pattern ready for execution.
   */
  record CompiledPattern(
      String patternId,
      String name,
      String detectionPlan,
      int confidence,
      long createdAt) {}

  /**
   * Value object representing a pattern in the Knowledge Graph.
   */
  record KgPattern(
      String id,
      String name,
      String spec,
      PatternStatus status,
      int confidence,
      List<String> tags,
      long createdAt,
      long matchCount) {}

  /**
   * Pattern status enumeration.
   */
  enum PatternStatus {
    DRAFT,
    ACTIVE,
    INACTIVE,
    ARCHIVED
  }

  /**
   * Value object representing a discovered pattern from analysis.
   */
  record DiscoveredPattern(
      String name,
      String spec,
      int support,
      int confidence,
      List<String> eventSequence) {}

  /**
   * Value object containing Knowledge Graph statistics.
   */
  record KgStatistics(
      long totalPatterns,
      long activePatterns,
      long totalMatches,
      long discoveredPatterns,
      double averageConfidence) {}
}
