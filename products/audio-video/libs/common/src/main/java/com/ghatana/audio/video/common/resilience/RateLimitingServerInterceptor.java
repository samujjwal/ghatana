package com.ghatana.audio.video.common.resilience;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Token-bucket rate limiter implemented as a gRPC server interceptor.
 *
 * <p>Limits are applied per-method. The bucket refills at a configurable rate
 * (tokens/second). When the bucket is empty the call is rejected with
 * {@link Status#RESOURCE_EXHAUSTED} so clients can back off and retry.
 *
 * <p>Configuration via environment variables (all optional):
 * <ul>
 *   <li>{@code AV_RATE_LIMIT_TPS} — global tokens per second (default 50)</li>
 *   <li>{@code AV_RATE_LIMIT_BURST} — maximum burst size (default 100)</li>
 *   <li>{@code AV_RATE_LIMIT_MAX_CONCURRENT} — maximum concurrent calls (default 20)</li>
 * </ul>
 */
public class RateLimitingServerInterceptor implements ServerInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(RateLimitingServerInterceptor.class);

    private final double tokensPerSecond;
    private final long maxBurst;
    private final Semaphore concurrencySemaphore;

    /** Per-method token bucket state: [availableTokens, lastRefillNanos] */
    private final ConcurrentHashMap<String, long[]> buckets = new ConcurrentHashMap<>();

    public RateLimitingServerInterceptor() {
        this.tokensPerSecond = parseEnvDouble("AV_RATE_LIMIT_TPS", 50.0);
        this.maxBurst        = parseEnvLong("AV_RATE_LIMIT_BURST", 100L);
        int maxConcurrent    = (int) parseEnvLong("AV_RATE_LIMIT_MAX_CONCURRENT", 20L);
        this.concurrencySemaphore = new Semaphore(maxConcurrent, true);

        LOG.info("Rate limiter initialised: tps={} burst={} maxConcurrent={}",
                tokensPerSecond, maxBurst, maxConcurrent);
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        String method = call.getMethodDescriptor().getFullMethodName();

        // 1. Concurrency check
        if (!concurrencySemaphore.tryAcquire()) {
            LOG.warn("Concurrency limit exceeded for {}", method);
            call.close(Status.RESOURCE_EXHAUSTED
                    .withDescription("Server is overloaded. Try again later."), new Metadata());
            return new ServerCall.Listener<>() {};
        }

        // 2. Token bucket check
        if (!tryConsume(method)) {
            concurrencySemaphore.release();
            LOG.warn("Rate limit exceeded for {}", method);
            call.close(Status.RESOURCE_EXHAUSTED
                    .withDescription("Rate limit exceeded. Retry after back-off."), new Metadata());
            return new ServerCall.Listener<>() {};
        }

        // 3. Proceed, releasing the concurrency permit on close
        ServerCall<ReqT, RespT> wrappedCall =
                new io.grpc.ForwardingServerCall.SimpleForwardingServerCall<>(call) {
                    @Override
                    public void close(Status status, Metadata trailers) {
                        concurrencySemaphore.release();
                        super.close(status, trailers);
                    }
                };

        return next.startCall(wrappedCall, headers);
    }

    // -------------------------------------------------------------------------
    // Token bucket
    // -------------------------------------------------------------------------

    private synchronized boolean tryConsume(String method) {
        long nowNs = System.nanoTime();
        long[] state = buckets.computeIfAbsent(method, k -> new long[]{maxBurst, nowNs});

        // Refill tokens based on elapsed time
        long elapsedNs = nowNs - state[1];
        long newTokens  = (long) (elapsedNs * tokensPerSecond / 1_000_000_000.0);
        state[0] = Math.min(maxBurst, state[0] + newTokens);
        if (newTokens > 0) state[1] = nowNs;

        if (state[0] >= 1) {
            state[0]--;
            return true;
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static double parseEnvDouble(String name, double def) {
        String val = System.getenv(name);
        if (val == null) return def;
        try { return Double.parseDouble(val); } catch (NumberFormatException e) { return def; }
    }

    private static long parseEnvLong(String name, long def) {
        String val = System.getenv(name);
        if (val == null) return def;
        try { return Long.parseLong(val); } catch (NumberFormatException e) { return def; }
    }
}
