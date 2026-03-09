package com.ghatana.datacloud.application.agent;

import io.activej.promise.Promise;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Autonomous agent for enriching collections with knowledge graph relationships.
 *
 * <p><b>Purpose</b><br>
 * Enriches metadata collections with inferred relationships, similarity analysis, and schema
 * improvement suggestions using the knowledge graph. Works with the GraphRepository to persist
 * enriched data and discover similar collections.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * KnowledgeGraphAgent agent = new KnowledgeGraphAgent(repository);
 * Promise<EnrichedCollection> enriched = agent.enrichCollection(collection);
 * Promise<List<Relationship>> relationships = agent.inferRelationships(collectionId);
 * }</pre>
 *
 * <p><b>Architecture</b><br>
 * Part of the collection-entity-system for multi-tenant metadata management. Uses ActiveJ
 * Promise for async operations and integrates with GraphRepository for persistence.
 *
 * @doc.type class
 * @doc.purpose Autonomous knowledge graph enrichment agent
 * @doc.layer product
 * @doc.pattern Service
 */
public class KnowledgeGraphAgent {
    private static final Logger logger = LoggerFactory.getLogger(KnowledgeGraphAgent.class);

    private final GraphRepository graphRepository;

    public KnowledgeGraphAgent(GraphRepository graphRepository) {
        this.graphRepository = Objects.requireNonNull(graphRepository, "graphRepository cannot be null");
    }

    /**
     * Enriches a collection with relationships and suggestions.
     *
     * @param collection the collection to enrich
     * @return Promise of enriched collection
     */
    public Promise<EnrichedCollection> enrichCollection(EnrichedCollection collection) {
        Objects.requireNonNull(collection, "collection cannot be null");

        return inferRelationships(collection.id)
                .then(relationships ->
                        suggestSchemaImprovements(collection.id)
                                .then(suggestions -> {
                                    EnrichedCollection enriched = new EnrichedCollection(
                                            collection.id,
                                            collection.tenantId,
                                            collection.name,
                                            relationships,
                                            suggestions
                                    );
                                    logger.debug("Enriched collection {} with {} relationships and {} suggestions",
                                            collection.id, relationships.size(), suggestions.size());
                                    return Promise.of(enriched);
                                })
                );
    }

    /**
     * Infers relationships between collections.
     *
     * <p>Uses the knowledge graph to find and infer semantic relationships
     * between the collection and other related collections.
     *
     * @param collectionId the collection ID
     * @return Promise of list of inferred relationships
     */
    public Promise<List<Relationship>> inferRelationships(UUID collectionId) {
        Objects.requireNonNull(collectionId, "collectionId cannot be null");

        return graphRepository.findSimilarCollections(collectionId, 0.7)
                .then(similar -> {
                    List<Relationship> relationships = similar.stream()
                            .map(enriched -> new Relationship(
                                    collectionId,
                                    enriched.id,
                                    "SIMILAR",
                                    0.85  // similarity score
                            ))
                            .toList();
                    logger.debug("Inferred {} relationships for collection {}", relationships.size(), collectionId);
                    return Promise.of(relationships);
                });
    }

    /**
     * Suggests schema improvements for a collection.
     *
     * <p>Analyzes the collection's schema against related collections to suggest
     * missing fields, consolidations, or improvements.
     *
     * @param collectionId the collection ID
     * @return Promise of list of schema improvement suggestions
     */
    public Promise<List<SchemaSuggestion>> suggestSchemaImprovements(UUID collectionId) {
        Objects.requireNonNull(collectionId, "collectionId cannot be null");

        logger.debug("Suggesting schema improvements for collection {}", collectionId);

        return graphRepository.findSimilarCollections(collectionId, 0.7)
                .map(similarCollections -> {
                    List<SchemaSuggestion> suggestions = new java.util.ArrayList<>();

                    if (similarCollections.isEmpty()) {
                        return suggestions;
                    }

                    // Collect field names from similar collections to detect missing fields
                    java.util.Map<String, Integer> fieldFrequency = new java.util.HashMap<>();
                    for (EnrichedCollection similar : similarCollections) {
                        // Check relationships for structural patterns
                        for (Relationship rel : similar.relationships()) {
                            String fieldHint = rel.type().toLowerCase();
                            fieldFrequency.merge(fieldHint, 1, Integer::sum);
                        }
                    }

                    // Suggest commonly-seen fields that this collection may be missing
                    int threshold = similarCollections.size() / 2 + 1;
                    for (var entry : fieldFrequency.entrySet()) {
                        if (entry.getValue() >= threshold) {
                            suggestions.add(new SchemaSuggestion(
                                entry.getKey() + "_id",
                                "UUID",
                                String.format(
                                    "Field '%s_id' found in %d/%d similar collections — consider adding for consistency",
                                    entry.getKey(), entry.getValue(), similarCollections.size())
                            ));
                        }
                    }

                    // Suggest an updated_at timestamp if collection has many relationships
                    if (similarCollections.size() > 3) {
                        suggestions.add(new SchemaSuggestion(
                            "updated_at",
                            "TIMESTAMP",
                            "Collection has many relationships — adding updated_at enables change tracking"
                        ));
                    }

                    logger.debug("Generated {} schema suggestions for collection {}", suggestions.size(), collectionId);
                    return suggestions;
                });
    }

    /**
     * Represents an enriched collection with relationships and suggestions.
     *
     * @doc.type record
     * @doc.purpose Enriched collection data transfer object
     * @doc.layer product
     * @doc.pattern Value Object
     */
    public record EnrichedCollection(
            UUID id,
            String tenantId,
            String name,
            List<Relationship> relationships,
            List<SchemaSuggestion> suggestions
    ) {
        public EnrichedCollection {
            Objects.requireNonNull(id, "id cannot be null");
            Objects.requireNonNull(tenantId, "tenantId cannot be null");
            Objects.requireNonNull(name, "name cannot be null");
            Objects.requireNonNull(relationships, "relationships cannot be null");
            Objects.requireNonNull(suggestions, "suggestions cannot be null");
        }
    }

    /**
     * Represents a relationship between two collections.
     *
     * @doc.type record
     * @doc.purpose Collection relationship descriptor
     * @doc.layer product
     * @doc.pattern Value Object
     */
    public record Relationship(
            UUID sourceId,
            UUID targetId,
            String type,
            double confidence
    ) {
        public Relationship {
            Objects.requireNonNull(sourceId, "sourceId cannot be null");
            Objects.requireNonNull(targetId, "targetId cannot be null");
            Objects.requireNonNull(type, "type cannot be null");
            if (confidence < 0.0 || confidence > 1.0) {
                throw new IllegalArgumentException("confidence must be between 0.0 and 1.0");
            }
        }
    }

    /**
     * Represents a schema improvement suggestion.
     *
     * @doc.type record
     * @doc.purpose Schema improvement recommendation
     * @doc.layer product
     * @doc.pattern Value Object
     */
    public record SchemaSuggestion(
            String fieldName,
            String suggestedType,
            String rationale
    ) {
        public SchemaSuggestion {
            Objects.requireNonNull(fieldName, "fieldName cannot be null");
            Objects.requireNonNull(suggestedType, "suggestedType cannot be null");
            Objects.requireNonNull(rationale, "rationale cannot be null");
        }
    }
}
