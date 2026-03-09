package com.ghatana.virtualorg.framework.memory;

import java.time.Instant;
import java.util.Set;

/**
 * Query object for searching memories.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides flexible filtering options for querying agent memory.
 *
 * @doc.type record
 * @doc.purpose Query parameters for memory search
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record MemoryQuery(
        String agentId,
        Set<MemoryType> types,
        String textQuery,
        Instant startTime,
        Instant endTime,
        double minImportance,
        String sessionId,
        String taskId,
        boolean includeEmbeddings,
        int limit,
        int offset,
        SortBy sortBy,
        SortOrder sortOrder
        ) {

    public enum SortBy {
        CREATED_AT,
        ACCESSED_AT,
        IMPORTANCE,
        RELEVANCE
    }

    public enum SortOrder {
        ASCENDING,
        DESCENDING
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String agentId;
        private Set<MemoryType> types;
        private String textQuery;
        private Instant startTime;
        private Instant endTime;
        private double minImportance = 0.0;
        private String sessionId;
        private String taskId;
        private boolean includeEmbeddings = false;
        private int limit = 50;
        private int offset = 0;
        private SortBy sortBy = SortBy.CREATED_AT;
        private SortOrder sortOrder = SortOrder.DESCENDING;

        private Builder() {
        }

        public Builder agentId(String agentId) {
            this.agentId = agentId;
            return this;
        }

        public Builder types(Set<MemoryType> types) {
            this.types = types;
            return this;
        }

        public Builder type(MemoryType type) {
            this.types = Set.of(type);
            return this;
        }

        public Builder textQuery(String textQuery) {
            this.textQuery = textQuery;
            return this;
        }

        public Builder startTime(Instant startTime) {
            this.startTime = startTime;
            return this;
        }

        public Builder endTime(Instant endTime) {
            this.endTime = endTime;
            return this;
        }

        public Builder timeRange(Instant start, Instant end) {
            this.startTime = start;
            this.endTime = end;
            return this;
        }

        public Builder minImportance(double minImportance) {
            this.minImportance = minImportance;
            return this;
        }

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder taskId(String taskId) {
            this.taskId = taskId;
            return this;
        }

        public Builder includeEmbeddings(boolean include) {
            this.includeEmbeddings = include;
            return this;
        }

        public Builder limit(int limit) {
            this.limit = limit;
            return this;
        }

        public Builder offset(int offset) {
            this.offset = offset;
            return this;
        }

        public Builder sortBy(SortBy sortBy) {
            this.sortBy = sortBy;
            return this;
        }

        public Builder sortOrder(SortOrder sortOrder) {
            this.sortOrder = sortOrder;
            return this;
        }

        public MemoryQuery build() {
            return new MemoryQuery(
                    agentId,
                    types,
                    textQuery,
                    startTime,
                    endTime,
                    minImportance,
                    sessionId,
                    taskId,
                    includeEmbeddings,
                    limit,
                    offset,
                    sortBy,
                    sortOrder
            );
        }
    }
}
