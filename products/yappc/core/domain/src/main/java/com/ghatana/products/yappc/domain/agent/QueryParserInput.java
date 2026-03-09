package com.ghatana.products.yappc.domain.agent;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Input for the Query Parser Agent.
 *
 * @doc.type record
 * @doc.purpose Query parser agent input
 * @doc.layer product
 * @doc.pattern Value Object
 */
public record QueryParserInput(
        @NotNull String query,
        @Nullable String currentRoute,
        @Nullable String persona,
        @Nullable List<String> visibleItems,
        @Nullable List<String> recentQueries
) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String query;
        private String currentRoute;
        private String persona;
        private List<String> visibleItems;
        private List<String> recentQueries;

        public Builder query(String query) {
            this.query = query;
            return this;
        }

        public Builder currentRoute(String currentRoute) {
            this.currentRoute = currentRoute;
            return this;
        }

        public Builder persona(String persona) {
            this.persona = persona;
            return this;
        }

        public Builder visibleItems(List<String> visibleItems) {
            this.visibleItems = visibleItems;
            return this;
        }

        public Builder recentQueries(List<String> recentQueries) {
            this.recentQueries = recentQueries;
            return this;
        }

        public QueryParserInput build() {
            return new QueryParserInput(query, currentRoute, persona, visibleItems, recentQueries);
        }
    }
}
