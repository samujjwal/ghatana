package com.ghatana.yappc.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link FeatureFlags}.
 *
 * @doc.type class
 * @doc.purpose Verify FeatureFlags behavior
 * @doc.layer test
 * @doc.pattern Unit Test
 */
@DisplayName("FeatureFlags Tests")
class FeatureFlagsTest {

  @Test
  @DisplayName("should return true for enabled flag")
  void shouldReturnTrueForEnabledFlag() { // GH-90000
    Map<String, Boolean> flags = Map.of("feature-a", true); // GH-90000
    FeatureFlags featureFlags = new FeatureFlags(flags); // GH-90000

    assertThat(featureFlags.enabled("feature-a")).isTrue();
  }

  @Test
  @DisplayName("should return false for disabled flag")
  void shouldReturnFalseForDisabledFlag() { // GH-90000
    Map<String, Boolean> flags = Map.of("feature-b", false); // GH-90000
    FeatureFlags featureFlags = new FeatureFlags(flags); // GH-90000

    assertThat(featureFlags.enabled("feature-b")).isFalse();
  }

  @Test
  @DisplayName("should return false for missing flag")
  void shouldReturnFalseForMissingFlag() { // GH-90000
    Map<String, Boolean> flags = Map.of("feature-a", true); // GH-90000
    FeatureFlags featureFlags = new FeatureFlags(flags); // GH-90000

    assertThat(featureFlags.enabled("non-existent")).isFalse();
  }

  @Test
  @DisplayName("should return false when flags map is null")
  void shouldReturnFalseWhenFlagsNull() { // GH-90000
    FeatureFlags featureFlags = new FeatureFlags(null); // GH-90000

    assertThat(featureFlags.enabled("any-flag")).isFalse();
  }

  @Test
  @DisplayName("should handle empty flags map")
  void shouldHandleEmptyFlagsMap() { // GH-90000
    FeatureFlags featureFlags = new FeatureFlags(java.util.Map.of()); // GH-90000

    assertThat(featureFlags.enabled("any-flag")).isFalse();
  }

  @Test
  @DisplayName("should store flags map correctly")
  void shouldStoreFlagsMap() { // GH-90000
    Map<String, Boolean> flags = Map.of("a", true, "b", false); // GH-90000
    FeatureFlags featureFlags = new FeatureFlags(flags); // GH-90000

    assertThat(featureFlags.flags()).isEqualTo(flags); // GH-90000
  }
}
