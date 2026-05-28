package com.ghatana.datacloud.analytics;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Result of an executed analytics query with quality indicators.
 *
 * <p><b>Purpose</b><br>
 * Carries result data, metadata, and quality indicators from query execution.
 * Includes degraded result explanation, policy scope enforcement, and freshness tracking.
 *
 * <p><b>Quality Indicators</b><br>
 * <ul>
 *   <li><b>Degraded Result</b>: Query returned partial or degraded results due to constraints</li>
 *   <li><b>Policy Scope</b>: Results filtered by governance policies</li>
 *   <li><b>Freshness</b>: Data staleness information for time-sensitive queries</li>
 * </ul>
 *
 * @doc.type model
 * @doc.purpose Carries result data and quality metadata from query execution
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
    
    // Group 4.3: Query quality indicators
    private final boolean degraded;
    private final String degradationReason;
    private final List<String> degradationDetails;
    private final boolean policyFiltered;
    private final String policyScope;
    private final List<String> appliedPolicies;
    private final Instant dataFreshnessTimestamp;
    private final long dataAgeSeconds;
    private final String freshnessStatus;

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
        this.degraded = builder.degraded;
        this.degradationReason = builder.degradationReason;
        this.degradationDetails = builder.degradationDetails;
        this.policyFiltered = builder.policyFiltered;
        this.policyScope = builder.policyScope;
        this.appliedPolicies = builder.appliedPolicies;
        this.dataFreshnessTimestamp = builder.dataFreshnessTimestamp;
        this.dataAgeSeconds = builder.dataAgeSeconds;
        this.freshnessStatus = builder.freshnessStatus;
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
    
    // Group 4.3: Degraded result indicators
    public boolean isDegraded() { return degraded; }
    public String getDegradationReason() { return degradationReason; }
    public List<String> getDegradationDetails() { return degradationDetails; }
    
    // Group 4.3: Policy scope enforcement
    public boolean isPolicyFiltered() { return policyFiltered; }
    public String getPolicyScope() { return policyScope; }
    public List<String> getAppliedPolicies() { return appliedPolicies; }
    
    // Group 4.3: Freshness tracking
    public Instant getDataFreshnessTimestamp() { return dataFreshnessTimestamp; }
    public long getDataAgeSeconds() { return dataAgeSeconds; }
    public String getFreshnessStatus() { return freshnessStatus; }

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
        
        // Group 4.3: Quality indicator fields
        private boolean degraded = false;
        private String degradationReason;
        private List<String> degradationDetails = List.of();
        private boolean policyFiltered = false;
        private String policyScope;
        private List<String> appliedPolicies = List.of();
        private Instant dataFreshnessTimestamp;
        private long dataAgeSeconds = 0;
        private String freshnessStatus = "unknown";

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
        
        // Group 4.3: Quality indicator builders
        public Builder degraded(boolean degraded) { this.degraded = degraded; return this; }
        public Builder degradationReason(String reason) { this.degradationReason = reason; return this; }
        public Builder degradationDetails(List<String> details) { this.degradationDetails = details; return this; }
        public Builder policyFiltered(boolean filtered) { this.policyFiltered = filtered; return this; }
        public Builder policyScope(String scope) { this.policyScope = scope; return this; }
        public Builder appliedPolicies(List<String> policies) { this.appliedPolicies = policies; return this; }
        public Builder dataFreshnessTimestamp(Instant timestamp) { this.dataFreshnessTimestamp = timestamp; return this; }
        public Builder dataAgeSeconds(long ageSeconds) { this.dataAgeSeconds = ageSeconds; return this; }
        public Builder freshnessStatus(String status) { this.freshnessStatus = status; return this; }

        public QueryResult build() { return new QueryResult(this); }
    }
}
