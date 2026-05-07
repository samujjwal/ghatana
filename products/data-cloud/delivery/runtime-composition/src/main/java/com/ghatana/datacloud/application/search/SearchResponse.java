package com.ghatana.datacloud.application.search;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * HTTP response DTO for search operations.
 *
 * <p><b>Purpose</b><br>
 * Binds search results to HTTP response with pagination, facets, and metadata,
 * providing JSON serialization for API consumers.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * SearchResponse response = SearchResponse.builder()
 *     .totalHits(1250)
 *     .offset(0)
 *     .limit(10)
 *     .results(List.of(result1, result2))
 *     .facets(List.of(facet1, facet2))
 *     .build();
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Immutable (all fields final, collections unmodifiable).
 *
 * <p><b>JSON Structure</b><br>
 * <pre>{@code
 * {
 *   "total_hits": 1250,
 *   "offset": 0,
 *   "limit": 10,
 *   "results": [{ id, score, content, highlights, metadata }, ...],
 *   "facets": [{ name, values }, ...],
 *   "query_time_ms": 45
 * }
 * }</pre>
 *
 * @see SearchRequest
 * @see SearchService
 * @doc.type class
 * @doc.purpose HTTP response DTO for search operations
 * @doc.layer product
 * @doc.pattern Value Object
 */
public final class SearchResponse {

    @JsonProperty("total_hits")
    private final Long totalHits;

    @JsonProperty("offset")
    private final Integer offset;

    @JsonProperty("limit")
    private final Integer limit;

    @JsonProperty("results")
    private final List<SearchResultItem> results;

    @JsonProperty("facets")
    private final List<SearchFacetItem> facets;

    @JsonProperty("query_time_ms")
    private final Long queryTimeMs;

    /**
     * Immutable result item in response.
     */
    public static final class SearchResultItem {
        @JsonProperty("id")
        public final String id;

        @JsonProperty("score")
        public final Float score;

        @JsonProperty("content")
        public final String content;

        @JsonProperty("highlights")
        public final Map<String, List<String>> highlights;

        @JsonProperty("metadata")
        public final Map<String, Object> metadata;

        /**
         * Constructs result item.
         *
         * @param id entity ID
         * @param score relevance score
         * @param content indexed content snippet
         * @param highlights highlighted matches
         * @param metadata additional metadata
         */
        public SearchResultItem(
                String id,
                Float score,
                String content,
                Map<String, List<String>> highlights,
                Map<String, Object> metadata) {
            this.id = Objects.requireNonNull(id, "id cannot be null");
            this.score = score;
            this.content = content;
            this.highlights = highlights;
            this.metadata = metadata;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SearchResultItem)) return false;
            SearchResultItem that = (SearchResultItem) o;
            return Objects.equals(id, that.id) &&
                    Objects.equals(score, that.score);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, score);
        }

        @Override
        public String toString() {
            return "SearchResultItem{" +
                    "id='" + id + '\'' +
                    ", score=" + score +
                    ", contentLength=" + (content != null ? content.length() : 0) +
                    '}';
        }
    }

    /**
     * Immutable facet item in response.
     */
    public static final class SearchFacetItem {
        @JsonProperty("field")
        public final String field;

        @JsonProperty("values")
        public final List<FacetValue> values;

        /**
         * Constructs facet item.
         *
         * @param field field name
         * @param values facet values with counts
         */
        public SearchFacetItem(String field, List<FacetValue> values) {
            this.field = Objects.requireNonNull(field, "field cannot be null");
            this.values = values != null ? Collections.unmodifiableList(values) : Collections.emptyList();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SearchFacetItem)) return false;
            SearchFacetItem that = (SearchFacetItem) o;
            return Objects.equals(field, that.field) &&
                    Objects.equals(values, that.values);
        }

        @Override
        public int hashCode() {
            return Objects.hash(field, values);
        }

        @Override
        public String toString() {
            return "SearchFacetItem{" +
                    "field='" + field + '\'' +
                    ", valueCount=" + values.size() +
                    '}';
        }
    }

    /**
     * Facet value with count.
     */
    public static final class FacetValue {
        @JsonProperty("value")
        public final String value;

        @JsonProperty("count")
        public final Integer count;

        /**
         * Constructs facet value.
         *
         * @param value value string
         * @param count occurrence count
         */
        public FacetValue(String value, Integer count) {
            this.value = Objects.requireNonNull(value, "value cannot be null");
            this.count = Objects.requireNonNull(count, "count cannot be null");
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof FacetValue)) return false;
            FacetValue that = (FacetValue) o;
            return Objects.equals(value, that.value) &&
                    Objects.equals(count, that.count);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value, count);
        }

        @Override
        public String toString() {
            return "FacetValue{" +
                    "value='" + value + '\'' +
                    ", count=" + count +
                    '}';
        }
    }

    /**
     * Private constructor for builder pattern.
     *
     * @param totalHits total matching documents
     * @param offset pagination offset
     * @param limit pagination limit
     * @param results search result items
     * @param facets aggregated facets
     * @param queryTimeMs query execution time
     */
    private SearchResponse(
            Long totalHits,
            Integer offset,
            Integer limit,
            List<SearchResultItem> results,
            List<SearchFacetItem> facets,
            Long queryTimeMs) {
        this.totalHits = totalHits;
        this.offset = offset;
        this.limit = limit;
        this.results = results != null ? Collections.unmodifiableList(results) : Collections.emptyList();
        this.facets = facets != null ? Collections.unmodifiableList(facets) : Collections.emptyList();
        this.queryTimeMs = queryTimeMs;
    }

    // ========== Accessors ==========

    public Long getTotalHits() {
        return totalHits;
    }

    public Integer getOffset() {
        return offset;
    }

    public Integer getLimit() {
        return limit;
    }

    public List<SearchResultItem> getResults() {
        return results;
    }

    public List<SearchFacetItem> getFacets() {
        return facets;
    }

    public Long getQueryTimeMs() {
        return queryTimeMs;
    }

    /**
     * Calculates total pages.
     *
     * @return number of pages (rounded up)
     */
    public long getTotalPages() {
        if (totalHits == null || limit == null || limit <= 0) {
            return 0;
        }
        return (totalHits + limit - 1) / limit;
    }

    /**
     * Calculates current page number (0-based).
     *
     * @return current page
     */
    public long getCurrentPage() {
        if (offset == null || limit == null || limit <= 0) {
            return 0;
        }
        return offset / limit;
    }

    /**
     * Checks if has next page.
     *
     * @return true if more results available
     */
    public boolean hasNextPage() {
        if (offset == null || limit == null || totalHits == null) {
            return false;
        }
        return (offset + limit) < totalHits;
    }

    /**
     * Checks if has previous page.
     *
     * @return true if not first page
     */
    public boolean hasPreviousPage() {
        return offset != null && offset > 0;
    }

    // ========== Builder Pattern ==========

    /**
     * Creates new SearchResponse builder.
     *
     * @return builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for SearchResponse instances.
     */
    public static final class Builder {
        private Long totalHits;
        private Integer offset;
        private Integer limit;
        private List<SearchResultItem> results;
        private List<SearchFacetItem> facets;
        private Long queryTimeMs;

        private Builder() {
            // Package-private constructor
        }

        public Builder totalHits(Long totalHits) {
            this.totalHits = totalHits;
            return this;
        }

        public Builder offset(Integer offset) {
            this.offset = offset;
            return this;
        }

        public Builder limit(Integer limit) {
            this.limit = limit;
            return this;
        }

        public Builder results(List<SearchResultItem> results) {
            this.results = results;
            return this;
        }

        public Builder facets(List<SearchFacetItem> facets) {
            this.facets = facets;
            return this;
        }

        public Builder queryTimeMs(Long queryTimeMs) {
            this.queryTimeMs = queryTimeMs;
            return this;
        }

        /**
         * Builds SearchResponse instance.
         *
         * @return immutable SearchResponse
         * @throws IllegalArgumentException if required fields missing
         */
        public SearchResponse build() {
            Objects.requireNonNull(totalHits, "totalHits cannot be null");
            Objects.requireNonNull(offset, "offset cannot be null");
            Objects.requireNonNull(limit, "limit cannot be null");

            return new SearchResponse(totalHits, offset, limit, results, facets, queryTimeMs);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SearchResponse)) return false;
        SearchResponse that = (SearchResponse) o;
        return Objects.equals(totalHits, that.totalHits) &&
                Objects.equals(offset, that.offset) &&
                Objects.equals(limit, that.limit) &&
                Objects.equals(results, that.results);
    }

    @Override
    public int hashCode() {
        return Objects.hash(totalHits, offset, limit, results);
    }

    @Override
    public String toString() {
        return "SearchResponse{" +
                "totalHits=" + totalHits +
                ", offset=" + offset +
                ", limit=" + limit +
                ", resultCount=" + results.size() +
                ", facetCount=" + facets.size() +
                ", queryTimeMs=" + queryTimeMs +
                '}';
    }
}
