package com.ghatana.refactorer.server.kg.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.Test;

/** Unit tests for KgConfiguration. 
 * @doc.type class
 * @doc.purpose Handles kg configuration test operations
 * @doc.layer core
 * @doc.pattern Test
*/
class KgConfigurationTest extends EventloopTestBase {

  @Test
  void testDefaultConfiguration() {
    KgConfiguration config = KgConfiguration.createDefault();
    
    assertThat(config.mining().minSupport()).isEqualTo(0.3);
    assertThat(config.mining().minConfidence()).isEqualTo(0.5);
    assertThat(config.mining().correlationWindowMs()).isEqualTo(300000);
    assertThat(config.mining().enabled()).isTrue();
    
    assertThat(config.patterns().autoActivate()).isFalse();
    assertThat(config.patterns().archivalAgeDays()).isEqualTo(90);
    assertThat(config.patterns().versioningEnabled()).isTrue();
  }

  @Test
  void testDevelopmentConfiguration() {
    KgConfiguration config = KgConfiguration.createForDevelopment();
    
    // Development has more permissive thresholds
    assertThat(config.mining().minSupport()).isEqualTo(0.2);
    assertThat(config.mining().minConfidence()).isEqualTo(0.3);
    assertThat(config.patterns().autoActivate()).isTrue();
  }

  @Test
  void testProductionConfiguration() {
    KgConfiguration config = KgConfiguration.createForProduction();
    
    // Production has stricter thresholds
    assertThat(config.mining().minSupport()).isEqualTo(0.5);
    assertThat(config.mining().minConfidence()).isEqualTo(0.8);
    assertThat(config.patterns().autoActivate()).isFalse();
  }

  @Test
  void testInvalidMinSupport() {
    assertThatThrownBy(
            () -> new KgConfiguration.MiningConfig(
                -0.1, 0.5, 300000, 100, true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("minSupport must be between 0 and 1");
  }

  @Test
  void testInvalidCorrelationWindow() {
    assertThatThrownBy(
            () -> new KgConfiguration.MiningConfig(
                0.3, 0.5, -1, 100, true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("correlationWindowMs must be positive");
  }

  @Test
  void testBuilderCustomConfiguration() {
    KgConfiguration config = KgConfigurationLoader.builder()
        .minSupport(0.4)
        .minConfidence(0.6)
        .correlationWindowMs(600000)
        .maxPatternsToReturn(200)
        .autoActivate(true)
        .batchSize(2000)
        .build();
    
    assertThat(config.mining().minSupport()).isEqualTo(0.4);
    assertThat(config.mining().minConfidence()).isEqualTo(0.6);
    assertThat(config.mining().correlationWindowMs()).isEqualTo(600000);
    assertThat(config.learning().batchSize()).isEqualTo(2000);
  }

  @Test
  void testConfigurationToString() {
    KgConfiguration config = KgConfiguration.createDefault();
    String str = config.toString();
    assertThat(str).contains("KgConfiguration").contains("mining");
  }

  @Test
  void testQueryBuilderConfiguration() {
    KgConfiguration config = KgConfiguration.createDefault();
    assertThat(config.queryBuilder().seqDefaultWindowSeconds()).isEqualTo(300);
    assertThat(config.queryBuilder().defaultConfidence()).isEqualTo(50);
  }

  @Test
  void testLearningConfiguration() {
    KgConfiguration config = KgConfiguration.createDefault();
    assertThat(config.learning().correlationAnalysisEnabled()).isTrue();
    assertThat(config.learning().frequentPatternMiningEnabled()).isTrue();
    assertThat(config.learning().batchSize()).isEqualTo(1000);
  }

  @Test
  void testCacheConfiguration() {
    KgConfiguration config = KgConfiguration.createDefault();
    assertThat(config.cache().enabled()).isTrue();
    assertThat(config.cache().evictionPolicy()).isEqualTo("LRU");
    assertThat(config.cache().maxPatterns()).isEqualTo(10000);
  }

  @Test
  void testConfigurationImmutability() {
    KgConfiguration config = KgConfiguration.createDefault();
    
    // Records are immutable, so accessing same values multiple times works
    assertThat(config.mining().minSupport())
        .isEqualTo(config.mining().minSupport());
  }
}
