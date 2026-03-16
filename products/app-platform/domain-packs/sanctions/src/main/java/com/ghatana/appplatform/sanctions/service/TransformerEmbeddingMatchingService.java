package com.ghatana.appplatform.sanctions.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * @doc.type      Service
 * @doc.purpose   Multilingual entity match using transformer-based sentence embeddings stored
 *                in PostgreSQL pgvector. Performs Approximate Nearest Neighbour (ANN) search
 *                against the sanctions list embedding index. Complements lexical matching with
 *                semantic similarity to catch transliteration variations and aliases.
 * @doc.layer     Application
 * @doc.pattern   Hybrid retrieval: pgvector ANN + threshold filtering; K-09 model tier
 *
 * Story: D14-015
 */
public class TransformerEmbeddingMatchingService {

    private static final Logger log = LoggerFactory.getLogger(TransformerEmbeddingMatchingService.class);

    /** Cosine similarity threshold — matches below this are discarded. */
    private static final double SIMILARITY_THRESHOLD = 0.82;

    private final EmbeddingModelPort    embeddingModel;
    private final VectorSearchPort      vectorSearch;
    private final Consumer<Object>      eventPublisher;
    private final Counter               matchesFound;
    private final Counter               queriesRan;
    private final Timer                 queryLatency;

    public TransformerEmbeddingMatchingService(EmbeddingModelPort embeddingModel,
                                                VectorSearchPort vectorSearch,
                                                Consumer<Object> eventPublisher,
                                                MeterRegistry meterRegistry) {
        this.embeddingModel = embeddingModel;
        this.vectorSearch   = vectorSearch;
        this.eventPublisher = eventPublisher;
        this.matchesFound   = meterRegistry.counter("sanctions.embedding.matches_found");
        this.queriesRan     = meterRegistry.counter("sanctions.embedding.queries");
        this.queryLatency   = meterRegistry.timer("sanctions.embedding.query_latency");
    }

    /**
     * Screens a query name against the vector-indexed sanctions list.
     *
     * @param clientId     client being screened (for event emission)
     * @param queryName    name to screen (may be in any language/script)
     * @param topK         number of nearest neighbours to retrieve before filtering
     */
    public EmbeddingScreenResult screen(String clientId, String queryName, int topK) {
        queriesRan.increment();
        return queryLatency.record(() -> {
            float[] queryEmbedding = embeddingModel.embed(queryName);
            List<VectorMatch> candidates = vectorSearch.findNearest(queryEmbedding, topK);

            List<EmbeddingMatch> accepted = candidates.stream()
                    .filter(vm -> vm.similarity() >= SIMILARITY_THRESHOLD)
                    .map(vm -> new EmbeddingMatch(vm.entityRef(), vm.name(),
                            vm.similarity(), vm.listId()))
                    .toList();

            if (!accepted.isEmpty()) {
                matchesFound.increment(accepted.size());
                log.info("EmbeddingMatch found: clientId={} queryName={} hits={}",
                        clientId, queryName, accepted.size());
                eventPublisher.accept(new EmbeddingMatchFoundEvent(clientId, queryName, accepted));
            }
            return new EmbeddingScreenResult(clientId, queryName, accepted, Instant.now());
        });
    }

    /**
     * Indexes a sanctions list entry into the vector store. Called during list ingestion.
     *
     * @param entityRef   unique entry reference
     * @param names       all names/aliases for this entity
     * @param listId      source list identifier
     */
    public void indexEntry(String entityRef, List<String> names, String listId) {
        for (String name : names) {
            float[] embedding = embeddingModel.embed(name);
            vectorSearch.upsert(entityRef, name, embedding, listId);
        }
        log.debug("Indexed entry entityRef={} aliases={}", entityRef, names.size());
    }

    /**
     * Removes all vector entries for a sanctioned entity. Called when an entry is removed
     * from a list.
     */
    public void removeEntry(String entityRef) {
        vectorSearch.delete(entityRef);
        log.debug("Removed vector entry entityRef={}", entityRef);
    }

    // ─── Ports ────────────────────────────────────────────────────────────────

    public interface EmbeddingModelPort {
        /** Embeds a name string into a fixed-dimension float vector. */
        float[] embed(String text);
    }

    public interface VectorSearchPort {
        /** Finds the {@code k} nearest vectors to the query in the index. */
        List<VectorMatch> findNearest(float[] query, int k);
        /** Inserts or updates a vector entry. */
        void upsert(String entityRef, String name, float[] embedding, String listId);
        void delete(String entityRef);
    }

    // ─── Domain records ───────────────────────────────────────────────────────

    public record VectorMatch(String entityRef, String name, double similarity, String listId) {}

    public record EmbeddingMatch(String entityRef, String name, double similarity, String listId) {}

    public record EmbeddingScreenResult(String clientId, String queryName,
                                         List<EmbeddingMatch> matches, Instant screenedAt) {
        public boolean hasMatches() { return !matches.isEmpty(); }
    }

    // ─── Events ───────────────────────────────────────────────────────────────

    public record EmbeddingMatchFoundEvent(String clientId, String queryName,
                                           List<EmbeddingMatch> matches) {}
}
