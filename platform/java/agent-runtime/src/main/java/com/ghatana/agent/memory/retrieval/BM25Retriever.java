package com.ghatana.agent.memory.retrieval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.agent.memory.model.MemoryItem;
import com.ghatana.agent.memory.store.ScoredMemoryItem;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Sparse lexical retriever using PostgreSQL {@code tsvector}/{@code tsquery}.
 *
 * <p>Uses {@code ts_rank_cd} with {@code RANK_NORM_LOGLENGTH} (flag=32) to
 * produce BM25-like scores that approximate the IDF weighting standard in BM25.
 *
 * <p>Relies on the {@code text_search_vector} column and the GIN index created
 * by {@code V001__create_memory_items.sql}.
 *
 * <p>All JDBC operations are executed off the ActiveJ eventloop via
 * {@link Promise#ofBlocking}.
 *
 * @doc.type class
 * @doc.purpose BM25-style sparse lexical retrieval via PostgreSQL full-text search
 * @doc.layer agent-memory
 * @doc.pattern Repository, RetrievalPipeline
 * @doc.gaa.memory episodic
 */
public class BM25Retriever {

    private static final Logger log = LoggerFactory.getLogger(BM25Retriever.class);

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /**
     * PostgreSQL BM25-like FTS query.
     *
     * <ul>
     *   <li>{@code ts_rank_cd(..., 32)} — normalise by log(length) (flag 32 = RANK_NORM_LOGLENGTH)</li>
     *   <li>Tenant isolation enforced by {@code tenant_id = ?}</li>
     *   <li>Soft-deleted items excluded via {@code deleted_at IS NULL}</li>
     *   <li>Optional type filter applied when {@code type_filter} is non-null</li>
     * </ul>
     */
    private static final String SQL_BM25 = """
            SELECT content,
                   ts_rank_cd(text_search_vector, plainto_tsquery('english', ?), 32) AS rank
              FROM memory_items
             WHERE tenant_id = ?
               AND deleted_at IS NULL
               AND expires_at IS NULL OR expires_at > NOW()
               AND text_search_vector @@ plainto_tsquery('english', ?)
             ORDER BY rank DESC
             LIMIT ?
            """;

    private static final String SQL_BM25_TYPED = """
            SELECT content,
                   ts_rank_cd(text_search_vector, plainto_tsquery('english', ?), 32) AS rank
              FROM memory_items
             WHERE tenant_id = ?
               AND type = ANY(?)
               AND (deleted_at IS NULL)
               AND (expires_at IS NULL OR expires_at > NOW())
               AND text_search_vector @@ plainto_tsquery('english', ?)
             ORDER BY rank DESC
             LIMIT ?
            """;

    private final DataSource dataSource;
    private final ExecutorService executor;

    /**
     * Creates a BM25Retriever backed by the given JDBC DataSource.
     *
     * @param dataSource JDBC DataSource (e.g., HikariCP)
     */
    public BM25Retriever(@NotNull DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Performs sparse lexical search using PostgreSQL FTS.
     *
     * @param query     Full-text query string (natural language)
     * @param limit     Maximum number of results to return
     * @param tenantId  Tenant filter (mandatory, enforces isolation)
     * @return Scored memory items ordered by BM25 rank descending
     */
    @NotNull
    public Promise<List<ScoredMemoryItem>> search(
            @NotNull String query,
            int limit,
            @NotNull String tenantId) {
        return searchFiltered(query, limit, tenantId, null);
    }

    /**
     * Performs sparse lexical search with optional memory-item type filtering.
     *
     * @param query     Full-text query string
     * @param limit     Maximum results
     * @param tenantId  Tenant isolation filter
     * @param itemTypes Optional list of {@link com.ghatana.agent.memory.model.MemoryItemType} names to restrict search
     * @return Scored results ordered by BM25 rank descending
     */
    @NotNull
    public Promise<List<ScoredMemoryItem>> searchFiltered(
            @NotNull String query,
            int limit,
            @NotNull String tenantId,
            @Nullable List<String> itemTypes) {

        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(tenantId, "tenantId");

        if (query.isBlank()) {
            log.debug("BM25 search called with blank query — returning empty");
            return Promise.of(List.of());
        }

        int effectiveLimit = Math.max(1, Math.min(limit, 1000));

        return Promise.ofBlocking(executor, () -> {
            log.debug("BM25 search: query='{}', tenant='{}', limit={}, types={}",
                    query, tenantId, effectiveLimit, itemTypes);

            List<ScoredMemoryItem> results = new ArrayList<>();

            try (Connection conn = dataSource.getConnection()) {
                if (itemTypes == null || itemTypes.isEmpty()) {
                    // No type filter
                    try (PreparedStatement ps = conn.prepareStatement(SQL_BM25)) {
                        ps.setString(1, query);    // ts_rank_cd tsquery
                        ps.setString(2, tenantId); // tenant_id = ?
                        ps.setString(3, query);    // @@ tsquery
                        ps.setInt(4, effectiveLimit);
                        results = executeAndMap(ps);
                    }
                } else {
                    // Type-filtered
                    try (PreparedStatement ps = conn.prepareStatement(SQL_BM25_TYPED)) {
                        ps.setString(1, query);
                        ps.setString(2, tenantId);
                        Array typeArray = conn.createArrayOf("TEXT", itemTypes.toArray());
                        ps.setArray(3, typeArray);
                        ps.setString(4, query);
                        ps.setInt(5, effectiveLimit);
                        results = executeAndMap(ps);
                    }
                }
            }

            log.debug("BM25 search returned {} results for query='{}'", results.size(), query);
            return results;
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private List<ScoredMemoryItem> executeAndMap(PreparedStatement ps) throws Exception {
        List<ScoredMemoryItem> results = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String json = rs.getString("content");
                double rank = rs.getDouble("rank");
                try {
                    MemoryItem item = MAPPER.readValue(json, MemoryItem.class);
                    results.add(new ScoredMemoryItem(
                            item,
                            normaliseRank(rank),
                            Map.of("bm25_raw", String.valueOf(rank), "retriever", "BM25")));
                } catch (Exception e) {
                    log.warn("Failed to deserialise memory item from JSON: {}", e.getMessage());
                }
            }
        }
        return results;
    }

    /**
     * Normalises the raw ts_rank_cd score (0..∞) to a [0, 1] range using
     * a simple sigmoid-like normalisation: {@code score / (1 + score)}.
     * This preserves ordering while bounding the output.
     */
    private static double normaliseRank(double tsRank) {
        if (tsRank <= 0.0) return 0.0;
        return tsRank / (1.0 + tsRank);
    }
}

