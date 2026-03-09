package com.ghatana.datacloud.entity.search;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Search query value object with filtering and faceting options.
 *
 * <p><b>Purpose</b><br>
 * Immutable representation of search query parameters including text query,
 * filters, facets, sorting, and pagination. Supports both full-text and
 * semantic search scenarios.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * SearchQuery query = SearchQuery.builder()
 *     .tenantId("tenant-123")
 *     .queryText("smartphone android")
 *     .filters(Map.of(
 *         "category", "electronics",
 *         "price_min", 100.0,
 *         "price_max", 500.0
 *     ))
 *     .facets(List.of("category", "brand", "price_range"))
 *     .sortBy("relevance") // or "price", "date", etc.
 *     .offset(0)
 *     .limit(20)
 *     .build();
 * }</pre>
 *
 * <p><b>Field Requirements</b><br>
 * - tenantId: Non-blank, for tenant isolation
 * - queryText: Optional text query for full-text search
 * - filters: Optional metadata filters (field → value)
 * - facets: Optional fields to aggregate counts on
 * - sortBy: Optional sort field (default: relevance)
 * - offset: Non-negative pagination offset (default: 0)
 * - limit: Positive max results (default: 10, max: 100)
 *
 * <p><b>Thread Safety</b><br>
 * Immutable - All fields are private final, collections are unmodifiable.
 *
 * @doc.type record
 * @doc.purpose Immutable search query value object
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public final class SearchQuery {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 100;

    private final String tenantId;
    private final String queryText;
    private final Map<String, Object> filters;
    private final List<String> facets;
    private final String sortBy;
    private final int offset;
    private final int limit;

    private SearchQuery(
            String tenantId,
            String queryText,
            Map<String, Object> filters,
            List<String> facets,
            String sortBy,
            int offset,
            int limit) {

        this.tenantId = Objects.requireNonNull(tenantId, "tenantId cannot be null");
        this.queryText = queryText;
        this.filters = filters != null ? Map.copyOf(filters) : Map.of();
        this.facets = facets != null ? List.copyOf(facets) : List.of();
        this.sortBy = sortBy != null ? sortBy : "relevance";
        this.offset = offset;
        this.limit = limit;

        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId cannot be blank");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset cannot be negative");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        if (limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit cannot exceed " + MAX_LIMIT);
        }
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getQueryText() {
        return queryText;
    }

    public Map<String, Object> getFilters() {
        return Collections.unmodifiableMap(filters);
    }

    public List<String> getFacets() {
        return Collections.unmodifiableList(facets);
    }

    public String getSortBy() {
        return sortBy;
    }

    public int getOffset() {
        return offset;
    }

    public int getLimit() {
        return limit;
    }

    public boolean hasQueryText() {
        return queryText != null && !queryText.isBlank();
    }

    public boolean hasFilters() {
        return !filters.isEmpty();
    }

    public boolean hasFacets() {
        return !facets.isEmpty();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String tenantId;
        private String queryText;
        private Map<String, Object> filters;
        private List<String> facets;
        private String sortBy;
        private int offset = 0;
        private int limit = DEFAULT_LIMIT;

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder queryText(String queryText) {
            this.queryText = queryText;
            return this;
        }

        public Builder filters(Map<String, Object> filters) {
            this.filters = filters;
            return this;
        }

        public Builder facets(List<String> facets) {
            this.facets = facets;
            return this;
        }

        public Builder sortBy(String sortBy) {
            this.sortBy = sortBy;
            return this;
        }

        public Builder offset(int offset) {
            this.offset = offset;
            return this;
        }

        public Builder limit(int limit) {
            this.limit = limit;
            return this;
        }

        public SearchQuery build() {
            return new SearchQuery(tenantId, queryText, filters, facets, sortBy, offset, limit);
        }
    }

    @Override
    public String toString() {
        return "SearchQuery{" +
                "tenantId='" + tenantId + '\'' +
                ", queryText='" + queryText + '\'' +
                ", filters=" + filters.keySet() +
                ", facets=" + facets +
                ", sortBy='" + sortBy + '\'' +
                ", offset=" + offset +
                ", limit=" + limit +
                '}';
    }
}
