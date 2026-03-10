package com.ghatana.yappc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for MigrationWriter agent.
 *
 * @doc.type record
 * @doc.purpose Worker agent that generates database migration scripts input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record MigrationWriterInput(@NotNull String sourceSchema, @NotNull String targetSchema, @NotNull String dbType, @NotNull Map<String, Object> options) {
  public MigrationWriterInput {
    if (sourceSchema == null || sourceSchema.isEmpty()) {
      throw new IllegalArgumentException("sourceSchema cannot be null or empty");
    }
    if (targetSchema == null || targetSchema.isEmpty()) {
      throw new IllegalArgumentException("targetSchema cannot be null or empty");
    }
    if (dbType == null || dbType.isEmpty()) {
      throw new IllegalArgumentException("dbType cannot be null or empty");
    }
    if (options == null) {
      options = Map.of();
    }
  }
}
