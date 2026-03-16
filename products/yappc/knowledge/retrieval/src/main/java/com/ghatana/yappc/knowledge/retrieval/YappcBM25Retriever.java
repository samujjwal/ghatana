/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Knowledge Module
 */
package com.ghatana.yappc.knowledge.retrieval;

import com.ghatana.agent.memory.retrieval.RetrievalPipeline;
import com.ghatana.platform.domain.domain.memory.MemoryItem;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * JDBC-backed BM25 lexical retriever for semantic search.
 *
 * <p><b>Purpose:</b> Sparse term-based retrieval using PostgreSQL full-text search (FTS).
 *
 * <p><b>Algorithm:</b> Uses PostgreSQL's tsvector/tsquery with BM25-like scoring via ts_rank_cd().
 * Complements DenseVectorRetriever for hybrid search.
 *
 * <p><b>SQL Pattern:</b>
 * <pre>{@code
 * SELECT *, ts_rank_cd(text_search_vector, plainto_tsquery(?), 32) as rank
 * FROM memory_items
 * WHERE tenant_id = ? AND text_search_vector @@ plainto_tsquery(?)
 * ORDER BY rank DESC
 * LIMIT ?
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose JDBC-backed BM25 lexical retriever for full-text search
 * @doc.layer product
 * @doc.pattern Strategy
 *
 * @since 2.4.0
 */
public class YappcBM25Retriever implements RetrievalPipeline {

    private static final Logger logger = LoggerFactory.getLogger(YappcBM25Retriever.class);

    private static final String SEARCH_SQL = """
            SELECT 
                id, tenant_id, episodic_id, memory_type, content,
                ts_rank_cd(text_search_vector, plainto_tsquery(?), 32) as rank,
                created_at, updated_at
            FROM yappc.memory_items
            WHERE tenant_id = ? 
              AND text_search_vector @@ plainto_tsquery(?)
            ORDER BY rank DESC
            LIMIT ?
            """;

    private final DataSource dataSource;
    private final Executor executor;

    /**
     * Creates a new BM25 retriever.
     *
     * @param dataSource connection pool
     * @param executor   thread pool for blocking JDBC
     */
    public YappcBM25Retriever(DataSource dataSource, Executor executor) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    @Override
    public Promise<RetrievalResult> retrieve(RetrievalRequest request) {
        return Promise.ofBlocking(executor, () -> {
            String query = request.getQuery();
            String tenantId = request.getTenantId();
            int limit = request.getLimit();

            logger.info("[BM25] Retrieving: tenant={} query='{}' limit={}", 
                    tenantId, query, limit);

            List<ScoredMemoryItem> results = new ArrayList<>();

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SEARCH_SQL)) {

                ps.setString(1, query);
                ps.setString(2, tenantId);
                ps.setString(3, query);
                ps.setInt(4, limit);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        results.add(new ScoredMemoryItem(
                                rs.getString("id"),
                                rs.getString("content"),
                                rs.getDouble("rank"),
                                rs.getString("memory_type"),
                                rs.getLong("created_at")
                        ));
                    }
                }

                logger.info("[BM25] Retrieved {} items for query '{}'", results.size(), query);

            } catch (SQLException e) {
                logger.error("[BM25] Search failed: {}", e.getMessage(), e);
                throw new RuntimeException("BM25 retrieval failed", e);
            }

            return new RetrievalResult(results, "BM25", System.currentTimeMillis());
        });
    }

    /**
     * Scored memory item result.
     */
    public record ScoredMemoryItem(
            String id,
            String content,
            double score,
            String memoryType,
            long retrievedAt
    ) {}

    /**
     * Retrieval result with metadata.
     */
    public record RetrievalResult(
            List<ScoredMemoryItem> items,
            String retrievalMethod,
            long timestamp
    ) {
        public int getCount() {
            return items.size();
        }
    }
}
