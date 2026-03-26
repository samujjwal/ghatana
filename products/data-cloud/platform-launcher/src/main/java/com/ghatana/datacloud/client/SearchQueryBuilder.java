package com.ghatana.datacloud.client;

import java.util.HashMap;
import java.util.Map;

/**
 * Builder implementation for SearchQuery.
 *
 * <p>Implements the builder pattern for constructing search queries
 * with fluent API support.</p>
 *
 * @doc.type class
 * @doc.purpose SearchQuery builder implementation
 * @doc.layer core
 * @doc.pattern Builder
 */
public class SearchQueryBuilder implements DataCloudClient.SearchQuery.Builder {

    private String query;
    private String collectionName;
    private int limit = 10;
    private int offset = 0;
    private final Map<String, String> filters = new HashMap<>();

    @Override
    public SearchQueryBuilder query(String query) {
        this.query = query;
        return this;
    }

    @Override
    public SearchQueryBuilder collectionName(String name) {
        this.collectionName = name;
        return this;
    }

    @Override
    public SearchQueryBuilder limit(int limit) {
        this.limit = limit;
        return this;
    }

    @Override
    public SearchQueryBuilder offset(int offset) {
        this.offset = offset;
        return this;
    }

    @Override
    public SearchQueryBuilder filter(String key, String value) {
        this.filters.put(key, value);
        return this;
    }

    @Override
    public DataCloudClient.SearchQuery build() {
        return new SearchQueryImpl(this);
    }

    /**
     * Internal implementation of SearchQuery.
     */
    private static class SearchQueryImpl implements DataCloudClient.SearchQuery {
        private final String query;
        private final String collectionName;
        private final int limit;
        private final int offset;
        private final Map<String, String> filters;

        SearchQueryImpl(SearchQueryBuilder builder) {
            this.query = builder.query;
            this.collectionName = builder.collectionName;
            this.limit = builder.limit;
            this.offset = builder.offset;
            this.filters = new HashMap<>(builder.filters);
        }

        @Override
        public String getQuery() {
            return query;
        }

        @Override
        public String getCollectionName() {
            return collectionName;
        }

        @Override
        public int getLimit() {
            return limit;
        }

        @Override
        public int getOffset() {
            return offset;
        }

        @Override
        public Map<String, String> getFilters() {
            return new HashMap<>(filters);
        }
    }
}

