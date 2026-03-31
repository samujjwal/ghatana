package com.ghatana.yappc.infrastructure.datacloud.pagination;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Paginated result container for Data-Cloud queries.
 *
 * <p><b>Purpose</b><br>
 * Provides a standardized way to return paginated query results with metadata
 * for cursor-based pagination. Supports efficient large dataset handling without
 * offset-based performance degradation.
 *
 * <p><b>Features</b><br>
 * - Cursor-based pagination for efficient deep paging<br>
 * - Total count tracking (when available)<br>
 * - Has-more indicator for incremental loading<br>
 * - Page size validation<br>
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Return first page
 * PaginatedResult<ProjectEntity> result = repository.findPaginated(
 *     filter, null, 50);
 *
 * // Return subsequent page using cursor
 * String nextCursor = result.nextCursor().orElseThrow();
 * PaginatedResult<ProjectEntity> nextPage = repository.findPaginated(
 *     filter, nextCursor, 50);
 * }</pre>
 *
 * @param <T> The entity type in the result
 *
 * @doc.type class
 * @doc.purpose Paginated query result container with cursor support
 * @doc.layer infrastructure
 * @doc.pattern Value Object, Data Transfer Object
 */
public final class PaginatedResult<T> {

    private final List<T> items;
    private final String nextCursor;
    private final int pageSize;
    private final long totalCount;
    private final boolean hasMore;

    private PaginatedResult(Builder<T> builder) {
        this.items = List.copyOf(Objects.requireNonNull(builder.items, "items must not be null"));
        this.nextCursor = builder.nextCursor;
        this.pageSize = builder.pageSize;
        this.totalCount = builder.totalCount;
        this.hasMore = builder.hasMore;
    }

    /**
     * Returns the items in the current page.
     *
     * @return immutable list of items
     */
    public List<T> items() {
        return items;
    }

    /**
     * Returns the cursor for the next page, if available.
     *
     * @return optional containing next cursor, empty if no more pages
     */
    public Optional<String> nextCursor() {
        return Optional.ofNullable(nextCursor);
    }

    /**
     * Returns the page size used for this query.
     *
     * @return number of items requested per page
     */
    public int pageSize() {
        return pageSize;
    }

    /**
     * Returns the total count of items matching the query, if known.
     *
     * @return total count, or -1 if not available
     */
    public long totalCount() {
        return totalCount;
    }

    /**
     * Returns whether more pages are available.
     *
     * @return true if next cursor is present
     */
    public boolean hasMore() {
        return hasMore;
    }

    /**
     * Returns the number of items in the current page.
     *
     * @return actual item count
     */
    public int itemCount() {
        return items.size();
    }

    /**
     * Returns whether this is the first page (no previous cursor).
     *
     * @return true if first page
     */
    public boolean isFirstPage() {
        return items.size() > 0 && nextCursor != null && items.size() < pageSize;
    }

    /**
     * Creates a new builder for the specified item type.
     *
     * @param <T> the item type
     * @return new builder instance
     */
    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    /**
     * Creates an empty result with no items.
     *
     * @param <T> the item type
     * @param pageSize the page size that was requested
     * @return empty paginated result
     */
    public static <T> PaginatedResult<T> empty(int pageSize) {
        return new Builder<T>()
            .items(List.of())
            .pageSize(pageSize)
            .totalCount(0)
            .hasMore(false)
            .build();
    }

    @Override
    public String toString() {
        return "PaginatedResult{" +
            "items=" + items.size() +
            ", nextCursor=" + (nextCursor != null ? "[present]" : "[null]") +
            ", pageSize=" + pageSize +
            ", totalCount=" + totalCount +
            ", hasMore=" + hasMore +
            '}';
    }

    /**
     * Builder for PaginatedResult.
     *
     * @param <T> the item type
     */
    public static final class Builder<T> {
        private List<T> items = List.of();
        private String nextCursor;
        private int pageSize = PaginationConfig.DEFAULT_PAGE_SIZE;
        private long totalCount = -1;
        private boolean hasMore = false;

        private Builder() {}

        public Builder<T> items(List<T> items) {
            this.items = items;
            return this;
        }

        public Builder<T> nextCursor(String nextCursor) {
            this.nextCursor = nextCursor;
            return this;
        }

        public Builder<T> pageSize(int pageSize) {
            this.pageSize = pageSize;
            return this;
        }

        public Builder<T> totalCount(long totalCount) {
            this.totalCount = totalCount;
            return this;
        }

        public Builder<T> hasMore(boolean hasMore) {
            this.hasMore = hasMore;
            return this;
        }

        public PaginatedResult<T> build() {
            return new PaginatedResult<>(this);
        }
    }
}
