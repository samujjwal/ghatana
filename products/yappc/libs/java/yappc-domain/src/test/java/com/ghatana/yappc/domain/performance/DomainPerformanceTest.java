package com.ghatana.yappc.domain.performance;

import com.ghatana.yappc.domain.model.Dashboard;
import com.ghatana.yappc.domain.model.ScanJob;
import com.ghatana.yappc.domain.model.Incident;
import com.ghatana.yappc.domain.model.CloudAccount;
import com.ghatana.yappc.domain.model.SecurityAlert;
import com.ghatana.yappc.domain.enums.CloudProvider;
import com.ghatana.yappc.domain.enums.ScanStatus;
import com.ghatana.yappc.domain.enums.ScanType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Performance tests for yappc-domain models.
 *
 * <p>These tests verify that domain model operations meet performance
 * requirements for production workloads. They measure:</p>
 * <ul>
 *   <li>Object creation throughput</li>
 *   <li>State transition latency</li>
 *   <li>Memory allocation patterns</li>
 *   <li>Concurrent access performance</li>
 * </ul>
 *
 * <p><b>Performance Targets:</b></p>
 * <ul>
 *   <li>Single object creation: &lt; 1ms</li>
 *   <li>Bulk creation (1000 objects): &lt; 100ms</li> // GH-90000
 *   <li>State transition: &lt; 0.1ms</li>
 *   <li>Concurrent creation throughput: &gt; 10,000 ops/sec</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Validates domain model performance characteristics
 * @doc.layer product
 * @doc.pattern PerformanceTest
 */
@Tag("performance")
@DisplayName("YAPPC Domain Performance Tests")
class DomainPerformanceTest {

    private static final int BULK_SIZE = 1000;
    private static final int CONCURRENT_THREADS = 8;
    private static final int OPS_PER_THREAD = 1000;

    @Nested
    @DisplayName("Dashboard Performance")
    class DashboardPerformanceTests {

        @Test
        @DisplayName("Single dashboard creation should be under 1ms")
        @Timeout(value = 100, unit = TimeUnit.MILLISECONDS) // GH-90000
        void singleCreationPerformance() { // GH-90000
            UUID workspaceId = UUID.randomUUID(); // GH-90000

            long startNanos = System.nanoTime(); // GH-90000
            Dashboard dashboard = Dashboard.of(workspaceId, "Performance Test Dashboard"); // GH-90000
            long durationNanos = System.nanoTime() - startNanos; // GH-90000

            assertThat(dashboard).isNotNull(); // GH-90000
            assertThat(Duration.ofNanos(durationNanos).toMillis()).isLessThan(1); // GH-90000
        }

        @Test
        @DisplayName("Bulk dashboard creation (1000) should be under 100ms")
        @Timeout(value = 200, unit = TimeUnit.MILLISECONDS) // GH-90000
        void bulkCreationPerformance() { // GH-90000
            UUID workspaceId = UUID.randomUUID(); // GH-90000
            List<Dashboard> dashboards = new ArrayList<>(BULK_SIZE); // GH-90000

            long startNanos = System.nanoTime(); // GH-90000
            for (int i = 0; i < BULK_SIZE; i++) { // GH-90000
                dashboards.add(Dashboard.of(workspaceId, "Dashboard " + i)); // GH-90000
            }
            long durationMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis(); // GH-90000

            assertThat(dashboards).hasSize(BULK_SIZE); // GH-90000
            assertThat(durationMs).isLessThan(100); // GH-90000

            // Calculate throughput
            double throughputPerSec = (BULK_SIZE * 1000.0) / durationMs; // GH-90000
            System.out.printf("Dashboard bulk creation: %d objects in %dms (%.0f ops/sec)%n", // GH-90000
                    BULK_SIZE, durationMs, throughputPerSec);
        }

        @Test
        @DisplayName("Concurrent dashboard creation should scale linearly")
        void concurrentCreationPerformance() throws InterruptedException { // GH-90000
            UUID workspaceId = UUID.randomUUID(); // GH-90000
            ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_THREADS); // GH-90000
            CountDownLatch startLatch = new CountDownLatch(1); // GH-90000
            CountDownLatch endLatch = new CountDownLatch(CONCURRENT_THREADS); // GH-90000
            AtomicLong totalOps = new AtomicLong(0); // GH-90000

            for (int t = 0; t < CONCURRENT_THREADS; t++) { // GH-90000
                executor.submit(() -> { // GH-90000
                    try {
                        startLatch.await(); // GH-90000
                        for (int i = 0; i < OPS_PER_THREAD; i++) { // GH-90000
                            Dashboard.of(workspaceId, "Concurrent Dashboard " + i); // GH-90000
                            totalOps.incrementAndGet(); // GH-90000
                        }
                    } catch (InterruptedException e) { // GH-90000
                        Thread.currentThread().interrupt(); // GH-90000
                    } finally {
                        endLatch.countDown(); // GH-90000
                    }
                });
            }

            long startNanos = System.nanoTime(); // GH-90000
            startLatch.countDown(); // GH-90000
            boolean completed = endLatch.await(10, TimeUnit.SECONDS); // GH-90000
            long durationMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis(); // GH-90000

            executor.shutdown(); // GH-90000

            assertThat(completed).isTrue(); // GH-90000
            assertThat(totalOps.get()).isEqualTo((long) CONCURRENT_THREADS * OPS_PER_THREAD); // GH-90000

            double throughputPerSec = (totalOps.get() * 1000.0) / durationMs; // GH-90000
            System.out.printf("Dashboard concurrent creation: %d ops in %dms (%.0f ops/sec with %d threads)%n", // GH-90000
                    totalOps.get(), durationMs, throughputPerSec, CONCURRENT_THREADS); // GH-90000

            // Expect at least 10,000 ops/sec
            assertThat(throughputPerSec).isGreaterThan(10000); // GH-90000
        }
    }

    @Nested
    @DisplayName("ScanJob Performance")
    class ScanJobPerformanceTests {

        @Test
        @DisplayName("ScanJob lifecycle should complete under 1ms")
        @Timeout(value = 100, unit = TimeUnit.MILLISECONDS) // GH-90000
        void lifecyclePerformance() { // GH-90000
            UUID workspaceId = UUID.randomUUID(); // GH-90000
            UUID projectId = UUID.randomUUID(); // GH-90000

            long startNanos = System.nanoTime(); // GH-90000
            ScanJob job = ScanJob.pending(workspaceId, projectId, ScanType.SAST); // GH-90000
            job.start(); // GH-90000
            job.complete(); // GH-90000
            long durationNanos = System.nanoTime() - startNanos; // GH-90000

            assertThat(job.getStatus()).isEqualTo(ScanStatus.COMPLETED); // GH-90000
            assertThat(Duration.ofNanos(durationNanos).toMillis()).isLessThan(1); // GH-90000
        }

        @Test
        @DisplayName("Bulk ScanJob creation with state transitions")
        @Timeout(value = 500, unit = TimeUnit.MILLISECONDS) // GH-90000
        void bulkLifecyclePerformance() { // GH-90000
            UUID workspaceId = UUID.randomUUID(); // GH-90000
            UUID projectId = UUID.randomUUID(); // GH-90000
            List<ScanJob> jobs = new ArrayList<>(BULK_SIZE); // GH-90000

            long startNanos = System.nanoTime(); // GH-90000
            for (int i = 0; i < BULK_SIZE; i++) { // GH-90000
                ScanJob job = ScanJob.pending(workspaceId, projectId, ScanType.SAST); // GH-90000
                job.start(); // GH-90000
                if (i % 10 == 0) { // GH-90000
                    job.fail("Simulated failure");
                } else {
                    job.complete(); // GH-90000
                }
                jobs.add(job); // GH-90000
            }
            long durationMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis(); // GH-90000

            assertThat(jobs).hasSize(BULK_SIZE); // GH-90000
            long completedCount = jobs.stream().filter(j -> j.getStatus() == ScanStatus.COMPLETED).count(); // GH-90000
            long failedCount = jobs.stream().filter(j -> j.getStatus() == ScanStatus.FAILED).count(); // GH-90000

            System.out.printf("ScanJob bulk lifecycle: %d objects in %dms (completed: %d, failed: %d)%n", // GH-90000
                    BULK_SIZE, durationMs, completedCount, failedCount);

            assertThat(durationMs).isLessThan(500); // GH-90000
        }

        @Test
        @DisplayName("State transition should be under 0.1ms")
        void stateTransitionPerformance() { // GH-90000
            UUID workspaceId = UUID.randomUUID(); // GH-90000
            UUID projectId = UUID.randomUUID(); // GH-90000
            ScanJob job = ScanJob.pending(workspaceId, projectId, ScanType.DAST); // GH-90000

            // Warm up
            job.start(); // GH-90000
            job = ScanJob.pending(workspaceId, projectId, ScanType.DAST); // GH-90000

            // Measure start transition
            long startNanos = System.nanoTime(); // GH-90000
            job.start(); // GH-90000
            long startDurationNanos = System.nanoTime() - startNanos; // GH-90000

            // Measure complete transition
            long completeStartNanos = System.nanoTime(); // GH-90000
            job.complete(); // GH-90000
            long completeDurationNanos = System.nanoTime() - completeStartNanos; // GH-90000

            // Each transition should be under 0.1ms (100,000 nanos) // GH-90000
            assertThat(startDurationNanos).isLessThan(100_000); // GH-90000
            assertThat(completeDurationNanos).isLessThan(100_000); // GH-90000

            System.out.printf("ScanJob state transitions: start=%dns, complete=%dns%n", // GH-90000
                    startDurationNanos, completeDurationNanos);
        }
    }

    @Nested
    @DisplayName("Incident Performance")
    class IncidentPerformanceTests {

        @Test
        @DisplayName("Incident creation and resolution should be fast")
        @Timeout(value = 100, unit = TimeUnit.MILLISECONDS) // GH-90000
        void incidentLifecyclePerformance() { // GH-90000
            UUID workspaceId = UUID.randomUUID(); // GH-90000

            long startNanos = System.nanoTime(); // GH-90000
            Incident incident = Incident.of(workspaceId, "Performance Test Incident", "CRITICAL"); // GH-90000
            incident.startInvestigation(); // GH-90000
            incident.resolve("Resolved via performance test");
            long durationNanos = System.nanoTime() - startNanos; // GH-90000

            assertThat(incident.getStatus()).isEqualTo("RESOLVED");
            assertThat(Duration.ofNanos(durationNanos).toMillis()).isLessThan(1); // GH-90000
        }

        @Test
        @DisplayName("Bulk incident processing")
        @Timeout(value = 500, unit = TimeUnit.MILLISECONDS) // GH-90000
        void bulkIncidentPerformance() { // GH-90000
            UUID workspaceId = UUID.randomUUID(); // GH-90000
            List<Incident> incidents = new ArrayList<>(BULK_SIZE); // GH-90000

            long startNanos = System.nanoTime(); // GH-90000
            for (int i = 0; i < BULK_SIZE; i++) { // GH-90000
                String severity = switch (i % 4) { // GH-90000
                    case 0 -> "CRITICAL";
                    case 1 -> "HIGH";
                    case 2 -> "MEDIUM";
                    default -> "LOW";
                };
                Incident incident = Incident.of(workspaceId, "Incident " + i, severity); // GH-90000
                if (i % 2 == 0) { // GH-90000
                    incident.startInvestigation(); // GH-90000
                    incident.resolve("Resolved");
                }
                incidents.add(incident); // GH-90000
            }
            long durationMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis(); // GH-90000

            assertThat(incidents).hasSize(BULK_SIZE); // GH-90000

            double throughputPerSec = (BULK_SIZE * 1000.0) / durationMs; // GH-90000
            System.out.printf("Incident bulk processing: %d objects in %dms (%.0f ops/sec)%n", // GH-90000
                    BULK_SIZE, durationMs, throughputPerSec);

            assertThat(durationMs).isLessThan(500); // GH-90000
        }
    }

    @Nested
    @DisplayName("CloudAccount Performance")
    class CloudAccountPerformanceTests {

        @Test
        @DisplayName("CloudAccount creation for all providers")
        @Timeout(value = 100, unit = TimeUnit.MILLISECONDS) // GH-90000
        void multiProviderCreationPerformance() { // GH-90000
            UUID workspaceId = UUID.randomUUID(); // GH-90000
            List<CloudAccount> accounts = new ArrayList<>(); // GH-90000

            long startNanos = System.nanoTime(); // GH-90000
            for (CloudProvider provider : CloudProvider.values()) { // GH-90000
                accounts.add(CloudAccount.of(workspaceId, provider, // GH-90000
                        "account-" + provider.name(), provider.name() + " Account")); // GH-90000
            }
            long durationNanos = System.nanoTime() - startNanos; // GH-90000

            assertThat(accounts).hasSize(CloudProvider.values().length); // GH-90000
            assertThat(Duration.ofNanos(durationNanos).toMillis()).isLessThan(10); // GH-90000
        }

        @Test
        @DisplayName("Bulk CloudAccount creation")
        @Timeout(value = 200, unit = TimeUnit.MILLISECONDS) // GH-90000
        void bulkCloudAccountPerformance() { // GH-90000
            UUID workspaceId = UUID.randomUUID(); // GH-90000
            CloudProvider[] providers = CloudProvider.values(); // GH-90000
            List<CloudAccount> accounts = new ArrayList<>(BULK_SIZE); // GH-90000

            long startNanos = System.nanoTime(); // GH-90000
            for (int i = 0; i < BULK_SIZE; i++) { // GH-90000
                CloudProvider provider = providers[i % providers.length];
                accounts.add(CloudAccount.of(workspaceId, provider, // GH-90000
                        "account-" + i, "Account " + i));
            }
            long durationMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis(); // GH-90000

            assertThat(accounts).hasSize(BULK_SIZE); // GH-90000

            double throughputPerSec = (BULK_SIZE * 1000.0) / durationMs; // GH-90000
            System.out.printf("CloudAccount bulk creation: %d objects in %dms (%.0f ops/sec)%n", // GH-90000
                    BULK_SIZE, durationMs, throughputPerSec);

            assertThat(durationMs).isLessThan(200); // GH-90000
        }
    }

    @Nested
    @DisplayName("SecurityAlert Performance")
    class SecurityAlertPerformanceTests {

        @Test
        @DisplayName("SecurityAlert lifecycle performance")
        @Timeout(value = 100, unit = TimeUnit.MILLISECONDS) // GH-90000
        void alertLifecyclePerformance() { // GH-90000
            UUID workspaceId = UUID.randomUUID(); // GH-90000
            UUID userId = UUID.randomUUID(); // GH-90000

            long startNanos = System.nanoTime(); // GH-90000
            SecurityAlert alert = SecurityAlert.of(workspaceId, "IDS", "CRITICAL", "Performance Test Alert"); // GH-90000
            alert.acknowledge(userId); // GH-90000
            alert.resolve(userId); // GH-90000
            long durationNanos = System.nanoTime() - startNanos; // GH-90000

            assertThat(alert.getStatus()).isEqualTo("RESOLVED");
            assertThat(Duration.ofNanos(durationNanos).toMillis()).isLessThan(1); // GH-90000
        }

        @Test
        @DisplayName("High-volume alert processing")
        @Timeout(value = 500, unit = TimeUnit.MILLISECONDS) // GH-90000
        void highVolumeAlertPerformance() { // GH-90000
            UUID workspaceId = UUID.randomUUID(); // GH-90000
            UUID userId = UUID.randomUUID(); // GH-90000
            String[] alertTypes = {"IDS", "WAF", "SIEM", "EDR", "DLP"};
            String[] severities = {"CRITICAL", "HIGH", "MEDIUM", "LOW"};
            List<SecurityAlert> alerts = new ArrayList<>(BULK_SIZE); // GH-90000

            long startNanos = System.nanoTime(); // GH-90000
            for (int i = 0; i < BULK_SIZE; i++) { // GH-90000
                SecurityAlert alert = SecurityAlert.of( // GH-90000
                        workspaceId,
                        alertTypes[i % alertTypes.length],
                        severities[i % severities.length],
                        "Alert " + i
                );
                if (i % 3 == 0) { // GH-90000
                    alert.acknowledge(userId); // GH-90000
                }
                if (i % 5 == 0) { // GH-90000
                    alert.resolve(userId); // GH-90000
                }
                alerts.add(alert); // GH-90000
            }
            long durationMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis(); // GH-90000

            assertThat(alerts).hasSize(BULK_SIZE); // GH-90000

            long openCount = alerts.stream().filter(SecurityAlert::isOpen).count(); // GH-90000
            long resolvedCount = alerts.stream().filter(a -> "RESOLVED".equals(a.getStatus())).count(); // GH-90000

            double throughputPerSec = (BULK_SIZE * 1000.0) / durationMs; // GH-90000
            System.out.printf("SecurityAlert high-volume: %d objects in %dms (%.0f ops/sec, open: %d, resolved: %d)%n", // GH-90000
                    BULK_SIZE, durationMs, throughputPerSec, openCount, resolvedCount);

            assertThat(durationMs).isLessThan(500); // GH-90000
        }
    }

    @Nested
    @DisplayName("Memory Allocation Performance")
    class MemoryPerformanceTests {

        @Test
        @DisplayName("Object creation should not cause excessive GC")
        void memoryAllocationPerformance() { // GH-90000
            UUID workspaceId = UUID.randomUUID(); // GH-90000
            UUID projectId = UUID.randomUUID(); // GH-90000
            int objectCount = 10_000;

            // Force GC before test
            System.gc(); // GH-90000
            long beforeMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory(); // GH-90000

            List<Object> objects = new ArrayList<>(objectCount); // GH-90000

            long startNanos = System.nanoTime(); // GH-90000
            for (int i = 0; i < objectCount; i++) { // GH-90000
                switch (i % 5) { // GH-90000
                    case 0 -> objects.add(Dashboard.of(workspaceId, "Dashboard " + i)); // GH-90000
                    case 1 -> objects.add(ScanJob.pending(workspaceId, projectId, ScanType.SAST)); // GH-90000
                    case 2 -> objects.add(Incident.of(workspaceId, "Incident " + i, "HIGH")); // GH-90000
                    case 3 -> objects.add(CloudAccount.of(workspaceId, CloudProvider.AWS, "acct-" + i, "Account " + i)); // GH-90000
                    case 4 -> objects.add(SecurityAlert.of(workspaceId, "IDS", "MEDIUM", "Alert " + i)); // GH-90000
                }
            }
            long durationMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis(); // GH-90000

            long afterMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory(); // GH-90000
            long memoryUsedBytes = afterMemory - beforeMemory;
            double bytesPerObject = (double) memoryUsedBytes / objectCount; // GH-90000

            System.out.printf("Memory allocation: %d objects in %dms, memory used: %.2f MB (%.0f bytes/object)%n", // GH-90000
                    objectCount, durationMs, memoryUsedBytes / (1024.0 * 1024.0), bytesPerObject); // GH-90000

            assertThat(objects).hasSize(objectCount); // GH-90000

            // Rough sanity check: each object should use less than 2KB on average
            assertThat(bytesPerObject).isLessThan(2048); // GH-90000
        }
    }

    @Nested
    @DisplayName("Throughput Baseline Tests")
    class ThroughputBaselineTests {

        @RepeatedTest(3) // GH-90000
        @DisplayName("Mixed workload throughput should exceed 10,000 ops/sec")
        @Timeout(value = 5, unit = TimeUnit.SECONDS) // GH-90000
        void mixedWorkloadThroughput() { // GH-90000
            UUID workspaceId = UUID.randomUUID(); // GH-90000
            UUID projectId = UUID.randomUUID(); // GH-90000
            int totalOps = 10_000;
            AtomicLong opsCompleted = new AtomicLong(0); // GH-90000

            long startNanos = System.nanoTime(); // GH-90000

            for (int i = 0; i < totalOps; i++) { // GH-90000
                switch (i % 5) { // GH-90000
                    case 0 -> {
                        Dashboard d = Dashboard.of(workspaceId, "D" + i); // GH-90000
                        assertThat(d.getCreatedAt()).isNotNull(); // GH-90000
                    }
                    case 1 -> {
                        ScanJob j = ScanJob.pending(workspaceId, projectId, ScanType.SAST); // GH-90000
                        j.start(); // GH-90000
                        j.complete(); // GH-90000
                    }
                    case 2 -> {
                        Incident inc = Incident.of(workspaceId, "Inc" + i, "HIGH"); // GH-90000
                        inc.startInvestigation(); // GH-90000
                    }
                    case 3 -> {
                        CloudAccount acc = CloudAccount.of(workspaceId, CloudProvider.GCP, "a" + i, "Acc" + i); // GH-90000
                        acc.markConnected(); // GH-90000
                    }
                    case 4 -> {
                        SecurityAlert alert = SecurityAlert.of(workspaceId, "IDS", "LOW", "A" + i); // GH-90000
                        alert.acknowledge(UUID.randomUUID()); // GH-90000
                    }
                }
                opsCompleted.incrementAndGet(); // GH-90000
            }

            long durationMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis(); // GH-90000
            double throughputPerSec = (opsCompleted.get() * 1000.0) / Math.max(1, durationMs); // GH-90000

            System.out.printf("Mixed workload: %d ops in %dms (%.0f ops/sec)%n", // GH-90000
                    opsCompleted.get(), durationMs, throughputPerSec); // GH-90000

            assertThat(throughputPerSec).isGreaterThan(10_000); // GH-90000
        }
    }
}
