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
@DisplayName("RequirementTraceabilityGraphAdapter Tests")
class RequirementTraceabilityGraphAdapterTest extends EventloopTestBase {

  @Mock private YAPPCGraphService graphService;

  private RequirementTraceabilityGraphAdapter adapter;

  @BeforeEach
  void setUp() { 
    MockitoAnnotations.openMocks(this); 
    adapter = new RequirementTraceabilityGraphAdapter(graphService); 
  }

  @Test
  @DisplayName("upsertRequirementNode creates a REQUIREMENT node in the graph")
  void upsertRequirementNodeCreatesRequirementNode() { 
    Requirement requirement = requirement(); 
    when(graphService.createYAPPCNode(any())).thenAnswer(invocation -> Promise.of(invocation.getArgument(0))); 

    runPromise(() -> adapter.upsertRequirementNode(requirement, "tenant-1")); 

    verify(graphService).createYAPPCNode(eq(YAPPCGraphNode.builder() 
        .id("req-1")
        .type(YAPPCGraphNode.YAPPCNodeType.REQUIREMENT) 
        .name("Secure billing access")
        .description("Users must authenticate with MFA before opening billing settings.")
        .properties(Map.of( 
            "status", "DRAFT",
            "priority", "MUST_HAVE",
            "currentVersion", 0))
        .tags(new java.util.LinkedHashSet<>()) 
        .metadata(new YAPPCGraphMetadata( 
            "tenant-1",
            "proj-1",
            null,
            "tester",
            requirement.getCreatedAt(), 
            requirement.getUpdatedAt(), 
            "0",
            Map.of("source", "requirements"))) 
        .build())); 
  }

  @Test
  @DisplayName("semanticSearch maps graph semantic matches to traceability candidates")
  void semanticSearchMapsMatches() { 
    YAPPCGraphNode node = YAPPCGraphNode.builder() 
        .id("svc-1")
        .type(YAPPCGraphNode.YAPPCNodeType.SERVICE) 
        .name("BillingService")
        .description("Handles billing settings")
        .properties(Map.of()) 
        .tags(java.util.Set.of()) 
        .metadata(new YAPPCGraphMetadata( 
            "tenant-1", "proj-1", null, "tester",
            Instant.parse("2026-04-06T00:00:00Z"),
            Instant.parse("2026-04-06T00:00:00Z"),
            "1", Map.of())) 
        .build(); 
    when(graphService.semanticSearch("secure billing", "tenant-1", 5, 0.7)) 
        .thenReturn(Promise.of(List.of(new KGSemanticSearchService.SemanticNodeMatch(node, 0.91, Map.of("tenantId", "tenant-1"))))); 

    List<com.ghatana.yappc.ai.requirements.traceability.RequirementTraceabilityGraphPort.TraceabilityCandidate> result =
        runPromise(() -> adapter.semanticSearch("secure billing", "tenant-1", 5, 0.7)); 

    assertThat(result).containsExactly( 
        new com.ghatana.yappc.ai.requirements.traceability.RequirementTraceabilityGraphPort.TraceabilityCandidate( 
            "svc-1", "SERVICE", "BillingService", 0.91, Map.of("tenantId", "tenant-1"))); 
  }

  @Test
  @DisplayName("createRelationship delegates to the graph service")
  void createRelationshipDelegates() { 
    when(graphService.createCodeRelationship("req-1", "svc-1", "IMPLEMENTS", "tenant-1")) 
        .thenReturn(Promise.of(null)); 

    runPromise(() -> adapter.createRelationship("req-1", "svc-1", "IMPLEMENTS", "tenant-1", Map.of("linkedBy", "test"))); 

    verify(graphService).createCodeRelationship("req-1", "svc-1", "IMPLEMENTS", "tenant-1"); 
  }

  private Requirement requirement() { 
    return Requirement.builder() 
        .requirementId("req-1")
        .projectId("proj-1")
        .title("Secure billing access")
        .description("Users must authenticate with MFA before opening billing settings.")
        .type(RequirementType.FUNCTIONAL) 
        .priority(RequirementPriority.MUST_HAVE) 
        .status(RequirementStatus.DRAFT) 
        .createdBy("tester")
        .metadata(RequirementMetadata.empty()) 
        .createdAt(Instant.parse("2026-04-06T00:00:00Z"))
        .updatedAt(Instant.parse("2026-04-06T00:00:00Z"))
        .build(); 
  }
}
