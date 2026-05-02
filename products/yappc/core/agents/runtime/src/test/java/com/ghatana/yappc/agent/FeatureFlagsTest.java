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
  void shouldReturnTrueForEnabledFlag() { 
    Map<String, Boolean> flags = Map.of("feature-a", true); 
    FeatureFlags featureFlags = new FeatureFlags(flags); 

    assertThat(featureFlags.enabled("feature-a")).isTrue();
  }

  @Test
  @DisplayName("should return false for disabled flag")
  void shouldReturnFalseForDisabledFlag() { 
    Map<String, Boolean> flags = Map.of("feature-b", false); 
    FeatureFlags featureFlags = new FeatureFlags(flags); 

    assertThat(featureFlags.enabled("feature-b")).isFalse();
  }

  @Test
  @DisplayName("should return false for missing flag")
  void shouldReturnFalseForMissingFlag() { 
    Map<String, Boolean> flags = Map.of("feature-a", true); 
    FeatureFlags featureFlags = new FeatureFlags(flags); 

    assertThat(featureFlags.enabled("non-existent")).isFalse();
  }

  @Test
  @DisplayName("should return false when flags map is null")
  void shouldReturnFalseWhenFlagsNull() { 
    FeatureFlags featureFlags = new FeatureFlags(null); 

    assertThat(featureFlags.enabled("any-flag")).isFalse();
  }

  @Test
  @DisplayName("should handle empty flags map")
  void shouldHandleEmptyFlagsMap() { 
    FeatureFlags featureFlags = new FeatureFlags(java.util.Map.of()); 

    assertThat(featureFlags.enabled("any-flag")).isFalse();
  }

  @Test
  @DisplayName("should store flags map correctly")
  void shouldStoreFlagsMap() { 
    Map<String, Boolean> flags = Map.of("a", true, "b", false); 
    FeatureFlags featureFlags = new FeatureFlags(flags); 

    assertThat(featureFlags.flags()).isEqualTo(flags); 
  }
}
