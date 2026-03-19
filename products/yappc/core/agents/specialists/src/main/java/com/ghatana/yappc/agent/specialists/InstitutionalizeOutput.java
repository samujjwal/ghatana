package com.ghatana.yappc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from Institutionalize agent.
 *
 * @doc.type record
 * @doc.purpose Worker agent that captures and institutionalizes learned patterns and practices output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record InstitutionalizeOutput(@NotNull String policyId, @NotNull String policyContent, double confidence, @NotNull Map<String, Object> metadata) {
  public InstitutionalizeOutput {
    if (policyId == null || policyId.isEmpty()) {
      throw new IllegalArgumentException("policyId cannot be null or empty");
    }
    if (policyContent == null || policyContent.isEmpty()) {
      throw new IllegalArgumentException("policyContent cannot be null or empty");
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
