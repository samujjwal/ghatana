package com.ghatana.appplatform.refdata.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * @doc.type      Service
 * @doc.purpose   ML-powered entity deduplication and resolution using BERT-class embeddings.
 *                Implements a two-phase approach: (1) blocking — candidate pair generation using
 *                phonetic/prefix keys to avoid O(n²) comparison, (2) scoring — BERT similarity
 *                per candidate pair, accepting pairs above the linkage threshold.
 *                Results are persisted in the entity_links table for downstream use.
 * @doc.layer     Application
 * @doc.pattern   K-09 supervised linkage; blocking + scoring pipeline
 *
 * Story: D11-012
 */
public class MlEntityResolutionService {

    private static final Logger log = LoggerFactory.getLogger(MlEntityResolutionService.class);

    /** Cosine similarity threshold for accepting a link. */
    private static final double LINK_THRESHOLD = 0.88;
    /** Maximum candidate pairs per blocking key (controls memory). */
    private static final int MAX_PAIRS_PER_KEY = 500;

    private final EmbeddingModelPort embeddingModel;
    private final DataSource         dataSource;
    private final Consumer<Object>   eventPublisher;
    private final Counter            entitiesLinked;
    private final Counter            candidatePairsEvaluated;
    private final Timer              resolutionTimer;

    public MlEntityResolutionService(EmbeddingModelPort embeddingModel,
                                      DataSource dataSource,
                                      Consumer<Object> eventPublisher,
                                      MeterRegistry meterRegistry) {
        this.embeddingModel          = embeddingModel;
        this.dataSource              = dataSource;
        this.eventPublisher          = eventPublisher;
        this.entitiesLinked          = meterRegistry.counter("refdata.entity_resolution.linked");
        this.candidatePairsEvaluated = meterRegistry.counter("refdata.entity_resolution.pairs_evaluated");
        this.resolutionTimer         = meterRegistry.timer("refdata.entity_resolution.batch_duration");
    }

    /**
     * Resolves a new or updated entity record against the existing entity population.
     * Returns the cluster representative if a link is found, or the entity is registered as
     * a new cluster root otherwise.
     *
     * @param entityId    identifier of the new/updated entity
     * @param name        canonical name for embedding
     * @param blockingKey phonetic/normalised blocking key (e.g. soundex, NYSIIS)
     */
    public ResolutionResult resolve(String entityId, String name, String blockingKey) {
        return resolutionTimer.record(() -> {
            float[] queryEmbedding = embeddingModel.embed(name);

            // Phase 1: blocking — fetch candidate entities sharing the blocking key
            List<EntityCandidate> candidates = loadCandidates(blockingKey, entityId, MAX_PAIRS_PER_KEY);
            candidatePairsEvaluated.increment(candidates.size());

            // Phase 2: scoring — embed each candidate and compute cosine similarity
            Optional<EntityCandidate> bestMatch = candidates.stream()
                    .map(c -> new ScoredCandidate(c, cosineSimilarity(queryEmbedding, c.embedding())))
                    .filter(sc -> sc.similarity() >= LINK_THRESHOLD)
                    .max(java.util.Comparator.comparingDouble(ScoredCandidate::similarity))
                    .map(ScoredCandidate::candidate);

            if (bestMatch.isPresent()) {
                EntityCandidate match = bestMatch.get();
                saveLink(entityId, match.entityId(), match.name());
                entitiesLinked.increment();
                log.info("EntityResolution LINKED entityId={} to clusterId={}", entityId, match.entityId());
                eventPublisher.accept(new EntityLinkedEvent(entityId, match.entityId(), name, match.name()));
                return ResolutionResult.linked(entityId, match.entityId());
            } else {
                // No match — register as new cluster root
                upsertEmbedding(entityId, name, blockingKey, queryEmbedding);
                log.debug("EntityResolution NEW_CLUSTER entityId={}", entityId);
                return ResolutionResult.newCluster(entityId);
            }
        });
    }

    /**
     * Records manual override of a linkage decision (feedback for model retraining).
     *
     * @param entityId     entity whose resolution is being corrected
     * @param correctLink  correct cluster representative (null = unlink, entity is its own root)
     * @param reviewerId   compliance officer providing the correction
     */
    public void recordOverride(String entityId, String correctLink, String reviewerId) {
        String sql = "INSERT INTO entity_link_overrides(entity_id, correct_link, reviewer_id, overridden_at) "
                   + "VALUES(?,?,?,?) ON CONFLICT(entity_id) DO UPDATE "
                   + "SET correct_link=EXCLUDED.correct_link, reviewer_id=EXCLUDED.reviewer_id, "
                   + "overridden_at=EXCLUDED.overridden_at";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, entityId);
            ps.setString(2, correctLink);
            ps.setString(3, reviewerId);
            ps.setTimestamp(4, Timestamp.from(Instant.now()));
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("recordOverride DB error entityId={}", entityId, e);
        }
        eventPublisher.accept(new EntityLinkOverriddenEvent(entityId, correctLink, reviewerId));
        log.info("EntityResolution OVERRIDE entityId={} correctLink={}", entityId, correctLink);
    }

    // ─── private helpers ──────────────────────────────────────────────────────

    private List<EntityCandidate> loadCandidates(String blockingKey, String excludeId, int limit) {
        String sql = "SELECT entity_id, entity_name, embedding FROM entity_embeddings "
                   + "WHERE blocking_key=? AND entity_id <> ? LIMIT ?";
        List<EntityCandidate> result = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, blockingKey);
            ps.setString(2, excludeId);
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    byte[] bytes = rs.getBytes("embedding");
                    result.add(new EntityCandidate(rs.getString("entity_id"),
                            rs.getString("entity_name"), bytesToFloats(bytes)));
                }
            }
        } catch (SQLException e) {
            log.error("loadCandidates error blockingKey={}", blockingKey, e);
        }
        return result;
    }

    private void saveLink(String entityId, String clusterId, String clusterName) {
        String sql = "INSERT INTO entity_links(entity_id, cluster_id, cluster_name, linked_at) "
                   + "VALUES(?,?,?,?) ON CONFLICT(entity_id) DO UPDATE "
                   + "SET cluster_id=EXCLUDED.cluster_id, cluster_name=EXCLUDED.cluster_name, "
                   + "linked_at=EXCLUDED.linked_at";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, entityId);
            ps.setString(2, clusterId);
            ps.setString(3, clusterName);
            ps.setTimestamp(4, Timestamp.from(Instant.now()));
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("saveLink DB error entityId={}", entityId, e);
        }
    }

    private void upsertEmbedding(String entityId, String name, String blockingKey, float[] embedding) {
        String sql = "INSERT INTO entity_embeddings(entity_id, entity_name, blocking_key, embedding, updated_at) "
                   + "VALUES(?,?,?,?,?) ON CONFLICT(entity_id) DO UPDATE "
                   + "SET entity_name=EXCLUDED.entity_name, embedding=EXCLUDED.embedding, "
                   + "updated_at=EXCLUDED.updated_at";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, entityId);
            ps.setString(2, name);
            ps.setString(3, blockingKey);
            ps.setBytes(4, floatsToBytes(embedding));
            ps.setTimestamp(5, Timestamp.from(Instant.now()));
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("upsertEmbedding DB error entityId={}", entityId, e);
        }
    }

    private double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) return 0.0;
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i]; }
        return (na == 0 || nb == 0) ? 0.0 : dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private byte[] floatsToBytes(float[] floats) {
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.allocate(floats.length * 4);
        for (float f : floats) buf.putFloat(f);
        return buf.array();
    }

    private float[] bytesToFloats(byte[] bytes) {
        java.nio.FloatBuffer fb = java.nio.ByteBuffer.wrap(bytes).asFloatBuffer();
        float[] result = new float[fb.limit()];
        fb.get(result);
        return result;
    }

    // ─── Port ─────────────────────────────────────────────────────────────────

    public interface EmbeddingModelPort {
        float[] embed(String text);
    }

    // ─── Domain records ───────────────────────────────────────────────────────

    private record EntityCandidate(String entityId, String name, float[] embedding) {}
    private record ScoredCandidate(EntityCandidate candidate, double similarity) {}

    public record ResolutionResult(String entityId, String clusterId, boolean isNewCluster) {
        public static ResolutionResult linked(String entityId, String clusterId) {
            return new ResolutionResult(entityId, clusterId, false);
        }
        public static ResolutionResult newCluster(String entityId) {
            return new ResolutionResult(entityId, entityId, true);
        }
    }

    // ─── Events ───────────────────────────────────────────────────────────────

    public record EntityLinkedEvent(String entityId, String clusterId,
                                     String entityName, String clusterName) {}
    public record EntityLinkOverriddenEvent(String entityId, String correctLink, String reviewerId) {}
}
