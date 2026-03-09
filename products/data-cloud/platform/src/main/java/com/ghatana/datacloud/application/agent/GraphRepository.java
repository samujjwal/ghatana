package com.ghatana.datacloud.application.agent;

import io.activej.promise.Promise;
import java.util.List;
import java.util.UUID;

/**
 * Repository for knowledge graph operations.
 *
 * <p><b>Purpose</b><br>
 * Abstraction for storing and querying enriched collections in the knowledge graph.
 * Uses ActiveJ Promise for async operations following the platform's concurrency model.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * GraphRepository repo = ...;
 * Promise<EnrichedCollection> saved = repo.saveEnrichedCollection(collection);
 * Promise<List<EnrichedCollection>> similar = repo.findSimilarCollections(id, 0.8);
 * }</pre>
 *
 * @doc.type interface
 * @doc.purpose Knowledge graph data access abstraction
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface GraphRepository {

    /**
     * Stores an enriched collection in the knowledge graph.
     *
     * @param collection the enriched collection to store
     * @return Promise that completes with stored collection
     */
    Promise<KnowledgeGraphAgent.EnrichedCollection> saveEnrichedCollection(
            KnowledgeGraphAgent.EnrichedCollection collection
    );

    /**
     * Finds similar collections in the graph.
     *
     * <p>Uses vector similarity search to find collections semantically similar
     * to the input collection based on the provided threshold.
     *
     * @param collectionId the collection ID
     * @param threshold similarity threshold (0.0-1.0)
     * @return Promise of list of similar collections
     */
    Promise<List<KnowledgeGraphAgent.EnrichedCollection>> findSimilarCollections(
            UUID collectionId,
            double threshold
    );
}
