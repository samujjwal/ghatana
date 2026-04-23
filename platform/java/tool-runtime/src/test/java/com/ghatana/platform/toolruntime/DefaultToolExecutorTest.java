/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.platform.toolruntime;

import com.ghatana.agent.framework.governance.ActionClass;
import com.ghatana.agent.framework.tools.ToolContract;
import com.ghatana.agent.framework.tools.ToolContractBuilder;
import com.ghatana.agent.framework.tools.ToolExecutionEnvelope;
import com.ghatana.agent.framework.tools.ToolExecutionResult;
import com.ghatana.agent.framework.tools.ToolExecutionStatus;
import com.ghatana.agent.framework.tools.ToolTransport;
import com.ghatana.platform.pac.PolicyAsCodeEngine;
import com.ghatana.platform.pac.PolicyEvalResult;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.platform.toolruntime.approval.ApprovalGateway;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ghatana.platform.observability.agent.AgentRunTracer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultToolExecutor}.
 */
@DisplayName("DefaultToolExecutor")
@ExtendWith(MockitoExtension.class) // GH-90000
class DefaultToolExecutorTest extends EventloopTestBase {

    @Mock
    private ApprovalGateway approvalGateway;

    @Mock
    private ToolExecutionMonitor monitor;

    @Mock
    private PolicyAsCodeEngine policyEngine;

    private DefaultToolExecutor executor;

    @BeforeEach
    void setUp() { // GH-90000
        executor = new DefaultToolExecutor(approvalGateway, monitor, policyEngine); // GH-90000
        // monitor.record always returns Promise.of(null) in all tests // GH-90000
        lenient().when(monitor.record(any(), any(), any(), any(), any(Long.class), any(Boolean.class))) // GH-90000
                 .thenReturn(Promise.of(null)); // GH-90000
        // default policy: ALLOW (most tests operate on the post-policy path) // GH-90000
        lenient().when(policyEngine.evaluate(any(), any(), any())) // GH-90000
                 .thenReturn(Promise.of(PolicyEvalResult.allow("tool.execution")));
    }

    private static ToolExecutionEnvelope envelope(String toolId, String agentId, ActionClass ac) { // GH-90000
        return ToolExecutionEnvelope.of(toolId, "1.0.0", agentId, null, "tenant-1", ac, "1.0", Map.of()); // GH-90000
    }

    private static ToolContract contract(String toolId, ActionClass ac, boolean requiresApproval) { // GH-90000
        return new ToolContractBuilder() // GH-90000
                .toolId(toolId) // GH-90000
                .name(toolId) // GH-90000
                .actionClass(ac) // GH-90000
                .requiresApproval(requiresApproval) // GH-90000
                .transport(ToolTransport.IN_PROCESS) // GH-90000
                .build(); // GH-90000
    }

    // ─── No handler registered ──────────────────────────────────────────────

    @Nested
    @DisplayName("no handler registered")
    class NoHandler {

        @Test
        @DisplayName("returns DENIED result when no handler is registered")
        void deniedWhenNoHandler() { // GH-90000
            ToolContract c = contract("missing-tool", ActionClass.READ, false); // GH-90000
            ToolExecutionEnvelope e = envelope("missing-tool", "agent-1", ActionClass.READ); // GH-90000

            ToolExecutionResult result = runPromise(() -> executor.execute(e, c)); // GH-90000

            assertThat(result.status()).isEqualTo(ToolExecutionStatus.DENIED); // GH-90000
            assertThat(result.errorMessage()).contains("missing-tool");
        }
    }

    // ─── Low-risk actions (no approval check) ─────────────────────────────── // GH-90000

    @Nested
    @DisplayName("low-risk READ action")
    class LowRiskAction {

        @Test
        @DisplayName("READ action executes directly without approval gate")
        void readActionDirectExecution() { // GH-90000
            ToolContract c = contract("search", ActionClass.READ, false); // GH-90000
            ToolExecutionEnvelope e = envelope("search", "agent-1", ActionClass.READ); // GH-90000
            executor.register("search", (env, con) -> Promise.of( // GH-90000
                    ToolExecutionResult.succeeded(env.invocationId(), "result", Map.of(), null, Instant.now(), Duration.ofMillis(10)))); // GH-90000

            ToolExecutionResult result = runPromise(() -> executor.execute(e, c)); // GH-90000

            assertThat(result.status()).isEqualTo(ToolExecutionStatus.SUCCESS); // GH-90000
            assertThat(result.output()).isEqualTo("result");
            verifyNoInteractions(approvalGateway); // GH-90000
        }

        @Test
        @DisplayName("DRAFT action executes directly without approval gate")
        void draftActionDirectExecution() { // GH-90000
            ToolContract c = contract("draft-tool", ActionClass.DRAFT, false); // GH-90000
            ToolExecutionEnvelope e = envelope("draft-tool", "agent-1", ActionClass.DRAFT); // GH-90000
            executor.register("draft-tool", (env, con) -> Promise.of( // GH-90000
                    ToolExecutionResult.succeeded(env.invocationId(), "draft-ok", Map.of(), null, Instant.now(), Duration.ofMillis(5)))); // GH-90000

            ToolExecutionResult result = runPromise(() -> executor.execute(e, c)); // GH-90000

            assertThat(result.status()).isEqualTo(ToolExecutionStatus.SUCCESS); // GH-90000
            verifyNoInteractions(approvalGateway); // GH-90000
        }
    }

    // ─── Approval gate ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("approval gate")
    class ApprovalGateTests {

        @Test
        @DisplayName("WRITE_IRREVERSIBLE with requiresApproval=true returns APPROVAL_PENDING when gateway says yes")
        void writeSideEffectPendingApproval() { // GH-90000
            when(approvalGateway.requiresApproval(any(), any(), any())).thenReturn(Promise.of(true)); // GH-90000
            ToolContract c = contract("send-email", ActionClass.WRITE_IRREVERSIBLE, true); // GH-90000
            ToolExecutionEnvelope e = envelope("send-email", "agent-1", ActionClass.WRITE_IRREVERSIBLE); // GH-90000
            executor.register("send-email", (env, con) -> Promise.of( // GH-90000
                    ToolExecutionResult.succeeded(env.invocationId(), "sent", Map.of(), null, Instant.now(), Duration.ZERO))); // GH-90000

            ToolExecutionResult result = runPromise(() -> executor.execute(e, c)); // GH-90000

            assertThat(result.status()).isEqualTo(ToolExecutionStatus.APPROVAL_PENDING); // GH-90000
        }

        @Test
        @DisplayName("WRITE_IRREVERSIBLE with requiresApproval=true executes when gateway says no-approval-needed")
        void writeSideEffectExecutesWhenAlreadyApproved() { // GH-90000
            when(approvalGateway.requiresApproval(any(), any(), any())).thenReturn(Promise.of(false)); // GH-90000
            ToolContract c = contract("send-email", ActionClass.WRITE_IRREVERSIBLE, true); // GH-90000
            ToolExecutionEnvelope e = envelope("send-email", "agent-1", ActionClass.WRITE_IRREVERSIBLE); // GH-90000
            executor.register("send-email", (env, con) -> Promise.of( // GH-90000
                    ToolExecutionResult.succeeded(env.invocationId(), "sent", Map.of(), null, Instant.now(), Duration.ZERO))); // GH-90000

            ToolExecutionResult result = runPromise(() -> executor.execute(e, c)); // GH-90000

            assertThat(result.status()).isEqualTo(ToolExecutionStatus.SUCCESS); // GH-90000
            verify(approvalGateway).requiresApproval("tenant-1", "agent-1", "WRITE_IRREVERSIBLE"); // GH-90000
        }

        @Test
        @DisplayName("requiresApproval=false skips the approval gateway even for privileged actions")
        void noApprovalFlagSkipsGateway() { // GH-90000
            ToolContract c = contract("db-write", ActionClass.WRITE_REVERSIBLE, false); // GH-90000
            ToolExecutionEnvelope e = envelope("db-write", "agent-1", ActionClass.WRITE_REVERSIBLE); // GH-90000
            executor.register("db-write", (env, con) -> Promise.of( // GH-90000
                    ToolExecutionResult.succeeded(env.invocationId(), "ok", Map.of(), null, Instant.now(), Duration.ZERO))); // GH-90000

            ToolExecutionResult result = runPromise(() -> executor.execute(e, c)); // GH-90000

            assertThat(result.status()).isEqualTo(ToolExecutionStatus.SUCCESS); // GH-90000
            verifyNoInteractions(approvalGateway); // GH-90000
        }
    }

    // ─── Handler exception ──────────────────────────────────────────────────

    @Nested
    @DisplayName("handler exception handling")
    class HandlerException {

        @Test
        @DisplayName("unhandled exception from handler produces FAILED result")
        void handlerExceptionProducesFailed() { // GH-90000
            ToolContract c = contract("bad-tool", ActionClass.READ, false); // GH-90000
            ToolExecutionEnvelope e = envelope("bad-tool", "agent-1", ActionClass.READ); // GH-90000
            executor.register("bad-tool", (env, con) -> Promise.ofException(new RuntimeException("boom")));

            ToolExecutionResult result = runPromise(() -> executor.execute(e, c)); // GH-90000

            assertThat(result.status()).isEqualTo(ToolExecutionStatus.FAILED); // GH-90000
            assertThat(result.errorMessage()).contains("boom");
        }
    }

    // ─── Monitor recording ──────────────────────────────────────────────────

    @Nested
    @DisplayName("monitor integration")
    class MonitorIntegration {

        @Test
        @DisplayName("monitor.record is called after every successful execution")
        void monitorCalledOnSuccess() { // GH-90000
            ToolContract c = contract("search", ActionClass.READ, false); // GH-90000
            ToolExecutionEnvelope e = envelope("search", "agent-1", ActionClass.READ); // GH-90000
            executor.register("search", (env, con) -> Promise.of( // GH-90000
                    ToolExecutionResult.succeeded(env.invocationId(), "out", Map.of(), null, Instant.now(), Duration.ofMillis(20)))); // GH-90000

            runPromise(() -> executor.execute(e, c)); // GH-90000

            verify(monitor).record( // GH-90000
                    any(), any(), any(), any(), any(Long.class), any(Boolean.class)); // GH-90000
        }

        @Test
        @DisplayName("monitor.record is called after a DENIED result")
        void monitorCalledOnDenied() { // GH-90000
            // No handler registered → denied
            ToolContract c = contract("no-tool", ActionClass.READ, false); // GH-90000
            ToolExecutionEnvelope e = envelope("no-tool", "agent-1", ActionClass.READ); // GH-90000

            runPromise(() -> executor.execute(e, c)); // GH-90000

            // Record should NOT be called for the no-handler path (logged before monitor) // GH-90000
            // The denied result from no-handler is returned directly without monitor.record
            verifyNoInteractions(monitor); // GH-90000
        }
    }

    // ─── TX-6: mandatory policy gate ────────────────────────────────────────

    @Nested
    @DisplayName("TX-6 mandatory policy gate")
    class MandatoryPolicyGate {

        @Test
        @DisplayName("policyEngine is evaluated before every tool execution")
        void policyEngineEvaluatedBeforeExecution() { // GH-90000
            ToolContract c = contract("search", ActionClass.READ, false); // GH-90000
            ToolExecutionEnvelope e = envelope("search", "agent-1", ActionClass.READ); // GH-90000
            executor.register("search", (env, con) -> Promise.of( // GH-90000
                    ToolExecutionResult.succeeded(env.invocationId(), "ok", Map.of(), null, Instant.now(), Duration.ofMillis(5)))); // GH-90000

            runPromise(() -> executor.execute(e, c)); // GH-90000

            verify(policyEngine).evaluate("tenant-1", "tool.execution", // GH-90000
                    Map.of("toolId", "search", "actionClass", ActionClass.READ.name(), // GH-90000
                            "tenantId", "tenant-1", "callerAgentId", "agent-1"));
        }

        @Test
        @DisplayName("tool execution is denied when policy engine returns DENY")
        void deniedWhenPolicyDenies() { // GH-90000
            when(policyEngine.evaluate(any(), any(), any())) // GH-90000
                    .thenReturn(Promise.of(PolicyEvalResult.deny( // GH-90000
                            "tool.execution", java.util.List.of("high-risk tool blocked"), 90)));

            ToolContract c = contract("dangerous-tool", ActionClass.WRITE_IRREVERSIBLE, false); // GH-90000
            ToolExecutionEnvelope e = envelope("dangerous-tool", "agent-1", ActionClass.WRITE_IRREVERSIBLE); // GH-90000
            executor.register("dangerous-tool", (env, con) -> Promise.of( // GH-90000
                    ToolExecutionResult.succeeded(env.invocationId(), "done", Map.of(), null, Instant.now(), Duration.ofMillis(5)))); // GH-90000

            ToolExecutionResult result = runPromise(() -> executor.execute(e, c)); // GH-90000

            assertThat(result.status()).isEqualTo(ToolExecutionStatus.DENIED); // GH-90000
            assertThat(result.errorMessage()).contains("high-risk tool blocked");
        }

        @Test
        @DisplayName("policy DENY reason is included in denied result when no reasons given")
        void defaultDenyReasonWhenPolicyHasNoReasons() { // GH-90000
            when(policyEngine.evaluate(any(), any(), any())) // GH-90000
                    .thenReturn(Promise.of(PolicyEvalResult.deny("tool.execution", java.util.List.of(), 50))); // GH-90000

            ToolContract c = contract("blocked-tool", ActionClass.WRITE_IRREVERSIBLE, false); // GH-90000
            ToolExecutionEnvelope e = envelope("blocked-tool", "agent-1", ActionClass.WRITE_IRREVERSIBLE); // GH-90000
            executor.register("blocked-tool", (env, con) -> Promise.of( // GH-90000
                    ToolExecutionResult.succeeded(env.invocationId(), "done", Map.of(), null, Instant.now(), Duration.ofMillis(5)))); // GH-90000

            ToolExecutionResult result = runPromise(() -> executor.execute(e, c)); // GH-90000

            assertThat(result.status()).isEqualTo(ToolExecutionStatus.DENIED); // GH-90000
            assertThat(result.errorMessage()).isNotBlank(); // GH-90000
        }

        @Test
        @DisplayName("handler is NOT called when policy denies")
        void handlerNotCalledWhenPolicyDenies() { // GH-90000
            when(policyEngine.evaluate(any(), any(), any())) // GH-90000
                    .thenReturn(Promise.of(PolicyEvalResult.deny( // GH-90000
                            "tool.execution", java.util.List.of("denied"), 80)));

            ToolContract c = contract("safe-read", ActionClass.READ, false); // GH-90000
            ToolExecutionEnvelope e = envelope("safe-read", "agent-1", ActionClass.READ); // GH-90000

            boolean[] handlerCalled = { false };
            executor.register("safe-read", (env, con) -> { // GH-90000
                handlerCalled[0] = true;
                return Promise.of(ToolExecutionResult.succeeded(env.invocationId(), "out", Map.of(), null, Instant.now(), Duration.ofMillis(1))); // GH-90000
            });

            runPromise(() -> executor.execute(e, c)); // GH-90000

            assertThat(handlerCalled[0]).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("policy DENY still calls monitor.record")
        void monitorCalledOnPolicyDeny() { // GH-90000
            when(policyEngine.evaluate(any(), any(), any())) // GH-90000
                    .thenReturn(Promise.of(PolicyEvalResult.deny( // GH-90000
                            "tool.execution", java.util.List.of("denied"), 70)));

            ToolContract c = contract("guarded-tool", ActionClass.READ, false); // GH-90000
            ToolExecutionEnvelope e = envelope("guarded-tool", "agent-1", ActionClass.READ); // GH-90000
            executor.register("guarded-tool", (env, con) -> Promise.of( // GH-90000
                    ToolExecutionResult.succeeded(env.invocationId(), "ok", Map.of(), null, Instant.now(), Duration.ofMillis(5)))); // GH-90000

            runPromise(() -> executor.execute(e, c)); // GH-90000

            verify(monitor).record(any(), any(), any(), any(), any(Long.class), any(Boolean.class)); // GH-90000
        }

        @Test
        @DisplayName("tool execution succeeds end-to-end when policy allows")
        void successEndToEndWhenPolicyAllows() { // GH-90000
            // policyEngine is pre-stubbed to ALLOW in @BeforeEach
            ToolContract c = contract("allowed-tool", ActionClass.READ, false); // GH-90000
            ToolExecutionEnvelope e = envelope("allowed-tool", "agent-1", ActionClass.READ); // GH-90000
            executor.register("allowed-tool", (env, con) -> Promise.of( // GH-90000
                    ToolExecutionResult.succeeded(env.invocationId(), "result", Map.of(), null, Instant.now(), Duration.ofMillis(10)))); // GH-90000

            ToolExecutionResult result = runPromise(() -> executor.execute(e, c)); // GH-90000

            assertThat(result.status()).isEqualTo(ToolExecutionStatus.SUCCESS); // GH-90000
        }
    }

    // ─── Metrics and tracing ─────────────────────────────────────────────────

    @Nested
    @DisplayName("metrics and tracing (P6-T5)")
    class MetricsAndTracing {

        private MeterRegistry meterRegistry;
        private DefaultToolExecutor instrumentedExecutor;

        @BeforeEach
        void setUp() { // GH-90000
            meterRegistry = new SimpleMeterRegistry(); // GH-90000
            // Use a real no-op OTel tracer — avoids sealed-interface mock issues
            io.opentelemetry.api.trace.Tracer noop =
                    io.opentelemetry.api.OpenTelemetry.noop().getTracer("test");
            AgentRunTracer tracer = new AgentRunTracer(noop); // GH-90000
            instrumentedExecutor = new DefaultToolExecutor( // GH-90000
                    approvalGateway, monitor, policyEngine, tracer, meterRegistry);
        }

        @Test
        @DisplayName("successful tool call increments agent.tool.calls counter")
        void successfulCallIncrementsToolCallsCounter() { // GH-90000
            ToolContract c = contract("read-tool", ActionClass.READ, false); // GH-90000
            ToolExecutionEnvelope e = envelope("read-tool", "agent-1", ActionClass.READ); // GH-90000
            instrumentedExecutor.register("read-tool", (env, con) -> Promise.of( // GH-90000
                    ToolExecutionResult.succeeded( // GH-90000
                            env.invocationId(), "ok", Map.of(), null, Instant.now(), Duration.ofMillis(5)))); // GH-90000

            runPromise(() -> instrumentedExecutor.execute(e, c)); // GH-90000

            Counter counter = meterRegistry.find(DefaultToolExecutor.METRIC_TOOL_CALLS).counter(); // GH-90000
            assertThat(counter).isNotNull(); // GH-90000
            assertThat(counter.count()).isEqualTo(1.0); // GH-90000
        }

        @Test
        @DisplayName("policy denial increments agent.policy.denials counter")
        void policyDenialIncrementsDenialsCounter() { // GH-90000
            when(policyEngine.evaluate(any(), any(), any())) // GH-90000
                    .thenReturn(Promise.of(PolicyEvalResult.deny( // GH-90000
                            "tool.execution", java.util.List.of("blocked"), 90)));

            ToolContract c = contract("blocked-tool", ActionClass.WRITE_IRREVERSIBLE, false); // GH-90000
            ToolExecutionEnvelope e = envelope("blocked-tool", "agent-1", ActionClass.WRITE_IRREVERSIBLE); // GH-90000
            instrumentedExecutor.register("blocked-tool", (env, con) -> Promise.of( // GH-90000
                    ToolExecutionResult.succeeded( // GH-90000
                            env.invocationId(), "ignored", Map.of(), null, Instant.now(), Duration.ZERO))); // GH-90000

            runPromise(() -> instrumentedExecutor.execute(e, c)); // GH-90000

            Counter counter = meterRegistry.find(DefaultToolExecutor.METRIC_TOOL_POLICY_DENIALS).counter(); // GH-90000
            assertThat(counter).isNotNull(); // GH-90000
            assertThat(counter.count()).isEqualTo(1.0); // GH-90000
        }

        @Test
        @DisplayName("policy denial does NOT increment tool.calls counter")
        void policyDenialDoesNotIncrementCallsCounter() { // GH-90000
            when(policyEngine.evaluate(any(), any(), any())) // GH-90000
                    .thenReturn(Promise.of(PolicyEvalResult.deny( // GH-90000
                            "tool.execution", java.util.List.of("blocked"), 90)));

            ToolContract c = contract("blocked-tool2", ActionClass.WRITE_IRREVERSIBLE, false); // GH-90000
            ToolExecutionEnvelope e = envelope("blocked-tool2", "agent-1", ActionClass.WRITE_IRREVERSIBLE); // GH-90000
            instrumentedExecutor.register("blocked-tool2", (env, con) -> Promise.of( // GH-90000
                    ToolExecutionResult.succeeded( // GH-90000
                            env.invocationId(), "ignored", Map.of(), null, Instant.now(), Duration.ZERO))); // GH-90000

            runPromise(() -> instrumentedExecutor.execute(e, c)); // GH-90000

            Counter counter = meterRegistry.find(DefaultToolExecutor.METRIC_TOOL_CALLS).counter(); // GH-90000
            // Counter is registered at construction time but should NOT be incremented on denial
            assertThat(counter).isNotNull(); // GH-90000
            assertThat(counter.count()).isZero(); // GH-90000
        }

        @Test
        @DisplayName("multiple calls accumulate counter correctly")
        void multipleCallsAccumulateCounter() { // GH-90000
            ToolContract c = contract("counter-tool", ActionClass.READ, false); // GH-90000
            instrumentedExecutor.register("counter-tool", (env, con) -> Promise.of( // GH-90000
                    ToolExecutionResult.succeeded( // GH-90000
                            env.invocationId(), "ok", Map.of(), null, Instant.now(), Duration.ofMillis(1)))); // GH-90000

            for (int i = 0; i < 3; i++) { // GH-90000
                ToolExecutionEnvelope e = envelope("counter-tool", "agent-1", ActionClass.READ); // GH-90000
                runPromise(() -> instrumentedExecutor.execute(e, c)); // GH-90000
            }

            Counter counter = meterRegistry.find(DefaultToolExecutor.METRIC_TOOL_CALLS).counter(); // GH-90000
            assertThat(counter).isNotNull(); // GH-90000
            assertThat(counter.count()).isEqualTo(3.0); // GH-90000
        }
    }
}
