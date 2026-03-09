package com.ghatana.datacloud.application.query;

import com.ghatana.datacloud.entity.MetaCollection;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static com.ghatana.platform.observability.util.BlockingExecutors.blockingExecutor;

/**
 * Advanced query builder with optimizations.
 *
 * <p><b>Purpose</b><br>
 * Builds optimized SQL/JSONB queries with:
 * - Advanced operators (IN, BETWEEN, LIKE, EXISTS)
 * - Query plan optimization
 * - Multi-field sorting
 * - Index usage detection
 *
 * <p><b>Features</b><br>
 * - Complex filter operators
 * - Query optimization hints
 * - Execution plan generation
 * - Performance estimation
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * AdvancedQueryBuilder builder = new AdvancedQueryBuilder(collection);
 *
 * builder.filter("price", "IN", Arrays.asList(10, 20, 30))
 *        .filter("date", "BETWEEN", new Object[]{startDate, endDate})
 *        .sort("name", "ASC", "secondary")
 *        .sort("price", "DESC", "tertiary")
 *        .withOptimization(true)
 *        .build();
 * }</pre>
 *
 * @doc.type service
 * @doc.purpose Advanced query building with optimization
 * @doc.layer application
 * @doc.pattern Builder
 */
public class AdvancedQueryBuilder {

    private static final Logger logger = LoggerFactory.getLogger(AdvancedQueryBuilder.class);

    private final MetaCollection collection;
    private final List<FilterExpression> filters = new ArrayList<>();
    private final List<SortExpression> sorts = new ArrayList<>();
    private boolean optimizationEnabled = false;
    private int limit = 100;
    private int offset = 0;
    private QueryPlan cachedPlan;

    /**
     * Creates a new advanced query builder.
     *
     * @param collection the collection schema (required)
     */
    public AdvancedQueryBuilder(MetaCollection collection) {
        this.collection = Objects.requireNonNull(collection, "collection cannot be null");
    }

    /**
     * Add a filter expression with advanced operators.
     *
     * @param field the field name
     * @param operator the operator (=, >, <, IN, BETWEEN, LIKE, EXISTS, NOT IN)
     * @param value the filter value
     * @return this builder
     */
    public AdvancedQueryBuilder filter(String field, String operator, Object value) {
        if (!isValidOperator(operator)) {
            throw new IllegalArgumentException("Invalid operator: " + operator);
        }
        filters.add(new FilterExpression(field, operator, value));
        logger.debug("Added filter: {} {} {}", field, operator, value);
        return this;
    }

    /**
     * Add IN operator filter (array membership).
     *
     * @param field the field name
     * @param values the values to match
     * @return this builder
     */
    public AdvancedQueryBuilder filterIn(String field, Collection<?> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("IN filter values cannot be empty");
        }
        filters.add(new FilterExpression(field, "IN", new ArrayList<>(values)));
        logger.debug("Added IN filter: {} IN {}", field, values.size());
        return this;
    }

    /**
     * Add BETWEEN operator filter (range query).
     *
     * @param field the field name
     * @param start the range start (inclusive)
     * @param end the range end (inclusive)
     * @return this builder
     */
    public AdvancedQueryBuilder filterBetween(String field, Object start, Object end) {
        if (start == null || end == null) {
            throw new IllegalArgumentException("BETWEEN boundaries cannot be null");
        }
        filters.add(new FilterExpression(field, "BETWEEN", new Object[]{start, end}));
        logger.debug("Added BETWEEN filter: {} BETWEEN {} AND {}", field, start, end);
        return this;
    }

    /**
     * Add LIKE operator filter (pattern matching).
     *
     * @param field the field name
     * @param pattern the pattern (use % for wildcards)
     * @return this builder
     */
    public AdvancedQueryBuilder filterLike(String field, String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            throw new IllegalArgumentException("LIKE pattern cannot be empty");
        }
        filters.add(new FilterExpression(field, "LIKE", pattern));
        logger.debug("Added LIKE filter: {} LIKE {}", field, pattern);
        return this;
    }

    /**
     * Add primary sort field.
     *
     * @param field the field name
     * @param direction ASC or DESC
     * @return this builder
     */
    public AdvancedQueryBuilder sort(String field, String direction) {
        if (!isValidDirection(direction)) {
            throw new IllegalArgumentException("Invalid direction: " + direction);
        }
        sorts.add(new SortExpression(field, direction, "primary"));
        logger.debug("Added sort: {} {}", field, direction);
        return this;
    }

    /**
     * Add secondary sort field.
     *
     * @param field the field name
     * @param direction ASC or DESC
     * @return this builder
     */
    public AdvancedQueryBuilder thenSort(String field, String direction) {
        if (!isValidDirection(direction)) {
            throw new IllegalArgumentException("Invalid direction: " + direction);
        }
        String priority;
        switch (sorts.size()) {
            case 0, 1 -> priority = "secondary";
            case 2 -> priority = "tertiary";
            default -> priority = "quaternary";
        }
        sorts.add(new SortExpression(field, direction, priority));
        logger.debug("Added {} sort: {} {}", priority, field, direction);
        return this;
    }

    /**
     * Enable query optimization.
     *
     * @param enabled true to enable optimization
     * @return this builder
     */
    public AdvancedQueryBuilder withOptimization(boolean enabled) {
        this.optimizationEnabled = enabled;
        logger.debug("Query optimization: {}", enabled ? "enabled" : "disabled");
        return this;
    }

    /**
     * Set result limit.
     *
     * @param limit the maximum number of results
     * @return this builder
     */
    public AdvancedQueryBuilder limit(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("Limit must be positive");
        }
        this.limit = limit;
        return this;
    }

    /**
     * Set result offset (pagination).
     *
     * @param offset the number of results to skip
     * @return this builder
     */
    public AdvancedQueryBuilder offset(int offset) {
        if (offset < 0) {
            throw new IllegalArgumentException("Offset cannot be negative");
        }
        this.offset = offset;
        return this;
    }

    /**
     * Build and optimize the query.
     *
     * @return query plan
     */
    public QueryPlan build() {
        logger.info("Building query with {} filters and {} sorts",
                filters.size(), sorts.size());

        // Check cache
        if (cachedPlan != null && optimizationEnabled) {
            logger.debug("Using cached query plan");
            return cachedPlan;
        }

        // Build query plan
        QueryPlan plan = new QueryPlan(
                UUID.randomUUID().toString(),
                filters,
                sorts,
                limit,
                offset,
                optimizationEnabled ? generateOptimizationHints() : null
        );

        // Cache if optimization enabled
        if (optimizationEnabled) {
            this.cachedPlan = plan;
        }

        logger.info("Query plan built: {} (optimized: {})",
                plan.id(), optimizationEnabled);

        return plan;
    }

    /**
     * Build and execute the query.
     *
     * @return promise of query results
     */
    public Promise<QueryResults> buildAndExecute() {
        QueryPlan plan = build();
        return executeQuery(plan);
    }

    /**
     * Generate optimization hints.
     *
     * @return optimization hints
     */
    private OptimizationHints generateOptimizationHints() {
        Set<String> indexedFields = detectIndexedFields();
        List<String> executionOrder = optimizeFilterOrder();
        int estimatedCost = estimateQueryCost();

        return new OptimizationHints(
                indexedFields,
                executionOrder,
                estimatedCost
        );
    }

    /**
     * Detect which fields might have indexes.
     *
     * @return set of potentially indexed fields
     */
    private Set<String> detectIndexedFields() {
        Set<String> indexed = new HashSet<>();
        // In production, this would query database metadata
        // For now, assume common fields are indexed
        indexed.add("id");
        indexed.add("created_at");
        indexed.add("updated_at");
        return indexed;
    }

    /**
     * Optimize the order of filter evaluation.
     *
     * @return optimized filter order
     */
    private List<String> optimizeFilterOrder() {
        List<String> order = new ArrayList<>();
        // Most selective filters first
        filters.stream()
                .filter(f -> "IN".equals(f.operator) || "=".equals(f.operator))
                .map(f -> f.field)
                .forEach(order::add);
        filters.stream()
                .filter(f -> !"IN".equals(f.operator) && !"=".equals(f.operator))
                .map(f -> f.field)
                .forEach(order::add);
        return order;
    }

    /**
     * Estimate query execution cost.
     *
     * @return estimated cost (lower is better)
     */
    private int estimateQueryCost() {
        int cost = 0;
        // Cost increases with filters
        cost += filters.size() * 10;
        // Cost increases with sorts
        cost += sorts.size() * 5;
        // Cost increases with result size
        cost += Math.log(limit);
        return cost;
    }

    /**
     * Check if operator is valid.
     *
     * @param operator the operator to check
     * @return true if valid
     */
    private boolean isValidOperator(String operator) {
        Set<String> validOps = Set.of(
                "=", ">", "<", ">=", "<=", "!=",
                "IN", "NOT IN", "BETWEEN", "LIKE", "ILIKE", "EXISTS"
        );
        return validOps.contains(operator);
    }

    /**
     * Check if sort direction is valid.
     *
     * @param direction the direction to check
     * @return true if valid
     */
    private boolean isValidDirection(String direction) {
        return "ASC".equalsIgnoreCase(direction) || "DESC".equalsIgnoreCase(direction);
    }

    /**
     * Execute the query with the given plan.
     *
     * @param plan the query plan
     * @return promise of results
     */
    private Promise<QueryResults> executeQuery(QueryPlan plan) {
        return Promise.ofBlocking(blockingExecutor(), () -> {
            logger.info("Executing optimized query: {}", plan.id());
            // Placeholder - in production this would execute SQL/JSONB query
            return new QueryResults(plan.id(), Collections.emptyList(), 0, plan.limit);
        });
    }

    /**
     * Filter expression.
     */
    public static class FilterExpression {
        public final String field;
        public final String operator;
        public final Object value;

        public FilterExpression(String field, String operator, Object value) {
            this.field = Objects.requireNonNull(field);
            this.operator = Objects.requireNonNull(operator);
            this.value = Objects.requireNonNull(value);
        }
    }

    /**
     * Sort expression.
     */
    public static class SortExpression {
        public final String field;
        public final String direction;
        public final String priority;

        public SortExpression(String field, String direction, String priority) {
            this.field = Objects.requireNonNull(field);
            this.direction = Objects.requireNonNull(direction);
            this.priority = Objects.requireNonNull(priority);
        }
    }

    /**
     * Query plan.
     */
    public record QueryPlan(
            String id,
            List<FilterExpression> filters,
            List<SortExpression> sorts,
            int limit,
            int offset,
            OptimizationHints hints
    ) {}

    /**
     * Optimization hints.
     */
    public record OptimizationHints(
            Set<String> indexedFields,
            List<String> executionOrder,
            int estimatedCost
    ) {}

    /**
     * Query results.
     */
    public record QueryResults(
            String queryId,
            List<Map<String, Object>> rows,
            int totalCount,
            int limit
    ) {}
}

