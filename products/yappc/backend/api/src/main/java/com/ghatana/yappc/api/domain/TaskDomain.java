/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * @doc.type record
 * @doc.purpose Task domain configuration from YAML files
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record TaskDomain(
    String id,
    String name,
    String description,
    int order,
    @JsonProperty("task_count") int taskCount,
    String icon,
    String color,
    String file,
    List<Task> tasks) {
  public record Task(
      String id,
      String name,
      String description,
      String category,
      List<String> tags,
      List<Subtask> subtasks,
      List<Tool> tools,
      List<String> collaborators,
      List<String> dependencies,
      @JsonProperty("success_criteria") List<String> successCriteria,
      @JsonProperty("estimated_duration") String estimatedDuration) {}

  public record Subtask(String id, String name, String description) {}

  public record Tool(String name, String purpose) {}
}
