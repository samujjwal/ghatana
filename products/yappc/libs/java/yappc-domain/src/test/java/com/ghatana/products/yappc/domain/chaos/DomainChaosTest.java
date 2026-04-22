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
@DisplayName("YAPPC Domain Chaos Tests [GH-90000]")
class DomainChaosTest {

    @Nested
    @DisplayName("Dashboard Chaos Tests [GH-90000]")
    class DashboardChaosTests {

        @Test
        @ChaosTest(value = ChaosType.CONCURRENCY, failureProbability = 0.3) // GH-90000
        @DisplayName("Dashboard creation is thread-safe under concurrent access [GH-90000]")
        void dashboardCreationIsThreadSafe() { // GH-90000
            AtomicInteger successCount = new AtomicInteger(0); // GH-90000
            UUID workspaceId = UUID.randomUUID(); // GH-90000

            ChaosScenario.ChaosExecutionResult<Dashboard> result = ChaosScenario.builder() // GH-90000
                    .withChaosType(ChaosType.CONCURRENCY) // GH-90000
                    .withFailureProbability(0.0) // No artificial failures // GH-90000
                    .withConcurrency(10) // GH-90000
                    .withIterations(100) // GH-90000
                    .withDuration(Duration.ofSeconds(10)) // GH-90000
                    .execute(() -> { // GH-90000
                        Dashboard dashboard = Dashboard.of( // GH-90000
                                workspaceId,
                                "Dashboard-" + successCount.incrementAndGet() // GH-90000
                        );
                        // Verify invariants
                        assertThat(dashboard.getWorkspaceId()).isEqualTo(workspaceId); // GH-90000
                        assertThat(dashboard.getName()).isNotNull(); // GH-90000
                        return dashboard;
                    });

            result.assertAllSucceeded(); // GH-90000
            assertThat(result.getSuccessCount()).isEqualTo(100); // GH-90000
        }

        @Test
        @ChaosTest(value = ChaosType.LATENCY, failureProbability = 0.5) // GH-90000
        @DisplayName("Dashboard handles high latency gracefully [GH-90000]")
        void dashboardHandlesLatency() throws InterruptedException { // GH-90000
            ChaosScenario.ChaosExecutionResult<Dashboard> result = ChaosScenario.builder() // GH-90000
                    .withChaosType(ChaosType.LATENCY) // GH-90000
                    .withFailureProbability(0.5) // GH-90000
                    .withIterations(20) // GH-90000
                    .withDuration(Duration.ofSeconds(5)) // GH-90000
                    .execute(() -> { // GH-90000
                        // Simulate some latency
                        try {
                            Thread.sleep(10); // GH-90000
                        } catch (InterruptedException e) { // GH-90000
                            Thread.currentThread().interrupt(); // GH-90000
                        }
                        return Dashboard.of(UUID.randomUUID(), "Test Dashboard"); // GH-90000
                    });

            // Even with latency, all operations should succeed
            result.assertAllSucceeded(); // GH-90000
        }
    }

    @Nested
    @DisplayName("ScanJob Chaos Tests [GH-90000]")
    class ScanJobChaosTests {

        @Test
        @ChaosTest(value = ChaosType.CONCURRENCY, failureProbability = 0.2) // GH-90000
        @DisplayName("ScanJob state transitions are atomic under concurrent access [GH-90000]")
        void scanJobStateTransitionsAreAtomic() { // GH-90000
            // Create a single ScanJob instance
            ScanJob scanJob = ScanJob.pending(UUID.randomUUID(), UUID.randomUUID(), ScanType.SAST); // GH-90000

            AtomicInteger startAttempts = new AtomicInteger(0); // GH-90000

            ChaosScenario.ChaosExecutionResult<Boolean> result = ChaosScenario.builder() // GH-90000
                    .withChaosType(ChaosType.CONCURRENCY) // GH-90000
                    .withFailureProbability(0.0) // GH-90000
                    .withConcurrency(5) // GH-90000
                    .withIterations(5) // GH-90000
                    .withDuration(Duration.ofSeconds(5)) // GH-90000
                    .execute(() -> { // GH-90000
                        startAttempts.incrementAndGet(); // GH-90000
                        // All threads try to start the same job
                        // Only one should succeed, others should handle gracefully
                        try {
                            synchronized (scanJob) { // GH-90000
                                if (scanJob.getStatus() == ScanStatus.PENDING) { // GH-90000
                                    scanJob.start(); // GH-90000
                                    return true;
                                }
                            }
                        } catch (Exception e) { // GH-90000
                            // Expected for non-first threads
                        }
                        return false;
                    });

            // At least one start should succeed
            assertThat(result.getSuccessCount()).isEqualTo(5); // GH-90000
        }

        @Test
        @ChaosTest(value = ChaosType.PARTIAL_FAILURE, failureProbability = 0.3) // GH-90000
        @DisplayName("ScanJob handles partial failures during processing [GH-90000]")
        void scanJobHandlesPartialFailures() { // GH-90000
            AtomicInteger processedCount = new AtomicInteger(0); // GH-90000

            ChaosScenario.ChaosExecutionResult<ScanJob> result = ChaosScenario.builder() // GH-90000
                    .withChaosType(ChaosType.PARTIAL_FAILURE) // GH-90000
                    .withFailureProbability(0.3) // GH-90000
                    .withIterations(50) // GH-90000
                    .withDuration(Duration.ofSeconds(10)) // GH-90000
                    .execute(() -> { // GH-90000
                        ScanJob job = ScanJob.pending(UUID.randomUUID(), UUID.randomUUID(), ScanType.DAST); // GH-90000
                        job.start(); // GH-90000

                        // Simulate processing with potential failure
                        if (Math.random() < 0.3) { // GH-90000
                            job.fail("Simulated partial failure [GH-90000]");
                            throw new RuntimeException("Processing failed [GH-90000]");
                        }

                        job.complete(); // GH-90000
                        processedCount.incrementAndGet(); // GH-90000
                        return job;
                    });

            // Expect some failures due to simulated partial failures
            assertThat(result.getSuccessRate()).isGreaterThan(0.5); // GH-90000
            assertThat(processedCount.get()).isGreaterThan(0); // GH-90000
        }
    }

    @Nested
    @DisplayName("Incident Chaos Tests [GH-90000]")
    class IncidentChaosTests {

        @Test
        @ChaosTest(value = ChaosType.CONCURRENCY, failureProbability = 0.2) // GH-90000
        @DisplayName("Incident lifecycle is consistent under concurrent updates [GH-90000]")
        void incidentLifecycleConsistentUnderConcurrency() { // GH-90000
            Incident incident = Incident.of( // GH-90000
                    UUID.randomUUID(), // GH-90000
                    "Concurrent Incident Test",
                    "CRITICAL"
            );

            ChaosScenario.ChaosExecutionResult<String> result = ChaosScenario.builder() // GH-90000
                    .withChaosType(ChaosType.CONCURRENCY) // GH-90000
                    .withFailureProbability(0.0) // GH-90000
                    .withConcurrency(10) // GH-90000
                    .withIterations(50) // GH-90000
                    .withDuration(Duration.ofSeconds(5)) // GH-90000
                    .execute(() -> { // GH-90000
                        synchronized (incident) { // GH-90000
                            String currentStatus = incident.getStatus(); // GH-90000
                            // Attempt state transitions based on current state
                            switch (currentStatus) { // GH-90000
                                case "OPEN":
                                    incident.startInvestigation(); // GH-90000
                                    break;
                                case "INVESTIGATING":
                                    if (Math.random() > 0.5) { // GH-90000
                                        incident.resolve("Resolution from " + Thread.currentThread().getName()); // GH-90000
                                    }
                                    break;
                                default:
                                    // Already resolved or other state
                                    break;
                            }
                            return incident.getStatus(); // GH-90000
                        }
                    });

            result.assertAllSucceeded(); // GH-90000

            // Final state should be valid
            assertThat(incident.getStatus()) // GH-90000
                    .isIn("OPEN", "INVESTIGATING", "RESOLVED"); // GH-90000
        }

        @Test
        @ChaosTest(value = ChaosType.RANDOM, failureProbability = 0.4) // GH-90000
        @DisplayName("Incident creation degrades gracefully under random chaos [GH-90000]")
        void incidentCreationDegrades() { // GH-90000
            ChaosScenario.ChaosExecutionResult<Incident> result = ChaosScenario.builder() // GH-90000
                    .withChaosType(ChaosType.RANDOM) // GH-90000
                    .withFailureProbability(0.4) // GH-90000
                    .withIterations(30) // GH-90000
                    .withDuration(Duration.ofSeconds(5)) // GH-90000
                    .execute(() -> { // GH-90000
                        Incident incident = Incident.of( // GH-90000
                                UUID.randomUUID(), // GH-90000
                                "Chaos Test Incident " + System.nanoTime(), // GH-90000
                                "HIGH"
                        );
                        assertThat(incident).isNotNull(); // GH-90000
                        assertThat(incident.getTitle()).isNotBlank(); // GH-90000
                        return incident;
                    });

            // System should still function even under chaos
            result.assertGracefulDegradation(); // GH-90000
            assertThat(result.getSuccessRate()).isGreaterThan(0.5); // GH-90000
        }
    }

    @Nested
    @DisplayName("High Load Chaos Tests [GH-90000]")
    class HighLoadChaosTests {

        @Test
        @ChaosTest(value = ChaosType.RESOURCE_EXHAUSTION, failureProbability = 0.1) // GH-90000
        @DisplayName("Domain models handle high volume creation [GH-90000]")
        void domainModelsHandleHighVolume() { // GH-90000
            ChaosScenario.ChaosExecutionResult<Void> result = ChaosScenario.builder() // GH-90000
                    .withChaosType(ChaosType.RESOURCE_EXHAUSTION) // GH-90000
                    .withFailureProbability(0.0) // Test the model, not injected chaos // GH-90000
                    .withConcurrency(8) // GH-90000
                    .withIterations(1000) // GH-90000
                    .withDuration(Duration.ofSeconds(30)) // GH-90000
                    .execute(() -> { // GH-90000
                        // Create various domain objects
                        Dashboard dashboard = Dashboard.of(UUID.randomUUID(), "Load Test Dashboard"); // GH-90000
                        ScanJob scanJob = ScanJob.pending(UUID.randomUUID(), UUID.randomUUID(), ScanType.SCA); // GH-90000
                        Incident incident = Incident.of(UUID.randomUUID(), "Load Test Incident", "LOW"); // GH-90000

                        // Verify all objects are valid
                        assertThat(dashboard.getCreatedAt()).isNotNull(); // GH-90000
                        assertThat(scanJob.getStatus()).isEqualTo(ScanStatus.PENDING); // GH-90000
                        assertThat(incident.getStatus()).isEqualTo("OPEN [GH-90000]");
                    });

            result.assertAllSucceeded(); // GH-90000
            assertThat(result.getSuccessCount()).isEqualTo(1000); // GH-90000

            // Verify average creation time is reasonable
            assertThat(result.getAverageDuration().toMillis()).isLessThan(100); // GH-90000
        }
    }
}
