package com.ghatana.yappc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for DbGuardian agent.
 *
 * @doc.type record
 * @doc.purpose Expert database guardian for schema design, migration and query optimization input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record DbGuardianInput(@NotNull String databaseId, @NotNull String operationType, @NotNull String sqlOrSchema, @NotNull Map<String, Object> context) {
  public DbGuardianInput {
    if (databaseId == null || databaseId.isEmpty()) {
      throw new IllegalArgumentException("databaseId cannot be null or empty");
    }
    if (operationType == null || operationType.isEmpty()) {
      throw new IllegalArgumentException("operationType cannot be null or empty");
    }
    if (sqlOrSchema == null || sqlOrSchema.isEmpty()) {
      throw new IllegalArgumentException("sqlOrSchema cannot be null or empty");
    }
    if (context == null) {
      context = Map.of();
    }
  }
}
