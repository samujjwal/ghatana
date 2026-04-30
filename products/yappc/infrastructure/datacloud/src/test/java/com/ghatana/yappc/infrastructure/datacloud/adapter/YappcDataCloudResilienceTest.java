package com.ghatana.yappc.infrastructure.datacloud.adapter;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.platform.governance.security.TenantContext;
import com.ghatana.platform.resilience.CircuitBreaker;
import com.ghatana.platform.resilience.RetryPolicy;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.domain.Identifiable;
import com.ghatana.yappc.infrastructure.datacloud.mapper.YappcEntityMapper;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    void setUp() { // GH-90000
        MockitoAnnotations.openMocks(this); // GH-90000
        TenantContext.setCurrentTenantId(TENANT_ID); // GH-90000
        
        testEntity = new TestEntity(UUID.randomUUID(), "test-name"); // GH-90000
    }

    @Test
    @DisplayName("Retry policy retries on failure and succeeds on third attempt")
    void retryPolicy_retriesOnFailure_succeedsOnThirdAttempt() { // GH-90000
        AtomicInteger attemptCount = new AtomicInteger(0); // GH-90000
        
        when(mockClient.save(anyString(), anyString(), any())) // GH-90000
                .thenAnswer(invocation -> { // GH-90000
                    int attempt = attemptCount.incrementAndGet(); // GH-90000
                    if (attempt < 3) { // GH-90000
                        return Promise.ofException(new RuntimeException("Simulated failure"));
                    }
                    return Promise.of(DataCloudClient.Entity.of("id-123", COLLECTION, Map.of("id", "id-123"))); // GH-90000
                });
        when(mockMapper.toEntityData(any())).thenReturn(Map.of("id", "id-123")); // GH-90000
        when(mockMapper.fromEntity(any(), any())).thenReturn(testEntity); // GH-90000

        RetryPolicy retryPolicy = RetryPolicy.builder() // GH-90000
                .maxRetries(3) // GH-90000
                .initialDelay(Duration.ofMillis(10)) // GH-90000
                .maxDelay(Duration.ofMillis(100)) // GH-90000
                .build(); // GH-90000

        Eventloop eventloop = eventloop(); // GH-90000
        TestEntity result = runPromise(() ->  // GH-90000
            retryPolicy.execute(eventloop, () -> mockClient.save(TENANT_ID, COLLECTION, Map.of())) // GH-90000
                .then(entity -> Promise.of(mockMapper.fromEntity(entity, TestEntity.class))) // GH-90000
        );

        assertThat(result).isNotNull(); // GH-90000
        assertThat(attemptCount.get()).isEqualTo(3); // GH-90000
        verify(mockClient, times(3)).save(anyString(), anyString(), any()); // GH-90000
    }

    @Test
    @DisplayName("Retry policy fails after max retries exhausted")
    void retryPolicy_failsAfterMaxRetries() { // GH-90000
        AtomicInteger attemptCount = new AtomicInteger(0); // GH-90000
        
        when(mockClient.save(anyString(), anyString(), any())) // GH-90000
                .thenAnswer(invocation -> { // GH-90000
                    attemptCount.incrementAndGet(); // GH-90000
                    return Promise.ofException(new RuntimeException("Simulated failure"));
                });

        RetryPolicy retryPolicy = RetryPolicy.builder() // GH-90000
                .maxRetries(2) // GH-90000
                .initialDelay(Duration.ofMillis(10)) // GH-90000
                .maxDelay(Duration.ofMillis(50)) // GH-90000
                .build(); // GH-90000

        Eventloop eventloop = eventloop(); // GH-90000
        
        assertThatThrownBy(() -> runPromise(() ->  // GH-90000
            retryPolicy.execute(eventloop, () -> mockClient.save(TENANT_ID, COLLECTION, Map.of())) // GH-90000
        ))
                .isInstanceOf(RuntimeException.class) // GH-90000
                .hasMessageContaining("Simulated failure");

        assertThat(attemptCount.get()).isEqualTo(3); // initial + 2 retries // GH-90000
    }

    @Test
    @DisplayName("Circuit breaker opens after failure threshold")
    void circuitBreaker_opensAfterFailureThreshold() { // GH-90000
        when(mockClient.findById(anyString(), anyString(), anyString())) // GH-90000
                .thenReturn(Promise.ofException(new RuntimeException("Simulated failure")));

        CircuitBreaker circuitBreaker = CircuitBreaker.builder("test-circuit")
                .failureThreshold(3) // GH-90000
                .successThreshold(2) // GH-90000
                .resetTimeout(Duration.ofSeconds(1)) // GH-90000
                .build(); // GH-90000

        Eventloop eventloop = eventloop(); // GH-90000

        // Execute failures until circuit opens
        for (int i = 0; i < 3; i++) { // GH-90000
            assertThatThrownBy(() -> runPromise(() ->  // GH-90000
                circuitBreaker.execute(eventloop, () -> mockClient.findById(TENANT_ID, COLLECTION, "id-123")) // GH-90000
            ))
                    .isInstanceOf(RuntimeException.class); // GH-90000
        }

        // Circuit should now be OPEN
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN); // GH-90000

        // Next call should be rejected immediately
        assertThatThrownBy(() -> runPromise(() ->  // GH-90000
            circuitBreaker.execute(eventloop, () -> mockClient.findById(TENANT_ID, COLLECTION, "id-123")) // GH-90000
        ))
                .isInstanceOf(CircuitBreaker.CircuitBreakerOpenException.class); // GH-90000
    }

    @Test
    @DisplayName("Circuit breaker transitions to CLOSED after successful probes")
    void circuitBreaker_transitionsToClosedAfterSuccessfulProbes() { // GH-90000
        AtomicInteger callCount = new AtomicInteger(0); // GH-90000
        
        when(mockClient.findById(anyString(), anyString(), anyString())) // GH-90000
                .thenAnswer(invocation -> { // GH-90000
                    int call = callCount.incrementAndGet(); // GH-90000
                    if (call <= 3) { // GH-90000
                        return Promise.ofException(new RuntimeException("Simulated failure"));
                    }
                    return Promise.of(Optional.of(DataCloudClient.Entity.of("id-123", COLLECTION, Map.of()))); // GH-90000
                });

        CircuitBreaker circuitBreaker = CircuitBreaker.builder("test-circuit")
                .failureThreshold(3) // GH-90000
                .successThreshold(2) // GH-90000
                .resetTimeout(Duration.ofMillis(100)) // GH-90000
                .build(); // GH-90000

        Eventloop eventloop = eventloop(); // GH-90000

        // Execute failures to open circuit
        for (int i = 0; i < 3; i++) { // GH-90000
            try {
                runPromise(() -> circuitBreaker.execute(eventloop, () ->  // GH-90000
                    mockClient.findById(TENANT_ID, COLLECTION, "id-123") // GH-90000
                ));
            } catch (Exception e) { // GH-90000
                // Expected failures
            }
        }

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN); // GH-90000

        // Wait for reset timeout
        try {
            Thread.sleep(150); // GH-90000
        } catch (InterruptedException e) { // GH-90000
            Thread.currentThread().interrupt(); // GH-90000
        }

        // Successful probes should close circuit
        for (int i = 0; i < 2; i++) { // GH-90000
            runPromise(() -> circuitBreaker.execute(eventloop, () ->  // GH-90000
                mockClient.findById(TENANT_ID, COLLECTION, "id-123") // GH-90000
            ));
        }

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED); // GH-90000
    }

    @Test
    @DisplayName("Repository uses retry policy and circuit breaker together")
    void repository_usesRetryAndCircuitBreaker() { // GH-90000
        AtomicInteger attemptCount = new AtomicInteger(0); // GH-90000
        
        when(mockClient.findById(anyString(), anyString(), anyString())) // GH-90000
                .thenAnswer(invocation -> { // GH-90000
                    int attempt = attemptCount.incrementAndGet(); // GH-90000
                    if (attempt == 1) { // GH-90000
                        return Promise.ofException(new RuntimeException("First failure"));
                    }
                    if (attempt == 2) { // GH-90000
                        return Promise.ofException(new RuntimeException("Second failure"));
                    }
                    return Promise.of(Optional.of(DataCloudClient.Entity.of("id-123", COLLECTION, Map.of("id", "id-123")))); // GH-90000
                });
        when(mockMapper.fromEntity(any(), any())).thenReturn(testEntity); // GH-90000

        CircuitBreaker circuitBreaker = CircuitBreaker.builder("test-circuit")
                .failureThreshold(5) // GH-90000
                .successThreshold(2) // GH-90000
                .resetTimeout(Duration.ofSeconds(30)) // GH-90000
                .build(); // GH-90000

        RetryPolicy retryPolicy = RetryPolicy.builder() // GH-90000
                .maxRetries(2) // GH-90000
                .initialDelay(Duration.ofMillis(10)) // GH-90000
                .maxDelay(Duration.ofMillis(50)) // GH-90000
                .build(); // GH-90000

        YappcDataCloudRepository<TestEntity> repository = new YappcDataCloudRepository<>( // GH-90000
            mockClient, mockMapper, COLLECTION, TestEntity.class, null, null, retryPolicy, circuitBreaker);

        Eventloop eventloop = eventloop(); // GH-90000
        Optional<TestEntity> result = runPromise(() -> repository.findById(testEntity.getId())); // GH-90000

        assertThat(result).isPresent(); // GH-90000
        assertThat(result.get().getId()).isEqualTo(testEntity.getId()); // GH-90000
        assertThat(attemptCount.get()).isEqualTo(3); // initial + 2 retries // GH-90000
    }

    // Test entity implementation
    static class TestEntity implements Identifiable<UUID> {
        private final UUID id;
        private final String name;

        TestEntity(UUID id, String name) { // GH-90000
            this.id = id;
            this.name = name;
        }

        @Override
        public UUID getId() { // GH-90000
            return id;
        }

        public String getName() { // GH-90000
            return name;
        }
    }
}
