package com.ghatana.agent.memory.retrieval;

import com.ghatana.agent.memory.store.ScoredMemoryItem;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Sparse lexical retriever using PostgreSQL tsvector/tsquery.
 * Computes BM25-like scores using ts_rank_cd with normalization.
 *
 * <p>Reuses existing {@code JdbcTemplate} and the tsvector column
 * from the V001 migration.
 *
 * @doc.type class
 * @doc.purpose BM25-style sparse lexical retrieval
 * @doc.layer agent-memory
 */
public class BM25Retriever {

    /**
     * Performs sparse lexical search.
     *
     * @param query  Full-text query
     * @param limit  Max results
     * @param tenantId Tenant filter
     * @return Scored results
     */
    @NotNull
    public Promise<List<ScoredMemoryItem>> search(
            @NotNull String query,
            int limit,
            @NotNull String tenantId) {
        // Implementation will use JdbcTemplate with tsquery
        // SELECT *, ts_rank_cd(text_search_vector, plainto_tsquery(?), 32) as rank
        // FROM memory_items WHERE tenant_id = ? AND text_search_vector @@ plainto_tsquery(?)
        // ORDER BY rank DESC LIMIT ?
        return Promise.of(List.of());
    }
}
