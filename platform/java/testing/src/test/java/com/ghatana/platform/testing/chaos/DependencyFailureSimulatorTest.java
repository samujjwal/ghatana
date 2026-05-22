/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.testing.chaos;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.sql.SQLException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Production-grade tests for dependency failure simulator (P1-2).
 *
 * <p>Validates that the simulator correctly injects failures for all dependency types:
 * <ul>
 *   <li>PostgreSQL unavailable, timeout, connection pool exhaustion, deadlock</li>
 *   <li>ClickHouse unavailable, timeout, malformed response</li>
 *   <li>OpenSearch unavailable, timeout, thread pool exhaustion</li>
 *   <li>S3 unavailable, timeout, rate limit</li>
 *   <li>Audit sink unavailable, timeout, buffer full</li>
 *   <li>Policy engine unavailable, timeout, latency</li>
 *   <li>AI completion unavailable, timeout, rate limit, latency</li>
 *   <li>Network timeout</li>
 *   <li>Queue saturation, backpressure</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Validates dependency failure simulator for P1-2 runtime failure injection
 * @doc.layer core
 * @doc.pattern UnitTest
 */
@DisplayName("Dependency Failure Simulator Tests (P1-2)")
class DependencyFailureSimulatorTest {

    @BeforeEach
    void setUp() {
        ChaosInjector.deactivate();
    }

    @Nested
    @DisplayName("PostgreSQL Failure Simulation")
    class PostgresFailureTests {

        @Test
        @DisplayName("should inject Postgres unavailable error")
        void shouldInjectPostgresUnavailable() {
            ChaosContext context = new ChaosContext(ChaosType.SERVICE_UNAVAILABLE, 1.0, 5000);
            ChaosInjector.activate(context);

            assertThatThrownBy(() -> DependencyFailureSimulator.withPostgresFailure(() -> "result"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("P1-2")
                .hasMessageContaining("PostgreSQL unavailable");
        }

        @Test
        @DisplayName("should inject Postgres network timeout")
        void shouldInjectPostgresTimeout() {
            ChaosContext context = new ChaosContext(ChaosType.NETWORK, 1.0, 5000);
            ChaosInjector.activate(context);

            assertThatThrownBy(() -> DependencyFailureSimulator.withPostgresFailure(() -> "result"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("P1-2")
                .hasMessageContaining("network timeout");
        }

        @Test
        @DisplayName("should inject Postgres connection pool exhaustion")
        void shouldInjectPostgresPoolExhaustion() {
            ChaosContext context = new ChaosContext(ChaosType.RESOURCE_EXHAUSTION, 1.0, 5000);
            ChaosInjector.activate(context);

            assertThatThrownBy(() -> DependencyFailureSimulator.withPostgresFailure(() -> "result"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("P1-2")
                .hasMessageContaining("connection pool exhausted");
        }

        @Test
        @DisplayName("should inject Postgres deadlock")
        void shouldInjectPostgresDeadlock() {
            ChaosContext context = new ChaosContext(ChaosType.PARTIAL_FAILURE, 1.0, 5000);
            ChaosInjector.activate(context);

            assertThatThrownBy(() -> DependencyFailureSimulator.withPostgresFailure(() -> "result"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("P1-2")
                .hasMessageContaining("deadlock");
        }

        @Test
        @DisplayName("should succeed when chaos inactive")
        void shouldSucceedWhenChaosInactive() throws SQLException {
            String result = DependencyFailureSimulator.withPostgresFailure(() -> "success");
            assertThat(result).isEqualTo("success");
        }
    }

    @Nested
    @DisplayName("ClickHouse Failure Simulation")
    class ClickHouseFailureTests {

        @Test
        @DisplayName("should inject ClickHouse unavailable error")
        void shouldInjectClickHouseUnavailable() {
            ChaosContext context = new ChaosContext(ChaosType.SERVICE_UNAVAILABLE, 1.0, 5000);
            ChaosInjector.activate(context);

            assertThatThrownBy(() -> DependencyFailureSimulator.withClickHouseFailure(() -> "result"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("P1-2")
                .hasMessageContaining("ClickHouse unavailable");
        }

        @Test
        @DisplayName("should inject ClickHouse query timeout")
        void shouldInjectClickHouseTimeout() {
            ChaosContext context = new ChaosContext(ChaosType.NETWORK, 1.0, 5000);
            ChaosInjector.activate(context);

            assertThatThrownBy(() -> DependencyFailureSimulator.withClickHouseFailure(() -> "result"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("P1-2")
                .hasMessageContaining("query timeout");
        }

        @Test
        @DisplayName("should inject ClickHouse malformed response")
        void shouldInjectClickHouseMalformedResponse() {
            ChaosContext context = new ChaosContext(ChaosType.DATA_CORRUPTION, 1.0, 5000);
            ChaosInjector.activate(context);

            assertThatThrownBy(() -> DependencyFailureSimulator.withClickHouseFailure(() -> "result"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("P1-2")
                .hasMessageContaining("malformed query response");
        }
    }

    @Nested
    @DisplayName("OpenSearch Failure Simulation")
    class OpenSearchFailureTests {

        @Test
        @DisplayName("should inject OpenSearch unavailable error")
        void shouldInjectOpenSearchUnavailable() throws IOException {
            ChaosContext context = new ChaosContext(ChaosType.SERVICE_UNAVAILABLE, 1.0, 5000);
            ChaosInjector.activate(context);

            assertThatThrownBy(() -> DependencyFailureSimulator.withOpenSearchFailure(() -> "result"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("P1-2")
                .hasMessageContaining("OpenSearch unavailable");
        }

        @Test
        @DisplayName("should inject OpenSearch connection timeout")
        void shouldInjectOpenSearchTimeout() throws IOException {
            ChaosContext context = new ChaosContext(ChaosType.NETWORK, 1.0, 5000);
            ChaosInjector.activate(context);

            assertThatThrownBy(() -> DependencyFailureSimulator.withOpenSearchFailure(() -> "result"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("P1-2")
                .hasMessageContaining("connection timeout");
        }

        @Test
        @DisplayName("should inject OpenSearch thread pool exhaustion")
        void shouldInjectOpenSearchPoolExhaustion() throws IOException {
            ChaosContext context = new ChaosContext(ChaosType.RESOURCE_EXHAUSTION, 1.0, 5000);
            ChaosInjector.activate(context);

            assertThatThrownBy(() -> DependencyFailureSimulator.withOpenSearchFailure(() -> "result"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("P1-2")
                .hasMessageContaining("thread pool exhausted");
        }
    }

    @Nested
    @DisplayName("S3 Failure Simulation")
    class S3FailureTests {

        @Test
        @DisplayName("should inject S3 unavailable error")
        void shouldInjectS3Unavailable() throws IOException {
            ChaosContext context = new ChaosContext(ChaosType.SERVICE_UNAVAILABLE, 1.0, 5000);
            ChaosInjector.activate(context);

            assertThatThrownBy(() -> DependencyFailureSimulator.withS3Failure(() -> "result"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("P1-2")
                .hasMessageContaining("S3 unavailable");
        }

        @Test
        @DisplayName("should inject S3 upload timeout")
        void shouldInjectS3Timeout() throws IOException {
            ChaosContext context = new ChaosContext(ChaosType.NETWORK, 1.0, 5000);
            ChaosInjector.activate(context);

            assertThatThrownBy(() -> DependencyFailureSimulator.withS3Failure(() -> "result"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("P1-2")
                .hasMessageContaining("upload timeout");
        }

        @Test
        @DisplayName("should inject S3 rate limit exceeded")
        void shouldInjectS3RateLimit() throws IOException {
            ChaosContext context = new ChaosContext(ChaosType.RESOURCE_EXHAUSTION, 1.0, 5000);
            ChaosInjector.activate(context);

            assertThatThrownBy(() -> DependencyFailureSimulator.withS3Failure(() -> "result"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("P1-2")
                .hasMessageContaining("rate limit exceeded");
        }
    }

    @Nested
    @DisplayName("Audit Sink Failure Simulation")
    class AuditSinkFailureTests {

        @Test
        @DisplayName("should inject audit sink unavailable error")
        void shouldInjectAuditSinkUnavailable() {
            ChaosContext context = new ChaosContext(ChaosType.SERVICE_UNAVAILABLE, 1.0, 5000);
            ChaosInjector.activate(context);

            assertThatThrownBy(() -> DependencyFailureSimulator.withAuditSinkFailure(() -> "result"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("P1-2")
                .hasMessageContaining("Audit sink unavailable");
        }

        @Test
        @DisplayName("should inject audit sink connection timeout")
        void shouldInjectAuditSinkTimeout() {
            ChaosContext context = new ChaosContext(ChaosType.NETWORK, 1.0, 5000);
            ChaosInjector.activate(context);

            assertThatThrownBy(() -> DependencyFailureSimulator.withAuditSinkFailure(() -> "result"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("P1-2")
                .hasMessageContaining("connection timeout");
        }

        @Test
        @DisplayName("should inject audit sink buffer full")
        void shouldInjectAuditSinkBufferFull() {
            ChaosContext context = new ChaosContext(ChaosType.RESOURCE_EXHAUSTION, 1.0, 5000);
            ChaosInjector.activate(context);

            assertThatThrownBy(() -> DependencyFailureSimulator.withAuditSinkFailure(() -> "result"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("P1-2")
                .hasMessageContaining("buffer full");
        }
    }

    @Nested
    @DisplayName("Policy Engine Failure Simulation")
    class PolicyEngineFailureTests {

        @Test
        @DisplayName("should inject policy engine unavailable error")
        void shouldInjectPolicyEngineUnavailable() throws TimeoutException {
            ChaosContext context = new ChaosContext(ChaosType.SERVICE_UNAVAILABLE, 1.0, 5000);
            ChaosInjector.activate(context);

            assertThatThrownBy(() -> DependencyFailureSimulator.withPolicyEngineFailure(() -> "result"))
                .isInstanceOf(TimeoutException.class)
                .hasMessageContaining("P1-2")
                .hasMessageContaining("Policy engine unavailable");
        }

        @Test
        @DisplayName("should inject policy engine network timeout")
        void shouldInjectPolicyEngineTimeout() throws TimeoutException {
            ChaosContext context = new ChaosContext(ChaosType.NETWORK, 1.0, 5000);
            ChaosInjector.activate(context);

            assertThatThrownBy(() -> DependencyFailureSimulator.withPolicyEngineFailure(() -> "result"))
                .isInstanceOf(TimeoutException.class)
                .hasMessageContaining("P1-2")
                .hasMessageContaining("network timeout");
        }

        @Test
        @DisplayName("should inject policy engine latency")
        void shouldInjectPolicyEngineLatency() throws TimeoutException {
            ChaosContext context = new ChaosContext(ChaosType.LATENCY, 1.0, 5000);
            ChaosInjector.activate(context);

            long startTime = System.currentTimeMillis();
            String result = DependencyFailureSimulator.withPolicyEngineFailure(() -> "result");
            long elapsed = System.currentTimeMillis() - startTime;

            assertThat(result).isEqualTo("result");
            assertThat(elapsed).isGreaterThanOrEqualTo(1000);
        }
    }

    @Nested
    @DisplayName("AI Completion Failure Simulation")
    class AICompletionFailureTests {

        @Test
        @DisplayName("should inject AI completion unavailable error")
        void shouldInjectAICompletionUnavailable() {
            ChaosContext context = new ChaosContext(ChaosType.SERVICE_UNAVAILABLE, 1.0, 5000);
            ChaosInjector.activate(context);

            assertThatThrownBy(() -> DependencyFailureSimulator.withAICompletionFailure(() -> "result"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("P1-2")
                .hasMessageContaining("AI completion unavailable");
        }

        @Test
        @DisplayName("should inject AI completion timeout")
        void shouldInjectAICompletionTimeout() {
            ChaosContext context = new ChaosContext(ChaosType.NETWORK, 1.0, 5000);
            ChaosInjector.activate(context);

            assertThatThrownBy(() -> DependencyFailureSimulator.withAICompletionFailure(() -> "result"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("P1-2")
                .hasMessageContaining("timeout");
        }

        @Test
        @DisplayName("should inject AI completion rate limit")
        void shouldInjectAICompletionRateLimit() {
            ChaosContext context = new ChaosContext(ChaosType.RESOURCE_EXHAUSTION, 1.0, 5000);
            ChaosInjector.activate(context);

            assertThatThrownBy(() -> DependencyFailureSimulator.withAICompletionFailure(() -> "result"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("P1-2")
                .hasMessageContaining("rate limit exceeded");
        }

        @Test
        @DisplayName("should inject AI completion latency")
        void shouldInjectAICompletionLatency() {
            ChaosContext context = new ChaosContext(ChaosType.LATENCY, 1.0, 5000);
            ChaosInjector.activate(context);

            long startTime = System.currentTimeMillis();
            String result = DependencyFailureSimulator.withAICompletionFailure(() -> "result");
            long elapsed = System.currentTimeMillis() - startTime;

            assertThat(result).isEqualTo("result");
            assertThat(elapsed).isGreaterThanOrEqualTo(1000);
        }
    }

    @Nested
    @DisplayName("Network Timeout Simulation")
    class NetworkTimeoutTests {

        @Test
        @DisplayName("should inject network timeout")
        void shouldInjectNetworkTimeout() throws TimeoutException {
            ChaosContext context = new ChaosContext(ChaosType.NETWORK, 1.0, 5000);
            ChaosInjector.activate(context);

            assertThatThrownBy(() -> DependencyFailureSimulator.withNetworkTimeout(() -> "result"))
                .isInstanceOf(TimeoutException.class)
                .hasMessageContaining("P1-2")
                .hasMessageContaining("Network timeout");
        }

        @Test
        @DisplayName("should inject latency timeout")
        void shouldInjectLatencyTimeout() throws TimeoutException {
            ChaosContext context = new ChaosContext(ChaosType.LATENCY, 1.0, 5000);
            ChaosInjector.activate(context);

            assertThatThrownBy(() -> DependencyFailureSimulator.withNetworkTimeout(() -> "result"))
                .isInstanceOf(TimeoutException.class)
                .hasMessageContaining("P1-2")
                .hasMessageContaining("Network timeout");
        }
    }

    @Nested
    @DisplayName("Queue Saturation Simulation")
    class QueueSaturationTests {

        @Test
        @DisplayName("should inject queue saturation")
        void shouldInjectQueueSaturation() {
            ChaosContext context = new ChaosContext(ChaosType.RESOURCE_EXHAUSTION, 1.0, 5000);
            ChaosInjector.activate(context);

            assertThatThrownBy(() -> DependencyFailureSimulator.withQueueSaturation(() -> "result"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("P1-2")
                .hasMessageContaining("Queue saturated");
        }

        @Test
        @DisplayName("should inject queue full")
        void shouldInjectQueueFull() {
            ChaosContext context = new ChaosContext(ChaosType.PARTIAL_FAILURE, 1.0, 5000);
            ChaosInjector.activate(context);

            assertThatThrownBy(() -> DependencyFailureSimulator.withQueueSaturation(() -> "result"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("P1-2")
                .hasMessageContaining("Queue full");
        }
    }

    @Nested
    @DisplayName("Probability-Based Failure Injection")
    class ProbabilityTests {

        @Test
        @DisplayName("should respect failure probability 0.0")
        void shouldRespectProbabilityZero() throws SQLException {
            ChaosContext context = new ChaosContext(ChaosType.SERVICE_UNAVAILABLE, 0.0, 5000);
            ChaosInjector.activate(context);

            for (int i = 0; i < 100; i++) {
                String result = DependencyFailureSimulator.withPostgresFailure(() -> "success");
                assertThat(result).isEqualTo("success");
            }
        }

        @Test
        @DisplayName("should respect failure probability 1.0")
        void shouldRespectProbabilityOne() {
            ChaosContext context = new ChaosContext(ChaosType.SERVICE_UNAVAILABLE, 1.0, 5000);
            ChaosInjector.activate(context);

            for (int i = 0; i < 10; i++) {
                assertThatThrownBy(() -> DependencyFailureSimulator.withPostgresFailure(() -> "success"))
                    .isInstanceOf(SQLException.class);
            }
        }

        @Test
        @DisplayName("should inject failures at specified probability")
        void shouldInjectAtSpecifiedProbability() {
            ChaosContext context = new ChaosContext(ChaosType.SERVICE_UNAVAILABLE, 0.5, 5000);
            ChaosInjector.activate(context);

            int failures = 0;
            int attempts = 100;
            for (int i = 0; i < attempts; i++) {
                try {
                    DependencyFailureSimulator.withPostgresFailure(() -> "success");
                } catch (SQLException e) {
                    failures++;
                }
            }

            // Should be around 50% with some variance
            assertThat(failures).isGreaterThan(30).isLessThan(70);
        }
    }
}
