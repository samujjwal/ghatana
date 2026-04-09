package com.ghatana.yappc.knowledge.pipeline;

import com.ghatana.yappc.knowledge.extraction.EntityExtractor.ExtractedEntity;
import com.ghatana.yappc.knowledge.extraction.EntityExtractor.ExtractedRelation;
import io.activej.promise.Promise;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * @doc.type class
 * @doc.purpose Deduplicates extracted graph entities and merges relationship detail before persistence.
 * @doc.layer product
 * @doc.pattern Resolver
 */
public final class KGConflictResolver {

  public Promise<List<ResolvedEntity>> resolve(List<ExtractedEntity> entities, String tenantId) {
    Objects.requireNonNull(tenantId, "tenantId");
    if (entities == null || entities.isEmpty()) {
      return Promise.of(List.of());
    }

    Map<String, ResolvedEntity> merged = new LinkedHashMap<>();
    for (ExtractedEntity entity : entities) {
      String key = dedupeKey(entity);
      ResolvedEntity existing = merged.get(key);
      merged.put(key, existing == null ? toResolvedEntity(entity, tenantId) : merge(existing, entity));
    }
    return Promise.of(List.copyOf(merged.values()));
  }

  private String dedupeKey(ExtractedEntity entity) {
    return entity.type().name() + ':' + entity.name().trim().toLowerCase(Locale.ROOT);
  }

  private ResolvedEntity toResolvedEntity(ExtractedEntity entity, String tenantId) {
    return new ResolvedEntity(entity.name(), entity.type(), entity.description(), entity.relations(), tenantId);
  }

  private ResolvedEntity merge(ResolvedEntity existing, ExtractedEntity incoming) {
    String description =
        incoming.description().length() > existing.description().length()
            ? incoming.description()
            : existing.description();
    LinkedHashSet<ExtractedRelation> relations = new LinkedHashSet<>(existing.relations());
    relations.addAll(incoming.relations());
    return new ResolvedEntity(existing.name(), existing.type(), description, List.copyOf(relations), existing.tenantId());
  }

  public record ResolvedEntity(
      String name,
      com.ghatana.yappc.knowledge.extraction.EntityExtractor.EntityType type,
      String description,
      List<ExtractedRelation> relations,
      String tenantId) {

    public ResolvedEntity {
      name = Objects.requireNonNullElse(name, "Unnamed entity");
      type = type == null ? com.ghatana.yappc.knowledge.extraction.EntityExtractor.EntityType.CONCEPT : type;
      description = Objects.requireNonNullElse(description, name);
      relations = relations == null ? List.of() : List.copyOf(relations);
      tenantId = Objects.requireNonNullElse(tenantId, "unknown-tenant");
    }
  }
}
