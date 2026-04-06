package com.ghatana.kernel.adapter.datacloud;

import java.util.List;

/**
 * Result from querying data from DataCloud storage.
 *
 * @doc.type class
 * @doc.purpose DataCloud query result
 * @doc.layer kernel
 * @doc.pattern ValueObject
 */
public class QueryResult {
    private final List<DataResult> results;
    private final int totalCount;
    private final boolean hasMore;

    public QueryResult(List<DataResult> results, int totalCount, boolean hasMore) {
        this.results = results != null ? results : List.of();
        this.totalCount = totalCount;
        this.hasMore = hasMore;
    }

    public List<DataResult> getResults() { return results; }
    public int getTotalCount() { return totalCount; }
    public boolean hasMore() { return hasMore; }
}
