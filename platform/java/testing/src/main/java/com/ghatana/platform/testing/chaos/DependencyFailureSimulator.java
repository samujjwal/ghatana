/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.testing.chaos;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.sql.SQLException;
import java.util.concurrent.TimeoutException;

/**
 * Production-grade dependency failure simulator for atomic workflow testing.
 *
 * <p>This class simulates real dependency failures (Postgres, ClickHouse, OpenSearch, S3, etc.)
 * for P1-1 and P1-2 failure-injection testing. It uses the chaos framework to inject
 * deterministic failures at specific workflow boundaries.</p>
 *
 * <p>Supported dependency types:
 * <ul>
 *   <li>PostgreSQL - database connection failures, query timeouts, deadlocks</li>
 *   <li>ClickHouse - analytics store failures, query errors</li>
 *   <li>OpenSearch - search index failures, timeout errors</li>
 *   <li>S3 - object storage failures, upload/download errors</li>
 *   <li>Audit Sink - audit write failures, unavailability</li>
 *   <li>Policy Engine - policy evaluation failures, timeout</li>
 *   <li>AI Completion - LLM gateway failures, rate limits</li>
 *   <li>Network - timeouts, connection refused, unreachable</li>
 *   <li>Queue - saturation, backpressure, full queue</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Simulates real dependency failures for atomic workflow testing (P1-1, P1-2)
 * @doc.layer core
 * @doc.pattern Simulator
 */
public final class DependencyFailureSimulator {

    private DependencyFailureSimulator() {
        // Utility class
    }

    /**
     * Simulates PostgreSQL dependency failure.
     *
     * @param operation the operation to execute
     * @param <T> the return type
     * @return the operation result
     * @throws SQLException if Postgres failure is injected
     */
    public static <T> T withPostgresFailure(ThrowingSupplier<T> operation) throws SQLException {
        ChaosContext ctx = ChaosInjector.getContext();
        if (ctx == null || !ctx.isActive()) {
            return getOrThrowSql(operation);
        }

        ChaosType type = ctx.getChaosType();
        if (type == ChaosType.SERVICE_UNAVAILABLE && ctx.shouldInjectFailure()) {
            throw new SQLException("P1-2: PostgreSQL unavailable - connection refused");
        }

        if (type == ChaosType.NETWORK && ctx.shouldInjectFailure()) {
            throw new SQLException("P1-2: PostgreSQL network timeout", new SocketTimeoutException());
        }

        if (type == ChaosType.RESOURCE_EXHAUSTION && ctx.shouldInjectFailure()) {
            throw new SQLException("P1-2: PostgreSQL connection pool exhausted");
        }

        if (type == ChaosType.PARTIAL_FAILURE && ctx.shouldInjectFailure()) {
            throw new SQLException("P1-2: PostgreSQL deadlock detected");
        }

        return getOrThrowSql(operation);
    }

    /**
     * Simulates ClickHouse dependency failure.
     *
     * @param operation the operation to execute
     * @param <T> the return type
     * @return the operation result
     * @throws RuntimeException if ClickHouse failure is injected
     */
    public static <T> T withClickHouseFailure(ThrowingSupplier<T> operation) {
        ChaosContext ctx = ChaosInjector.getContext();
        if (ctx == null || !ctx.isActive()) {
            return getOrThrowUnchecked(operation);
        }

        ChaosType type = ctx.getChaosType();
        if (type == ChaosType.SERVICE_UNAVAILABLE && ctx.shouldInjectFailure()) {
            throw new RuntimeException("P1-2: ClickHouse unavailable - service down");
        }

        if (type == ChaosType.NETWORK && ctx.shouldInjectFailure()) {
            throw new RuntimeException("P1-2: ClickHouse query timeout");
        }

        if (type == ChaosType.DATA_CORRUPTION && ctx.shouldInjectFailure()) {
            throw new RuntimeException("P1-2: ClickHouse malformed query response");
        }

        return getOrThrowUnchecked(operation);
    }

    /**
     * Simulates OpenSearch dependency failure.
     *
     * @param operation the operation to execute
     * @param <T> the return type
     * @return the operation result
     * @throws IOException if OpenSearch failure is injected
     */
    public static <T> T withOpenSearchFailure(ThrowingSupplier<T> operation) throws IOException {
        ChaosContext ctx = ChaosInjector.getContext();
        if (ctx == null || !ctx.isActive()) {
            return getOrThrowIo(operation);
        }

        ChaosType type = ctx.getChaosType();
        if (type == ChaosType.SERVICE_UNAVAILABLE && ctx.shouldInjectFailure()) {
            throw new IOException("P1-2: OpenSearch unavailable - cluster health red");
        }

        if (type == ChaosType.NETWORK && ctx.shouldInjectFailure()) {
            throw new IOException("P1-2: OpenSearch connection timeout");
        }

        if (type == ChaosType.RESOURCE_EXHAUSTION && ctx.shouldInjectFailure()) {
            throw new IOException("P1-2: OpenSearch thread pool exhausted");
        }

        return getOrThrowIo(operation);
    }

    /**
     * Simulates S3 dependency failure.
     *
     * @param operation the operation to execute
     * @param <T> the return type
     * @return the operation result
     * @throws IOException if S3 failure is injected
     */
    public static <T> T withS3Failure(ThrowingSupplier<T> operation) throws IOException {
        ChaosContext ctx = ChaosInjector.getContext();
        if (ctx == null || !ctx.isActive()) {
            return getOrThrowIo(operation);
        }

        ChaosType type = ctx.getChaosType();
        if (type == ChaosType.SERVICE_UNAVAILABLE && ctx.shouldInjectFailure()) {
            throw new IOException("P1-2: S3 unavailable - service error 503");
        }

        if (type == ChaosType.NETWORK && ctx.shouldInjectFailure()) {
            throw new IOException("P1-2: S3 upload timeout");
        }

        if (type == ChaosType.RESOURCE_EXHAUSTION && ctx.shouldInjectFailure()) {
            throw new IOException("P1-2: S3 rate limit exceeded");
        }

        return getOrThrowIo(operation);
    }

    /**
     * Simulates audit sink dependency failure.
     *
     * @param operation the operation to execute
     * @param <T> the return type
     * @return the operation result
     * @throws RuntimeException if audit sink failure is injected
     */
    public static <T> T withAuditSinkFailure(ThrowingSupplier<T> operation) {
        ChaosContext ctx = ChaosInjector.getContext();
        if (ctx == null || !ctx.isActive()) {
            return getOrThrowUnchecked(operation);
        }

        ChaosType type = ctx.getChaosType();
        if (type == ChaosType.SERVICE_UNAVAILABLE && ctx.shouldInjectFailure()) {
            throw new RuntimeException("P1-2: Audit sink unavailable - write rejected");
        }

        if (type == ChaosType.NETWORK && ctx.shouldInjectFailure()) {
            throw new RuntimeException("P1-2: Audit sink connection timeout");
        }

        if (type == ChaosType.RESOURCE_EXHAUSTION && ctx.shouldInjectFailure()) {
            throw new RuntimeException("P1-2: Audit sink buffer full");
        }

        return getOrThrowUnchecked(operation);
    }

    /**
     * Simulates policy engine dependency failure.
     *
     * @param operation the operation to execute
     * @param <T> the return type
     * @return the operation result
     * @throws TimeoutException if policy engine failure is injected
     */
    public static <T> T withPolicyEngineFailure(ThrowingSupplier<T> operation) throws TimeoutException {
        ChaosContext ctx = ChaosInjector.getContext();
        if (ctx == null || !ctx.isActive()) {
            return getOrThrowTimeout(operation);
        }

        ChaosType type = ctx.getChaosType();
        if (type == ChaosType.SERVICE_UNAVAILABLE && ctx.shouldInjectFailure()) {
            throw new TimeoutException("P1-2: Policy engine unavailable - evaluation timeout");
        }

        if (type == ChaosType.NETWORK && ctx.shouldInjectFailure()) {
            throw new TimeoutException("P1-2: Policy engine network timeout");
        }

        if (type == ChaosType.LATENCY && ctx.shouldInjectFailure()) {
            ChaosInjector.maybeInjectLatency(5000);
        }

        return getOrThrowTimeout(operation);
    }

    /**
     * Simulates AI completion dependency failure.
     *
     * @param operation the operation to execute
     * @param <T> the return type
     * @return the operation result
     * @throws RuntimeException if AI completion failure is injected
     */
    public static <T> T withAICompletionFailure(ThrowingSupplier<T> operation) {
        ChaosContext ctx = ChaosInjector.getContext();
        if (ctx == null || !ctx.isActive()) {
            return getOrThrowUnchecked(operation);
        }

        ChaosType type = ctx.getChaosType();
        if (type == ChaosType.SERVICE_UNAVAILABLE && ctx.shouldInjectFailure()) {
            throw new RuntimeException("P1-2: AI completion unavailable - LLM gateway error");
        }

        if (type == ChaosType.NETWORK && ctx.shouldInjectFailure()) {
            throw new RuntimeException("P1-2: AI completion timeout");
        }

        if (type == ChaosType.RESOURCE_EXHAUSTION && ctx.shouldInjectFailure()) {
            throw new RuntimeException("P1-2: AI completion rate limit exceeded");
        }

        if (type == ChaosType.LATENCY && ctx.shouldInjectFailure()) {
            ChaosInjector.maybeInjectLatency(10000);
        }

        return getOrThrowUnchecked(operation);
    }

    /**
     * Simulates network timeout failure.
     *
     * @param operation the operation to execute
     * @param <T> the return type
     * @return the operation result
     * @throws TimeoutException if network timeout is injected
     */
    public static <T> T withNetworkTimeout(ThrowingSupplier<T> operation) throws TimeoutException {
        ChaosContext ctx = ChaosInjector.getContext();
        if (ctx == null || !ctx.isActive()) {
            return getOrThrowTimeout(operation);
        }

        ChaosType type = ctx.getChaosType();
        if ((type == ChaosType.NETWORK || type == ChaosType.LATENCY) && ctx.shouldInjectFailure()) {
            throw new TimeoutException("P1-2: Network timeout - operation exceeded deadline");
        }

        return getOrThrowTimeout(operation);
    }

    /**
     * Simulates queue saturation failure.
     *
     * @param operation the operation to execute
     * @param <T> the return type
     * @return the operation result
     * @throws RuntimeException if queue saturation is injected
     */
    public static <T> T withQueueSaturation(ThrowingSupplier<T> operation) {
        ChaosContext ctx = ChaosInjector.getContext();
        if (ctx == null || !ctx.isActive()) {
            return getOrThrowUnchecked(operation);
        }

        ChaosType type = ctx.getChaosType();
        if (type == ChaosType.RESOURCE_EXHAUSTION && ctx.shouldInjectFailure()) {
            throw new RuntimeException("P1-2: Queue saturated - backpressure rejection");
        }

        if (type == ChaosType.PARTIAL_FAILURE && ctx.shouldInjectFailure()) {
            throw new RuntimeException("P1-2: Queue full - message rejected");
        }

        return getOrThrowUnchecked(operation);
    }

    private static <T> T getOrThrowUnchecked(ThrowingSupplier<T> operation) {
        try {
            return operation.get();
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new RuntimeException("Dependency failure simulation operation failed", exception);
        }
    }

    private static <T> T getOrThrowSql(ThrowingSupplier<T> operation) throws SQLException {
        try {
            return operation.get();
        } catch (SQLException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new RuntimeException("Dependency failure simulation operation failed", exception);
        }
    }

    private static <T> T getOrThrowIo(ThrowingSupplier<T> operation) throws IOException {
        try {
            return operation.get();
        } catch (IOException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new RuntimeException("Dependency failure simulation operation failed", exception);
        }
    }

    private static <T> T getOrThrowTimeout(ThrowingSupplier<T> operation) throws TimeoutException {
        try {
            return operation.get();
        } catch (TimeoutException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new RuntimeException("Dependency failure simulation operation failed", exception);
        }
    }

    /**
     * Functional interface for operations that may throw checked exceptions.
     */
    @FunctionalInterface
    public interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
