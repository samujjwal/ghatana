package com.ghatana.yappc.agent.specialists;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Output from AgentDispatcher agent.
 *
 * @doc.type record
 * @doc.purpose Task routing decision output
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record AgentDispatcherOutput(
    @NotNull String assignmentId,
    @NotNull String assignedAgentId,
    @NotNull String routingReason,
    @NotNull List<String> alternativeAgents,
    double confidenceScore,
    @NotNull Map<String, Object> metadata) {

  public AgentDispatcherOutput {
    if (assignmentId == null || assignmentId.isEmpty()) {
      throw new IllegalArgumentException("assignmentId cannot be null or empty");
    }
    if (assignedAgentId == null || assignedAgentId.isEmpty()) {
      throw new IllegalArgumentException("assignedAgentId cannot be null or empty");
    }
    if (routingReason == null || routingReason.isEmpty()) {
      routingReason = "capability-match";
    }
    if (alternativeAgents == null) {
      alternativeAgents = List.of();
    }
    if (confidenceScore < 0.0 || confidenceScore > 1.0) {
      confidenceScore = Math.max(0.0, Math.min(1.0, confidenceScore));
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }
}
