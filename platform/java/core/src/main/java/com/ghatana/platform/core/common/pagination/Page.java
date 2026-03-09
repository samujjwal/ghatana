package com.ghatana.platform.core.common.pagination;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * Page of query results with pagination metadata.
 *
 * <p>
 * <b>Purpose</b><br>
 * Platform-agnostic page result without depending on Spring Data or other
 * framework-specific types.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * Page<User> page = Page.of(users, 20, 0, 100);
 * Page<UserDTO> dtos = page.map(user -> new UserDTO(user));
 * }</pre>
 *
 * <p>
 * <b>Thread Safety</b><br>
 * Immutable and thread-safe.
 *
 * @param <T> content type
 * @see PageRequest
 * @see Sort
 * @doc.type record
 * @doc.purpose Platform-agnostic page result with metadata
 * @doc.layer core
 * @doc.pattern Value Object
 */
public record Page<T>(
        List<T> content,
        int pageSize,
        int pageNumber,
        long totalElements
        ) {

    /**
     * Creates a page.
     *
     * @param content page content
     * @param pageSize items per page
     * @param pageNumber zero-based page number
     * @param totalElements total items across all pages
     * @param <T> content type
     * @return page
     */
    public static <T> Page<T> of(List<T> content, int pageSize, int pageNumber, long totalElements) {
        return new Page<>(List.copyOf(content), pageSize, pageNumber, totalElements);
    }

    /**
     * Creates an empty page.
     *
     * @param <T> content type
     * @return empty page
     */
    public static <T> Page<T> empty() {
        return new Page<>(Collections.emptyList(), 0, 0, 0);
    }

    /**
     * Maps page content to another type.
     *
     * @param mapper mapping function
     * @param <U> target type
     * @return mapped page
     */
    public <U> Page<U> map(Function<T, U> mapper) {
        List<U> mapped = content.stream()
                .map(mapper)
                .toList();
        return new Page<>(mapped, pageSize, pageNumber, totalElements);
    }

    /**
     * Calculates total pages.
     *
     * @return total pages
     */
    public int getTotalPages() {
        return pageSize == 0 ? 1 : (int) Math.ceil((double) totalElements / pageSize);
    }

    /**
     * Checks if this is the first page.
     *
     * @return true if first page
     */
    public boolean isFirst() {
        return pageNumber == 0;
    }

    /**
     * Checks if this is the last page.
     *
     * @return true if last page
     */
    public boolean isLast() {
        return pageNumber >= getTotalPages() - 1;
    }

    /**
     * Checks if there is a next page.
     *
     * @return true if next page exists
     */
    public boolean hasNext() {
        return !isLast();
    }

    /**
     * Checks if there is a previous page.
     *
     * @return true if previous page exists
     */
    public boolean hasPrevious() {
        return !isFirst();
    }

    /**
     * Gets the number of elements in this page.
     *
     * @return element count
     */
    public int getNumberOfElements() {
        return content.size();
    }

    /**
     * Checks if page is empty.
     *
     * @return true if no content
     */
    public boolean isEmpty() {
        return content.isEmpty();
    }
}
