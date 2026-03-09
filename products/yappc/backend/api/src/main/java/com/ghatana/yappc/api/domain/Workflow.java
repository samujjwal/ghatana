/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * @doc.type record
 * @doc.purpose Workflow configuration from YAML files
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record Workflow(
    String id,
    String name,
    String description,
    @JsonProperty("lifecycle_stages") List<String> lifecycleStages,
    List<Phase> phases) {
  public record Phase(
      String id,
      String name,
      String description,
      List<String> stages,
      List<String> tasks,
      @JsonProperty("estimated_duration") String estimatedDuration) {}
}
