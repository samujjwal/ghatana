package com.ghatana.yappc.infrastructure.datacloud.adapter;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.platform.governance.security.TenantContext;
import com.ghatana.platform.resilience.CircuitBreaker;
import com.ghatana.platform.resilience.RetryPolicy;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.products.yappc.domain.Identifiable;
import com.ghatana.yappc.infrastructure.datacloud.mapper.YappcEntityMapper;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Integration tests for YappcDataCloudRepository resilience patterns.
 *
 * <p>Tests retry logic, circuit breaker behavior, and health check integration
 * without requiring actual Data Cloud infrastructure. Uses mock DataCloudClient
 * to simulate failures and verify resilience mechanisms work correctly.
 *
 * @doc.type class
 * @doc.purpose Integration tests for repository resilience patterns
 * @doc.layer infrastructure
 * @doc.pattern Integration Test
 */
@DisplayName("YappcDataCloudRepository Resilience Tests")
class YappcDataCloudResilienceTest extends EventloopTestBase {

    @Mock
    private DataCloudClient mockClient;

    @Mock
    private YappcEntityMapper mockMapper;

    private TestEntity testEntity;
    private static final String TENANT_ID = "test-tenant";
    private static final String COLLECTION = "test-collection";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        TenantContext.setCurrentTenantId(TENANT_ID);
        
        testEntity = new TestEntity(UUID.randomUUID(), "test-name");
    }

    @Test
    @DisplayName("Retry policy retries on failure and succeeds on third attempt")
    void retryPolicy_retriesOnFailure_succeedsOnThirdAttempt() {
        AtomicInteger attemptCount = new AtomicInteger(0);
        
        when(mockClient.save(anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    int attempt = attemptCount.incrementAndGet();
                    if (attempt < 3) {
                        return Promise.ofException(new RuntimeException("Simulated failure"));
                    }
                    return Promise.of(DataCloudClient.Entity.of("id-123", COLLECTION, Map.of("id", "id-123")));
                });
        when(mockMapper.toEntityData(any())).thenReturn(Map.of("id", "id-123"));
        when(mockMapper.fromEntity(any(), any())).thenReturn(testEntity);

        RetryPolicy retryPolicy = RetryPolicy.builder()
                .maxRetries(3)
                .initialDelay(Duration.ofMillis(10))
                .maxDelay(Duration.ofMillis(100))
                .build();

        Eventloop eventloop = getEventloop();
        TestEntity result = runPromise(() -> 
            retryPolicy.execute(eventloop, () -> mockClient.save(TENANT_ID, COLLECTION, Map.of()))
                .then(entity -> Promise.of(mockMapper.fromEntity(entity, TestEntity.class)))
        );

        assertThat(result).isNotNull();
        assertThat(attemptCount.get()).isEqualTo(3);
        verify(mockClient, times(3)).save(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Retry policy fails after max retries exhausted")
    void retryPolicy_failsAfterMaxRetries() {
        AtomicInteger attemptCount = new AtomicInteger(0);
        
        when(mockClient.save(anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    attemptCount.incrementAndGet();
                    return Promise.ofException(new RuntimeException("Simulated failure"));
                });

        RetryPolicy retryPolicy = RetryPolicy.builder()
                .maxRetries(2)
                .initialDelay(Duration.ofMillis(10))
                .maxDelay(Duration.ofMillis(50))
                .build();

        Eventloop eventloop = getEventloop();
        
        assertThatThrownBy(() -> runPromise(() -> 
            retryPolicy.execute(eventloop, () -> mockClient.save(TENANT_ID, COLLECTION, Map.of()))
        ))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Simulated failure");

        assertThat(attemptCount.get()).isEqualTo(3); // initial + 2 retries
    }

    @Test
    @DisplayName("Circuit breaker opens after failure threshold")
    void circuitBreaker_opensAfterFailureThreshold() {
        when(mockClient.findById(anyString(), anyString(), anyString()))
                .thenReturn(Promise.ofException(new RuntimeException("Simulated failure")));

        CircuitBreaker circuitBreaker = CircuitBreaker.builder("test-circuit")
                .failureThreshold(3)
                .successThreshold(2)
                .resetTimeout(Duration.ofSeconds(1))
                .build();

        Eventloop eventloop = getEventloop();

        // Execute failures until circuit opens
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> runPromise(() -> 
                circuitBreaker.execute(eventloop, () -> mockClient.findById(TENANT_ID, COLLECTION, "id-123"))
            ))
                    .isInstanceOf(RuntimeException.class);
        }

        // Circuit should now be OPEN
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // Next call should be rejected immediately
        assertThatThrownBy(() -> runPromise(() -> 
            circuitBreaker.execute(eventloop, () -> mockClient.findById(TENANT_ID, COLLECTION, "id-123"))
        ))
                .isInstanceOf(CircuitBreaker.CircuitBreakerOpenException.class);
    }

    @Test
    @DisplayName("Circuit breaker transitions to CLOSED after successful probes")
    void circuitBreaker_transitionsToClosedAfterSuccessfulProbes() {
        AtomicInteger callCount = new AtomicInteger(0);
        
        when(mockClient.findById(anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    int call = callCount.incrementAndGet();
                    if (call <= 3) {
                        return Promise.ofException(new RuntimeException("Simulated failure"));
                    }
                    return Promise.of(Optional.of(DataCloudClient.Entity.of("id-123", COLLECTION, Map.of())));
                });

        CircuitBreaker circuitBreaker = CircuitBreaker.builder("test-circuit")
                .failureThreshold(3)
                .successThreshold(2)
                .resetTimeout(Duration.ofMillis(100))
                .build();

        Eventloop eventloop = getEventloop();

        // Execute failures to open circuit
        for (int i = 0; i < 3; i++) {
            try {
                runPromise(() -> circuitBreaker.execute(eventloop, () -> 
                    mockClient.findById(TENANT_ID, COLLECTION, "id-123")
                ));
            } catch (Exception e) {
                // Expected failures
            }
        }

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // Wait for reset timeout
        try {
            Thread.sleep(150);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Successful probes should close circuit
        for (int i = 0; i < 2; i++) {
            runPromise(() -> circuitBreaker.execute(eventloop, () -> 
                mockClient.findById(TENANT_ID, COLLECTION, "id-123")
            ));
        }

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("Repository uses retry policy and circuit breaker together")
    void repository_usesRetryAndCircuitBreaker() {
        AtomicInteger attemptCount = new AtomicInteger(0);
        
        when(mockClient.findById(anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    int attempt = attemptCount.incrementAndGet();
                    if (attempt == 1) {
                        return Promise.ofException(new RuntimeException("First failure"));
                    }
                    if (attempt == 2) {
                        return Promise.ofException(new RuntimeException("Second failure"));
                    }
                    return Promise.of(Optional.of(DataCloudClient.Entity.of("id-123", COLLECTION, Map.of("id", "id-123"))));
                });
        when(mockMapper.fromEntity(any(), any())).thenReturn(testEntity);

        CircuitBreaker circuitBreaker = CircuitBreaker.builder("test-circuit")
                .failureThreshold(5)
                .successThreshold(2)
                .resetTimeout(Duration.ofSeconds(30))
                .build();

        RetryPolicy retryPolicy = RetryPolicy.builder()
                .maxRetries(2)
                .initialDelay(Duration.ofMillis(10))
                .maxDelay(Duration.ofMillis(50))
                .build();

        YappcDataCloudRepository<TestEntity> repository = new YappcDataCloudRepository<>(
                mockClient, mockMapper, COLLECTION, TestEntity.class, null, retryPolicy, circuitBreaker);

        Eventloop eventloop = getEventloop();
        Optional<TestEntity> result = runPromise(() -> repository.findById(testEntity.getId()));

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(testEntity.getId());
        assertThat(attemptCount.get()).isEqualTo(3); // initial + 2 retries
    }

    // Test entity implementation
    static class TestEntity implements Identifiable<UUID> {
        private final UUID id;
        private final String name;

        TestEntity(UUID id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public UUID getId() {
            return id;
        }

        public String getName() {
            return name;
        }
    }
}
