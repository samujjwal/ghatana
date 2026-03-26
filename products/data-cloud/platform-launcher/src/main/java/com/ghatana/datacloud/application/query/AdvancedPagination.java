package com.ghatana.datacloud.application.query;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Advanced pagination system with multiple strategies.
 *
 * <p><b>Purpose</b><br>
 * Provides multiple pagination approaches:
 * - Offset-limit pagination (traditional)
 * - Cursor-based pagination (performant)
 * - Keyset pagination (seek method)
 * - Performance-optimized for large datasets
 *
 * <p><b>Features</b><br>
 * - Multiple pagination strategies
 * - Automatic strategy selection
 * - Performance optimization
 * - Efficient seeking
 * - Cursor encoding/decoding
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * AdvancedPagination pagination = new AdvancedPagination();
 *
 * // Offset-limit pagination
 * PaginationRequest req = PaginationRequest.offsetLimit(10, 20);
 *
 * // Cursor-based pagination
 * req = PaginationRequest.cursor("eyJpZCI6IDEyM30=", 10);
 *
 * // Execute pagination
 * PaginationResult result = pagination.paginate(data, req);
 * }</pre>
 *
 * @doc.type service
 * @doc.purpose Advanced pagination strategies
 * @doc.layer application
 * @doc.pattern Strategy
 */
public class AdvancedPagination {

    private static final Logger logger = LoggerFactory.getLogger(AdvancedPagination.class);

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 1000;
    private static final long CURSOR_THRESHOLD = 10000; // Switch to cursor after this many rows

    /**
     * Paginate a list of items using appropriate strategy.
     *
     * @param items the items to paginate
     * @param request the pagination request
     * @return pagination result
     */
    public <T> PaginationResult<T> paginate(List<T> items, PaginationRequest request) {
        if (items == null || items.isEmpty()) {
            return new PaginationResult<>(Collections.emptyList(), new PaginationMetadata(0, 0, 0, null));
        }

        if (request instanceof OffsetLimitRequest) {
            return paginateOffsetLimit(items, (OffsetLimitRequest) request);
        } else if (request instanceof CursorRequest) {
            return paginateCursor(items, (CursorRequest) request);
        } else if (request instanceof KeysetRequest) {
            return paginateKeyset(items, (KeysetRequest) request);
        } else {
            throw new IllegalArgumentException("Unknown pagination request type");
        }
    }

    /**
     * Offset-limit pagination (traditional).
     *
     * @param items the items
     * @param request the offset-limit request
     * @return pagination result
     */
    private <T> PaginationResult<T> paginateOffsetLimit(List<T> items, OffsetLimitRequest request) {
        int offset = Math.max(0, request.offset());
        int limit = Math.min(request.limit(), MAX_PAGE_SIZE);
        int total = items.size();

        if (offset >= total) {
            return new PaginationResult<>(Collections.emptyList(),
                    new PaginationMetadata(total, offset, 0, null));
        }

        int end = Math.min(offset + limit, total);
        List<T> page = items.subList(offset, end);

        String nextCursor = end < total ? encodeCursor(end) : null;

        logger.debug("Offset-limit pagination: offset={}, limit={}, total={}, pageSize={}",
                offset, limit, total, page.size());

        return new PaginationResult<>(page,
                new PaginationMetadata(total, offset, page.size(), nextCursor));
    }

    /**
     * Cursor-based pagination (performant for large datasets).
     *
     * @param items the items
     * @param request the cursor request
     * @return pagination result
     */
    private <T> PaginationResult<T> paginateCursor(List<T> items, CursorRequest request) {
        int limit = Math.min(request.limit(), MAX_PAGE_SIZE);
        int total = items.size();

        int startIndex = 0;
        if (request.cursor() != null && !request.cursor().isEmpty()) {
            startIndex = decodeCursor(request.cursor());
        }

        if (startIndex >= total) {
            return new PaginationResult<>(Collections.emptyList(),
                    new PaginationMetadata(total, startIndex, 0, null));
        }

        int endIndex = Math.min(startIndex + limit, total);
        List<T> page = items.subList(startIndex, endIndex);

        String nextCursor = endIndex < total ? encodeCursor(endIndex) : null;

        logger.debug("Cursor-based pagination: cursor={}, limit={}, total={}, pageSize={}",
                request.cursor(), limit, total, page.size());

        return new PaginationResult<>(page,
                new PaginationMetadata(total, startIndex, page.size(), nextCursor));
    }

    /**
     * Keyset pagination (seek method - most efficient for large datasets).
     *
     * @param items the items
     * @param request the keyset request
     * @return pagination result
     */
    private <T> PaginationResult<T> paginateKeyset(List<T> items, KeysetRequest request) {
        int limit = Math.min(request.limit(), MAX_PAGE_SIZE);
        int total = items.size();

        int startIndex = 0;
        if (request.lastKey() != null && !request.lastKey().isEmpty()) {
            startIndex = findKeyIndex(items, request.lastKey()) + 1;
        }

        if (startIndex >= total) {
            return new PaginationResult<>(Collections.emptyList(),
                    new PaginationMetadata(total, startIndex, 0, null));
        }

        int endIndex = Math.min(startIndex + limit, total);
        List<T> page = items.subList(startIndex, endIndex);

        String nextKey = endIndex < total ? extractKey(items.get(endIndex - 1)) : null;

        logger.debug("Keyset pagination: lastKey={}, limit={}, total={}, pageSize={}",
                request.lastKey(), limit, total, page.size());

        return new PaginationResult<>(page,
                new PaginationMetadata(total, startIndex, page.size(), nextKey));
    }

    /**
     * Find index of item by key.
     *
     * @param items the items
     * @param key the key
     * @return index or -1 if not found
     */
    private <T> int findKeyIndex(List<T> items, String key) {
        for (int i = 0; i < items.size(); i++) {
            if (extractKey(items.get(i)).equals(key)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Extract key from item (uses toString for now).
     *
     * @param item the item
     * @return the key
     */
    private <T> String extractKey(T item) {
        return item.toString();
    }

    /**
     * Encode index as cursor.
     *
     * @param index the index
     * @return encoded cursor
     */
    private String encodeCursor(int index) {
        String json = "{\"offset\":" + index + "}";
        return Base64.getEncoder().encodeToString(json.getBytes());
    }

    /**
     * Decode cursor to index.
     *
     * @param cursor the cursor
     * @return the index
     */
    private int decodeCursor(String cursor) {
        try {
            byte[] decoded = Base64.getDecoder().decode(cursor);
            String json = new String(decoded);
            // Simple JSON parsing for offset
            int startIndex = json.indexOf("\"offset\":") + 9;
            int endIndex = json.indexOf("}", startIndex);
            return Integer.parseInt(json.substring(startIndex, endIndex).trim());
        } catch (Exception e) {
            logger.warn("Failed to decode cursor: {}", cursor, e);
            return 0;
        }
    }

    /**
     * Select best pagination strategy based on dataset size.
     *
     * @param totalSize the total dataset size
     * @return recommended strategy
     */
    public String recommendStrategy(long totalSize) {
        if (totalSize < 1000) {
            return "OFFSET_LIMIT"; // Good for small datasets
        } else if (totalSize < 100000) {
            return "CURSOR"; // Good for medium datasets
        } else {
            return "KEYSET"; // Best for large datasets
        }
    }

    /**
     * Pagination request base interface.
     */
    public interface PaginationRequest {
        int limit();

        static OffsetLimitRequest offsetLimit(int offset, int limit) {
            return new OffsetLimitRequest(offset, limit);
        }

        static CursorRequest cursor(String cursor, int limit) {
            return new CursorRequest(cursor, limit);
        }

        static KeysetRequest keyset(String lastKey, int limit) {
            return new KeysetRequest(lastKey, limit);
        }
    }

    /**
     * Offset-limit pagination request.
     */
    public record OffsetLimitRequest(int offset, int limit) implements PaginationRequest {}

    /**
     * Cursor-based pagination request.
     */
    public record CursorRequest(String cursor, int limit) implements PaginationRequest {}

    /**
     * Keyset pagination request.
     */
    public record KeysetRequest(String lastKey, int limit) implements PaginationRequest {}

    /**
     * Pagination result.
     */
    public record PaginationResult<T>(List<T> items, PaginationMetadata metadata) {}

    /**
     * Pagination metadata.
     */
    public record PaginationMetadata(
            int total,
            int offset,
            int pageSize,
            String nextCursor
    ) {
        public boolean hasNext() {
            return nextCursor != null;
        }

        public int pages() {
            return (total + pageSize - 1) / pageSize;
        }

        public int currentPage() {
            return (offset / pageSize) + 1;
        }
    }
}

