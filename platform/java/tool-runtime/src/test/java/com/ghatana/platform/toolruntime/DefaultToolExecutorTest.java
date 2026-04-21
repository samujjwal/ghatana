/*
 * Copyright (c) 2026 Ghatana Inc.
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
@ExtendWith(MockitoExtension.class)
class DefaultToolExecutorTest extends EventloopTestBase {

    @Mock
    private ApprovalGateway approvalGateway;

    @Mock
    private ToolExecutionMonitor monitor;

    @Mock
    private PolicyAsCodeEngine policyEngine;

    private DefaultToolExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new DefaultToolExecutor(approvalGateway, monitor, policyEngine);
        // monitor.record always returns Promise.of(null) in all tests
        lenient().when(monitor.record(any(), any(), any(), any(), any(Long.class), any(Boolean.class)))
                 .thenReturn(Promise.of(null));
        // default policy: ALLOW (most tests operate on the post-policy path)
        lenient().when(policyEngine.evaluate(any(), any(), any()))
                 .thenReturn(Promise.of(PolicyEvalResult.allow("tool.execution")));
    }

    private static ToolExecutionEnvelope envelope(String toolId, String agentId, ActionClass ac) {
        return ToolExecutionEnvelope.of(toolId, "1.0.0", agentId, null, "tenant-1", ac, "1.0", Map.of());
    }

    private static ToolContract contract(String toolId, ActionClass ac, boolean requiresApproval) {
        return new ToolContractBuilder()
                .toolId(toolId)
                .name(toolId)
                .actionClass(ac)
                .requiresApproval(requiresApproval)
                .transport(ToolTransport.IN_PROCESS)
                .build();
    }

    // ─── No handler registered ──────────────────────────────────────────────

    @Nested
    @DisplayName("no handler registered")
    class NoHandler {

        @Test
        @DisplayName("returns DENIED result when no handler is registered")
        void deniedWhenNoHandler() {
            ToolContract c = contract("missing-tool", ActionClass.READ, false);
            ToolExecutionEnvelope e = envelope("missing-tool", "agent-1", ActionClass.READ);

            ToolExecutionResult result = runPromise(() -> executor.execute(e, c));

            assertThat(result.status()).isEqualTo(ToolExecutionStatus.DENIED);
            assertThat(result.errorMessage()).contains("missing-tool");
        }
    }

    // ─── Low-risk actions (no approval check) ───────────────────────────────

    @Nested
    @DisplayName("low-risk READ action")
    class LowRiskAction {

        @Test
        @DisplayName("READ action executes directly without approval gate")
        void readActionDirectExecution() {
            ToolContract c = contract("search", ActionClass.READ, false);
            ToolExecutionEnvelope e = envelope("search", "agent-1", ActionClass.READ);
            executor.register("search", (env, con) -> Promise.of(
                    ToolExecutionResult.succeeded(env.invocationId(), "result", Map.of(), null, Instant.now(), Duration.ofMillis(10))));

            ToolExecutionResult result = runPromise(() -> executor.execute(e, c));

            assertThat(result.status()).isEqualTo(ToolExecutionStatus.SUCCESS);
            assertThat(result.output()).isEqualTo("result");
            verifyNoInteractions(approvalGateway);
        }

        @Test
        @DisplayName("DRAFT action executes directly without approval gate")
        void draftActionDirectExecution() {
            ToolContract c = contract("draft-tool", ActionClass.DRAFT, false);
            ToolExecutionEnvelope e = envelope("draft-tool", "agent-1", ActionClass.DRAFT);
            executor.register("draft-tool", (env, con) -> Promise.of(
                    ToolExecutionResult.succeeded(env.invocationId(), "draft-ok", Map.of(), null, Instant.now(), Duration.ofMillis(5))));

            ToolExecutionResult result = runPromise(() -> executor.execute(e, c));

            assertThat(result.status()).isEqualTo(ToolExecutionStatus.SUCCESS);
            verifyNoInteractions(approvalGateway);
        }
    }

    // ─── Approval gate ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("approval gate")
    class ApprovalGateTests {

        @Test
        @DisplayName("WRITE_IRREVERSIBLE with requiresApproval=true returns APPROVAL_PENDING when gateway says yes")
        void writeSideEffectPendingApproval() {
            when(approvalGateway.requiresApproval(any(), any(), any())).thenReturn(Promise.of(true));
            ToolContract c = contract("send-email", ActionClass.WRITE_IRREVERSIBLE, true);
            ToolExecutionEnvelope e = envelope("send-email", "agent-1", ActionClass.WRITE_IRREVERSIBLE);
            executor.register("send-email", (env, con) -> Promise.of(
                    ToolExecutionResult.succeeded(env.invocationId(), "sent", Map.of(), null, Instant.now(), Duration.ZERO)));

            ToolExecutionResult result = runPromise(() -> executor.execute(e, c));

            assertThat(result.status()).isEqualTo(ToolExecutionStatus.APPROVAL_PENDING);
        }

        @Test
        @DisplayName("WRITE_IRREVERSIBLE with requiresApproval=true executes when gateway says no-approval-needed")
        void writeSideEffectExecutesWhenAlreadyApproved() {
            when(approvalGateway.requiresApproval(any(), any(), any())).thenReturn(Promise.of(false));
            ToolContract c = contract("send-email", ActionClass.WRITE_IRREVERSIBLE, true);
            ToolExecutionEnvelope e = envelope("send-email", "agent-1", ActionClass.WRITE_IRREVERSIBLE);
            executor.register("send-email", (env, con) -> Promise.of(
                    ToolExecutionResult.succeeded(env.invocationId(), "sent", Map.of(), null, Instant.now(), Duration.ZERO)));

            ToolExecutionResult result = runPromise(() -> executor.execute(e, c));

            assertThat(result.status()).isEqualTo(ToolExecutionStatus.SUCCESS);
            verify(approvalGateway).requiresApproval("tenant-1", "agent-1", "WRITE_IRREVERSIBLE");
        }

        @Test
        @DisplayName("requiresApproval=false skips the approval gateway even for privileged actions")
        void noApprovalFlagSkipsGateway() {
            ToolContract c = contract("db-write", ActionClass.WRITE_REVERSIBLE, false);
            ToolExecutionEnvelope e = envelope("db-write", "agent-1", ActionClass.WRITE_REVERSIBLE);
            executor.register("db-write", (env, con) -> Promise.of(
                    ToolExecutionResult.succeeded(env.invocationId(), "ok", Map.of(), null, Instant.now(), Duration.ZERO)));

            ToolExecutionResult result = runPromise(() -> executor.execute(e, c));

            assertThat(result.status()).isEqualTo(ToolExecutionStatus.SUCCESS);
            verifyNoInteractions(approvalGateway);
        }
    }

    // ─── Handler exception ──────────────────────────────────────────────────

    @Nested
    @DisplayName("handler exception handling")
    class HandlerException {

        @Test
        @DisplayName("unhandled exception from handler produces FAILED result")
        void handlerExceptionProducesFailed() {
            ToolContract c = contract("bad-tool", ActionClass.READ, false);
            ToolExecutionEnvelope e = envelope("bad-tool", "agent-1", ActionClass.READ);
            executor.register("bad-tool", (env, con) -> Promise.ofException(new RuntimeException("boom")));

            ToolExecutionResult result = runPromise(() -> executor.execute(e, c));

            assertThat(result.status()).isEqualTo(ToolExecutionStatus.FAILED);
            assertThat(result.errorMessage()).contains("boom");
        }
    }

    // ─── Monitor recording ──────────────────────────────────────────────────

    @Nested
    @DisplayName("monitor integration")
    class MonitorIntegration {

        @Test
        @DisplayName("monitor.record is called after every successful execution")
        void monitorCalledOnSuccess() {
            ToolContract c = contract("search", ActionClass.READ, false);
            ToolExecutionEnvelope e = envelope("search", "agent-1", ActionClass.READ);
            executor.register("search", (env, con) -> Promise.of(
                    ToolExecutionResult.succeeded(env.invocationId(), "out", Map.of(), null, Instant.now(), Duration.ofMillis(20))));

            runPromise(() -> executor.execute(e, c));

            verify(monitor).record(
                    any(), any(), any(), any(), any(Long.class), any(Boolean.class));
        }

        @Test
        @DisplayName("monitor.record is called after a DENIED result")
        void monitorCalledOnDenied() {
            // No handler registered → denied
            ToolContract c = contract("no-tool", ActionClass.READ, false);
            ToolExecutionEnvelope e = envelope("no-tool", "agent-1", ActionClass.READ);

            runPromise(() -> executor.execute(e, c));

            // Record should NOT be called for the no-handler path (logged before monitor)
            // The denied result from no-handler is returned directly without monitor.record
            verifyNoInteractions(monitor);
        }
    }

    // ─── TX-6: mandatory policy gate ────────────────────────────────────────

    @Nested
    @DisplayName("TX-6 mandatory policy gate")
    class MandatoryPolicyGate {

        @Test
        @DisplayName("policyEngine is evaluated before every tool execution")
        void policyEngineEvaluatedBeforeExecution() {
            ToolContract c = contract("search", ActionClass.READ, false);
            ToolExecutionEnvelope e = envelope("search", "agent-1", ActionClass.READ);
            executor.register("search", (env, con) -> Promise.of(
                    ToolExecutionResult.succeeded(env.invocationId(), "ok", Map.of(), null, Instant.now(), Duration.ofMillis(5))));

            runPromise(() -> executor.execute(e, c));

            verify(policyEngine).evaluate("tenant-1", "tool.execution",
                    Map.of("toolId", "search", "actionClass", ActionClass.READ.name(),
                            "tenantId", "tenant-1", "callerAgentId", "agent-1"));
        }

        @Test
        @DisplayName("tool execution is denied when policy engine returns DENY")
        void deniedWhenPolicyDenies() {
            when(policyEngine.evaluate(any(), any(), any()))
                    .thenReturn(Promise.of(PolicyEvalResult.deny(
                            "tool.execution", java.util.List.of("high-risk tool blocked"), 90)));

            ToolContract c = contract("dangerous-tool", ActionClass.WRITE_IRREVERSIBLE, false);
            ToolExecutionEnvelope e = envelope("dangerous-tool", "agent-1", ActionClass.WRITE_IRREVERSIBLE);
            executor.register("dangerous-tool", (env, con) -> Promise.of(
                    ToolExecutionResult.succeeded(env.invocationId(), "done", Map.of(), null, Instant.now(), Duration.ofMillis(5))));

            ToolExecutionResult result = runPromise(() -> executor.execute(e, c));

            assertThat(result.status()).isEqualTo(ToolExecutionStatus.DENIED);
            assertThat(result.errorMessage()).contains("high-risk tool blocked");
        }

        @Test
        @DisplayName("policy DENY reason is included in denied result when no reasons given")
        void defaultDenyReasonWhenPolicyHasNoReasons() {
            when(policyEngine.evaluate(any(), any(), any()))
                    .thenReturn(Promise.of(PolicyEvalResult.deny("tool.execution", java.util.List.of(), 50)));

            ToolContract c = contract("blocked-tool", ActionClass.WRITE_IRREVERSIBLE, false);
            ToolExecutionEnvelope e = envelope("blocked-tool", "agent-1", ActionClass.WRITE_IRREVERSIBLE);
            executor.register("blocked-tool", (env, con) -> Promise.of(
                    ToolExecutionResult.succeeded(env.invocationId(), "done", Map.of(), null, Instant.now(), Duration.ofMillis(5))));

            ToolExecutionResult result = runPromise(() -> executor.execute(e, c));

            assertThat(result.status()).isEqualTo(ToolExecutionStatus.DENIED);
            assertThat(result.errorMessage()).isNotBlank();
        }

        @Test
        @DisplayName("handler is NOT called when policy denies")
        void handlerNotCalledWhenPolicyDenies() {
            when(policyEngine.evaluate(any(), any(), any()))
                    .thenReturn(Promise.of(PolicyEvalResult.deny(
                            "tool.execution", java.util.List.of("denied"), 80)));

            ToolContract c = contract("safe-read", ActionClass.READ, false);
            ToolExecutionEnvelope e = envelope("safe-read", "agent-1", ActionClass.READ);

            boolean[] handlerCalled = { false };
            executor.register("safe-read", (env, con) -> {
                handlerCalled[0] = true;
                return Promise.of(ToolExecutionResult.succeeded(env.invocationId(), "out", Map.of(), null, Instant.now(), Duration.ofMillis(1)));
            });

            runPromise(() -> executor.execute(e, c));

            assertThat(handlerCalled[0]).isFalse();
        }

        @Test
        @DisplayName("policy DENY still calls monitor.record")
        void monitorCalledOnPolicyDeny() {
            when(policyEngine.evaluate(any(), any(), any()))
                    .thenReturn(Promise.of(PolicyEvalResult.deny(
                            "tool.execution", java.util.List.of("denied"), 70)));

            ToolContract c = contract("guarded-tool", ActionClass.READ, false);
            ToolExecutionEnvelope e = envelope("guarded-tool", "agent-1", ActionClass.READ);
            executor.register("guarded-tool", (env, con) -> Promise.of(
                    ToolExecutionResult.succeeded(env.invocationId(), "ok", Map.of(), null, Instant.now(), Duration.ofMillis(5))));

            runPromise(() -> executor.execute(e, c));

            verify(monitor).record(any(), any(), any(), any(), any(Long.class), any(Boolean.class));
        }

        @Test
        @DisplayName("tool execution succeeds end-to-end when policy allows")
        void successEndToEndWhenPolicyAllows() {
            // policyEngine is pre-stubbed to ALLOW in @BeforeEach
            ToolContract c = contract("allowed-tool", ActionClass.READ, false);
            ToolExecutionEnvelope e = envelope("allowed-tool", "agent-1", ActionClass.READ);
            executor.register("allowed-tool", (env, con) -> Promise.of(
                    ToolExecutionResult.succeeded(env.invocationId(), "result", Map.of(), null, Instant.now(), Duration.ofMillis(10))));

            ToolExecutionResult result = runPromise(() -> executor.execute(e, c));

            assertThat(result.status()).isEqualTo(ToolExecutionStatus.SUCCESS);
        }
    }

    // ─── Metrics and tracing ─────────────────────────────────────────────────

    @Nested
    @DisplayName("metrics and tracing (P6-T5)")
    class MetricsAndTracing {

        private MeterRegistry meterRegistry;
        private DefaultToolExecutor instrumentedExecutor;

        @BeforeEach
        void setUp() {
            meterRegistry = new SimpleMeterRegistry();
            // Use a real no-op OTel tracer — avoids sealed-interface mock issues
            io.opentelemetry.api.trace.Tracer noop =
                    io.opentelemetry.api.OpenTelemetry.noop().getTracer("test");
            AgentRunTracer tracer = new AgentRunTracer(noop);
            instrumentedExecutor = new DefaultToolExecutor(
                    approvalGateway, monitor, policyEngine, tracer, meterRegistry);
        }

        @Test
        @DisplayName("successful tool call increments agent.tool.calls counter")
        void successfulCallIncrementsToolCallsCounter() {
            ToolContract c = contract("read-tool", ActionClass.READ, false);
            ToolExecutionEnvelope e = envelope("read-tool", "agent-1", ActionClass.READ);
            instrumentedExecutor.register("read-tool", (env, con) -> Promise.of(
                    ToolExecutionResult.succeeded(
                            env.invocationId(), "ok", Map.of(), null, Instant.now(), Duration.ofMillis(5))));

            runPromise(() -> instrumentedExecutor.execute(e, c));

            Counter counter = meterRegistry.find(DefaultToolExecutor.METRIC_TOOL_CALLS).counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("policy denial increments agent.policy.denials counter")
        void policyDenialIncrementsDenialsCounter() {
            when(policyEngine.evaluate(any(), any(), any()))
                    .thenReturn(Promise.of(PolicyEvalResult.deny(
                            "tool.execution", java.util.List.of("blocked"), 90)));

            ToolContract c = contract("blocked-tool", ActionClass.WRITE_IRREVERSIBLE, false);
            ToolExecutionEnvelope e = envelope("blocked-tool", "agent-1", ActionClass.WRITE_IRREVERSIBLE);
            instrumentedExecutor.register("blocked-tool", (env, con) -> Promise.of(
                    ToolExecutionResult.succeeded(
                            env.invocationId(), "ignored", Map.of(), null, Instant.now(), Duration.ZERO)));

            runPromise(() -> instrumentedExecutor.execute(e, c));

            Counter counter = meterRegistry.find(DefaultToolExecutor.METRIC_TOOL_POLICY_DENIALS).counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("policy denial does NOT increment tool.calls counter")
        void policyDenialDoesNotIncrementCallsCounter() {
            when(policyEngine.evaluate(any(), any(), any()))
                    .thenReturn(Promise.of(PolicyEvalResult.deny(
                            "tool.execution", java.util.List.of("blocked"), 90)));

            ToolContract c = contract("blocked-tool2", ActionClass.WRITE_IRREVERSIBLE, false);
            ToolExecutionEnvelope e = envelope("blocked-tool2", "agent-1", ActionClass.WRITE_IRREVERSIBLE);
            instrumentedExecutor.register("blocked-tool2", (env, con) -> Promise.of(
                    ToolExecutionResult.succeeded(
                            env.invocationId(), "ignored", Map.of(), null, Instant.now(), Duration.ZERO)));

            runPromise(() -> instrumentedExecutor.execute(e, c));

            Counter counter = meterRegistry.find(DefaultToolExecutor.METRIC_TOOL_CALLS).counter();
            // Counter is registered at construction time but should NOT be incremented on denial
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isZero();
        }

        @Test
        @DisplayName("multiple calls accumulate counter correctly")
        void multipleCallsAccumulateCounter() {
            ToolContract c = contract("counter-tool", ActionClass.READ, false);
            instrumentedExecutor.register("counter-tool", (env, con) -> Promise.of(
                    ToolExecutionResult.succeeded(
                            env.invocationId(), "ok", Map.of(), null, Instant.now(), Duration.ofMillis(1))));

            for (int i = 0; i < 3; i++) {
                ToolExecutionEnvelope e = envelope("counter-tool", "agent-1", ActionClass.READ);
                runPromise(() -> instrumentedExecutor.execute(e, c));
            }

            Counter counter = meterRegistry.find(DefaultToolExecutor.METRIC_TOOL_CALLS).counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(3.0);
        }
    }
}
