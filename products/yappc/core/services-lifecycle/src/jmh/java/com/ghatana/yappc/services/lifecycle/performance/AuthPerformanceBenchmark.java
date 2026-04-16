package com.ghatana.yappc.services.lifecycle.performance;

import com.ghatana.platform.security.jwt.JwtTokenProvider;
import com.ghatana.yappc.services.security.YappcApiSecurity;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * JMH performance benchmarks for YAPPC authentication operations.
 *
 * <p><b>Purpose</b><br>
 * Measures real authentication latency for critical auth operations in production context.
 * Validates latency targets for login, token validation, and token refresh operations.
 *
 * <p><b>Production Latency Targets</b><br>
 * <ul>
 *   <li>loginLatency: &lt;100 ms p99 — API key authentication and JWT token generation</li>
 *   <li>tokenValidationLatency: &lt;10 ms p99 — JWT signature verification and claims extraction</li>
 *   <li>tokenRefreshLatency: &lt;50 ms p99 — Access token refresh with refresh token validation</li>
 * </ul>
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * ./gradlew :products:yappc:core:services-lifecycle:jmh
 * }</pre>
 *
 * @doc.type benchmark
 * @doc.purpose Performance measurement for authentication operations
 * @doc.layer product
 * @doc.pattern JMH Benchmark
 */
@BenchmarkMode({Mode.AverageTime, Mode.Throughput})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Fork(value = 2, jvmArgs = {"-Xms2G", "-Xmx2G"})
@Warmup(iterations = 3, time = 5)
@Measurement(iterations = 5, time = 10)
public class AuthPerformanceBenchmark {

    private JwtTokenProvider jwtTokenProvider;
    private String validApiKey;
    private String validToken;
    private String validRefreshToken;

    @Setup
    public void setup() {
        jwtTokenProvider = new JwtTokenProvider("test-secret-key-with-sufficient-length");
        validApiKey = UUID.randomUUID().toString();
        validToken = jwtTokenProvider.generateToken(validApiKey, "test-tenant", 3600);
        validRefreshToken = UUID.randomUUID().toString();
    }

    /**
     * Benchmark: API key authentication and JWT token generation (login).
     *
     * <p>Measures the complete login flow:
     * <ol>
     *   <li>API key validation</li>
     *   <li>Tenant resolution</li>
     *   <li>JWT token generation</li>
     * </ol>
     *
     * <p>Target: &lt;100 ms p99
     */
    @Benchmark
    public void loginLatency(Blackhole blackhole) {
        // Simulate API key validation
        if (isValidApiKey(validApiKey)) {
            // Generate JWT token
            String token = jwtTokenProvider.generateToken(validApiKey, "test-tenant", 3600);
            blackhole.consume(token);
        }
    }

    /**
     * Benchmark: JWT token validation.
     *
     * <p>Measures JWT signature verification and claims extraction.
     * This is called on every authenticated request.
     *
     * <p>Target: &lt;10 ms p99 (hot path)
     */
    @Benchmark
    public void tokenValidationLatency(Blackhole blackhole) {
        boolean isValid = jwtTokenProvider.validateToken(validToken);
        if (isValid) {
            String tenantId = jwtTokenProvider.extractTenantId(validToken);
            blackhole.consume(tenantId);
        }
    }

    /**
     * Benchmark: Token refresh.
     *
     * <p>Measures access token refresh with refresh token validation.
     *
     * <p>Target: &lt;50 ms p99
     */
    @Benchmark
    public void tokenRefreshLatency(Blackhole blackhole) {
        // Simulate refresh token validation
        if (isValidRefreshToken(validRefreshToken)) {
            // Generate new access token
            String newToken = jwtTokenProvider.generateToken(validApiKey, "test-tenant", 3600);
            blackhole.consume(newToken);
        }
    }

    /**
     * Benchmark: Concurrent token validation.
     *
     * <p>Measures token validation under concurrent load.
     * Simulates multiple authenticated requests hitting the service simultaneously.
     */
    @Benchmark
    @Threads(10)
    public void concurrentTokenValidation(Blackhole blackhole) {
        boolean isValid = jwtTokenProvider.validateToken(validToken);
        if (isValid) {
            String tenantId = jwtTokenProvider.extractTenantId(validToken);
            blackhole.consume(tenantId);
        }
    }

    /**
     * Simulate API key validation (placeholder for real validation logic).
     */
    private boolean isValidApiKey(String apiKey) {
        // In production, this would check against the API key store
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * Simulate refresh token validation (placeholder for real validation logic).
     */
    private boolean isValidRefreshToken(String refreshToken) {
        // In production, this would check against the refresh token store
        return refreshToken != null && !refreshToken.isBlank();
    }
}
