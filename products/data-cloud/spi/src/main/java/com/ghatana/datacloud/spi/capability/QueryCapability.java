package com.ghatana.datacloud.spi.capability;

import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;

/**
 * Capability interface for plugins that support query operations.
 *
 * <p>
 * <b>Purpose</b><br>
 * Defines query operations for data retrieval:
 * <ul>
 * <li>Filtered queries</li>
 * <li>Range queries</li>
 * <li>Aggregations</li>
 * <li>Pagination</li>
 * </ul>
 *
 * @param <T> the result type
 * @see com.ghatana.datacloud.spi.Plugin
 * @doc.type interface
 * @doc.purpose Query capability for plugins
 * @doc.layer spi
 * @doc.pattern Capability
 */
public interface QueryCapability<T> {

    /**
     * Executes a query.
     *
     * @param query the query specification
     * @return list of matching results
     */
    Promise<List<T>> query(QuerySpec query);

    /**
     * Counts results matching a query.
     *
     * @param query the query specification
     * @return the count
     */
    Promise<Long> count(QuerySpec query);

    /**
     * Executes a query with pagination.
     *
     * @param query the query specification
     * @param page the page number (0-based)
     * @param pageSize the page size
     * @return paginated results
     */
    Promise<PagedResult<T>> queryPaged(QuerySpec query, int page, int pageSize);

    /**
     * Query specification.
     */
    record QuerySpec(
            Map<String, Object> filters,
            List<String> orderBy,
            boolean ascending,
            List<String> projections
            ) {

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {

            private Map<String, Object> filters = Map.of();
            private List<String> orderBy = List.of();
            private boolean ascending = true;
            private List<String> projections = List.of();

            public Builder filters(Map<String, Object> filters) {
                this.filters = filters;
                return this;
            }

            public Builder filter(String key, Object value) {
                this.filters = Map.of(key, value);
                return this;
            }

            public Builder orderBy(List<String> orderBy) {
                this.orderBy = orderBy;
                return this;
            }

            public Builder orderBy(String... fields) {
                this.orderBy = List.of(fields);
                return this;
            }

            public Builder ascending(boolean ascending) {
                this.ascending = ascending;
                return this;
            }

            public Builder projections(List<String> projections) {
                this.projections = projections;
                return this;
            }

            public QuerySpec build() {
                return new QuerySpec(filters, orderBy, ascending, projections);
            }
        }
    }

    /**
     * Paginated result.
     *
     * @param <T> the result type
     */
    record PagedResult<T>(
            List<T> items,
            int page,
            int pageSize,
            long totalItems,
            int totalPages
            ) {

        public boolean hasNext() {
            return page < totalPages - 1;
        }

        public boolean hasPrevious() {
            return page > 0;
        }
    }
}
