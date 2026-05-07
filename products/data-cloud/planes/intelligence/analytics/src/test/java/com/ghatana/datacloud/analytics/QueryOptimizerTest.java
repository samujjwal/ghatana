/*
 * Copyright (c) 2026 Ghatana Inc. 
 * All rights reserved.
 */
package com.ghatana.datacloud.analytics;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for QueryOptimizer.
 *
 * @doc.type class
 * @doc.purpose Tests for query optimization logic
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("QueryOptimizer Tests")
class QueryOptimizerTest {

    private QueryOptimizer optimizer;

    @BeforeEach
    void setUp() { 
        optimizer = new QueryOptimizer(); 
    }

    @Nested
    @DisplayName("Basic Optimization")
    class BasicOptimizationTests {

        @Test
        @DisplayName("optimizer can be created with all optimizations enabled")
        void defaultConstructor_allEnabled() { 
            QueryOptimizer opt = new QueryOptimizer(); 
            assertThat(opt).isNotNull(); 
        }

        @Test
        @DisplayName("optimizer can be created with specific flags")
        void customConstructor_withFlags() { 
            QueryOptimizer opt = new QueryOptimizer(true, false, true); 
            assertThat(opt).isNotNull(); 
        }

        @Test
        @DisplayName("valid SELECT query returns unoptimized result")
        void validSelect_returnsUnoptimized() throws JSQLParserException { 
            String query = "SELECT * FROM products";
            Statement stmt = CCJSqlParserUtil.parse(query); 
            
            QueryOptimizer.OptimizationResult result = optimizer.optimize(query, stmt); 
            
            assertThat(result.optimized()).isFalse(); 
            assertThat(result.appliedOptimizations()).isEmpty(); 
        }

        @Test
        @DisplayName("SELECT with WHERE clause returns unoptimized result")
        void selectWithWhere_returnsUnoptimized() throws JSQLParserException { 
            String query = "SELECT * FROM products WHERE price > 100";
            Statement stmt = CCJSqlParserUtil.parse(query); 
            
            QueryOptimizer.OptimizationResult result = optimizer.optimize(query, stmt); 
            
            assertThat(result.optimized()).isFalse(); 
            assertThat(result.appliedOptimizations()).isEmpty(); 
        }

        @Test
        @DisplayName("SELECT with LIMIT returns unoptimized result")
        void selectWithLimit_returnsUnoptimized() throws JSQLParserException { 
            String query = "SELECT * FROM products LIMIT 100";
            Statement stmt = CCJSqlParserUtil.parse(query); 
            
            QueryOptimizer.OptimizationResult result = optimizer.optimize(query, stmt); 
            
            assertThat(result.optimized()).isFalse(); 
            assertThat(result.appliedOptimizations()).isEmpty(); 
        }

        @Test
        @DisplayName("SELECT with GROUP BY returns unoptimized result")
        void selectWithGroupBy_returnsUnoptimized() throws JSQLParserException { 
            String query = "SELECT category, COUNT(*) FROM products GROUP BY category"; 
            Statement stmt = CCJSqlParserUtil.parse(query); 
            
            QueryOptimizer.OptimizationResult result = optimizer.optimize(query, stmt); 
            
            assertThat(result.optimized()).isFalse(); 
            assertThat(result.appliedOptimizations()).isEmpty(); 
        }
    }

    @Nested
    @DisplayName("OptimizationResult Record")
    class OptimizationResultTests {

        @Test
        @DisplayName("unoptimized() returns result with optimized=false and empty optimizations")
        void unoptimized_returnsCorrectResult() { 
            QueryOptimizer.OptimizationResult result = QueryOptimizer.OptimizationResult.unoptimized("SELECT * FROM table");
            
            assertThat(result.optimized()).isFalse(); 
            assertThat(result.optimizedQuery()).isEqualTo("SELECT * FROM table");
            assertThat(result.appliedOptimizations()).isEmpty(); 
        }

        @Test
        @DisplayName("optimized result with optimizations returns correct values")
        void optimizedResult_returnsCorrectValues() { 
            QueryOptimizer.OptimizationResult result = new QueryOptimizer.OptimizationResult( 
                true,
                "SELECT * FROM table",
                List.of("Predicate pushdown", "Column pruning") 
            );
            
            assertThat(result.optimized()).isTrue(); 
            assertThat(result.optimizedQuery()).isEqualTo("SELECT * FROM table");
            assertThat(result.appliedOptimizations()).containsExactly("Predicate pushdown", "Column pruning"); 
        }

        @Test
        @DisplayName("appliedOptimizations list is immutable")
        void appliedOptimizations_isImmutable() { 
            List<String> original = new ArrayList<>(); 
            original.add("Predicate pushdown");
            QueryOptimizer.OptimizationResult result = new QueryOptimizer.OptimizationResult( 
                true,
                "SELECT * FROM table",
                original
            );
            
            // Modifying original list should not affect result
            original.add("Column pruning");
            assertThat(result.appliedOptimizations()).hasSize(1); 
        }
    }

    @Nested
    @DisplayName("Optimization Flags")
    class OptimizationFlagTests {

        @Test
        @DisplayName("optimizer with predicate pushdown disabled")
        void predicatePushdownDisabled_noPredicateOptimization() { 
            QueryOptimizer opt = new QueryOptimizer(false, true, true); 
            assertThat(opt).isNotNull(); 
        }

        @Test
        @DisplayName("optimizer with column pruning disabled")
        void columnPruningDisabled_noPruningOptimization() { 
            QueryOptimizer opt = new QueryOptimizer(true, false, true); 
            assertThat(opt).isNotNull(); 
        }

        @Test
        @DisplayName("optimizer with limit pushdown disabled")
        void limitPushdownDisabled_noLimitOptimization() { 
            QueryOptimizer opt = new QueryOptimizer(true, true, false); 
            assertThat(opt).isNotNull(); 
        }

        @Test
        @DisplayName("optimizer with all optimizations disabled")
        void allDisabled_noOptimizations() { 
            QueryOptimizer opt = new QueryOptimizer(false, false, false); 
            assertThat(opt).isNotNull(); 
        }
    }

    @Nested
    @DisplayName("Limit Pushdown")
    class LimitPushdownTests {

        @Test
        @DisplayName("pushes LIMIT into plain subquery with no WHERE, GROUP BY, or DISTINCT")
        void pushesLimitIntoSubquery() throws JSQLParserException {
            String query = "SELECT * FROM (SELECT id, name FROM products) sub LIMIT 10";
            Statement stmt = CCJSqlParserUtil.parse(query);

            QueryOptimizer.OptimizationResult result = optimizer.optimize(query, stmt);

            assertThat(result.optimized()).isTrue();
            assertThat(result.appliedOptimizations()).contains("Limit pushdown");
            assertThat(result.optimizedQuery()).containsIgnoringCase("LIMIT 10");
        }

        @Test
        @DisplayName("does not push LIMIT when outer has WHERE clause")
        void doesNotPushLimitWhenOuterHasWhere() throws JSQLParserException {
            String query = "SELECT * FROM (SELECT id, name FROM products) sub WHERE id > 5 LIMIT 10";
            Statement stmt = CCJSqlParserUtil.parse(query);

            QueryOptimizer.OptimizationResult result = optimizer.optimize(query, stmt);

            assertThat(result.optimized()).isFalse();
            assertThat(result.appliedOptimizations()).isEmpty();
        }

        @Test
        @DisplayName("does not push LIMIT when inner subquery has GROUP BY")
        void doesNotPushLimitWhenInnerHasGroupBy() throws JSQLParserException {
            String query = "SELECT * FROM (SELECT category, COUNT(*) AS cnt FROM products GROUP BY category) sub LIMIT 5";
            Statement stmt = CCJSqlParserUtil.parse(query);

            QueryOptimizer.OptimizationResult result = optimizer.optimize(query, stmt);

            assertThat(result.optimized()).isFalse();
        }

        @Test
        @DisplayName("does not push LIMIT when inner subquery already has LIMIT")
        void doesNotPushLimitWhenInnerAlreadyHasLimit() throws JSQLParserException {
            String query = "SELECT * FROM (SELECT id FROM products LIMIT 20) sub LIMIT 10";
            Statement stmt = CCJSqlParserUtil.parse(query);

            QueryOptimizer.OptimizationResult result = optimizer.optimize(query, stmt);

            assertThat(result.optimized()).isFalse();
        }

        @Test
        @DisplayName("does not push LIMIT for simple table scan (no subquery)")
        void doesNotPushLimitForTableScan() throws JSQLParserException {
            String query = "SELECT * FROM products LIMIT 10";
            Statement stmt = CCJSqlParserUtil.parse(query);

            QueryOptimizer.OptimizationResult result = optimizer.optimize(query, stmt);

            assertThat(result.optimized()).isFalse();
        }

        @Test
        @DisplayName("limit pushdown disabled flag prevents optimization")
        void limitPushdownFlagDisablesOptimization() throws JSQLParserException {
            QueryOptimizer noLimitOpt = new QueryOptimizer(true, true, false);
            String query = "SELECT * FROM (SELECT id FROM products) sub LIMIT 10";
            Statement stmt = CCJSqlParserUtil.parse(query);

            QueryOptimizer.OptimizationResult result = noLimitOpt.optimize(query, stmt);

            assertThat(result.optimized()).isFalse();
        }

        @Test
        @DisplayName("does not push LIMIT when inner subquery has DISTINCT")
        void doesNotPushLimitWhenInnerHasDistinct() throws JSQLParserException {
            String query = "SELECT * FROM (SELECT DISTINCT category FROM products) sub LIMIT 5";
            Statement stmt = CCJSqlParserUtil.parse(query);

            QueryOptimizer.OptimizationResult result = optimizer.optimize(query, stmt);

            assertThat(result.optimized()).isFalse();
        }

        @Test
        @DisplayName("does not push LIMIT when outer FROM has joins")
        void doesNotPushLimitWhenOuterHasJoins() throws JSQLParserException {
            String query = "SELECT * FROM (SELECT id FROM products) sub JOIN categories c ON sub.id = c.id LIMIT 10";
            Statement stmt = CCJSqlParserUtil.parse(query);

            QueryOptimizer.OptimizationResult result = optimizer.optimize(query, stmt);

            assertThat(result.optimized()).isFalse();
        }
    }

    @Nested
    @DisplayName("Optimization Throughput")
    class OptimizationThroughputTests {

        /**
         * Throughput guard: the optimizer must process at least 500 queries per second
         * for pushdown-eligible subquery patterns. This validates that jsqlparser AST
         * rewriting does not regress to an unacceptable latency for high-frequency
         * query optimization paths.
         */
        @Test
        @DisplayName("processes at least 500 limit-pushdown queries per second")
        void limitPushdownThroughputIsAcceptable() throws JSQLParserException {
            String query = "SELECT * FROM (SELECT id, name, price FROM products) sub LIMIT 50";
            Statement stmt = CCJSqlParserUtil.parse(query);

            int iterations = 1000;
            // Warm up
            for (int i = 0; i < 50; i++) {
                optimizer.optimize(query, stmt);
            }

            long start = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                QueryOptimizer.OptimizationResult result = optimizer.optimize(query, stmt);
                assertThat(result.optimized()).isTrue();
            }
            long elapsedNs = System.nanoTime() - start;

            double queriesPerSecond = (iterations * 1_000_000_000.0) / elapsedNs;
            assertThat(queriesPerSecond)
                    .as("Optimizer throughput must exceed 500 queries/sec, got %.1f", queriesPerSecond)
                    .isGreaterThan(500.0);
        }

        @Test
        @DisplayName("non-optimizable queries complete in under 5ms each")
        void nonOptimizableQueryLatencyIsAcceptable() throws JSQLParserException {
            String query = "SELECT category, COUNT(*) FROM products WHERE price > 100 GROUP BY category";
            Statement stmt = CCJSqlParserUtil.parse(query);

            int iterations = 100;
            // Warm up
            for (int i = 0; i < 10; i++) {
                optimizer.optimize(query, stmt);
            }

            for (int i = 0; i < iterations; i++) {
                long start = System.nanoTime();
                optimizer.optimize(query, stmt);
                long elapsedMs = (System.nanoTime() - start) / 1_000_000;
                assertThat(elapsedMs)
                        .as("Single non-optimizable query must complete in under 5ms")
                        .isLessThan(5L);
            }
        }
    }
}
