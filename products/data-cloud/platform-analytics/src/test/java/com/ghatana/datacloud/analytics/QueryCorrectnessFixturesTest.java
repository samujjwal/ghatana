/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.analytics;

import com.ghatana.datacloud.entity.Entity;
import com.ghatana.datacloud.entity.storage.QuerySpec;
import com.ghatana.datacloud.entity.storage.StorageConnector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Correctness fixture tests for the Analytics query engine.
 *
 * <p>Uses a mock {@link StorageConnector} with deterministic datasets to verify:
 * COUNT aggregation (with and without GROUP BY), SELECT projection, // GH-90000
 * JOIN hash-join, TIMESERIES routing, null-connector safety,
 * input validation, and result/plan caching.
 *
 * @doc.type class
 * @doc.purpose Correctness tests for AnalyticsQueryEngine with deterministic datasets
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Analytics Query Engine - Correctness Fixtures")
@ExtendWith(MockitoExtension.class) // GH-90000
class QueryCorrectnessFixturesTest extends EventloopTestBase {

    // ─────────────────────────────────────────────────────────────────
    // Deterministic datasets
    // ─────────────────────────────────────────────────────────────────

    static final List<Entity> SALES = List.of( // GH-90000
            mkEntity("1", "sales", Map.of("product", "Widget A", "region", "US",   "quantity", 100)), // GH-90000
            mkEntity("2", "sales", Map.of("product", "Widget A", "region", "US",   "quantity", 150)), // GH-90000
            mkEntity("3", "sales", Map.of("product", "Widget B", "region", "EU",   "quantity",  50)), // GH-90000
            mkEntity("4", "sales", Map.of("product", "Widget B", "region", "EU",   "quantity",  75)), // GH-90000
            mkEntity("5", "sales", Map.of("product", "Widget C", "region", "APAC", "quantity", 200)) // GH-90000
    );

    static final List<Entity> CUSTOMERS = List.of( // GH-90000
            mkEntity("c1", "customers", Map.of("customerId", "C001", "name", "Alice")), // GH-90000
            mkEntity("c2", "customers", Map.of("customerId", "C002", "name", "Bob")), // GH-90000
            mkEntity("c3", "customers", Map.of("customerId", "C003", "name", "Carol")) // GH-90000
    );

    static final List<Entity> ORDERS = List.of( // GH-90000
            mkEntity("o1", "orders", Map.of("customerId", "C001", "amount", 100.0)), // GH-90000
            mkEntity("o2", "orders", Map.of("customerId", "C002", "amount", 200.0)), // GH-90000
            mkEntity("o3", "orders", Map.of("customerId", "C001", "amount",  50.0)) // GH-90000
    );

    // ─────────────────────────────────────────────────────────────────
    // Test wiring
    // ─────────────────────────────────────────────────────────────────

    @Mock StorageConnector storageConnector;
    AnalyticsQueryEngine engine;

    @BeforeEach
    void setUp() { // GH-90000
        engine = new AnalyticsQueryEngine(storageConnector); // GH-90000
        lenient().when(storageConnector.query(anyString(), eq("sales"), any(QuerySpec.class)))
                .thenReturn(Promise.of(mkResult(SALES))); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────
    // AGGREGATE: COUNT without GROUP BY
    // ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("COUNT aggregation - no GROUP BY")
    class CountAllTests {

        @Test
        @DisplayName("COUNT(*) over 5 rows returns single row with count=5")
        void countAll_returnsSingleRowWithCount() { // GH-90000
            QueryResult r = runSql("SELECT COUNT(*) FROM sales");
            assertThat(r.getRows()).hasSize(1); // GH-90000
            assertThat(r.getQueryType()).isEqualTo("AGGREGATE");
            assertThat(((Number) r.getRows().get(0).get("count")).longValue()).isEqualTo(5L);
        }

        @Test
        @DisplayName("COUNT over empty collection returns count=0")
        void countAll_emptyCollection_returnsZero() { // GH-90000
            stubCollection("empty", List.of()); // GH-90000
            QueryResult r = runSql("SELECT COUNT(*) FROM empty");
            assertThat(r.getRows()).hasSize(1); // GH-90000
            assertThat(((Number) r.getRows().get(0).get("count")).longValue()).isEqualTo(0L);
        }

        @Test
        @DisplayName("SUM() in SELECT recognised as AGGREGATE query type")
        void sumInSelect_recognisedAsAggregate() { // GH-90000
            assertThat(runSql("SELECT SUM(quantity) FROM sales").getQueryType())
                    .isEqualTo("AGGREGATE");
        }

        @Test
        @DisplayName("AVG() in SELECT recognised as AGGREGATE query type")
        void avgInSelect_recognisedAsAggregate() { // GH-90000
            assertThat(runSql("SELECT AVG(quantity) FROM sales").getQueryType())
                    .isEqualTo("AGGREGATE");
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // AGGREGATE: COUNT with GROUP BY
    // ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("COUNT aggregation - with GROUP BY")
    class GroupByCountTests {

        @Test
        @DisplayName("GROUP BY region: US=2, EU=2, APAC=1")
        void groupByRegion_correctCounts() { // GH-90000
            QueryResult r = runSql("SELECT region, COUNT(*) FROM sales GROUP BY region");
            assertThat(r.getQueryType()).isEqualTo("AGGREGATE");
            assertThat(r.getRows()).hasSize(3); // GH-90000
            Map<String, Long> m = countMap(r, "region"); // GH-90000
            assertThat(m).containsEntry("US", 2L).containsEntry("EU", 2L).containsEntry("APAC", 1L); // GH-90000
        }

        @Test
        @DisplayName("GROUP BY product: Widget A=2, Widget B=2, Widget C=1")
        void groupByProduct_correctCounts() { // GH-90000
            QueryResult r = runSql("SELECT product, COUNT(*) FROM sales GROUP BY product");
            assertThat(r.getRows()).hasSize(3); // GH-90000
            Map<String, Long> m = countMap(r, "product"); // GH-90000
            assertThat(m).containsEntry("Widget A", 2L) // GH-90000
                         .containsEntry("Widget B", 2L) // GH-90000
                         .containsEntry("Widget C", 1L); // GH-90000
        }

        @Test
        @DisplayName("GROUP BY on single-entity collection: 1 group, count=1")
        void groupBySingleEntity_oneGroup() { // GH-90000
            stubCollection("items", List.of(mkEntity("x", "items", Map.of("cat", "alpha")))); // GH-90000
            QueryResult r = runSql("SELECT cat, COUNT(*) FROM items GROUP BY cat");
            assertThat(r.getRows()).hasSize(1); // GH-90000
            assertThat(((Number) r.getRows().get(0).get("count")).longValue()).isEqualTo(1L);
        }

        @Test
        @DisplayName("GROUP BY field absent from all entities returns 0 groups")
        void groupByMissingField_returnsEmpty() { // GH-90000
            QueryResult r = runSql("SELECT missing, COUNT(*) FROM sales GROUP BY missing");
            assertThat(r.getRows()).isEmpty(); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // SELECT
    // ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("SELECT - row projection")
    class SelectTests {

        @Test
        @DisplayName("SELECT * returns all 5 rows with expected fields")
        void selectAll_returnsAllRows() { // GH-90000
            QueryResult r = runSql("SELECT * FROM sales");
            assertThat(r.getQueryType()).isEqualTo("SELECT");
            assertThat(r.getRows()).hasSize(5); // GH-90000
            for (Map<String, Object> row : r.getRows()) { // GH-90000
                assertThat(row).containsKeys("product", "region", "quantity"); // GH-90000
            }
        }

        @Test
        @DisplayName("SELECT row includes 'id' field derived from entity UUID")
        void selectAll_includesId() { // GH-90000
            QueryResult r = runSql("SELECT * FROM sales");
            assertThat(r.getRows().get(0)).containsKey("id");
        }

        @Test
        @DisplayName("SELECT on empty collection returns 0 rows")
        void selectEmpty_returnsNoRows() { // GH-90000
            stubCollection("noop", List.of()); // GH-90000
            assertThat(runSql("SELECT * FROM noop").getRows()).isEmpty();
        }

        @Test
        @DisplayName("SELECT result has non-blank queryId, isOptimized=true, executionTimeMs>=0")
        void selectResult_hasCorrectMetadata() { // GH-90000
            QueryResult r = runSql("SELECT * FROM sales");
            assertThat(r.getQueryId()).isNotBlank(); // GH-90000
            assertThat(r.isOptimized()).isTrue(); // GH-90000
            assertThat(r.getExecutionTimeMs()).isGreaterThanOrEqualTo(0); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // LIMIT routing
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("SELECT with LIMIT clause routes as SELECT (not AGGREGATE)")
    void limitClause_routesToSelect() { // GH-90000
        assertThat(runSql("SELECT * FROM sales LIMIT 2").getQueryType()).isEqualTo("SELECT");
    }

    // ─────────────────────────────────────────────────────────────────
    // JOIN
    // ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("JOIN - hash join across two collections")
    class JoinTests {

        @BeforeEach
        void stubJoinCollections() { // GH-90000
            stubCollection("customers", CUSTOMERS); // GH-90000
            stubCollection("orders", ORDERS); // GH-90000
        }

        @Test
        @DisplayName("JOIN on customerId: C001 appears in 2 orders, C002 in 1 → 3 total rows")
        void join_onCustomerId_correctRowCount() { // GH-90000
            QueryResult r = runSql("SELECT * FROM customers JOIN orders ON customerId");
            assertThat(r.getQueryType()).isEqualTo("JOIN");
            assertThat(r.getRows()).hasSize(3); // GH-90000
        }

        @Test
        @DisplayName("JOIN row merges fields from both left and right collections")
        void join_mergedRow_hasFieldsFromBothSides() { // GH-90000
            QueryResult r = runSql("SELECT * FROM customers JOIN orders ON customerId");
            assertThat(r.getRows()).isNotEmpty(); // GH-90000
            Map<String, Object> row = r.getRows().get(0); // GH-90000
            assertThat(row).containsKey("name");    // from customers
            assertThat(row).containsKey("amount");  // from orders
        }

    }

    @Test
    @DisplayName("JOIN where right side has no matching key values returns empty result")
    void joinNoMatchingKeys_returnsEmpty() { // GH-90000
        stubCollection("customers", CUSTOMERS); // GH-90000
        stubCollection("orders", List.of(mkEntity("o9", "orders", Map.of("otherField", "X")))); // GH-90000
        QueryResult r = runSql("SELECT * FROM customers JOIN orders ON customerId");
        assertThat(r.getRows()).isEmpty(); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────
    // TIMESERIES routing
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("DATE_TRUNC in SELECT routes to TIMESERIES query type")
    void datetrunc_routesToTimeseries() { // GH-90000
        stubCollection("events", List.of()); // GH-90000
        QueryResult r = runPromise(() -> engine.submitQuery("t1", // GH-90000
                "SELECT DATE_TRUNC(day, ts) FROM events", // GH-90000
                Map.of("timeWindowStart", "2026-01-01T00:00:00Z", // GH-90000
                       "timeWindowEnd",   "2026-01-02T00:00:00Z")));
        assertThat(r.getQueryType()).isEqualTo("TIMESERIES");
    }

    // ─────────────────────────────────────────────────────────────────
    // Null StorageConnector (testing/legacy mode) // GH-90000
    // ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Null StorageConnector - safe empty results")
    class NullConnectorTests {

        @Test
        @DisplayName("SELECT with no connector returns empty rows without throwing")
        void nullConnector_selectReturnsEmpty() { // GH-90000
            QueryResult r = runPromise(() -> // GH-90000
                    new AnalyticsQueryEngine().submitQuery("t1", "SELECT * FROM sales", Map.of())); // GH-90000
            assertThat(r.getRows()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("AGGREGATE with no connector returns empty rows without throwing")
        void nullConnector_aggregateReturnsEmpty() { // GH-90000
            QueryResult r = runPromise(() -> // GH-90000
                    new AnalyticsQueryEngine().submitQuery("t1", "SELECT COUNT(*) FROM sales", Map.of())); // GH-90000
            assertThat(r.getRows()).isEmpty(); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Input validation
    // ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Input validation - null inputs rejected at engine boundary")
    class ValidationTests {

        @Test
        @DisplayName("null tenantId throws NullPointerException with 'tenantId' in message")
        void nullTenantId_throwsNPE() { // GH-90000
            assertThatThrownBy(() -> // GH-90000
                    runPromise(() -> engine.submitQuery(null, "SELECT * FROM sales", Map.of()))) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("tenantId");
        }

        @Test
        @DisplayName("null queryText throws NullPointerException with 'queryText' in message")
        void nullQueryText_throwsNPE() { // GH-90000
            assertThatThrownBy(() -> // GH-90000
                    runPromise(() -> engine.submitQuery("t1", null, Map.of()))) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("queryText");
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Cached result and plan retrieval
    // ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Cached result and plan retrieval via getResult / getPlan")
    class CachedRetrievalTests {

        @Test
        @DisplayName("getResult for known queryId returns the same cached result")
        void getResult_knownId_returnsCached() { // GH-90000
            QueryResult submitted = runSql("SELECT * FROM sales");
            QueryResult fetched   = runPromise(() -> engine.getResult(submitted.getQueryId())); // GH-90000
            assertThat(fetched.getQueryId()).isEqualTo(submitted.getQueryId()); // GH-90000
        }

        @Test
        @DisplayName("getResult for unknown id throws IllegalArgumentException with 'not found'")
        void getResult_unknownId_throws() { // GH-90000
            assertThatThrownBy(() -> runPromise(() -> engine.getResult("unknown")))
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("not found");
        }

        @Test
        @DisplayName("getPlan for known queryId lists collection name as data source")
        void getPlan_knownId_returnsCorrectPlan() { // GH-90000
            QueryResult result = runSql("SELECT * FROM sales");
            QueryPlan plan = runPromise(() -> engine.getPlan(result.getQueryId())); // GH-90000
            assertThat(plan).isNotNull(); // GH-90000
            assertThat(plan.getDataSources()).contains("sales");
        }

        @Test
        @DisplayName("getPlan for unknown id throws IllegalArgumentException with 'not found'")
        void getPlan_unknownId_throws() { // GH-90000
            assertThatThrownBy(() -> runPromise(() -> engine.getPlan("ghost")))
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("not found");
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // close() // GH-90000
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("close() shuts down the worker executor without throwing")
    void close_doesNotThrow() throws Exception { // GH-90000
        new AnalyticsQueryEngine(storageConnector).close(); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────

    /** Submit SQL against the default tenant with empty params. */
    private QueryResult runSql(String sql) { // GH-90000
        return runPromise(() -> engine.submitQuery("tenant-1", sql, Map.of())); // GH-90000
    }

    /** Stub the named collection to return the given entity list. */
    private void stubCollection(String name, List<Entity> entities) { // GH-90000
        when(storageConnector.query(anyString(), eq(name), any(QuerySpec.class))) // GH-90000
                .thenReturn(Promise.of(mkResult(entities))); // GH-90000
    }

    /** Build a {groupField -> count} map from grouped aggregate result rows. */
    private static Map<String, Long> countMap(QueryResult r, String groupField) { // GH-90000
        Map<String, Long> m = new HashMap<>(); // GH-90000
        for (Map<String, Object> row : r.getRows()) { // GH-90000
            m.put((String) row.get(groupField), ((Number) row.get("count")).longValue());
        }
        return m;
    }

    /** Create a test Entity with a deterministic UUID seed, collection name, and data fields. */
    private static Entity mkEntity(String idSeed, String collection, Map<String, Object> data) { // GH-90000
        Entity e = Entity.builder() // GH-90000
                .id(UUID.nameUUIDFromBytes(idSeed.getBytes())) // GH-90000
                .tenantId("tenant-fixture")
                .collectionName(collection) // GH-90000
                .build(); // GH-90000
        e.getData().putAll(data); // GH-90000
        return e;
    }

    /** Wrap entities in a {@link StorageConnector.QueryResult} with no pagination offsets. */
    private static StorageConnector.QueryResult mkResult(List<Entity> entities) { // GH-90000
        return new StorageConnector.QueryResult(entities, entities.size(), entities.size(), 0, 0); // GH-90000
    }
}
