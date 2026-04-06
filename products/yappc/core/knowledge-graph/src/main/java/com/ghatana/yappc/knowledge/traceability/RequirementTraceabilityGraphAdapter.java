package com.ghatana.yappc.knowledge.traceability;

import com.ghatana.yappc.ai.requirements.domain.requirement.Requirement;
import com.ghatana.yappc.ai.requirements.traceability.RequirementTraceabilityGraphPort;
import com.ghatana.yappc.knowledge.YAPPCGraphService;
import com.ghatana.yappc.knowledge.model.YAPPCGraphMetadata;
import com.ghatana.yappc.knowledge.model.YAPPCGraphNode;
import io.activej.promise.Promise;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @doc.type class
 * @doc.purpose Adapts the knowledge graph service to the requirement traceability graph port
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class RequirementTraceabilityGraphAdapter implements RequirementTraceabilityGraphPort {

  private final YAPPCGraphService graphService;

  public RequirementTraceabilityGraphAdapter(YAPPCGraphService graphService) {
    this.graphService = Objects.requireNonNull(graphService, "graphService");
  }

  @Override
  public Promise<Void> upsertRequirementNode(Requirement requirement, String tenantId) {
    YAPPCGraphNode node = YAPPCGraphNode.builder()
        .id(requirement.getRequirementId())
        .type(YAPPCGraphNode.YAPPCNodeType.REQUIREMENT)
        .name(requirement.getTitle())
        .description(requirement.getDescription())
        .properties(Map.of(
            "status", requirement.getStatus().name(),
            "priority", requirement.getPriority().name(),
            "currentVersion", requirement.getCurrentVersion()))
        .tags(new LinkedHashSet<>(requirement.getMetadata().tags()))
        .metadata(new YAPPCGraphMetadata(
            tenantId,
            requirement.getProjectId(),
            null,
            requirement.getCreatedBy(),
            requirement.getCreatedAt(),
            requirement.getUpdatedAt(),
            Integer.toString(requirement.getCurrentVersion()),
            Map.of("source", "requirements")))
        .build();

    return graphService.createYAPPCNode(node).map(ignored -> null);
  }

  @Override
  public Promise<List<TraceabilityCandidate>> semanticSearch(
      String query,
      String tenantId,
      int limit,
      double minSimilarity) {
    return graphService.semanticSearch(query, tenantId, limit, minSimilarity)
        .map(matches -> matches.stream()
            .map(match -> new TraceabilityCandidate(
                match.node().id(),
                match.node().type().name(),
                match.node().name(),
                match.similarity(),
                match.metadata()))
            .toList());
  }

  @Override
  public Promise<Void> createRelationship(
      String sourceNodeId,
      String targetNodeId,
      String relationshipType,
      String tenantId,
      Map<String, Object> properties) {
    return graphService.createCodeRelationship(sourceNodeId, targetNodeId, relationshipType, tenantId)
        .map(ignored -> null);
  }
}