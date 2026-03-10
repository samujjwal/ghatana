package com.ghatana.yappc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from MigrationWriter agent.
 *
 * @doc.type record
 * @doc.purpose Worker agent that generates database migration scripts output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record MigrationWriterOutput(@NotNull String migrationId, @NotNull String upScript, @NotNull String downScript, @NotNull Map<String, Object> metadata) {
  public MigrationWriterOutput {
    if (migrationId == null || migrationId.isEmpty()) {
      throw new IllegalArgumentException("migrationId cannot be null or empty");
    }
    if (upScript == null || upScript.isEmpty()) {
      throw new IllegalArgumentException("upScript cannot be null or empty");
    }
    if (downScript == null || downScript.isEmpty()) {
      throw new IllegalArgumentException("downScript cannot be null or empty");
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
