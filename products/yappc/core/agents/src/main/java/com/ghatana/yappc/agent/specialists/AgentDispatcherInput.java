package com.ghatana.yappc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Input for AgentDispatcher agent.
 *
 * @doc.type record
 * @doc.purpose Task routing request input
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record AgentDispatcherInput(
    @NotNull String taskId,
    @NotNull String taskDescription,
    @NotNull List<String> requiredCapabilities,
    @NotNull String priority,
    @NotNull Map<String, Object> context) {

  public AgentDispatcherInput {
    if (taskId == null || taskId.isEmpty()) {
      throw new IllegalArgumentException("taskId cannot be null or empty");
    }
    if (taskDescription == null || taskDescription.isEmpty()) {
      throw new IllegalArgumentException("taskDescription cannot be null or empty");
    }
    if (requiredCapabilities == null) {
      requiredCapabilities = List.of();
    }
    if (priority == null || priority.isEmpty()) {
      priority = "NORMAL";
    }
    if (context == null) {
      context = Map.of();
    }
  }
}
