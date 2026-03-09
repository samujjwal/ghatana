package com.ghatana.platform.testing.chaos;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Random;
import java.util.concurrent.TimeoutException;

/**
 * Central class for injecting chaos into test executions.
 *
 * <p>This class provides static methods to inject various types of failures
 * based on the current {@link ChaosContext}. It uses thread-local storage
 * to maintain chaos state per test thread.</p>
 *
 * @doc.type class
 * @doc.purpose Injects failures based on active chaos context
 * @doc.layer core
 * @doc.pattern Utility
 */
public final class ChaosInjector {

    private static final ThreadLocal<ChaosContext> CURRENT_CONTEXT = new ThreadLocal<>();
    private static final Random RANDOM = new Random();

    private ChaosInjector() {
        // Utility class
    }

    /**
     * Activates chaos injection for the current thread.
     *
     * @param context the chaos context to activate
     */
    public static void activate(ChaosContext context) {
        CURRENT_CONTEXT.set(context);
    }

    /**
     * Deactivates chaos injection for the current thread.
     */
    public static void deactivate() {
        CURRENT_CONTEXT.remove();
    }

    /**
     * Returns the current chaos context, if any.
     */
    public static ChaosContext getContext() {
        return CURRENT_CONTEXT.get();
    }

    /**
     * Checks if chaos injection is currently active.
     */
    public static boolean isActive() {
        ChaosContext ctx = CURRENT_CONTEXT.get();
        return ctx != null && ctx.isActive();
    }

    /**
     * Potentially injects a network failure.
     *
     * @throws IOException          if network chaos is injected
     * @throws SocketTimeoutException if timeout chaos is injected
     */
    public static void maybeInjectNetworkFailure() throws IOException {
        ChaosContext ctx = CURRENT_CONTEXT.get();
        if (ctx == null || !ctx.isActive()) {
            return;
        }

        ChaosType type = ctx.getChaosType();
        if (type != ChaosType.NETWORK && type != ChaosType.RANDOM) {
            return;
        }

        if (ctx.shouldInjectFailure()) {
            int failureType = RANDOM.nextInt(3);
            switch (failureType) {
                case 0:
                    throw new SocketTimeoutException("Chaos: Connection timed out");
                case 1:
                    throw new IOException("Chaos: Connection refused");
                default:
                    throw new IOException("Chaos: Network unreachable");
            }
        }
    }

    /**
     * Potentially injects latency.
     *
     * @param maxLatencyMs maximum latency to inject
     */
    public static void maybeInjectLatency(long maxLatencyMs) {
        ChaosContext ctx = CURRENT_CONTEXT.get();
        if (ctx == null || !ctx.isActive()) {
            return;
        }

        ChaosType type = ctx.getChaosType();
        if (type != ChaosType.LATENCY && type != ChaosType.RANDOM) {
            return;
        }

        if (ctx.shouldInjectFailure()) {
            long delay = RANDOM.nextLong(maxLatencyMs);
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Potentially injects a service unavailable error.
     *
     * @throws ServiceUnavailableException if chaos is injected
     */
    public static void maybeInjectServiceUnavailable() throws ServiceUnavailableException {
        ChaosContext ctx = CURRENT_CONTEXT.get();
        if (ctx == null || !ctx.isActive()) {
            return;
        }

        ChaosType type = ctx.getChaosType();
        if (type != ChaosType.SERVICE_UNAVAILABLE && type != ChaosType.RANDOM) {
            return;
        }

        if (ctx.shouldInjectFailure()) {
            throw new ServiceUnavailableException("Chaos: Service temporarily unavailable");
        }
    }

    /**
     * Potentially injects a timeout.
     *
     * @throws TimeoutException if timeout chaos is injected
     */
    public static void maybeInjectTimeout() throws TimeoutException {
        ChaosContext ctx = CURRENT_CONTEXT.get();
        if (ctx == null || !ctx.isActive()) {
            return;
        }

        ChaosType type = ctx.getChaosType();
        if (type != ChaosType.NETWORK && type != ChaosType.LATENCY && type != ChaosType.RANDOM) {
            return;
        }

        if (ctx.shouldInjectFailure()) {
            throw new TimeoutException("Chaos: Operation timed out");
        }
    }

    /**
     * Potentially corrupts data by returning null or modified value.
     *
     * @param original the original value
     * @param <T>      the type of value
     * @return the original or corrupted value
     */
    public static <T> T maybeCorruptData(T original) {
        ChaosContext ctx = CURRENT_CONTEXT.get();
        if (ctx == null || !ctx.isActive()) {
            return original;
        }

        ChaosType type = ctx.getChaosType();
        if (type != ChaosType.DATA_CORRUPTION && type != ChaosType.RANDOM) {
            return original;
        }

        if (ctx.shouldInjectFailure()) {
            // Return null to simulate data corruption
            return null;
        }

        return original;
    }

    /**
     * Potentially injects resource exhaustion.
     *
     * @throws ResourceExhaustedException if chaos is injected
     */
    public static void maybeInjectResourceExhaustion() throws ResourceExhaustedException {
        ChaosContext ctx = CURRENT_CONTEXT.get();
        if (ctx == null || !ctx.isActive()) {
            return;
        }

        ChaosType type = ctx.getChaosType();
        if (type != ChaosType.RESOURCE_EXHAUSTION && type != ChaosType.RANDOM) {
            return;
        }

        if (ctx.shouldInjectFailure()) {
            int failureType = RANDOM.nextInt(3);
            switch (failureType) {
                case 0:
                    throw new ResourceExhaustedException("Chaos: Out of memory");
                case 1:
                    throw new ResourceExhaustedException("Chaos: Too many open files");
                default:
                    throw new ResourceExhaustedException("Chaos: Connection pool exhausted");
            }
        }
    }

    /**
     * Exception thrown when a service is unavailable due to chaos injection.
     */
    public static class ServiceUnavailableException extends Exception {
        public ServiceUnavailableException(String message) {
            super(message);
        }
    }

    /**
     * Exception thrown when resources are exhausted due to chaos injection.
     */
    public static class ResourceExhaustedException extends Exception {
        public ResourceExhaustedException(String message) {
            super(message);
        }
    }
}
