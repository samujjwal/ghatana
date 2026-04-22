package com.ghatana.yappc.knowledge.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.ghatana.ai.embedding.EmbeddingService;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.api.events.DomainEvent;
import com.ghatana.yappc.knowledge.embedding.EmbeddingGenerator;
import com.ghatana.yappc.knowledge.extraction.EntityExtractor;
import com.ghatana.yappc.knowledge.extraction.EntityExtractor.EntityType;
import com.ghatana.yappc.knowledge.extraction.EntityExtractor.ExtractedEntity;
import com.ghatana.yappc.knowledge.extraction.EntityExtractor.ExtractedRelation;
import com.ghatana.yappc.knowledge.model.YAPPCGraphEdge;
import com.ghatana.yappc.knowledge.model.YAPPCGraphNode;
import io.activej.promise.Promise;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("KGUpdatePipeline Tests [GH-90000]")
class KGUpdatePipelineTest extends EventloopTestBase {

  @Mock private EmbeddingService embeddingService;

  @Test
  @DisplayName("process converts an event into persisted nodes and edges [GH-90000]")
  void processConvertsAnEventIntoPersistedNodesAndEdges() { // GH-90000
    when(embeddingService.createEmbedding(any())) // GH-90000
        .thenReturn(Promise.of(new com.ghatana.ai.embedding.EmbeddingResult("text", new float[] {0.1f, 0.2f}, "model"))) // GH-90000
        .thenReturn(Promise.of(new com.ghatana.ai.embedding.EmbeddingResult("text", new float[] {0.3f, 0.4f}, "model"))); // GH-90000

    RecordingNodeWriter nodeWriter = new RecordingNodeWriter(); // GH-90000
    RecordingEdgeWriter edgeWriter = new RecordingEdgeWriter(); // GH-90000
    KGUpdatePipeline pipeline =
        new KGUpdatePipeline( // GH-90000
            event -> Promise.of("Billing service implements invoice requirement [GH-90000]"),
            extractor(List.of( // GH-90000
                new ExtractedEntity( // GH-90000
                    "Billing Service",
                    EntityType.CODE_MODULE,
                    "Handles billing",
                    List.of(new ExtractedRelation("Invoice Requirement", "IMPLEMENTS"))), // GH-90000
                new ExtractedEntity( // GH-90000
                    "Invoice Requirement",
                    EntityType.REQUIREMENT,
                    "Invoicing must be supported",
                    List.of()))), // GH-90000
            new KGConflictResolver(), // GH-90000
            new EmbeddingGenerator(embeddingService, 2), // GH-90000
            nodeWriter,
            edgeWriter);

    KGUpdatePipeline.KGUpdateResult result = runPromise(() -> pipeline.process(new TestDomainEvent())); // GH-90000

    assertThat(result.nodesPersisted()).isEqualTo(2); // GH-90000
    assertThat(result.edgesPersisted()).isEqualTo(1); // GH-90000
    assertThat(nodeWriter.savedNodes).hasSize(2); // GH-90000
    assertThat(edgeWriter.savedEdges).singleElement().satisfies(edge -> { // GH-90000
      assertThat(edge.relationshipType()).isEqualTo(YAPPCGraphEdge.YAPPCRelationshipType.IMPLEMENTS); // GH-90000
      assertThat(edge.properties()).containsEntry("eventId", result.eventId()); // GH-90000
    });
  }

  @Test
  @DisplayName("process falls back unknown relation types and skips missing targets [GH-90000]")
  void processFallsBackUnknownRelationTypesAndSkipsMissingTargets() { // GH-90000
    when(embeddingService.createEmbedding(any())) // GH-90000
        .thenReturn(Promise.of(new com.ghatana.ai.embedding.EmbeddingResult("text", new float[] {1.0f}, "model"))); // GH-90000

    RecordingNodeWriter nodeWriter = new RecordingNodeWriter(); // GH-90000
    RecordingEdgeWriter edgeWriter = new RecordingEdgeWriter(); // GH-90000
    KGUpdatePipeline pipeline =
        new KGUpdatePipeline( // GH-90000
            event -> Promise.of("single concept [GH-90000]"),
            extractor(List.of( // GH-90000
                new ExtractedEntity( // GH-90000
                    "Decision Note",
                    EntityType.DECISION,
                    "Documented choice",
                    List.of( // GH-90000
                        new ExtractedRelation("Missing Target", "UNKNOWN"), // GH-90000
                        new ExtractedRelation("Decision Note", "UNKNOWN"))))), // GH-90000
            new KGConflictResolver(), // GH-90000
            new EmbeddingGenerator(embeddingService, 1), // GH-90000
            nodeWriter,
            edgeWriter);

    KGUpdatePipeline.KGUpdateResult result = runPromise(() -> pipeline.process(new TestDomainEvent())); // GH-90000

    assertThat(result.nodesPersisted()).isEqualTo(1); // GH-90000
    assertThat(result.edgesPersisted()).isEqualTo(1); // GH-90000
    assertThat(nodeWriter.savedNodes.getFirst().type()).isEqualTo(YAPPCGraphNode.YAPPCNodeType.DOCUMENT); // GH-90000
    assertThat(edgeWriter.savedEdges.getFirst().relationshipType()).isEqualTo(YAPPCGraphEdge.YAPPCRelationshipType.USES); // GH-90000
  }

  @Test
  @DisplayName("process handles concept entities and blank metadata values [GH-90000]")
  void processHandlesConceptEntitiesAndBlankMetadataValues() { // GH-90000
    when(embeddingService.createEmbedding(any())) // GH-90000
        .thenReturn(Promise.of(new com.ghatana.ai.embedding.EmbeddingResult("text", new float[] {0.5f}, "model"))); // GH-90000

    RecordingNodeWriter nodeWriter = new RecordingNodeWriter(); // GH-90000
    RecordingEdgeWriter edgeWriter = new RecordingEdgeWriter(); // GH-90000
    KGUpdatePipeline pipeline =
        new KGUpdatePipeline( // GH-90000
            event -> Promise.of("concept [GH-90000]"),
            extractor(List.of(new ExtractedEntity("!!!", EntityType.CONCEPT, "Shared concept", List.of()))), // GH-90000
            new KGConflictResolver(), // GH-90000
            new EmbeddingGenerator(embeddingService, 1), // GH-90000
            nodeWriter,
            edgeWriter);

    KGUpdatePipeline.KGUpdateResult result = runPromise(() -> pipeline.process(new BlankPayloadDomainEvent())); // GH-90000

    assertThat(result.nodeIds()).containsExactly("tenant-a:concept:entity [GH-90000]");
    assertThat(nodeWriter.savedNodes.getFirst().type()).isEqualTo(YAPPCGraphNode.YAPPCNodeType.DOCUMENT); // GH-90000
    assertThat(nodeWriter.savedNodes.getFirst().metadata().projectId()).isNull(); // GH-90000
    assertThat(nodeWriter.savedNodes.getFirst().metadata().workspaceId()).isNull(); // GH-90000
    assertThat(edgeWriter.savedEdges).isEmpty(); // GH-90000
  }

  @Test
  @DisplayName("update result normalizes null values [GH-90000]")
  void updateResultNormalizesNullValues() { // GH-90000
    KGUpdatePipeline.KGUpdateResult result = new KGUpdatePipeline.KGUpdateResult(null, -1, -2, null); // GH-90000

    assertThat(result.eventId()).isEqualTo("unknown-event [GH-90000]");
    assertThat(result.nodesPersisted()).isZero(); // GH-90000
    assertThat(result.edgesPersisted()).isZero(); // GH-90000
    assertThat(result.nodeIds()).isEmpty(); // GH-90000
  }

  @Test
  @DisplayName("embedded entity normalizes null values [GH-90000]")
  void embeddedEntityNormalizesNullValues() { // GH-90000
    KGUpdatePipeline.EmbeddedEntity entity = new KGUpdatePipeline.EmbeddedEntity(null, null, null, null, null, null); // GH-90000

    assertThat(entity.name()).isEqualTo("Unnamed entity [GH-90000]");
    assertThat(entity.type()).isEqualTo(EntityType.CONCEPT); // GH-90000
    assertThat(entity.description()).isEqualTo("Unnamed entity [GH-90000]");
    assertThat(entity.relations()).isEmpty(); // GH-90000
    assertThat(entity.tenantId()).isEqualTo("unknown-tenant [GH-90000]");
    assertThat(entity.embedding()).isEmpty(); // GH-90000
  }

  private EntityExtractor extractor(List<ExtractedEntity> entities) { // GH-90000
    return new EntityExtractor(mockYappcAiService()) { // GH-90000
      @Override
      public Promise<List<ExtractedEntity>> extract(String text, String sourceType) { // GH-90000
        return Promise.of(entities); // GH-90000
      }
    };
  }

  private com.ghatana.yappc.ai.service.YAPPCAIService mockYappcAiService() { // GH-90000
    return org.mockito.Mockito.mock(com.ghatana.yappc.ai.service.YAPPCAIService.class); // GH-90000
  }

  private static final class RecordingNodeWriter implements KGUpdatePipeline.NodeWriter {
    private final List<YAPPCGraphNode> savedNodes = new ArrayList<>(); // GH-90000

    @Override
    public Promise<YAPPCGraphNode> save(YAPPCGraphNode node) { // GH-90000
      savedNodes.add(node); // GH-90000
      return Promise.of(node); // GH-90000
    }
  }

  private static final class RecordingEdgeWriter implements KGUpdatePipeline.EdgeWriter {
    private final List<YAPPCGraphEdge> savedEdges = new ArrayList<>(); // GH-90000

    @Override
    public Promise<YAPPCGraphEdge> save(YAPPCGraphEdge edge) { // GH-90000
      savedEdges.add(edge); // GH-90000
      return Promise.of(edge); // GH-90000
    }
  }

  private static final class TestDomainEvent extends DomainEvent {

    private TestDomainEvent() { // GH-90000
      super("RequirementCreated", "aggregate-1", "Requirement", "tenant-a", "tester"); // GH-90000
    }

    @Override
    public Map<String, Object> toPayload() { // GH-90000
      return Map.of("projectId", "project-a", "workspaceId", "workspace-a"); // GH-90000
    }
  }

  private static final class BlankPayloadDomainEvent extends DomainEvent {

    private BlankPayloadDomainEvent() { // GH-90000
      super("RequirementCreated", "aggregate-1", "Requirement", "tenant-a", "tester"); // GH-90000
    }

    @Override
    public Map<String, Object> toPayload() { // GH-90000
      return Map.of("projectId", " ", "workspaceId", 7); // GH-90000
    }
  }
}
