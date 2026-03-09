package com.ghatana.audio.video.common;

import com.ghatana.audio.video.common.observability.MetricsServerInterceptor;
import com.ghatana.audio.video.common.observability.TracingServerInterceptor;
import com.ghatana.audio.video.common.resilience.CircuitBreakerServerInterceptor;
import com.ghatana.audio.video.common.resilience.RateLimitingServerInterceptor;
import com.ghatana.audio.video.common.security.InputValidationServerInterceptor;
import com.ghatana.audio.video.common.security.JwtServerInterceptor;
import io.grpc.ServerInterceptor;

import java.util.List;

/**
 * Factory that assembles the standard interceptor chain for audio-video gRPC servers.
 *
 * <p>Interceptors are applied in the order returned by {@link #build()}.
 * gRPC applies interceptors in reverse-list order (last = outermost), so the chain
 * as seen by an incoming request is:
 * <pre>
 *   TracingInterceptor → RateLimiter → CircuitBreaker → JwtInterceptor → Service
 * </pre>
 *
 * <p>Usage in a gRPC server setup:
 * <pre>{@code
 *   ServerBuilder.forPort(port)
 *       .addService(myService)
 *       .intercept(GrpcInterceptorChain.build())  // single varargs call
 *       .build();
 * }</pre>
 */
public final class GrpcInterceptorChain {

    private GrpcInterceptorChain() {}

    /**
     * Build the full interceptor chain.
     *
     * @return list of interceptors in application order (innermost first for gRPC)
     */
    public static List<ServerInterceptor> build() {
        return List.of(
                new JwtServerInterceptor(),
                new InputValidationServerInterceptor(),
                new CircuitBreakerServerInterceptor(),
                new RateLimitingServerInterceptor(),
                MetricsServerInterceptor.getInstance(),
                new TracingServerInterceptor()
        );
    }
}
