package com.ghatana.datacloud.observability;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link MdcPropagation}.
 *
 * <p>Verifies that MDC context is correctly captured on the originating thread
 * and restored on the worker thread, with proper cleanup after execution.
 */
@DisplayName("MdcPropagation")
class MdcPropagationTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Nested
    @DisplayName("capture()")
    class CaptureTests {

        @Test
        @DisplayName("returns empty map when no MDC context is active")
        void returnsEmptyMapWhenNoMdcActive() {
            MDC.clear();

            Map<String, String> snapshot = MdcPropagation.capture();

            assertThat(snapshot).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("returns snapshot of current MDC entries")
        void returnsSnapshotOfCurrentMdcEntries() {
            MDC.put("requestId", "req-123");
            MDC.put("tenantId", "tenant-abc");

            Map<String, String> snapshot = MdcPropagation.capture();

            assertThat(snapshot)
                    .containsEntry("requestId", "req-123")
                    .containsEntry("tenantId", "tenant-abc");
        }

        @Test
        @DisplayName("returns an unmodifiable snapshot isolated from subsequent MDC changes")
        void snapshotIsIsolatedFromSubsequentChanges() {
            MDC.put("requestId", "req-original");
            Map<String, String> snapshot = MdcPropagation.capture();

            MDC.put("requestId", "req-changed");
            MDC.put("newKey", "newValue");

            // Snapshot must remain at values at time of capture
            assertThat(snapshot)
                    .containsEntry("requestId", "req-original")
                    .doesNotContainKey("newKey");
        }
    }

    @Nested
    @DisplayName("withContext()")
    class WithContextTests {

        @Test
        @DisplayName("executes supplier and returns its result")
        void executesSupplierAndReturnsResult() throws Exception {
            Map<String, String> snapshot = Map.of("requestId", "req-abc");

            String result = MdcPropagation.withContext(snapshot, () -> "hello");

            assertThat(result).isEqualTo("hello");
        }

        @Test
        @DisplayName("restores MDC entries before invoking supplier")
        void restoresMdcEntriesBeforeSupplier() throws Exception {
            Map<String, String> snapshot = Map.of("requestId", "req-456", "tenantId", "tenant-xyz");
            Map<String, String> captured = new HashMap<>();

            MdcPropagation.withContext(snapshot, () -> {
                captured.put("requestId", MDC.get("requestId"));
                captured.put("tenantId", MDC.get("tenantId"));
                return null;
            });

            assertThat(captured)
                    .containsEntry("requestId", "req-456")
                    .containsEntry("tenantId", "tenant-xyz");
        }

        @Test
        @DisplayName("clears restored MDC keys after supplier completes normally")
        void clearsRestoredMdcKeysAfterNormalCompletion() throws Exception {
            Map<String, String> snapshot = Map.of("requestId", "req-789");
            MDC.clear(); // ensure clean state

            MdcPropagation.withContext(snapshot, () -> "done");

            assertThat(MDC.get("requestId")).isNull();
        }

        @Test
        @DisplayName("clears restored MDC keys even when supplier throws")
        void clearsRestoredMdcKeysWhenSupplierThrows() {
            Map<String, String> snapshot = Map.of("requestId", "req-err");
            MDC.clear();

            assertThatThrownBy(() -> MdcPropagation.withContext(snapshot, () -> {
                throw new RuntimeException("boom");
            })).isInstanceOf(RuntimeException.class).hasMessage("boom");

            assertThat(MDC.get("requestId")).isNull();
        }

        @Test
        @DisplayName("does not affect pre-existing MDC keys not in snapshot")
        void doesNotAffectPreExistingMdcKeysOutsideSnapshot() throws Exception {
            MDC.put("existingKey", "existingValue");
            Map<String, String> snapshot = Map.of("requestId", "req-abc");

            MdcPropagation.withContext(snapshot, () -> null);

            // existingKey is not in snapshot, so it should be untouched
            assertThat(MDC.get("existingKey")).isEqualTo("existingValue");
            assertThat(MDC.get("requestId")).isNull();
        }

        @Test
        @DisplayName("empty snapshot is a no-op — supplier executes without MDC changes")
        void emptySnapshotIsNoOp() throws Exception {
            MDC.put("existingKey", "existingValue");
            Map<String, String> snapshot = Map.of();

            String result = MdcPropagation.withContext(snapshot, () -> "result");

            assertThat(result).isEqualTo("result");
            assertThat(MDC.get("existingKey")).isEqualTo("existingValue");
        }
    }

    @Nested
    @DisplayName("cross-thread propagation")
    class CrossThreadPropagationTests {

        @Test
        @DisplayName("MDC context is propagated to worker thread via capture+withContext")
        void mdcContextPropagatedToWorkerThread() throws Exception {
            MDC.put("requestId", "req-worker-123");
            MDC.put("tenantId", "tenant-worker");

            Map<String, String> snapshot = MdcPropagation.capture();

            ExecutorService pool = Executors.newSingleThreadExecutor();
            try {
                Future<Map<String, String>> future = pool.submit(() -> {
                    // Simulate what Promise.ofBlocking does — runs on a different thread
                    Map<String, String> captured = new HashMap<>();
                    try {
                        MdcPropagation.withContext(snapshot, () -> {
                            captured.put("requestId", MDC.get("requestId"));
                            captured.put("tenantId", MDC.get("tenantId"));
                            return null;
                        });
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    return captured;
                });

                Map<String, String> workerMdc = future.get();
                assertThat(workerMdc)
                        .containsEntry("requestId", "req-worker-123")
                        .containsEntry("tenantId", "tenant-worker");
            } finally {
                pool.shutdown();
            }
        }

        @Test
        @DisplayName("worker thread MDC is clean after withContext completes")
        void workerThreadMdcCleanAfterWithContext() throws Exception {
            MDC.put("requestId", "req-cleanup-test");
            Map<String, String> snapshot = MdcPropagation.capture();

            ExecutorService pool = Executors.newSingleThreadExecutor();
            try {
                // First task: run with MDC context
                pool.submit(() -> {
                    try {
                        MdcPropagation.withContext(snapshot, () -> null);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }).get();

                // Second task: verify MDC is clean after first task's withContext
                Future<String> future = pool.submit(() -> MDC.get("requestId"));
                assertThat(future.get()).isNull();
            } finally {
                pool.shutdown();
            }
        }
    }
}
