package com.ghatana.platform.database.query;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Generic search filter builder for database queries.
 *
 * @doc.type class
 * @doc.purpose Provides a fluent API for building dynamic search queries
 * @doc.layer platform
 * @doc.pattern Builder
 */
public class SearchFilter {

    private final List<Criterion> criteria = new ArrayList<>();
    private final List<String> sortOrders = new ArrayList<>();
    private int offset = 0;
    private int limit = 100;

    private SearchFilter() {}

    public static SearchFilter builder() {
        return new SearchFilter();
    }

    /**
     * Add an equals criterion.
     */
    public SearchFilter eq(String field, Object value) {
        criteria.add(new Criterion(field, Operator.EQ, value));
        return this;
    }

    /**
     * Add a not equals criterion.
     */
    public SearchFilter ne(String field, Object value) {
        criteria.add(new Criterion(field, Operator.NE, value));
        return this;
    }

    /**
     * Add a greater than criterion.
     */
    public SearchFilter gt(String field, Comparable<?> value) {
        criteria.add(new Criterion(field, Operator.GT, value));
        return this;
    }

    /**
     * Add a greater than or equals criterion.
     */
    public SearchFilter gte(String field, Comparable<?> value) {
        criteria.add(new Criterion(field, Operator.GTE, value));
        return this;
    }

    /**
     * Add a less than criterion.
     */
    public SearchFilter lt(String field, Comparable<?> value) {
        criteria.add(new Criterion(field, Operator.LT, value));
        return this;
    }

    /**
     * Add a less than or equals criterion.
     */
    public SearchFilter lte(String field, Comparable<?> value) {
        criteria.add(new Criterion(field, Operator.LTE, value));
        return this;
    }

    /**
     * Add an in-list criterion.
     */
    public SearchFilter in(String field, Collection<?> values) {
        criteria.add(new Criterion(field, Operator.IN, values));
        return this;
    }

    /**
     * Add a like criterion for pattern matching.
     */
    public SearchFilter like(String field, String pattern) {
        criteria.add(new Criterion(field, Operator.LIKE, pattern));
        return this;
    }

    /**
     * Add an is-null criterion.
     */
    public SearchFilter isNull(String field) {
        criteria.add(new Criterion(field, Operator.IS_NULL, null));
        return this;
    }

    /**
     * Add an is-not-null criterion.
     */
    public SearchFilter isNotNull(String field) {
        criteria.add(new Criterion(field, Operator.IS_NOT_NULL, null));
        return this;
    }

    /**
     * Add ascending sort order.
     */
    public SearchFilter orderByAsc(String field) {
        sortOrders.add(field + " ASC");
        return this;
    }

    /**
     * Add descending sort order.
     */
    public SearchFilter orderByDesc(String field) {
        sortOrders.add(field + " DESC");
        return this;
    }

    /**
     * Set pagination offset.
     */
    public SearchFilter offset(int offset) {
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be >= 0");
        }
        this.offset = offset;
        return this;
    }

    /**
     * Set pagination limit.
     */
    public SearchFilter limit(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be > 0");
        }
        this.limit = limit;
        return this;
    }

    /**
     * Build and return the filter.
     */
    public SearchFilter build() {
        return this;
    }

    // Getters for query building

    public List<Criterion> getCriteria() {
        return Collections.unmodifiableList(criteria);
    }

    public List<String> getSortOrders() {
        return Collections.unmodifiableList(sortOrders);
    }

    public int getOffset() {
        return offset;
    }

    public int getLimit() {
        return limit;
    }

    public boolean hasCriteria() {
        return !criteria.isEmpty();
    }

    public boolean hasSortOrders() {
        return !sortOrders.isEmpty();
    }

    // Internal classes

    public record Criterion(String field, Operator operator, Object value) {}

    public enum Operator {
        EQ("="),
        NE("!="),
        GT(">"),
        GTE(">="),
        LT("<"),
        LTE("<="),
        IN("IN"),
        LIKE("LIKE"),
        IS_NULL("IS NULL"),
        IS_NOT_NULL("IS NOT NULL");

        private final String sql;

        Operator(String sql) {
            this.sql = sql;
        }

        public String toSql() {
            return sql;
        }
    }

    // SQL Generation helpers

    /**
     * Generate WHERE clause SQL.
     */
    public String toWhereClause(Function<String, String> columnMapper) {
        if (criteria.isEmpty()) {
            return "";
        }

        return criteria.stream()
                .map(c -> formatCriterion(c, columnMapper))
                .collect(Collectors.joining(" AND "));
    }

    /**
     * Generate ORDER BY clause SQL.
     */
    public String toOrderByClause() {
        if (sortOrders.isEmpty()) {
            return "";
        }
        return "ORDER BY " + String.join(", ", sortOrders);
    }

    /**
     * Generate LIMIT/OFFSET clause SQL.
     */
    public String toLimitClause() {
        return "LIMIT " + limit + " OFFSET " + offset;
    }

    private String formatCriterion(Criterion c, Function<String, String> columnMapper) {
        String column = columnMapper.apply(c.field);

        return switch (c.operator) {
            case EQ -> column + " = ?";
            case NE -> column + " != ?";
            case GT -> column + " > ?";
            case GTE -> column + " >= ?";
            case LT -> column + " < ?";
            case LTE -> column + " <= ?";
            case IN -> column + " IN (" + placeholders(((Collection<?>) c.value).size()) + ")";
            case LIKE -> column + " LIKE ?";
            case IS_NULL -> column + " IS NULL";
            case IS_NOT_NULL -> column + " IS NOT NULL";
        };
    }

    private String placeholders(int count) {
        return String.join(", ", Collections.nCopies(count, "?"));
    }
}
