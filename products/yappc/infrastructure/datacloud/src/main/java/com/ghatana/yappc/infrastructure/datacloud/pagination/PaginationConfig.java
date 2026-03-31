package com.ghatana.yappc.infrastructure.datacloud.pagination;

import com.ghatana.platform.core.util.PaginationUtils;

/**
 * Configuration and utilities for Data-Cloud cursor-based pagination.
 *
 * <p>Delegates offset/size validation to the canonical {@link PaginationUtils}
 * from {@code platform:java:core}. Cursor encoding/decoding is preserved here
 * as it is specific to the YAPPC Data Cloud seek pagination strategy.</p>
 *
 * @doc.type class
 * @doc.purpose Cursor-based pagination configuration for YAPPC Data Cloud
 * @doc.layer infrastructure
 * @doc.pattern Utility, Configuration
 */
public final class PaginationConfig {

    private PaginationConfig() {
        // Utility class
    }

    /** Default number of items per page. */
    public static final int DEFAULT_PAGE_SIZE = PaginationUtils.DEFAULT_PAGE_SIZE;

    /** Maximum allowed page size. */
    public static final int MAX_PAGE_SIZE = 500;

    /** Minimum allowed page size. */
    public static final int MIN_PAGE_SIZE = PaginationUtils.MIN_PAGE_SIZE;

    /**
     * Clamps the requested page size to the valid range for this product.
     *
     * @param requestedSize the requested page size
     * @return clamped size between 1 and 500
     */
    public static int clampPageSize(int requestedSize) {
        return Math.max(MIN_PAGE_SIZE, Math.min(requestedSize, MAX_PAGE_SIZE));
    }

    /**
     * Validates and returns page size with default fallback.
     *
     * @param requestedSize the requested page size, may be null
     * @return validated page size or default
     */
    public static int validatePageSize(Integer requestedSize) {
        if (requestedSize == null) {
            return DEFAULT_PAGE_SIZE;
        }
        return clampPageSize(requestedSize);
    }

    /**
     * Encodes a cursor from last-seen entity ID and sort value.
     * Delegates to {@link PaginationUtils#encodeCursor(String, String)}.
     *
     * @param lastId        the last entity ID seen
     * @param lastSortValue the last sort value seen, may be null
     * @return URL-safe Base64-encoded cursor string
     */
    public static String encodeCursor(String lastId, String lastSortValue) {
        return PaginationUtils.encodeCursor(lastId, lastSortValue);
    }

    /**
     * Decodes a cursor produced by {@link #encodeCursor(String, String)}.
     * Delegates to {@link PaginationUtils#decodeCursor(String)}.
     *
     * @param cursor the encoded cursor
     * @return decoded cursor data
     * @throws IllegalArgumentException if cursor is invalid
     */
    public static CursorData decodeCursor(String cursor) {
        var data = PaginationUtils.decodeCursor(cursor);
        return new CursorData(data.lastId(), data.lastSortValue());
    }

    /**
     * Data class for decoded cursor parts.
     */
    public record CursorData(String lastId, String lastSortValue) {
        /**
         * Creates cursor data with ID only (no sort value).
         *
         * @param lastId the last entity ID
         */
        public CursorData(String lastId) {
            this(lastId, null);
        }
    }
}
