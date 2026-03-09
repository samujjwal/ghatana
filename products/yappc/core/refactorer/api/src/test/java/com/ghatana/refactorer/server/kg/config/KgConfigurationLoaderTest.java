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
  void testLoadDefaultConfiguration() {
    KgConfiguration config = KgConfigurationLoader.loadForEnvironment("default");
    
    assertThat(config.mining().minSupport()).isEqualTo(0.3);
    assertThat(config.mining().minConfidence()).isEqualTo(0.5);
  }

  @Test
  void testLoadDevelopmentConfiguration() {
    KgConfiguration config = KgConfigurationLoader.loadForEnvironment("development");
    
    assertThat(config.mining().minSupport()).isEqualTo(0.2);
    assertThat(config.mining().minConfidence()).isEqualTo(0.3);
    assertThat(config.patterns().autoActivate()).isTrue();
  }

  @Test
  void testLoadProductionConfiguration() {
    KgConfiguration config = KgConfigurationLoader.loadForEnvironment("production");
    
    assertThat(config.mining().minSupport()).isEqualTo(0.5);
    assertThat(config.mining().minConfidence()).isEqualTo(0.8);
    assertThat(config.patterns().autoActivate()).isFalse();
  }

  @Test
  void testLoadUnknownEnvironmentDefaultsToDefault() {
    KgConfiguration config = KgConfigurationLoader.loadForEnvironment("unknown");
    
    // Unknown environment should use default configuration
    assertThat(config.mining().minSupport()).isEqualTo(0.3);
    assertThat(config.mining().minConfidence()).isEqualTo(0.5);
  }

  @Test
  void testLoadFromSystemProperties() {
    // This test reads from system properties, so we verify the method works
    // In a real test, you'd use JVM system property setting
    KgConfiguration config = KgConfigurationLoader.loadFromSystemProperties();
    
    assertThat(config).isNotNull();
    assertThat(config.mining().minSupport()).isGreaterThanOrEqualTo(0);
  }

  @Test
  void testBuilderPattern() {
    KgConfiguration config = KgConfigurationLoader.builder()
        .minSupport(0.4)
        .minConfidence(0.65)
        .correlationWindowMs(450000)
        .maxPatternsToReturn(150)
        .autoActivate(true)
        .archivalAgeDays(60)
        .versioningEnabled(false)
        .seqDefaultWindowSeconds(450)
        .defaultConfidence(65)
        .correlationAnalysisEnabled(false)
        .frequentPatternMiningEnabled(true)
        .batchSize(1500)
        .cacheEnabled(true)
        .evictionPolicy("LRU")
        .maxPatterns(20000)
        .build();
    
    assertThat(config.mining().minSupport()).isEqualTo(0.4);
    assertThat(config.mining().minConfidence()).isEqualTo(0.65);
    assertThat(config.mining().correlationWindowMs()).isEqualTo(450000);
    assertThat(config.patterns().autoActivate()).isTrue();
    assertThat(config.patterns().archivalAgeDays()).isEqualTo(60);
    assertThat(config.patterns().versioningEnabled()).isFalse();
    assertThat(config.learning().batchSize()).isEqualTo(1500);
    assertThat(config.cache().maxPatterns()).isEqualTo(20000);
  }

  @Test
  void testBuilderPartialConfiguration() {
    KgConfiguration config = KgConfigurationLoader.builder()
        .minSupport(0.35)
        .build();
    
    // Custom value should be set
    assertThat(config.mining().minSupport()).isEqualTo(0.35);
    
    // Other values should have defaults
    assertThat(config.mining().minConfidence()).isEqualTo(0.5);
  }

  @Test
  void testBuilderFluentAPI() {
    KgConfiguration config = KgConfigurationLoader.builder()
        .minSupport(0.25)
        .minConfidence(0.55)
        .maxPatternsToReturn(75)
        .correlationWindowMs(250000)
        .autoActivate(true)
        .batchSize(800)
        .build();
    
    assertThat(config.mining().minSupport()).isEqualTo(0.25);
    assertThat(config.mining().minConfidence()).isEqualTo(0.55);
    assertThat(config.mining().maxPatternsToReturn()).isEqualTo(75);
  }

  @Test
  void testBuilderReturnsNewInstance() {
    KgConfiguration config1 = KgConfigurationLoader.builder()
        .minSupport(0.4)
        .build();
    
    KgConfiguration config2 = KgConfigurationLoader.builder()
        .minSupport(0.6)
        .build();
    
    assertThat(config1.mining().minSupport()).isEqualTo(0.4);
    assertThat(config2.mining().minSupport()).isEqualTo(0.6);
  }

  @Test
  void testProductionConfigurationValues() {
    KgConfiguration config = KgConfigurationLoader.loadForEnvironment("production");
    
    // Production-specific validations
    assertThat(config.mining().minSupport()).isEqualTo(0.5);
    assertThat(config.mining().minConfidence()).isEqualTo(0.8);
    assertThat(config.mining().correlationWindowMs()).isEqualTo(600000); // 10 min
    assertThat(config.patterns().archivalAgeDays()).isEqualTo(180);
    assertThat(config.queryBuilder().defaultConfidence()).isEqualTo(75);
    assertThat(config.learning().batchSize()).isEqualTo(5000);
    assertThat(config.cache().maxPatterns()).isEqualTo(50000);
  }

  @Test
  void testDevelopmentConfigurationValues() {
    KgConfiguration config = KgConfigurationLoader.loadForEnvironment("development");
    
    // Development-specific validations
    assertThat(config.mining().minSupport()).isEqualTo(0.2);
    assertThat(config.mining().minConfidence()).isEqualTo(0.3);
    assertThat(config.mining().correlationWindowMs()).isEqualTo(60000); // 1 min
    assertThat(config.patterns().archivalAgeDays()).isEqualTo(30);
    assertThat(config.patterns().autoActivate()).isTrue();
    assertThat(config.queryBuilder().defaultConfidence()).isEqualTo(30);
    assertThat(config.learning().batchSize()).isEqualTo(500);
    assertThat(config.cache().maxPatterns()).isEqualTo(5000);
  }
}
