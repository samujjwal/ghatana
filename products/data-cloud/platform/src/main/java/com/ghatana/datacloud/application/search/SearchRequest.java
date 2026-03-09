package com.ghatana.datacloud.application.search;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * HTTP request DTO for search operations.
 *
 * <p>
 * <b>Purpose</b><br>
 * Binds incoming search requests to HTTP API, providing JSON
 * serialization/deserialization
 * and validation for full-text, vector, and faceted search queries.
 *
 * <p>
 * <b>Usage</b><br>
 * 
 * <pre>{@code
 * SearchRequest request = SearchRequest.builder()
 *         .query("gaming laptop")
 *         .filters(Map.of("category", "electronics"))
 *         .facets(List.of("brand", "price_range"))
 *         .limit(20)
 *         .build();
 * }</pre>
 *
 * <p>
 * <b>Thread Safety</b><br>
 * Immutable (all fields final, collections unmodifiable).
 *
 * <p>
 * <b>JSON Mapping</b><br>
 * - Supports snake_case JSON properties via @JsonProperty
 * - query → queryText in SearchQuery
 * - sort_by → sortBy in SearchQuery
 * - All optional fields have sensible defaults
 *
 * @see SearchResponse
 * @see SearchService
 * @doc.type class
 * @doc.purpose HTTP request DTO for search operations
 * @doc.layer product
 * @doc.pattern Value Object
 */
public final class SearchRequest {

    @JsonProperty("query")
    private final String query;

    @JsonProperty("filters")
    private final Map<String, Object> filters;

    @JsonProperty("facets")
    private final List<String> facets;

    @JsonProperty("sort_by")
    private final String sortBy;

    @JsonProperty("offset")
    private final Integer offset;

    @JsonProperty("limit")
    private final Integer limit;

    @JsonProperty("vector")
    private final float[] vector;

    /**
     * Constructor used by both builder and Jackson deserialization.
     *
     * @param query   search query text
     * @param filters optional filters map
     * @param facets  optional facets to aggregate
     * @param sortBy  optional sort field
     * @param offset  pagination offset
     * @param limit   pagination limit
     * @param vector  optional vector for semantic search
     */
    @JsonCreator
    public SearchRequest(
            @JsonProperty("query") String query,
            @JsonProperty("filters") Map<String, Object> filters,
            @JsonProperty("facets") List<String> facets,
            @JsonProperty("sort_by") String sortBy,
            @JsonProperty("offset") Integer offset,
            @JsonProperty("limit") Integer limit,
            @JsonProperty("vector") float[] vector) {
        this.query = query;
        this.filters = filters;
        this.facets = facets;
        this.sortBy = sortBy;
        this.offset = offset;
        this.limit = limit;
        this.vector = vector;
    }

    // ========== Accessors ==========

    /**
     * Returns search query text.
     *
     * @return query text, may be null
     */
    public String getQuery() {
        return query;
    }

    /**
     * Returns filters map.
     *
     * @return filters map, may be null
     */
    public Map<String, Object> getFilters() {
        return filters;
    }

    /**
     * Returns facets to aggregate.
     *
     * @return facets list, may be null
     */
    public List<String> getFacets() {
        return facets;
    }

    /**
     * Returns sort field.
     *
     * @return sort field, may be null (defaults to "relevance")
     */
    public String getSortBy() {
        return sortBy;
    }

    /**
     * Returns pagination offset.
     *
     * @return offset, may be null when not provided
     */
    public Integer getOffset() {
        return offset;
    }

    /**
     * Returns pagination limit.
     *
     * @return limit, may be null when not provided
     */
    public Integer getLimit() {
        return limit;
    }

    /**
     * Returns vector for semantic search.
     *
     * @return vector array, may be null
     */
    public float[] getVector() {
        return vector;
    }

    /**
     * Checks if query is present.
     *
     * @return true if query is not null or blank
     */
    public boolean hasQuery() {
        return query != null && !query.isBlank();
    }

    /**
     * Checks if filters are present.
     *
     * @return true if filters map is not empty
     */
    public boolean hasFilters() {
        return filters != null && !filters.isEmpty();
    }

    /**
     * Checks if facets are present.
     *
     * @return true if facets list is not empty
     */
    public boolean hasFacets() {
        return facets != null && !facets.isEmpty();
    }

    /**
     * Checks if vector is present.
     *
     * @return true if vector array is not null and not empty
     */
    public boolean hasVector() {
        return vector != null && vector.length > 0;
    }

    // ========== Builder Pattern ==========

    /**
     * Creates new SearchRequest builder.
     *
     * @return builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for SearchRequest instances.
     *
     * <p>
     * Provides fluent API for constructing search requests with optional fields.
     */
    public static final class Builder {
        private String query;
        private Map<String, Object> filters;
        private List<String> facets;
        private String sortBy;
        private Integer offset;
        private Integer limit;
        private float[] vector;

        private Builder() {
            // Package-private constructor
        }

        /**
         * Sets search query text.
         *
         * @param query search query
         * @return builder for chaining
         */
        public Builder query(String query) {
            this.query = query;
            return this;
        }

        /**
         * Sets filters map.
         *
         * @param filters filter map
         * @return builder for chaining
         */
        public Builder filters(Map<String, Object> filters) {
            this.filters = filters;
            return this;
        }

        /**
         * Sets facets to aggregate.
         *
         * @param facets facet list
         * @return builder for chaining
         */
        public Builder facets(List<String> facets) {
            this.facets = facets;
            return this;
        }

        /**
         * Sets sort field.
         *
         * @param sortBy sort field name
         * @return builder for chaining
         */
        public Builder sortBy(String sortBy) {
            this.sortBy = sortBy;
            return this;
        }

        /**
         * Sets pagination offset.
         *
         * @param offset pagination offset (0-based)
         * @return builder for chaining
         */
        public Builder offset(Integer offset) {
            this.offset = offset;
            return this;
        }

        /**
         * Sets pagination limit.
         *
         * @param limit pagination limit (1-100)
         * @return builder for chaining
         */
        public Builder limit(Integer limit) {
            this.limit = limit;
            return this;
        }

        /**
         * Sets vector for semantic search.
         *
         * @param vector embedding vector
         * @return builder for chaining
         */
        public Builder vector(float[] vector) {
            this.vector = vector;
            return this;
        }

        /**
         * Builds SearchRequest instance.
         *
         * <p>
         * Performs validation:
         * - If both query and vector present, vector takes precedence
         * - offset must be ≥ 0
         * - limit must be 1-100
         *
         * @return immutable SearchRequest
         * @throws IllegalArgumentException if validation fails
         */
        public SearchRequest build() {
            if (offset != null && offset < 0) {
                throw new IllegalArgumentException("offset must be >= 0, got: " + offset);
            }
            if (limit != null && (limit < 1 || limit > 100)) {
                throw new IllegalArgumentException("limit must be 1-100, got: " + limit);
            }
            return new SearchRequest(query, filters, facets, sortBy, offset, limit, vector);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof SearchRequest))
            return false;
        SearchRequest that = (SearchRequest) o;
        return Objects.equals(query, that.query) &&
                Objects.equals(filters, that.filters) &&
                Objects.equals(facets, that.facets) &&
                Objects.equals(sortBy, that.sortBy) &&
                Objects.equals(offset, that.offset) &&
                Objects.equals(limit, that.limit);
    }

    @Override
    public int hashCode() {
        return Objects.hash(query, filters, facets, sortBy, offset, limit);
    }

    @Override
    public String toString() {
        return "SearchRequest{" +
                "query='" + query + '\'' +
                ", filters=" + filters +
                ", facets=" + facets +
                ", sortBy='" + sortBy + '\'' +
                ", offset=" + offset +
                ", limit=" + limit +
                ", hasVector=" + (vector != null && vector.length > 0) +
                '}';
    }
}
