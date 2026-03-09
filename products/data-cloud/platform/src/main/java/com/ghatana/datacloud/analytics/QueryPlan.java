package com.ghatana.datacloud.analytics;

import java.util.List;

/**
 * Execution plan for an analytics query.
 *
 * @doc.type model
 * @doc.purpose Describes how an analytics query will be executed
 * @doc.layer core
 */
public class QueryPlan {
    private final String queryId;
    private final QueryType queryType;
    private final List<String> dataSources;
    private final double estimatedCost;
    private final boolean optimized;

    private QueryPlan(Builder builder) {
        this.queryId = builder.queryId;
        this.queryType = builder.queryType;
        this.dataSources = builder.dataSources;
        this.estimatedCost = builder.estimatedCost;
        this.optimized = builder.optimized;
    }

    public String getQueryId() { return queryId; }
    public QueryType getQueryType() { return queryType; }
    public List<String> getDataSources() { return dataSources; }
    public double getEstimatedCost() { return estimatedCost; }
    public boolean isOptimized() { return optimized; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String queryId;
        private QueryType queryType;
        private List<String> dataSources;
        private double estimatedCost;
        private boolean optimized;

        public Builder queryId(String queryId) { this.queryId = queryId; return this; }
        public Builder queryType(QueryType queryType) { this.queryType = queryType; return this; }
        public Builder dataSources(List<String> dataSources) { this.dataSources = dataSources; return this; }
        public Builder estimatedCost(double cost) { this.estimatedCost = cost; return this; }
        public Builder optimized(boolean optimized) { this.optimized = optimized; return this; }

        public QueryPlan build() { return new QueryPlan(this); }
    }
}
