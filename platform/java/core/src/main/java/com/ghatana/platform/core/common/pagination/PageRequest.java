package com.ghatana.platform.core.common.pagination;

/**
 * Abstraction for pagination request parameters.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides a platform-agnostic way to specify page number, size, and sorting
 * without depending on Spring Data or other framework-specific types.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * PageRequest request = PageRequest.of(0, 20);
 * PageRequest sorted = PageRequest.of(0, 20, Sort.by("name").ascending());
 * }</pre>
 *
 * <p>
 * <b>Thread Safety</b><br>
 * Immutable and thread-safe.
 *
 * @see Page
 * @see Sort
 * @doc.type record
 * @doc.purpose Platform-agnostic pagination request parameters
 * @doc.layer core
 * @doc.pattern Value Object
 */
public record PageRequest(
        int pageNumber,
        int pageSize,
        Sort sort
        ) {

    /**
     * Creates a page request with no sorting.
     *
     * @param pageNumber zero-based page number
     * @param pageSize number of items per page
     * @return page request
     */
    public static PageRequest of(int pageNumber, int pageSize) {
        return new PageRequest(pageNumber, pageSize, Sort.unsorted());
    }

    /**
     * Creates a page request with sorting.
     *
     * @param pageNumber zero-based page number
     * @param pageSize number of items per page
     * @param sort sort specification
     * @return page request
     */
    public static PageRequest of(int pageNumber, int pageSize, Sort sort) {
        return new PageRequest(pageNumber, pageSize, sort);
    }

    /**
     * Calculates the offset for this page.
     *
     * @return offset (number of items to skip)
     */
    public int getOffset() {
        return pageNumber * pageSize;
    }

    /**
     * Checks if this page request has sorting.
     *
     * @return true if sorted
     */
    public boolean isSorted() {
        return sort != null && !sort.isUnsorted();
    }
}
