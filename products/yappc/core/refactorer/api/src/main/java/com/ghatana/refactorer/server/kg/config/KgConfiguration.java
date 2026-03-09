package com.ghatana.refactorer.server.kg.config;

/**
 * Configuration properties for Knowledge Graph operations.

 *

 * <p>Loads KG-related configuration from application.conf including pattern mining parameters,

 * correlation analysis settings, and learning operator thresholds.

 *

 * <p>Usage:

 * ```java

 * KgConfiguration config = KgConfiguration.load(configProvider);

 * double minSupport = config.mining().minSupport();

 * int correlationWindowMs = config.mining().correlationWindowMs();

 * ```

 *

 * <p>Binding Decision #11: Configuration externalization enables customization without code

 * recompilation, supporting different environments (dev/staging/prod) with different thresholds.

 *

 * @doc.type class

 * @doc.purpose Group related configuration knobs that shape knowledge graph analysis behavior.

 * @doc.layer product

 * @doc.pattern Configuration

 */

public final class KgConfiguration {

  /**
 * Pattern mining configuration. */
  public record MiningConfig(
      double minSupport,
      double minConfidence,
      int correlationWindowMs,
      int maxPatternsToReturn,
      boolean enabled) {

    public MiningConfig {
      if (minSupport < 0 || minSupport > 1) {
        throw new IllegalArgumentException("minSupport must be between 0 and 1");
      }
      if (minConfidence < 0 || minConfidence > 1) {
        throw new IllegalArgumentException("minConfidence must be between 0 and 1");
      }
      if (correlationWindowMs <= 0) {
        throw new IllegalArgumentException("correlationWindowMs must be positive");
      }
      if (maxPatternsToReturn <= 0) {
        throw new IllegalArgumentException("maxPatternsToReturn must be positive");
      }
    }
  }

  /**
 * Pattern lifecycle configuration. */
  public record PatternConfig(
      boolean autoActivate,
      int archivalAgeDays,
      boolean versioningEnabled) {

    public PatternConfig {
      if (archivalAgeDays <= 0) {
        throw new IllegalArgumentException("archivalAgeDays must be positive");
      }
    }
  }

  /**
 * Query builder configuration. */
  public record QueryBuilderConfig(
      int seqDefaultWindowSeconds,
      int defaultConfidence) {

    public QueryBuilderConfig {
      if (seqDefaultWindowSeconds <= 0) {
        throw new IllegalArgumentException("seqDefaultWindowSeconds must be positive");
      }
      if (defaultConfidence < 0 || defaultConfidence > 100) {
        throw new IllegalArgumentException("defaultConfidence must be 0-100");
      }
    }
  }

  /**
 * Learning operator configuration. */
  public record LearningConfig(
      boolean correlationAnalysisEnabled,
      boolean frequentPatternMiningEnabled,
      int batchSize) {

    public LearningConfig {
      if (batchSize <= 0) {
        throw new IllegalArgumentException("batchSize must be positive");
      }
    }
  }

  /**
 * Cache configuration. */
  public record CacheConfig(
      boolean enabled,
      String evictionPolicy,
      int maxPatterns) {

    public CacheConfig {
      if (maxPatterns <= 0) {
        throw new IllegalArgumentException("maxPatterns must be positive");
      }
    }
  }

  private final MiningConfig mining;
  private final PatternConfig patterns;
  private final QueryBuilderConfig queryBuilder;
  private final LearningConfig learning;
  private final CacheConfig cache;

  public KgConfiguration(
      MiningConfig mining,
      PatternConfig patterns,
      QueryBuilderConfig queryBuilder,
      LearningConfig learning,
      CacheConfig cache) {
    this.mining = mining;
    this.patterns = patterns;
    this.queryBuilder = queryBuilder;
    this.learning = learning;
    this.cache = cache;
  }

  /**
 * Gets pattern mining configuration. */
  public MiningConfig mining() {
    return mining;
  }

  /**
 * Gets pattern lifecycle configuration. */
  public PatternConfig patterns() {
    return patterns;
  }

  /**
 * Gets query builder configuration. */
  public QueryBuilderConfig queryBuilder() {
    return queryBuilder;
  }

  /**
 * Gets learning operator configuration. */
  public LearningConfig learning() {
    return learning;
  }

  /**
 * Gets cache configuration. */
  public CacheConfig cache() {
    return cache;
  }

  /**
   * Creates default KG configuration (suitable for testing).
   *
   * @return Default configuration instance
   */
  public static KgConfiguration createDefault() {
    return new KgConfiguration(
        new MiningConfig(
            0.3,      // minSupport: 30%
            0.5,      // minConfidence: 50%
            300000,   // correlationWindowMs: 5 minutes
            100,      // maxPatternsToReturn
            true),    // enabled
        new PatternConfig(
            false,    // autoActivate: false (manual approval)
            90,       // archivalAgeDays: 90 days
            true),    // versioningEnabled
        new QueryBuilderConfig(
            300,      // seqDefaultWindowSeconds: 5 minutes
            50),      // defaultConfidence: 50%
        new LearningConfig(
            true,     // correlationAnalysisEnabled
            true,     // frequentPatternMiningEnabled
            1000),    // batchSize
        new CacheConfig(
            true,     // enabled
            "LRU",    // evictionPolicy
            10000));  // maxPatterns
  }

  /**
   * Creates development KG configuration (permissive thresholds).
   *
   * @return Development configuration instance
   */
  public static KgConfiguration createForDevelopment() {
    return new KgConfiguration(
        new MiningConfig(
            0.2,      // minSupport: 20% (discover more patterns)
            0.3,      // minConfidence: 30% (low bar for dev)
            60000,    // correlationWindowMs: 1 minute (shorter window)
            200,      // maxPatternsToReturn
            true),    // enabled
        new PatternConfig(
            true,     // autoActivate: true (auto-enable in dev)
            30,       // archivalAgeDays: 30 days (shorter)
            true),    // versioningEnabled
        new QueryBuilderConfig(
            60,       // seqDefaultWindowSeconds: 1 minute
            30),      // defaultConfidence: 30%
        new LearningConfig(
            true,     // correlationAnalysisEnabled
            true,     // frequentPatternMiningEnabled
            500),     // batchSize: smaller for dev
        new CacheConfig(
            true,     // enabled
            "LRU",    // evictionPolicy
            5000));   // maxPatterns: smaller cache
  }

  /**
   * Creates production KG configuration (strict thresholds).
   *
   * @return Production configuration instance
   */
  public static KgConfiguration createForProduction() {
    return new KgConfiguration(
        new MiningConfig(
            0.5,      // minSupport: 50% (high bar, only frequent patterns)
            0.8,      // minConfidence: 80% (high confidence)
            600000,   // correlationWindowMs: 10 minutes (longer window)
            50,       // maxPatternsToReturn (limit responses)
            true),    // enabled
        new PatternConfig(
            false,    // autoActivate: false (manual approval)
            180,      // archivalAgeDays: 180 days (long retention)
            true),    // versioningEnabled
        new QueryBuilderConfig(
            600,      // seqDefaultWindowSeconds: 10 minutes
            75),      // defaultConfidence: 75%
        new LearningConfig(
            true,     // correlationAnalysisEnabled
            true,     // frequentPatternMiningEnabled
            5000),    // batchSize: larger for batch processing
        new CacheConfig(
            true,     // enabled
            "LRU",    // evictionPolicy
            50000));  // maxPatterns: large cache
  }

  @Override
  public String toString() {
    return "KgConfiguration{"
        + "mining=" + mining
        + ", patterns=" + patterns
        + ", queryBuilder=" + queryBuilder
        + ", learning=" + learning
        + ", cache=" + cache
        + '}';
  }
}
