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
@DisplayName("FeatureFlags Tests [GH-90000]")
class FeatureFlagsTest {

  @Test
  @DisplayName("should return true for enabled flag [GH-90000]")
  void shouldReturnTrueForEnabledFlag() { // GH-90000
    Map<String, Boolean> flags = Map.of("feature-a", true); // GH-90000
    FeatureFlags featureFlags = new FeatureFlags(flags); // GH-90000

    assertThat(featureFlags.enabled("feature-a [GH-90000]")).isTrue();
  }

  @Test
  @DisplayName("should return false for disabled flag [GH-90000]")
  void shouldReturnFalseForDisabledFlag() { // GH-90000
    Map<String, Boolean> flags = Map.of("feature-b", false); // GH-90000
    FeatureFlags featureFlags = new FeatureFlags(flags); // GH-90000

    assertThat(featureFlags.enabled("feature-b [GH-90000]")).isFalse();
  }

  @Test
  @DisplayName("should return false for missing flag [GH-90000]")
  void shouldReturnFalseForMissingFlag() { // GH-90000
    Map<String, Boolean> flags = Map.of("feature-a", true); // GH-90000
    FeatureFlags featureFlags = new FeatureFlags(flags); // GH-90000

    assertThat(featureFlags.enabled("non-existent [GH-90000]")).isFalse();
  }

  @Test
  @DisplayName("should return false when flags map is null [GH-90000]")
  void shouldReturnFalseWhenFlagsNull() { // GH-90000
    FeatureFlags featureFlags = new FeatureFlags(null); // GH-90000

    assertThat(featureFlags.enabled("any-flag [GH-90000]")).isFalse();
  }

  @Test
  @DisplayName("should handle empty flags map [GH-90000]")
  void shouldHandleEmptyFlagsMap() { // GH-90000
    FeatureFlags featureFlags = new FeatureFlags(java.util.Map.of()); // GH-90000

    assertThat(featureFlags.enabled("any-flag [GH-90000]")).isFalse();
  }

  @Test
  @DisplayName("should store flags map correctly [GH-90000]")
  void shouldStoreFlagsMap() { // GH-90000
    Map<String, Boolean> flags = Map.of("a", true, "b", false); // GH-90000
    FeatureFlags featureFlags = new FeatureFlags(flags); // GH-90000

    assertThat(featureFlags.flags()).isEqualTo(flags); // GH-90000
  }
}
