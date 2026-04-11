package com.ghatana.platform.core.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Generic pagination model for paginated query results.
 *
 * @doc.type class
 * @doc.purpose Provides a standardized pagination model for API responses
 * @doc.layer platform
 * @doc.pattern Data Transfer Object
 * @param <T> Content type
 */
public record Pagination<T>(
        List<T> content,
        int pageNumber,
        int pageSize,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        boolean empty
) {

    /**
     * Creates a new pagination instance with validation.
     */
    public Pagination {
        Objects.requireNonNull(content, "content must not be null");
        if (pageNumber < 0) {
            throw new IllegalArgumentException("pageNumber must be >= 0");
        }
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be > 0");
        }
        if (totalElements < 0) {
            throw new IllegalArgumentException("totalElements must be >= 0");
        }
        // Make content unmodifiable
        content = Collections.unmodifiableList(content);
    }

    /**
     * Creates a pagination for the first page with given page size.
     */
    public static <T> Pagination<T> firstPage(int pageSize) {
        return new Pagination<>(Collections.emptyList(), 0, pageSize, 0, 0, true, true, true);
    }

    /**
     * Creates a pagination with calculated total pages.
     */
    public static <T> Pagination<T> of(List<T> content, int pageNumber, int pageSize, long totalElements) {
        int totalPages = pageSize == 0 ? 0 : (int) Math.ceil((double) totalElements / pageSize);
        boolean first = pageNumber == 0;
        boolean last = pageNumber >= totalPages - 1 || totalPages == 0;
        boolean empty = content.isEmpty();
        return new Pagination<>(content, pageNumber, pageSize, totalElements, totalPages, first, last, empty);
    }

    /**
     * Creates an empty pagination for the given page request.
     */
    public static <T> Pagination<T> empty(PageRequest pageRequest) {
        return new Pagination<>(
                Collections.emptyList(),
                pageRequest.pageNumber(),
                pageRequest.pageSize(),
                0,
                0,
                pageRequest.pageNumber() == 0,
                true,
                true
        );
    }

    /**
     * Returns the number of elements in the current page.
     */
    public int numberOfElements() {
        return content.size();
    }

    /**
     * Returns a new pagination with mapped content.
     */
    public <R> Pagination<R> map(java.util.function.Function<T, R> mapper) {
        List<R> mappedContent = content.stream().map(mapper).toList();
        return new Pagination<>(mappedContent, pageNumber, pageSize, totalElements, totalPages, first, last, empty);
    }

    /**
     * Page request for pagination queries.
     */
    public record PageRequest(int pageNumber, int pageSize) {
        public PageRequest {
            if (pageNumber < 0) {
                throw new IllegalArgumentException("pageNumber must be >= 0");
            }
            if (pageSize <= 0) {
                throw new IllegalArgumentException("pageSize must be > 0");
            }
        }

        public int offset() {
            return pageNumber * pageSize;
        }

        public static PageRequest of(int pageNumber, int pageSize) {
            return new PageRequest(pageNumber, pageSize);
        }

        public static PageRequest first(int pageSize) {
            return new PageRequest(0, pageSize);
        }

        public PageRequest next() {
            return new PageRequest(pageNumber + 1, pageSize);
        }

        public PageRequest previous() {
            return pageNumber == 0 ? this : new PageRequest(pageNumber - 1, pageSize);
        }
    }
}
