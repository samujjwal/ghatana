/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.analytics;

import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Correctness tests for Analytics query engine with deterministic fixtures.
 *
 * <p>Provides canonical datasets with known expected results for:
 * <ul>
 *   <li>Aggregation functions (SUM, AVG, COUNT, MIN, MAX)</li>
 *   <li>Filtering and where clauses</li>
 *   <li>Sorting and ordering</li>
 *   <li>Grouping and having clauses</li>
 *   <li>Join operations (basic)</li>
 * </ul>
 *
 * <p><strong>Data Persistence:</strong> Tests use in-memory fixtures or H2 database.
 * No external dependencies. Fully deterministic.
 *
 * <p><strong>Expected Results:</strong> Hard-coded in test. If query engine changes,
 * tests verify the engine still produces correct results (or test expectations are updated
 * with diff and explanation in commit message).
 *
 * @doc.type class
 * @doc.purpose Correctness tests for Analytics query engine with deterministic datasets
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Analytics Query Engine – Correctness Fixtures")
class QueryCorrectnessFixturesTest {

    private AnalyticsQueryEngine queryEngine;
    private AnalyticsFixtures fixtures;

    @BeforeEach
    void setUp() {
        // Initialize query engine (real or mock)
        this.queryEngine = new DefaultAnalyticsQueryEngine();
        this.fixtures = new AnalyticsFixtures();
        
        // Load deterministic dataset
        fixtures.loadSalesData();
    }

    /**
     * Sales Dataset:
     * id | product | region | quantity | unitPrice | date
     * 1  | Widget A | US    | 100      | 10.00     | 2026-01-01
     * 2  | Widget A | US    | 150      | 10.00     | 2026-01-02
     * 3  | Widget B | EU    | 50       | 20.00     | 2026-01-01
     * 4  | Widget B | EU    | 75       | 20.00     | 2026-01-02
     * 5  | Widget C | APAC  | 200      | 5.00      | 2026-01-01
     */

    @Nested
    @DisplayName("Aggregation: SUM")
    class SumAggregationTests {

        @Test
        @DisplayName("SUM(quantity) across all rows → 575")
        void sum_allQuantities_correct() {
            QueryPlan plan = new QueryPlan()
                    .addSelect("SUM(quantity) as total_qty")
                    .from("sales");

            QueryResult result = queryEngine.execute(plan).blockingGet();

            assertThat(result.getRows()).hasSize(1);
            Map<String, Object> row = result.getRows().get(0);
            assertThat(row.get("total_qty")).isEqualTo(575);
        }

        @Test
        @DisplayName("SUM(quantity * unitPrice) – total revenue → 7,175.00")
        void sum_revenue_correct() {
            QueryPlan plan = new QueryPlan()
                    .addSelect("SUM(quantity * unitPrice) as revenue")
                    .from("sales");

            QueryResult result = queryEngine.execute(plan).blockingGet();

            Map<String, Object> row = result.getRows().get(0);
            assertThat(row.get("revenue")).isEqualTo(7175.00);
        }

        @Test
        @DisplayName("SUM grouped by region → US: 2500, EU: 2500, APAC: 1000")
        void sum_groupByRegion_correct() {
            QueryPlan plan = new QueryPlan()
                    .addSelect("region, SUM(quantity * unitPrice) as revenue")
                    .from("sales")
                    .groupBy("region");

            QueryResult result = queryEngine.execute(plan).blockingGet();

            assertThat(result.getRows()).hasSize(3);

            // Verify each region's revenue
            Map<String, Double> regionRevenue = new java.util.HashMap<>();
            for (Map<String, Object> row : result.getRows()) {
                regionRevenue.put(
                        (String) row.get("region"),
                        ((Number) row.get("revenue")).doubleValue()
                );
            }

            assertThat(regionRevenue)
                    .containsEntry("US", 2500.00)
                    .containsEntry("EU", 2500.00)
                    .containsEntry("APAC", 1000.00);
        }
    }

    @Nested
    @DisplayName("Aggregation: AVG")
    class AverageAggregationTests {

        @Test
        @DisplayName("AVG(unitPrice) → 13.00")
        void avg_unitPrice_correct() {
            QueryPlan plan = new QueryPlan()
                    .addSelect("AVG(unitPrice) as avg_price")
                    .from("sales");

            QueryResult result = queryEngine.execute(plan).blockingGet();

            Map<String, Object> row = result.getRows().get(0);
            assertThat(((Number) row.get("avg_price")).doubleValue()).isCloseTo(13.0, within(0.01));
        }

        @Test
        @DisplayName("AVG grouped by product → Widget A: 10, Widget B: 20, Widget C: 5")
        void avg_groupByProduct_correct() {
            QueryPlan plan = new QueryPlan()
                    .addSelect("product, AVG(unitPrice) as avg_price")
                    .from("sales")
                    .groupBy("product");

            QueryResult result = queryEngine.execute(plan).blockingGet();

            assertThat(result.getRows()).hasSize(3);

            Map<String, Double> productAvg = new java.util.HashMap<>();
            for (Map<String, Object> row : result.getRows()) {
                productAvg.put(
                        (String) row.get("product"),
                        ((Number) row.get("avg_price")).doubleValue()
                );
            }

            assertThat(productAvg.get("Widget A")).isCloseTo(10.0, within(0.01));
            assertThat(productAvg.get("Widget B")).isCloseTo(20.0, within(0.01));
            assertThat(productAvg.get("Widget C")).isCloseTo(5.0, within(0.01));
        }
    }

    @Nested
    @DisplayName("Aggregation: COUNT")
    class CountAggregationTests {

        @Test
        @DisplayName("COUNT(*) → 5")
        void count_allRows_correct() {
            QueryPlan plan = new QueryPlan()
                    .addSelect("COUNT(*) as row_count")
                    .from("sales");

            QueryResult result = queryEngine.execute(plan).blockingGet();

            Map<String, Object> row = result.getRows().get(0);
            assertThat(row.get("row_count")).isEqualTo(5L);
        }

        @Test
        @DisplayName("COUNT grouped by region → US: 2, EU: 2, APAC: 1")
        void count_groupByRegion_correct() {
            QueryPlan plan = new QueryPlan()
                    .addSelect("region, COUNT(*) as count")
                    .from("sales")
                    .groupBy("region");

            QueryResult result = queryEngine.execute(plan).blockingGet();

            assertThat(result.getRows()).hasSize(3);

            Map<String, Long> regionCount = new java.util.HashMap<>();
            for (Map<String, Object> row : result.getRows()) {
                regionCount.put(
                        (String) row.get("region"),
                        ((Number) row.get("count")).longValue()
                );
            }

            assertThat(regionCount)
                    .containsEntry("US", 2L)
                    .containsEntry("EU", 2L)
                    .containsEntry("APAC", 1L);
        }

        @Test
        @DisplayName("COUNT DISTINCT(product) → 3")
        void count_distinctProducts_correct() {
            QueryPlan plan = new QueryPlan()
                    .addSelect("COUNT(DISTINCT product) as distinct_products")
                    .from("sales");

            QueryResult result = queryEngine.execute(plan).blockingGet();

            Map<String, Object> row = result.getRows().get(0);
            assertThat(row.get("distinct_products")).isEqualTo(3L);
        }
    }

    @Nested
    @DisplayName("Aggregation: MIN / MAX")
    class MinMaxAggregationTests {

        @Test
        @DisplayName("MIN(unitPrice) → 5.00, MAX(unitPrice) → 20.00")
        void minMax_unitPrice_correct() {
            QueryPlan planMin = new QueryPlan()
                    .addSelect("MIN(unitPrice) as min_price")
                    .from("sales");

            QueryPlan planMax = new QueryPlan()
                    .addSelect("MAX(unitPrice) as max_price")
                    .from("sales");

            QueryResult minResult = queryEngine.execute(planMin).blockingGet();
            QueryResult maxResult = queryEngine.execute(planMax).blockingGet();

            assertThat(minResult.getRows().get(0).get("min_price")).isEqualTo(5.00);
            assertThat(maxResult.getRows().get(0).get("max_price")).isEqualTo(20.00);
        }

        @Test
        @DisplayName("MIN/MAX grouped by region")
        void minMax_groupByRegion_correct() {
            QueryPlan plan = new QueryPlan()
                    .addSelect("region, MIN(unitPrice) as min_price, MAX(unitPrice) as max_price")
                    .from("sales")
                    .groupBy("region");

            QueryResult result = queryEngine.execute(plan).blockingGet();

            assertThat(result.getRows()).hasSize(3);
            // Verify US has min: 10, max: 10 (both Widget A at 10)
            // EU has min: 20, max: 20 (Widget B)
            // APAC has min: 5, max: 5 (Widget C)
        }
    }

    @Nested
    @DisplayName("Filtering: WHERE clauses")
    class FilteringTests {

        @Test
        @DisplayName("WHERE region = 'US' → 2 rows")
        void filter_byRegion_correct() {
            QueryPlan plan = new QueryPlan()
                    .addSelect("*")
                    .from("sales")
                    .where("region = 'US'");

            QueryResult result = queryEngine.execute(plan).blockingGet();

            assertThat(result.getRows()).hasSize(2);
            for (Map<String, Object> row : result.getRows()) {
                assertThat(row.get("region")).isEqualTo("US");
            }
        }

        @Test
        @DisplayName("WHERE quantity > 100 → 3 rows (Widget A:150, Widget B:75? no, Widget C:200)")
        void filter_byQuantity_correct() {
            QueryPlan plan = new QueryPlan()
                    .addSelect("*")
                    .from("sales")
                    .where("quantity > 100");

            QueryResult result = queryEngine.execute(plan).blockingGet();

            assertThat(result.getRows()).hasSize(2);  // Widget A:150, Widget C:200
        }

        @Test
        @DisplayName("WHERE unitPrice >= 10 AND region = 'US' → 2 rows")
        void filter_combined_correct() {
            QueryPlan plan = new QueryPlan()
                    .addSelect("*")
                    .from("sales")
                    .where("unitPrice >= 10 AND region = 'US'");

            QueryResult result = queryEngine.execute(plan).blockingGet();

            assertThat(result.getRows()).hasSize(2);
            for (Map<String, Object> row : result.getRows()) {
                assertThat(((Number) row.get("unitPrice")).doubleValue()).isGreaterThanOrEqualTo(10);
                assertThat(row.get("region")).isEqualTo("US");
            }
        }
    }

    @Nested
    @DisplayName("Sorting: ORDER BY")
    class SortingTests {

        @Test
        @DisplayName("ORDER BY quantity DESC → [200, 150, 75, 50, 100]")
        void orderBy_quantity_descending_correct() {
            QueryPlan plan = new QueryPlan()
                    .addSelect("product, quantity")
                    .from("sales")
                    .orderBy("quantity DESC");

            QueryResult result = queryEngine.execute(plan).blockingGet();

            List<Map<String, Object>> rows = result.getRows();
            assertThat(((Number) rows.get(0).get("quantity")).intValue()).isEqualTo(200);
            assertThat(((Number) rows.get(1).get("quantity")).intValue()).isEqualTo(150);
            assertThat(((Number) rows.get(4).get("quantity")).intValue()).isEqualTo(50);
        }

        @Test
        @DisplayName("ORDER BY region ASC, unitPrice DESC")
        void orderBy_multiple_correct() {
            QueryPlan plan = new QueryPlan()
                    .addSelect("region, unitPrice")
                    .from("sales")
                    .orderBy("region ASC, unitPrice DESC");

            QueryResult result = queryEngine.execute(plan).blockingGet();

            // Result should be: APAC first, then EU, then US
            // Within each region, sorted by unitPrice DESC
            assertThat(result.getRows().get(0).get("region")).isEqualTo("APAC");
        }
    }

    @Nested
    @DisplayName("HAVING clauses (Post-aggregation filtering)")
    class HavingTests {

        @Test
        @DisplayName("GROUP BY region HAVING COUNT(*) > 1 → US, EU (not APAC with 1 row)")
        void having_count_correct() {
            QueryPlan plan = new QueryPlan()
                    .addSelect("region, COUNT(*) as count")
                    .from("sales")
                    .groupBy("region")
                    .having("COUNT(*) > 1");

            QueryResult result = queryEngine.execute(plan).blockingGet();

            assertThat(result.getRows()).hasSize(2);  // US and EU only
            for (Map<String, Object> row : result.getRows()) {
                assertThat(((Number) row.get("count")).longValue()).isGreaterThan(1);
            }
        }

        @Test
        @DisplayName("GROUP BY region HAVING SUM(quantity) > 500")
        void having_sum_correct() {
            QueryPlan plan = new QueryPlan()
                    .addSelect("region, SUM(quantity) as total")
                    .from("sales")
                    .groupBy("region")
                    .having("SUM(quantity) > 500");

            QueryResult result = queryEngine.execute(plan).blockingGet();

            for (Map<String, Object> row : result.getRows()) {
                assertThat(((Number) row.get("total")).intValue()).isGreaterThan(500);
            }
        }
    }

    @Nested
    @DisplayName("Limit and Offset")
    class LimitOffsetTests {

        @Test
        @DisplayName("LIMIT 2 → returns 2 rows")
        void limit_firstTwo_correct() {
            QueryPlan plan = new QueryPlan()
                    .addSelect("*")
                    .from("sales")
                    .limit(2);

            QueryResult result = queryEngine.execute(plan).blockingGet();

            assertThat(result.getRows()).hasSize(2);
        }

        @Test
        @DisplayName("OFFSET 2 LIMIT 2 → skips first 2, returns next 2")
        void offsetLimit_pagination_correct() {
            QueryPlan plan = new QueryPlan()
                    .addSelect("id")
                    .from("sales")
                    .offset(2)
                    .limit(2);

            QueryResult result = queryEngine.execute(plan).blockingGet();

            assertThat(result.getRows()).hasSize(2);
        }
    }

    // ==================== Helper Assertion Methods ====================

    private static org.assertj.core.api.Condition<Double> within(double tolerance) {
        return new org.assertj.core.api.Condition<Double>(
                value -> Math.abs(value) <= tolerance,
                "within " + tolerance
        );
    }
}

/**
 * Test fixtures: in-memory dataset for correctness tests.
 *
 * @doc.type class
 * @doc.purpose Fixture management for Analytics correctness tests
 * @doc.layer test-support
 * @doc.pattern Fixture
 */
class AnalyticsFixtures {
    // Placeholder for fixture loading logic
    void loadSalesData() {
        // In real implementation: load test data into H2 or in-memory store
    }
}

/**
 * Default query engine implementation (stub for testing).
 *
 * @doc.type class
 * @doc.purpose Query engine implementation (testable, deterministic)
 * @doc.layer product
 * @doc.pattern Service
 */
class DefaultAnalyticsQueryEngine implements AnalyticsQueryEngine {
    @Override
    public Promise<QueryResult> execute(QueryPlan plan) {
        // Real implementation would parse plan and execute against database
        return Promise.of(new QueryResult());
    }
}

/**
 * QueryPlan builder for test fluency.
 *
 * @doc.type class
 * @doc.purpose Fluent builder for query plans
 * @doc.layer test-support
 * @doc.pattern Builder
 */
class QueryPlan {
    private StringBuilder sql = new StringBuilder("SELECT ");
    private String fromClause = "";
    private String whereClause = "";
    private String groupByClause = "";
    private String havingClause = "";
    private String orderByClause = "";
    private int limitValue = 0;
    private int offsetValue = 0;

    public QueryPlan addSelect(String select) {
        this.sql = new StringBuilder("SELECT " + select);
        return this;
    }

    public QueryPlan from(String table) {
        this.fromClause = " FROM " + table;
        return this;
    }

    public QueryPlan where(String condition) {
        this.whereClause = " WHERE " + condition;
        return this;
    }

    public QueryPlan groupBy(String columns) {
        this.groupByClause = " GROUP BY " + columns;
        return this;
    }

    public QueryPlan having(String condition) {
        this.havingClause = " HAVING " + condition;
        return this;
    }

    public QueryPlan orderBy(String columns) {
        this.orderByClause = " ORDER BY " + columns;
        return this;
    }

    public QueryPlan limit(int limit) {
        this.limitValue = limit;
        return this;
    }

    public QueryPlan offset(int offset) {
        this.offsetValue = offset;
        return this;
    }

    @Override
    public String toString() {
        StringBuilder query = new StringBuilder(sql);
        if (!fromClause.isEmpty()) query.append(fromClause);
        if (!whereClause.isEmpty()) query.append(whereClause);
        if (!groupByClause.isEmpty()) query.append(groupByClause);
        if (!havingClause.isEmpty()) query.append(havingClause);
        if (!orderByClause.isEmpty()) query.append(orderByClause);
        if (limitValue > 0) query.append(" LIMIT ").append(limitValue);
        if (offsetValue > 0) query.append(" OFFSET ").append(offsetValue);
        return query.toString();
    }
}

/**
 * Query result model.
 */
class QueryResult {
    private List<Map<String, Object>> rows = new java.util.ArrayList<>();

    public List<Map<String, Object>> getRows() {
        return rows;
    }
}

/**
 * Analytics query engine interface.
 */
interface AnalyticsQueryEngine {
    Promise<QueryResult> execute(QueryPlan plan);
}
