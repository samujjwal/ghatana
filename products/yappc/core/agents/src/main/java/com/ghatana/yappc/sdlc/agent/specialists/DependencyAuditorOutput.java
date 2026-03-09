package com.ghatana.yappc.sdlc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from DependencyAuditor agent.
 *
 * @doc.type record
 * @doc.purpose Worker agent that audits project dependencies for security and licensing output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record DependencyAuditorOutput(@NotNull String auditId, @NotNull List<String> vulnerableDeps, @NotNull List<String> licensingIssues, @NotNull Map<String, Object> metadata) {
  public DependencyAuditorOutput {
    if (auditId == null || auditId.isEmpty()) {
      throw new IllegalArgumentException("auditId cannot be null or empty");
    }
    if (vulnerableDeps == null) {
      vulnerableDeps = List.of();
    }
    if (licensingIssues == null) {
      licensingIssues = List.of();
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
