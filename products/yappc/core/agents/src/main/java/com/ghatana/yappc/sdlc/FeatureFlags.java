package com.ghatana.yappc.sdlc;

import java.util.Map;

/**
 * Feature flags for conditional step behavior.
 *
 * @param flags map of flag names to enabled state
 * @doc.type record
 * @doc.purpose Feature toggles for workflow customization
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record FeatureFlags(Map<String, Boolean> flags) {
  public boolean enabled(String key) {
    return flags != null && Boolean.TRUE.equals(flags.get(key));
  }
}
