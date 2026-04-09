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

@ExtendWith(MockitoExtension.class)
@DisplayName("KGUpdatePipeline Tests")
class KGUpdatePipelineTest extends EventloopTestBase {

  @Mock private EmbeddingService embeddingService;

  @Test
  @DisplayName("process converts an event into persisted nodes and edges")
  void processConvertsAnEventIntoPersistedNodesAndEdges() {
    when(embeddingService.createEmbedding(any()))
        .thenReturn(Promise.of(new com.ghatana.ai.embedding.EmbeddingResult("text", new float[] {0.1f, 0.2f}, "model")))
        .thenReturn(Promise.of(new com.ghatana.ai.embedding.EmbeddingResult("text", new float[] {0.3f, 0.4f}, "model")));

    RecordingNodeWriter nodeWriter = new RecordingNodeWriter();
    RecordingEdgeWriter edgeWriter = new RecordingEdgeWriter();
    KGUpdatePipeline pipeline =
        new KGUpdatePipeline(
            event -> Promise.of("Billing service implements invoice requirement"),
            extractor(List.of(
                new ExtractedEntity(
                    "Billing Service",
                    EntityType.CODE_MODULE,
                    "Handles billing",
                    List.of(new ExtractedRelation("Invoice Requirement", "IMPLEMENTS"))),
                new ExtractedEntity(
                    "Invoice Requirement",
                    EntityType.REQUIREMENT,
                    "Invoicing must be supported",
                    List.of()))),
            new KGConflictResolver(),
            new EmbeddingGenerator(embeddingService, 2),
            nodeWriter,
            edgeWriter);

    KGUpdatePipeline.KGUpdateResult result = runPromise(() -> pipeline.process(new TestDomainEvent()));

    assertThat(result.nodesPersisted()).isEqualTo(2);
    assertThat(result.edgesPersisted()).isEqualTo(1);
    assertThat(nodeWriter.savedNodes).hasSize(2);
    assertThat(edgeWriter.savedEdges).singleElement().satisfies(edge -> {
      assertThat(edge.relationshipType()).isEqualTo(YAPPCGraphEdge.YAPPCRelationshipType.IMPLEMENTS);
      assertThat(edge.properties()).containsEntry("eventId", result.eventId());
    });
  }

  @Test
  @DisplayName("process falls back unknown relation types and skips missing targets")
  void processFallsBackUnknownRelationTypesAndSkipsMissingTargets() {
    when(embeddingService.createEmbedding(any()))
        .thenReturn(Promise.of(new com.ghatana.ai.embedding.EmbeddingResult("text", new float[] {1.0f}, "model")));

    RecordingNodeWriter nodeWriter = new RecordingNodeWriter();
    RecordingEdgeWriter edgeWriter = new RecordingEdgeWriter();
    KGUpdatePipeline pipeline =
        new KGUpdatePipeline(
            event -> Promise.of("single concept"),
            extractor(List.of(
                new ExtractedEntity(
                    "Decision Note",
                    EntityType.DECISION,
                    "Documented choice",
                    List.of(
                        new ExtractedRelation("Missing Target", "UNKNOWN"),
                        new ExtractedRelation("Decision Note", "UNKNOWN"))))),
            new KGConflictResolver(),
            new EmbeddingGenerator(embeddingService, 1),
            nodeWriter,
            edgeWriter);

    KGUpdatePipeline.KGUpdateResult result = runPromise(() -> pipeline.process(new TestDomainEvent()));

    assertThat(result.nodesPersisted()).isEqualTo(1);
    assertThat(result.edgesPersisted()).isEqualTo(1);
    assertThat(nodeWriter.savedNodes.getFirst().type()).isEqualTo(YAPPCGraphNode.YAPPCNodeType.DOCUMENT);
    assertThat(edgeWriter.savedEdges.getFirst().relationshipType()).isEqualTo(YAPPCGraphEdge.YAPPCRelationshipType.USES);
  }

  @Test
  @DisplayName("process handles concept entities and blank metadata values")
  void processHandlesConceptEntitiesAndBlankMetadataValues() {
    when(embeddingService.createEmbedding(any()))
        .thenReturn(Promise.of(new com.ghatana.ai.embedding.EmbeddingResult("text", new float[] {0.5f}, "model")));

    RecordingNodeWriter nodeWriter = new RecordingNodeWriter();
    RecordingEdgeWriter edgeWriter = new RecordingEdgeWriter();
    KGUpdatePipeline pipeline =
        new KGUpdatePipeline(
            event -> Promise.of("concept"),
            extractor(List.of(new ExtractedEntity("!!!", EntityType.CONCEPT, "Shared concept", List.of()))),
            new KGConflictResolver(),
            new EmbeddingGenerator(embeddingService, 1),
            nodeWriter,
            edgeWriter);

    KGUpdatePipeline.KGUpdateResult result = runPromise(() -> pipeline.process(new BlankPayloadDomainEvent()));

    assertThat(result.nodeIds()).containsExactly("tenant-a:concept:entity");
    assertThat(nodeWriter.savedNodes.getFirst().type()).isEqualTo(YAPPCGraphNode.YAPPCNodeType.DOCUMENT);
    assertThat(nodeWriter.savedNodes.getFirst().metadata().projectId()).isNull();
    assertThat(nodeWriter.savedNodes.getFirst().metadata().workspaceId()).isNull();
    assertThat(edgeWriter.savedEdges).isEmpty();
  }

  @Test
  @DisplayName("update result normalizes null values")
  void updateResultNormalizesNullValues() {
    KGUpdatePipeline.KGUpdateResult result = new KGUpdatePipeline.KGUpdateResult(null, -1, -2, null);

    assertThat(result.eventId()).isEqualTo("unknown-event");
    assertThat(result.nodesPersisted()).isZero();
    assertThat(result.edgesPersisted()).isZero();
    assertThat(result.nodeIds()).isEmpty();
  }

  @Test
  @DisplayName("embedded entity normalizes null values")
  void embeddedEntityNormalizesNullValues() {
    KGUpdatePipeline.EmbeddedEntity entity = new KGUpdatePipeline.EmbeddedEntity(null, null, null, null, null, null);

    assertThat(entity.name()).isEqualTo("Unnamed entity");
    assertThat(entity.type()).isEqualTo(EntityType.CONCEPT);
    assertThat(entity.description()).isEqualTo("Unnamed entity");
    assertThat(entity.relations()).isEmpty();
    assertThat(entity.tenantId()).isEqualTo("unknown-tenant");
    assertThat(entity.embedding()).isEmpty();
  }

  private EntityExtractor extractor(List<ExtractedEntity> entities) {
    return new EntityExtractor(mockYappcAiService()) {
      @Override
      public Promise<List<ExtractedEntity>> extract(String text, String sourceType) {
        return Promise.of(entities);
      }
    };
  }

  private com.ghatana.yappc.ai.service.YAPPCAIService mockYappcAiService() {
    return org.mockito.Mockito.mock(com.ghatana.yappc.ai.service.YAPPCAIService.class);
  }

  private static final class RecordingNodeWriter implements KGUpdatePipeline.NodeWriter {
    private final List<YAPPCGraphNode> savedNodes = new ArrayList<>();

    @Override
    public Promise<YAPPCGraphNode> save(YAPPCGraphNode node) {
      savedNodes.add(node);
      return Promise.of(node);
    }
  }

  private static final class RecordingEdgeWriter implements KGUpdatePipeline.EdgeWriter {
    private final List<YAPPCGraphEdge> savedEdges = new ArrayList<>();

    @Override
    public Promise<YAPPCGraphEdge> save(YAPPCGraphEdge edge) {
      savedEdges.add(edge);
      return Promise.of(edge);
    }
  }

  private static final class TestDomainEvent extends DomainEvent {

    private TestDomainEvent() {
      super("RequirementCreated", "aggregate-1", "Requirement", "tenant-a", "tester");
    }

    @Override
    public Map<String, Object> toPayload() {
      return Map.of("projectId", "project-a", "workspaceId", "workspace-a");
    }
  }

  private static final class BlankPayloadDomainEvent extends DomainEvent {

    private BlankPayloadDomainEvent() {
      super("RequirementCreated", "aggregate-1", "Requirement", "tenant-a", "tester");
    }

    @Override
    public Map<String, Object> toPayload() {
      return Map.of("projectId", " ", "workspaceId", 7);
    }
  }
}
