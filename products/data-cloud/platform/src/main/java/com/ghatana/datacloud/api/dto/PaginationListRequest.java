package com.ghatana.datacloud.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request object for paginated list queries.
 *
 * <p><b>Purpose</b><br>
 * Encapsulates pagination parameters (cursor position, page size, sorting, filtering)
 * for consistent pagination across all entity list endpoints.
 *
 * <p><b>Usage in API</b><br>
 * <pre>{@code
 * GET /api/collections/{collectionId}/entities/list
 * {
 *   "cursor": "base64-encoded-position",
 *   "pageSize": 50,
 *   "sortBy": "name",
 *   "sortOrder": "ASCENDING",
 *   "filter": {"type": "equals", "field": "status", "value": "active"}
 * }
 * }</pre>
 *
 * @doc.type record
 * @doc.purpose Request for paginated entity list queries
 * @doc.layer product
 * @doc.pattern Value Object
 */
public record PaginationListRequest(
    @JsonProperty("cursor")
    String cursor,

    @JsonProperty("pageSize")
    int pageSize,

    @JsonProperty("sortBy")
    String sortBy,

    @JsonProperty("sortOrder")
    String sortOrder,

    @JsonProperty("filter")
    String filter  // JSON string of QueryFilter
) {

    public static final int DEFAULT_PAGE_SIZE = 100;
    public static final int MAX_PAGE_SIZE = 1000;

    /**
     * Creates pagination request with validation.
     *
     * @param cursor     pagination cursor (nullable)
     * @param pageSize   number of results (1-1000)
     * @param sortBy     field to sort by (nullable)
     * @param sortOrder  ASCENDING or DESCENDING (nullable)
     * @param filter     JSON query filter (nullable)
     * @throws IllegalArgumentException if pageSize outside valid range
     */
    public PaginationListRequest {
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                "pageSize must be between 1 and " + MAX_PAGE_SIZE + ", got " + pageSize);
        }
    }

    /**
     * Creates builder for PaginationListRequest.
     *
     * @return builder with defaults
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for PaginationListRequest.
     */
    public static class Builder {
        private String cursor = null;
        private int pageSize = DEFAULT_PAGE_SIZE;
        private String sortBy = "createdAt";
        private String sortOrder = "DESCENDING";
        private String filter = null;

        public Builder cursor(String cursor) {
            this.cursor = cursor;
            return this;
        }

        public Builder pageSize(int pageSize) {
            if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
                throw new IllegalArgumentException(
                    "pageSize must be between 1 and " + MAX_PAGE_SIZE);
            }
            this.pageSize = pageSize;
            return this;
        }

        public Builder sortBy(String sortBy) {
            this.sortBy = sortBy != null ? sortBy : "createdAt";
            return this;
        }

        public Builder sortOrder(String sortOrder) {
            this.sortOrder = sortOrder != null ? sortOrder : "DESCENDING";
            return this;
        }

        public Builder filter(String filter) {
            this.filter = filter;
            return this;
        }

        public PaginationListRequest build() {
            return new PaginationListRequest(cursor, pageSize, sortBy, sortOrder, filter);
        }
    }
}
