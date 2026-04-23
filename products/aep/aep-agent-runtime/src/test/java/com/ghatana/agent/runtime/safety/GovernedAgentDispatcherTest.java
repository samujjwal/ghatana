/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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
@ExtendWith(MockitoExtension.class) // GH-90000
class GovernedAgentDispatcherTest extends EventloopTestBase {

    @Mock
    private AgentDispatcher delegate;

    @Mock
    private AgentTraceLedger traceLedger;

    @Mock
    private AgentReleaseRepository releaseRepository;

    private DefaultInvariantMonitor invariantMonitor;
    private AgentContext ctx;

    @Override
    protected Duration eventloopTimeout() { // GH-90000
        return Duration.ofSeconds(10); // GH-90000
    }

    @BeforeEach
    void setUp() { // GH-90000
        invariantMonitor = new DefaultInvariantMonitor(); // GH-90000
        ctx = AgentContext.builder() // GH-90000
                .turnId("turn-1")
                .agentId("test-agent")
                .tenantId("tenant-x")
                .memoryStore(mock(MemoryStore.class)) // GH-90000
                .build(); // GH-90000

        // Default stubs — lenient because not all tests use them
        lenient().when(traceLedger.append(any())).thenReturn(Promise.of(null)); // GH-90000
        lenient().when(delegate.dispatch(anyString(), any(), any())) // GH-90000
                .thenReturn(Promise.of(AgentResult.builder() // GH-90000
                        .status(AgentResultStatus.SUCCESS) // GH-90000
                        .agentId("test-agent")
                        .confidence(1.0) // GH-90000
                        .processingTime(Duration.ofMillis(10)) // GH-90000
                        .build())); // GH-90000
        lenient().when(delegate.resolve(anyString())).thenReturn(ExecutionTier.JAVA_IMPLEMENTED); // GH-90000
    }

    // =========================================================================
    // Backward compatibility — 3-arg constructor (no release repository) // GH-90000
    // =========================================================================

    @Nested
    @DisplayName("without release repository (backward-compat)")
    class WithoutReleaseRepository {

        @Test
        @DisplayName("3-arg constructor dispatches without release check")
        void threeArgConstructorDispatchesWithoutReleaseCheck() { // GH-90000
            GovernedAgentDispatcher dispatcher = new GovernedAgentDispatcher( // GH-90000
                    delegate, invariantMonitor, traceLedger);

            AgentResult<?> result = runPromise(() -> dispatcher.dispatch("test-agent", "input", ctx)); // GH-90000

            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.SUCCESS); // GH-90000
            verify(delegate).dispatch(anyString(), any(), any()); // GH-90000
        }

        @Test
        @DisplayName("resolve delegates to inner dispatcher")
        void resolveDelegatesToInnerDispatcher() { // GH-90000
            GovernedAgentDispatcher dispatcher = new GovernedAgentDispatcher( // GH-90000
                    delegate, invariantMonitor, traceLedger);

            ExecutionTier tier = dispatcher.resolve("test-agent");

            assertThat(tier).isEqualTo(ExecutionTier.JAVA_IMPLEMENTED); // GH-90000
            verify(delegate).resolve("test-agent");
        }
    }

    // =========================================================================
    // Release guard — ACTIVE release dispatches successfully
    // =========================================================================

    /** Creates a minimal valid release with TX-2/TX-5 required fields. */
    private static AgentRelease release(String agentId, AgentReleaseState state) { // GH-90000
        return new AgentReleaseBuilder() // GH-90000
                .agentId(agentId) // GH-90000
                .releaseVersion("1.0.0")
                .state(state) // GH-90000
                .redactionProfileId("rp-test")
                .threatModelId("tm-test")
                .addPermittedPurpose("agent.inference")
                .capabilityMaturityProfile("L1")
                .build(); // GH-90000
    }

    @Nested
    @DisplayName("with release repository")
    class WithReleaseRepository {

        private GovernedAgentDispatcher dispatcher;

        @BeforeEach
        void setUp() { // GH-90000
            dispatcher = new GovernedAgentDispatcher( // GH-90000
                    delegate, invariantMonitor, traceLedger, releaseRepository);
        }

        @Test
        @DisplayName("dispatches when active release is dispatchable")
        void dispatchesWhenReleaseIsDispatchable() { // GH-90000
            AgentRelease activeRelease = release("test-agent", AgentReleaseState.ACTIVE); // GH-90000

            when(releaseRepository.findGoverningRelease("test-agent", "tenant-x")) // GH-90000
                    .thenReturn(Promise.of(Optional.of(activeRelease))); // GH-90000

            AgentResult<?> result = runPromise(() -> dispatcher.dispatch("test-agent", "input", ctx)); // GH-90000

            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.SUCCESS); // GH-90000
        }

        @Test
        @DisplayName("dispatches when no governing release found")
        void dispatchesWhenNoGoverningReleaseFound() { // GH-90000
            when(releaseRepository.findGoverningRelease("test-agent", "tenant-x")) // GH-90000
                    .thenReturn(Promise.of(Optional.empty())); // GH-90000

            AgentResult<?> result = runPromise(() -> dispatcher.dispatch("test-agent", "input", ctx)); // GH-90000

            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.SUCCESS); // GH-90000
        }

        @Test
        @DisplayName("denies dispatch when release state is BLOCKED")
        void deniesDispatchWhenReleaseIsBlocked() { // GH-90000
            AgentRelease blockedRelease = release("test-agent", AgentReleaseState.BLOCKED); // GH-90000

            when(releaseRepository.findGoverningRelease("test-agent", "tenant-x")) // GH-90000
                    .thenReturn(Promise.of(Optional.of(blockedRelease))); // GH-90000

            AgentResult<?> result = runPromise(() -> dispatcher.dispatch("test-agent", "input", ctx)); // GH-90000

            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.DENIED); // GH-90000
            assertThat(result.getExplanation()).contains("BLOCKED");
        }

        @Test
        @DisplayName("denies dispatch when release state is RETIRED")
        void deniesDispatchWhenReleaseIsRetired() { // GH-90000
            AgentRelease retiredRelease = release("test-agent", AgentReleaseState.RETIRED); // GH-90000

            when(releaseRepository.findGoverningRelease("test-agent", "tenant-x")) // GH-90000
                    .thenReturn(Promise.of(Optional.of(retiredRelease))); // GH-90000

            AgentResult<?> result = runPromise(() -> dispatcher.dispatch("test-agent", "input", ctx)); // GH-90000

            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.DENIED); // GH-90000
            assertThat(result.getExplanation()).contains("RETIRED");
        }

        @Test
        @DisplayName("denies dispatch when release state is DEPRECATED")
        void deniesDispatchWhenReleaseIsDeprecated() { // GH-90000
            AgentRelease deprecatedRelease = release("test-agent", AgentReleaseState.DEPRECATED); // GH-90000

            when(releaseRepository.findGoverningRelease("test-agent", "tenant-x")) // GH-90000
                    .thenReturn(Promise.of(Optional.of(deprecatedRelease))); // GH-90000

            AgentResult<?> result = runPromise(() -> dispatcher.dispatch("test-agent", "input", ctx)); // GH-90000

            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.DENIED); // GH-90000
        }

        @Test
        @DisplayName("enriches context with agentReleaseId when release is found")
        @SuppressWarnings("unchecked")
        void enrichesContextWithAgentReleaseId() { // GH-90000
            AgentRelease activeRelease = release("test-agent", AgentReleaseState.ACTIVE); // GH-90000

            when(releaseRepository.findGoverningRelease("test-agent", "tenant-x")) // GH-90000
                    .thenReturn(Promise.of(Optional.of(activeRelease))); // GH-90000

            // Capture the context passed to delegate
            when(delegate.dispatch(anyString(), any(), any())) // GH-90000
                    .thenAnswer(invocation -> { // GH-90000
                        AgentContext capturedCtx = invocation.getArgument(2); // GH-90000
                        // Verify that the context was enriched with the release ID
                        assertThat(capturedCtx.getConfig("agentReleaseId"))
                                .isEqualTo(activeRelease.agentReleaseId()); // GH-90000
                        return Promise.of(AgentResult.builder() // GH-90000
                                .status(AgentResultStatus.SUCCESS) // GH-90000
                                .agentId("test-agent")
                                .confidence(1.0) // GH-90000
                                .processingTime(Duration.ofMillis(5)) // GH-90000
                                .build()); // GH-90000
                    });

            runPromise(() -> dispatcher.dispatch("test-agent", "input", ctx)); // GH-90000
        }

        @Test
        @DisplayName("does not add agentReleaseId to context when no release found")
        @SuppressWarnings("unchecked")
        void doesNotAddReleaseIdWhenNoReleaseFound() { // GH-90000
            when(releaseRepository.findGoverningRelease("test-agent", "tenant-x")) // GH-90000
                    .thenReturn(Promise.of(Optional.empty())); // GH-90000

            when(delegate.dispatch(anyString(), any(), any())) // GH-90000
                    .thenAnswer(invocation -> { // GH-90000
                        AgentContext capturedCtx = invocation.getArgument(2); // GH-90000
                        assertThat(capturedCtx.getConfig("agentReleaseId")).isNull();
                        return Promise.of(AgentResult.builder() // GH-90000
                                .status(AgentResultStatus.SUCCESS) // GH-90000
                                .agentId("test-agent")
                                .confidence(1.0) // GH-90000
                                .processingTime(Duration.ofMillis(5)) // GH-90000
                                .build()); // GH-90000
                    });

            runPromise(() -> dispatcher.dispatch("test-agent", "input", ctx)); // GH-90000
        }

        @Test
        @DisplayName("delegates resolve to inner dispatcher")
        void delegatesResolve() { // GH-90000
            ExecutionTier tier = dispatcher.resolve("test-agent");
            assertThat(tier).isEqualTo(ExecutionTier.JAVA_IMPLEMENTED); // GH-90000
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
        void dispatchesActiveReleaseEndToEnd() { // GH-90000
            InMemoryAgentReleaseRepository repo = new InMemoryAgentReleaseRepository(); // GH-90000
            AgentRelease release = release("test-agent", AgentReleaseState.ACTIVE); // GH-90000
            runPromise(() -> repo.save(release)); // GH-90000

            GovernedAgentDispatcher dispatcher = new GovernedAgentDispatcher( // GH-90000
                    delegate, invariantMonitor, traceLedger, repo);

            AgentResult<?> result = runPromise(() -> dispatcher.dispatch("test-agent", "payload", ctx)); // GH-90000

            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.SUCCESS); // GH-90000
        }

        @Test
        @DisplayName("uses real trace ledger end-to-end with release guard")
        void usesRealTraceLedgerEndToEnd() { // GH-90000
            HashChainedTraceAppender ledger = new HashChainedTraceAppender(); // GH-90000
            InMemoryAgentReleaseRepository repo = new InMemoryAgentReleaseRepository(); // GH-90000
            AgentRelease release = release("test-agent", AgentReleaseState.ACTIVE); // GH-90000
            runPromise(() -> repo.save(release)); // GH-90000

            GovernedAgentDispatcher dispatcher = new GovernedAgentDispatcher( // GH-90000
                    delegate, invariantMonitor, ledger, repo);

            AgentResult<?> result = runPromise(() -> dispatcher.dispatch("test-agent", "payload", ctx)); // GH-90000

            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.SUCCESS); // GH-90000
        }

        @Test
        @DisplayName("blocks dispatch on BLOCKED state end-to-end with real ledger")
        void blocksDispatchWithRealLedger() { // GH-90000
            HashChainedTraceAppender ledger = new HashChainedTraceAppender(); // GH-90000
            InMemoryAgentReleaseRepository repo = new InMemoryAgentReleaseRepository(); // GH-90000
            AgentRelease release = release("test-agent", AgentReleaseState.BLOCKED); // GH-90000
            runPromise(() -> repo.save(release)); // GH-90000

            GovernedAgentDispatcher dispatcher = new GovernedAgentDispatcher( // GH-90000
                    delegate, invariantMonitor, ledger, repo);

            AgentResult<?> result = runPromise(() -> dispatcher.dispatch("test-agent", "payload", ctx)); // GH-90000

            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.DENIED); // GH-90000
            assertThat(result.getExplanation()).containsIgnoringCase("BLOCKED");
        }
    }

    // =========================================================================
    // TX-4: Explainability trace events (TURN_STARTED, POLICY_EVALUATED) // GH-90000
    // =========================================================================

    @Nested
    @DisplayName("TX-4 explainability trace events")
    class ExplainabilityTraceEvents {

        private final List<TraceEvent> capturedEvents = new ArrayList<>(); // GH-90000

        @BeforeEach
        void captureAppends() { // GH-90000
            capturedEvents.clear(); // GH-90000
            lenient().when(traceLedger.append(any())).thenAnswer(invocation -> { // GH-90000
                capturedEvents.add(invocation.getArgument(0)); // GH-90000
                return Promise.of(null); // GH-90000
            });
        }

        @Test
        @DisplayName("emits TURN_STARTED event before dispatch")
        void emitsTurnStarted() { // GH-90000
            GovernedAgentDispatcher dispatcher = new GovernedAgentDispatcher( // GH-90000
                    delegate, invariantMonitor, traceLedger);

            runPromise(() -> dispatcher.dispatch("test-agent", "input", ctx)); // GH-90000

            assertThat(capturedEvents) // GH-90000
                    .extracting(TraceEvent::eventType) // GH-90000
                    .contains(TraceEventType.TURN_STARTED); // GH-90000
        }

        @Test
        @DisplayName("TURN_STARTED event payload includes agentId")
        void turnStartedPayloadHasAgentId() { // GH-90000
            GovernedAgentDispatcher dispatcher = new GovernedAgentDispatcher( // GH-90000
                    delegate, invariantMonitor, traceLedger);

            runPromise(() -> dispatcher.dispatch("test-agent", "input", ctx)); // GH-90000

            TraceEvent turnStarted = capturedEvents.stream() // GH-90000
                    .filter(e -> e.eventType() == TraceEventType.TURN_STARTED) // GH-90000
                    .findFirst() // GH-90000
                    .orElseThrow(); // GH-90000
            assertThat(turnStarted.payload()).containsEntry("agentId", "test-agent"); // GH-90000
        }

        @Test
        @DisplayName("TURN_STARTED includes release metadata when release present")
        void turnStartedIncludesReleaseMetadata() { // GH-90000
            AgentRelease rel = new AgentReleaseBuilder() // GH-90000
                    .agentId("test-agent")
                    .releaseVersion("2.0.0")
                    .state(AgentReleaseState.ACTIVE) // GH-90000
                    .redactionProfileId("rp-prod")
                    .threatModelId("tm-prod")
                    .addPermittedPurpose("agent.inference")
                    .capabilityMaturityProfile("L2")
                    .policyPackId("pp-123")
                    .build(); // GH-90000

            when(releaseRepository.findGoverningRelease("test-agent", "tenant-x")) // GH-90000
                    .thenReturn(Promise.of(Optional.of(rel))); // GH-90000

            GovernedAgentDispatcher dispatcher = new GovernedAgentDispatcher( // GH-90000
                    delegate, invariantMonitor, traceLedger, releaseRepository);

            runPromise(() -> dispatcher.dispatch("test-agent", "input", ctx)); // GH-90000

            TraceEvent turnStarted = capturedEvents.stream() // GH-90000
                    .filter(e -> e.eventType() == TraceEventType.TURN_STARTED) // GH-90000
                    .findFirst() // GH-90000
                    .orElseThrow(); // GH-90000
            assertThat(turnStarted.payload()) // GH-90000
                    .containsEntry("agentReleaseId", rel.agentReleaseId()) // GH-90000
                    .containsEntry("releaseVersion", "2.0.0") // GH-90000
                    .containsEntry("redactionProfileId", "rp-prod") // GH-90000
                    .containsEntry("threatModelId", "tm-prod") // GH-90000
                    .containsEntry("capabilityMaturityProfile", "L2") // GH-90000
                    .containsEntry("policyPackId", "pp-123"); // GH-90000
        }

        @Test
        @DisplayName("emits POLICY_EVALUATED (ALLOW) when invariants pass")
        void emitsPolicyEvaluatedAllowWhenInvariantsPass() { // GH-90000
            GovernedAgentDispatcher dispatcher = new GovernedAgentDispatcher( // GH-90000
                    delegate, invariantMonitor, traceLedger);

            runPromise(() -> dispatcher.dispatch("test-agent", "input", ctx)); // GH-90000

            TraceEvent policyEval = capturedEvents.stream() // GH-90000
                    .filter(e -> e.eventType() == TraceEventType.POLICY_EVALUATED) // GH-90000
                    .findFirst() // GH-90000
                    .orElseThrow(); // GH-90000
            assertThat(policyEval.payload()).containsEntry("decision", "ALLOW"); // GH-90000
            assertThat(policyEval.payload()).containsEntry("violationCount", "0"); // GH-90000
        }

        @Test
        @DisplayName("emits POLICY_EVALUATED (DENY) and ACTION_DENIED when invariants fail")
        void emitsPolicyEvaluatedDenyThenActionDenied() { // GH-90000
            // Inject a breached cost cap invariant by setting metrics that violate
            AgentContext overBudgetCtx = AgentContext.builder() // GH-90000
                    .turnId("turn-over")
                    .agentId("test-agent")
                    .tenantId("tenant-x")
                    .memoryStore(mock(MemoryStore.class)) // GH-90000
                    .addConfig("__accumulatedCostUsd", 999.0) // GH-90000
                    .addConfig("__costCapUsd", 1.0) // GH-90000
                    .build(); // GH-90000

            GovernedAgentDispatcher dispatcher = new GovernedAgentDispatcher( // GH-90000
                    delegate, invariantMonitor, traceLedger);

            AgentResult<?> result = runPromise(() -> // GH-90000
                    dispatcher.dispatch("test-agent", "input", overBudgetCtx)); // GH-90000

            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.DENIED); // GH-90000

            List<TraceEventType> types = capturedEvents.stream() // GH-90000
                    .map(TraceEvent::eventType) // GH-90000
                    .toList(); // GH-90000
            assertThat(types).contains(TraceEventType.TURN_STARTED); // GH-90000
            assertThat(types).contains(TraceEventType.POLICY_EVALUATED); // GH-90000
            assertThat(types).contains(TraceEventType.ACTION_DENIED); // GH-90000

            TraceEvent policyEval = capturedEvents.stream() // GH-90000
                    .filter(e -> e.eventType() == TraceEventType.POLICY_EVALUATED) // GH-90000
                    .findFirst() // GH-90000
                    .orElseThrow(); // GH-90000
            assertThat(policyEval.payload()).containsEntry("decision", "DENY"); // GH-90000
        }

        @Test
        @DisplayName("TURN_STARTED is emitted before POLICY_EVALUATED in sequence")
        void turnStartedBeforePolicyEvaluated() { // GH-90000
            GovernedAgentDispatcher dispatcher = new GovernedAgentDispatcher( // GH-90000
                    delegate, invariantMonitor, traceLedger);

            runPromise(() -> dispatcher.dispatch("test-agent", "input", ctx)); // GH-90000

            List<TraceEventType> types = capturedEvents.stream() // GH-90000
                    .map(TraceEvent::eventType) // GH-90000
                    .toList(); // GH-90000
            int turnStartedIdx = types.indexOf(TraceEventType.TURN_STARTED); // GH-90000
            int policyEvaluatedIdx = types.indexOf(TraceEventType.POLICY_EVALUATED); // GH-90000
            assertThat(turnStartedIdx).isNotNegative(); // GH-90000
            assertThat(policyEvaluatedIdx).isGreaterThan(turnStartedIdx); // GH-90000
        }

        @Test
        @DisplayName("POLICY_EVALUATED (ALLOW) payload includes policyPackId when release is present")
        void policyEvaluatedAllowIncludesPolicyPackId() { // GH-90000
            AgentRelease rel = new AgentReleaseBuilder() // GH-90000
                    .agentId("test-agent")
                    .releaseVersion("1.0")
                    .state(AgentReleaseState.ACTIVE) // GH-90000
                    .redactionProfileId("rp-t")
                    .threatModelId("tm-t")
                    .addPermittedPurpose("agent.inference")
                    .capabilityMaturityProfile("L1")
                    .policyPackId("pp-456")
                    .build(); // GH-90000

            when(releaseRepository.findGoverningRelease("test-agent", "tenant-x")) // GH-90000
                    .thenReturn(Promise.of(Optional.of(rel))); // GH-90000

            GovernedAgentDispatcher dispatcher = new GovernedAgentDispatcher( // GH-90000
                    delegate, invariantMonitor, traceLedger, releaseRepository);

            runPromise(() -> dispatcher.dispatch("test-agent", "input", ctx)); // GH-90000

            TraceEvent policyEval = capturedEvents.stream() // GH-90000
                    .filter(e -> e.eventType() == TraceEventType.POLICY_EVALUATED) // GH-90000
                    .findFirst() // GH-90000
                    .orElseThrow(); // GH-90000
            assertThat(policyEval.payload()) // GH-90000
                    .containsEntry("decision", "ALLOW") // GH-90000
                    .containsEntry("policyPackId", "pp-456"); // GH-90000
        }

        @Test
        @DisplayName("ACTION_EXECUTED payload includes policyPackId when release is present")
        void actionExecutedIncludesPolicyPackId() { // GH-90000
            AgentRelease rel = new AgentReleaseBuilder() // GH-90000
                    .agentId("test-agent")
                    .releaseVersion("1.0")
                    .state(AgentReleaseState.ACTIVE) // GH-90000
                    .redactionProfileId("rp-t")
                    .threatModelId("tm-t")
                    .addPermittedPurpose("agent.inference")
                    .capabilityMaturityProfile("L1")
                    .policyPackId("pp-789")
                    .build(); // GH-90000

            when(releaseRepository.findGoverningRelease("test-agent", "tenant-x")) // GH-90000
                    .thenReturn(Promise.of(Optional.of(rel))); // GH-90000

            GovernedAgentDispatcher dispatcher = new GovernedAgentDispatcher( // GH-90000
                    delegate, invariantMonitor, traceLedger, releaseRepository);

            runPromise(() -> dispatcher.dispatch("test-agent", "input", ctx)); // GH-90000

            TraceEvent actionExecuted = capturedEvents.stream() // GH-90000
                    .filter(e -> e.eventType() == TraceEventType.ACTION_EXECUTED) // GH-90000
                    .findFirst() // GH-90000
                    .orElseThrow(); // GH-90000
            assertThat(actionExecuted.payload()).containsEntry("policyPackId", "pp-789"); // GH-90000
        }

        @Test
        @DisplayName("full happy-path event sequence is correct with real ledger")
        void fullHappyPathSequenceWithRealLedger() { // GH-90000
            HashChainedTraceAppender ledger = new HashChainedTraceAppender(); // GH-90000
            InMemoryAgentReleaseRepository repo = new InMemoryAgentReleaseRepository(); // GH-90000
            AgentRelease rel = release("test-agent", AgentReleaseState.ACTIVE); // GH-90000
            runPromise(() -> repo.save(rel)); // GH-90000

            GovernedAgentDispatcher dispatcher = new GovernedAgentDispatcher( // GH-90000
                    delegate, invariantMonitor, ledger, repo);

            runPromise(() -> dispatcher.dispatch("test-agent", "payload", ctx)); // GH-90000

            String traceId = ctx.getConfig("__traceId") != null
                    ? ctx.getConfig("__traceId").toString()
                    : null;
            // Fetch all events from ledger
            List<TraceEvent> events = runPromise(() -> // GH-90000
                    ledger.getByAgent("test-agent", "tenant-x", null, null, 100)); // GH-90000

            assertThat(events).isNotEmpty(); // GH-90000
            List<TraceEventType> types = events.stream().map(TraceEvent::eventType).toList(); // GH-90000
            assertThat(types).contains( // GH-90000
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

        private com.ghatana.agent.pluggability.AgentCapabilityManifest supervisedOnlyManifest() { // GH-90000
            // A manifest with only SUPERVISED mode — not AUTONOMOUS
            return new com.ghatana.agent.pluggability.AgentCapabilityManifest( // GH-90000
                    "test-agent", "1.0.0", "tenant-x",
                    java.util.List.of(com.ghatana.agent.pluggability.InteractionMode.SUPERVISED), // GH-90000
                    com.ghatana.agent.pluggability.SupervisionRole.SUBORDINATE,
                    com.ghatana.agent.pluggability.HandoffCapability.NONE,
                    java.util.List.of(), java.util.List.of(), java.util.Map.of()); // GH-90000
        }

        private com.ghatana.agent.pluggability.AgentCapabilityManifest autonomousManifest() { // GH-90000
            return com.ghatana.agent.pluggability.AgentCapabilityManifest.standalone("test-agent", "1.0.0", "tenant-x"); // GH-90000
        }

        @Test
        @DisplayName("AUTONOMOUS manifest allows dispatch without supervisor context")
        void autonomousManifestAllowsDispatch() { // GH-90000
            GovernedAgentDispatcher dispatcher = new GovernedAgentDispatcher( // GH-90000
                    delegate, invariantMonitor, traceLedger, null, null, autonomousManifest()); // GH-90000
            AgentResult<?> result = runPromise(() -> dispatcher.dispatch("test-agent", "input", ctx)); // GH-90000
            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.SUCCESS); // GH-90000
        }

        @Test
        @DisplayName("null manifest skips manifest check")
        void nullManifestSkipsCheck() { // GH-90000
            GovernedAgentDispatcher dispatcher = new GovernedAgentDispatcher( // GH-90000
                    delegate, invariantMonitor, traceLedger, null, null, null);
            AgentResult<?> result = runPromise(() -> dispatcher.dispatch("test-agent", "input", ctx)); // GH-90000
            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.SUCCESS); // GH-90000
        }

        @Test
        @DisplayName("SUPERVISED-only manifest with no supervisorAgentId in context → DENIED")
        void supervisedOnlyManifestWithoutSupervisorIsDenied() { // GH-90000
            GovernedAgentDispatcher dispatcher = new GovernedAgentDispatcher( // GH-90000
                    delegate, invariantMonitor, traceLedger, null, null, supervisedOnlyManifest()); // GH-90000
            AgentResult<?> result = runPromise(() -> dispatcher.dispatch("test-agent", "input", ctx)); // GH-90000
            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.DENIED); // GH-90000
        }

        @Test
        @DisplayName("SUPERVISED-only manifest with supervisorAgentId in context → allowed")
        void supervisedManifestWithSupervisorContextIsAllowed() { // GH-90000
            AgentContext supervisedCtx = ctx.toBuilder() // GH-90000
                    .addConfig("supervisorAgentId", "supervisor-001") // GH-90000
                    .build(); // GH-90000
            GovernedAgentDispatcher dispatcher = new GovernedAgentDispatcher( // GH-90000
                    delegate, invariantMonitor, traceLedger, null, null, supervisedOnlyManifest()); // GH-90000
            AgentResult<?> result = runPromise(() -> dispatcher.dispatch("test-agent", "input", supervisedCtx)); // GH-90000
            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.SUCCESS); // GH-90000
        }
    }
}
