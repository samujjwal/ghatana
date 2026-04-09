package com.ghatana.yappc.knowledge.pipeline;

import com.ghatana.yappc.api.events.DomainEvent;
import com.ghatana.yappc.knowledge.embedding.EmbeddingGenerator;
import com.ghatana.yappc.knowledge.extraction.EntityExtractor;
import com.ghatana.yappc.knowledge.extraction.EntityExtractor.ExtractedRelation;
import com.ghatana.yappc.knowledge.model.YAPPCGraphEdge;
import com.ghatana.yappc.knowledge.model.YAPPCGraphMetadata;
import com.ghatana.yappc.knowledge.model.YAPPCGraphNode;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * @doc.type class
 * @doc.purpose Processes domain events into tenant-scoped knowledge graph nodes and edges with generated embeddings.
 * @doc.layer product
 * @doc.pattern Pipeline
 */
public final class KGUpdatePipeline {

  private final EventTextConverter eventTextConverter;
  private final EntityExtractor entityExtractor;
  private final KGConflictResolver conflictResolver;
  private final EmbeddingGenerator embeddingGenerator;
  private final NodeWriter nodeWriter;
  private final EdgeWriter edgeWriter;

  public KGUpdatePipeline(
      EventTextConverter eventTextConverter,
      EntityExtractor entityExtractor,
      KGConflictResolver conflictResolver,
      EmbeddingGenerator embeddingGenerator,
      NodeWriter nodeWriter,
      EdgeWriter edgeWriter) {
    this.eventTextConverter = Objects.requireNonNull(eventTextConverter, "eventTextConverter");
    this.entityExtractor = Objects.requireNonNull(entityExtractor, "entityExtractor");
    this.conflictResolver = Objects.requireNonNull(conflictResolver, "conflictResolver");
    this.embeddingGenerator = Objects.requireNonNull(embeddingGenerator, "embeddingGenerator");
    this.nodeWriter = Objects.requireNonNull(nodeWriter, "nodeWriter");
    this.edgeWriter = Objects.requireNonNull(edgeWriter, "edgeWriter");
  }

  public Promise<KGUpdateResult> process(DomainEvent event) {
    Objects.requireNonNull(event, "event");
    return eventTextConverter
        .convert(event)
        .then(text -> entityExtractor.extract(text, event.getEventType()))
        .then(entities -> conflictResolver.resolve(entities, event.getTenantId()))
        .then(resolved -> embedResolved(resolved, 0, new ArrayList<>()))
        .then(embedded -> persist(event, embedded));
  }

  private Promise<List<EmbeddedEntity>> embedResolved(
      List<KGConflictResolver.ResolvedEntity> entities,
      int index,
      List<EmbeddedEntity> accumulated) {
    if (index >= entities.size()) {
      return Promise.of(List.copyOf(accumulated));
    }
    KGConflictResolver.ResolvedEntity entity = entities.get(index);
    return embeddingGenerator
        .generate(entity.description())
        .then(
            embedding -> {
              accumulated.add(
                  new EmbeddedEntity(
                      entity.name(), entity.type(), entity.description(), entity.relations(), entity.tenantId(), embedding));
              return embedResolved(entities, index + 1, accumulated);
            });
  }

  private Promise<KGUpdateResult> persist(DomainEvent event, List<EmbeddedEntity> entities) {
    Map<String, String> nodeIdsByName = new LinkedHashMap<>();
    for (EmbeddedEntity entity : entities) {
      nodeIdsByName.put(entity.name().toLowerCase(Locale.ROOT), nodeId(event, entity.name(), entity.type().name()));
    }
    return persistNodes(event, entities, nodeIdsByName, 0, new ArrayList<>())
        .then(nodeIds -> persistEdges(event, entities, nodeIdsByName, 0, new ArrayList<>())
            .map(edges -> new KGUpdateResult(event.getEventId(), nodeIds.size(), edges.size(), nodeIds)));
  }

  private Promise<List<String>> persistNodes(
      DomainEvent event,
      List<EmbeddedEntity> entities,
      Map<String, String> nodeIdsByName,
      int index,
      List<String> nodeIds) {
    if (index >= entities.size()) {
      return Promise.of(List.copyOf(nodeIds));
    }
    EmbeddedEntity entity = entities.get(index);
    YAPPCGraphNode node = toNode(event, entity, nodeIdsByName.get(entity.name().toLowerCase(Locale.ROOT)));
    return nodeWriter.save(node).then(saved -> {
      nodeIds.add(saved.id());
      return persistNodes(event, entities, nodeIdsByName, index + 1, nodeIds);
    });
  }

  private Promise<List<String>> persistEdges(
      DomainEvent event,
      List<EmbeddedEntity> entities,
      Map<String, String> nodeIdsByName,
      int index,
      List<String> edgeIds) {
    if (index >= entities.size()) {
      return Promise.of(List.copyOf(edgeIds));
    }
    EmbeddedEntity entity = entities.get(index);
    List<YAPPCGraphEdge> edges = new ArrayList<>();
    String sourceNodeId = nodeIdsByName.get(entity.name().toLowerCase(Locale.ROOT));
    for (ExtractedRelation relation : entity.relations()) {
      String targetNodeId = nodeIdsByName.get(relation.target().toLowerCase(Locale.ROOT));
      if (targetNodeId != null) {
        edges.add(toEdge(event, sourceNodeId, targetNodeId, relation.type()));
      }
    }
    return persistEdgeList(edges, 0, edgeIds).then(ignored -> persistEdges(event, entities, nodeIdsByName, index + 1, edgeIds));
  }

  private Promise<List<String>> persistEdgeList(List<YAPPCGraphEdge> edges, int index, List<String> edgeIds) {
    if (index >= edges.size()) {
      return Promise.of(List.copyOf(edgeIds));
    }
    return edgeWriter.save(edges.get(index)).then(saved -> {
      edgeIds.add(saved.id());
      return persistEdgeList(edges, index + 1, edgeIds);
    });
  }

  private YAPPCGraphNode toNode(DomainEvent event, EmbeddedEntity entity, String nodeId) {
    return YAPPCGraphNode.builder()
        .id(nodeId)
        .type(mapNodeType(entity.type()))
        .name(entity.name())
        .description(entity.description())
        .properties(Map.of("embeddingDimension", entity.embedding().length, "aggregateType", event.getAggregateType()))
        .tags(java.util.Set.of(event.getEventType().toLowerCase(Locale.ROOT)))
        .metadata(metadata(event))
        .build();
  }

  private YAPPCGraphEdge toEdge(DomainEvent event, String sourceNodeId, String targetNodeId, String relationType) {
    String normalizedType = relationType.toUpperCase(Locale.ROOT);
    YAPPCGraphEdge.YAPPCRelationshipType edgeType;
    try {
      edgeType = YAPPCGraphEdge.YAPPCRelationshipType.valueOf(normalizedType);
    } catch (IllegalArgumentException exception) {
      edgeType = YAPPCGraphEdge.YAPPCRelationshipType.USES;
    }
    return YAPPCGraphEdge.builder()
        .id(sourceNodeId + '_' + targetNodeId + '_' + edgeType.name())
        .sourceNodeId(sourceNodeId)
        .targetNodeId(targetNodeId)
        .relationshipType(edgeType)
        .properties(Map.of("eventId", event.getEventId()))
        .metadata(metadata(event))
        .build();
  }

  private YAPPCGraphMetadata metadata(DomainEvent event) {
    Map<String, Object> payload = event.toPayload();
    Instant occurredAt = event.getOccurredAt();
    return new YAPPCGraphMetadata(
        event.getTenantId(),
        payloadValue(payload, "projectId"),
        payloadValue(payload, "workspaceId"),
        event.getUserId(),
        occurredAt,
        occurredAt,
        Integer.toString(event.getSchemaVersion()),
        Map.of("eventType", event.getEventType(), "aggregateType", event.getAggregateType()));
  }

  private String payloadValue(Map<String, Object> payload, String key) {
    Object value = payload.get(key);
    return value instanceof String string && !string.isBlank() ? string : null;
  }

  private String nodeId(DomainEvent event, String name, String type) {
    String normalized = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    return event.getTenantId() + ':' + type.toLowerCase(Locale.ROOT) + ':' + (normalized.isBlank() ? "entity" : normalized);
  }

  private YAPPCGraphNode.YAPPCNodeType mapNodeType(
      com.ghatana.yappc.knowledge.extraction.EntityExtractor.EntityType type) {
    return switch (type) {
      case REQUIREMENT -> YAPPCGraphNode.YAPPCNodeType.REQUIREMENT;
      case CODE_MODULE -> YAPPCGraphNode.YAPPCNodeType.COMPONENT;
      case DECISION -> YAPPCGraphNode.YAPPCNodeType.DOCUMENT;
      case CONCEPT -> YAPPCGraphNode.YAPPCNodeType.DOCUMENT;
    };
  }

  public interface EventTextConverter {
    Promise<String> convert(DomainEvent event);
  }

  public interface NodeWriter {
    Promise<YAPPCGraphNode> save(YAPPCGraphNode node);
  }

  public interface EdgeWriter {
    Promise<YAPPCGraphEdge> save(YAPPCGraphEdge edge);
  }

  record EmbeddedEntity(
      String name,
      com.ghatana.yappc.knowledge.extraction.EntityExtractor.EntityType type,
      String description,
      List<ExtractedRelation> relations,
      String tenantId,
      float[] embedding) {

    EmbeddedEntity {
      name = Objects.requireNonNullElse(name, "Unnamed entity");
      type = type == null ? com.ghatana.yappc.knowledge.extraction.EntityExtractor.EntityType.CONCEPT : type;
      description = Objects.requireNonNullElse(description, name);
      relations = relations == null ? List.of() : List.copyOf(relations);
      tenantId = Objects.requireNonNullElse(tenantId, "unknown-tenant");
      embedding = embedding == null ? new float[0] : embedding.clone();
    }
  }

  public record KGUpdateResult(String eventId, int nodesPersisted, int edgesPersisted, List<String> nodeIds) {

    public KGUpdateResult {
      eventId = Objects.requireNonNullElse(eventId, "unknown-event");
      nodesPersisted = Math.max(0, nodesPersisted);
      edgesPersisted = Math.max(0, edgesPersisted);
      nodeIds = nodeIds == null ? List.of() : List.copyOf(nodeIds);
    }
  }
}
