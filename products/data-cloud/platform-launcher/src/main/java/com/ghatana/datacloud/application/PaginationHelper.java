package com.ghatana.datacloud.application;

import java.util.List;
import java.util.Objects;

/**
 * Helper utility for pagination with stable ordering.
 *
 * <p><b>Purpose</b><br>
 * Provides consistent pagination support across all list endpoints.
 * Ensures stable ordering to maintain consistency across page boundaries.
 *
 * <p><b>Key Features</b><br>
 * - Configurable page size with limits
 * - Stable ordering (createdAt DESC, then ID)
 * - Offset calculation for large datasets
 * - Validation of pagination parameters
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * PaginationHelper.PaginationParams params = PaginationHelper.validate(page, pageSize);
 * List<Entity> results = repository.findAll(params.offset(), params.limit());
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Pagination utility for stable ordering
 * @doc.layer application
 * @doc.pattern Utility
 */
public final class PaginationHelper {

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 1000;
    private static final int MIN_PAGE_SIZE = 1;

    private PaginationHelper() {
        // Utility class - no instantiation
    }

    /**
     * Pagination parameters with validated values.
     *
     * @param offset the offset for the query (0-based)
     * @param limit the limit for the query
     * @doc.type record
     */
    public record PaginationParams(int offset, int limit) {
        /**
         * Creates pagination parameters with validation.
         *
         * @param offset the offset (must be >= 0)
         * @param limit the limit (must be between 1 and MAX_PAGE_SIZE)
         * @throws IllegalArgumentException if parameters are invalid
         */
        public PaginationParams {
            if (offset < 0) {
                throw new IllegalArgumentException("Offset must be >= 0, got: " + offset);
            }
            if (limit < MIN_PAGE_SIZE || limit > MAX_PAGE_SIZE) {
                throw new IllegalArgumentException(
                    "Limit must be between " + MIN_PAGE_SIZE + " and " + MAX_PAGE_SIZE + ", got: " + limit
                );
            }
        }
    }

    /**
     * Validates and normalizes pagination parameters.
     *
     * <p>GIVEN: Page number and page size from request
     * WHEN: validate() is called
     * THEN: Returns validated PaginationParams with safe defaults
     *
     * @param page the page number (0-based, default 0)
     * @param pageSize the page size (default 50, max 1000)
     * @return validated pagination parameters
     * @throws IllegalArgumentException if parameters are invalid
     */
    public static PaginationParams validate(Integer page, Integer pageSize) {
        int p = Objects.requireNonNullElse(page, 0);
        int ps = Objects.requireNonNullElse(pageSize, DEFAULT_PAGE_SIZE);

        if (p < 0) {
            throw new IllegalArgumentException("Page must be >= 0, got: " + p);
        }
        if (ps < MIN_PAGE_SIZE || ps > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                "Page size must be between " + MIN_PAGE_SIZE + " and " + MAX_PAGE_SIZE + ", got: " + ps
            );
        }

        int offset = p * ps;
        return new PaginationParams(offset, ps);
    }

    /**
     * Calculates offset from page and page size.
     *
     * @param page the page number (0-based)
     * @param pageSize the page size
     * @return the offset for the query
     */
    public static int calculateOffset(int page, int pageSize) {
        return page * pageSize;
    }

    /**
     * Calculates total pages from total count and page size.
     *
     * @param totalCount the total number of items
     * @param pageSize the page size
     * @return the total number of pages
     */
    public static int calculateTotalPages(long totalCount, int pageSize) {
        return (int) Math.ceil((double) totalCount / pageSize);
    }

    /**
     * Checks if there is a next page.
     *
     * @param currentPage the current page number (0-based)
     * @param pageSize the page size
     * @param totalCount the total number of items
     * @return true if there is a next page
     */
    public static boolean hasNextPage(int currentPage, int pageSize, long totalCount) {
        int nextPageOffset = (currentPage + 1) * pageSize;
        return nextPageOffset < totalCount;
    }

    /**
     * Checks if there is a previous page.
     *
     * @param currentPage the current page number (0-based)
     * @return true if there is a previous page
     */
    public static boolean hasPreviousPage(int currentPage) {
        return currentPage > 0;
    }
}
