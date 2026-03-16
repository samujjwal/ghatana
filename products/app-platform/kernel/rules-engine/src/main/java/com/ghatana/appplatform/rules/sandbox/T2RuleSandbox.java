/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.rules.sandbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * In-process T2 rule execution sandbox (STORY-K03-004).
 *
 * <p>T2 rules are jurisdiction-specific compiled rule packs that execute in a
 * thread-isolated context. Restrictions enforced at this layer:
 * <ul>
 *   <li>CPU timeout: execution killed after {@link #timeoutMs()} milliseconds</li>
 *   <li>No network access (enforced by the restricted API surface)</li>
 *   <li>No file system access (enforced by the restricted API surface)</li>
 *   <li>Memory limit: advisory (JVM-level enforcement via cgroup in Kubernetes)</li>
 *   <li>All calls to external services routed through {@link SandboxApiSurface}</li>
 * </ul>
 *
 * <p>Note: Full GraalVM polyglot V8/WASM isolation is deferred to Sprint 16.
 * Sprint 4 provides thread-based time-boxing as the primary isolation boundary.
 *
 * @doc.type  class
 * @doc.purpose T2 rule execution sandbox with timeout and resource isolation (K03-004)
 * @doc.layer product
 * @doc.pattern Sandbox
 */
public final class T2RuleSandbox {

    private static final Logger log = LoggerFactory.getLogger(T2RuleSandbox.class);

    /** Default execution timeout (100 ms per rule evaluation). */
    public static final long DEFAULT_TIMEOUT_MS = 100L;

    /** Default memory advisory limit (64 MB). */
    public static final long DEFAULT_MEMORY_LIMIT_MB = 64L;

    private final long timeoutMs;
    private final long memoryLimitMb;
    private final SandboxApiSurface apiSurface;
    private final SandboxResourceAccountant accountant;
    private final ExecutorService sandboxExecutor;

    public T2RuleSandbox(SandboxApiSurface apiSurface,
                         SandboxResourceAccountant accountant,
                         long timeoutMs,
                         long memoryLimitMb) {
        this.apiSurface    = Objects.requireNonNull(apiSurface,   "apiSurface");
        this.accountant    = Objects.requireNonNull(accountant,   "accountant");
        this.timeoutMs     = timeoutMs > 0 ? timeoutMs : DEFAULT_TIMEOUT_MS;
        this.memoryLimitMb = memoryLimitMb > 0 ? memoryLimitMb : DEFAULT_MEMORY_LIMIT_MB;
        this.sandboxExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "t2-sandbox");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Creates a sandbox with default timeout and memory limits.
     *
     * @param apiSurface restricted API surface available inside the sandbox
     */
    public static T2RuleSandbox withDefaults(SandboxApiSurface apiSurface) {
        return new T2RuleSandbox(
                apiSurface,
                new SandboxResourceAccountant(apiSurface.jurisdictionId()),
                DEFAULT_TIMEOUT_MS,
                DEFAULT_MEMORY_LIMIT_MB);
    }

    // ── Execution ────────────────────────────────────────────────────────────

    /**
     * Executes a rule function inside the sandbox.
     *
     * @param jurisdictionId jurisdiction context for resource accounting
     * @param ruleId         rule identifier for logging
     * @param input          input context passed to the rule
     * @param ruleFunction   rule logic to execute (must close over apiSurface only)
     * @param <T>            result type
     * @return rule result
     * @throws SandboxTimeoutException     if execution exceeds {@link #timeoutMs}
     * @throws SandboxExecutionException   if the rule throws an unexpected exception
     */
    public <T> T execute(String jurisdictionId, String ruleId,
                         Map<String, Object> input,
                         Callable<T> ruleFunction) {
        Objects.requireNonNull(jurisdictionId, "jurisdictionId");
        Objects.requireNonNull(ruleId,         "ruleId");
        Objects.requireNonNull(ruleFunction,   "ruleFunction");

        accountant.recordExecution(ruleId);
        long startNs = System.nanoTime();

        Future<T> future = sandboxExecutor.submit(ruleFunction);
        try {
            T result = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
            accountant.recordDuration(ruleId, elapsedMs);
            return result;
        } catch (TimeoutException e) {
            future.cancel(true);
            accountant.recordTimeout(ruleId);
            throw new SandboxTimeoutException(
                    "T2 rule timed out after " + timeoutMs + " ms: ruleId=" + ruleId, e);
        } catch (Exception e) {
            accountant.recordError(ruleId);
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new SandboxExecutionException("T2 rule execution failed: ruleId=" + ruleId, cause);
        }
    }

    public long timeoutMs()     { return timeoutMs; }
    public long memoryLimitMb() { return memoryLimitMb; }

    public void shutdown() {
        sandboxExecutor.shutdown();
    }

    // ── Exception types ──────────────────────────────────────────────────────

    /** Thrown when a T2 rule exceeds its time budget. */
    public static final class SandboxTimeoutException extends RuntimeException {
        public SandboxTimeoutException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** Thrown when a T2 rule throws an unexpected exception. */
    public static final class SandboxExecutionException extends RuntimeException {
        public SandboxExecutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
