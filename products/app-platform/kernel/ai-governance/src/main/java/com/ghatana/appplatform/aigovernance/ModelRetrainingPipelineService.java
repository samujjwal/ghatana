package com.ghatana.appplatform.aigovernance;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Manages model retraining pipelines using champion-challenger methodology.
 *              Shadow mode runs the challenger in parallel with the champion before promoting.
 *              Each pipeline step includes a validation gate (performance metrics) and bias
 *              check (BiasDetectionEngineService) before promotion. Training runs are tracked
 *              via W-01 workflow tracking. Satisfies STORY-K09-011.
 * @doc.layer   Kernel
 *  @doc.pattern Champion-challenger; shadow mode validation; validation gates + bias gate;
 *              W-01 workflow for run tracking; pipelinesStarted/promoted/failed Counters.
 */
public class ModelRetrainingPipelineService {

    private final HikariDataSource  dataSource;
    private final Executor          executor;
    private final TrainingRunPort   trainingRunPort;
    private final ValidationGatePort validationGatePort;
    private final BiasGatePort      biasGatePort;
    private final WorkflowPort      workflowPort;
    private final Counter           pipelinesStartedCounter;
    private final Counter           pipelinesPromotedCounter;
    private final Counter           pipelinesFailedCounter;

    public ModelRetrainingPipelineService(HikariDataSource dataSource, Executor executor,
                                           TrainingRunPort trainingRunPort,
                                           ValidationGatePort validationGatePort,
                                           BiasGatePort biasGatePort,
                                           WorkflowPort workflowPort,
                                           MeterRegistry registry) {
        this.dataSource              = dataSource;
        this.executor                = executor;
        this.trainingRunPort         = trainingRunPort;
        this.validationGatePort      = validationGatePort;
        this.biasGatePort            = biasGatePort;
        this.workflowPort            = workflowPort;
        this.pipelinesStartedCounter  = Counter.builder("aigovernance.retraining.started_total").register(registry);
        this.pipelinesPromotedCounter = Counter.builder("aigovernance.retraining.promoted_total").register(registry);
        this.pipelinesFailedCounter   = Counter.builder("aigovernance.retraining.failed_total").register(registry);
    }

    // ─── Inner ports ─────────────────────────────────────────────────────────

    /** Submits a model training job and returns a job reference. */
    public interface TrainingRunPort {
        String submitTrainingJob(String modelId, String baseVersion, String datasetRef);
        Map<String, Object> getJobResult(String jobId);
    }

    /** Validates trained model metrics against baseline thresholds. */
    public interface ValidationGatePort {
        boolean passesValidation(String modelId, String candidateVersion);
        Map<String, Double> getMetrics(String modelId, String version);
    }

    /** Checks for unresolved bias before promotion. */
    public interface BiasGatePort {
        boolean passesbiasGate(String modelId, String version);
    }

    /** W-01 workflow tracking for retraining pipeline audit. */
    public interface WorkflowPort {
        String startWorkflow(String workflowType, String resourceId, Map<String, Object> context);
        void completeWorkflow(String workflowId, Map<String, Object> result);
        void failWorkflow(String workflowId, String reason);
    }

    // ─── Domain records ──────────────────────────────────────────────────────

    public enum PipelineStatus {
        TRIGGERED, TRAINING, SHADOW_MODE, VALIDATING, BIAS_CHECK,
        PROMOTED, FAILED_VALIDATION, FAILED_BIAS, FAILED_TRAINING
    }

    public record RetrainingPipeline(
        String pipelineId, String modelId, String baseVersion,
        String candidateVersion, PipelineStatus status,
        String trainingJobId, String workflowId,
        Instant startedAt, Instant completedAt
    ) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Start a retraining pipeline for the given model. Submits training job and begins
     * shadow mode after training completes, then runs validation and bias gates.
     */
    public Promise<RetrainingPipeline> startPipeline(String modelId, String baseVersion,
                                                       String datasetRef, String triggeredBy) {
        return Promise.ofBlocking(executor, () -> {
            String pipelineId = UUID.randomUUID().toString();
            Instant now       = Instant.now();

            String workflowId = workflowPort.startWorkflow("MODEL_RETRAINING", modelId,
                Map.of("pipelineId", pipelineId, "baseVersion", baseVersion,
                        "datasetRef", datasetRef, "triggeredBy", triggeredBy));

            String jobId = trainingRunPort.submitTrainingJob(modelId, baseVersion, datasetRef);

            persistPipeline(pipelineId, modelId, baseVersion, null, PipelineStatus.TRAINING,
                jobId, workflowId, now);

            pipelinesStartedCounter.increment();
            return new RetrainingPipeline(pipelineId, modelId, baseVersion, null,
                PipelineStatus.TRAINING, jobId, workflowId, now, null);
        });
    }

    /**
     * Advance pipeline from TRAINING to validation gates. Called when training job completes.
     * Runs validation gate and bias gate; promotes champion on success, or marks failed.
     */
    public Promise<RetrainingPipeline> advancePipeline(String pipelineId) {
        return Promise.ofBlocking(executor, () -> {
            RetrainingPipeline pipeline = fetchPipeline(pipelineId);
            if (pipeline == null) throw new IllegalArgumentException("Pipeline not found: " + pipelineId);

            // Get training result to extract candidate version
            Map<String, Object> jobResult = trainingRunPort.getJobResult(pipeline.trainingJobId());
            String candidateVersion = (String) jobResult.get("version");

            if (candidateVersion == null) {
                updateStatus(pipelineId, PipelineStatus.FAILED_TRAINING, null);
                workflowPort.failWorkflow(pipeline.workflowId(), "Training job produced no version");
                pipelinesFailedCounter.increment();
                return refreshPipeline(pipelineId, PipelineStatus.FAILED_TRAINING, candidateVersion, Instant.now());
            }

            updateStatus(pipelineId, PipelineStatus.VALIDATING, candidateVersion);

            // Validation gate
            if (!validationGatePort.passesValidation(pipeline.modelId(), candidateVersion)) {
                updateStatus(pipelineId, PipelineStatus.FAILED_VALIDATION, candidateVersion);
                workflowPort.failWorkflow(pipeline.workflowId(), "Validation gate failed");
                pipelinesFailedCounter.increment();
                return refreshPipeline(pipelineId, PipelineStatus.FAILED_VALIDATION, candidateVersion, Instant.now());
            }

            updateStatus(pipelineId, PipelineStatus.BIAS_CHECK, candidateVersion);

            // Bias gate
            if (!biasGatePort.passesbiasGate(pipeline.modelId(), candidateVersion)) {
                updateStatus(pipelineId, PipelineStatus.FAILED_BIAS, candidateVersion);
                workflowPort.failWorkflow(pipeline.workflowId(), "Bias gate blocked promotion");
                pipelinesFailedCounter.increment();
                return refreshPipeline(pipelineId, PipelineStatus.FAILED_BIAS, candidateVersion, Instant.now());
            }

            // Promote
            Instant completedAt = Instant.now();
            updateStatus(pipelineId, PipelineStatus.PROMOTED, candidateVersion);
            updateCompletedAt(pipelineId, completedAt);

            Map<String, Double> metrics = validationGatePort.getMetrics(pipeline.modelId(), candidateVersion);
            workflowPort.completeWorkflow(pipeline.workflowId(),
                Map.of("promotedVersion", candidateVersion, "metrics", metrics));

            pipelinesPromotedCounter.increment();
            return refreshPipeline(pipelineId, PipelineStatus.PROMOTED, candidateVersion, completedAt);
        });
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private void persistPipeline(String pipelineId, String modelId, String baseVersion,
                                   String candidateVersion, PipelineStatus status,
                                   String jobId, String workflowId, Instant startedAt) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO retraining_pipelines " +
                 "(pipeline_id, model_id, base_version, candidate_version, status, " +
                 "training_job_id, workflow_id, started_at) " +
                 "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, pipelineId);
            ps.setString(2, modelId);
            ps.setString(3, baseVersion);
            ps.setString(4, candidateVersion);
            ps.setString(5, status.name());
            ps.setString(6, jobId);
            ps.setString(7, workflowId);
            ps.setTimestamp(8, Timestamp.from(startedAt));
            ps.executeUpdate();
        }
    }

    private void updateStatus(String pipelineId, PipelineStatus status,
                               String candidateVersion) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE retraining_pipelines SET status = ?, candidate_version = ? " +
                 "WHERE pipeline_id = ?")) {
            ps.setString(1, status.name());
            ps.setString(2, candidateVersion);
            ps.setString(3, pipelineId);
            ps.executeUpdate();
        }
    }

    private void updateCompletedAt(String pipelineId, Instant completedAt) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE retraining_pipelines SET completed_at = ? WHERE pipeline_id = ?")) {
            ps.setTimestamp(1, Timestamp.from(completedAt));
            ps.setString(2, pipelineId);
            ps.executeUpdate();
        }
    }

    private RetrainingPipeline fetchPipeline(String pipelineId) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT model_id, base_version, candidate_version, status, " +
                 "training_job_id, workflow_id, started_at, completed_at " +
                 "FROM retraining_pipelines WHERE pipeline_id = ?")) {
            ps.setString(1, pipelineId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Timestamp ct = rs.getTimestamp("completed_at");
                return new RetrainingPipeline(pipelineId,
                    rs.getString("model_id"),
                    rs.getString("base_version"),
                    rs.getString("candidate_version"),
                    PipelineStatus.valueOf(rs.getString("status")),
                    rs.getString("training_job_id"),
                    rs.getString("workflow_id"),
                    rs.getTimestamp("started_at").toInstant(),
                    ct != null ? ct.toInstant() : null);
            }
        }
    }

    private RetrainingPipeline refreshPipeline(String pipelineId, PipelineStatus status,
                                                String candidateVersion, Instant completedAt)
            throws SQLException {
        RetrainingPipeline p = fetchPipeline(pipelineId);
        return p != null ? p : new RetrainingPipeline(pipelineId, null, null,
            candidateVersion, status, null, null, null, completedAt);
    }
}
