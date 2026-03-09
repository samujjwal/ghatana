package com.ghatana.products.yappc.domain.performance;

import com.ghatana.products.yappc.domain.model.Dashboard;
import com.ghatana.products.yappc.domain.model.ScanJob;
import com.ghatana.products.yappc.domain.model.Incident;
import com.ghatana.products.yappc.domain.model.CloudAccount;
import com.ghatana.products.yappc.domain.model.SecurityAlert;
import com.ghatana.products.yappc.domain.model.ScanFinding;
import com.ghatana.products.yappc.domain.enums.CloudProvider;
import com.ghatana.products.yappc.domain.enums.ScanStatus;
import com.ghatana.products.yappc.domain.enums.ScanType;
import com.ghatana.platform.testing.TestCategories;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.time.Instant;
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
 *   <li>Bulk creation (1000 objects): &lt; 100ms</li>
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
        @Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
        void singleCreationPerformance() {
            UUID workspaceId = UUID.randomUUID();

            long startNanos = System.nanoTime();
            Dashboard dashboard = Dashboard.of(workspaceId, "Performance Test Dashboard");
            long durationNanos = System.nanoTime() - startNanos;

            assertThat(dashboard).isNotNull();
            assertThat(Duration.ofNanos(durationNanos).toMillis()).isLessThan(1);
        }

        @Test
        @DisplayName("Bulk dashboard creation (1000) should be under 100ms")
        @Timeout(value = 200, unit = TimeUnit.MILLISECONDS)
        void bulkCreationPerformance() {
            UUID workspaceId = UUID.randomUUID();
            List<Dashboard> dashboards = new ArrayList<>(BULK_SIZE);

            long startNanos = System.nanoTime();
            for (int i = 0; i < BULK_SIZE; i++) {
                dashboards.add(Dashboard.of(workspaceId, "Dashboard " + i));
            }
            long durationMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();

            assertThat(dashboards).hasSize(BULK_SIZE);
            assertThat(durationMs).isLessThan(100);

            // Calculate throughput
            double throughputPerSec = (BULK_SIZE * 1000.0) / durationMs;
            System.out.printf("Dashboard bulk creation: %d objects in %dms (%.0f ops/sec)%n",
                    BULK_SIZE, durationMs, throughputPerSec);
        }

        @Test
        @DisplayName("Concurrent dashboard creation should scale linearly")
        void concurrentCreationPerformance() throws InterruptedException {
            UUID workspaceId = UUID.randomUUID();
            ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_THREADS);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(CONCURRENT_THREADS);
            AtomicLong totalOps = new AtomicLong(0);

            for (int t = 0; t < CONCURRENT_THREADS; t++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int i = 0; i < OPS_PER_THREAD; i++) {
                            Dashboard.of(workspaceId, "Concurrent Dashboard " + i);
                            totalOps.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            long startNanos = System.nanoTime();
            startLatch.countDown();
            boolean completed = endLatch.await(10, TimeUnit.SECONDS);
            long durationMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();

            executor.shutdown();

            assertThat(completed).isTrue();
            assertThat(totalOps.get()).isEqualTo((long) CONCURRENT_THREADS * OPS_PER_THREAD);

            double throughputPerSec = (totalOps.get() * 1000.0) / durationMs;
            System.out.printf("Dashboard concurrent creation: %d ops in %dms (%.0f ops/sec with %d threads)%n",
                    totalOps.get(), durationMs, throughputPerSec, CONCURRENT_THREADS);

            // Expect at least 10,000 ops/sec
            assertThat(throughputPerSec).isGreaterThan(10000);
        }
    }

    @Nested
    @DisplayName("ScanJob Performance")
    class ScanJobPerformanceTests {

        @Test
        @DisplayName("ScanJob lifecycle should complete under 1ms")
        @Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
        void lifecyclePerformance() {
            UUID workspaceId = UUID.randomUUID();
            UUID projectId = UUID.randomUUID();

            long startNanos = System.nanoTime();
            ScanJob job = ScanJob.pending(workspaceId, projectId, ScanType.SAST);
            job.start();
            job.complete();
            long durationNanos = System.nanoTime() - startNanos;

            assertThat(job.getStatus()).isEqualTo(ScanStatus.COMPLETED);
            assertThat(Duration.ofNanos(durationNanos).toMillis()).isLessThan(1);
        }

        @Test
        @DisplayName("Bulk ScanJob creation with state transitions")
        @Timeout(value = 500, unit = TimeUnit.MILLISECONDS)
        void bulkLifecyclePerformance() {
            UUID workspaceId = UUID.randomUUID();
            UUID projectId = UUID.randomUUID();
            List<ScanJob> jobs = new ArrayList<>(BULK_SIZE);

            long startNanos = System.nanoTime();
            for (int i = 0; i < BULK_SIZE; i++) {
                ScanJob job = ScanJob.pending(workspaceId, projectId, ScanType.SAST);
                job.start();
                if (i % 10 == 0) {
                    job.fail("Simulated failure");
                } else {
                    job.complete();
                }
                jobs.add(job);
            }
            long durationMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();

            assertThat(jobs).hasSize(BULK_SIZE);
            long completedCount = jobs.stream().filter(j -> j.getStatus() == ScanStatus.COMPLETED).count();
            long failedCount = jobs.stream().filter(j -> j.getStatus() == ScanStatus.FAILED).count();

            System.out.printf("ScanJob bulk lifecycle: %d objects in %dms (completed: %d, failed: %d)%n",
                    BULK_SIZE, durationMs, completedCount, failedCount);

            assertThat(durationMs).isLessThan(500);
        }

        @Test
        @DisplayName("State transition should be under 0.1ms")
        void stateTransitionPerformance() {
            UUID workspaceId = UUID.randomUUID();
            UUID projectId = UUID.randomUUID();
            ScanJob job = ScanJob.pending(workspaceId, projectId, ScanType.DAST);

            // Warm up
            job.start();
            job = ScanJob.pending(workspaceId, projectId, ScanType.DAST);

            // Measure start transition
            long startNanos = System.nanoTime();
            job.start();
            long startDurationNanos = System.nanoTime() - startNanos;

            // Measure complete transition
            long completeStartNanos = System.nanoTime();
            job.complete();
            long completeDurationNanos = System.nanoTime() - completeStartNanos;

            // Each transition should be under 0.1ms (100,000 nanos)
            assertThat(startDurationNanos).isLessThan(100_000);
            assertThat(completeDurationNanos).isLessThan(100_000);

            System.out.printf("ScanJob state transitions: start=%dns, complete=%dns%n",
                    startDurationNanos, completeDurationNanos);
        }
    }

    @Nested
    @DisplayName("Incident Performance")
    class IncidentPerformanceTests {

        @Test
        @DisplayName("Incident creation and resolution should be fast")
        @Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
        void incidentLifecyclePerformance() {
            UUID workspaceId = UUID.randomUUID();

            long startNanos = System.nanoTime();
            Incident incident = Incident.of(workspaceId, "Performance Test Incident", "CRITICAL");
            incident.startInvestigation();
            incident.resolve("Resolved via performance test");
            long durationNanos = System.nanoTime() - startNanos;

            assertThat(incident.getStatus()).isEqualTo("RESOLVED");
            assertThat(Duration.ofNanos(durationNanos).toMillis()).isLessThan(1);
        }

        @Test
        @DisplayName("Bulk incident processing")
        @Timeout(value = 500, unit = TimeUnit.MILLISECONDS)
        void bulkIncidentPerformance() {
            UUID workspaceId = UUID.randomUUID();
            List<Incident> incidents = new ArrayList<>(BULK_SIZE);

            long startNanos = System.nanoTime();
            for (int i = 0; i < BULK_SIZE; i++) {
                String severity = switch (i % 4) {
                    case 0 -> "CRITICAL";
                    case 1 -> "HIGH";
                    case 2 -> "MEDIUM";
                    default -> "LOW";
                };
                Incident incident = Incident.of(workspaceId, "Incident " + i, severity);
                if (i % 2 == 0) {
                    incident.startInvestigation();
                    incident.resolve("Resolved");
                }
                incidents.add(incident);
            }
            long durationMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();

            assertThat(incidents).hasSize(BULK_SIZE);

            double throughputPerSec = (BULK_SIZE * 1000.0) / durationMs;
            System.out.printf("Incident bulk processing: %d objects in %dms (%.0f ops/sec)%n",
                    BULK_SIZE, durationMs, throughputPerSec);

            assertThat(durationMs).isLessThan(500);
        }
    }

    @Nested
    @DisplayName("CloudAccount Performance")
    class CloudAccountPerformanceTests {

        @Test
        @DisplayName("CloudAccount creation for all providers")
        @Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
        void multiProviderCreationPerformance() {
            UUID workspaceId = UUID.randomUUID();
            List<CloudAccount> accounts = new ArrayList<>();

            long startNanos = System.nanoTime();
            for (CloudProvider provider : CloudProvider.values()) {
                accounts.add(CloudAccount.of(workspaceId, provider,
                        "account-" + provider.name(), provider.name() + " Account"));
            }
            long durationNanos = System.nanoTime() - startNanos;

            assertThat(accounts).hasSize(CloudProvider.values().length);
            assertThat(Duration.ofNanos(durationNanos).toMillis()).isLessThan(10);
        }

        @Test
        @DisplayName("Bulk CloudAccount creation")
        @Timeout(value = 200, unit = TimeUnit.MILLISECONDS)
        void bulkCloudAccountPerformance() {
            UUID workspaceId = UUID.randomUUID();
            CloudProvider[] providers = CloudProvider.values();
            List<CloudAccount> accounts = new ArrayList<>(BULK_SIZE);

            long startNanos = System.nanoTime();
            for (int i = 0; i < BULK_SIZE; i++) {
                CloudProvider provider = providers[i % providers.length];
                accounts.add(CloudAccount.of(workspaceId, provider,
                        "account-" + i, "Account " + i));
            }
            long durationMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();

            assertThat(accounts).hasSize(BULK_SIZE);

            double throughputPerSec = (BULK_SIZE * 1000.0) / durationMs;
            System.out.printf("CloudAccount bulk creation: %d objects in %dms (%.0f ops/sec)%n",
                    BULK_SIZE, durationMs, throughputPerSec);

            assertThat(durationMs).isLessThan(200);
        }
    }

    @Nested
    @DisplayName("SecurityAlert Performance")
    class SecurityAlertPerformanceTests {

        @Test
        @DisplayName("SecurityAlert lifecycle performance")
        @Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
        void alertLifecyclePerformance() {
            UUID workspaceId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            long startNanos = System.nanoTime();
            SecurityAlert alert = SecurityAlert.of(workspaceId, "IDS", "CRITICAL", "Performance Test Alert");
            alert.acknowledge(userId);
            alert.resolve(userId);
            long durationNanos = System.nanoTime() - startNanos;

            assertThat(alert.getStatus()).isEqualTo("RESOLVED");
            assertThat(Duration.ofNanos(durationNanos).toMillis()).isLessThan(1);
        }

        @Test
        @DisplayName("High-volume alert processing")
        @Timeout(value = 500, unit = TimeUnit.MILLISECONDS)
        void highVolumeAlertPerformance() {
            UUID workspaceId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            String[] alertTypes = {"IDS", "WAF", "SIEM", "EDR", "DLP"};
            String[] severities = {"CRITICAL", "HIGH", "MEDIUM", "LOW"};
            List<SecurityAlert> alerts = new ArrayList<>(BULK_SIZE);

            long startNanos = System.nanoTime();
            for (int i = 0; i < BULK_SIZE; i++) {
                SecurityAlert alert = SecurityAlert.of(
                        workspaceId,
                        alertTypes[i % alertTypes.length],
                        severities[i % severities.length],
                        "Alert " + i
                );
                if (i % 3 == 0) {
                    alert.acknowledge(userId);
                }
                if (i % 5 == 0) {
                    alert.resolve(userId);
                }
                alerts.add(alert);
            }
            long durationMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();

            assertThat(alerts).hasSize(BULK_SIZE);

            long openCount = alerts.stream().filter(SecurityAlert::isOpen).count();
            long resolvedCount = alerts.stream().filter(a -> "RESOLVED".equals(a.getStatus())).count();

            double throughputPerSec = (BULK_SIZE * 1000.0) / durationMs;
            System.out.printf("SecurityAlert high-volume: %d objects in %dms (%.0f ops/sec, open: %d, resolved: %d)%n",
                    BULK_SIZE, durationMs, throughputPerSec, openCount, resolvedCount);

            assertThat(durationMs).isLessThan(500);
        }
    }

    @Nested
    @DisplayName("Memory Allocation Performance")
    class MemoryPerformanceTests {

        @Test
        @DisplayName("Object creation should not cause excessive GC")
        void memoryAllocationPerformance() {
            UUID workspaceId = UUID.randomUUID();
            UUID projectId = UUID.randomUUID();
            int objectCount = 10_000;

            // Force GC before test
            System.gc();
            long beforeMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

            List<Object> objects = new ArrayList<>(objectCount);

            long startNanos = System.nanoTime();
            for (int i = 0; i < objectCount; i++) {
                switch (i % 5) {
                    case 0 -> objects.add(Dashboard.of(workspaceId, "Dashboard " + i));
                    case 1 -> objects.add(ScanJob.pending(workspaceId, projectId, ScanType.SAST));
                    case 2 -> objects.add(Incident.of(workspaceId, "Incident " + i, "HIGH"));
                    case 3 -> objects.add(CloudAccount.of(workspaceId, CloudProvider.AWS, "acct-" + i, "Account " + i));
                    case 4 -> objects.add(SecurityAlert.of(workspaceId, "IDS", "MEDIUM", "Alert " + i));
                }
            }
            long durationMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();

            long afterMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            long memoryUsedBytes = afterMemory - beforeMemory;
            double bytesPerObject = (double) memoryUsedBytes / objectCount;

            System.out.printf("Memory allocation: %d objects in %dms, memory used: %.2f MB (%.0f bytes/object)%n",
                    objectCount, durationMs, memoryUsedBytes / (1024.0 * 1024.0), bytesPerObject);

            assertThat(objects).hasSize(objectCount);

            // Rough sanity check: each object should use less than 2KB on average
            assertThat(bytesPerObject).isLessThan(2048);
        }
    }

    @Nested
    @DisplayName("Throughput Baseline Tests")
    class ThroughputBaselineTests {

        @RepeatedTest(3)
        @DisplayName("Mixed workload throughput should exceed 10,000 ops/sec")
        @Timeout(value = 5, unit = TimeUnit.SECONDS)
        void mixedWorkloadThroughput() {
            UUID workspaceId = UUID.randomUUID();
            UUID projectId = UUID.randomUUID();
            int totalOps = 10_000;
            AtomicLong opsCompleted = new AtomicLong(0);

            long startNanos = System.nanoTime();

            for (int i = 0; i < totalOps; i++) {
                switch (i % 5) {
                    case 0 -> {
                        Dashboard d = Dashboard.of(workspaceId, "D" + i);
                        assertThat(d.getCreatedAt()).isNotNull();
                    }
                    case 1 -> {
                        ScanJob j = ScanJob.pending(workspaceId, projectId, ScanType.SAST);
                        j.start();
                        j.complete();
                    }
                    case 2 -> {
                        Incident inc = Incident.of(workspaceId, "Inc" + i, "HIGH");
                        inc.startInvestigation();
                    }
                    case 3 -> {
                        CloudAccount acc = CloudAccount.of(workspaceId, CloudProvider.GCP, "a" + i, "Acc" + i);
                        acc.markConnected();
                    }
                    case 4 -> {
                        SecurityAlert alert = SecurityAlert.of(workspaceId, "IDS", "LOW", "A" + i);
                        alert.acknowledge(UUID.randomUUID());
                    }
                }
                opsCompleted.incrementAndGet();
            }

            long durationMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
            double throughputPerSec = (opsCompleted.get() * 1000.0) / Math.max(1, durationMs);

            System.out.printf("Mixed workload: %d ops in %dms (%.0f ops/sec)%n",
                    opsCompleted.get(), durationMs, throughputPerSec);

            assertThat(throughputPerSec).isGreaterThan(10_000);
        }
    }
}
