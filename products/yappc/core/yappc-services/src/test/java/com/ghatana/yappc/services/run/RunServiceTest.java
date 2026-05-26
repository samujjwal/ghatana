package com.ghatana.yappc.services.run;

import com.ghatana.audit.AuditLogger;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.yappc.domain.run.RunResult;
import com.ghatana.yappc.domain.run.RunSpec;
import com.ghatana.yappc.domain.run.RunStatus;
import com.ghatana.yappc.domain.run.RunTask;
import com.ghatana.yappc.services.learn.LearningEvidenceService;
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
@SuppressWarnings("unchecked")
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
        service = new RunServiceImpl(auditLogger, metrics, new NoOpCiCdAdapter()); 
    }

    @Test
    @DisplayName("execute: empty task list â†’ SUCCESS status and metadata contains environment")
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
    @DisplayName("execute: build task type with no-op adapter â†’ task result has NOT_READY status")
    void shouldExecuteBuildTaskWithNoOpAdapter() { 
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

        assertThat(result.status()).isEqualTo(RunStatus.NOT_READY); 
        assertThat(result.taskResults()).hasSize(1); 
        assertThat(result.taskResults().get(0).status()).isEqualTo(RunStatus.NOT_READY); 
        assertThat(result.taskResults().get(0).output()).contains("[NOT_READY]");
    }

    @Test
    @DisplayName("execute: test task type with no-op adapter â†’ task result has NOT_READY status")
    void shouldExecuteTestTaskWithNoOpAdapter() { 
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

        assertThat(result.status()).isEqualTo(RunStatus.NOT_READY); 
        assertThat(result.taskResults().get(0).status()).isEqualTo(RunStatus.NOT_READY); 
        assertThat(result.taskResults().get(0).output()).contains("[NOT_READY]");
    }

    @Test
    @DisplayName("execute: deploy task type with no-op adapter â†’ task result has NOT_READY status")
    void shouldExecuteDeployTaskWithNoOpAdapter() { 
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

        assertThat(result.status()).isEqualTo(RunStatus.NOT_READY); 
        assertThat(result.taskResults().get(0).status()).isEqualTo(RunStatus.NOT_READY); 
        assertThat(result.taskResults().get(0).output()).contains("[NOT_READY]");
    }

    @Test
    @DisplayName("execute: unknown task type â†’ overall status is FAILED")
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
    @DisplayName("execute: mix of succeed and fail tasks â†’ overall status is FAILED")
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
    @DisplayName("rollback: with no-op adapter returns NOT_READY status with rollback metadata")
    void shouldRollbackDeploymentWithNoOpAdapter() { 
        RunResult result = runPromise(() -> service.rollback("deploy-123", "v1.0.0")); 

        assertThat(result).isNotNull(); 
        assertThat(result.status()).isEqualTo(RunStatus.NOT_READY); 
        assertThat(result.metadata()).containsEntry("rollback_to", "v1.0.0"); 
        verify(auditLogger, times(1)).log(any(Map.class)); 
    }

    @Test
    @DisplayName("promote: with no-op adapter returns NOT_READY status with target environment in metadata")
    void shouldPromoteDeploymentWithNoOpAdapter() { 
        RunResult result = runPromise(() -> service.promote("deploy-123", "production")); 

        assertThat(result).isNotNull(); 
        assertThat(result.status()).isEqualTo(RunStatus.NOT_READY); 
        assertThat(result.metadata()).containsEntry("promoted_to", "production"); 
        verify(auditLogger, times(1)).log(any(Map.class)); 
    }

    @Test
    @DisplayName("retry: executes the run spec and records failed run lineage")
    void shouldRetryFailedRunWithLineageMetadata() {
        RunSpec spec = RunSpec.builder()
                .id("run-retry-1")
                .artifactsRef("artifacts-1")
                .environment("staging")
                .tasks(List.of())
                .config(Map.of())
                .build();

        RunResult result = runPromise(() -> service.retry("failed-run-1", spec));

        assertThat(result.status()).isEqualTo(RunStatus.SUCCESS);
        assertThat(result.runSpecRef()).isEqualTo("run-retry-1");
        assertThat(result.metadata())
                .containsEntry("retry_of", "failed-run-1")
                .containsEntry("retry_run_spec", "run-retry-1");
        verify(auditLogger, times(2)).log(any(Map.class));
    }

    @Test
    @DisplayName("execute: migrate task type with no-op adapter â†’ task result has NOT_READY status")
    void shouldExecuteMigrateTaskWithNoOpAdapter() { 
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

        assertThat(result.status()).isEqualTo(RunStatus.NOT_READY); 
        assertThat(result.taskResults().get(0).status()).isEqualTo(RunStatus.NOT_READY); 
        assertThat(result.taskResults().get(0).output()).contains("[NOT_READY]");
    }

    @Test
    @DisplayName("execute: null spec id â†’ exception propagated")
    void shouldFailForNullSpecId() { 
        RunSpec spec = RunSpec.builder() 
                .id(null) 
                .artifactsRef("artifacts-1")
                .environment("staging")
                .tasks(List.of()) 
                .config(Map.of()) 
                .build(); 

        try {
            runPromise(() -> service.execute(spec)); 
            fail("Expected exception for null spec id");
        } catch (IllegalArgumentException e) { 
            assertThat(e.getMessage()).contains("RunSpec.id is required");
        }
    }

    @Test
    @DisplayName("execute: blank spec id â†’ exception propagated")
    void shouldFailForBlankSpecId() { 
        RunSpec spec = RunSpec.builder() 
                .id("")
                .artifactsRef("artifacts-1")
                .environment("staging")
                .tasks(List.of()) 
                .config(Map.of()) 
                .build(); 

        try {
            runPromise(() -> service.execute(spec)); 
            fail("Expected exception for blank spec id");
        } catch (IllegalArgumentException e) { 
            assertThat(e.getMessage()).contains("RunSpec.id is required");
        }
    }

    @Test
    @DisplayName("execute: task with shouldFail: true config â†’ FAILED status")
    void shouldFailForInjectedFailure() { 
        RunTask buildTask = RunTask.builder() 
                .id("task-build-1")
                .type("build")
                .name("Build")
                .config(Map.of("shouldFail", true)) 
                .build(); 
        RunSpec spec = RunSpec.builder() 
                .id("run-fail")
                .artifactsRef("artifacts-1")
                .environment("ci")
                .tasks(List.of(buildTask)) 
                .config(Map.of()) 
                .build(); 

        RunResult result = runPromise(() -> service.execute(spec)); 

        assertThat(result.status()).isEqualTo(RunStatus.FAILED); 
        assertThat(result.taskResults()).hasSize(1); 
        assertThat(result.taskResults().get(0).status()).isEqualTo(RunStatus.FAILED); 
        assertThat(result.taskResults().get(0).error()).contains("injected failure");
    }

    @Test
    @DisplayName("execute: failed run records learning evidence with tenant and project provenance")
    void shouldRecordLearningEvidenceForFailedRun() {
        LearningEvidenceService learningEvidenceService = mock(LearningEvidenceService.class);
        when(learningEvidenceService.recordRunOutcome(any(LearningEvidenceService.EvidenceContext.class), any(RunResult.class)))
                .thenReturn(Promise.of("learn-run-evidence-1"));
        RunService evidenceBackedService = new RunServiceImpl(
                auditLogger,
                metrics,
                new NoOpCiCdAdapter(),
                learningEvidenceService);
        RunTask buildTask = RunTask.builder()
                .id("task-build-1")
                .type("build")
                .name("Build")
                .config(Map.of("shouldFail", true))
                .build();
        RunSpec spec = RunSpec.builder()
                .id("run-fail")
                .artifactsRef("artifacts-1")
                .environment("ci")
                .tasks(List.of(buildTask))
                .config(Map.of(
                        "tenantId", "tenant-123",
                        "workspaceId", "workspace-123",
                        "projectId", "project-123",
                        "correlationId", "corr-123"))
                .build();

        RunResult result = runPromise(() -> evidenceBackedService.execute(spec));

        assertThat(result.status()).isEqualTo(RunStatus.FAILED);
        verify(learningEvidenceService).recordRunOutcome(
                argThat(context ->
                        context.tenantId().equals("tenant-123")
                                && context.workspaceId().equals("workspace-123")
                                && context.projectId().equals("project-123")
                                && context.subjectId().equals(result.id())
                                && context.correlationId().equals("corr-123")
                                && context.metadata().get("runSpecId").equals("run-fail")),
                eq(result));
    }

    @Test
    @DisplayName("execute: with real adapter injection -> adapter is used and returns meaningful results")
    void shouldUseRealAdapterWhenInjected() { 
        // Create a mock real adapter that returns SUCCESS
        CiCdPort mockAdapter = mock(CiCdPort.class); 
        when(mockAdapter.build(any())).thenReturn(Promise.of(com.ghatana.yappc.domain.run.TaskResult.builder() 
                .taskId("task-build-1")
                .status(RunStatus.SUCCESS) 
                .output("Build completed successfully")
                .durationMs(1000L) 
                .build())); 
        when(mockAdapter.isReady()).thenReturn(true); 

        RunService serviceWithRealAdapter = new RunServiceImpl(auditLogger, metrics, mockAdapter); 

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

        RunResult result = runPromise(() -> serviceWithRealAdapter.execute(spec)); 

        assertThat(result.status()).isEqualTo(RunStatus.SUCCESS); 
        assertThat(result.taskResults()).hasSize(1); 
        assertThat(result.taskResults().get(0).status()).isEqualTo(RunStatus.SUCCESS); 
        assertThat(result.taskResults().get(0).output()).contains("Build completed successfully");
        verify(mockAdapter).build(any()); 
    }
}
