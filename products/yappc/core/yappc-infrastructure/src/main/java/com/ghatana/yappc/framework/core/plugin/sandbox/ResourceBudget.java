/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Framework Module
 */
package com.ghatana.yappc.framework.core.plugin.sandbox;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Enforces wall-clock and (best-effort) memory resource limits when executing
 * a plugin invocation.
 *
 * <p><b>Wall-clock enforcement:</b> The callable is submitted to a dedicated
 * virtual-thread executor. If it does not complete within {@code maxWallMs}
 * milliseconds the thread is interrupted and a {@link PluginTimeoutException}
 * is thrown.
 *
 * <p><b>Memory enforcement:</b> Java does not support per-thread memory limits,
 * so memory monitoring is best-effort only — it checks heap usage before and
 * after the invocation and logs a warning when the delta exceeds
 * {@code maxMemoryBytes}.
 *
 * @doc.type class
 * @doc.purpose Enforces resource limits (wall time, memory) for plugin execution
 * @doc.layer product
 * @doc.pattern Decorator
 */
public class ResourceBudget {

    private static final Logger log = LoggerFactory.getLogger(ResourceBudget.class);

    /** Unlimited budget constant. */
    public static final long UNLIMITED = Long.MAX_VALUE;

    private final String pluginId;
    private final long maxWallMs;
    private final long maxMemoryBytes;

    /**
     * Creates a resource budget.
     *
     * @param pluginId       plugin this budget is tracking (for error messages)
     * @param maxWallMs      maximum allowed wall-clock milliseconds; use {@link #UNLIMITED}
     *                       to disable
     * @param maxMemoryBytes maximum allowed heap-delta bytes; use {@link #UNLIMITED}
     *                       to disable
     */
    public ResourceBudget(String pluginId, long maxWallMs, long maxMemoryBytes) {
        this.pluginId = pluginId;
        this.maxWallMs = maxWallMs;
        this.maxMemoryBytes = maxMemoryBytes;
    }

    /**
     * Creates an unlimited budget (no time or memory constraints).
     *
     * @param pluginId plugin identifier
     * @return unlimited budget
     */
    public static ResourceBudget unlimited(String pluginId) {
        return new ResourceBudget(pluginId, UNLIMITED, UNLIMITED);
    }

    /**
     * Executes {@code work} within this budget's constraints.
     *
     * @param <T>  return type of the callable
     * @param work the plugin invocation to execute
     * @return the value returned by {@code work}
     * @throws PluginTimeoutException if the wall-clock budget is exceeded
     * @throws Exception              if {@code work} throws
     */
    public <T> T execute(Callable<T> work) throws Exception {
        Runtime rt = Runtime.getRuntime();
        long heapBefore = rt.totalMemory() - rt.freeMemory();

        if (maxWallMs == UNLIMITED) {
            T result = work.call();
            checkMemory(rt, heapBefore);
            return result;
        }

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            Future<T> future = executor.submit(work);
            T result;
            try {
                result = future.get(maxWallMs, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                throw new PluginTimeoutException(pluginId, maxWallMs);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof Exception ex) {
                    throw ex;
                }
                throw new RuntimeException(cause);
            }
            checkMemory(rt, heapBefore);
            return result;
        } finally {
            executor.shutdownNow();
        }
    }

    private void checkMemory(Runtime rt, long heapBefore) {
        if (maxMemoryBytes == UNLIMITED) {
            return;
        }
        long heapAfter = rt.totalMemory() - rt.freeMemory();
        long delta = heapAfter - heapBefore;
        if (delta > maxMemoryBytes) {
            log.warn("Plugin '{}' heap delta {} bytes exceeded budget {} bytes (best-effort check).",
                    pluginId, delta, maxMemoryBytes);
        }
    }
}
