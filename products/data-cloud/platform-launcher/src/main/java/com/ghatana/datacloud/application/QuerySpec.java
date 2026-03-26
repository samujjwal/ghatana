package com.ghatana.datacloud.application;

import java.util.Map;
import java.util.Objects;

/**
 * Immutable specification for a parameterized database query.
 *
 * <p><b>Purpose</b><br>
 * Represents a complete query specification with SQL, parameters, and pagination info.
 * Created by `DynamicQueryBuilder` and executed by repository layer.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * DynamicQueryBuilder builder = new DynamicQueryBuilder(collection, fields);
 * QuerySpec spec = builder
 *     .select()
 *     .filter("status", "ACTIVE")
 *     .sort("name", "ASC")
 *     .paginate(0, 50)
 *     .build();
 *
 * String sql = spec.sql();
 * Map<String, Object> params = spec.parameters();
 * int offset = spec.offset();
 * int limit = spec.limit();
 * }</pre>
 *
 * <p><b>SQL Injection Prevention</b><br>
 * All parameters are parameterized (never concatenated into SQL).
 * Field names are validated against collection schema.
 * Operators are validated against whitelist.
 *
 * <p><b>Thread Safety</b><br>
 * Immutable record - thread-safe.
 *
 * @param sql the parameterized SQL query (required, must not contain user input)
 * @param parameters the query parameters (required, can be empty)
 * @param offset the pagination offset (0-based, required)
 * @param limit the pagination limit (max results, required)
 * @param sort the sort expression (optional, can be null)
 *
 * @see DynamicQueryBuilder
 * @see com.ghatana.datacloud.entity.EntityRepository
 * @doc.type record
 * @doc.purpose Immutable query specification for repository execution
 * @doc.layer product
 * @doc.pattern Value Object (Application Layer)
 */
public record QuerySpec(
    String sql,
    Map<String, Object> parameters,
    int offset,
    int limit,
    String sort
) {
    /**
     * Creates a new QuerySpec with validation.
     *
     * <p>Validates that SQL and parameters are not null, and pagination values are valid.
     *
     * @param sql the parameterized SQL query (required)
     * @param parameters the query parameters (required, can be empty)
     * @param offset the pagination offset (0-based, required)
     * @param limit the pagination limit (max results, required)
     * @param sort the sort expression (optional, can be null)
     * @throws NullPointerException if sql or parameters is null
     * @throws IllegalArgumentException if offset < 0 or limit <= 0
     */
    public QuerySpec {
        Objects.requireNonNull(sql, "SQL must not be null");
        Objects.requireNonNull(parameters, "Parameters must not be null");
        
        if (offset < 0) {
            throw new IllegalArgumentException("Offset must be >= 0, got: " + offset);
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("Limit must be > 0, got: " + limit);
        }
        if (limit > 10000) {
            throw new IllegalArgumentException("Limit must be <= 10000, got: " + limit);
        }
    }

    /**
     * Gets the SQL query.
     *
     * @return the parameterized SQL query
     */
    @Override
    public String sql() {
        return sql;
    }

    /**
     * Gets the query parameters.
     *
     * @return the parameters map (immutable)
     */
    @Override
    public Map<String, Object> parameters() {
        return Map.copyOf(parameters);
    }

    /**
     * Gets the pagination offset.
     *
     * @return the offset (0-based)
     */
    @Override
    public int offset() {
        return offset;
    }

    /**
     * Gets the pagination limit.
     *
     * @return the limit (max results)
     */
    @Override
    public int limit() {
        return limit;
    }

    /**
     * Gets the sort expression.
     *
     * @return the sort expression (null if not specified)
     */
    @Override
    public String sort() {
        return sort;
    }

    /**
     * Creates a QuerySpec with default pagination (offset 0, limit 50).
     *
     * @param sql the SQL query
     * @param parameters the parameters
     * @return QuerySpec with default pagination
     */
    public static QuerySpec of(String sql, Map<String, Object> parameters) {
        return new QuerySpec(sql, parameters, 0, 50, null);
    }

    /**
     * Creates a QuerySpec with custom pagination.
     *
     * @param sql the SQL query
     * @param parameters the parameters
     * @param offset the offset
     * @param limit the limit
     * @return QuerySpec with custom pagination
     */
    public static QuerySpec of(String sql, Map<String, Object> parameters, int offset, int limit) {
        return new QuerySpec(sql, parameters, offset, limit, null);
    }

    /**
     * Creates a QuerySpec with all fields.
     *
     * @param sql the SQL query
     * @param parameters the parameters
     * @param offset the offset
     * @param limit the limit
     * @param sort the sort expression
     * @return QuerySpec with all fields
     */
    public static QuerySpec of(String sql, Map<String, Object> parameters, int offset, int limit, String sort) {
        return new QuerySpec(sql, parameters, offset, limit, sort);
    }
}
