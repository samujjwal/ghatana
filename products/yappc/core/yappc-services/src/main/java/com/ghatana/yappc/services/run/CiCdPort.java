package com.ghatana.yappc.services.run;

import com.ghatana.yappc.domain.run.RunTask;
import com.ghatana.yappc.domain.run.TaskResult;
import io.activej.promise.Promise;

import java.util.Map;

/**
 * Port interface for CI/CD system integration.
 * 
 * <p>
 * <b>Purpose</b><br>
 * Defines the contract for integrating with external CI/CD systems
 * such as Jenkins, GitHub Actions, GitLab CI, or custom build pipelines.
 * Implementations provide real build, test, deploy, migrate, and rollback
 * capabilities.
 * 
 * <p>
 * <b>Responsibilities</b><br>
 * - Execute build tasks with configuration
 * - Execute test suites with coverage reporting
 * - Deploy artifacts to target environments
 * - Run database migrations with rollback support
 * - Rollback deployments to previous versions
 * 
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * CiCdPort adapter = new JenkinsCiCdAdapter(jenkinsConfig);
 * TaskResult result = adapter.build(task).await();
 * }</pre>
 *
 * @doc.type interface
 * @doc.purpose CI/CD system integration port
 * @doc.layer service
 * @doc.pattern Port
 */
public interface CiCdPort {

    /**
     * Executes a build task.
     *
     * @param task The build task specification
     * @return Promise of task result with build output, logs, and status
     */
    Promise<TaskResult> build(RunTask task);

    /**
     * Executes a test task.
     *
     * @param task The test task specification
     * @return Promise of task result with test results, coverage, and status
     */
    Promise<TaskResult> test(RunTask task);

    /**
     * Executes a deployment task.
     *
     * @param task The deployment task specification
     * @return Promise of task result with deployment status and metadata
     */
    Promise<TaskResult> deploy(RunTask task);

    /**
     * Executes a database migration task.
     *
     * @param task The migration task specification
     * @return Promise of task result with migration status and schema version
     */
    Promise<TaskResult> migrate(RunTask task);

    /**
     * Rolls back a deployment to a previous version.
     *
     * @param deploymentId The deployment ID to rollback
     * @param targetVersion The target version to rollback to
     * @return Promise of rollback result with status and metadata
     */
    Promise<TaskResult> rollback(String deploymentId, String targetVersion);

    /**
     * Checks if the adapter is ready to execute tasks.
     *
     * @return true if the adapter is configured and ready, false otherwise
     */
    boolean isReady();
}
