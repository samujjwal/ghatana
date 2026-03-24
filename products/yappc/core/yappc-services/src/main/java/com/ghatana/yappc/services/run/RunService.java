package com.ghatana.yappc.services.run;

import com.ghatana.yappc.domain.run.ObservationConfig;
import com.ghatana.yappc.domain.run.RunResult;
import com.ghatana.yappc.domain.run.RunSpec;
import io.activej.promise.Promise;

/**
 * @doc.type interface
 * @doc.purpose Executes generated systems (build, deploy, test)
 * @doc.layer service
 * @doc.pattern Service
 */
public interface RunService {
    /**
     * Executes the run specification (build/deploy/test).
     * 
     * @param spec The run specification
     * @return Promise of run result
     */
    Promise<RunResult> execute(RunSpec spec);
    
    /**
     * Executes with real-time observation streaming.
     * 
     * @param spec The run specification
     * @param config Observation configuration
     * @return Promise of run result with observations
     */
    Promise<RunResult> executeWithObservation(RunSpec spec, ObservationConfig config);

    /**
     * Rolls back a deployment to a previous version.
     * 
     * @param deploymentId ID of the deployment to rollback
     * @param targetVersion Version to rollback to
     * @return Promise of rollback result
     */
    Promise<RunResult> rollback(String deploymentId, String targetVersion);

    /**
     * Promotes a deployment to the next environment (e.g., Staging -> Prod).
     * 
     * @param deploymentId ID of the deployment to promote
     * @param targetEnvironment Target environment ID
     * @return Promise of promotion result
     */
    Promise<RunResult> promote(String deploymentId, String targetEnvironment);
}
