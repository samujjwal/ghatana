package com.ghatana.yappc.services.run;

import com.ghatana.yappc.domain.run.RunStatus;
import com.ghatana.yappc.domain.run.RunTask;
import com.ghatana.yappc.domain.run.TaskResult;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * No-op CI/CD adapter that returns NOT_READY status.
 * 
 * <p>
 * <b>Purpose</b><br>
 * Provides a safe default implementation when no real CI/CD system
 * is configured. Returns NOT_READY status instead of fake SUCCESS,
 * making the system honest about its execution state.
 * 
 * <p>
 * <b>Behavior</b><br>
 * - All methods return RunStatus.NOT_READY
 * - Logs warnings indicating no real CI/CD adapter is connected
 * - Provides clear guidance on what needs to be configured
 * 
 * <p>
 * <b>Usage</b><br>
 * This should be the default adapter injected when no real CI/CD
 * integration is configured via environment or launcher.
 *
 * @doc.type class
 * @doc.purpose No-op CI/CD adapter for when no real system is configured
 * @doc.layer service
 * @doc.pattern Null Object
 */
public class NoOpCiCdAdapter implements CiCdPort {

    private static final Logger log = LoggerFactory.getLogger(NoOpCiCdAdapter.class);

    @Override
    public Promise<TaskResult> build(RunTask task) {
        log.warn("[NOT_READY] No real CI/CD build system is connected. "
            + "Task '{}' (id={}) cannot execute. "
            + "Configure a real CI/CD adapter (e.g., Jenkins, GitHub Actions) to enable builds.",
            task.name() == null || task.name().isBlank() ? task.id() : task.name(), task.id());

        return Promise.of(TaskResult.builder()
                .taskId(task.id())
                .status(RunStatus.NOT_READY)
                .output("[NOT_READY] Build cannot execute - no CI/CD adapter configured. "
                    + "Configure a real CI/CD adapter to enable build execution.")
                .durationMs(0L)
                .build());
    }

    @Override
    public Promise<TaskResult> test(RunTask task) {
        log.warn("[NOT_READY] No real test runner is connected. "
            + "Task '{}' (id={}) cannot execute. "
            + "Configure a real CI/CD adapter to enable test execution.",
            task.name() == null || task.name().isBlank() ? task.id() : task.name(), task.id());

        return Promise.of(TaskResult.builder()
                .taskId(task.id())
                .status(RunStatus.NOT_READY)
                .output("[NOT_READY] Test cannot execute - no CI/CD adapter configured. "
                    + "Configure a real CI/CD adapter to enable test execution.")
                .durationMs(0L)
                .build());
    }

    @Override
    public Promise<TaskResult> deploy(RunTask task) {
        String targetEnvironment = task.config() != null && task.config().get("environment") != null
            ? String.valueOf(task.config().get("environment"))
            : "default";

        log.warn("[NOT_READY] No real deployment adapter is connected. "
            + "Task '{}' (id={}) targeting '{}' cannot execute. "
            + "Configure a real CI/CD adapter to enable deployments.",
            task.name() == null || task.name().isBlank() ? task.id() : task.name(), task.id(), targetEnvironment);

        return Promise.of(TaskResult.builder()
                .taskId(task.id())
                .status(RunStatus.NOT_READY)
                .output("[NOT_READY] Deployment cannot execute - no CI/CD adapter configured. "
                    + "Configure a real CI/CD adapter to enable deployment execution.")
                .durationMs(0L)
                .build());
    }

    @Override
    public Promise<TaskResult> migrate(RunTask task) {
        log.warn("[NOT_READY] No real migration runner is connected. "
            + "Task '{}' (id={}) cannot execute. "
            + "Configure a real CI/CD adapter to enable schema migrations.",
            task.name() == null || task.name().isBlank() ? task.id() : task.name(), task.id());

        return Promise.of(TaskResult.builder()
                .taskId(task.id())
                .status(RunStatus.NOT_READY)
                .output("[NOT_READY] Migration cannot execute - no CI/CD adapter configured. "
                    + "Configure a real CI/CD adapter to enable migration execution.")
                .durationMs(0L)
                .build());
    }

    @Override
    public Promise<TaskResult> rollback(String deploymentId, String targetVersion) {
        log.warn("[NOT_READY] No real rollback mechanism is connected. "
            + "Deployment '{}' rollback to '{}' cannot execute. "
            + "Configure a real CI/CD adapter to enable rollback.",
            deploymentId, targetVersion);

        return Promise.of(TaskResult.builder()
                .taskId(deploymentId)
                .status(RunStatus.NOT_READY)
                .output("[NOT_READY] Rollback cannot execute - no CI/CD adapter configured. "
                    + "Configure a real CI/CD adapter to enable rollback execution.")
                .durationMs(0L)
                .build());
    }

    @Override
    public boolean isReady() {
        return false;
    }
}
