package com.ghatana.yappc.infrastructure.datacloud.pagination;

import java.util.Base64;
import java.util.Objects;

/**
 * Configuration and utilities for Data-Cloud pagination.
 *
 * <p><b>Purpose</b><br>
 * Defines pagination constants and provides cursor encoding/decoding utilities
 * for efficient deep pagination without offset-based performance degradation.
 *
 * <p><b>Cursor Format</b><br>
 * Cursors are Base64-encoded JSON objects containing the last seen sort value
 * and entity ID, enabling efficient "seek" pagination.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Validate page size
 * int safeSize = PaginationConfig.clampPageSize(requestedSize);
 *
 * // Encode cursor
 * String cursor = PaginationConfig.encodeCursor(lastItemId, lastSortValue);
 *
 * // Decode cursor
 * CursorData data = PaginationConfig.decodeCursor(cursor);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Pagination configuration and cursor utilities
 * @doc.layer infrastructure
 * @doc.pattern Utility, Configuration
 */
public final class PaginationConfig {

    private PaginationConfig() {
        // Utility class
    }

    /**
     * Default number of items per page.
     */
    public static final int DEFAULT_PAGE_SIZE = 50;

    /**
     * Maximum allowed page size to prevent excessive memory usage.
     */
    public static final int MAX_PAGE_SIZE = 500;

    /**
     * Minimum allowed page size.
     */
    public static final int MIN_PAGE_SIZE = 1;

    /**
     * Clamps the requested page size to valid range.
     *
     * @param requestedSize the requested page size
     * @return clamped size between MIN_PAGE_SIZE and MAX_PAGE_SIZE
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
     * Encodes cursor data to string format.
     *
     * @param lastId the last entity ID seen
     * @param lastSortValue the last sort value seen
     * @return encoded cursor string
     */
    public static String encodeCursor(String lastId, String lastSortValue) {
        Objects.requireNonNull(lastId, "lastId must not be null");
        String data = lastSortValue != null
            ? lastSortValue + "|" + lastId
            : lastId;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data.getBytes());
    }

    /**
     * Decodes cursor string to component parts.
     *
     * @param cursor the encoded cursor
     * @return decoded cursor data
     * @throws IllegalArgumentException if cursor is invalid
     */
    public static CursorData decodeCursor(String cursor) {
        Objects.requireNonNull(cursor, "cursor must not be null");
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor));
            int separatorIndex = decoded.indexOf('|');
            if (separatorIndex > 0) {
                return new CursorData(
                    decoded.substring(separatorIndex + 1),
                    decoded.substring(0, separatorIndex)
                );
            }
            return new CursorData(decoded, null);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid cursor format: " + cursor, e);
        }
    }

    /**
     * Data class for decoded cursor information.
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
