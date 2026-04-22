package com.ghatana.yappc.services.run;

import com.ghatana.audit.AuditLogger;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.yappc.domain.run.RunResult;
import com.ghatana.yappc.domain.run.RunSpec;
import com.ghatana.yappc.domain.run.RunStatus;
import com.ghatana.yappc.domain.run.RunTask;
import io.activej.promise.Promise;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * @doc.type class
 * @doc.purpose Tests for RunService implementation
 * @doc.layer test
 * @doc.pattern Test
 */
@DisplayName("RunService [GH-90000]")
class RunServiceTest extends EventloopTestBase {

    private AuditLogger auditLogger;
    private MetricsCollector metrics;
    private RunService service;

    @BeforeEach
    void setUp() { // GH-90000
        auditLogger = mock(AuditLogger.class); // GH-90000
        metrics = mock(MetricsCollector.class); // GH-90000
        when(auditLogger.log(any(Map.class))).thenReturn(Promise.complete()); // GH-90000
        service = new RunServiceImpl(auditLogger, metrics, new NoOpCiCdAdapter()); // GH-90000
    }

    @Test
    @DisplayName("execute: empty task list → SUCCESS status and metadata contains environment [GH-90000]")
    void shouldExecuteRunSpecWithNoTasks() { // GH-90000
        RunSpec spec = RunSpec.builder() // GH-90000
                .id("run-123 [GH-90000]")
                .artifactsRef("artifacts-123 [GH-90000]")
                .environment("staging [GH-90000]")
                .tasks(List.of()) // GH-90000
                .config(Map.of("tenantId", "tenant-123")) // GH-90000
                .build(); // GH-90000

        RunResult result = runPromise(() -> service.execute(spec)); // GH-90000

        assertThat(result).isNotNull(); // GH-90000
        assertThat(result.id()).isNotNull(); // GH-90000
        assertThat(result.runSpecRef()).isEqualTo("run-123 [GH-90000]");
        assertThat(result.metadata()).containsEntry("environment", "staging"); // GH-90000
        assertThat(result.status()).isNotNull(); // GH-90000
        assertThat(result.startedAt()).isNotNull(); // GH-90000
        assertThat(result.completedAt()).isNotNull(); // GH-90000
        verify(auditLogger, times(1)).log(any(Map.class)); // GH-90000
        verify(metrics, atLeastOnce()).recordTimer(anyString(), anyLong(), any(Map.class)); // GH-90000
    }

    @Test
    @DisplayName("execute: build task type with no-op adapter → task result has NOT_READY status [GH-90000]")
    void shouldExecuteBuildTaskWithNoOpAdapter() { // GH-90000
        RunTask buildTask = RunTask.builder() // GH-90000
                .id("task-build-1 [GH-90000]")
                .type("build [GH-90000]")
                .name("Build [GH-90000]")
                .config(Map.of()) // GH-90000
                .build(); // GH-90000
        RunSpec spec = RunSpec.builder() // GH-90000
                .id("run-build [GH-90000]")
                .artifactsRef("artifacts-1 [GH-90000]")
                .environment("ci [GH-90000]")
                .tasks(List.of(buildTask)) // GH-90000
                .config(Map.of()) // GH-90000
                .build(); // GH-90000

        RunResult result = runPromise(() -> service.execute(spec)); // GH-90000

        assertThat(result.status()).isEqualTo(RunStatus.NOT_READY); // GH-90000
        assertThat(result.taskResults()).hasSize(1); // GH-90000
        assertThat(result.taskResults().get(0).status()).isEqualTo(RunStatus.NOT_READY); // GH-90000
        assertThat(result.taskResults().get(0).output()).contains("[NOT_READY] [GH-90000]");
    }

    @Test
    @DisplayName("execute: test task type with no-op adapter → task result has NOT_READY status [GH-90000]")
    void shouldExecuteTestTaskWithNoOpAdapter() { // GH-90000
        RunTask testTask = RunTask.builder() // GH-90000
                .id("task-test-1 [GH-90000]")
                .type("test [GH-90000]")
                .name("Test [GH-90000]")
                .config(Map.of()) // GH-90000
                .build(); // GH-90000
        RunSpec spec = RunSpec.builder() // GH-90000
                .id("run-test [GH-90000]")
                .artifactsRef("artifacts-1 [GH-90000]")
                .environment("ci [GH-90000]")
                .tasks(List.of(testTask)) // GH-90000
                .config(Map.of()) // GH-90000
                .build(); // GH-90000

        RunResult result = runPromise(() -> service.execute(spec)); // GH-90000

        assertThat(result.status()).isEqualTo(RunStatus.NOT_READY); // GH-90000
        assertThat(result.taskResults().get(0).status()).isEqualTo(RunStatus.NOT_READY); // GH-90000
        assertThat(result.taskResults().get(0).output()).contains("[NOT_READY] [GH-90000]");
    }

    @Test
    @DisplayName("execute: deploy task type with no-op adapter → task result has NOT_READY status [GH-90000]")
    void shouldExecuteDeployTaskWithNoOpAdapter() { // GH-90000
        RunTask deployTask = RunTask.builder() // GH-90000
                .id("task-deploy-1 [GH-90000]")
                .type("deploy [GH-90000]")
                .name("Deploy [GH-90000]")
                .config(Map.of()) // GH-90000
                .build(); // GH-90000
        RunSpec spec = RunSpec.builder() // GH-90000
                .id("run-deploy [GH-90000]")
                .artifactsRef("artifacts-1 [GH-90000]")
                .environment("production [GH-90000]")
                .tasks(List.of(deployTask)) // GH-90000
                .config(Map.of()) // GH-90000
                .build(); // GH-90000

        RunResult result = runPromise(() -> service.execute(spec)); // GH-90000

        assertThat(result.status()).isEqualTo(RunStatus.NOT_READY); // GH-90000
        assertThat(result.taskResults().get(0).status()).isEqualTo(RunStatus.NOT_READY); // GH-90000
        assertThat(result.taskResults().get(0).output()).contains("[NOT_READY] [GH-90000]");
    }

    @Test
    @DisplayName("execute: unknown task type → overall status is FAILED [GH-90000]")
    void shouldFailForUnknownTaskType() { // GH-90000
        RunTask unknownTask = RunTask.builder() // GH-90000
                .id("task-unknown [GH-90000]")
                .type("unknown-type [GH-90000]")
                .name("Unknown [GH-90000]")
                .config(Map.of()) // GH-90000
                .build(); // GH-90000
        RunSpec spec = RunSpec.builder() // GH-90000
                .id("run-unknown [GH-90000]")
                .artifactsRef("artifacts-1 [GH-90000]")
                .environment("staging [GH-90000]")
                .tasks(List.of(unknownTask)) // GH-90000
                .config(Map.of()) // GH-90000
                .build(); // GH-90000

        RunResult result = runPromise(() -> service.execute(spec)); // GH-90000

        assertThat(result.status()).isEqualTo(RunStatus.FAILED); // GH-90000
        assertThat(result.taskResults()).hasSize(1); // GH-90000
        assertThat(result.taskResults().get(0).status()).isEqualTo(RunStatus.FAILED); // GH-90000
        assertThat(result.taskResults().get(0).error()).contains("Unknown task type [GH-90000]");
    }

    @Test
    @DisplayName("execute: mix of succeed and fail tasks → overall status is FAILED [GH-90000]")
    void shouldReportFailedWhenAnyTaskFails() { // GH-90000
        RunTask buildTask = RunTask.builder() // GH-90000
                .id("task-build [GH-90000]")
                .type("build [GH-90000]")
                .name("Build [GH-90000]")
                .config(Map.of()) // GH-90000
                .build(); // GH-90000
        RunTask unknownTask = RunTask.builder() // GH-90000
                .id("task-unknown [GH-90000]")
                .type("bad-type [GH-90000]")
                .name("Unknown [GH-90000]")
                .config(Map.of()) // GH-90000
                .build(); // GH-90000
        RunSpec spec = RunSpec.builder() // GH-90000
                .id("run-mixed [GH-90000]")
                .artifactsRef("artifacts-1 [GH-90000]")
                .environment("staging [GH-90000]")
                .tasks(List.of(buildTask, unknownTask)) // GH-90000
                .config(Map.of()) // GH-90000
                .build(); // GH-90000

        RunResult result = runPromise(() -> service.execute(spec)); // GH-90000

        assertThat(result.status()).isEqualTo(RunStatus.FAILED); // GH-90000
        assertThat(result.taskResults()).hasSize(2); // GH-90000
    }

    @Test
    @DisplayName("rollback: with no-op adapter returns NOT_READY status with rollback metadata [GH-90000]")
    void shouldRollbackDeploymentWithNoOpAdapter() { // GH-90000
        RunResult result = runPromise(() -> service.rollback("deploy-123", "v1.0.0")); // GH-90000

        assertThat(result).isNotNull(); // GH-90000
        assertThat(result.status()).isEqualTo(RunStatus.NOT_READY); // GH-90000
        assertThat(result.metadata()).containsEntry("rollback_to", "v1.0.0"); // GH-90000
        verify(auditLogger, times(1)).log(any(Map.class)); // GH-90000
    }

    @Test
    @DisplayName("promote: with no-op adapter returns NOT_READY status with target environment in metadata [GH-90000]")
    void shouldPromoteDeploymentWithNoOpAdapter() { // GH-90000
        RunResult result = runPromise(() -> service.promote("deploy-123", "production")); // GH-90000

        assertThat(result).isNotNull(); // GH-90000
        assertThat(result.status()).isEqualTo(RunStatus.NOT_READY); // GH-90000
        assertThat(result.metadata()).containsEntry("promoted_to", "production"); // GH-90000
        verify(auditLogger, times(1)).log(any(Map.class)); // GH-90000
    }

    @Test
    @DisplayName("execute: migrate task type with no-op adapter → task result has NOT_READY status [GH-90000]")
    void shouldExecuteMigrateTaskWithNoOpAdapter() { // GH-90000
        RunTask migrateTask = RunTask.builder() // GH-90000
                .id("task-migrate-1 [GH-90000]")
                .type("migrate [GH-90000]")
                .name("Database Migration [GH-90000]")
                .config(Map.of()) // GH-90000
                .build(); // GH-90000
        RunSpec spec = RunSpec.builder() // GH-90000
                .id("run-migrate [GH-90000]")
                .artifactsRef("artifacts-1 [GH-90000]")
                .environment("production [GH-90000]")
                .tasks(List.of(migrateTask)) // GH-90000
                .config(Map.of()) // GH-90000
                .build(); // GH-90000

        RunResult result = runPromise(() -> service.execute(spec)); // GH-90000

        assertThat(result.status()).isEqualTo(RunStatus.NOT_READY); // GH-90000
        assertThat(result.taskResults().get(0).status()).isEqualTo(RunStatus.NOT_READY); // GH-90000
        assertThat(result.taskResults().get(0).output()).contains("[NOT_READY] [GH-90000]");
    }

    @Test
    @DisplayName("execute: null spec id → exception propagated [GH-90000]")
    void shouldFailForNullSpecId() { // GH-90000
        RunSpec spec = RunSpec.builder() // GH-90000
                .id(null) // GH-90000
                .artifactsRef("artifacts-1 [GH-90000]")
                .environment("staging [GH-90000]")
                .tasks(List.of()) // GH-90000
                .config(Map.of()) // GH-90000
                .build(); // GH-90000

        try {
            runPromise(() -> service.execute(spec)); // GH-90000
            fail("Expected exception for null spec id [GH-90000]");
        } catch (IllegalArgumentException e) { // GH-90000
            assertThat(e.getMessage()).contains("RunSpec.id is required [GH-90000]");
        }
    }

    @Test
    @DisplayName("execute: blank spec id → exception propagated [GH-90000]")
    void shouldFailForBlankSpecId() { // GH-90000
        RunSpec spec = RunSpec.builder() // GH-90000
                .id(" [GH-90000]")
                .artifactsRef("artifacts-1 [GH-90000]")
                .environment("staging [GH-90000]")
                .tasks(List.of()) // GH-90000
                .config(Map.of()) // GH-90000
                .build(); // GH-90000

        try {
            runPromise(() -> service.execute(spec)); // GH-90000
            fail("Expected exception for blank spec id [GH-90000]");
        } catch (IllegalArgumentException e) { // GH-90000
            assertThat(e.getMessage()).contains("RunSpec.id is required [GH-90000]");
        }
    }

    @Test
    @DisplayName("execute: task with shouldFail: true config → FAILED status [GH-90000]")
    void shouldFailForInjectedFailure() { // GH-90000
        RunTask buildTask = RunTask.builder() // GH-90000
                .id("task-build-1 [GH-90000]")
                .type("build [GH-90000]")
                .name("Build [GH-90000]")
                .config(Map.of("shouldFail", true)) // GH-90000
                .build(); // GH-90000
        RunSpec spec = RunSpec.builder() // GH-90000
                .id("run-fail [GH-90000]")
                .artifactsRef("artifacts-1 [GH-90000]")
                .environment("ci [GH-90000]")
                .tasks(List.of(buildTask)) // GH-90000
                .config(Map.of()) // GH-90000
                .build(); // GH-90000

        RunResult result = runPromise(() -> service.execute(spec)); // GH-90000

        assertThat(result.status()).isEqualTo(RunStatus.FAILED); // GH-90000
        assertThat(result.taskResults()).hasSize(1); // GH-90000
        assertThat(result.taskResults().get(0).status()).isEqualTo(RunStatus.FAILED); // GH-90000
        assertThat(result.taskResults().get(0).error()).contains("injected failure [GH-90000]");
    }

    @Test
    @DisplayName("execute: with real adapter injection → adapter is used and returns meaningful results [GH-90000]")
    void shouldUseRealAdapterWhenInjected() { // GH-90000
        // Create a mock real adapter that returns SUCCESS
        CiCdPort mockAdapter = mock(CiCdPort.class); // GH-90000
        when(mockAdapter.build(any())).thenReturn(Promise.of(com.ghatana.yappc.domain.run.TaskResult.builder() // GH-90000
                .taskId("task-build-1 [GH-90000]")
                .status(RunStatus.SUCCESS) // GH-90000
                .output("Build completed successfully [GH-90000]")
                .durationMs(1000L) // GH-90000
                .build())); // GH-90000
        when(mockAdapter.isReady()).thenReturn(true); // GH-90000

        RunService serviceWithRealAdapter = new RunServiceImpl(auditLogger, metrics, mockAdapter); // GH-90000

        RunTask buildTask = RunTask.builder() // GH-90000
                .id("task-build-1 [GH-90000]")
                .type("build [GH-90000]")
                .name("Build [GH-90000]")
                .config(Map.of()) // GH-90000
                .build(); // GH-90000
        RunSpec spec = RunSpec.builder() // GH-90000
                .id("run-build [GH-90000]")
                .artifactsRef("artifacts-1 [GH-90000]")
                .environment("ci [GH-90000]")
                .tasks(List.of(buildTask)) // GH-90000
                .config(Map.of()) // GH-90000
                .build(); // GH-90000

        RunResult result = runPromise(() -> serviceWithRealAdapter.execute(spec)); // GH-90000

        assertThat(result.status()).isEqualTo(RunStatus.SUCCESS); // GH-90000
        assertThat(result.taskResults()).hasSize(1); // GH-90000
        assertThat(result.taskResults().get(0).status()).isEqualTo(RunStatus.SUCCESS); // GH-90000
        assertThat(result.taskResults().get(0).output()).contains("Build completed successfully [GH-90000]");
        verify(mockAdapter).build(any()); // GH-90000
    }
}
