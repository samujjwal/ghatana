package com.ghatana.yappc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from QualityGuard agent.
 *
 * @doc.type record
 * @doc.purpose Expert quality guard agent enforcing code and artifact quality standards output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record QualityGuardOutput(@NotNull String guardId, boolean passed, @NotNull List<String> violations, @NotNull Map<String, Object> metadata) {
  public QualityGuardOutput {
    if (guardId == null || guardId.isEmpty()) {
      throw new IllegalArgumentException("guardId cannot be null or empty");
    }
    if (violations == null) {
      violations = List.of();
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
