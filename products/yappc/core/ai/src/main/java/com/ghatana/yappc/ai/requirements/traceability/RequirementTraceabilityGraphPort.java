package com.ghatana.yappc.ai.requirements.traceability;

import com.ghatana.yappc.ai.requirements.domain.requirement.Requirement;
import io.activej.promise.Promise;
import java.util.List;
import java.util.Map;

/**
 * @doc.type interface
 * @doc.purpose Explicit port for creating and querying requirement traceability data in the knowledge graph
 * @doc.layer product
 * @doc.pattern Port
 */
public interface RequirementTraceabilityGraphPort {

  Promise<Void> upsertRequirementNode(Requirement requirement, String tenantId);

  Promise<List<TraceabilityCandidate>> semanticSearch(
      String query,
      String tenantId,
      int limit,
      double minSimilarity);

  Promise<Void> createRelationship(
      String sourceNodeId,
      String targetNodeId,
      String relationshipType,
      String tenantId,
      Map<String, Object> properties);

  record TraceabilityCandidate(
      String nodeId,
      String nodeType,
      String nodeName,
      double similarity,
      Map<String, String> metadata) {}
}