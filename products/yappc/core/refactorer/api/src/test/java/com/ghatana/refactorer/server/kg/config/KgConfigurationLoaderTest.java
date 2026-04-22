package com.ghatana.refactorer.server.kg.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.Test;

/** Unit tests for KgConfigurationLoader.
 * @doc.type class
 * @doc.purpose Handles kg configuration loader test operations
 * @doc.layer core
 * @doc.pattern Test
*/
class KgConfigurationLoaderTest extends EventloopTestBase {

  @Test
  void testLoadDefaultConfiguration() { // GH-90000
    KgConfiguration config = KgConfigurationLoader.loadForEnvironment("default [GH-90000]");

    assertThat(config.mining().minSupport()).isEqualTo(0.3); // GH-90000
    assertThat(config.mining().minConfidence()).isEqualTo(0.5); // GH-90000
  }

  @Test
  void testLoadDevelopmentConfiguration() { // GH-90000
    KgConfiguration config = KgConfigurationLoader.loadForEnvironment("development [GH-90000]");

    assertThat(config.mining().minSupport()).isEqualTo(0.2); // GH-90000
    assertThat(config.mining().minConfidence()).isEqualTo(0.3); // GH-90000
    assertThat(config.patterns().autoActivate()).isTrue(); // GH-90000
  }

  @Test
  void testLoadProductionConfiguration() { // GH-90000
    KgConfiguration config = KgConfigurationLoader.loadForEnvironment("production [GH-90000]");

    assertThat(config.mining().minSupport()).isEqualTo(0.5); // GH-90000
    assertThat(config.mining().minConfidence()).isEqualTo(0.8); // GH-90000
    assertThat(config.patterns().autoActivate()).isFalse(); // GH-90000
  }

  @Test
  void testLoadUnknownEnvironmentDefaultsToDefault() { // GH-90000
    KgConfiguration config = KgConfigurationLoader.loadForEnvironment("unknown [GH-90000]");

    // Unknown environment should use default configuration
    assertThat(config.mining().minSupport()).isEqualTo(0.3); // GH-90000
    assertThat(config.mining().minConfidence()).isEqualTo(0.5); // GH-90000
  }

  @Test
  void testLoadFromSystemProperties() { // GH-90000
    // This test reads from system properties, so we verify the method works
    // In a real test, you'd use JVM system property setting
    KgConfiguration config = KgConfigurationLoader.loadFromSystemProperties(); // GH-90000

    assertThat(config).isNotNull(); // GH-90000
    assertThat(config.mining().minSupport()).isGreaterThanOrEqualTo(0); // GH-90000
  }

  @Test
  void testBuilderPattern() { // GH-90000
    KgConfiguration config = KgConfigurationLoader.builder() // GH-90000
        .minSupport(0.4) // GH-90000
        .minConfidence(0.65) // GH-90000
        .correlationWindowMs(450000) // GH-90000
        .maxPatternsToReturn(150) // GH-90000
        .autoActivate(true) // GH-90000
        .archivalAgeDays(60) // GH-90000
        .versioningEnabled(false) // GH-90000
        .seqDefaultWindowSeconds(450) // GH-90000
        .defaultConfidence(65) // GH-90000
        .correlationAnalysisEnabled(false) // GH-90000
        .frequentPatternMiningEnabled(true) // GH-90000
        .batchSize(1500) // GH-90000
        .cacheEnabled(true) // GH-90000
        .evictionPolicy("LRU [GH-90000]")
        .maxPatterns(20000) // GH-90000
        .build(); // GH-90000

    assertThat(config.mining().minSupport()).isEqualTo(0.4); // GH-90000
    assertThat(config.mining().minConfidence()).isEqualTo(0.65); // GH-90000
    assertThat(config.mining().correlationWindowMs()).isEqualTo(450000); // GH-90000
    assertThat(config.patterns().autoActivate()).isTrue(); // GH-90000
    assertThat(config.patterns().archivalAgeDays()).isEqualTo(60); // GH-90000
    assertThat(config.patterns().versioningEnabled()).isFalse(); // GH-90000
    assertThat(config.learning().batchSize()).isEqualTo(1500); // GH-90000
    assertThat(config.cache().maxPatterns()).isEqualTo(20000); // GH-90000
  }

  @Test
  void testBuilderPartialConfiguration() { // GH-90000
    KgConfiguration config = KgConfigurationLoader.builder() // GH-90000
        .minSupport(0.35) // GH-90000
        .build(); // GH-90000

    // Custom value should be set
    assertThat(config.mining().minSupport()).isEqualTo(0.35); // GH-90000

    // Other values should have defaults
    assertThat(config.mining().minConfidence()).isEqualTo(0.5); // GH-90000
  }

  @Test
  void testBuilderFluentAPI() { // GH-90000
    KgConfiguration config = KgConfigurationLoader.builder() // GH-90000
        .minSupport(0.25) // GH-90000
        .minConfidence(0.55) // GH-90000
        .maxPatternsToReturn(75) // GH-90000
        .correlationWindowMs(250000) // GH-90000
        .autoActivate(true) // GH-90000
        .batchSize(800) // GH-90000
        .build(); // GH-90000

    assertThat(config.mining().minSupport()).isEqualTo(0.25); // GH-90000
    assertThat(config.mining().minConfidence()).isEqualTo(0.55); // GH-90000
    assertThat(config.mining().maxPatternsToReturn()).isEqualTo(75); // GH-90000
  }

  @Test
  void testBuilderReturnsNewInstance() { // GH-90000
    KgConfiguration config1 = KgConfigurationLoader.builder() // GH-90000
        .minSupport(0.4) // GH-90000
        .build(); // GH-90000

    KgConfiguration config2 = KgConfigurationLoader.builder() // GH-90000
        .minSupport(0.6) // GH-90000
        .build(); // GH-90000

    assertThat(config1.mining().minSupport()).isEqualTo(0.4); // GH-90000
    assertThat(config2.mining().minSupport()).isEqualTo(0.6); // GH-90000
  }

  @Test
  void testProductionConfigurationValues() { // GH-90000
    KgConfiguration config = KgConfigurationLoader.loadForEnvironment("production [GH-90000]");

    // Production-specific validations
    assertThat(config.mining().minSupport()).isEqualTo(0.5); // GH-90000
    assertThat(config.mining().minConfidence()).isEqualTo(0.8); // GH-90000
    assertThat(config.mining().correlationWindowMs()).isEqualTo(600000); // 10 min // GH-90000
    assertThat(config.patterns().archivalAgeDays()).isEqualTo(180); // GH-90000
    assertThat(config.queryBuilder().defaultConfidence()).isEqualTo(75); // GH-90000
    assertThat(config.learning().batchSize()).isEqualTo(5000); // GH-90000
    assertThat(config.cache().maxPatterns()).isEqualTo(50000); // GH-90000
  }

  @Test
  void testDevelopmentConfigurationValues() { // GH-90000
    KgConfiguration config = KgConfigurationLoader.loadForEnvironment("development [GH-90000]");

    // Development-specific validations
    assertThat(config.mining().minSupport()).isEqualTo(0.2); // GH-90000
    assertThat(config.mining().minConfidence()).isEqualTo(0.3); // GH-90000
    assertThat(config.mining().correlationWindowMs()).isEqualTo(60000); // 1 min // GH-90000
    assertThat(config.patterns().archivalAgeDays()).isEqualTo(30); // GH-90000
    assertThat(config.patterns().autoActivate()).isTrue(); // GH-90000
    assertThat(config.queryBuilder().defaultConfidence()).isEqualTo(30); // GH-90000
    assertThat(config.learning().batchSize()).isEqualTo(500); // GH-90000
    assertThat(config.cache().maxPatterns()).isEqualTo(5000); // GH-90000
  }
}
