package com.ghatana.datacloud.analytics;

import java.util.List;
import java.util.Map;

/**
 * Result of an executed analytics query.
 *
 * @doc.type model
 * @doc.purpose Carries result data and metadata from query execution
 * @doc.layer core
 * @doc.pattern ValueObject
 */
public class QueryResult {
    private final String queryId;
    private final List<Map<String, Object>> rows;
    private final int rowCount;
    private final int columnCount;
    private final long executionTimeMs;
    private final String queryType;
    private final boolean optimized;
    private final int offset;
    private final int limit;
    private final int totalRows;

    private QueryResult(Builder builder) {
        this.queryId = builder.queryId;
        this.rows = builder.rows;
        this.rowCount = builder.rowCount;
        this.columnCount = builder.columnCount;
        this.executionTimeMs = builder.executionTimeMs;
        this.queryType = builder.queryType;
        this.optimized = builder.optimized;
        this.offset = builder.offset;
        this.limit = builder.limit;
        this.totalRows = builder.totalRows;
    }

    public String getQueryId() { return queryId; }
    public List<Map<String, Object>> getRows() { return rows; }
    public int getRowCount() { return rowCount; }
    public int getColumnCount() { return columnCount; }
    public long getExecutionTimeMs() { return executionTimeMs; }
    public String getQueryType() { return queryType; }
    public boolean isOptimized() { return optimized; }
    public int getOffset() { return offset; }
    public int getLimit() { return limit; }
    public int getTotalRows() { return totalRows; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String queryId;
        private List<Map<String, Object>> rows;
        private int rowCount;
        private int columnCount;
        private long executionTimeMs;
        private String queryType;
        private boolean optimized;
        private int offset = 0;
        private int limit = 1000;
        private int totalRows = 0;

        public Builder queryId(String queryId) { this.queryId = queryId; return this; }
        public Builder rows(List<Map<String, Object>> rows) { this.rows = rows; return this; }
        public Builder rowCount(int rowCount) { this.rowCount = rowCount; return this; }
        public Builder columnCount(int columnCount) { this.columnCount = columnCount; return this; }
        public Builder executionTimeMs(long time) { this.executionTimeMs = time; return this; }
        public Builder queryType(String type) { this.queryType = type; return this; }
        public Builder optimized(boolean optimized) { this.optimized = optimized; return this; }
        public Builder offset(int offset) { this.offset = offset; return this; }
        public Builder limit(int limit) { this.limit = limit; return this; }
        public Builder totalRows(int totalRows) { this.totalRows = totalRows; return this; }

        public QueryResult build() { return new QueryResult(this); }
    }
}
