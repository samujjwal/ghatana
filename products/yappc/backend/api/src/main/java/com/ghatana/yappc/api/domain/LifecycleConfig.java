/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * @doc.type record
 * @doc.purpose Lifecycle configuration from YAML files
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record LifecycleConfig(List<Stage> stages, List<Transition> transitions) {
  public record Stage(
      String id,
      String name,
      String description,
      int order,
      String icon,
      String color,
      @JsonProperty("required_artifacts") List<String> requiredArtifacts,
      @JsonProperty("output_artifacts") List<String> outputArtifacts) {}

  public record Transition(
      String from,
      String to,
      List<String> conditions,
      @JsonProperty("auto_trigger") boolean autoTrigger) {}
}
