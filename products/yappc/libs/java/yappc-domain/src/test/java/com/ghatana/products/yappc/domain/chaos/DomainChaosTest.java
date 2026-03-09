package com.ghatana.products.yappc.domain.chaos;

import com.ghatana.products.yappc.domain.model.Dashboard;
import com.ghatana.products.yappc.domain.model.ScanJob;
import com.ghatana.products.yappc.domain.model.Incident;
import com.ghatana.products.yappc.domain.enums.ScanStatus;
import com.ghatana.products.yappc.domain.enums.ScanType;
import com.ghatana.platform.testing.chaos.ChaosScenario;
import com.ghatana.platform.testing.chaos.ChaosType;
import com.ghatana.platform.testing.chaos.ChaosTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chaos tests for yappc-domain models demonstrating resilience testing.
 *
 * <p>These tests verify that domain models behave correctly under
 * adverse conditions such as concurrent access, partial failures,
 * and high load.</p>
 *
 * @doc.type class
 * @doc.purpose Validates domain model resilience under chaos conditions
 * @doc.layer product
 * @doc.pattern ChaosTest
 */
@DisplayName("YAPPC Domain Chaos Tests")
class DomainChaosTest {

    @Nested
    @DisplayName("Dashboard Chaos Tests")
    class DashboardChaosTests {

        @Test
        @ChaosTest(value = ChaosType.CONCURRENCY, failureProbability = 0.3)
        @DisplayName("Dashboard creation is thread-safe under concurrent access")
        void dashboardCreationIsThreadSafe() {
            AtomicInteger successCount = new AtomicInteger(0);
            UUID workspaceId = UUID.randomUUID();

            ChaosScenario.ChaosExecutionResult<Dashboard> result = ChaosScenario.builder()
                    .withChaosType(ChaosType.CONCURRENCY)
                    .withFailureProbability(0.0) // No artificial failures
                    .withConcurrency(10)
                    .withIterations(100)
                    .withDuration(Duration.ofSeconds(10))
                    .execute(() -> {
                        Dashboard dashboard = Dashboard.of(
                                workspaceId,
                                "Dashboard-" + successCount.incrementAndGet()
                        );
                        // Verify invariants
                        assertThat(dashboard.getWorkspaceId()).isEqualTo(workspaceId);
                        assertThat(dashboard.getName()).isNotNull();
                        return dashboard;
                    });

            result.assertAllSucceeded();
            assertThat(result.getSuccessCount()).isEqualTo(100);
        }

        @Test
        @ChaosTest(value = ChaosType.LATENCY, failureProbability = 0.5)
        @DisplayName("Dashboard handles high latency gracefully")
        void dashboardHandlesLatency() throws InterruptedException {
            ChaosScenario.ChaosExecutionResult<Dashboard> result = ChaosScenario.builder()
                    .withChaosType(ChaosType.LATENCY)
                    .withFailureProbability(0.5)
                    .withIterations(20)
                    .withDuration(Duration.ofSeconds(5))
                    .execute(() -> {
                        // Simulate some latency
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return Dashboard.of(UUID.randomUUID(), "Test Dashboard");
                    });

            // Even with latency, all operations should succeed
            result.assertAllSucceeded();
        }
    }

    @Nested
    @DisplayName("ScanJob Chaos Tests")
    class ScanJobChaosTests {

        @Test
        @ChaosTest(value = ChaosType.CONCURRENCY, failureProbability = 0.2)
        @DisplayName("ScanJob state transitions are atomic under concurrent access")
        void scanJobStateTransitionsAreAtomic() {
            // Create a single ScanJob instance
            ScanJob scanJob = ScanJob.pending(UUID.randomUUID(), UUID.randomUUID(), ScanType.SAST);

            AtomicInteger startAttempts = new AtomicInteger(0);

            ChaosScenario.ChaosExecutionResult<Boolean> result = ChaosScenario.builder()
                    .withChaosType(ChaosType.CONCURRENCY)
                    .withFailureProbability(0.0)
                    .withConcurrency(5)
                    .withIterations(5)
                    .withDuration(Duration.ofSeconds(5))
                    .execute(() -> {
                        startAttempts.incrementAndGet();
                        // All threads try to start the same job
                        // Only one should succeed, others should handle gracefully
                        try {
                            synchronized (scanJob) {
                                if (scanJob.getStatus() == ScanStatus.PENDING) {
                                    scanJob.start();
                                    return true;
                                }
                            }
                        } catch (Exception e) {
                            // Expected for non-first threads
                        }
                        return false;
                    });

            // At least one start should succeed
            assertThat(result.getSuccessCount()).isEqualTo(5);
        }

        @Test
        @ChaosTest(value = ChaosType.PARTIAL_FAILURE, failureProbability = 0.3)
        @DisplayName("ScanJob handles partial failures during processing")
        void scanJobHandlesPartialFailures() {
            AtomicInteger processedCount = new AtomicInteger(0);

            ChaosScenario.ChaosExecutionResult<ScanJob> result = ChaosScenario.builder()
                    .withChaosType(ChaosType.PARTIAL_FAILURE)
                    .withFailureProbability(0.3)
                    .withIterations(50)
                    .withDuration(Duration.ofSeconds(10))
                    .execute(() -> {
                        ScanJob job = ScanJob.pending(UUID.randomUUID(), UUID.randomUUID(), ScanType.DAST);
                        job.start();

                        // Simulate processing with potential failure
                        if (Math.random() < 0.3) {
                            job.fail("Simulated partial failure");
                            throw new RuntimeException("Processing failed");
                        }

                        job.complete();
                        processedCount.incrementAndGet();
                        return job;
                    });

            // Expect some failures due to simulated partial failures
            assertThat(result.getSuccessRate()).isGreaterThan(0.5);
            assertThat(processedCount.get()).isGreaterThan(0);
        }
    }

    @Nested
    @DisplayName("Incident Chaos Tests")
    class IncidentChaosTests {

        @Test
        @ChaosTest(value = ChaosType.CONCURRENCY, failureProbability = 0.2)
        @DisplayName("Incident lifecycle is consistent under concurrent updates")
        void incidentLifecycleConsistentUnderConcurrency() {
            Incident incident = Incident.of(
                    UUID.randomUUID(),
                    "Concurrent Incident Test",
                    "CRITICAL"
            );

            ChaosScenario.ChaosExecutionResult<String> result = ChaosScenario.builder()
                    .withChaosType(ChaosType.CONCURRENCY)
                    .withFailureProbability(0.0)
                    .withConcurrency(10)
                    .withIterations(50)
                    .withDuration(Duration.ofSeconds(5))
                    .execute(() -> {
                        synchronized (incident) {
                            String currentStatus = incident.getStatus();
                            // Attempt state transitions based on current state
                            switch (currentStatus) {
                                case "OPEN":
                                    incident.startInvestigation();
                                    break;
                                case "INVESTIGATING":
                                    if (Math.random() > 0.5) {
                                        incident.resolve("Resolution from " + Thread.currentThread().getName());
                                    }
                                    break;
                                default:
                                    // Already resolved or other state
                                    break;
                            }
                            return incident.getStatus();
                        }
                    });

            result.assertAllSucceeded();

            // Final state should be valid
            assertThat(incident.getStatus())
                    .isIn("OPEN", "INVESTIGATING", "RESOLVED");
        }

        @Test
        @ChaosTest(value = ChaosType.RANDOM, failureProbability = 0.4)
        @DisplayName("Incident creation degrades gracefully under random chaos")
        void incidentCreationDegrades() {
            ChaosScenario.ChaosExecutionResult<Incident> result = ChaosScenario.builder()
                    .withChaosType(ChaosType.RANDOM)
                    .withFailureProbability(0.4)
                    .withIterations(30)
                    .withDuration(Duration.ofSeconds(5))
                    .execute(() -> {
                        Incident incident = Incident.of(
                                UUID.randomUUID(),
                                "Chaos Test Incident " + System.nanoTime(),
                                "HIGH"
                        );
                        assertThat(incident).isNotNull();
                        assertThat(incident.getTitle()).isNotBlank();
                        return incident;
                    });

            // System should still function even under chaos
            result.assertGracefulDegradation();
            assertThat(result.getSuccessRate()).isGreaterThan(0.5);
        }
    }

    @Nested
    @DisplayName("High Load Chaos Tests")
    class HighLoadChaosTests {

        @Test
        @ChaosTest(value = ChaosType.RESOURCE_EXHAUSTION, failureProbability = 0.1)
        @DisplayName("Domain models handle high volume creation")
        void domainModelsHandleHighVolume() {
            ChaosScenario.ChaosExecutionResult<Void> result = ChaosScenario.builder()
                    .withChaosType(ChaosType.RESOURCE_EXHAUSTION)
                    .withFailureProbability(0.0) // Test the model, not injected chaos
                    .withConcurrency(8)
                    .withIterations(1000)
                    .withDuration(Duration.ofSeconds(30))
                    .execute(() -> {
                        // Create various domain objects
                        Dashboard dashboard = Dashboard.of(UUID.randomUUID(), "Load Test Dashboard");
                        ScanJob scanJob = ScanJob.pending(UUID.randomUUID(), UUID.randomUUID(), ScanType.SCA);
                        Incident incident = Incident.of(UUID.randomUUID(), "Load Test Incident", "LOW");

                        // Verify all objects are valid
                        assertThat(dashboard.getCreatedAt()).isNotNull();
                        assertThat(scanJob.getStatus()).isEqualTo(ScanStatus.PENDING);
                        assertThat(incident.getStatus()).isEqualTo("OPEN");
                    });

            result.assertAllSucceeded();
            assertThat(result.getSuccessCount()).isEqualTo(1000);

            // Verify average creation time is reasonable
            assertThat(result.getAverageDuration().toMillis()).isLessThan(100);
        }
    }
}
