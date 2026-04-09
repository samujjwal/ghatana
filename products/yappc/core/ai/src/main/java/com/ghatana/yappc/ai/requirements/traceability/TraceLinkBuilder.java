package com.ghatana.yappc.ai.requirements.traceability;

import com.ghatana.yappc.ai.requirements.domain.requirement.Requirement;
import io.activej.promise.Promise;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @doc.type class
 * @doc.purpose Creates requirement-to-code and requirement-to-test trace links through the knowledge graph port
 * @doc.layer product
 * @doc.pattern Service
 */
public final class TraceLinkBuilder {
  private static final int SEARCH_LIMIT = 12;
  private static final double MIN_SIMILARITY = 0.72d;

  private final RequirementTraceabilityGraphPort graphPort;

  public TraceLinkBuilder(RequirementTraceabilityGraphPort graphPort) {
    this.graphPort = Objects.requireNonNull(graphPort, "graphPort");
  }

  public Promise<TraceLinkSummary> linkRequirement(Requirement requirement, String tenantId) {
    Objects.requireNonNull(requirement, "requirement");
    Objects.requireNonNull(tenantId, "tenantId");

    String query = requirement.getTitle() + "\n" + requirement.getDescription();

    return graphPort.upsertRequirementNode(requirement, tenantId)
        .then(ignored -> graphPort.semanticSearch(query, tenantId, SEARCH_LIMIT, MIN_SIMILARITY))
        .then(candidates -> createLinks(requirement, tenantId, candidates));
  }

  private Promise<TraceLinkSummary> createLinks(
      Requirement requirement,
      String tenantId,
      List<RequirementTraceabilityGraphPort.TraceabilityCandidate> candidates) {
    List<RequirementTraceabilityGraphPort.TraceabilityCandidate> codeCandidates = candidates.stream()
        .filter(candidate -> isCodeNode(candidate.nodeType()))
        .limit(3)
        .toList();
    List<RequirementTraceabilityGraphPort.TraceabilityCandidate> testCandidates = candidates.stream()
        .filter(candidate -> "TEST".equals(candidate.nodeType()))
        .limit(3)
        .toList();

    Promise<Void> chain = Promise.complete();
    List<String> linkedCodeNodeIds = new ArrayList<>();
    List<String> linkedTestNodeIds = new ArrayList<>();

    for (RequirementTraceabilityGraphPort.TraceabilityCandidate candidate : codeCandidates) {
      chain = chain.then(ignored -> graphPort.createRelationship(
          requirement.getRequirementId(),
          candidate.nodeId(),
          "IMPLEMENTS",
          tenantId,
          Map.of(
              "linkedBy", "TraceLinkBuilder",
              "similarity", candidate.similarity(),
              "candidateType", candidate.nodeType())));
      linkedCodeNodeIds.add(candidate.nodeId());
    }

    for (RequirementTraceabilityGraphPort.TraceabilityCandidate candidate : testCandidates) {
      chain = chain.then(ignored -> graphPort.createRelationship(
          requirement.getRequirementId(),
          candidate.nodeId(),
          "TESTS",
          tenantId,
          Map.of(
              "linkedBy", "TraceLinkBuilder",
              "similarity", candidate.similarity(),
              "candidateType", candidate.nodeType())));
      linkedTestNodeIds.add(candidate.nodeId());
    }

    return chain.map(ignored -> new TraceLinkSummary(
        requirement.getRequirementId(),
        linkedCodeNodeIds,
        linkedTestNodeIds));
  }

  private boolean isCodeNode(String nodeType) {
    return switch (nodeType) {
      case "CLASS", "INTERFACE", "SERVICE", "COMPONENT", "API", "DATABASE" -> true;
      default -> false;
    };
  }

  public record TraceLinkSummary(
      String requirementId,
      List<String> implementationNodeIds,
      List<String> testNodeIds) {}
}
