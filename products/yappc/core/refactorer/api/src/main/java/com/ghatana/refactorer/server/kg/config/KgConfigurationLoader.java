package com.ghatana.refactorer.server.kg.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Loader for KG configuration from application.conf.

 *

 * <p>Handles parsing and validation of KG configuration properties, supporting multiple

 * environment profiles (development, production) with sensible defaults.

 *

 * @doc.type class

 * @doc.purpose Translate Typesafe Config into strongly typed runtime settings.

 * @doc.layer product

 * @doc.pattern Factory

 */

public final class KgConfigurationLoader {
  private static final Logger logger = LogManager.getLogger(KgConfigurationLoader.class);

  private KgConfigurationLoader() {
    // Utility class
  }

  /**
   * Loads KG configuration for the given environment.
   *
   * @param environment Environment profile (development, production, or default)
   * @return Loaded KG configuration
   */
  public static KgConfiguration loadForEnvironment(String environment) {
    logger.info("Loading KG configuration for environment: {}", environment);

    KgConfiguration config = switch (environment) {
      case "development" -> {
        logger.info("Using development KG configuration (permissive thresholds)");
        yield KgConfiguration.createForDevelopment();
      }
      case "production" -> {
        logger.info("Using production KG configuration (strict thresholds)");
        yield KgConfiguration.createForProduction();
      }
      default -> {
        logger.info("Using default KG configuration");
        yield KgConfiguration.createDefault();
      }
    };

    logger.info("KG Configuration loaded: {}", config);
    return config;
  }

  /**
   * Loads KG configuration with system property override support.
   *
   * <p>Environment can be specified via system property: kg.environment
   *
   * @return Loaded KG configuration
   */
  public static KgConfiguration loadFromSystemProperties() {
    String environment = System.getProperty("kg.environment", "default");
    return loadForEnvironment(environment);
  }

  /**
   * Creates a builder for custom KG configuration.
   *
   * @return Configuration builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
 * Builder for custom KG configuration. */
  public static final class Builder {
    private double minSupport = 0.3;
    private double minConfidence = 0.5;
    private int correlationWindowMs = 300000;
    private int maxPatternsToReturn = 100;
    private boolean miningEnabled = true;
    private boolean autoActivate = false;
    private int archivalAgeDays = 90;
    private boolean versioningEnabled = true;
    private int seqDefaultWindowSeconds = 300;
    private int defaultConfidence = 50;
    private boolean correlationAnalysisEnabled = true;
    private boolean frequentPatternMiningEnabled = true;
    private int batchSize = 1000;
    private boolean cacheEnabled = true;
    private String evictionPolicy = "LRU";
    private int maxPatterns = 10000;

    public Builder minSupport(double minSupport) {
      this.minSupport = minSupport;
      return this;
    }

    public Builder minConfidence(double minConfidence) {
      this.minConfidence = minConfidence;
      return this;
    }

    public Builder correlationWindowMs(int correlationWindowMs) {
      this.correlationWindowMs = correlationWindowMs;
      return this;
    }

    public Builder maxPatternsToReturn(int maxPatternsToReturn) {
      this.maxPatternsToReturn = maxPatternsToReturn;
      return this;
    }

    public Builder miningEnabled(boolean miningEnabled) {
      this.miningEnabled = miningEnabled;
      return this;
    }

    public Builder autoActivate(boolean autoActivate) {
      this.autoActivate = autoActivate;
      return this;
    }

    public Builder archivalAgeDays(int archivalAgeDays) {
      this.archivalAgeDays = archivalAgeDays;
      return this;
    }

    public Builder versioningEnabled(boolean versioningEnabled) {
      this.versioningEnabled = versioningEnabled;
      return this;
    }

    public Builder seqDefaultWindowSeconds(int seqDefaultWindowSeconds) {
      this.seqDefaultWindowSeconds = seqDefaultWindowSeconds;
      return this;
    }

    public Builder defaultConfidence(int defaultConfidence) {
      this.defaultConfidence = defaultConfidence;
      return this;
    }

    public Builder correlationAnalysisEnabled(boolean correlationAnalysisEnabled) {
      this.correlationAnalysisEnabled = correlationAnalysisEnabled;
      return this;
    }

    public Builder frequentPatternMiningEnabled(boolean frequentPatternMiningEnabled) {
      this.frequentPatternMiningEnabled = frequentPatternMiningEnabled;
      return this;
    }

    public Builder batchSize(int batchSize) {
      this.batchSize = batchSize;
      return this;
    }

    public Builder cacheEnabled(boolean cacheEnabled) {
      this.cacheEnabled = cacheEnabled;
      return this;
    }

    public Builder evictionPolicy(String evictionPolicy) {
      this.evictionPolicy = evictionPolicy;
      return this;
    }

    public Builder maxPatterns(int maxPatterns) {
      this.maxPatterns = maxPatterns;
      return this;
    }

    public KgConfiguration build() {
      return new KgConfiguration(
          new KgConfiguration.MiningConfig(
              minSupport,
              minConfidence,
              correlationWindowMs,
              maxPatternsToReturn,
              miningEnabled),
          new KgConfiguration.PatternConfig(
              autoActivate,
              archivalAgeDays,
              versioningEnabled),
          new KgConfiguration.QueryBuilderConfig(
              seqDefaultWindowSeconds,
              defaultConfidence),
          new KgConfiguration.LearningConfig(
              correlationAnalysisEnabled,
              frequentPatternMiningEnabled,
              batchSize),
          new KgConfiguration.CacheConfig(
              cacheEnabled,
              evictionPolicy,
              maxPatterns));
    }
  }
}
