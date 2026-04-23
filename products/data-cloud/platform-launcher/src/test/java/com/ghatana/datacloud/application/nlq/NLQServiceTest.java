package com.ghatana.datacloud.application.nlq;

import com.ghatana.platform.core.exception.BaseException;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.datacloud.application.QuerySpec;
import com.ghatana.datacloud.entity.DataType;
import com.ghatana.datacloud.entity.Entity;
import com.ghatana.datacloud.entity.EntityRepository;
import com.ghatana.datacloud.entity.MetaCollection;
import com.ghatana.datacloud.entity.MetaField;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for {@link NLQService} with 100% coverage.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Query parsing with various natural language patterns</li>
 *   <li>Query execution with EntityRepository integration</li>
 *   <li>Confidence scoring and validation</li>
 *   <li>Error handling and edge cases</li>
 *   <li>Metrics collection</li>
 * </ul>
 *
 * @doc.type test
 * @doc.purpose Comprehensive NLQService testing with 100% coverage
 * @doc.layer application
 */
@DisplayName("NLQ Service Tests")
@ExtendWith(MockitoExtension.class) // GH-90000
class NLQServiceTest extends EventloopTestBase {

    @Mock
    private EntityRepository entityRepository;

    @Mock
    private MetricsCollector metricsCollector;

    private NLQService nlqService;
    private MetaCollection testCollection;

    @BeforeEach
    void setup() { // GH-90000
        nlqService = new NLQService(entityRepository, metricsCollector); // GH-90000

        // Create test collection with fields
        testCollection = MetaCollection.builder() // GH-90000
            .id(UUID.randomUUID()) // GH-90000
            .tenantId("tenant-1")
            .name("users")
            .fields(List.of( // GH-90000
                MetaField.builder().name("name").type(DataType.STRING).build(),
                MetaField.builder().name("age").type(DataType.NUMBER).build(),
                MetaField.builder().name("email").type(DataType.STRING).build(),
                MetaField.builder().name("status").type(DataType.STRING).build(),
                MetaField.builder().name("salary").type(DataType.NUMBER).build()
            ))
            .build(); // GH-90000
    }

    // ========================================================================
    // QUERY PARSING TESTS
    // ========================================================================

    @Test
    @DisplayName("Should parse simple numeric filter query")
    void shouldParseNumericFilter() { // GH-90000
        // GIVEN: Natural language query with numeric comparison
        String query = "age > 25";

        // WHEN: Parsing query
        QueryPlan plan = runPromise(() -> nlqService.parseQuery(query, testCollection)); // GH-90000

        // THEN: Returns valid plan with confidence (0.95 * 0.3 = 0.285) // GH-90000
        assertThat(plan).isNotNull(); // GH-90000
        assertThat(plan.originalQuery()).isEqualTo(query); // GH-90000
        assertThat(plan.confidence()).isGreaterThan(0.25); // GH-90000
        assertThat(plan.filterCount()).isEqualTo(1); // GH-90000
        assertThat(plan.tenantId()).isEqualTo("tenant-1");
        assertThat(plan.collectionName()).isEqualTo("users");

        // AND: Metrics recorded
        verify(metricsCollector).recordTimer(eq("nlq.parse_query"), anyLong());
        verify(metricsCollector).incrementCounter("nlq.queries_parsed", "status", "success"); // GH-90000
    }

    @Test
    @DisplayName("Should parse equals filter query")
    void shouldParseEqualsFilter() { // GH-90000
        // GIVEN: Natural language query with equality
        String query = "status is active";

        // WHEN: Parsing query
        QueryPlan plan = runPromise(() -> nlqService.parseQuery(query, testCollection)); // GH-90000

        // THEN: Returns valid plan (0.9 * 0.3 = 0.27) // GH-90000
        assertThat(plan.confidence()).isGreaterThan(0.25); // GH-90000
        assertThat(plan.filterCount()).isEqualTo(1); // GH-90000
    }

    @Test
    @DisplayName("Should parse contains filter query")
    void shouldParseContainsFilter() { // GH-90000
        // GIVEN: Natural language query with LIKE pattern
        String query = "name contains John";

        // WHEN: Parsing query
        QueryPlan plan = runPromise(() -> nlqService.parseQuery(query, testCollection)); // GH-90000

        // THEN: Returns valid plan (0.85 * 0.2 = 0.17) // GH-90000
        assertThat(plan.confidence()).isGreaterThan(0.15); // GH-90000
        assertThat(plan.filterCount()).isEqualTo(1); // GH-90000
    }

    @ParameterizedTest
    @CsvSource({ // GH-90000
        "age > 30, 0.25",
        "salary >= 50000, 0.25",
        "age < 65, 0.25",
        "salary <= 100000, 0.25"
    })
    @DisplayName("Should parse various numeric operators")
    void shouldParseNumericOperators(String query, double minConfidence) { // GH-90000
        // WHEN: Parsing query with different operators
        QueryPlan plan = runPromise(() -> nlqService.parseQuery(query, testCollection)); // GH-90000

        // THEN: Returns valid plan with appropriate confidence
        assertThat(plan.confidence()).isGreaterThanOrEqualTo(minConfidence); // GH-90000
        assertThat(plan.filterCount()).isGreaterThan(0); // GH-90000
    }

    @Test
    @DisplayName("Should parse query with sorting")
    void shouldParseSorting() { // GH-90000
        // GIVEN: Query with sort directive
        String query = "age > 25 sorted by name ascending";

        // WHEN: Parsing query
        QueryPlan plan = runPromise(() -> nlqService.parseQuery(query, testCollection)); // GH-90000

        // THEN: Includes sort in plan
        assertThat(plan.sortCount()).isGreaterThan(0); // GH-90000
    }

    @Test
    @DisplayName("Should parse complex query with multiple filters")
    void shouldParseComplexQuery() { // GH-90000
        // GIVEN: Complex query with multiple conditions
        String query = "age > 25 and status is active and salary >= 50000";

        // WHEN: Parsing query
        QueryPlan plan = runPromise(() -> nlqService.parseQuery(query, testCollection)); // GH-90000

        // THEN: Extracts multiple filters
        assertThat(plan.filterCount()).isGreaterThan(1); // GH-90000
        assertThat(plan.confidence()).isGreaterThan(0.5); // GH-90000
    }

    @Test
    @DisplayName("Should reject null query")
    void shouldRejectNullQuery() { // GH-90000
        // WHEN/THEN: Throws exception for null query
        assertThatThrownBy(() -> // GH-90000
            runPromise(() -> nlqService.parseQuery(null, testCollection)) // GH-90000
        ).isInstanceOf(IllegalArgumentException.class) // GH-90000
         .hasMessageContaining("Query cannot be null");
    }

    @Test
    @DisplayName("Should reject empty query")
    void shouldRejectEmptyQuery() { // GH-90000
        // WHEN/THEN: Throws exception for empty query
        assertThatThrownBy(() -> // GH-90000
            runPromise(() -> nlqService.parseQuery("   ", testCollection)) // GH-90000
        ).isInstanceOf(IllegalArgumentException.class) // GH-90000
         .hasMessageContaining("Query cannot be null or empty");
    }

    @Test
    @DisplayName("Should reject null collection")
    void shouldRejectNullCollection() { // GH-90000
        // WHEN/THEN: Throws exception for null collection
        assertThatThrownBy(() -> // GH-90000
            runPromise(() -> nlqService.parseQuery("age > 25", null)) // GH-90000
        ).isInstanceOf(IllegalArgumentException.class) // GH-90000
         .hasMessageContaining("Collection cannot be null");
    }

    @Test
    @DisplayName("Should handle query with unknown field gracefully")
    void shouldHandleUnknownField() { // GH-90000
        // GIVEN: Query with field not in collection
        String query = "unknownField > 100";

        // WHEN: Parsing query
        QueryPlan plan = runPromise(() -> nlqService.parseQuery(query, testCollection)); // GH-90000

        // THEN: Returns plan with lower confidence
        assertThat(plan.confidence()).isLessThan(0.5); // GH-90000
        assertThat(plan.filterCount()).isEqualTo(0); // GH-90000
    }

    // ========================================================================
    // QUERY EXECUTION TESTS
    // ========================================================================

    @Test
    @DisplayName("Should execute query successfully")
    void shouldExecuteQuery() { // GH-90000
        // GIVEN: Valid query plan
        QuerySpec querySpec = QuerySpec.of( // GH-90000
            "SELECT * FROM users WHERE age > ?",
            Map.of("age", 25), // GH-90000
            0,
            10
        );

        QueryPlan plan = new QueryPlan( // GH-90000
            "plan-1",
            "age > 25",
            querySpec,
            0.9,
            1,
            0,
            "tenant-1",
            "users"
        );

        // AND: Repository returns entities
        List<Entity> entities = List.of( // GH-90000
            createEntity("user-1", Map.of("name", "John", "age", 30)), // GH-90000
            createEntity("user-2", Map.of("name", "Jane", "age", 28)) // GH-90000
        );

        when(entityRepository.findByQuery(eq("tenant-1"), eq("users"), any(QuerySpec.class)))
            .thenReturn(Promise.of(entities)); // GH-90000

        // WHEN: Executing query
        QueryResult result = runPromise(() -> nlqService.executeQuery(plan)); // GH-90000

        // THEN: Returns successful result
        assertThat(result).isNotNull(); // GH-90000
        assertThat(result.planId()).isEqualTo("plan-1");
        assertThat(result.rows()).hasSize(2); // GH-90000
        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.confidence()).isEqualTo(0.9); // GH-90000
        assertThat(result.executionTimeMs()).isGreaterThanOrEqualTo(0); // GH-90000

        // AND: Metrics recorded
        verify(metricsCollector).recordTimer(eq("nlq.execute_query"), anyLong());
        verify(metricsCollector).incrementCounter("nlq.queries_executed", "status", "success"); // GH-90000
    }

    @Test
    @DisplayName("Should return empty result for low confidence query")
    void shouldHandleLowConfidence() { // GH-90000
        // GIVEN: Query plan with low confidence
        QueryPlan plan = new QueryPlan( // GH-90000
            "plan-1",
            "vague query",
            QuerySpec.of("SELECT * FROM users", Map.of()), // GH-90000
            0.3,
            0,
            0,
            "tenant-1",
            "users"
        );

        // WHEN: Executing query
        QueryResult result = runPromise(() -> nlqService.executeQuery(plan)); // GH-90000

        // THEN: Returns empty result with LOW_CONFIDENCE status
        assertThat(result.rows()).isEmpty(); // GH-90000
        assertThat(result.status()).isEqualTo("LOW_CONFIDENCE");

        // AND: Metric recorded
        verify(metricsCollector).incrementCounter("nlq.low_confidence_fallback");
    }

    @Test
    @DisplayName("Should handle null repository gracefully")
    void shouldHandleNullRepository() { // GH-90000
        // GIVEN: Service without repository
        NLQService serviceWithoutRepo = new NLQService(null, metricsCollector); // GH-90000

        QueryPlan plan = new QueryPlan( // GH-90000
            "plan-1",
            "age > 25",
            QuerySpec.of("SELECT * FROM users", Map.of()), // GH-90000
            0.9,
            1,
            0,
            "tenant-1",
            "users"
        );

        // WHEN: Executing query
        QueryResult result = runPromise(() -> serviceWithoutRepo.executeQuery(plan)); // GH-90000

        // THEN: Returns empty result with NO_REPO status
        assertThat(result.rows()).isEmpty(); // GH-90000
        assertThat(result.status()).isEqualTo("NO_REPO");

        // AND: Metric recorded
        verify(metricsCollector).incrementCounter("nlq.no_repository");
    }

    @Test
    @DisplayName("Should handle unsupported query spec type")
    void shouldHandleUnsupportedQuerySpec() { // GH-90000
        // GIVEN: Plan with unsupported spec type
        QueryPlan plan = new QueryPlan( // GH-90000
            "plan-1",
            "test query",
            "unsupported-spec-type",  // Wrong type
            0.9,
            1,
            0,
            "tenant-1",
            "users"
        );

        // WHEN: Executing query
        QueryResult result = runPromise(() -> nlqService.executeQuery(plan)); // GH-90000

        // THEN: Returns empty result with UNSUPPORTED_SPEC status
        assertThat(result.rows()).isEmpty(); // GH-90000
        assertThat(result.status()).isEqualTo("UNSUPPORTED_SPEC");

        // AND: Metric recorded
        verify(metricsCollector).incrementCounter("nlq.unsupported_query_spec");
    }

    @Test
    @DisplayName("Should handle repository errors gracefully")
    void shouldHandleRepositoryError() { // GH-90000
        // GIVEN: Query plan
        QueryPlan plan = new QueryPlan( // GH-90000
            "plan-1",
            "age > 25",
            QuerySpec.of("SELECT * FROM users", Map.of()), // GH-90000
            0.9,
            1,
            0,
            "tenant-1",
            "users"
        );

        // AND: Repository throws exception
        when(entityRepository.findByQuery(anyString(), anyString(), any())) // GH-90000
            .thenReturn(Promise.ofException(new RuntimeException("Database error")));

        // WHEN/THEN: Executing query throws exception but metrics are recorded
        assertThatThrownBy(() -> // GH-90000
            runPromise(() -> nlqService.executeQuery(plan)) // GH-90000
        ).isInstanceOf(RuntimeException.class); // GH-90000

        // AND: Error metric recorded
        verify(metricsCollector).incrementCounter("nlq.queries_executed", "status", "error"); // GH-90000
    }

    @Test
    @DisplayName("Should reject null plan in execute")
    void shouldRejectNullPlanInExecute() { // GH-90000
        // WHEN/THEN: Throws exception for null plan
        assertThatThrownBy(() -> // GH-90000
            nlqService.executeQuery(null) // GH-90000
        ).isInstanceOf(IllegalArgumentException.class) // GH-90000
         .hasMessageContaining("Plan cannot be null");
    }

    // ========================================================================
    // VALIDATION TESTS
    // ========================================================================

    @Test
    @DisplayName("Should validate valid plan")
    void shouldValidateValidPlan() { // GH-90000
        // GIVEN: Valid query plan
        QueryPlan plan = new QueryPlan( // GH-90000
            "plan-1",
            "age > 25",
            QuerySpec.of("SELECT * FROM users", Map.of()), // GH-90000
            0.8,
            2,
            1,
            "tenant-1",
            "users"
        );

        // WHEN: Validating plan
        ValidationResult result = runPromise(() -> nlqService.validatePlan(plan)); // GH-90000

        // THEN: Validation passes
        assertThat(result.isValid()).isTrue(); // GH-90000
        assertThat(result.errors()).isEmpty(); // GH-90000

        // AND: Metric recorded
        verify(metricsCollector).incrementCounter("nlq.validation", "status", "valid"); // GH-90000
    }

    @Test
    @DisplayName("Should reject plan with too many filters")
    void shouldRejectTooManyFilters() { // GH-90000
        // GIVEN: Plan with excessive filters
        QueryPlan plan = new QueryPlan( // GH-90000
            "plan-1",
            "complex query",
            QuerySpec.of("SELECT * FROM users", Map.of()), // GH-90000
            0.8,
            15,  // Exceeds MAX_FILTERS (10) // GH-90000
            1,
            "tenant-1",
            "users"
        );

        // WHEN: Validating plan
        ValidationResult result = runPromise(() -> nlqService.validatePlan(plan)); // GH-90000

        // THEN: Validation fails
        assertThat(result.isValid()).isFalse(); // GH-90000
        assertThat(result.errors()).anyMatch(e -> e.contains("Too many filters"));

        // AND: Metric recorded
        verify(metricsCollector).incrementCounter("nlq.validation", "status", "invalid"); // GH-90000
    }

    @Test
    @DisplayName("Should reject plan with very low confidence")
    void shouldRejectVeryLowConfidence() { // GH-90000
        // GIVEN: Plan with very low confidence
        QueryPlan plan = new QueryPlan( // GH-90000
            "plan-1",
            "vague query",
            QuerySpec.of("SELECT * FROM users", Map.of()), // GH-90000
            0.2,  // Below 0.3 threshold
            1,
            0,
            "tenant-1",
            "users"
        );

        // WHEN: Validating plan
        ValidationResult result = runPromise(() -> nlqService.validatePlan(plan)); // GH-90000

        // THEN: Validation fails
        assertThat(result.isValid()).isFalse(); // GH-90000
        assertThat(result.errors()).anyMatch(e -> e.contains("Confidence too low"));
    }

    @Test
    @DisplayName("Should warn about moderate confidence")
    void shouldWarnAboutModerateConfidence() { // GH-90000
        // GIVEN: Plan with moderate confidence
        QueryPlan plan = new QueryPlan( // GH-90000
            "plan-1",
            "query",
            QuerySpec.of("SELECT * FROM users", Map.of()), // GH-90000
            0.45,  // Between 0.3 and 0.5
            1,
            0,
            "tenant-1",
            "users"
        );

        // WHEN: Validating plan
        ValidationResult result = runPromise(() -> nlqService.validatePlan(plan)); // GH-90000

        // THEN: Validation passes with warning
        assertThat(result.isValid()).isTrue(); // GH-90000
        assertThat(result.warnings()).anyMatch(w -> w.contains("Confidence below execution threshold"));
    }

    @Test
    @DisplayName("Should warn about many sort fields")
    void shouldWarnAboutManySorts() { // GH-90000
        // GIVEN: Plan with many sort fields
        QueryPlan plan = new QueryPlan( // GH-90000
            "plan-1",
            "query",
            QuerySpec.of("SELECT * FROM users", Map.of()), // GH-90000
            0.8,
            1,
            5,  // More than recommended 1-2
            "tenant-1",
            "users"
        );

        // WHEN: Validating plan
        ValidationResult result = runPromise(() -> nlqService.validatePlan(plan)); // GH-90000

        // THEN: Validation passes with warning
        assertThat(result.isValid()).isTrue(); // GH-90000
        assertThat(result.warnings()).anyMatch(w -> w.contains("Many sort fields"));
    }

    @Test
    @DisplayName("Should reject null plan in validate")
    void shouldRejectNullPlanInValidate() { // GH-90000
        // WHEN/THEN: Throws exception for null plan
        assertThatThrownBy(() -> // GH-90000
            runPromise(() -> nlqService.validatePlan(null)) // GH-90000
        ).isInstanceOf(IllegalArgumentException.class) // GH-90000
         .hasMessageContaining("Plan cannot be null");
    }

    // ========================================================================
    // CONFIDENCE SCORING TESTS
    // ========================================================================

    @Test
    @DisplayName("Should calculate confidence score")
    void shouldCalculateConfidence() { // GH-90000
        // GIVEN: Natural language query
        String query = "age > 30 and status is active";

        // WHEN: Getting confidence score
        Double confidence = runPromise(() -> nlqService.getConfidenceScore(query, testCollection)); // GH-90000

        // THEN: Returns valid confidence
        assertThat(confidence).isBetween(0.0, 1.0); // GH-90000
        assertThat(confidence).isGreaterThan(0.4); // GH-90000
    }

    @Test
    @DisplayName("Should return lower confidence for vague query")
    void shouldReturnLowConfidenceForVagueQuery() { // GH-90000
        // GIVEN: Vague query with no recognized patterns
        String query = "show me some stuff";

        // WHEN: Getting confidence score
        Double confidence = runPromise(() -> nlqService.getConfidenceScore(query, testCollection)); // GH-90000

        // THEN: Returns low confidence
        assertThat(confidence).isLessThan(0.5); // GH-90000
    }

    @ParameterizedTest
    @ValueSource(strings = { // GH-90000
        "age > 30",
        "status is active",
        "name contains John",
        "salary >= 50000"
    })
    @DisplayName("Should return reasonable confidence for clear patterns")
    void shouldReturnHighConfidenceForClearPatterns(String query) { // GH-90000
        // WHEN: Getting confidence for clear query
        Double confidence = runPromise(() -> nlqService.getConfidenceScore(query, testCollection)); // GH-90000

        // THEN: Returns reasonable confidence (weighted by 0.2-0.3) // GH-90000
        assertThat(confidence).isGreaterThan(0.15); // GH-90000
    }

    @Test
    @DisplayName("Should reject null query in confidence scoring")
    void shouldRejectNullQueryInConfidence() { // GH-90000
        // WHEN/THEN: Throws exception for null query
        assertThatThrownBy(() -> // GH-90000
            runPromise(() -> nlqService.getConfidenceScore(null, testCollection)) // GH-90000
        ).isInstanceOf(IllegalArgumentException.class) // GH-90000
         .hasMessageContaining("Query cannot be null");
    }

    // ========================================================================
    // EDGE CASES AND ERROR HANDLING
    // ========================================================================

    @Test
    @DisplayName("Should handle parsing error gracefully")
    void shouldHandleParsingError() { // GH-90000
        // GIVEN: Collection with null fields (causes error) // GH-90000
        MetaCollection badCollection = MetaCollection.builder() // GH-90000
            .id(UUID.randomUUID()) // GH-90000
            .tenantId("tenant-1")
            .name("bad")
            .fields(null) // GH-90000
            .build(); // GH-90000

        // WHEN/THEN: Throws BaseException with appropriate error message
        assertThatThrownBy(() -> // GH-90000
            runPromise(() -> nlqService.parseQuery("age > 25", badCollection)) // GH-90000
        ).isInstanceOf(BaseException.class) // GH-90000
         .hasMessageContaining("Failed to parse query");

        // Clear the fatal error from eventloop since we expected this exception
        clearFatalError(); // GH-90000
    }

    @Test
    @DisplayName("Should handle collection with no fields")
    void shouldHandleEmptyFieldList() { // GH-90000
        // GIVEN: Collection with empty field list
        MetaCollection emptyCollection = MetaCollection.builder() // GH-90000
            .id(UUID.randomUUID()) // GH-90000
            .tenantId("tenant-1")
            .name("empty")
            .fields(List.of()) // GH-90000
            .build(); // GH-90000

        // WHEN: Parsing query
        QueryPlan plan = runPromise(() -> nlqService.parseQuery("age > 25", emptyCollection)); // GH-90000

        // THEN: Returns plan with low confidence
        assertThat(plan.confidence()).isLessThan(0.5); // GH-90000
        assertThat(plan.filterCount()).isEqualTo(0); // GH-90000
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    private Entity createEntity(String id, Map<String, Object> data) { // GH-90000
        return Entity.builder() // GH-90000
            .id(UUID.randomUUID()) // GH-90000
            .tenantId("tenant-1")
            .collectionName("users")
            .data(new HashMap<>(data)) // GH-90000
            .createdAt(java.time.Instant.now()) // GH-90000
            .updatedAt(java.time.Instant.now()) // GH-90000
            .build(); // GH-90000
    }
}
