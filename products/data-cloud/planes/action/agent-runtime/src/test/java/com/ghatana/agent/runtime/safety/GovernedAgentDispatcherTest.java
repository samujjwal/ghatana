/*
 * Copyright (c) 2026 Ghatana Inc. 
 * All rights reserved.
 */
/**
 * @doc.type class
 * @doc.purpose Test release-awareness and governance checks in GovernedAgentDispatcher
 * @doc.layer agent-runtime
 * @doc.pattern Test
 */
package com.ghatana.agent.runtime.safety;

import com.ghatana.agent.AgentResult;
import com.ghatana.agent.AgentResultStatus;
import com.ghatana.agent.audit.AgentTraceLedger;
import com.ghatana.agent.audit.HashChainedTraceAppender;
import com.ghatana.agent.dispatch.AgentDispatcher;
import com.ghatana.agent.dispatch.ExecutionTier;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.agent.release.AgentRelease;
import com.ghatana.agent.release.AgentReleaseBuilder;
import com.ghatana.agent.release.AgentReleaseRepository;
import com.ghatana.agent.release.AgentReleaseState;
import com.ghatana.agent.release.InMemoryAgentReleaseRepository;
import com.ghatana.agent.runtime.mode.ExecutionStrategy;
import com.ghatana.agent.runtime.mode.MasteryAwareModeSelector;
import com.ghatana.agent.runtime.mode.ModeSelectionResult;
import com.ghatana.agent.runtime.mode.TaskClassification;
import com.ghatana.agent.runtime.mode.TaskNovelty;
import com.ghatana.agent.runtime.mode.TaskRiskLevel;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ghatana.agent.audit.TraceEvent;
import com.ghatana.agent.audit.TraceEventType;
import com.ghatana.agent.context.version.VersionContext;
import com.ghatana.agent.context.version.VersionContextResolver;
import com.ghatana.agent.mastery.MasteryDecision;
import com.ghatana.agent.mastery.MasteryQuery;
import com.ghatana.agent.mastery.MasteryRegistry;
import com.ghatana.agent.mastery.MasteryScore;
import com.ghatana.agent.mastery.MasteryState;
import com.ghatana.agent.mastery.VersionApplicability;
import com.ghatana.agent.mastery.VersionScope;
import com.ghatana.agent.runtime.mode.SupervisionMode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for GovernedAgentDispatcher: release-awareness, invariant checks,
 * trace recording, and backward-compat 3-arg constructor.
 */
@DisplayName("GovernedAgentDispatcher")
@ExtendWith(MockitoExtension.class) 
class GovernedAgentDispatcherTest extends EventloopTestBase {

    @Mock
    private AgentDispatcher delegate;

    @Mock
    private AgentTraceLedger traceLedger;

    @Mock
    private AgentReleaseRepository releaseRepository;

    @Mock
    private MasteryAwareModeSelector modeSelector;

    private DefaultInvariantMonitor invariantMonitor;
    private AgentContext ctx;

    @Override
    protected Duration eventloopTimeout() { 
        return Duration.ofSeconds(10); 
    }

    @BeforeEach
    void setUp() {
        invariantMonitor = new DefaultInvariantMonitor();
        ctx = AgentContext.builder()
                .turnId("turn-1")
                .agentId("test-agent")
                .tenantId("tenant-x")
                .memoryStore(mock(MemoryStore.class))
                .build();

        // Default stubs — lenient because not all tests use them
        lenient().when(traceLedger.append(any())).thenReturn(Promise.of(null));
        lenient().when(delegate.dispatch(anyString(), any(), any()))
                .thenReturn(Promise.of(AgentResult.builder()
                        .status(AgentResultStatus.SUCCESS)
                        .agentId("test-agent")
                        .confidence(1.0)
                        .processingTime(Duration.ofMillis(10))
                        .build()));
        lenient().when(delegate.resolve(anyString())).thenReturn(ExecutionTier.JAVA_IMPLEMENTED);
        
        // Stub modeSelector to return a default mode
        ModeSelectionResult defaultResult = ModeSelectionResult.autonomous(ExecutionStrategy.DETERMINISTIC_EXECUTION, "Default mode");
        MasteryDecision defaultDecision = MasteryDecision.allow(
                "default-item", "default-skill", MasteryState.PRACTICED,
                MasteryScore.correctnessOnly(0.5), VersionScope.empty(),
                "Default decision");
        MasteryAwareModeSelector.EnrichedModeSelectionResult enrichedDefaultResult = new MasteryAwareModeSelector.EnrichedModeSelectionResult(
                defaultResult.strategy(),
                defaultResult.supervision(),
                defaultResult.reasoning(),
                Map.of(),
                defaultDecision,
                TaskClassification.of(TaskRiskLevel.LOW, TaskNovelty.FAMILIAR),
                VersionContext.empty()
        );
        lenient().when(modeSelector.selectMode(any(), any(), any()))
                .thenReturn(Promise.of(enrichedDefaultResult));
    }

    // =========================================================================
    // Backward compatibility — 3-arg constructor (no release repository) 
    // =========================================================================

    @Nested
    @DisplayName("without release repository (backward-compat)")
    class WithoutReleaseRepository {

        @Test
        @DisplayName("3-arg constructor dispatches without release check")
        void threeArgConstructorDispatchesWithoutReleaseCheck() { 
            GovernedAgentDispatcher dispatcher = new GovernedAgentDispatcher( 
                    delegate, invariantMonitor, traceLedger, modeSelector);

            AgentResult<?> result = runPromise(() -> dispatcher.dispatch("test-agent", "input", ctx)); 

            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.SUCCESS); 
            verify(delegate).dispatch(anyString(), any(), any()); 
        }

        @Test
        @DisplayName("resolve delegates to inner dispatcher")
        void resolveDelegatesToInnerDispatcher() { 
            GovernedAgentDispatcher dispatcher = new GovernedAgentDispatcher( 
                    delegate, invariantMonitor, traceLedger, modeSelector);

            ExecutionTier tier = dispatcher.resolve("test-agent");

            assertThat(tier).isEqualTo(ExecutionTier.JAVA_IMPLEMENTED); 
            verify(delegate).resolve("test-agent");
        }
    }

    // =========================================================================
    // Release guard — ACTIVE release dispatches successfully
    // =========================================================================

    /** Creates a minimal valid release with TX-2/TX-5 required fields. */
    private static AgentRelease release(String agentId, AgentReleaseState state) {
        AgentReleaseBuilder builder = new AgentReleaseBuilder()
                .agentId(agentId)
                .tenantId("tenant-x")
                .releaseVersion("1.0.0")
                .state(state)
                .redactionProfileId("rp-test")
                .threatModelId("tm-test")
                .evaluationPackId("ep-test")
                .memoryContractId("mc-test")
                .addPermittedPurpose("agent.inference")
                .capabilityMaturityProfile("L1");
        if (state == AgentReleaseState.ACTIVE || state == AgentReleaseState.CANARY) {
            builder.masteryPolicyPackId("mastery-pack-1");
            builder.learningContractId("learning-contract-1");
        }
        return builder.build();
    }

    @Nested
    @DisplayName("with release repository")
    class WithReleaseRepository {

        private GovernedAgentDispatcher dispatcher;

        @BeforeEach
        void setUp() { 
            dispatcher = new GovernedAgentDispatcher( 
                    delegate, invariantMonitor, traceLedger, modeSelector, releaseRepository);
        }

        @Test
        @DisplayName("dispatches when active release is dispatchable")
        void dispatchesWhenReleaseIsDispatchable() { 
            AgentRelease activeRelease = release("test-agent", AgentReleaseState.ACTIVE); 

            when(releaseRepository.findGoverningRelease("test-agent", "tenant-x")) 
                    .thenReturn(Promise.of(Optional.of(activeRelease))); 

            AgentResult<?> result = runPromise(() -> dispatcher.dispatch("test-agent", "input", ctx)); 

            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.SUCCESS); 
        }

        @Test
        @DisplayName("dispatches when no governing release found")
        void dispatchesWhenNoGoverningReleaseFound() { 
            when(releaseRepository.findGoverningRelease("test-agent", "tenant-x")) 
                    .thenReturn(Promise.of(Optional.empty())); 

            AgentResult<?> result = runPromise(() -> dispatcher.dispatch("test-agent", "input", ctx)); 

            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.SUCCESS); 
        }

        @Test
        @DisplayName("denies dispatch when release state is BLOCKED")
        void deniesDispatchWhenReleaseIsBlocked() { 
            AgentRelease blockedRelease = release("test-agent", AgentReleaseState.BLOCKED); 

            when(releaseRepository.findGoverningRelease("test-agent", "tenant-x")) 
                    .thenReturn(Promise.of(Optional.of(blockedRelease))); 

            AgentResult<?> result = runPromise(() -> dispatcher.dispatch("test-agent", "input", ctx)); 

            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.DENIED); 
            assertThat(result.getExplanation()).contains("BLOCKED");
        }

        @Test
        @DisplayName("denies dispatch when release state is RETIRED")
        void deniesDispatchWhenReleaseIsRetired() { 
            AgentRelease retiredRelease = release("test-agent", AgentReleaseState.RETIRED); 

            when(releaseRepository.findGoverningRelease("test-agent", "tenant-x")) 
                    .thenReturn(Promise.of(Optional.of(retiredRelease))); 

            AgentResult<?> result = runPromise(() -> dispatcher.dispatch("test-agent", "input", ctx)); 

            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.DENIED); 
            assertThat(result.getExplanation()).contains("RETIRED");
        }

        @Test
        @DisplayName("denies dispatch when release state is DEPRECATED")
        void deniesDispatchWhenReleaseIsDeprecated() { 
            AgentRelease deprecatedRelease = release("test-agent", AgentReleaseState.DEPRECATED); 

            when(releaseRepository.findGoverningRelease("test-agent", "tenant-x")) 
                    .thenReturn(Promise.of(Optional.of(deprecatedRelease))); 

            AgentResult<?> result = runPromise(() -> dispatcher.dispatch("test-agent", "input", ctx)); 

            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.DENIED); 
        }

        @Test
        @DisplayName("enriches context with agentReleaseId when release is found")
        @SuppressWarnings("unchecked")
        void enrichesContextWithAgentReleaseId() { 
            AgentRelease activeRelease = release("test-agent", AgentReleaseState.ACTIVE); 

            when(releaseRepository.findGoverningRelease("test-agent", "tenant-x")) 
                    .thenReturn(Promise.of(Optional.of(activeRelease))); 

            // Capture the context passed to delegate
            when(delegate.dispatch(anyString(), any(), any())) 
                    .thenAnswer(invocation -> { 
                        AgentContext capturedCtx = invocation.getArgument(2); 
                        // Verify that the context was enriched with the release ID
                        assertThat(capturedCtx.getConfig("agentReleaseId"))
                                .isEqualTo(activeRelease.agentReleaseId()); 
                        return Promise.of(AgentResult.builder() 
                                .status(AgentResultStatus.SUCCESS) 
                                .agentId("test-agent")
                                .confidence(1.0) 
                                .processingTime(Duration.ofMillis(5)) 
                                .build()); 
                    });

            runPromise(() -> dispatcher.dispatch("test-agent", "input", ctx)); 
        }

        @Test
        @DisplayName("does not add agentReleaseId to context when no release found")
        @SuppressWarnings("unchecked")
        void doesNotAddReleaseIdWhenNoReleaseFound() { 
            when(releaseRepository.findGoverningRelease("test-agent", "tenant-x")) 
                    .thenReturn(Promise.of(Optional.empty())); 

            when(delegate.dispatch(anyString(), any(), any())) 
                    .thenAnswer(invocation -> { 
                        AgentContext capturedCtx = invocation.getArgument(2); 
                        assertThat(capturedCtx.getConfig("agentReleaseId")).isNull();
                        return Promise.of(AgentResult.builder() 
                                .status(AgentResultStatus.SUCCESS) 
                                .agentId("test-agent")
                                .confidence(1.0) 
                                .processingTime(Duration.ofMillis(5)) 
                                .build()); 
                    });

            runPromise(() -> dispatcher.dispatch("test-agent", "input", ctx)); 
        }

        @Test
        @DisplayName("delegates resolve to inner dispatcher")
        void delegatesResolve() { 
            ExecutionTier tier = dispatcher.resolve("test-agent");
            assertThat(tier).isEqualTo(ExecutionTier.JAVA_IMPLEMENTED); 
        }
    }

    // =========================================================================
    // Release guard — integration with InMemoryAgentReleaseRepository
    // =========================================================================

    @Nested
    @DisplayName("with InMemoryAgentReleaseRepository")
    class WithInMemoryReleaseRepository {

        @Test
        @DisplayName("dispatches active release end-to-end")
        void dispatchesActiveReleaseEndToEnd() { 
            InMemoryAgentReleaseRepository repo = new InMemoryAgentReleaseRepository(); 
            AgentRelease release = release("test-agent", AgentReleaseState.ACTIVE); 
            runPromise(() -> repo.save(release)); 

            GovernedAgentDispatcher dispatcher = new GovernedAgentDispatcher( 
                    delegate, invariantMonitor, traceLedger, modeSelector, repo);

            AgentResult<?> result = runPromise(() -> dispatcher.dispatch("test-agent", "payload", ctx)); 

            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.SUCCESS); 
        }

        @Test
        @DisplayName("uses real trace ledger end-to-end with release guard")
        void usesRealTraceLedgerEndToEnd() { 
            HashChainedTraceAppender ledger = new HashChainedTraceAppender(); 
            InMemoryAgentReleaseRepository repo = new InMemoryAgentReleaseRepository(); 
            AgentRelease release = release("test-agent", AgentReleaseState.ACTIVE); 
            runPromise(() -> repo.save(release)); 

            GovernedAgentDispatcher dispatcher = new GovernedAgentDispatcher( 
                    delegate, invariantMonitor, ledger, modeSelector, repo);

            AgentResult<?> result = runPromise(() -> dispatcher.dispatch("test-agent", "payload", ctx)); 

            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.SUCCESS); 
        }

        @Test
        @DisplayName("blocks dispatch on BLOCKED state end-to-end with real ledger")
        void blocksDispatchWithRealLedger() { 
            HashChainedTraceAppender ledger = new HashChainedTraceAppender(); 
            InMemoryAgentReleaseRepository repo = new InMemoryAgentReleaseRepository(); 
            AgentRelease release = release("test-agent", AgentReleaseState.BLOCKED); 
            runPromise(() -> repo.save(release)); 

            GovernedAgentDispatcher dispatcher = new GovernedAgentDispatcher( 
                    delegate, invariantMonitor, ledger, modeSelector, repo);

            AgentResult<?> result = runPromise(() -> dispatcher.dispatch("test-agent", "payload", ctx)); 

            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.DENIED); 
            assertThat(result.getExplanation()).containsIgnoringCase("BLOCKED");
        }
    }

    // =========================================================================
    // TX-4: Explainability trace events (TURN_STARTED, POLICY_EVALUATED) 
    // =========================================================================

    @Nested
    @DisplayName("TX-4 explainability trace events")
    class ExplainabilityTraceEvents {

        private final List<TraceEvent> capturedEvents = new ArrayList<>(); 

        @BeforeEach
        void captureAppends() { 
            capturedEvents.clear(); 
            lenient().when(traceLedger.append(any())).thenAnswer(invocation -> { 
                capturedEvents.add(invocation.getArgument(0)); 
                return Promise.of(null); 
            });
        }

        @Test
        @DisplayName("emits TURN_STARTED event before dispatch")
        void emitsTurnStarted() {
            GovernedAgentDispatcher dispatcher = new GovernedAgentDispatcher(
                    delegate, invariantMonitor, traceLedger, modeSelector);

            runPromise(() -> dispatcher.dispatch("test-agent", "input", ctx));

            assertThat(capturedEvents)
                    .extracting(TraceEvent::eventType)
                    .contains(TraceEventType.TURN_STARTED);
        }

        @Test
        @DisplayName("TURN_STARTED event payload includes agentId")
        void turnStartedPayloadHasAgentId() {
            GovernedAgentDispatcher dispatcher = new GovernedAgentDispatcher(
                    delegate, invariantMonitor, traceLedger, modeSelector);

            runPromise(() -> dispatcher.dispatch("test-agent", "input", ctx));

            TraceEvent turnStarted = capturedEvents.stream()
                    .filter(e -> e.eventType() == TraceEventType.TURN_STARTED)
                    .findFirst()
                    .orElseThrow();
            assertThat(turnStarted.payload()).containsEntry("agentId", "test-agent");
        }

        @Test
        @DisplayName("TURN_STARTED includes release metadata when release present")
        void turnStartedIncludesReleaseMetadata() {
            AgentRelease rel = new AgentReleaseBuilder()
                    .agentId("test-agent")
                    .tenantId("tenant-x")
                    .releaseVersion("2.0.0")
                    .state(AgentReleaseState.ACTIVE)
                    .redactionProfileId("rp-prod")
                    .threatModelId("tm-prod")
                    .evaluationPackId("ep-prod")
                    .memoryContractId("mc-prod")
                    .addPermittedPurpose("agent.inference")
                    .capabilityMaturityProfile("L2")
                    .policyPackId("pp-123")
                    .masteryPolicyPackId("mastery-pack-1")
                    .learningContractId("learning-contract-1")
                    .build(); 

            when(releaseRepository.findGoverningRelease("test-agent", "tenant-x")) 
                    .thenReturn(Promise.of(Optional.of(rel))); 

            GovernedAgentDispatcher dispatcher = new GovernedAgentDispatcher( 
                    delegate, invariantMonitor, traceLedger, modeSelector, releaseRepository);

            runPromise(() -> dispatcher.dispatch("test-agent", "input", ctx)); 

            TraceEvent turnStarted = capturedEvents.stream() 
                    .filter(e -> e.eventType() == TraceEventType.TURN_STARTED) 
                    .findFirst() 
                    .orElseThrow(); 
            assertThat(turnStarted.payload()) 
                    .containsEntry("agentReleaseId", rel.agentReleaseId()) 
                    .containsEntry("releaseVersion", "2.0.0") 
                    .containsEntry("redactionProfileId", "rp-prod") 
                    .containsEntry("threatModelId", "tm-prod") 
                    .containsEntry("capabilityMaturityProfile", "L2") 
                    .containsEntry("policyPackId", "pp-123"); 
        }

        @Test
        @DisplayName("emits POLICY_EVALUATED (ALLOW) when invariants pass")
        void emitsPolicyEvaluatedAllowWhenInvariantsPass() {
            GovernedAgentDispatcher dispatcher = new GovernedAgentDispatcher(
                    delegate, invariantMonitor, traceLedger, modeSelector);

            runPromise(() -> dispatcher.dispatch("test-agent", "input", ctx));

            TraceEvent policyEval = capturedEvents.stream()
                    .filter(e -> e.eventType() == TraceEventType.POLICY_EVALUATED)
                    .findFirst()
                    .orElseThrow();
            assertThat(policyEval.payload()).containsEntry("decision", "ALLOW");
            assertThat(policyEval.payload()).containsEntry("violationCount", "0");
        }

        @Test
        @DisplayName("emits POLICY_EVALUATED (DENY) and ACTION_DENIED when invariants fail")
        void emitsPolicyEvaluatedDenyThenActionDenied() {
            // Inject a breached cost cap invariant by setting metrics that violate
            AgentContext overBudgetCtx = AgentContext.builder()
                    .turnId("turn-over")
                    .agentId("test-agent")
                    .tenantId("tenant-x")
                    .memoryStore(mock(MemoryStore.class))
                    .addConfig("__accumulatedCostUsd", 999.0)
                    .addConfig("__costCapUsd", 1.0)
                    .build();

            GovernedAgentDispatcher dispatcher = new GovernedAgentDispatcher(
                    delegate, invariantMonitor, traceLedger, modeSelector);

            AgentResult<?> result = runPromise(() ->
                    dispatcher.dispatch("test-agent", "input", overBudgetCtx));

            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.DENIED);

            List<TraceEventType> types = capturedEvents.stream()
                    .map(TraceEvent::eventType)
                    .toList();
            assertThat(types).contains(TraceEventType.TURN_STARTED);
            assertThat(types).contains(TraceEventType.POLICY_EVALUATED);
            assertThat(types).contains(TraceEventType.ACTION_DENIED);

            TraceEvent policyEval = capturedEvents.stream()
                    .filter(e -> e.eventType() == TraceEventType.POLICY_EVALUATED)
                    .findFirst()
                    .orElseThrow();
            assertThat(policyEval.payload()).containsEntry("decision", "DENY");
        }

        @Test
        @DisplayName("TURN_STARTED is emitted before POLICY_EVALUATED in sequence")
        void turnStartedBeforePolicyEvaluated() {
            GovernedAgentDispatcher dispatcher = new GovernedAgentDispatcher(
                    delegate, invariantMonitor, traceLedger, modeSelector);

            runPromise(() -> dispatcher.dispatch("test-agent", "input", ctx)); 

            List<TraceEventType> types = capturedEvents.stream() 
                    .map(TraceEvent::eventType) 
                    .toList(); 
            int turnStartedIdx = types.indexOf(TraceEventType.TURN_STARTED); 
            int policyEvaluatedIdx = types.indexOf(TraceEventType.POLICY_EVALUATED); 
            assertThat(turnStartedIdx).isNotNegative(); 
            assertThat(policyEvaluatedIdx).isGreaterThan(turnStartedIdx); 
        }

        @Test
        @DisplayName("POLICY_EVALUATED (ALLOW) payload includes policyPackId when release is present")
        void policyEvaluatedAllowIncludesPolicyPackId() {
            AgentRelease rel = new AgentReleaseBuilder()
                    .agentId("test-agent")
                    .tenantId("tenant-x")
                    .releaseVersion("1.0")
                    .state(AgentReleaseState.ACTIVE)
                    .redactionProfileId("rp-t")
                    .threatModelId("tm-t")
                    .evaluationPackId("ep-t")
                    .memoryContractId("mc-t")
                    .addPermittedPurpose("agent.inference")
                    .capabilityMaturityProfile("L1")
                    .policyPackId("pp-456")
                    .masteryPolicyPackId("mastery-pack-1")
                    .learningContractId("learning-contract-1")
                    .build(); 

            when(releaseRepository.findGoverningRelease("test-agent", "tenant-x")) 
                    .thenReturn(Promise.of(Optional.of(rel))); 

            GovernedAgentDispatcher dispatcher = new GovernedAgentDispatcher( 
                    delegate, invariantMonitor, traceLedger, modeSelector, releaseRepository);

            runPromise(() -> dispatcher.dispatch("test-agent", "input", ctx)); 

            TraceEvent policyEval = capturedEvents.stream() 
                    .filter(e -> e.eventType() == TraceEventType.POLICY_EVALUATED) 
                    .findFirst() 
                    .orElseThrow(); 
            assertThat(policyEval.payload()) 
                    .containsEntry("decision", "ALLOW") 
                    .containsEntry("policyPackId", "pp-456"); 
        }

        @Test
        @DisplayName("ACTION_EXECUTED payload includes policyPackId when release is present")
        void actionExecutedIncludesPolicyPackId() {
            AgentRelease rel = new AgentReleaseBuilder()
                    .agentId("test-agent")
                    .tenantId("tenant-x")
                    .releaseVersion("1.0")
                    .state(AgentReleaseState.ACTIVE)
                    .redactionProfileId("rp-t")
                    .threatModelId("tm-t")
                    .evaluationPackId("ep-t")
                    .memoryContractId("mc-t")
                    .addPermittedPurpose("agent.inference")
                    .capabilityMaturityProfile("L1")
                    .policyPackId("pp-789")
                    .masteryPolicyPackId("mastery-pack-1")
                    .learningContractId("learning-contract-1")
                    .build(); 

            when(releaseRepository.findGoverningRelease("test-agent", "tenant-x")) 
                    .thenReturn(Promise.of(Optional.of(rel))); 

            GovernedAgentDispatcher dispatcher = new GovernedAgentDispatcher( 
                    delegate, invariantMonitor, traceLedger, modeSelector, releaseRepository);

            runPromise(() -> dispatcher.dispatch("test-agent", "input", ctx)); 

            TraceEvent actionExecuted = capturedEvents.stream() 
                    .filter(e -> e.eventType() == TraceEventType.ACTION_EXECUTED) 
                    .findFirst() 
                    .orElseThrow(); 
            assertThat(actionExecuted.payload()).containsEntry("policyPackId", "pp-789"); 
        }

        @Test
        @DisplayName("full happy-path event sequence is correct with real ledger")
        void fullHappyPathSequenceWithRealLedger() { 
            HashChainedTraceAppender ledger = new HashChainedTraceAppender(); 
            InMemoryAgentReleaseRepository repo = new InMemoryAgentReleaseRepository(); 
            AgentRelease rel = release("test-agent", AgentReleaseState.ACTIVE); 
            runPromise(() -> repo.save(rel)); 

            GovernedAgentDispatcher dispatcher = new GovernedAgentDispatcher(
                    delegate, invariantMonitor, ledger, modeSelector, repo);

            runPromise(() -> dispatcher.dispatch("test-agent", "payload", ctx)); 

            String traceId = ctx.getConfig("__traceId") != null
                    ? ctx.getConfig("__traceId").toString()
                    : null;
            // Fetch all events from ledger
            List<TraceEvent> events = runPromise(() -> 
                    ledger.getByAgent("test-agent", "tenant-x", null, null, 100)); 

            assertThat(events).isNotEmpty(); 
            List<TraceEventType> types = events.stream().map(TraceEvent::eventType).toList(); 
            assertThat(types).contains( 
                    TraceEventType.TURN_STARTED,
                    TraceEventType.POLICY_EVALUATED,
                    TraceEventType.ACTION_EXECUTED);
        }
    }

    // =========================================================================
    // P8-T12: AgentCapabilityManifest guard
    // =========================================================================

    @Nested
    @DisplayName("manifest capability guard (P8-T12)")
    class ManifestGuardTests {

        private com.ghatana.agent.pluggability.AgentCapabilityManifest supervisedOnlyManifest() { 
            // A manifest with only SUPERVISED mode — not AUTONOMOUS
            return new com.ghatana.agent.pluggability.AgentCapabilityManifest( 
                    "test-agent", "1.0.0", "tenant-x",
                    java.util.List.of(com.ghatana.agent.pluggability.InteractionMode.SUPERVISED), 
                    com.ghatana.agent.pluggability.SupervisionRole.SUBORDINATE,
                    com.ghatana.agent.pluggability.HandoffCapability.NONE,
                    java.util.List.of(), java.util.List.of(), java.util.Map.of()); 
        }

        private com.ghatana.agent.pluggability.AgentCapabilityManifest autonomousManifest() {
            return com.ghatana.agent.pluggability.AgentCapabilityManifest.standalone("test-agent", "1.0.0", "tenant-x");
        }

        @Test
        @DisplayName("AUTONOMOUS manifest allows dispatch without supervisor context")
        void autonomousManifestAllowsDispatch() { 
            GovernedAgentDispatcher dispatcher = new GovernedAgentDispatcher(
                    delegate, invariantMonitor, traceLedger, modeSelector, null, null, autonomousManifest()); 
            AgentResult<?> result = runPromise(() -> dispatcher.dispatch("test-agent", "input", ctx)); 
            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.SUCCESS); 
        }

        @Test
        @DisplayName("null manifest skips manifest check")
        void nullManifestSkipsCheck() { 
            GovernedAgentDispatcher dispatcher = new GovernedAgentDispatcher(
                    delegate, invariantMonitor, traceLedger, modeSelector, null, null, null);
            AgentResult<?> result = runPromise(() -> dispatcher.dispatch("test-agent", "input", ctx)); 
            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.SUCCESS); 
        }

        @Test
        @DisplayName("SUPERVISED-only manifest with no supervisorAgentId in context → DENIED")
        void supervisedOnlyManifestWithoutSupervisorIsDenied() {
            GovernedAgentDispatcher dispatcher = new GovernedAgentDispatcher(
                    delegate, invariantMonitor, traceLedger, modeSelector, null, null, supervisedOnlyManifest());
            AgentResult<?> result = runPromise(() -> dispatcher.dispatch("test-agent", "input", ctx));
            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.DENIED);
        }

        @Test
        @DisplayName("SUPERVISED-only manifest with supervisorAgentId in context → allowed")
        void supervisedManifestWithSupervisorContextIsAllowed() {
            AgentContext supervisedCtx = ctx.toBuilder()
                    .addConfig("supervisorAgentId", "supervisor-001")
                    .build();
            GovernedAgentDispatcher dispatcher = new GovernedAgentDispatcher(
                    delegate, invariantMonitor, traceLedger, modeSelector, null, null, supervisedOnlyManifest());
            AgentResult<?> result = runPromise(() -> dispatcher.dispatch("test-agent", "input", supervisedCtx));
            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.SUCCESS);
        }
    }

    // =========================================================================
    // Phase 6 — Runtime dispatcher enforcement
    // =========================================================================

    @Nested
    @DisplayName("Phase 6 governance enforcement")
    class Phase6GovernanceTests {

        private final List<TraceEvent> capturedEvents = new ArrayList<>();

        @BeforeEach
        void captureAppends() {
            capturedEvents.clear();
            lenient().when(traceLedger.append(any())).thenAnswer(invocation -> {
                capturedEvents.add(invocation.getArgument(0));
                return Promise.of(null);
            });
        }

        @Test
        @DisplayName("shadow release cannot serve normal response")
        void shadowReleaseCannotServeNormalResponse() {
            AgentRelease shadowRelease = release("test-agent", AgentReleaseState.SHADOW);
            when(releaseRepository.findGoverningRelease("test-agent", "tenant-x"))
                    .thenReturn(Promise.of(Optional.of(shadowRelease)));

            GovernedAgentDispatcher dispatcher = new GovernedAgentDispatcher(
                    delegate, invariantMonitor, traceLedger, releaseRepository);

            // No shadowMode in context — normal response path
            AgentResult<?> result = runPromise(() -> dispatcher.dispatch("test-agent", "input", ctx));

            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.DENIED);
            assertThat(result.getExplanation()).containsIgnoringCase("SHADOW");
        }

        @Test
        @DisplayName("shadow release can run in shadow evaluation mode")
        void shadowReleaseCanRunInShadowMode() {
            AgentRelease shadowRelease = release("test-agent", AgentReleaseState.SHADOW);
            when(releaseRepository.findGoverningRelease("test-agent", "tenant-x"))
                    .thenReturn(Promise.of(Optional.of(shadowRelease)));

            GovernedAgentDispatcher dispatcher = new GovernedAgentDispatcher(
                    delegate, invariantMonitor, traceLedger, releaseRepository);

            AgentContext shadowCtx = ctx.toBuilder()
                    .addConfig("shadowMode", "true")
                    .build();

            AgentResult<?> result = runPromise(() -> dispatcher.dispatch("test-agent", "input", shadowCtx));

            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.SUCCESS);
        }

        @Test
        @DisplayName("blocked release denies dispatch")
        void blockedReleaseDenies() {
            AgentRelease blockedRelease = release("test-agent", AgentReleaseState.BLOCKED);
            when(releaseRepository.findGoverningRelease("test-agent", "tenant-x"))
                    .thenReturn(Promise.of(Optional.of(blockedRelease)));

            GovernedAgentDispatcher dispatcher = new GovernedAgentDispatcher(
                    delegate, invariantMonitor, traceLedger, releaseRepository);

            AgentResult<?> result = runPromise(() -> dispatcher.dispatch("test-agent", "input", ctx));

            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.DENIED);
        }

        @Test
        @DisplayName("missing skillId denies when mastery registry is configured")
        void missingSkillIdDeniesWhenMasteryBound() {
            MasteryRegistry masteryRegistry = mock(MasteryRegistry.class);
            AgentRelease masteryBoundRelease = release("test-agent", AgentReleaseState.ACTIVE);
            when(releaseRepository.findGoverningRelease("test-agent", "tenant-x"))
                    .thenReturn(Promise.of(Optional.of(masteryBoundRelease)));
            // Stub decide() to return a decision that denies dispatch due to mastery state
            MasteryDecision blockDecision = MasteryDecision.block(
                    "unknown", "test-agent", MasteryState.QUARANTINED,
                    MasteryScore.zero(), VersionScope.empty(),
                    VersionApplicability.UNKNOWN,
                    true, "Quarantined - skillId was derived from agentId");
            when(masteryRegistry.decide(any())).thenReturn(Promise.of(blockDecision));
            GovernedAgentDispatcher dispatcher = new GovernedAgentDispatcher(
                    delegate, invariantMonitor, traceLedger, releaseRepository, null, null,
                    masteryRegistry, null, null, null, null);

            // ctx has no skillId (will be derived from agentId)
            AgentResult<?> result = runPromise(() -> dispatcher.dispatch("test-agent", "input", ctx));

            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.DENIED);
            // The denial reason comes from the mastery decision state (QUARANTINED)
        }

        @Test
        @DisplayName("quarantined mastery denies dispatch")
        void quarantinedMasteryDenies() {
            MasteryRegistry masteryRegistry = mock(MasteryRegistry.class);
            MasteryDecision quarantinedDecision = MasteryDecision.block(
                    "item-1", "skill-1", MasteryState.QUARANTINED,
                    MasteryScore.zero(), VersionScope.empty(),
                    VersionApplicability.OBSOLETE,
                    true, "Quarantined due to unsafe behavior");
            when(masteryRegistry.decide(any())).thenReturn(Promise.of(quarantinedDecision));

            AgentContext skillCtx = ctx.toBuilder()
                    .addConfig("skillId", "skill-1")
                    .build();

            GovernedAgentDispatcher dispatcher = new GovernedAgentDispatcher(
                    delegate, invariantMonitor, traceLedger, null, null, null,
                    masteryRegistry, null, null, null, null);

            AgentResult<?> result = runPromise(() -> dispatcher.dispatch("test-agent", "input", skillCtx));

            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.DENIED);
            assertThat(result.getExplanation()).containsIgnoringCase("QUARANTINED");
        }

        @Test
        @DisplayName("obsolete mastery denies dispatch")
        void obsoleteMasteryDenies() {
            MasteryRegistry masteryRegistry = mock(MasteryRegistry.class);
            MasteryDecision obsoleteDecision = MasteryDecision.block(
                    "item-2", "skill-2", MasteryState.OBSOLETE,
                    MasteryScore.zero(), VersionScope.empty(),
                    VersionApplicability.OBSOLETE,
                    true, "Skill is obsolete");
            when(masteryRegistry.decide(any())).thenReturn(Promise.of(obsoleteDecision));

            AgentContext skillCtx = ctx.toBuilder()
                    .addConfig("skillId", "skill-2")
                    .build();

            GovernedAgentDispatcher dispatcher = new GovernedAgentDispatcher(
                    delegate, invariantMonitor, traceLedger, null, null, null,
                    masteryRegistry, null, null, null, null);

            AgentResult<?> result = runPromise(() -> dispatcher.dispatch("test-agent", "input", skillCtx));

            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.DENIED);
            assertThat(result.getExplanation()).containsIgnoringCase("OBSOLETE");
        }

        @Test
        @DisplayName("practiced mastery requires approval when requiresHumanApproval=true")
        void practicedMasteryRequiresApproval() {
            MasteryRegistry masteryRegistry = mock(MasteryRegistry.class);
            MasteryDecision practicedDecision = MasteryDecision.requireApproval(
                    "item-3", "skill-3", MasteryState.PRACTICED,
                    MasteryScore.correctnessOnly(0.5), VersionScope.empty(),
                    "Practiced mastery requires approval");
            when(masteryRegistry.decide(any())).thenReturn(Promise.of(practicedDecision));

            AgentContext skillCtx = ctx.toBuilder()
                    .addConfig("skillId", "skill-3")
                    // no hasApproval
                    .build();

            GovernedAgentDispatcher dispatcher = new GovernedAgentDispatcher(
                    delegate, invariantMonitor, traceLedger, null, null, null,
                    masteryRegistry, null, null, null, null);

            AgentResult<?> result = runPromise(() -> dispatcher.dispatch("test-agent", "input", skillCtx));

            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.DENIED);
            assertThat(result.getExplanation()).containsIgnoringCase("approval");
        }

        @Test
        @DisplayName("practiced mastery dispatches when approval is present")
        void practicedMasteryDispatchesWithApproval() {
            MasteryRegistry masteryRegistry = mock(MasteryRegistry.class);
            MasteryDecision practicedDecision = MasteryDecision.allow(
                    "item-4", "skill-4", MasteryState.PRACTICED,
                    MasteryScore.correctnessOnly(0.5), VersionScope.empty(),
                    VersionApplicability.ACTIVE,
                    0.5, false, "Practiced mastery dispatches with approval");
            when(masteryRegistry.decide(any())).thenReturn(Promise.of(practicedDecision));

            AgentContext skillCtx = ctx.toBuilder()
                    .addConfig("skillId", "skill-4")
                    .addConfig("hasApproval", Boolean.TRUE)
                    .build();

            GovernedAgentDispatcher dispatcher = new GovernedAgentDispatcher(
                    delegate, invariantMonitor, traceLedger, null, null, null,
                    masteryRegistry, null, null, null, null);

            AgentResult<?> result = runPromise(() -> dispatcher.dispatch("test-agent", "input", skillCtx));

            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.SUCCESS);
        }

        @Test
        @DisplayName("competent mastery requires verification proof")
        void competentMasteryRequiresVerification() {
            MasteryRegistry masteryRegistry = mock(MasteryRegistry.class);
            MasteryDecision competentDecision = MasteryDecision.requireVerification(
                    "item-5", "skill-5", MasteryState.COMPETENT,
                    MasteryScore.correctnessOnly(0.7), VersionScope.empty(),
                    "Competent mastery requires verification", List.of());
            when(masteryRegistry.decide(any())).thenReturn(Promise.of(competentDecision));

            AgentContext skillCtx = ctx.toBuilder()
                    .addConfig("skillId", "skill-5")
                    // no hasVerification
                    .build();

            GovernedAgentDispatcher dispatcher = new GovernedAgentDispatcher(
                    delegate, invariantMonitor, traceLedger, null, null, null,
                    masteryRegistry, null, null, null, null);

            AgentResult<?> result = runPromise(() -> dispatcher.dispatch("test-agent", "input", skillCtx));

            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.DENIED);
            assertThat(result.getExplanation()).containsIgnoringCase("verification");
        }

        @Test
        @DisplayName("trace ledger receives MASTERY_DECISION_MADE event when mastery configured")
        void traceLedgerReceivesMasteryDecisionEvent() {
            MasteryRegistry masteryRegistry = mock(MasteryRegistry.class);
            MasteryDecision allowDecision = MasteryDecision.allow(
                    "item-6", "skill-6", MasteryState.MASTERED,
                    MasteryScore.perfect(), VersionScope.empty(),
                    "Mastered");
            when(masteryRegistry.decide(any())).thenReturn(Promise.of(allowDecision));

            AgentContext skillCtx = ctx.toBuilder()
                    .addConfig("skillId", "skill-6")
                    .build();

            GovernedAgentDispatcher dispatcher = new GovernedAgentDispatcher(
                    delegate, invariantMonitor, traceLedger, null, null, null,
                    masteryRegistry, null, null, null, null);

            runPromise(() -> dispatcher.dispatch("test-agent", "input", skillCtx));

            assertThat(capturedEvents)
                    .extracting(TraceEvent::eventType)
                    .contains(TraceEventType.MASTERY_DECISION_MADE);
        }

        @Test
        @DisplayName("trace ledger receives VERSION_CONTEXT_RESOLVED event when resolver configured")
        void traceLedgerReceivesVersionContextResolvedEvent() {
            VersionContextResolver resolver = mock(VersionContextResolver.class);
            when(resolver.resolve(any())).thenReturn(Promise.of(VersionContext.empty()));

            GovernedAgentDispatcher dispatcher = new GovernedAgentDispatcher(
                    delegate, invariantMonitor, traceLedger, null, null, null,
                    null, resolver, null, null, null);

            runPromise(() -> dispatcher.dispatch("test-agent", "input", ctx));

            assertThat(capturedEvents)
                    .extracting(TraceEvent::eventType)
                    .contains(TraceEventType.VERSION_CONTEXT_RESOLVED);
        }

        @Test
        @DisplayName("trace ledger receives DISPATCH_ALLOWED event on successful governance")
        void traceLedgerReceivesDispatchAllowedEvent() {
            GovernedAgentDispatcher dispatcher = new GovernedAgentDispatcher(
                    delegate, invariantMonitor, traceLedger);

            runPromise(() -> dispatcher.dispatch("test-agent", "input", ctx));

            assertThat(capturedEvents)
                    .extracting(TraceEvent::eventType)
                    .contains(TraceEventType.DISPATCH_ALLOWED);
        }

        @Test
        @DisplayName("mode selector: DISPATCH_ALLOWED and MODE_SELECTED events emitted")
        void modeSelectorEmitsModeSelectedEvent() {
            MasteryAwareModeSelector modeSelector = mock(MasteryAwareModeSelector.class);
            ModeSelectionResult autoResult = ModeSelectionResult.autonomous(
                    ExecutionStrategy.DETERMINISTIC_EXECUTION, "direct execution");
            MasteryDecision masteryDecision = MasteryDecision.allow(
                    "mode-item", "mode-skill", MasteryState.PRACTICED,
                    MasteryScore.correctnessOnly(0.5), VersionScope.empty(),
                    "Mode decision");
            MasteryAwareModeSelector.EnrichedModeSelectionResult enrichedResult = new MasteryAwareModeSelector.EnrichedModeSelectionResult(
                    autoResult.strategy(),
                    autoResult.supervision(),
                    autoResult.reasoning(),
                    Map.of(),
                    masteryDecision,
                    TaskClassification.of(TaskRiskLevel.LOW, TaskNovelty.FAMILIAR),
                    VersionContext.empty()
            );
            when(modeSelector.selectMode(anyString(), anyString(), anyString(), anyString(), anyString(), any()))
                    .thenReturn(Promise.of(enrichedResult));

            AgentContext skillCtx = ctx.toBuilder()
                    .addConfig("skillId", "skill-mode")
                    .build();

            GovernedAgentDispatcher dispatcher = new GovernedAgentDispatcher(
                    delegate, invariantMonitor, traceLedger, null, null, null,
                    null, null, null, modeSelector, null);

            runPromise(() -> dispatcher.dispatch("test-agent", "input", skillCtx));

            assertThat(capturedEvents)
                    .extracting(TraceEvent::eventType)
                    .contains(TraceEventType.MODE_SELECTED);
        }
    }
}
