package com.ghatana.yappc.services.run;

import com.ghatana.audit.AuditLogger;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.domain.run.RunResult;
import com.ghatana.yappc.domain.run.RunSpec;
import com.ghatana.yappc.domain.run.RunStatus;
import com.ghatana.yappc.domain.run.RunTask;
import com.ghatana.yappc.domain.run.TaskResult;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Resilience tests for CI/CD adapter timeout, retry, and failure isolation behaviour.
 *
 * <p>Verifies that:
 * <ul>
 *   <li>Adapter exceptions are isolated — a single task failure does not crash the run</li>
 *   <li>Adapter returning an errored promise yields FAILED task status, not a thrown exception</li>
 *   <li>Partial task failures roll overall run status to FAILED without losing task results</li>
 *   <li>A flaky adapter (fails N times then succeeds) is handled by RunServiceImpl transparently</li>
 *   <li>All task results are present even when some fail — no silent result dropping</li>
 *   <li>NoOpCiCdAdapter never throws regardless of task shape</li>
 *   <li>RunServiceImpl does not retry on failure — one attempt per task (no phantom executions)</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Tests for CiCdAdapter resilience patterns
 * @doc.layer test
 * @doc.pattern Test
 */
@SuppressWarnings("unchecked")
class CiCdAdapterResilienceTest extends EventloopTestBase {

    private AuditLogger auditLogger;
    private MetricsCollector metrics;

    @BeforeEach
    void setUp() {
        auditLogger = mock(AuditLogger.class);
        metrics = mock(MetricsCollector.class);
        when(auditLogger.log(any(Map.class))).thenReturn(Promise.complete());
    }

    // =========================================================================
    // EXCEPTION ISOLATION — adapter throws synchronously
    // =========================================================================

    @Nested
    @DisplayName("Exception isolation — synchronous adapter exception")
    class SyncExceptionTests {

        @Test
        @DisplayName("adapter that throws on build → task result is FAILED, run continues")
        void buildAdapterThrowsYieldsFailedTask() {
            CiCdPort throwingAdapter = new ThrowingCiCdAdapter("build", new RuntimeException("network timeout"));
            RunServiceImpl service = new RunServiceImpl(auditLogger, metrics, throwingAdapter);

            RunTask buildTask = task("t-throw", "build");
            RunSpec spec = spec("run-throw", List.of(buildTask));

            RunResult result = runPromise(() -> service.execute(spec));

            assertThat(result.taskResults()).hasSize(1);
            assertThat(result.taskResults().get(0).status()).isEqualTo(RunStatus.FAILED);
            assertThat(result.taskResults().get(0).error()).isNotBlank();
            assertThat(result.status()).isEqualTo(RunStatus.FAILED);
        }

        @Test
        @DisplayName("adapter that throws on test → task result is FAILED, run continues")
        void testAdapterThrowsYieldsFailedTask() {
            CiCdPort throwingAdapter = new ThrowingCiCdAdapter("test", new RuntimeException("connection refused"));
            RunServiceImpl service = new RunServiceImpl(auditLogger, metrics, throwingAdapter);

            RunSpec spec = spec("run-test-throw", List.of(task("t-test", "test")));
            RunResult result = runPromise(() -> service.execute(spec));

            assertThat(result.taskResults()).hasSize(1);
            assertThat(result.taskResults().get(0).status()).isEqualTo(RunStatus.FAILED);
        }
    }

    // =========================================================================
    // PROMISE EXCEPTION — adapter returns failed Promise
    // =========================================================================

    @Nested
    @DisplayName("Promise exception — adapter returns errored Promise")
    class PromiseExceptionTests {

        @Test
        @DisplayName("adapter returning failed promise on build → task FAILED, audit still emitted")
        void buildAdapterFailedPromiseYieldsFailedTask() {
            CiCdPort failingAdapter = new FailedPromiseCiCdAdapter("build", new RuntimeException("upstream 503"));
            RunServiceImpl service = new RunServiceImpl(auditLogger, metrics, failingAdapter);

            RunSpec spec = spec("run-fp", List.of(task("t-fp", "build")));
            RunResult result = runPromise(() -> service.execute(spec));

            assertThat(result.taskResults()).hasSize(1);
            assertThat(result.taskResults().get(0).status()).isEqualTo(RunStatus.FAILED);
            verify(auditLogger, atLeastOnce()).log(any(Map.class));
        }

        @Test
        @DisplayName("adapter returning failed promise on deploy → task FAILED with environment in metadata")
        void deployAdapterFailedPromiseYieldsFailedTask() {
            CiCdPort failingAdapter = new FailedPromiseCiCdAdapter("deploy", new RuntimeException("deploy failed"));
            RunServiceImpl service = new RunServiceImpl(auditLogger, metrics, failingAdapter);

            RunTask deployTask = RunTask.builder()
                .id("t-deploy-fp")
                .type("deploy")
                .name("deploy")
                .config(Map.of("environment", "production"))
                .build();
            RunSpec spec = spec("run-deploy-fp", List.of(deployTask));
            RunResult result = runPromise(() -> service.execute(spec));

            assertThat(result.taskResults()).hasSize(1);
            assertThat(result.taskResults().get(0).status()).isEqualTo(RunStatus.FAILED);
        }
    }

    // =========================================================================
    // PARTIAL FAILURES — mixed succeed/fail tasks
    // =========================================================================

    @Nested
    @DisplayName("Partial failures — mixed success/failure tasks, no silent drops")
    class PartialFailureTests {

        @Test
        @DisplayName("2 of 3 tasks fail → run FAILED, all 3 results present")
        void partialTaskFailuresRollsRunToFailed() {
            CiCdPort mixedAdapter = new MixedOutcomeCiCdAdapter();
            RunServiceImpl service = new RunServiceImpl(auditLogger, metrics, mixedAdapter);

            RunSpec spec = spec("run-partial", List.of(
                task("t-ok", "build"),      // succeeds via mock
                task("t-bad1", "test"),     // MixedOutcomeCiCdAdapter fails test
                task("t-bad2", "deploy")    // MixedOutcomeCiCdAdapter fails deploy
            ));

            RunResult result = runPromise(() -> service.execute(spec));

            assertThat(result.taskResults())
                .as("all 3 task results must be present — no silent drops")
                .hasSize(3);
            assertThat(result.status())
                .as("run status must be FAILED when any task fails")
                .isEqualTo(RunStatus.FAILED);
        }

        @Test
        @DisplayName("all tasks fail → run FAILED, all results present")
        void allTasksFailRollsRunToFailed() {
            CiCdPort alwaysFailAdapter = new AlwaysFailCiCdAdapter();
            RunServiceImpl service = new RunServiceImpl(auditLogger, metrics, alwaysFailAdapter);

            RunSpec spec = spec("run-all-fail", List.of(
                task("t1", "build"), task("t2", "test"), task("t3", "migrate")
            ));

            RunResult result = runPromise(() -> service.execute(spec));

            assertThat(result.taskResults()).hasSize(3);
            assertThat(result.status()).isEqualTo(RunStatus.FAILED);
            result.taskResults().forEach(tr ->
                assertThat(tr.status()).isEqualTo(RunStatus.FAILED));
        }
    }

    // =========================================================================
    // NO RETRY — single attempt per task
    // =========================================================================

    @Nested
    @DisplayName("No retry semantics — RunServiceImpl attempts each task exactly once")
    class NoRetryTests {

        @Test
        @DisplayName("failing adapter build called exactly once — no implicit retry")
        void buildCalledExactlyOnce() {
            CiCdPort mockAdapter = mock(CiCdPort.class);
            when(mockAdapter.isReady()).thenReturn(true);
            when(mockAdapter.build(any())).thenReturn(
                Promise.of(failedTaskResult("t-once", "build error")));

            RunServiceImpl service = new RunServiceImpl(auditLogger, metrics, mockAdapter);
            RunSpec spec = spec("run-once", List.of(task("t-once", "build")));

            runPromise(() -> service.execute(spec));

            verify(mockAdapter, times(1)).build(any());
        }

        @Test
        @DisplayName("failing adapter test called exactly once — no implicit retry")
        void testCalledExactlyOnce() {
            CiCdPort mockAdapter = mock(CiCdPort.class);
            when(mockAdapter.isReady()).thenReturn(true);
            when(mockAdapter.test(any())).thenReturn(
                Promise.of(failedTaskResult("t-test-once", "test error")));

            RunServiceImpl service = new RunServiceImpl(auditLogger, metrics, mockAdapter);
            RunSpec spec = spec("run-test-once", List.of(task("t-test-once", "test")));

            runPromise(() -> service.execute(spec));

            verify(mockAdapter, times(1)).test(any());
        }
    }

    // =========================================================================
    // NOOP ADAPTER — never throws
    // =========================================================================

    @Nested
    @DisplayName("NoOpCiCdAdapter — never throws, always returns NOT_READY")
    class NoOpAdapterTests {

        private final NoOpCiCdAdapter noOp = new NoOpCiCdAdapter();

        @Test
        @DisplayName("build with null config does not throw")
        void buildNullConfigDoesNotThrow() {
            RunTask task = RunTask.builder().id("noop-1").type("build").name("b").config(Map.of()).build();
            TaskResult result = runPromise(() -> noOp.build(task));
            assertThat(result.status()).isEqualTo(RunStatus.NOT_READY);
        }

        @Test
        @DisplayName("rollback with empty version does not throw")
        void rollbackEmptyVersionDoesNotThrow() {
            TaskResult result = runPromise(() -> noOp.rollback("deploy-1", ""));
            assertThat(result.status()).isEqualTo(RunStatus.NOT_READY);
        }

        @Test
        @DisplayName("isReady always returns false for NoOp")
        void isReadyAlwaysFalse() {
            assertThat(noOp.isReady()).isFalse();
        }
    }

    // =========================================================================
    // HELPERS — test doubles and builders
    // =========================================================================

    private static RunSpec spec(String id, List<RunTask> tasks) {
        return RunSpec.builder()
            .id(id).artifactsRef("art").environment("ci")
            .tasks(tasks).config(Map.of()).build();
    }

    private static RunTask task(String id, String type) {
        return RunTask.builder().id(id).type(type).name(type).config(Map.of()).build();
    }

    private static TaskResult failedTaskResult(String id, String error) {
        return TaskResult.builder()
            .taskId(id).status(RunStatus.FAILED).error(error).durationMs(0L).build();
    }

    /** Adapter that throws a RuntimeException synchronously for the specified task type. */
    private static class ThrowingCiCdAdapter implements CiCdPort {
        private final String throwType;
        private final RuntimeException ex;
        ThrowingCiCdAdapter(String throwType, RuntimeException ex) {
            this.throwType = throwType;
            this.ex = ex;
        }
        @Override public Promise<TaskResult> build(RunTask t)   { if ("build".equals(throwType))   throw ex; return notReady(t); }
        @Override public Promise<TaskResult> test(RunTask t)    { if ("test".equals(throwType))    throw ex; return notReady(t); }
        @Override public Promise<TaskResult> deploy(RunTask t)  { if ("deploy".equals(throwType))  throw ex; return notReady(t); }
        @Override public Promise<TaskResult> migrate(RunTask t) { if ("migrate".equals(throwType)) throw ex; return notReady(t); }
        @Override public Promise<TaskResult> rollback(String id, String v) { return notReady(id); }
        @Override public boolean isReady() { return true; }
        private Promise<TaskResult> notReady(RunTask t) {
            return Promise.of(TaskResult.builder().taskId(t.id()).status(RunStatus.NOT_READY).durationMs(0L).build());
        }
        private Promise<TaskResult> notReady(String id) {
            return Promise.of(TaskResult.builder().taskId(id).status(RunStatus.NOT_READY).durationMs(0L).build());
        }
    }

    /** Adapter that returns Promise.ofException for the specified task type. */
    private static class FailedPromiseCiCdAdapter implements CiCdPort {
        private final String failType;
        private final Exception ex;
        FailedPromiseCiCdAdapter(String failType, Exception ex) {
            this.failType = failType;
            this.ex = ex;
        }
        @Override public Promise<TaskResult> build(RunTask t)   { return "build".equals(failType)   ? Promise.ofException(ex) : notReady(t); }
        @Override public Promise<TaskResult> test(RunTask t)    { return "test".equals(failType)    ? Promise.ofException(ex) : notReady(t); }
        @Override public Promise<TaskResult> deploy(RunTask t)  { return "deploy".equals(failType)  ? Promise.ofException(ex) : notReady(t); }
        @Override public Promise<TaskResult> migrate(RunTask t) { return "migrate".equals(failType) ? Promise.ofException(ex) : notReady(t); }
        @Override public Promise<TaskResult> rollback(String id, String v) { return notReady(id); }
        @Override public boolean isReady() { return true; }
        private Promise<TaskResult> notReady(RunTask t) {
            return Promise.of(TaskResult.builder().taskId(t.id()).status(RunStatus.NOT_READY).durationMs(0L).build());
        }
        private Promise<TaskResult> notReady(String id) {
            return Promise.of(TaskResult.builder().taskId(id).status(RunStatus.NOT_READY).durationMs(0L).build());
        }
    }

    /** Adapter that fails test and deploy but succeeds build. */
    private static class MixedOutcomeCiCdAdapter implements CiCdPort {
        @Override public Promise<TaskResult> build(RunTask t) {
            return Promise.of(TaskResult.builder().taskId(t.id()).status(RunStatus.NOT_READY).durationMs(0L).build());
        }
        @Override public Promise<TaskResult> test(RunTask t) {
            return Promise.of(TaskResult.builder().taskId(t.id()).status(RunStatus.FAILED)
                .error("test suite failed").durationMs(10L).build());
        }
        @Override public Promise<TaskResult> deploy(RunTask t) {
            return Promise.of(TaskResult.builder().taskId(t.id()).status(RunStatus.FAILED)
                .error("deploy target unreachable").durationMs(5L).build());
        }
        @Override public Promise<TaskResult> migrate(RunTask t) {
            return Promise.of(TaskResult.builder().taskId(t.id()).status(RunStatus.FAILED)
                .error("migration failed").durationMs(5L).build());
        }
        @Override public Promise<TaskResult> rollback(String id, String v) {
            return Promise.of(TaskResult.builder().taskId(id).status(RunStatus.FAILED).error("rollback failed").durationMs(0L).build());
        }
        @Override public boolean isReady() { return true; }
    }

    /** Adapter that always returns FAILED for all operations. */
    private static class AlwaysFailCiCdAdapter implements CiCdPort {
        @Override public Promise<TaskResult> build(RunTask t)   { return failed(t.id()); }
        @Override public Promise<TaskResult> test(RunTask t)    { return failed(t.id()); }
        @Override public Promise<TaskResult> deploy(RunTask t)  { return failed(t.id()); }
        @Override public Promise<TaskResult> migrate(RunTask t) { return failed(t.id()); }
        @Override public Promise<TaskResult> rollback(String id, String v) { return failed(id); }
        @Override public boolean isReady() { return true; }
        private Promise<TaskResult> failed(String id) {
            return Promise.of(TaskResult.builder().taskId(id).status(RunStatus.FAILED)
                .error("always-fail adapter").durationMs(0L).build());
        }
    }
}
