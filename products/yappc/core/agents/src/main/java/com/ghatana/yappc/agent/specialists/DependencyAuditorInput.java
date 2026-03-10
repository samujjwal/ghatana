package com.ghatana.yappc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for DependencyAuditor agent.
 *
 * @doc.type record
 * @doc.purpose Worker agent that audits project dependencies for security and licensing input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record DependencyAuditorInput(@NotNull String projectId, @NotNull String manifestPath, @NotNull Map<String, Object> auditConfig) {
  public DependencyAuditorInput {
    if (projectId == null || projectId.isEmpty()) {
      throw new IllegalArgumentException("projectId cannot be null or empty");
    }
    if (manifestPath == null || manifestPath.isEmpty()) {
      throw new IllegalArgumentException("manifestPath cannot be null or empty");
    }
    if (auditConfig == null) {
      auditConfig = Map.of();
    }
  }
}
