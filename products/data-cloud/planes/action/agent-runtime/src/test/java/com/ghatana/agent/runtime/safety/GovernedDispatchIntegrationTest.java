/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
/**
 * @doc.type class
 * @doc.purpose Integration tests for real governed-dispatch flow combining GovernedAgentDispatcher and AgentEventOperatorCapabilityAdapter
 * @doc.layer agent-runtime
 * @doc.pattern Integration Test
 */
package com.ghatana.agent.runtime.safety;

import com.ghatana.agent.AgentConfig;
import com.ghatana.agent.AgentDescriptor;
import com.ghatana.agent.AgentResult;
import com.ghatana.agent.AgentResultStatus;
import com.ghatana.agent.AgentType;
import com.ghatana.agent.TypedAgent;
import com.ghatana.agent.audit.HashChainedTraceAppender;
import com.ghatana.agent.dispatch.AgentDispatcher;
import com.ghatana.agent.dispatch.ExecutionTier;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.agent.mastery.MasteryDecision;
import com.ghatana.agent.mastery.MasteryRegistry;
import com.ghatana.agent.mastery.MasteryScore;
import com.ghatana.agent.mastery.MasteryState;
import com.ghatana.agent.mastery.VersionScope;
import com.ghatana.agent.release.AgentRelease;
import com.ghatana.agent.release.AgentReleaseBuilder;
import com.ghatana.agent.release.AgentReleaseRepository;
import com.ghatana.agent.release.AgentReleaseState;
import com.ghatana.agent.release.InMemoryAgentReleaseRepository;
import com.ghatana.agent.registry.AgentEventOperatorCapabilityAdapter;
import com.ghatana.agent.runtime.mode.ExecutionStrategy;
import com.ghatana.agent.runtime.mode.MasteryAwareModeSelector;
import com.ghatana.agent.runtime.mode.ModeSelectionResult;
import com.ghatana.agent.runtime.mode.TaskClassification;
import com.ghatana.agent.runtime.mode.TaskNovelty;
import com.ghatana.agent.runtime.mode.TaskRiskLevel;
import com.ghatana.aep.model.EventContext;
import com.ghatana.aep.model.EventTimeContext;
import com.ghatana.aep.model.ReplayContext;
import com.ghatana.aep.model.UncertaintyContext;
import com.ghatana.aep.operator.contract.EventOperatorResult;
import com.ghatana.aep.operator.contract.OperatorRuntimeContext;
import com.ghatana.core.operator.agent.AgentCapabilityRole;
import com.ghatana.core.operator.agent.AgentSideEffectProfile;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Integration tests for the real governed-dispatch flow combining:
 * - GovernedAgentDispatcher (release guard, invariant checks, trace ledger)
 * - AgentEventOperatorCapabilityAdapter (governance metadata, policy enforcement)
 * - Real agent execution through the full stack
 *
 * These tests verify that the governed dispatch path works end-to-end with
 * proper policy enforcement, trace emission, and release awareness.
 */
@DisplayName("Governed Dispatch Integration Tests")
class GovernedDispatchIntegrationTest extends EventloopTestBase {

    private HashChainedTraceAppender traceLedger;
    private InMemoryAgentReleaseRepository releaseRepository;
    private MasteryAwareModeSelector modeSelector;
    private DefaultInvariantMonitor invariantMonitor;
    private MemoryStore memoryStore;

    @BeforeEach
    void setUp() {
        traceLedger = new HashChainedTraceAppender();
        releaseRepository = new InMemoryAgentReleaseRepository();
        modeSelector = mock(MasteryAwareModeSelector.class);
        invariantMonitor = new DefaultInvariantMonitor();
        memoryStore = mock(MemoryStore.class);

        // Default mode selector stub
        ModeSelectionResult defaultResult = ModeSelectionResult.autonomous(
            ExecutionStrategy.DETERMINISTIC_EXECUTION, "Default mode");
        MasteryDecision defaultDecision = MasteryDecision.allow(
            "default-item", "default-skill", MasteryState.PRACTICED,
            MasteryScore.correctnessOnly(0.5), VersionScope.empty(),
            "Default decision");
        MasteryAwareModeSelector.EnrichedModeSelectionResult enrichedDefaultResult =
            new MasteryAwareModeSelector.EnrichedModeSelectionResult(
                defaultResult.strategy(),
                defaultResult.supervision(),
                defaultResult.reasoning(),
                Map.of(),
                defaultDecision,
                TaskClassification.of(TaskRiskLevel.LOW, TaskNovelty.FAMILIAR),
                com.ghatana.agent.context.version.VersionContext.empty()
            );
        when(modeSelector.selectMode(any(), any(), any()))
            .thenReturn(Promise.of(enrichedDefaultResult));
    }

    @Nested
    @DisplayName("Governed dispatch security scenarios")
    class GovernedDispatchSecurityScenarios {

        @Test
        @DisplayName("governed dispatch rejects agent without release record")
        void governedDispatch_rejectsAgentWithoutReleaseRecord() {
            // Given - no release record exists
            TestAgent agent = new TestAgent("orphan-agent");
            AgentDispatcher delegate = (agentId, input, ctx) ->
                Promise.of(AgentResult.<Map<String, Object>>builder()
                    .status(AgentResultStatus.SUCCESS)
                    .agentId(agentId)
                    .confidence(0.85)
                    .processingTime(Duration.ofMillis(10))
                    .output(Map.of("result", "success"))
                    .build());

            GovernedAgentDispatcher dispatcher = new GovernedAgentDispatcher(
                delegate, invariantMonitor, traceLedger, modeSelector, releaseRepository);

            // When - dispatch without release
            AgentContext ctx = AgentContext.builder()
                .turnId("turn-1")
                .agentId("orphan-agent")
                .tenantId("tenant-x")
                .memoryStore(memoryStore)
                .build();

            AgentResult<?> result = runPromise(() -> dispatcher.dispatch("orphan-agent", "input", ctx));

            // Then - dispatch is denied
            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.DENIED);
            assertThat(result.getExplanation()).contains("release");
        }

        @Test
        @DisplayName("governed dispatch rejects expired release")
        void governedDispatch_rejectsExpiredRelease() {
            // Given - create expired release
            AgentRelease expiredRelease = new AgentReleaseBuilder()
                .agentId("test-agent")
                .tenantId("tenant-x")
                .releaseVersion("1.0.0")
                .state(AgentReleaseState.EXPIRED)
                .redactionProfileId("rp-test")
                .threatModelId("tm-test")
                .evaluationPackId("ep-test")
                .memoryContractId("mc-test")
                .addPermittedPurpose("agent.inference")
                .capabilityMaturityProfile("L1")
                .build();
            runPromise(() -> releaseRepository.save(expiredRelease));

            TestAgent agent = new TestAgent("test-agent");
            AgentDispatcher delegate = (agentId, input, ctx) ->
                Promise.of(AgentResult.<Map<String, Object>>builder()
                    .status(AgentResultStatus.SUCCESS)
                    .agentId(agentId)
                    .confidence(0.85)
                    .processingTime(Duration.ofMillis(10))
                    .output(Map.of("result", "success"))
                    .build());

            GovernedAgentDispatcher dispatcher = new GovernedAgentDispatcher(
                delegate, invariantMonitor, traceLedger, modeSelector, releaseRepository);

            AgentContext ctx = AgentContext.builder()
                .turnId("turn-1")
                .agentId("test-agent")
                .tenantId("tenant-x")
                .memoryStore(memoryStore)
                .build();

            AgentResult<?> result = runPromise(() -> dispatcher.dispatch("test-agent", "input", ctx));

            // Then - dispatch is denied
            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.DENIED);
            assertThat(result.getExplanation()).contains("EXPIRED");
        }

        @Test
        @DisplayName("governed dispatch enforces permitted purposes")
        void governedDispatch_enforcesPermittedPurposes() {
            // Given - release with restricted purposes
            AgentRelease restrictedRelease = new AgentReleaseBuilder()
                .agentId("test-agent")
                .tenantId("tenant-x")
                .releaseVersion("1.0.0")
                .state(AgentReleaseState.ACTIVE)
                .redactionProfileId("rp-test")
                .threatModelId("tm-test")
                .evaluationPackId("ep-test")
                .memoryContractId("mc-test")
                .addPermittedPurpose("agent.inference")
                .capabilityMaturityProfile("L1")
                .build();
            runPromise(() -> releaseRepository.save(restrictedRelease));

            // When - attempt dispatch with disallowed purpose
            AgentContext ctx = AgentContext.builder()
                .turnId("turn-1")
                .agentId("test-agent")
                .tenantId("tenant-x")
                .memoryStore(memoryStore)
                .purpose("agent.action") // Not in permitted purposes
                .build();

            TestAgent agent = new TestAgent("test-agent");
            AgentDispatcher delegate = (agentId, input, ctx) ->
                Promise.of(AgentResult.<Map<String, Object>>builder()
                    .status(AgentResultStatus.SUCCESS)
                    .agentId(agentId)
                    .confidence(0.85)
                    .processingTime(Duration.ofMillis(10))
                    .output(Map.of("result", "success"))
                    .build());

            GovernedAgentDispatcher dispatcher = new GovernedAgentDispatcher(
                delegate, invariantMonitor, traceLedger, modeSelector, releaseRepository);

            AgentResult<?> result = runPromise(() -> dispatcher.dispatch("test-agent", "input", ctx));

            // Then - dispatch is denied due to purpose violation
            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.DENIED);
            assertThat(result.getExplanation()).contains("purpose");
        }

        @Test
        @DisplayName("governed dispatch enforces capability maturity profile")
        void governedDispatch_enforcesCapabilityMaturityProfile() {
            // Given - release with L1 maturity (low maturity)
            AgentRelease lowMaturityRelease = new AgentReleaseBuilder()
                .agentId("test-agent")
                .tenantId("tenant-x")
                .releaseVersion("1.0.0")
                .state(AgentReleaseState.ACTIVE)
                .redactionProfileId("rp-test")
                .threatModelId("tm-test")
                .evaluationPackId("ep-test")
                .memoryContractId("mc-test")
                .addPermittedPurpose("agent.inference")
                .capabilityMaturityProfile("L1")
                .build();
            runPromise(() -> releaseRepository.save(lowMaturityRelease));

            // When - attempt high-risk operation
            AgentContext ctx = AgentContext.builder()
                .turnId("turn-1")
                .agentId("test-agent")
                .tenantId("tenant-x")
                .memoryStore(memoryStore)
                .riskLevel("HIGH")
                .build();

            TestAgent agent = new TestAgent("test-agent");
            AgentDispatcher delegate = (agentId, input, ctx) ->
                Promise.of(AgentResult.<Map<String, Object>>builder()
                    .status(AgentResultStatus.SUCCESS)
                    .agentId(agentId)
                    .confidence(0.85)
                    .processingTime(Duration.ofMillis(10))
                    .output(Map.of("result", "success"))
                    .build());

            GovernedAgentDispatcher dispatcher = new GovernedAgentDispatcher(
                delegate, invariantMonitor, traceLedger, modeSelector, releaseRepository);

            AgentResult<?> result = runPromise(() -> dispatcher.dispatch("test-agent", "input", ctx));

            // Then - dispatch is denied due to maturity mismatch
            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.DENIED);
            assertThat(result.getExplanation()).contains("maturity");
        }
    }

    @Nested
    @DisplayName("Full governed dispatch flow")
    class FullGovernedDispatchFlow {

        @Test
        @DisplayName("active release with governed adapter allows dispatch with full trace")
        void activeReleaseWithGovernedAdapter_allowsDispatchWithFullTrace() {
            // Given - create an active release
            AgentRelease activeRelease = new AgentReleaseBuilder()
                .agentId("test-agent")
                .tenantId("tenant-x")
                .releaseVersion("1.0.0")
                .state(AgentReleaseState.ACTIVE)
                .redactionProfileId("rp-test")
                .threatModelId("tm-test")
                .evaluationPackId("ep-test")
                .memoryContractId("mc-test")
                .addPermittedPurpose("agent.inference")
                .capabilityMaturityProfile("L1")
                .policyPackId("pp-123")
                .masteryPolicyPackId("mastery-pack-1")
                .learningContractId("learning-contract-1")
                .build();
            runPromise(() -> releaseRepository.save(activeRelease));

            // Given - create governed agent dispatcher
            TestAgent agent = new TestAgent("test-agent");
            AgentDispatcher delegate = (agentId, input, ctx) ->
                Promise.of(AgentResult.<Map<String, Object>>builder()
                    .status(AgentResultStatus.SUCCESS)
                    .agentId(agentId)
                    .confidence(0.85)
                    .processingTime(Duration.ofMillis(10))
                    .output(Map.of("result", "success"))
                    .build());

            GovernedAgentDispatcher dispatcher = new GovernedAgentDispatcher(
                delegate, invariantMonitor, traceLedger, modeSelector, releaseRepository);

            // Given - create governed adapter
            AgentEventOperatorCapabilityAdapter adapter = new AgentEventOperatorCapabilityAdapter(
                agent,
                "agents/test-agent@1.0.0",
                AgentCapabilityRole.AGENT_REVIEW,
                "schema://agent/input",
                "schema://agent/output",
                AgentSideEffectProfile.PROPOSE_ACTION,
                policy("model"),
                policy("tool"),
                policy("memory"),
                policy("retrieval"),
                policy("guardrail"),
                policy("replay"),
                policy("uncertainty"),
                policy("human-review"),
                policy("observability"),
                memoryStore);

            // When - dispatch through governed path
            AgentContext ctx = AgentContext.builder()
                .turnId("turn-1")
                .agentId("test-agent")
                .tenantId("tenant-x")
                .memoryStore(memoryStore)
                .build();

            AgentResult<?> result = runPromise(() -> dispatcher.dispatch("test-agent", "input", ctx));

            // Then - dispatch succeeds
            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.SUCCESS);

            // Then - trace ledger contains governance events
            List<com.ghatana.agent.audit.TraceEvent> events = runPromise(() ->
                traceLedger.getByAgent("test-agent", "tenant-x", null, null, 100));
            assertThat(events).isNotEmpty();
        }

        @Test
        @DisplayName("blocked release with governed adapter denies dispatch with trace")
        void blockedReleaseWithGovernedAdapter_deniesDispatchWithTrace() {
            // Given - create a blocked release
            AgentRelease blockedRelease = new AgentReleaseBuilder()
                .agentId("test-agent")
                .tenantId("tenant-x")
                .releaseVersion("1.0.0")
                .state(AgentReleaseState.BLOCKED)
                .redactionProfileId("rp-test")
                .threatModelId("tm-test")
                .evaluationPackId("ep-test")
                .memoryContractId("mc-test")
                .addPermittedPurpose("agent.inference")
                .capabilityMaturityProfile("L1")
                .build();
            runPromise(() -> releaseRepository.save(blockedRelease));

            // Given - create governed agent dispatcher
            TestAgent agent = new TestAgent("test-agent");
            AgentDispatcher delegate = (agentId, input, ctx) ->
                Promise.of(AgentResult.<Map<String, Object>>builder()
                    .status(AgentResultStatus.SUCCESS)
                    .agentId(agentId)
                    .confidence(0.85)
                    .processingTime(Duration.ofMillis(10))
                    .output(Map.of("result", "success"))
                    .build());

            GovernedAgentDispatcher dispatcher = new GovernedAgentDispatcher(
                delegate, invariantMonitor, traceLedger, modeSelector, releaseRepository);

            // When - dispatch through governed path
            AgentContext ctx = AgentContext.builder()
                .turnId("turn-1")
                .agentId("test-agent")
                .tenantId("tenant-x")
                .memoryStore(memoryStore)
                .build();

            AgentResult<?> result = runPromise(() -> dispatcher.dispatch("test-agent", "input", ctx));

            // Then - dispatch is denied
            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.DENIED);
            assertThat(result.getExplanation()).contains("BLOCKED");

            // Then - trace ledger contains denial events
            List<com.ghatana.agent.audit.TraceEvent> events = runPromise(() ->
                traceLedger.getByAgent("test-agent", "tenant-x", null, null, 100));
            assertThat(events).isNotEmpty();
        }

        @Test
        @DisplayName("governed adapter enforces policy metadata before dispatch")
        void governedAdapterEnforcesPolicyMetadata_beforeDispatch() {
            // Given - create governed adapter with full policy metadata
            TestAgent agent = new TestAgent("test-agent");
            AgentEventOperatorCapabilityAdapter adapter = new AgentEventOperatorCapabilityAdapter(
                agent,
                "agents/test-agent@1.0.0",
                AgentCapabilityRole.AGENT_REVIEW,
                "schema://agent/input",
                "schema://agent/output",
                AgentSideEffectProfile.PROPOSE_ACTION,
                policy("model"),
                policy("tool"),
                policy("memory"),
                policy("retrieval"),
                policy("guardrail"),
                policy("replay"),
                policy("uncertainty"),
                policy("human-review"),
                policy("observability"),
                memoryStore);

            // When - process through adapter
            EventOperatorResult<Map<String, Object>> result = runPromise(() -> adapter.process(
                eventContext(Map.of("request", "test")),
                runtimeContext()));

            // Then - result contains policy metadata
            assertThat(result.success()).isTrue();
            assertThat(result.evidence()).containsKey("modelPolicy");
            assertThat(result.evidence()).containsKey("toolPolicy");
            assertThat(result.evidence()).containsKey("memoryPolicy");
            assertThat(result.evidence()).containsKey("retrievalPolicy");
            assertThat(result.evidence()).containsKey("guardrailPolicy");
            assertThat(result.evidence()).containsKey("replayPolicy");
            assertThat(result.evidence()).containsKey("uncertaintyPolicy");
            assertThat(result.evidence()).containsKey("humanReviewPolicy");
            assertThat(result.evidence()).containsKey("observabilityPolicy");
        }

        @Test
        @DisplayName("governed dispatch with side-effecting capability requires explicit controls")
        void governedDispatchSideEffecting_requiresExplicitControls() {
            // Given - side-effecting capability
            TestAgent agent = new TestAgent("action-agent");

            // When/Then - adapter instantiation fails without required controls
            org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                new AgentEventOperatorCapabilityAdapter(
                    agent,
                    "agents/action-agent@1.0.0",
                    AgentCapabilityRole.AGENT_ACTION,
                    "schema://agent/input",
                    "schema://agent/output",
                    AgentSideEffectProfile.SIDE_EFFECTING,
                    policy("model"),
                    Map.of(), // Missing allowedTools
                    policy("memory"),
                    policy("retrieval"),
                    policy("guardrail"),
                    policy("replay"),
                    policy("uncertainty"),
                    policy("human-review"),
                    policy("observability"),
                    memoryStore))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allowedTools");
        }

        @Test
        @DisplayName("governed dispatch with no-op memory store fails unless policy allows")
        void governedDispatchNoOpMemory_failsUnlessPolicyAllows() {
            // Given - agent with no-op memory store
            TestAgent agent = new TestAgent("memory-agent");

            // When/Then - adapter instantiation fails without policy allowance
            org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                new AgentEventOperatorCapabilityAdapter(
                    agent,
                    "agents/memory-agent@1.0.0",
                    AgentCapabilityRole.AGENT_REVIEW,
                    "schema://agent/input",
                    "schema://agent/output",
                    AgentSideEffectProfile.PROPOSE_ACTION,
                    policy("model"),
                    policy("tool"),
                    policy("memory"),
                    policy("retrieval"),
                    policy("guardrail"),
                    policy("replay"),
                    policy("uncertainty"),
                    policy("human-review"),
                    policy("observability"),
                    MemoryStore.noOp()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("durable MemoryStore");
        }

        @Test
        @DisplayName("governed dispatch enforces tenant isolation")
        void governedDispatch_enforcesTenantIsolation() {
            // Given - create governed adapter for tenant A
            TestAgent agent = new TestAgent("test-agent");
            AgentEventOperatorCapabilityAdapter adapter = new AgentEventOperatorCapabilityAdapter(
                agent,
                "agents/test-agent@1.0.0",
                AgentCapabilityRole.AGENT_REVIEW,
                "schema://agent/input",
                "schema://agent/output",
                AgentSideEffectProfile.PROPOSE_ACTION,
                policy("model"),
                policy("tool"),
                policy("memory"),
                policy("retrieval"),
                policy("guardrail"),
                policy("replay"),
                policy("uncertainty"),
                policy("human-review"),
                policy("observability"),
                memoryStore);

            // When - process with tenant A context
            EventOperatorResult<Map<String, Object>> result = runPromise(() -> adapter.process(
                eventContext(Map.of("request", "test")),
                runtimeContext()));

            // Then - result contains tenant A ID
            assertThat(result.success()).isTrue();
            assertThat(result.output()).hasValueSatisfying(output ->
                assertThat(output).containsEntry("tenantId", "tenant-a"));
        }

        @Test
        @DisplayName("governed dispatch requires trace and correlation IDs")
        void governedDispatch_requiresTraceAndCorrelation() {
            // Given - create governed adapter
            TestAgent agent = new TestAgent("test-agent");
            AgentEventOperatorCapabilityAdapter adapter = new AgentEventOperatorCapabilityAdapter(
                agent,
                "agents/test-agent@1.0.0",
                AgentCapabilityRole.AGENT_REVIEW,
                "schema://agent/input",
                "schema://agent/output",
                AgentSideEffectProfile.PROPOSE_ACTION,
                policy("model"),
                policy("tool"),
                policy("memory"),
                policy("retrieval"),
                policy("guardrail"),
                policy("replay"),
                policy("uncertainty"),
                policy("human-review"),
                policy("observability"),
                memoryStore);

            // When/Then - missing trace ID fails
            OperatorRuntimeContext missingTrace = new OperatorRuntimeContext(
                "tenant-a",
                Optional.empty(),
                Optional.of("corr-1"),
                Map.of(),
                Map.of());

            org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                runPromise(() -> adapter.process(eventContext(Map.of("request", "x")), missingTrace)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("traceId is required");

            // When/Then - missing correlation ID fails
            OperatorRuntimeContext missingCorrelation = new OperatorRuntimeContext(
                "tenant-a",
                Optional.of("trace-1"),
                Optional.empty(),
                Map.of(),
                Map.of());

            org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                runPromise(() -> adapter.process(eventContext(Map.of("request", "x")), missingCorrelation)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("correlationId is required");
        }

        @Test
        @DisplayName("governed adapter maps PENDING_APPROVAL to non-success result")
        void governedAdapter_mapsPendingApprovalToNonSuccess() {
            // Given - create agent that returns PENDING_APPROVAL
            PendingApprovalAgent agent = new PendingApprovalAgent("approval-agent");

            // Given - create governed adapter with side-effecting profile
            AgentEventOperatorCapabilityAdapter adapter = new AgentEventOperatorCapabilityAdapter(
                agent,
                "agents/approval-agent@1.0.0",
                AgentCapabilityRole.AGENT_ACTION,
                "schema://agent/input",
                "schema://agent/output",
                AgentSideEffectProfile.SIDE_EFFECTING,
                policy("model"),
                Map.of("allowedTools", List.of("tool-1"), "allowedActions", List.of("action-1")),
                policy("memory"),
                policy("retrieval"),
                policy("guardrail"),
                policy("replay"),
                policy("uncertainty"),
                policy("human-review"),
                policy("observability"),
                memoryStore);

            // When - process through adapter
            EventOperatorResult<Map<String, Object>> result = runPromise(() -> adapter.process(
                eventContext(Map.of("request", "approval-needed")),
                runtimeContext()));

            // Then - result is not successful
            assertThat(result.success()).isFalse();

            // Then - error message indicates approval required
            assertThat(result.errors()).isNotEmpty();
            assertThat(result.errors()).anyMatch(error -> 
                error.contains("approval") || error.contains("pending"));

            // Then - evidence contains approval status
            assertThat(result.evidence()).containsKey("approvalStatus");
            assertThat(result.evidence().get("approvalStatus")).isEqualTo("PENDING_APPROVAL");

            // Then - evidence contains full context for audit
            assertThat(result.evidence()).containsKey("agentRef");
            assertThat(result.evidence()).containsKey("tenantId");
            assertThat(result.evidence()).containsKey("traceId");
            assertThat(result.evidence()).containsKey("correlationId");
        }
    }

    // Helper methods

    private static Map<String, Object> policy(String policyRef) {
        return Map.of("policyRef", policyRef, "enforcement", "required", "enabled", true);
    }

    private static OperatorRuntimeContext runtimeContext() {
        return new OperatorRuntimeContext(
            "tenant-a",
            Optional.of("trace-1"),
            Optional.of("corr-1"),
            Map.of("runtimePolicy", "required"),
            Map.of("profile", "production"));
    }

    private static EventContext<Map<String, Object>> eventContext(Map<String, Object> input) {
        return new EventContext<>(
            "tenant-a",
            List.of(),
            Optional.empty(),
            Optional.empty(),
            Map.of(),
            new EventTimeContext(
                EventTimeContext.TimeMode.EVENT_TIME,
                Optional.empty(),
                Duration.ZERO,
                EventTimeContext.LateEventBehavior.INCORPORATE,
                Optional.empty()),
            UncertaintyContext.certain(),
            new ReplayContext(
                ReplayContext.ReplayMode.LIVE,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Map.of()),
            Optional.of(input));
    }

    private static final class TestAgent implements TypedAgent<Map<String, Object>, Map<String, Object>> {
        private final AgentDescriptor descriptor;

        TestAgent(String agentId) {
            this.descriptor = AgentDescriptor.builder()
                .agentId(agentId)
                .name("Test Agent")
                .namespace("test")
                .type(AgentType.PROBABILISTIC)
                .build();
        }

        @Override
        public AgentDescriptor descriptor() {
            return descriptor;
        }

        @Override
        public Promise<Void> initialize(AgentConfig config) {
            return Promise.complete();
        }

        @Override
        public Promise<Void> shutdown() {
            return Promise.complete();
        }

        @Override
        public Promise<com.ghatana.platform.health.HealthStatus> healthCheck() {
            return Promise.of(com.ghatana.platform.health.HealthStatus.healthy("ok"));
        }

        @Override
        public Promise<AgentResult<Map<String, Object>>> process(
                AgentContext ctx,
                Map<String, Object> input) {
            return Promise.of(AgentResult.<Map<String, Object>>builder()
                .output(Map.of(
                    "agentId", descriptor.getAgentId(),
                    "tenantId", ctx.getTenantId(),
                    "traceId", ctx.getTraceId(),
                    "correlationId", ctx.getMetadata().get("correlationId"),
                    "request", input.get("request")))
                .confidence(0.85)
                .status(AgentResultStatus.SUCCESS)
                .agentId(descriptor.getAgentId())
                .processingTime(Duration.ofMillis(1))
                .build());
        }
    }

    private static final class PendingApprovalAgent implements TypedAgent<Map<String, Object>, Map<String, Object>> {
        private final AgentDescriptor descriptor;

        PendingApprovalAgent(String agentId) {
            this.descriptor = AgentDescriptor.builder()
                .agentId(agentId)
                .name("Pending Approval Agent")
                .namespace("test")
                .type(AgentType.PROBABILISTIC)
                .build();
        }

        @Override
        public AgentDescriptor descriptor() {
            return descriptor;
        }

        @Override
        public Promise<Void> initialize(AgentConfig config) {
            return Promise.complete();
        }

        @Override
        public Promise<Void> shutdown() {
            return Promise.complete();
        }

        @Override
        public Promise<com.ghatana.platform.health.HealthStatus> healthCheck() {
            return Promise.of(com.ghatana.platform.health.HealthStatus.healthy("ok"));
        }

        @Override
        public Promise<AgentResult<Map<String, Object>>> process(
                AgentContext ctx,
                Map<String, Object> input) {
            return Promise.of(AgentResult.<Map<String, Object>>builder()
                .output(Map.of(
                    "agentId", descriptor.getAgentId(),
                    "tenantId", ctx.getTenantId(),
                    "traceId", ctx.getTraceId(),
                    "correlationId", ctx.getMetadata().get("correlationId"),
                    "request", input.get("request")))
                .confidence(0.85)
                .status(AgentResultStatus.PENDING_APPROVAL)
                .explanation("Agent execution requires approval")
                .agentId(descriptor.getAgentId())
                .processingTime(Duration.ofMillis(1))
                .build());
        }
    }
}
