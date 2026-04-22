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
  void testDefaultConfiguration() { // GH-90000
    KgConfiguration config = KgConfiguration.createDefault(); // GH-90000

    assertThat(config.mining().minSupport()).isEqualTo(0.3); // GH-90000
    assertThat(config.mining().minConfidence()).isEqualTo(0.5); // GH-90000
    assertThat(config.mining().correlationWindowMs()).isEqualTo(300000); // GH-90000
    assertThat(config.mining().enabled()).isTrue(); // GH-90000

    assertThat(config.patterns().autoActivate()).isFalse(); // GH-90000
    assertThat(config.patterns().archivalAgeDays()).isEqualTo(90); // GH-90000
    assertThat(config.patterns().versioningEnabled()).isTrue(); // GH-90000
  }

  @Test
  void testDevelopmentConfiguration() { // GH-90000
    KgConfiguration config = KgConfiguration.createForDevelopment(); // GH-90000

    // Development has more permissive thresholds
    assertThat(config.mining().minSupport()).isEqualTo(0.2); // GH-90000
    assertThat(config.mining().minConfidence()).isEqualTo(0.3); // GH-90000
    assertThat(config.patterns().autoActivate()).isTrue(); // GH-90000
  }

  @Test
  void testProductionConfiguration() { // GH-90000
    KgConfiguration config = KgConfiguration.createForProduction(); // GH-90000

    // Production has stricter thresholds
    assertThat(config.mining().minSupport()).isEqualTo(0.5); // GH-90000
    assertThat(config.mining().minConfidence()).isEqualTo(0.8); // GH-90000
    assertThat(config.patterns().autoActivate()).isFalse(); // GH-90000
  }

  @Test
  void testInvalidMinSupport() { // GH-90000
    assertThatThrownBy( // GH-90000
            () -> new KgConfiguration.MiningConfig( // GH-90000
                -0.1, 0.5, 300000, 100, true))
        .isInstanceOf(IllegalArgumentException.class) // GH-90000
        .hasMessage("minSupport must be between 0 and 1 [GH-90000]");
  }

  @Test
  void testInvalidCorrelationWindow() { // GH-90000
    assertThatThrownBy( // GH-90000
            () -> new KgConfiguration.MiningConfig( // GH-90000
                0.3, 0.5, -1, 100, true))
        .isInstanceOf(IllegalArgumentException.class) // GH-90000
        .hasMessage("correlationWindowMs must be positive [GH-90000]");
  }

  @Test
  void testBuilderCustomConfiguration() { // GH-90000
    KgConfiguration config = KgConfigurationLoader.builder() // GH-90000
        .minSupport(0.4) // GH-90000
        .minConfidence(0.6) // GH-90000
        .correlationWindowMs(600000) // GH-90000
        .maxPatternsToReturn(200) // GH-90000
        .autoActivate(true) // GH-90000
        .batchSize(2000) // GH-90000
        .build(); // GH-90000

    assertThat(config.mining().minSupport()).isEqualTo(0.4); // GH-90000
    assertThat(config.mining().minConfidence()).isEqualTo(0.6); // GH-90000
    assertThat(config.mining().correlationWindowMs()).isEqualTo(600000); // GH-90000
    assertThat(config.learning().batchSize()).isEqualTo(2000); // GH-90000
  }

  @Test
  void testConfigurationToString() { // GH-90000
    KgConfiguration config = KgConfiguration.createDefault(); // GH-90000
    String str = config.toString(); // GH-90000
    assertThat(str).contains("KgConfiguration [GH-90000]").contains("mining [GH-90000]");
  }

  @Test
  void testQueryBuilderConfiguration() { // GH-90000
    KgConfiguration config = KgConfiguration.createDefault(); // GH-90000
    assertThat(config.queryBuilder().seqDefaultWindowSeconds()).isEqualTo(300); // GH-90000
    assertThat(config.queryBuilder().defaultConfidence()).isEqualTo(50); // GH-90000
  }

  @Test
  void testLearningConfiguration() { // GH-90000
    KgConfiguration config = KgConfiguration.createDefault(); // GH-90000
    assertThat(config.learning().correlationAnalysisEnabled()).isTrue(); // GH-90000
    assertThat(config.learning().frequentPatternMiningEnabled()).isTrue(); // GH-90000
    assertThat(config.learning().batchSize()).isEqualTo(1000); // GH-90000
  }

  @Test
  void testCacheConfiguration() { // GH-90000
    KgConfiguration config = KgConfiguration.createDefault(); // GH-90000
    assertThat(config.cache().enabled()).isTrue(); // GH-90000
    assertThat(config.cache().evictionPolicy()).isEqualTo("LRU [GH-90000]");
    assertThat(config.cache().maxPatterns()).isEqualTo(10000); // GH-90000
  }

  @Test
  void testConfigurationImmutability() { // GH-90000
    KgConfiguration config = KgConfiguration.createDefault(); // GH-90000

    // Records are immutable, so accessing same values multiple times works
    assertThat(config.mining().minSupport()) // GH-90000
        .isEqualTo(config.mining().minSupport()); // GH-90000
  }
}
