package com.ghatana.yappc.knowledge.traceability;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.ai.requirements.domain.requirement.Requirement;
import com.ghatana.yappc.ai.requirements.domain.requirement.RequirementMetadata;
import com.ghatana.yappc.ai.requirements.domain.requirement.RequirementPriority;
import com.ghatana.yappc.ai.requirements.domain.requirement.RequirementStatus;
import com.ghatana.yappc.ai.requirements.domain.requirement.RequirementType;
import com.ghatana.yappc.knowledge.YAPPCGraphService;
import com.ghatana.yappc.knowledge.model.YAPPCGraphMetadata;
import com.ghatana.yappc.knowledge.model.YAPPCGraphNode;
import com.ghatana.yappc.knowledge.query.KGSemanticSearchService;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Verifies the knowledge graph adapter maps requirement traceability operations onto the graph service
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("RequirementTraceabilityGraphAdapter Tests [GH-90000]")
class RequirementTraceabilityGraphAdapterTest extends EventloopTestBase {

  @Mock private YAPPCGraphService graphService;

  private RequirementTraceabilityGraphAdapter adapter;

  @BeforeEach
  void setUp() { // GH-90000
    MockitoAnnotations.openMocks(this); // GH-90000
    adapter = new RequirementTraceabilityGraphAdapter(graphService); // GH-90000
  }

  @Test
  @DisplayName("upsertRequirementNode creates a REQUIREMENT node in the graph [GH-90000]")
  void upsertRequirementNodeCreatesRequirementNode() { // GH-90000
    Requirement requirement = requirement(); // GH-90000
    when(graphService.createYAPPCNode(any())).thenAnswer(invocation -> Promise.of(invocation.getArgument(0))); // GH-90000

    runPromise(() -> adapter.upsertRequirementNode(requirement, "tenant-1")); // GH-90000

    verify(graphService).createYAPPCNode(eq(YAPPCGraphNode.builder() // GH-90000
        .id("req-1 [GH-90000]")
        .type(YAPPCGraphNode.YAPPCNodeType.REQUIREMENT) // GH-90000
        .name("Secure billing access [GH-90000]")
        .description("Users must authenticate with MFA before opening billing settings. [GH-90000]")
        .properties(Map.of( // GH-90000
            "status", "DRAFT",
            "priority", "MUST_HAVE",
            "currentVersion", 0))
        .tags(new java.util.LinkedHashSet<>()) // GH-90000
        .metadata(new YAPPCGraphMetadata( // GH-90000
            "tenant-1",
            "proj-1",
            null,
            "tester",
            requirement.getCreatedAt(), // GH-90000
            requirement.getUpdatedAt(), // GH-90000
            "0",
            Map.of("source", "requirements"))) // GH-90000
        .build())); // GH-90000
  }

  @Test
  @DisplayName("semanticSearch maps graph semantic matches to traceability candidates [GH-90000]")
  void semanticSearchMapsMatches() { // GH-90000
    YAPPCGraphNode node = YAPPCGraphNode.builder() // GH-90000
        .id("svc-1 [GH-90000]")
        .type(YAPPCGraphNode.YAPPCNodeType.SERVICE) // GH-90000
        .name("BillingService [GH-90000]")
        .description("Handles billing settings [GH-90000]")
        .properties(Map.of()) // GH-90000
        .tags(java.util.Set.of()) // GH-90000
        .metadata(new YAPPCGraphMetadata( // GH-90000
            "tenant-1", "proj-1", null, "tester",
            Instant.parse("2026-04-06T00:00:00Z [GH-90000]"),
            Instant.parse("2026-04-06T00:00:00Z [GH-90000]"),
            "1", Map.of())) // GH-90000
        .build(); // GH-90000
    when(graphService.semanticSearch("secure billing", "tenant-1", 5, 0.7)) // GH-90000
        .thenReturn(Promise.of(List.of(new KGSemanticSearchService.SemanticNodeMatch(node, 0.91, Map.of("tenantId", "tenant-1"))))); // GH-90000

    List<com.ghatana.yappc.ai.requirements.traceability.RequirementTraceabilityGraphPort.TraceabilityCandidate> result =
        runPromise(() -> adapter.semanticSearch("secure billing", "tenant-1", 5, 0.7)); // GH-90000

    assertThat(result).containsExactly( // GH-90000
        new com.ghatana.yappc.ai.requirements.traceability.RequirementTraceabilityGraphPort.TraceabilityCandidate( // GH-90000
            "svc-1", "SERVICE", "BillingService", 0.91, Map.of("tenantId", "tenant-1"))); // GH-90000
  }

  @Test
  @DisplayName("createRelationship delegates to the graph service [GH-90000]")
  void createRelationshipDelegates() { // GH-90000
    when(graphService.createCodeRelationship("req-1", "svc-1", "IMPLEMENTS", "tenant-1")) // GH-90000
        .thenReturn(Promise.of(null)); // GH-90000

    runPromise(() -> adapter.createRelationship("req-1", "svc-1", "IMPLEMENTS", "tenant-1", Map.of("linkedBy", "test"))); // GH-90000

    verify(graphService).createCodeRelationship("req-1", "svc-1", "IMPLEMENTS", "tenant-1"); // GH-90000
  }

  private Requirement requirement() { // GH-90000
    return Requirement.builder() // GH-90000
        .requirementId("req-1 [GH-90000]")
        .projectId("proj-1 [GH-90000]")
        .title("Secure billing access [GH-90000]")
        .description("Users must authenticate with MFA before opening billing settings. [GH-90000]")
        .type(RequirementType.FUNCTIONAL) // GH-90000
        .priority(RequirementPriority.MUST_HAVE) // GH-90000
        .status(RequirementStatus.DRAFT) // GH-90000
        .createdBy("tester [GH-90000]")
        .metadata(RequirementMetadata.empty()) // GH-90000
        .createdAt(Instant.parse("2026-04-06T00:00:00Z [GH-90000]"))
        .updatedAt(Instant.parse("2026-04-06T00:00:00Z [GH-90000]"))
        .build(); // GH-90000
  }
}
