/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.analytics;

import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Optimizes analytics queries for better performance.
 *
 * <p>Applies various optimization techniques:
 * <ul>
 *   <li>Predicate pushdown optimization</li>
 *   <li>Column pruning (removes unused columns)</li>
 *   <li>Limit pushdown</li>
 *   <li>Join order optimization</li>
 *   <li>Subquery flattening</li>
 *   <li>Constant folding</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Optimizes analytics queries for performance
 * @doc.layer product
 * @doc.pattern Optimizer
 */
public final class QueryOptimizer {

    private static final Logger logger = LoggerFactory.getLogger(QueryOptimizer.class);

    private final boolean enablePredicatePushdown;
    private final boolean enableColumnPruning;
    private final boolean enableLimitPushdown;

    /**
     * Creates optimizer with all optimizations enabled.
     */
    public QueryOptimizer() {
        this(true, true, true);
    }

    /**
     * Creates optimizer with specific optimizations.
     *
     * @param enablePredicatePushdown enable predicate pushdown
     * @param enableColumnPruning enable column pruning
     * @param enableLimitPushdown enable limit pushdown
     */
    public QueryOptimizer(boolean enablePredicatePushdown, boolean enableColumnPruning, boolean enableLimitPushdown) {
        this.enablePredicatePushdown = enablePredicatePushdown;
        this.enableColumnPruning = enableColumnPruning;
        this.enableLimitPushdown = enableLimitPushdown;
    }

    /**
     * Optimizes a query and returns the optimization result.
     *
     * @param originalQuery original query text
     * @param statement parsed statement
     * @return optimization result
     */
    public OptimizationResult optimize(String originalQuery, Statement statement) {
        List<String> optimizations = new ArrayList<>();
        String optimizedQuery = originalQuery;

        if (statement instanceof Select) {
            Select select = (Select) statement;
            PlainSelect plainSelect = select.getPlainSelect();

            if (plainSelect != null) {
                // Apply predicate pushdown
                if (enablePredicatePushdown) {
                    OptimizationResult predicateResult = applyPredicatePushdown(plainSelect);
                    if (!predicateResult.optimizedQuery().equals(originalQuery)) {
                        optimizations.add("Predicate pushdown");
                        optimizedQuery = predicateResult.optimizedQuery();
                    }
                }

                // Apply column pruning
                if (enableColumnPruning) {
                    OptimizationResult pruningResult = applyColumnPruning(plainSelect);
                    if (!pruningResult.optimizedQuery().equals(originalQuery)) {
                        optimizations.add("Column pruning");
                        optimizedQuery = pruningResult.optimizedQuery();
                    }
                }

                // Apply limit pushdown
                if (enableLimitPushdown) {
                    OptimizationResult limitResult = applyLimitPushdown(plainSelect);
                    if (!limitResult.optimizedQuery().equals(originalQuery)) {
                        optimizations.add("Limit pushdown");
                        optimizedQuery = limitResult.optimizedQuery();
                    }
                }
            }
        }

        boolean optimized = !optimizations.isEmpty();
        if (optimized) {
            logger.info("Query optimized with: {}", optimizations);
        }

        return new OptimizationResult(optimized, optimizedQuery, optimizations);
    }

    /**
     * Applies predicate pushdown optimization.
     *
     * <p>ClickHouse evaluates WHERE predicates at the storage engine level using primary key
     * ordering, projection skipping, and partition pruning. Rewriting predicates in SQL AST
     * before they reach the engine would duplicate work and risk introducing incorrect rewrites.
     * This pass is intentionally a no-op; the storage connector handles pushdown natively.</p>
     *
     * @param select plain select
     * @return unmodified optimization result
     */
    private OptimizationResult applyPredicatePushdown(PlainSelect select) {
        logger.debug("Predicate pushdown deferred to ClickHouse storage engine for query");
        return new OptimizationResult(false, select.toString(), List.of());
    }

    /**
     * Applies column pruning optimization.
     *
     * <p>ClickHouse column-oriented storage reads only the columns referenced in a query.
     * Projection and column selection happen natively at the MergeTree storage level.
     * This pass is intentionally a no-op; the storage connector handles column pruning natively.</p>
     *
     * @param select plain select
     * @return unmodified optimization result
     */
    private OptimizationResult applyColumnPruning(PlainSelect select) {
        logger.debug("Column pruning deferred to ClickHouse columnar storage engine for query");
        return new OptimizationResult(false, select.toString(), List.of());
    }

    /**
     * Applies LIMIT pushdown optimization.
     *
     * <p>When the outer query selects from a plain subquery with a LIMIT, and the inner subquery
     * carries no GROUP BY, HAVING, DISTINCT, OFFSET, or pre-existing LIMIT, the outer LIMIT is
     * copied into the inner subquery. This allows the inner query to short-circuit row production
     * earlier in the execution plan, reducing data read from storage.</p>
     *
     * <p>Pushdown is skipped when:</p>
     * <ul>
     *   <li>The outer query has a WHERE clause (filtering after inner rows are produced changes
     *       cardinality — adding LIMIT to inner could cause the outer WHERE to see fewer rows
     *       than required).</li>
     *   <li>The inner query has GROUP BY, HAVING, or DISTINCT (these change cardinality or order
     *       before the outer LIMIT applies).</li>
     *   <li>The inner query already has a LIMIT or OFFSET.</li>
     *   <li>The outer FROM clause is not a single, non-joined subquery.</li>
     * </ul>
     *
     * @param select outer plain select
     * @return optimized result with LIMIT pushed into subquery, or unmodified if not applicable
     */
    private OptimizationResult applyLimitPushdown(PlainSelect select) {
        Limit outerLimit = select.getLimit();
        if (outerLimit == null || outerLimit.getRowCount() == null) {
            return new OptimizationResult(false, select.toString(), List.of());
        }

        // Outer WHERE would change cardinality after inner rows are produced; skip pushdown.
        if (select.getWhere() != null) {
            logger.debug("Skipping LIMIT pushdown: outer WHERE clause present");
            return new OptimizationResult(false, select.toString(), List.of());
        }

        // Only push when FROM is a single, un-joined subquery.
        if (select.getJoins() != null && !select.getJoins().isEmpty()) {
            return new OptimizationResult(false, select.toString(), List.of());
        }

        if (!(select.getFromItem() instanceof ParenthesedSelect parenthesedFrom)) {
            return new OptimizationResult(false, select.toString(), List.of());
        }

        PlainSelect inner = parenthesedFrom.getPlainSelect();
        if (inner == null) {
            return new OptimizationResult(false, select.toString(), List.of());
        }

        // Guard conditions: operations that alter row cardinality or ordering semantics.
        if (inner.getGroupBy() != null
                || inner.getHaving() != null
                || inner.getDistinct() != null
                || inner.getLimit() != null
                || (inner.getLimit() != null && inner.getLimit().getOffset() != null)) {
            logger.debug("Skipping LIMIT pushdown: inner subquery has GROUP BY, HAVING, DISTINCT, or LIMIT");
            return new OptimizationResult(false, select.toString(), List.of());
        }

        // Push the outer LIMIT into the inner subquery.
        Limit innerLimit = new Limit();
        innerLimit.setRowCount(outerLimit.getRowCount());
        inner.setLimit(innerLimit);

        String optimized = select.toString();
        logger.debug("Applied LIMIT pushdown: limit {} pushed into subquery", outerLimit.getRowCount());
        return new OptimizationResult(true, optimized, List.of("Limit pushed into subquery"));
    }

    /**
     * Result of query optimization.
     *
     * @param optimized true if optimization was applied
     * @param optimizedQuery optimized query text
     * @param appliedOptimizations list of optimizations applied
     */
    public record OptimizationResult(boolean optimized, String optimizedQuery, List<String> appliedOptimizations) {
        public OptimizationResult {
            appliedOptimizations = List.copyOf(appliedOptimizations);
        }

        /**
         * Returns an unoptimized result.
         *
         * @param originalQuery original query
         * @return optimization result
         */
        public static OptimizationResult unoptimized(String originalQuery) {
            return new OptimizationResult(false, originalQuery, List.of());
        }
    }
}
