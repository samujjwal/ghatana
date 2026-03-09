/*
 * Copyright (c) 2024 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.orchestrator.queue.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.orchestrator.queue.ExecutionJob;
import com.ghatana.orchestrator.queue.ExecutionQueue;
import io.activej.inject.annotation.Inject;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Day 39: Durable, distributed execution queue backed by PostgreSQL.
 * Uses SKIP LOCKED for efficient concurrent job claiming.
 */
public class PostgresExecutionQueue implements ExecutionQueue {

    private static final Logger logger = LoggerFactory.getLogger(PostgresExecutionQueue.class);

    private final DataSource dataSource;
    private final Executor executor;
    private final ObjectMapper objectMapper;
    private final String workerId;

    @Inject
    public PostgresExecutionQueue(DataSource dataSource, Executor executor, ObjectMapper objectMapper) {
        this.dataSource = dataSource;
        this.executor = executor;
        this.objectMapper = objectMapper;
        this.workerId = UUID.randomUUID().toString();
    }

    @Override
    public Promise<Void> enqueue(String tenantId, String pipelineId, Object triggerData, String idempotencyKey) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection()) {
                String sql = "INSERT INTO execution_queue " +
                        "(job_id, tenant_id, pipeline_id, idempotency_key, trigger_data, status, enqueued_at) " +
                        "VALUES (?, ?, ?, ?, ?::jsonb, 'PENDING', NOW()) " +
                        "ON CONFLICT (tenant_id, idempotency_key) DO NOTHING";

                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, UUID.randomUUID().toString());
                    ps.setString(2, tenantId);
                    ps.setString(3, pipelineId);
                    ps.setString(4, idempotencyKey);
                    ps.setString(5, objectMapper.writeValueAsString(triggerData));

                    int rows = ps.executeUpdate();
                    if (rows == 0) {
                        logger.debug("Duplicate job ignored: tenantId={}, idempotencyKey={}", tenantId, idempotencyKey);
                    } else {
                        logger.info("Enqueued job: tenantId={}, pipelineId={}, idempotencyKey={}", tenantId, pipelineId, idempotencyKey);
                    }
                }
            } catch (Exception e) {
                logger.error("Failed to enqueue job", e);
                throw new RuntimeException("Failed to enqueue job", e);
            }
            return null;
        });
    }

    @Override
    public int size() {
        // Approximate size (count of pending jobs)
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM execution_queue WHERE status = 'PENDING'")) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to get queue size", e);
        }
        return 0;
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public Promise<Void> clear() {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("DELETE FROM execution_queue")) {
                int deleted = ps.executeUpdate();
                logger.info("Cleared execution queue, deleted {} jobs", deleted);
            } catch (SQLException e) {
                logger.error("Failed to clear queue", e);
                throw new RuntimeException("Failed to clear queue", e);
            }
            return null;
        });
    }

    /**
     * Poll for jobs using the stored procedure with SKIP LOCKED.
     *
     * @param maxJobs Maximum number of jobs to claim
     * @param visibilityTimeoutSeconds How long to lock the jobs
     * @return List of claimed jobs
     */
    public Promise<List<ExecutionJob>> poll(int maxJobs, int visibilityTimeoutSeconds) {
        return Promise.ofBlocking(executor, () -> {
            List<ExecutionJob> jobs = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT * FROM poll_execution_queue(?, ?, ?)")) {

                ps.setString(1, workerId);
                ps.setInt(2, maxJobs);
                ps.setInt(3, visibilityTimeoutSeconds);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        jobs.add(mapResultSetToJob(rs));
                    }
                }
            } catch (Exception e) {
                logger.error("Failed to poll jobs", e);
                throw new RuntimeException("Failed to poll jobs", e);
            }
            return jobs;
        });
    }

    private ExecutionJob mapResultSetToJob(ResultSet rs) throws SQLException {
        try {
            String tenantId = rs.getString("tenant_id");
            String pipelineId = rs.getString("pipeline_id");
            String idempotencyKey = rs.getString("idempotency_key");
            String jobId = rs.getString("job_id");
            String triggerDataJson = rs.getString("trigger_data");
            Object triggerData = triggerDataJson != null ?
                objectMapper.readValue(triggerDataJson, Object.class) : null;

            // Note: instanceId is not in execution_queue table yet, assuming it's generated later or part of triggerData
            // For now passing null or extracting from triggerData if possible
            String instanceId = null;

            return new ExecutionJob(tenantId, pipelineId, triggerData, idempotencyKey, jobId, instanceId);
        } catch (Exception e) {
            throw new SQLException("Failed to map job data", e);
        }
    }

    /**
     * Mark a job as completed.
     */
    @Override
    public Promise<Void> complete(String jobId, String status, Object result) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "UPDATE execution_queue SET status = ?, result = ?::jsonb, completed_at = NOW() WHERE job_id = ?")) {

                ps.setString(1, status);
                ps.setString(2, result != null ? objectMapper.writeValueAsString(result) : null);
                ps.setString(3, jobId);

                ps.executeUpdate();
            } catch (Exception e) {
                logger.error("Failed to complete job: {}", jobId, e);
                throw new RuntimeException("Failed to complete job", e);
            }
            return null;
        });
    }
}
