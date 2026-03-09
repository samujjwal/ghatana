package com.ghatana.core.event.query;

import java.util.Set;

/**
 * Describes the query capabilities of the underlying event store.
 */
public class StoreCapabilities {
    private final boolean supportsStreaming;
    private final boolean supportsIndexing;
    private final Set<String> supportedQueryOperators;
    private final boolean supportsTimeBasedQueries;
    private final boolean supportsEventTypeFiltering;
    private final boolean supportsStreamFiltering;

    private StoreCapabilities(Builder builder) {
        this.supportsStreaming = builder.supportsStreaming;
        this.supportsIndexing = builder.supportsIndexing;
        this.supportedQueryOperators = builder.supportedQueryOperators;
        this.supportsTimeBasedQueries = builder.supportsTimeBasedQueries;
        this.supportsEventTypeFiltering = builder.supportsEventTypeFiltering;
        this.supportsStreamFiltering = builder.supportsStreamFiltering;
    }

    public boolean supportsStreaming() {
        return supportsStreaming;
    }

    public boolean supportsIndexing() {
        return supportsIndexing;
    }

    public Set<String> getSupportedQueryOperators() {
        return supportedQueryOperators;
    }

    public boolean supportsTimeBasedQueries() {
        return supportsTimeBasedQueries;
    }

    public boolean supportsEventTypeFiltering() {
        return supportsEventTypeFiltering;
    }

    public boolean supportsStreamFiltering() {
        return supportsStreamFiltering;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private boolean supportsStreaming = true;
        private boolean supportsIndexing = false;
        private Set<String> supportedQueryOperators = Set.of("=", "!=", "<", "<=", ">", ">=", "in");
        private boolean supportsTimeBasedQueries = true;
        private boolean supportsEventTypeFiltering = true;
        private boolean supportsStreamFiltering = true;

        public Builder withStreaming(boolean supportsStreaming) {
            this.supportsStreaming = supportsStreaming;
            return this;
        }

        public Builder withIndexing(boolean supportsIndexing) {
            this.supportsIndexing = supportsIndexing;
            return this;
        }

        public Builder withSupportedQueryOperators(Set<String> supportedQueryOperators) {
            this.supportedQueryOperators = supportedQueryOperators;
            return this;
        }

        public Builder withTimeBasedQueries(boolean supportsTimeBasedQueries) {
            this.supportsTimeBasedQueries = supportsTimeBasedQueries;
            return this;
        }

        public Builder withEventTypeFiltering(boolean supportsEventTypeFiltering) {
            this.supportsEventTypeFiltering = supportsEventTypeFiltering;
            return this;
        }

        public Builder withStreamFiltering(boolean supportsStreamFiltering) {
            this.supportsStreamFiltering = supportsStreamFiltering;
            return this;
        }

        public StoreCapabilities build() {
            return new StoreCapabilities(this);
        }
    }
}
