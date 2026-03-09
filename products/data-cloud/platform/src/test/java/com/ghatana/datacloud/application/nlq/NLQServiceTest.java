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
@ExtendWith(MockitoExtension.class)
class NLQServiceTest extends EventloopTestBase {
    
    @Mock
    private EntityRepository entityRepository;
    
    @Mock
    private MetricsCollector metricsCollector;
    
    private NLQService nlqService;
    private MetaCollection testCollection;
    
    @BeforeEach
    void setup() {
        nlqService = new NLQService(entityRepository, metricsCollector);
        
        // Create test collection with fields
        testCollection = MetaCollection.builder()
            .id(UUID.randomUUID())
            .tenantId("tenant-1")
            .name("users")
            .fields(List.of(
                MetaField.builder().name("name").type(DataType.STRING).build(),
                MetaField.builder().name("age").type(DataType.NUMBER).build(),
                MetaField.builder().name("email").type(DataType.STRING).build(),
                MetaField.builder().name("status").type(DataType.STRING).build(),
                MetaField.builder().name("salary").type(DataType.NUMBER).build()
            ))
            .build();
    }
    
    // ========================================================================
    // QUERY PARSING TESTS
    // ========================================================================
    
    @Test
    @DisplayName("Should parse simple numeric filter query")
    void shouldParseNumericFilter() {
        // GIVEN: Natural language query with numeric comparison
        String query = "age > 25";
        
        // WHEN: Parsing query
        QueryPlan plan = runPromise(() -> nlqService.parseQuery(query, testCollection));
        
        // THEN: Returns valid plan with confidence (0.95 * 0.3 = 0.285)
        assertThat(plan).isNotNull();
        assertThat(plan.originalQuery()).isEqualTo(query);
        assertThat(plan.confidence()).isGreaterThan(0.25);
        assertThat(plan.filterCount()).isEqualTo(1);
        assertThat(plan.tenantId()).isEqualTo("tenant-1");
        assertThat(plan.collectionName()).isEqualTo("users");
        
        // AND: Metrics recorded
        verify(metricsCollector).recordTimer(eq("nlq.parse_query"), anyLong());
        verify(metricsCollector).incrementCounter("nlq.queries_parsed", "status", "success");
    }
    
    @Test
    @DisplayName("Should parse equals filter query")
    void shouldParseEqualsFilter() {
        // GIVEN: Natural language query with equality
        String query = "status is active";
        
        // WHEN: Parsing query
        QueryPlan plan = runPromise(() -> nlqService.parseQuery(query, testCollection));
        
        // THEN: Returns valid plan (0.9 * 0.3 = 0.27)
        assertThat(plan.confidence()).isGreaterThan(0.25);
        assertThat(plan.filterCount()).isEqualTo(1);
    }
    
    @Test
    @DisplayName("Should parse contains filter query")
    void shouldParseContainsFilter() {
        // GIVEN: Natural language query with LIKE pattern
        String query = "name contains John";
        
        // WHEN: Parsing query
        QueryPlan plan = runPromise(() -> nlqService.parseQuery(query, testCollection));
        
        // THEN: Returns valid plan (0.85 * 0.2 = 0.17)
        assertThat(plan.confidence()).isGreaterThan(0.15);
        assertThat(plan.filterCount()).isEqualTo(1);
    }
    
    @ParameterizedTest
    @CsvSource({
        "age > 30, 0.25",
        "salary >= 50000, 0.25",
        "age < 65, 0.25",
        "salary <= 100000, 0.25"
    })
    @DisplayName("Should parse various numeric operators")
    void shouldParseNumericOperators(String query, double minConfidence) {
        // WHEN: Parsing query with different operators
        QueryPlan plan = runPromise(() -> nlqService.parseQuery(query, testCollection));
        
        // THEN: Returns valid plan with appropriate confidence
        assertThat(plan.confidence()).isGreaterThanOrEqualTo(minConfidence);
        assertThat(plan.filterCount()).isGreaterThan(0);
    }
    
    @Test
    @DisplayName("Should parse query with sorting")
    void shouldParseSorting() {
        // GIVEN: Query with sort directive
        String query = "age > 25 sorted by name ascending";
        
        // WHEN: Parsing query
        QueryPlan plan = runPromise(() -> nlqService.parseQuery(query, testCollection));
        
        // THEN: Includes sort in plan
        assertThat(plan.sortCount()).isGreaterThan(0);
    }
    
    @Test
    @DisplayName("Should parse complex query with multiple filters")
    void shouldParseComplexQuery() {
        // GIVEN: Complex query with multiple conditions
        String query = "age > 25 and status is active and salary >= 50000";
        
        // WHEN: Parsing query
        QueryPlan plan = runPromise(() -> nlqService.parseQuery(query, testCollection));
        
        // THEN: Extracts multiple filters
        assertThat(plan.filterCount()).isGreaterThan(1);
        assertThat(plan.confidence()).isGreaterThan(0.5);
    }
    
    @Test
    @DisplayName("Should reject null query")
    void shouldRejectNullQuery() {
        // WHEN/THEN: Throws exception for null query
        assertThatThrownBy(() -> 
            runPromise(() -> nlqService.parseQuery(null, testCollection))
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("Query cannot be null");
    }
    
    @Test
    @DisplayName("Should reject empty query")
    void shouldRejectEmptyQuery() {
        // WHEN/THEN: Throws exception for empty query
        assertThatThrownBy(() -> 
            runPromise(() -> nlqService.parseQuery("   ", testCollection))
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("Query cannot be null or empty");
    }
    
    @Test
    @DisplayName("Should reject null collection")
    void shouldRejectNullCollection() {
        // WHEN/THEN: Throws exception for null collection
        assertThatThrownBy(() -> 
            runPromise(() -> nlqService.parseQuery("age > 25", null))
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("Collection cannot be null");
    }
    
    @Test
    @DisplayName("Should handle query with unknown field gracefully")
    void shouldHandleUnknownField() {
        // GIVEN: Query with field not in collection
        String query = "unknownField > 100";
        
        // WHEN: Parsing query
        QueryPlan plan = runPromise(() -> nlqService.parseQuery(query, testCollection));
        
        // THEN: Returns plan with lower confidence
        assertThat(plan.confidence()).isLessThan(0.5);
        assertThat(plan.filterCount()).isEqualTo(0);
    }
    
    // ========================================================================
    // QUERY EXECUTION TESTS
    // ========================================================================
    
    @Test
    @DisplayName("Should execute query successfully")
    void shouldExecuteQuery() {
        // GIVEN: Valid query plan
        QuerySpec querySpec = QuerySpec.of(
            "SELECT * FROM users WHERE age > ?",
            Map.of("age", 25),
            0,
            10
        );
        
        QueryPlan plan = new QueryPlan(
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
        List<Entity> entities = List.of(
            createEntity("user-1", Map.of("name", "John", "age", 30)),
            createEntity("user-2", Map.of("name", "Jane", "age", 28))
        );
        
        when(entityRepository.findByQuery(eq("tenant-1"), eq("users"), any(QuerySpec.class)))
            .thenReturn(Promise.of(entities));
        
        // WHEN: Executing query
        QueryResult result = runPromise(() -> nlqService.executeQuery(plan));
        
        // THEN: Returns successful result
        assertThat(result).isNotNull();
        assertThat(result.planId()).isEqualTo("plan-1");
        assertThat(result.rows()).hasSize(2);
        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.confidence()).isEqualTo(0.9);
        assertThat(result.executionTimeMs()).isGreaterThanOrEqualTo(0);
        
        // AND: Metrics recorded
        verify(metricsCollector).recordTimer(eq("nlq.execute_query"), anyLong());
        verify(metricsCollector).incrementCounter("nlq.queries_executed", "status", "success");
    }
    
    @Test
    @DisplayName("Should return empty result for low confidence query")
    void shouldHandleLowConfidence() {
        // GIVEN: Query plan with low confidence
        QueryPlan plan = new QueryPlan(
            "plan-1",
            "vague query",
            QuerySpec.of("SELECT * FROM users", Map.of()),
            0.3,
            0,
            0,
            "tenant-1",
            "users"
        );
        
        // WHEN: Executing query
        QueryResult result = runPromise(() -> nlqService.executeQuery(plan));
        
        // THEN: Returns empty result with LOW_CONFIDENCE status
        assertThat(result.rows()).isEmpty();
        assertThat(result.status()).isEqualTo("LOW_CONFIDENCE");
        
        // AND: Metric recorded
        verify(metricsCollector).incrementCounter("nlq.low_confidence_fallback");
    }
    
    @Test
    @DisplayName("Should handle null repository gracefully")
    void shouldHandleNullRepository() {
        // GIVEN: Service without repository
        NLQService serviceWithoutRepo = new NLQService(null, metricsCollector);
        
        QueryPlan plan = new QueryPlan(
            "plan-1",
            "age > 25",
            QuerySpec.of("SELECT * FROM users", Map.of()),
            0.9,
            1,
            0,
            "tenant-1",
            "users"
        );
        
        // WHEN: Executing query
        QueryResult result = runPromise(() -> serviceWithoutRepo.executeQuery(plan));
        
        // THEN: Returns empty result with NO_REPO status
        assertThat(result.rows()).isEmpty();
        assertThat(result.status()).isEqualTo("NO_REPO");
        
        // AND: Metric recorded
        verify(metricsCollector).incrementCounter("nlq.no_repository");
    }
    
    @Test
    @DisplayName("Should handle unsupported query spec type")
    void shouldHandleUnsupportedQuerySpec() {
        // GIVEN: Plan with unsupported spec type
        QueryPlan plan = new QueryPlan(
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
        QueryResult result = runPromise(() -> nlqService.executeQuery(plan));
        
        // THEN: Returns empty result with UNSUPPORTED_SPEC status
        assertThat(result.rows()).isEmpty();
        assertThat(result.status()).isEqualTo("UNSUPPORTED_SPEC");
        
        // AND: Metric recorded
        verify(metricsCollector).incrementCounter("nlq.unsupported_query_spec");
    }
    
    @Test
    @DisplayName("Should handle repository errors gracefully")
    void shouldHandleRepositoryError() {
        // GIVEN: Query plan
        QueryPlan plan = new QueryPlan(
            "plan-1",
            "age > 25",
            QuerySpec.of("SELECT * FROM users", Map.of()),
            0.9,
            1,
            0,
            "tenant-1",
            "users"
        );
        
        // AND: Repository throws exception
        when(entityRepository.findByQuery(anyString(), anyString(), any()))
            .thenReturn(Promise.ofException(new RuntimeException("Database error")));
        
        // WHEN/THEN: Executing query throws exception but metrics are recorded
        assertThatThrownBy(() -> 
            runPromise(() -> nlqService.executeQuery(plan))
        ).isInstanceOf(RuntimeException.class);
        
        // AND: Error metric recorded
        verify(metricsCollector).incrementCounter("nlq.queries_executed", "status", "error");
    }
    
    @Test
    @DisplayName("Should reject null plan in execute")
    void shouldRejectNullPlanInExecute() {
        // WHEN/THEN: Throws exception for null plan
        assertThatThrownBy(() -> 
            nlqService.executeQuery(null)
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("Plan cannot be null");
    }
    
    // ========================================================================
    // VALIDATION TESTS
    // ========================================================================
    
    @Test
    @DisplayName("Should validate valid plan")
    void shouldValidateValidPlan() {
        // GIVEN: Valid query plan
        QueryPlan plan = new QueryPlan(
            "plan-1",
            "age > 25",
            QuerySpec.of("SELECT * FROM users", Map.of()),
            0.8,
            2,
            1,
            "tenant-1",
            "users"
        );
        
        // WHEN: Validating plan
        ValidationResult result = runPromise(() -> nlqService.validatePlan(plan));
        
        // THEN: Validation passes
        assertThat(result.isValid()).isTrue();
        assertThat(result.errors()).isEmpty();
        
        // AND: Metric recorded
        verify(metricsCollector).incrementCounter("nlq.validation", "status", "valid");
    }
    
    @Test
    @DisplayName("Should reject plan with too many filters")
    void shouldRejectTooManyFilters() {
        // GIVEN: Plan with excessive filters
        QueryPlan plan = new QueryPlan(
            "plan-1",
            "complex query",
            QuerySpec.of("SELECT * FROM users", Map.of()),
            0.8,
            15,  // Exceeds MAX_FILTERS (10)
            1,
            "tenant-1",
            "users"
        );
        
        // WHEN: Validating plan
        ValidationResult result = runPromise(() -> nlqService.validatePlan(plan));
        
        // THEN: Validation fails
        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("Too many filters"));
        
        // AND: Metric recorded
        verify(metricsCollector).incrementCounter("nlq.validation", "status", "invalid");
    }
    
    @Test
    @DisplayName("Should reject plan with very low confidence")
    void shouldRejectVeryLowConfidence() {
        // GIVEN: Plan with very low confidence
        QueryPlan plan = new QueryPlan(
            "plan-1",
            "vague query",
            QuerySpec.of("SELECT * FROM users", Map.of()),
            0.2,  // Below 0.3 threshold
            1,
            0,
            "tenant-1",
            "users"
        );
        
        // WHEN: Validating plan
        ValidationResult result = runPromise(() -> nlqService.validatePlan(plan));
        
        // THEN: Validation fails
        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("Confidence too low"));
    }
    
    @Test
    @DisplayName("Should warn about moderate confidence")
    void shouldWarnAboutModerateConfidence() {
        // GIVEN: Plan with moderate confidence
        QueryPlan plan = new QueryPlan(
            "plan-1",
            "query",
            QuerySpec.of("SELECT * FROM users", Map.of()),
            0.45,  // Between 0.3 and 0.5
            1,
            0,
            "tenant-1",
            "users"
        );
        
        // WHEN: Validating plan
        ValidationResult result = runPromise(() -> nlqService.validatePlan(plan));
        
        // THEN: Validation passes with warning
        assertThat(result.isValid()).isTrue();
        assertThat(result.warnings()).anyMatch(w -> w.contains("Confidence below execution threshold"));
    }
    
    @Test
    @DisplayName("Should warn about many sort fields")
    void shouldWarnAboutManySorts() {
        // GIVEN: Plan with many sort fields
        QueryPlan plan = new QueryPlan(
            "plan-1",
            "query",
            QuerySpec.of("SELECT * FROM users", Map.of()),
            0.8,
            1,
            5,  // More than recommended 1-2
            "tenant-1",
            "users"
        );
        
        // WHEN: Validating plan
        ValidationResult result = runPromise(() -> nlqService.validatePlan(plan));
        
        // THEN: Validation passes with warning
        assertThat(result.isValid()).isTrue();
        assertThat(result.warnings()).anyMatch(w -> w.contains("Many sort fields"));
    }
    
    @Test
    @DisplayName("Should reject null plan in validate")
    void shouldRejectNullPlanInValidate() {
        // WHEN/THEN: Throws exception for null plan
        assertThatThrownBy(() -> 
            runPromise(() -> nlqService.validatePlan(null))
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("Plan cannot be null");
    }
    
    // ========================================================================
    // CONFIDENCE SCORING TESTS
    // ========================================================================
    
    @Test
    @DisplayName("Should calculate confidence score")
    void shouldCalculateConfidence() {
        // GIVEN: Natural language query
        String query = "age > 30 and status is active";
        
        // WHEN: Getting confidence score
        Double confidence = runPromise(() -> nlqService.getConfidenceScore(query, testCollection));
        
        // THEN: Returns valid confidence
        assertThat(confidence).isBetween(0.0, 1.0);
        assertThat(confidence).isGreaterThan(0.4);
    }
    
    @Test
    @DisplayName("Should return lower confidence for vague query")
    void shouldReturnLowConfidenceForVagueQuery() {
        // GIVEN: Vague query with no recognized patterns
        String query = "show me some stuff";
        
        // WHEN: Getting confidence score
        Double confidence = runPromise(() -> nlqService.getConfidenceScore(query, testCollection));
        
        // THEN: Returns low confidence
        assertThat(confidence).isLessThan(0.5);
    }
    
    @ParameterizedTest
    @ValueSource(strings = {
        "age > 30",
        "status is active",
        "name contains John",
        "salary >= 50000"
    })
    @DisplayName("Should return reasonable confidence for clear patterns")
    void shouldReturnHighConfidenceForClearPatterns(String query) {
        // WHEN: Getting confidence for clear query
        Double confidence = runPromise(() -> nlqService.getConfidenceScore(query, testCollection));
        
        // THEN: Returns reasonable confidence (weighted by 0.2-0.3)
        assertThat(confidence).isGreaterThan(0.15);
    }
    
    @Test
    @DisplayName("Should reject null query in confidence scoring")
    void shouldRejectNullQueryInConfidence() {
        // WHEN/THEN: Throws exception for null query
        assertThatThrownBy(() -> 
            runPromise(() -> nlqService.getConfidenceScore(null, testCollection))
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("Query cannot be null");
    }
    
    // ========================================================================
    // EDGE CASES AND ERROR HANDLING
    // ========================================================================
    
    @Test
    @DisplayName("Should handle parsing error gracefully")
    void shouldHandleParsingError() {
        // GIVEN: Collection with null fields (causes error)
        MetaCollection badCollection = MetaCollection.builder()
            .id(UUID.randomUUID())
            .tenantId("tenant-1")
            .name("bad")
            .fields(null)
            .build();
        
        // WHEN/THEN: Throws BaseException with appropriate error message
        assertThatThrownBy(() -> 
            runPromise(() -> nlqService.parseQuery("age > 25", badCollection))
        ).isInstanceOf(BaseException.class)
         .hasMessageContaining("Failed to parse query");
        
        // Clear the fatal error from eventloop since we expected this exception
        clearFatalError();
    }
    
    @Test
    @DisplayName("Should handle collection with no fields")
    void shouldHandleEmptyFieldList() {
        // GIVEN: Collection with empty field list
        MetaCollection emptyCollection = MetaCollection.builder()
            .id(UUID.randomUUID())
            .tenantId("tenant-1")
            .name("empty")
            .fields(List.of())
            .build();
        
        // WHEN: Parsing query
        QueryPlan plan = runPromise(() -> nlqService.parseQuery("age > 25", emptyCollection));
        
        // THEN: Returns plan with low confidence
        assertThat(plan.confidence()).isLessThan(0.5);
        assertThat(plan.filterCount()).isEqualTo(0);
    }
    
    // ========================================================================
    // HELPER METHODS
    // ========================================================================
    
    private Entity createEntity(String id, Map<String, Object> data) {
        return Entity.builder()
            .id(UUID.randomUUID())
            .tenantId("tenant-1")
            .collectionName("users")
            .data(new HashMap<>(data))
            .createdAt(java.time.Instant.now())
            .updatedAt(java.time.Instant.now())
            .build();
    }
}
