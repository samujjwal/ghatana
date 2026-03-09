package com.ghatana.products.yappc.domain.repository;

import java.util.List;

/**
 * Platform-native pagination result replacing Spring Data {@code Page}.
 *
 * <p>Encapsulates a page of results with metadata for offset-based pagination.
 * This eliminates the Spring Data dependency from domain repository interfaces.
 *
 * @param <T> the element type
 *
 * @doc.type record
 * @doc.purpose Platform-native paginated query result
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record PageResult<T>(
    List<T> content,
    long totalElements,
    int offset,
    int limit
) {

    /**
     * Returns the number of elements in this page.
     *
     * @return number of elements
     */
    public int size() {
        return content.size();
    }

    /**
     * Returns whether this page has content.
     *
     * @return true if this page has content
     */
    public boolean hasContent() {
        return !content.isEmpty();
    }

    /**
     * Returns whether there is a next page.
     *
     * @return true if there are more results
     */
    public boolean hasNext() {
        return (long) offset + limit < totalElements;
    }

    /**
     * Returns the total number of pages.
     *
     * @return total pages
     */
    public int totalPages() {
        return limit == 0 ? 1 : (int) Math.ceil((double) totalElements / limit);
    }
}
