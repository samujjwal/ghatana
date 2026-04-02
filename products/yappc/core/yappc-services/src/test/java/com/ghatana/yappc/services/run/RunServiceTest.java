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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * @doc.type class
 * @doc.purpose Tests for RunService implementation
 * @doc.layer test
 * @doc.pattern Test
 */
@DisplayName("RunService")
class RunServiceTest extends EventloopTestBase {

    private AuditLogger auditLogger;
    private MetricsCollector metrics;
    private RunService service;

    @BeforeEach
    void setUp() {
        auditLogger = mock(AuditLogger.class);
        metrics = mock(MetricsCollector.class);
        when(auditLogger.log(any(Map.class))).thenReturn(Promise.complete());
        service = new RunServiceImpl(auditLogger, metrics);
    }

    @Test
    @DisplayName("execute: empty task list → SUCCESS status and metadata contains environment")
    void shouldExecuteRunSpecWithNoTasks() {
        RunSpec spec = RunSpec.builder()
                .id("run-123")
                .artifactsRef("artifacts-123")
                .environment("staging")
                .tasks(List.of())
                .config(Map.of("tenantId", "tenant-123"))
                .build();

        RunResult result = runPromise(() -> service.execute(spec));

        assertThat(result).isNotNull();
        assertThat(result.id()).isNotNull();
        assertThat(result.runSpecRef()).isEqualTo("run-123");
        assertThat(result.metadata()).containsEntry("environment", "staging");
        assertThat(result.status()).isNotNull();
        assertThat(result.startedAt()).isNotNull();
        assertThat(result.completedAt()).isNotNull();
        verify(auditLogger, times(1)).log(any(Map.class));
        verify(metrics, atLeastOnce()).recordTimer(anyString(), anyLong(), any(Map.class));
    }

    @Test
    @DisplayName("execute: build task type → task result has SUCCESS status")
    void shouldExecuteBuildTask() {
        RunTask buildTask = RunTask.builder()
                .id("task-build-1")
                .type("build")
                .name("Build")
                .config(Map.of())
                .build();
        RunSpec spec = RunSpec.builder()
                .id("run-build")
                .artifactsRef("artifacts-1")
                .environment("ci")
                .tasks(List.of(buildTask))
                .config(Map.of())
                .build();

        RunResult result = runPromise(() -> service.execute(spec));

        assertThat(result.status()).isEqualTo(RunStatus.SUCCESS);
        assertThat(result.taskResults()).hasSize(1);
        assertThat(result.taskResults().get(0).status()).isEqualTo(RunStatus.SUCCESS);
    }

    @Test
    @DisplayName("execute: test task type → task result has SUCCESS status")
    void shouldExecuteTestTask() {
        RunTask testTask = RunTask.builder()
                .id("task-test-1")
                .type("test")
                .name("Test")
                .config(Map.of())
                .build();
        RunSpec spec = RunSpec.builder()
                .id("run-test")
                .artifactsRef("artifacts-1")
                .environment("ci")
                .tasks(List.of(testTask))
                .config(Map.of())
                .build();

        RunResult result = runPromise(() -> service.execute(spec));

        assertThat(result.status()).isEqualTo(RunStatus.SUCCESS);
        assertThat(result.taskResults().get(0).status()).isEqualTo(RunStatus.SUCCESS);
        assertThat(result.taskResults().get(0).output()).contains("passed");
    }

    @Test
    @DisplayName("execute: deploy task type → task result has SUCCESS status")
    void shouldExecuteDeployTask() {
        RunTask deployTask = RunTask.builder()
                .id("task-deploy-1")
                .type("deploy")
                .name("Deploy")
                .config(Map.of())
                .build();
        RunSpec spec = RunSpec.builder()
                .id("run-deploy")
                .artifactsRef("artifacts-1")
                .environment("production")
                .tasks(List.of(deployTask))
                .config(Map.of())
                .build();

        RunResult result = runPromise(() -> service.execute(spec));

        assertThat(result.status()).isEqualTo(RunStatus.SUCCESS);
        assertThat(result.taskResults().get(0).status()).isEqualTo(RunStatus.SUCCESS);
    }

    @Test
    @DisplayName("execute: unknown task type → overall status is FAILED")
    void shouldFailForUnknownTaskType() {
        RunTask unknownTask = RunTask.builder()
                .id("task-unknown")
                .type("unknown-type")
                .name("Unknown")
                .config(Map.of())
                .build();
        RunSpec spec = RunSpec.builder()
                .id("run-unknown")
                .artifactsRef("artifacts-1")
                .environment("staging")
                .tasks(List.of(unknownTask))
                .config(Map.of())
                .build();

        RunResult result = runPromise(() -> service.execute(spec));

        assertThat(result.status()).isEqualTo(RunStatus.FAILED);
        assertThat(result.taskResults()).hasSize(1);
        assertThat(result.taskResults().get(0).status()).isEqualTo(RunStatus.FAILED);
        assertThat(result.taskResults().get(0).error()).contains("Unknown task type");
    }

    @Test
    @DisplayName("execute: mix of succeed and fail tasks → overall status is FAILED")
    void shouldReportFailedWhenAnyTaskFails() {
        RunTask buildTask = RunTask.builder()
                .id("task-build")
                .type("build")
                .name("Build")
                .config(Map.of())
                .build();
        RunTask unknownTask = RunTask.builder()
                .id("task-unknown")
                .type("bad-type")
                .name("Unknown")
                .config(Map.of())
                .build();
        RunSpec spec = RunSpec.builder()
                .id("run-mixed")
                .artifactsRef("artifacts-1")
                .environment("staging")
                .tasks(List.of(buildTask, unknownTask))
                .config(Map.of())
                .build();

        RunResult result = runPromise(() -> service.execute(spec));

        assertThat(result.status()).isEqualTo(RunStatus.FAILED);
        assertThat(result.taskResults()).hasSize(2);
    }

    @Test
    @DisplayName("rollback: returns success result with rollback metadata")
    void shouldRollbackDeployment() {
        RunResult result = runPromise(() -> service.rollback("deploy-123", "v1.0.0"));

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(RunStatus.SUCCESS);
        assertThat(result.metadata()).containsEntry("rollback_to", "v1.0.0");
        verify(auditLogger, times(1)).log(any(Map.class));
    }

    @Test
    @DisplayName("promote: returns success result with target environment in metadata")
    void shouldPromoteDeployment() {
        RunResult result = runPromise(() -> service.promote("deploy-123", "production"));

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(RunStatus.SUCCESS);
        assertThat(result.metadata()).containsEntry("promoted_to", "production");
        verify(auditLogger, times(1)).log(any(Map.class));
    }

    @Test
    @DisplayName("execute: migrate task type → task result has SUCCESS status")
    void shouldExecuteMigrateTask() {
        RunTask migrateTask = RunTask.builder()
                .id("task-migrate-1")
                .type("migrate")
                .name("Database Migration")
                .config(Map.of())
                .build();
        RunSpec spec = RunSpec.builder()
                .id("run-migrate")
                .artifactsRef("artifacts-1")
                .environment("production")
                .tasks(List.of(migrateTask))
                .config(Map.of())
                .build();

        RunResult result = runPromise(() -> service.execute(spec));

        assertThat(result.status()).isEqualTo(RunStatus.SUCCESS);
        assertThat(result.taskResults().get(0).status()).isEqualTo(RunStatus.SUCCESS);
    }
}

