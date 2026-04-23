package com.ghatana.datacloud.analytics;

import com.ghatana.datacloud.entity.storage.StorageConnector;
import com.ghatana.datacloud.entity.storage.QuerySpec;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;

/**
 * Comprehensive tests for {@link AnalyticsQueryEngine} with 100% coverage.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Query submission and execution</li>
 *   <li>Query plan generation for all query types</li>
 *   <li>Query type detection (SELECT, AGGREGATE, TIMESERIES, JOIN)</li> // GH-90000
 *   <li>Result caching and retrieval</li>
 *   <li>Error handling and validation</li>
 *   <li>Cost estimation</li>
 *   <li>Data source extraction</li>
 * </ul>
 *
 * @doc.type test
 * @doc.purpose Comprehensive test coverage for AnalyticsQueryEngine
 * @doc.layer core
 * @doc.pattern Unit Test, Integration Test
 */
@DisplayName("Analytics Query Engine Tests")
@ExtendWith(MockitoExtension.class) // GH-90000
class AnalyticsQueryEngineTest extends EventloopTestBase {

    @Mock
    StorageConnector storageConnector;

    private AnalyticsQueryEngine engine;

    @BeforeEach
    void setup() { // GH-90000
        // L2: inject StorageConnector so engine has access to real storage in execution paths
        engine = new AnalyticsQueryEngine(storageConnector); // GH-90000
        // Default stub: return empty QueryResult so .map() calls don't NPE // GH-90000
        lenient().when(storageConnector.query(anyString(), anyString(), any(QuerySpec.class))) // GH-90000
                .thenReturn(Promise.of(StorageConnector.QueryResult.empty())); // GH-90000
    }

    // ========================================================================
    // QUERY SUBMISSION TESTS
    // ========================================================================

    @Test
    @DisplayName("Should submit and execute SELECT query successfully")
    void shouldSubmitSelectQuery() { // GH-90000
        // GIVEN: Simple SELECT query
        String query = "SELECT * FROM users WHERE age > 25";
        Map<String, Object> params = Map.of("limit", 100); // GH-90000

        // WHEN: Submitting query
        QueryResult result = runPromise(() -> // GH-90000
            engine.submitQuery("tenant-1", query, params) // GH-90000
        );

        // THEN: Query executes successfully
        assertThat(result).isNotNull(); // GH-90000
        assertThat(result.getQueryId()).isNotNull(); // GH-90000
        assertThat(result.getQueryType()).isEqualTo("SELECT");
        assertThat(result.isOptimized()).isTrue(); // GH-90000
        assertThat(result.getExecutionTimeMs()).isGreaterThanOrEqualTo(0); // GH-90000
        assertThat(result.getRows()).isNotNull(); // GH-90000
        assertThat(result.getRowCount()).isEqualTo(0); // Empty until StorageConnector integration // GH-90000
    }

    @Test
    @DisplayName("Should submit and execute AGGREGATE query successfully")
    void shouldSubmitAggregateQuery() { // GH-90000
        // GIVEN: Aggregation query
        String query = "SELECT COUNT(*), AVG(salary) FROM employees GROUP BY department"; // GH-90000

        // WHEN: Submitting query
        QueryResult result = runPromise(() -> // GH-90000
            engine.submitQuery("tenant-1", query, Map.of()) // GH-90000
        );

        // THEN: Query executes as AGGREGATE type
        assertThat(result.getQueryType()).isEqualTo("AGGREGATE");
        assertThat(result.isOptimized()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("Should submit and execute TIMESERIES query successfully")
    void shouldSubmitTimeseriesQuery() { // GH-90000
        // GIVEN: Time-series query without aggregation (TIMESERIES takes precedence) // GH-90000
        String query = "SELECT * FROM events WHERE timestamp > INTERVAL '1 day'";

        // WHEN: Submitting query
        QueryResult result = runPromise(() -> // GH-90000
            engine.submitQuery("tenant-1", query, Map.of()) // GH-90000
        );

        // THEN: Query executes as TIMESERIES type
        assertThat(result.getQueryType()).isEqualTo("TIMESERIES");
    }

    @Test
    @DisplayName("Should submit and execute JOIN query successfully")
    void shouldSubmitJoinQuery() { // GH-90000
        // GIVEN: JOIN query
        String query = "SELECT u.*, o.* FROM users u JOIN orders o ON u.id = o.user_id";

        // WHEN: Submitting query
        QueryResult result = runPromise(() -> // GH-90000
            engine.submitQuery("tenant-1", query, Map.of()) // GH-90000
        );

        // THEN: Query executes as JOIN type
        assertThat(result.getQueryType()).isEqualTo("JOIN");
    }

    @Test
    @DisplayName("Should reject null tenant ID")
    void shouldRejectNullTenantId() { // GH-90000
        // WHEN/THEN: Throws exception for null tenant ID
        assertThatThrownBy(() -> // GH-90000
            runPromise(() -> engine.submitQuery(null, "SELECT * FROM users", Map.of())) // GH-90000
        ).isInstanceOf(NullPointerException.class) // GH-90000
         .hasMessageContaining("tenantId");
    }

    @Test
    @DisplayName("Should reject null query text")
    void shouldRejectNullQueryText() { // GH-90000
        // WHEN/THEN: Throws exception for null query text
        assertThatThrownBy(() -> // GH-90000
            runPromise(() -> engine.submitQuery("tenant-1", null, Map.of())) // GH-90000
        ).isInstanceOf(NullPointerException.class) // GH-90000
         .hasMessageContaining("queryText");
    }

    @Test
    @DisplayName("Should handle empty parameters")
    void shouldHandleEmptyParameters() { // GH-90000
        // GIVEN: Query with empty parameters
        String query = "SELECT * FROM users";

        // WHEN: Submitting query with empty parameters
        QueryResult result = runPromise(() -> // GH-90000
            engine.submitQuery("tenant-1", query, Map.of()) // GH-90000
        );

        // THEN: Query executes successfully
        assertThat(result).isNotNull(); // GH-90000
    }

    // ========================================================================
    // QUERY PLAN TESTS
    // ========================================================================

    @Test
    @DisplayName("Should generate query plan for submitted query")
    void shouldGenerateQueryPlan() { // GH-90000
        // GIVEN: Submitted query
        String query = "SELECT * FROM users WHERE status = 'active'";
        QueryResult result = runPromise(() -> // GH-90000
            engine.submitQuery("tenant-1", query, Map.of()) // GH-90000
        );

        // WHEN: Retrieving query plan
        QueryPlan plan = runPromise(() -> // GH-90000
            engine.getPlan(result.getQueryId()) // GH-90000
        );

        // THEN: Plan is generated correctly
        assertThat(plan).isNotNull(); // GH-90000
        assertThat(plan.getQueryId()).isEqualTo(result.getQueryId()); // GH-90000
        assertThat(plan.getQueryType()).isEqualTo(QueryType.SELECT); // GH-90000
        assertThat(plan.isOptimized()).isTrue(); // GH-90000
        assertThat(plan.getEstimatedCost()).isGreaterThan(0); // GH-90000
        assertThat(plan.getDataSources()).isNotEmpty(); // GH-90000
    }

    @ParameterizedTest
    @CsvSource({ // GH-90000
        "SELECT * FROM users, SELECT",
        "SELECT COUNT(*) FROM users GROUP BY status, AGGREGATE", // GH-90000
        "SELECT SUM(amount) FROM transactions, AGGREGATE", // GH-90000
        "SELECT AVG(score) FROM tests GROUP BY student, AGGREGATE", // GH-90000
        "SELECT * FROM users u JOIN orders o ON u.id = o.user_id, JOIN",
        "SELECT * FROM events WHERE timestamp > INTERVAL '1 day', TIMESERIES",
        "SELECT * FROM logs WHERE timestamp > INTERVAL '1 hour', TIMESERIES"
    })
    @DisplayName("Should detect correct query type")
    void shouldDetectQueryType(String queryText, String expectedType) { // GH-90000
        // WHEN: Submitting query
        QueryResult result = runPromise(() -> // GH-90000
            engine.submitQuery("tenant-1", queryText, Map.of()) // GH-90000
        );

        // THEN: Correct query type is detected
        assertThat(result.getQueryType()).isEqualTo(expectedType); // GH-90000
    }

    @Test
    @DisplayName("Should estimate query cost based on complexity")
    void shouldEstimateQueryCost() { // GH-90000
        // GIVEN: Simple and complex queries
        String simpleQuery = "SELECT * FROM users";
        String complexQuery = "SELECT u.*, o.*, p.* FROM users u JOIN orders o ON u.id = o.user_id JOIN products p ON o.product_id = p.id WHERE u.status = 'active' AND o.created_at > '2024-01-01' GROUP BY u.department HAVING COUNT(*) > 10"; // GH-90000

        // WHEN: Submitting both queries
        QueryResult simpleResult = runPromise(() -> // GH-90000
            engine.submitQuery("tenant-1", simpleQuery, Map.of()) // GH-90000
        );
        QueryResult complexResult = runPromise(() -> // GH-90000
            engine.submitQuery("tenant-1", complexQuery, Map.of()) // GH-90000
        );

        // AND: Retrieving plans
        QueryPlan simplePlan = runPromise(() -> // GH-90000
            engine.getPlan(simpleResult.getQueryId()) // GH-90000
        );
        QueryPlan complexPlan = runPromise(() -> // GH-90000
            engine.getPlan(complexResult.getQueryId()) // GH-90000
        );

        // THEN: Complex query has higher estimated cost
        assertThat(complexPlan.getEstimatedCost()).isGreaterThan(simplePlan.getEstimatedCost()); // GH-90000
    }

    @Test
    @DisplayName("Should extract data sources from query")
    void shouldExtractDataSources() { // GH-90000
        // GIVEN: Query with FROM clause
        String query = "SELECT * FROM users WHERE age > 25";

        // WHEN: Submitting query
        QueryResult result = runPromise(() -> // GH-90000
            engine.submitQuery("tenant-1", query, Map.of()) // GH-90000
        );

        // AND: Retrieving plan
        QueryPlan plan = runPromise(() -> // GH-90000
            engine.getPlan(result.getQueryId()) // GH-90000
        );

        // THEN: Data sources are extracted (JSQLParser returns actual table names) // GH-90000
        assertThat(plan.getDataSources()).contains("users");
    }

    @Test
    @DisplayName("Should extract multiple data sources from JOIN query")
    void shouldExtractMultipleDataSources() { // GH-90000
        // GIVEN: JOIN query
        String query = "SELECT * FROM users u JOIN orders o ON u.id = o.user_id";

        // WHEN: Submitting query
        QueryResult result = runPromise(() -> // GH-90000
            engine.submitQuery("tenant-1", query, Map.of()) // GH-90000
        );

        // AND: Retrieving plan
        QueryPlan plan = runPromise(() -> // GH-90000
            engine.getPlan(result.getQueryId()) // GH-90000
        );

        // THEN: Multiple data sources are extracted (JSQLParser extracts actual table names) // GH-90000
        assertThat(plan.getDataSources()).hasSize(2); // GH-90000
        assertThat(plan.getDataSources()).contains("users", "orders"); // GH-90000
    }

    @Test
    @DisplayName("Should use default source when no FROM clause")
    void shouldUseDefaultSource() { // GH-90000
        // GIVEN: Query without FROM clause
        String query = "SELECT 1 + 1";

        // WHEN: Submitting query
        QueryResult result = runPromise(() -> // GH-90000
            engine.submitQuery("tenant-1", query, Map.of()) // GH-90000
        );

        // AND: Retrieving plan
        QueryPlan plan = runPromise(() -> // GH-90000
            engine.getPlan(result.getQueryId()) // GH-90000
        );

        // THEN: Default source is used
        assertThat(plan.getDataSources()).contains("default_source");
    }

    @Test
    @DisplayName("Should throw exception when retrieving non-existent plan")
    void shouldThrowExceptionForNonExistentPlan() { // GH-90000
        // WHEN/THEN: Throws exception for non-existent plan
        assertThatThrownBy(() -> // GH-90000
            runPromise(() -> engine.getPlan("non-existent-id"))
        ).isInstanceOf(IllegalArgumentException.class) // GH-90000
         .hasMessageContaining("Plan not found");
    }

    // ========================================================================
    // RESULT CACHING AND RETRIEVAL TESTS
    // ========================================================================

    @Test
    @DisplayName("Should cache query results")
    void shouldCacheResults() { // GH-90000
        // GIVEN: Submitted query
        String query = "SELECT * FROM users";
        QueryResult originalResult = runPromise(() -> // GH-90000
            engine.submitQuery("tenant-1", query, Map.of()) // GH-90000
        );

        // WHEN: Retrieving result from cache
        QueryResult cachedResult = runPromise(() -> // GH-90000
            engine.getResult(originalResult.getQueryId()) // GH-90000
        );

        // THEN: Same result is returned
        assertThat(cachedResult.getQueryId()).isEqualTo(originalResult.getQueryId()); // GH-90000
        assertThat(cachedResult.getQueryType()).isEqualTo(originalResult.getQueryType()); // GH-90000
        assertThat(cachedResult.getRowCount()).isEqualTo(originalResult.getRowCount()); // GH-90000
    }

    @Test
    @DisplayName("Should throw exception when retrieving non-existent result")
    void shouldThrowExceptionForNonExistentResult() { // GH-90000
        // WHEN/THEN: Throws exception for non-existent result
        assertThatThrownBy(() -> // GH-90000
            runPromise(() -> engine.getResult("non-existent-id"))
        ).isInstanceOf(IllegalArgumentException.class) // GH-90000
         .hasMessageContaining("Result not found");
    }

    // ========================================================================
    // QUERY EXECUTION TESTS
    // ========================================================================

    @Test
    @DisplayName("Should return empty results until StorageConnector integration")
    void shouldReturnEmptyResultsForNow() { // GH-90000
        // GIVEN: Any query
        String query = "SELECT * FROM users WHERE age > 25";

        // WHEN: Executing query
        QueryResult result = runPromise(() -> // GH-90000
            engine.submitQuery("tenant-1", query, Map.of()) // GH-90000
        );

        // THEN: Returns empty results (transitional until StorageConnector wired) // GH-90000
        assertThat(result.getRows()).isEmpty(); // GH-90000
        assertThat(result.getRowCount()).isEqualTo(0); // GH-90000
        assertThat(result.getColumnCount()).isEqualTo(0); // GH-90000
    }

    @Test
    @DisplayName("Should record execution time")
    void shouldRecordExecutionTime() { // GH-90000
        // GIVEN: Query
        String query = "SELECT * FROM users";

        // WHEN: Executing query
        QueryResult result = runPromise(() -> // GH-90000
            engine.submitQuery("tenant-1", query, Map.of()) // GH-90000
        );

        // THEN: Execution time is recorded
        assertThat(result.getExecutionTimeMs()).isGreaterThanOrEqualTo(0); // GH-90000
    }

    @Test
    @DisplayName("Should handle multiple concurrent queries")
    void shouldHandleConcurrentQueries() { // GH-90000
        // GIVEN: Multiple queries
        List<Promise<QueryResult>> promises = new ArrayList<>(); // GH-90000
        for (int i = 0; i < 10; i++) { // GH-90000
            String query = "SELECT * FROM users WHERE id = " + i;
            promises.add(engine.submitQuery("tenant-1", query, Map.of())); // GH-90000
        }

        // WHEN: Executing all queries concurrently
        List<QueryResult> results = runPromise(() -> // GH-90000
            io.activej.promise.Promises.toList(promises) // GH-90000
        );

        // THEN: All queries complete successfully
        assertThat(results).hasSize(10); // GH-90000
        assertThat(results).allMatch(r -> r.getQueryType().equals("SELECT"));
    }

    @ParameterizedTest
    @ValueSource(strings = { // GH-90000
        "SELECT * FROM users",
        "SELECT COUNT(*) FROM orders GROUP BY status", // GH-90000
        "SELECT * FROM users u JOIN orders o ON u.id = o.user_id",
        "SELECT DATE_TRUNC('day', created_at), COUNT(*) FROM events" // GH-90000
    })
    @DisplayName("Should execute various query types successfully")
    void shouldExecuteVariousQueryTypes(String query) { // GH-90000
        // WHEN: Executing query
        QueryResult result = runPromise(() -> // GH-90000
            engine.submitQuery("tenant-1", query, Map.of()) // GH-90000
        );

        // THEN: Query executes successfully
        assertThat(result).isNotNull(); // GH-90000
        assertThat(result.getQueryId()).isNotNull(); // GH-90000
        assertThat(result.isOptimized()).isTrue(); // GH-90000
    }

    // ========================================================================
    // QUERY RESULT STRUCTURE TESTS
    // ========================================================================

    @Test
    @DisplayName("Should build query result with all fields")
    void shouldBuildCompleteQueryResult() { // GH-90000
        // GIVEN: Query result builder
        List<Map<String, Object>> rows = List.of( // GH-90000
            Map.of("id", 1, "name", "Alice"), // GH-90000
            Map.of("id", 2, "name", "Bob") // GH-90000
        );

        // WHEN: Building result
        QueryResult result = QueryResult.builder() // GH-90000
            .queryId("query-123")
            .rows(rows) // GH-90000
            .rowCount(2) // GH-90000
            .columnCount(2) // GH-90000
            .executionTimeMs(150) // GH-90000
            .queryType("SELECT")
            .optimized(true) // GH-90000
            .build(); // GH-90000

        // THEN: All fields are set correctly
        assertThat(result.getQueryId()).isEqualTo("query-123");
        assertThat(result.getRows()).hasSize(2); // GH-90000
        assertThat(result.getRowCount()).isEqualTo(2); // GH-90000
        assertThat(result.getColumnCount()).isEqualTo(2); // GH-90000
        assertThat(result.getExecutionTimeMs()).isEqualTo(150); // GH-90000
        assertThat(result.getQueryType()).isEqualTo("SELECT");
        assertThat(result.isOptimized()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("Should build query plan with all fields")
    void shouldBuildCompleteQueryPlan() { // GH-90000
        // WHEN: Building plan
        QueryPlan plan = QueryPlan.builder() // GH-90000
            .queryId("query-123")
            .queryType(QueryType.AGGREGATE) // GH-90000
            .dataSources(List.of("source1", "source2")) // GH-90000
            .estimatedCost(75.5) // GH-90000
            .optimized(true) // GH-90000
            .build(); // GH-90000

        // THEN: All fields are set correctly
        assertThat(plan.getQueryId()).isEqualTo("query-123");
        assertThat(plan.getQueryType()).isEqualTo(QueryType.AGGREGATE); // GH-90000
        assertThat(plan.getDataSources()).containsExactly("source1", "source2"); // GH-90000
        assertThat(plan.getEstimatedCost()).isEqualTo(75.5); // GH-90000
        assertThat(plan.isOptimized()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("Should build analytics query with all fields")
    void shouldBuildCompleteAnalyticsQuery() { // GH-90000
        // GIVEN: Query builder
        Map<String, Object> params = Map.of("limit", 100); // GH-90000

        // WHEN: Building query
        AnalyticsQuery query = AnalyticsQuery.builder() // GH-90000
            .id("query-123")
            .tenantId("tenant-1")
            .queryText("SELECT * FROM users")
            .parameters(params) // GH-90000
            .submittedAt(java.time.Instant.now()) // GH-90000
            .status("SUBMITTED")
            .build(); // GH-90000

        // THEN: All fields are set correctly
        assertThat(query.getId()).isEqualTo("query-123");
        assertThat(query.getTenantId()).isEqualTo("tenant-1");
        assertThat(query.getQueryText()).isEqualTo("SELECT * FROM users");
        assertThat(query.getParameters()).isEqualTo(params); // GH-90000
        assertThat(query.getSubmittedAt()).isNotNull(); // GH-90000
        assertThat(query.getStatus()).isEqualTo("SUBMITTED");
    }

    @Test
    @DisplayName("Should update query status and completion time")
    void shouldUpdateQueryStatus() { // GH-90000
        // GIVEN: Query
        AnalyticsQuery query = AnalyticsQuery.builder() // GH-90000
            .id("query-123")
            .tenantId("tenant-1")
            .queryText("SELECT * FROM users")
            .parameters(Map.of()) // GH-90000
            .submittedAt(java.time.Instant.now()) // GH-90000
            .status("SUBMITTED")
            .build(); // GH-90000

        // WHEN: Updating status
        query.setStatus("COMPLETED");
        query.setCompletedAt(java.time.Instant.now()); // GH-90000

        // THEN: Status is updated
        assertThat(query.getStatus()).isEqualTo("COMPLETED");
        assertThat(query.getCompletedAt()).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("Should set error on query failure")
    void shouldSetErrorOnFailure() { // GH-90000
        // GIVEN: Query
        AnalyticsQuery query = AnalyticsQuery.builder() // GH-90000
            .id("query-123")
            .tenantId("tenant-1")
            .queryText("SELECT * FROM users")
            .parameters(Map.of()) // GH-90000
            .submittedAt(java.time.Instant.now()) // GH-90000
            .status("RUNNING")
            .build(); // GH-90000

        // WHEN: Setting error
        query.setError("Connection timeout");
        query.setStatus("FAILED");

        // THEN: Error is set
        assertThat(query.getError()).isEqualTo("Connection timeout");
        assertThat(query.getStatus()).isEqualTo("FAILED");
    }
}
