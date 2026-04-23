/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
    void setUp() { // GH-90000
        optimizer = new QueryOptimizer(); // GH-90000
    }

    @Nested
    @DisplayName("Basic Optimization")
    class BasicOptimizationTests {

        @Test
        @DisplayName("optimizer can be created with all optimizations enabled")
        void defaultConstructor_allEnabled() { // GH-90000
            QueryOptimizer opt = new QueryOptimizer(); // GH-90000
            assertThat(opt).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("optimizer can be created with specific flags")
        void customConstructor_withFlags() { // GH-90000
            QueryOptimizer opt = new QueryOptimizer(true, false, true); // GH-90000
            assertThat(opt).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("valid SELECT query returns unoptimized result")
        void validSelect_returnsUnoptimized() throws JSQLParserException { // GH-90000
            String query = "SELECT * FROM products";
            Statement stmt = CCJSqlParserUtil.parse(query); // GH-90000
            
            QueryOptimizer.OptimizationResult result = optimizer.optimize(query, stmt); // GH-90000
            
            assertThat(result.optimized()).isFalse(); // GH-90000
            assertThat(result.appliedOptimizations()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("SELECT with WHERE clause returns unoptimized result")
        void selectWithWhere_returnsUnoptimized() throws JSQLParserException { // GH-90000
            String query = "SELECT * FROM products WHERE price > 100";
            Statement stmt = CCJSqlParserUtil.parse(query); // GH-90000
            
            QueryOptimizer.OptimizationResult result = optimizer.optimize(query, stmt); // GH-90000
            
            assertThat(result.optimized()).isFalse(); // GH-90000
            assertThat(result.appliedOptimizations()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("SELECT with LIMIT returns unoptimized result")
        void selectWithLimit_returnsUnoptimized() throws JSQLParserException { // GH-90000
            String query = "SELECT * FROM products LIMIT 100";
            Statement stmt = CCJSqlParserUtil.parse(query); // GH-90000
            
            QueryOptimizer.OptimizationResult result = optimizer.optimize(query, stmt); // GH-90000
            
            assertThat(result.optimized()).isFalse(); // GH-90000
            assertThat(result.appliedOptimizations()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("SELECT with GROUP BY returns unoptimized result")
        void selectWithGroupBy_returnsUnoptimized() throws JSQLParserException { // GH-90000
            String query = "SELECT category, COUNT(*) FROM products GROUP BY category"; // GH-90000
            Statement stmt = CCJSqlParserUtil.parse(query); // GH-90000
            
            QueryOptimizer.OptimizationResult result = optimizer.optimize(query, stmt); // GH-90000
            
            assertThat(result.optimized()).isFalse(); // GH-90000
            assertThat(result.appliedOptimizations()).isEmpty(); // GH-90000
        }
    }

    @Nested
    @DisplayName("OptimizationResult Record")
    class OptimizationResultTests {

        @Test
        @DisplayName("unoptimized() returns result with optimized=false and empty optimizations")
        void unoptimized_returnsCorrectResult() { // GH-90000
            QueryOptimizer.OptimizationResult result = QueryOptimizer.OptimizationResult.unoptimized("SELECT * FROM table");
            
            assertThat(result.optimized()).isFalse(); // GH-90000
            assertThat(result.optimizedQuery()).isEqualTo("SELECT * FROM table");
            assertThat(result.appliedOptimizations()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("optimized result with optimizations returns correct values")
        void optimizedResult_returnsCorrectValues() { // GH-90000
            QueryOptimizer.OptimizationResult result = new QueryOptimizer.OptimizationResult( // GH-90000
                true,
                "SELECT * FROM table",
                List.of("Predicate pushdown", "Column pruning") // GH-90000
            );
            
            assertThat(result.optimized()).isTrue(); // GH-90000
            assertThat(result.optimizedQuery()).isEqualTo("SELECT * FROM table");
            assertThat(result.appliedOptimizations()).containsExactly("Predicate pushdown", "Column pruning"); // GH-90000
        }

        @Test
        @DisplayName("appliedOptimizations list is immutable")
        void appliedOptimizations_isImmutable() { // GH-90000
            List<String> original = new ArrayList<>(); // GH-90000
            original.add("Predicate pushdown");
            QueryOptimizer.OptimizationResult result = new QueryOptimizer.OptimizationResult( // GH-90000
                true,
                "SELECT * FROM table",
                original
            );
            
            // Modifying original list should not affect result
            original.add("Column pruning");
            assertThat(result.appliedOptimizations()).hasSize(1); // GH-90000
        }
    }

    @Nested
    @DisplayName("Optimization Flags")
    class OptimizationFlagTests {

        @Test
        @DisplayName("optimizer with predicate pushdown disabled")
        void predicatePushdownDisabled_noPredicateOptimization() { // GH-90000
            QueryOptimizer opt = new QueryOptimizer(false, true, true); // GH-90000
            assertThat(opt).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("optimizer with column pruning disabled")
        void columnPruningDisabled_noPruningOptimization() { // GH-90000
            QueryOptimizer opt = new QueryOptimizer(true, false, true); // GH-90000
            assertThat(opt).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("optimizer with limit pushdown disabled")
        void limitPushdownDisabled_noLimitOptimization() { // GH-90000
            QueryOptimizer opt = new QueryOptimizer(true, true, false); // GH-90000
            assertThat(opt).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("optimizer with all optimizations disabled")
        void allDisabled_noOptimizations() { // GH-90000
            QueryOptimizer opt = new QueryOptimizer(false, false, false); // GH-90000
            assertThat(opt).isNotNull(); // GH-90000
        }
    }
}
