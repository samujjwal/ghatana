package com.ghatana.yappc.agent.specialists;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for QueryExplain agent.
 *
 * @doc.type record
 * @doc.purpose Debug micro-agent that analyzes SQL query execution plans for optimization input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record QueryExplainInput(@NotNull String query, @NotNull String databaseType, @NotNull Map<String, Object> executionPlan) {
  public QueryExplainInput {
    if (query == null || query.isEmpty()) {
      throw new IllegalArgumentException("query cannot be null or empty");
    }
    if (databaseType == null || databaseType.isEmpty()) {
      throw new IllegalArgumentException("databaseType cannot be null or empty");
    }
    if (executionPlan == null) {
      executionPlan = Map.of();
    }
  }
}
