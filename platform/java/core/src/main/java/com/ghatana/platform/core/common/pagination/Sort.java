package com.ghatana.platform.core.common.pagination;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Sort specification for query results.
 *
 * <p>
 * <b>Purpose</b><br>
 * Platform-agnostic way to specify result ordering without depending on Spring
 * Data or other framework-specific types.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * Sort sort = Sort.by("name").ascending();
 * Sort multi = Sort.by("name", "createdAt").descending();
 * Sort unsorted = Sort.unsorted();
 * }</pre>
 *
 * <p>
 * <b>Thread Safety</b><br>
 * Immutable and thread-safe.
 *
 * @see PageRequest
 * @see Page
 * @doc.type class
 * @doc.purpose Platform-agnostic sort specification
 * @doc.layer core
 * @doc.pattern Value Object
 */
public class Sort {

    private static final Sort UNSORTED = new Sort(Collections.emptyList());

    private final List<Order> orders;

    private Sort(List<Order> orders) {
        this.orders = List.copyOf(orders);
    }

    /**
     * Creates an unsorted specification.
     *
     * @return unsorted spec
     */
    public static Sort unsorted() {
        return UNSORTED;
    }

    /**
     * Creates a sort builder for the given properties.
     *
     * @param properties property names
     * @return sort builder
     */
    public static SortBuilder by(String... properties) {
        return new SortBuilder(properties);
    }

    /**
     * Checks if this sort is unsorted.
     *
     * @return true if no ordering
     */
    public boolean isUnsorted() {
        return orders.isEmpty();
    }

    /**
     * Gets the order specifications.
     *
     * @return immutable list of orders
     */
    public List<Order> getOrders() {
        return orders;
    }

    /**
     * Builder for Sort instances.
     */
    public static class SortBuilder {

        private final String[] properties;

        private SortBuilder(String[] properties) {
            this.properties = properties;
        }

        /**
         * Creates ascending sort.
         *
         * @return sort instance
         */
        public Sort ascending() {
            List<Order> orders = new ArrayList<>();
            for (String property : properties) {
                orders.add(new Order(property, Direction.ASC));
            }
            return new Sort(orders);
        }

        /**
         * Creates descending sort.
         *
         * @return sort instance
         */
        public Sort descending() {
            List<Order> orders = new ArrayList<>();
            for (String property : properties) {
                orders.add(new Order(property, Direction.DESC));
            }
            return new Sort(orders);
        }
    }

    /**
     * Individual order specification.
     *
     * @param property property name
     * @param direction sort direction
     */
    public record Order(String property, Direction direction) {
        /**
         * Checks if this order is ascending.
         *
         * @return true if ascending
         */
    public boolean isAscending() {
        return direction == Direction.ASC;
    }

    /**
     * Checks if this order is descending.
     *
     * @return true if descending
     */
    public boolean isDescending() {
        return direction == Direction.DESC;
    }
}

/**
 * Sort direction.
 */
public enum Direction {
    /**
     * Ascending order
     */
    ASC,
    /**
     * Descending order
     */
    DESC
}
}
