package com.ghatana.datacloud.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/**
 * Response object for paginated entity list queries.
 *
 * <p><b>Purpose</b><br>
 * Encapsulates paginated results with cursor navigation, total count, and metadata.
 * Enables consistent pagination across all entity list endpoints.
 *
 * <p><b>Usage in API Response</b><br>
 * <pre>{@code
 * {
 *   "items": [...],
 *   "totalCount": 10000,
 *   "nextCursor": "base64-encoded-position",
 *   "prevCursor": "base64-encoded-position",
 *   "hasMore": true,
 *   "pageSize": 50
 * }
 * }</pre>
 *
 * @param <T> Type of items in the response
 *
 * @doc.type record
 * @doc.purpose Response for paginated entity list queries
 * @doc.layer product
 * @doc.pattern Value Object
 */
public record PaginationListResponse<T>(
    @JsonProperty("items")
    List<T> items,

    @JsonProperty("totalCount")
    long totalCount,

    @JsonProperty("nextCursor")
    String nextCursor,

    @JsonProperty("prevCursor")
    String prevCursor,

    @JsonProperty("hasMore")
    boolean hasMore,

    @JsonProperty("pageSize")
    int pageSize
) {

    /**
     * Creates pagination response with validation.
     *
     * @param items       list of results (required)
     * @param totalCount  total items across all pages (non-negative)
     * @param nextCursor  cursor for next page (nullable)
     * @param prevCursor  cursor for previous page (nullable)
     * @param hasMore     whether more results exist
     * @param pageSize    number of items in this page (0+)
     * @throws NullPointerException if items is null
     * @throws IllegalArgumentException if counts are negative
     */
    public PaginationListResponse {
        Objects.requireNonNull(items, "items cannot be null");
        if (totalCount < 0) {
            throw new IllegalArgumentException("totalCount cannot be negative");
        }
        if (pageSize < 0) {
            throw new IllegalArgumentException("pageSize cannot be negative");
        }
    }

    /**
     * Returns whether next page is available.
     *
     * @return true if nextCursor exists and hasMore is true
     */
    public boolean hasNextPage() {
        return nextCursor != null && hasMore;
    }

    /**
     * Returns whether previous page is available.
     *
     * @return true if prevCursor exists
     */
    public boolean hasPrevPage() {
        return prevCursor != null;
    }

    /**
     * Returns whether results are empty.
     *
     * @return true if items list is empty
     */
    public boolean isEmpty() {
        return items.isEmpty();
    }

    /**
     * Creates builder for PaginationListResponse with inferred type.
     *
     * @param <T> Type of items
     * @return builder instance
     */
    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    /**
     * Builder for PaginationListResponse.
     */
    public static class Builder<T> {
        private List<T> items = List.of();
        private long totalCount = 0;
        private String nextCursor = null;
        private String prevCursor = null;
        private boolean hasMore = false;
        private int pageSize = 0;

        public Builder<T> items(List<T> items) {
            this.items = Objects.requireNonNull(items, "items cannot be null");
            this.pageSize = items.size();
            return this;
        }

        public Builder<T> totalCount(long totalCount) {
            if (totalCount < 0) {
                throw new IllegalArgumentException("totalCount cannot be negative");
            }
            this.totalCount = totalCount;
            return this;
        }

        public Builder<T> nextCursor(String nextCursor) {
            this.nextCursor = nextCursor;
            return this;
        }

        public Builder<T> prevCursor(String prevCursor) {
            this.prevCursor = prevCursor;
            return this;
        }

        public Builder<T> hasMore(boolean hasMore) {
            this.hasMore = hasMore;
            return this;
        }

        public PaginationListResponse<T> build() {
            return new PaginationListResponse<>(items, totalCount, nextCursor, prevCursor, hasMore, pageSize);
        }
    }
}
