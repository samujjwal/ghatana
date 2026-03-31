package com.ghatana.datacloud.application;

import com.ghatana.platform.core.util.PaginationUtils;

/**
 * Helper utility for pagination with stable ordering.
 *
 * <p>Delegates core validation and offset math to the canonical
 * {@link PaginationUtils} from platform:java:core, preserving the
 * existing {@code PaginationParams} API for Data Cloud controllers.</p>
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * PaginationHelper.PaginationParams params = PaginationHelper.validate(page, pageSize);
 * List<Entity> results = repository.findAll(params.offset(), params.limit());
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Pagination facade delegating to platform PaginationUtils
 * @doc.layer application
 * @doc.pattern Utility, Facade
 */
public final class PaginationHelper {

    private PaginationHelper() {
        // Utility class - no instantiation
    }

    /**
     * Pagination parameters with validated values.
     *
     * @param offset the row offset (0-based)
     * @param limit  the page size
     * @doc.type record
     */
    public record PaginationParams(int offset, int limit) {}

    /**
     * Validates and normalizes pagination parameters.
     *
     * @param page     zero-based page number, may be null (defaults to 0)
     * @param pageSize items per page, may be null (defaults to 50, max 1000)
     * @return validated pagination parameters
     * @throws IllegalArgumentException if parameters are out of range
     */
    public static PaginationParams validate(Integer page, Integer pageSize) {
        var req = PaginationUtils.validateRequest(page, pageSize);
        return new PaginationParams(req.getOffset(), req.pageSize());
    }

    /**
     * Calculates offset from page and page size.
     *
     * @param page     the page number (0-based)
     * @param pageSize the page size
     * @return the offset for the query
     */
    public static int calculateOffset(int page, int pageSize) {
        return (int) PaginationUtils.calculateOffset(page, pageSize);
    }

    /**
     * Calculates total pages.
     *
     * @param totalCount total number of items
     * @param pageSize   page size
     * @return total page count
     */
    public static int calculateTotalPages(long totalCount, int pageSize) {
        return PaginationUtils.calculateTotalPages(totalCount, pageSize);
    }

    /**
     * Checks if there is a next page.
     *
     * @param currentPage the current page number (0-based)
     * @param pageSize    the page size
     * @param totalCount  the total number of items
     * @return true if a next page exists
     */
    public static boolean hasNextPage(int currentPage, int pageSize, long totalCount) {
        long nextOffset = PaginationUtils.calculateOffset(currentPage + 1, pageSize);
        return nextOffset < totalCount;
    }

    /**
     * Checks if there is a previous page.
     *
     * @param currentPage the current page number (0-based)
     * @return true if a previous page exists
     */
    public static boolean hasPreviousPage(int currentPage) {
        return currentPage > 0;
    }
}
