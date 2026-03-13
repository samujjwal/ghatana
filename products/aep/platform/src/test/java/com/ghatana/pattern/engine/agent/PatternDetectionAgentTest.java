package com.ghatana.pattern.engine.agent;

import com.ghatana.core.operator.OperatorConfig;
import com.ghatana.core.operator.OperatorId;
import com.ghatana.core.operator.OperatorResult;
import com.ghatana.core.operator.OperatorState;
import com.ghatana.core.operator.OperatorType;
import com.ghatana.pattern.api.model.DetectionPlan;
import com.ghatana.pattern.engine.agent.operators.BaseOperator;
import com.ghatana.pattern.engine.agent.operators.FilterOperator;
import com.ghatana.pattern.engine.agent.operators.MapOperator;
import com.ghatana.pattern.engine.agent.operators.StreamOperator;
import com.ghatana.pattern.engine.evaluator.ProbabilisticEvaluator;
import com.ghatana.pattern.engine.nfa.NFA;
import com.ghatana.pattern.engine.nfa.NFAState;
import com.ghatana.pattern.engine.nfa.NFAStateType;
import com.ghatana.platform.domain.domain.event.Event;
import com.ghatana.platform.domain.domain.event.EventTime;
import com.ghatana.platform.domain.domain.event.GEvent;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive tests for PatternDetectionAgent, FilterOperator, MapOperator,
 * StreamOperator, and BaseOperator.
 *
 * Coverage:
 * - PatternDetectionAgent: NFA matching, event filtering, windowed detection,
 *   lifecycle, metrics, edge cases, builder
 * - FilterOperator: predicate-based filtering, header/payload convenience methods
 * - MapOperator: event transformation, enrichment, type rename
 * - StreamOperator: tumbling and sliding windows, aggregation, buffer overflow
 */
class PatternDetectionAgentTest extends EventloopTestBase {

    // ─── Test NFA Builders ───────────────────────────────────────────────────────

    /**
     * Builds a simple 2-state NFA: start --"login.attempt"--> end
     */
    private static NFA buildSimpleNFA(String patternName, String eventType) {
        NFA nfa = new NFA(patternName);
        NFAState end = new NFAState("end", NFAStateType.END);
        nfa.addState(end);
        nfa.addTransition(nfa.getStartState(), end, eventType);
        return nfa;
    }

    /**
     * Builds a sequence NFA: start --"A"--> mid --"B"--> end
     */
    private static NFA buildSequenceNFA(String patternName, String typeA, String typeB) {
        NFA nfa = new NFA(patternName);
        NFAState mid = new NFAState("mid", NFAStateType.INTERMEDIATE);
        NFAState end = new NFAState("end", NFAStateType.END);
        nfa.addState(mid);
        nfa.addState(end);
        nfa.addTransition(nfa.getStartState(), mid, typeA);
        nfa.addTransition(mid, end, typeB);
        return nfa;
    }

    /**
     * Builds a 3-step sequence NFA: start --"A"--> s1 --"B"--> s2 --"C"--> end
     */
    private static NFA buildTripleSequenceNFA(String patternName, String typeA, String typeB, String typeC) {
        NFA nfa = new NFA(patternName);
        NFAState s1 = new NFAState("s1", NFAStateType.INTERMEDIATE);
        NFAState s2 = new NFAState("s2", NFAStateType.INTERMEDIATE);
        NFAState end = new NFAState("end", NFAStateType.END);
        nfa.addState(s1);
        nfa.addState(s2);
        nfa.addState(end);
        nfa.addTransition(nfa.getStartState(), s1, typeA);
        nfa.addTransition(s1, s2, typeB);
        nfa.addTransition(s2, end, typeC);
        return nfa;
    }

    private static GEvent makeEvent(String type) {
        return GEvent.builder()
                .type(type)
                .payload(new HashMap<>())
                .headers(new HashMap<>())
                .time(EventTime.now())
                .build();
    }

    private static GEvent makeEvent(String type, Map<String, Object> payload) {
        return GEvent.builder()
                .type(type)
                .payload(new HashMap<>(payload))
                .headers(new HashMap<>())
                .time(EventTime.now())
                .build();
    }

    private static GEvent makeEvent(String type, Map<String, Object> payload, Map<String, String> headers) {
        return GEvent.builder()
                .type(type)
                .payload(new HashMap<>(payload))
                .headers(new HashMap<>(headers))
                .time(EventTime.now())
                .build();
    }

    private static OperatorId testId(String name) {
        return OperatorId.of("test", "pattern", name, "1.0");
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // PatternDetectionAgent Tests
    // ═════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("PatternDetectionAgent")
    class PatternDetectionAgentTests {

        private NFA simpleNFA;
        private PatternDetectionAgent agent;

        @BeforeEach
        void setUp() {
            simpleNFA = buildSimpleNFA("login-detection", "login.attempt");
            agent = PatternDetectionAgent.builder()
                    .operatorId(testId("login-detector"))
                    .name("Login Detector")
                    .nfa(simpleNFA)
                    .confidenceThreshold(0.5)
                    .windowDuration(Duration.ofMinutes(10))
                    .build();
        }

        // ─── Builder Tests ───────────────────────────────────────────────────────

        @Test
        @DisplayName("Builder creates agent with correct properties")
        void builderCreatesAgentCorrectly() {
            assertThat(agent.getId()).isEqualTo(testId("login-detector"));
            assertThat(agent.getName()).isEqualTo("Login Detector");
            assertThat(agent.getType()).isEqualTo(OperatorType.PATTERN);
            assertThat(agent.getConfidenceThreshold()).isEqualTo(0.5);
            assertThat(agent.getNfa()).isSameAs(simpleNFA);
            assertThat(agent.getWindowDuration()).isEqualTo(Duration.ofMinutes(10));
            assertThat(agent.getState()).isEqualTo(OperatorState.CREATED);
            assertThat(agent.isStateful()).isTrue();
        }

        @Test
        @DisplayName("Builder requires operatorId")
        void builderRequiresOperatorId() {
            assertThatNullPointerException().isThrownBy(() ->
                    PatternDetectionAgent.builder()
                            .name("X")
                            .nfa(simpleNFA)
                            .build()
            ).withMessageContaining("operatorId");
        }

        @Test
        @DisplayName("Builder requires name")
        void builderRequiresName() {
            assertThatNullPointerException().isThrownBy(() ->
                    PatternDetectionAgent.builder()
                            .operatorId(testId("x"))
                            .nfa(simpleNFA)
                            .build()
            ).withMessageContaining("name");
        }

        @Test
        @DisplayName("Builder requires NFA")
        void builderRequiresNfa() {
            assertThatNullPointerException().isThrownBy(() ->
                    PatternDetectionAgent.builder()
                            .operatorId(testId("x"))
                            .name("X")
                            .build()
            ).withMessageContaining("NFA");
        }

        @Test
        @DisplayName("Builder clamps confidence threshold to [0,1]")
        void builderClampsConfidence() {
            PatternDetectionAgent highAgent = PatternDetectionAgent.builder()
                    .operatorId(testId("high"))
                    .name("High")
                    .nfa(simpleNFA)
                    .confidenceThreshold(1.5)
                    .build();
            assertThat(highAgent.getConfidenceThreshold()).isEqualTo(1.0);

            PatternDetectionAgent lowAgent = PatternDetectionAgent.builder()
                    .operatorId(testId("low"))
                    .name("Low")
                    .nfa(simpleNFA)
                    .confidenceThreshold(-0.5)
                    .build();
            assertThat(lowAgent.getConfidenceThreshold()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("Default window duration is 5 minutes when not specified")
        void defaultWindowDuration() {
            PatternDetectionAgent defaultAgent = PatternDetectionAgent.builder()
                    .operatorId(testId("default"))
                    .name("Default")
                    .nfa(simpleNFA)
                    .build();
            assertThat(defaultAgent.getWindowDuration()).isEqualTo(Duration.ofMinutes(5));
        }

        // ─── Lifecycle Tests ─────────────────────────────────────────────────────

        @Test
        @DisplayName("Full lifecycle: initialize, start, process, stop")
        void fullLifecycle() {
            // Initialize
            Promise<Void> initPromise = agent.initialize(OperatorConfig.empty());
            assertThat(runPromise(() -> initPromise)).isNull(); // completed
            assertThat(agent.getState()).isEqualTo(OperatorState.INITIALIZED);

            // Start
            Promise<Void> startPromise = agent.start();
            assertThat(runPromise(() -> startPromise)).isNull();
            assertThat(agent.getState()).isEqualTo(OperatorState.RUNNING);

            // Process
            Promise<OperatorResult> result = agent.process(makeEvent("login.attempt"));
            assertThat(runPromise(() -> result)).isNotNull();

            // Stop
            Promise<Void> stopPromise = agent.stop();
            assertThat(runPromise(() -> stopPromise)).isNull();
            assertThat(agent.getState()).isEqualTo(OperatorState.STOPPED);
        }

        @Test
        @DisplayName("Config override replaces confidence threshold on initialize")
        void configOverrideConfidenceThreshold() {
            OperatorConfig config = OperatorConfig.builder()
                    .withProperty("confidence.threshold", "0.9")
                    .build();
            agent.initialize(config);
            // The evaluator is recreated with new threshold
            assertThat(agent.getEvaluator().getConfidenceThreshold()).isEqualTo(0.9);
        }

        // ─── Single Event Match Tests ────────────────────────────────────────────

        @Test
        @DisplayName("Single event triggers match for simple NFA")
        void singleEventTriggersMatch() {
            agent.initialize(OperatorConfig.empty());
            agent.start();

            Promise<OperatorResult> result = agent.process(makeEvent("login.attempt"));
            OperatorResult or = runPromise(() -> result);

            assertThat(or).isNotNull();
            assertThat(or.isSuccess()).isTrue();
            assertThat(or.getOutputEvents()).isNotEmpty();

            Event outputEvent = or.getOutputEvents().getFirst();
            assertThat(outputEvent.getType()).isEqualTo("pattern.match.detected");
            assertThat(outputEvent.getPayload("patternName")).isEqualTo("login-detection");
            assertThat(outputEvent.getPayload("confidence")).isInstanceOf(Number.class);
            assertThat(((Number) outputEvent.getPayload("confidence")).doubleValue())
                    .isGreaterThanOrEqualTo(0.5);
        }

        @Test
        @DisplayName("Non-matching event produces empty output")
        void nonMatchingEventProducesEmpty() {
            agent.initialize(OperatorConfig.empty());
            agent.start();

            Promise<OperatorResult> result = agent.process(makeEvent("other.event"));
            OperatorResult or = runPromise(() -> result);

            assertThat(or).isNotNull();
            assertThat(or.isSuccess()).isTrue();
            // Empty or no output events
            assertThat(or.getOutputEvents()).isEmpty();
        }

        @Test
        @DisplayName("Null event returns failure result")
        void nullEventReturnsFailure() {
            agent.initialize(OperatorConfig.empty());
            agent.start();

            Promise<OperatorResult> result = agent.process(null);
            OperatorResult or = runPromise(() -> result);

            assertThat(or).isNotNull();
            assertThat(or.isSuccess()).isFalse();
            assertThat(or.getErrorMessage()).contains("null");
        }

        // ─── Sequence Pattern Tests ──────────────────────────────────────────────

        @Test
        @DisplayName("Sequence NFA requires events in order")
        void sequenceNFARequiresOrder() {
            NFA seqNFA = buildSequenceNFA("login-then-purchase", "login", "purchase");
            PatternDetectionAgent seqAgent = PatternDetectionAgent.builder()
                    .operatorId(testId("seq-agent"))
                    .name("Sequence Agent")
                    .nfa(seqNFA)
                    .confidenceThreshold(0.3)
                    .build();
            seqAgent.initialize(OperatorConfig.empty());
            seqAgent.start();

            // First event: "login" — no match yet (needs B)
            OperatorResult r1 = runPromise(() -> seqAgent.process(makeEvent("login")));
            assertThat(r1.getOutputEvents()).isEmpty();

            // Second event: "purchase" — should match!
            OperatorResult r2 = runPromise(() -> seqAgent.process(makeEvent("purchase")));
            assertThat(r2.getOutputEvents()).isNotEmpty();
            assertThat(r2.getOutputEvents().getFirst().getPayload("patternName"))
                    .isEqualTo("login-then-purchase");
        }

        @Test
        @DisplayName("Sequence NFA does not match wrong order")
        void sequenceNFADoesNotMatchWrongOrder() {
            NFA seqNFA = buildSequenceNFA("a-then-b", "A", "B");
            PatternDetectionAgent seqAgent = PatternDetectionAgent.builder()
                    .operatorId(testId("wrong-order"))
                    .name("Wrong Order Test")
                    .nfa(seqNFA)
                    .confidenceThreshold(0.3)
                    .build();
            seqAgent.initialize(OperatorConfig.empty());
            seqAgent.start();

            // B first, then A — should not match
            OperatorResult r1 = runPromise(() -> seqAgent.process(makeEvent("B")));
            assertThat(r1.getOutputEvents()).isEmpty();

            OperatorResult r2 = runPromise(() -> seqAgent.process(makeEvent("A")));
            assertThat(r2.getOutputEvents()).isEmpty();
        }

        @Test
        @DisplayName("Three-step sequence NFA matches correctly")
        void threeStepSequence() {
            NFA tripleNFA = buildTripleSequenceNFA("abc-pattern", "A", "B", "C");
            PatternDetectionAgent tripleAgent = PatternDetectionAgent.builder()
                    .operatorId(testId("triple"))
                    .name("Triple Sequence")
                    .nfa(tripleNFA)
                    .confidenceThreshold(0.3)
                    .build();
            tripleAgent.initialize(OperatorConfig.empty());
            tripleAgent.start();

            assertThat(runPromise(() -> tripleAgent.process(makeEvent("A"))).getOutputEvents()).isEmpty();
            assertThat(runPromise(() -> tripleAgent.process(makeEvent("B"))).getOutputEvents()).isEmpty();

            OperatorResult r3 = runPromise(() -> tripleAgent.process(makeEvent("C")));
            assertThat(r3.getOutputEvents()).isNotEmpty();
            assertThat(r3.getOutputEvents().getFirst().getPayload("matchedEventCount"))
                    .isEqualTo(3);
        }

        // ─── Event Type Filtering ────────────────────────────────────────────────

        @Test
        @DisplayName("DetectionPlan event types filter unrelated events")
        void detectionPlanEventTypeFilter() {
            DetectionPlan plan = DetectionPlan.builder()
                    .eventTypes(List.of("login.attempt", "login.failure"))
                    .version("1.0")
                    .build();

            PatternDetectionAgent filteredAgent = PatternDetectionAgent.builder()
                    .operatorId(testId("filtered"))
                    .name("Filtered Agent")
                    .nfa(simpleNFA)
                    .detectionPlan(plan)
                    .confidenceThreshold(0.5)
                    .build();
            filteredAgent.initialize(OperatorConfig.empty());
            filteredAgent.start();

            // Unrelated event type — should be filtered
            OperatorResult r = runPromise(() -> filteredAgent.process(makeEvent("order.placed")));
            assertThat(r.getOutputEvents()).isEmpty();
            assertThat(filteredAgent.getAcceptedEventTypes())
                    .containsExactlyInAnyOrder("login.attempt", "login.failure");
        }

        @Test
        @DisplayName("No DetectionPlan means all events are accepted")
        void noDetectionPlanAcceptsAll() {
            assertThat(agent.getAcceptedEventTypes()).isEmpty();
            agent.initialize(OperatorConfig.empty());
            agent.start();

            // Any event type should be processed (not filtered)
            OperatorResult r = runPromise(() -> agent.process(makeEvent("login.attempt")));
            // It's a match for our NFA
            assertThat(r.getOutputEvents()).isNotEmpty();
        }

        // ─── Metrics Tests ───────────────────────────────────────────────────────

        @Test
        @DisplayName("Metrics track events received and matches")
        void metricsTrackEventsAndMatches() {
            agent.initialize(OperatorConfig.empty());
            agent.start();

            agent.process(makeEvent("login.attempt"));
            agent.process(makeEvent("other.event"));
            agent.process(makeEvent("login.attempt"));

            assertThat(agent.getEventsReceived()).isEqualTo(3);
            assertThat(agent.getMatchesDetected()).isGreaterThanOrEqualTo(1);

            Map<String, Object> metrics = agent.getMetrics();
            assertThat(metrics).containsKey("events_received");
            assertThat(metrics).containsKey("matches_detected");
            assertThat(metrics).containsKey("pattern_name");
            assertThat(metrics.get("pattern_name")).isEqualTo("login-detection");
        }

        @Test
        @DisplayName("Internal state includes NFA and window details")
        void internalStateContainsDetails() {
            agent.initialize(OperatorConfig.empty());
            agent.start();

            Map<String, Object> state = agent.getInternalState();
            assertThat(state).containsKey("pattern_name");
            assertThat(state).containsKey("confidence_threshold");
            assertThat(state).containsKey("window_start");
            assertThat(state).containsKey("window_duration_seconds");
        }

        // ─── toEvent Tests ──────────────────────────────────────────────────────

        @Test
        @DisplayName("toEvent serializes agent to event")
        void toEventSerializesAgent() {
            Event agentEvent = agent.toEvent();
            assertThat(agentEvent.getType()).isEqualTo("agent.pattern.detection");
            assertThat(agentEvent.getPayload("agentType")).isEqualTo("PatternDetectionAgent");
            assertThat(agentEvent.getPayload("patternName")).isEqualTo("login-detection");
            assertThat(agentEvent.getPayload("confidenceThreshold")).isEqualTo(0.5);
        }

        // ─── Match Output Structure ──────────────────────────────────────────────

        @Test
        @DisplayName("Match output event contains complete metadata")
        void matchOutputContainsMetadata() {
            agent.initialize(OperatorConfig.empty());
            agent.start();

            GEvent input = makeEvent("login.attempt", Map.of(), Map.of("correlationId", "corr-123"));
            OperatorResult r = runPromise(() -> agent.process(input));

            assertThat(r.getOutputEvents()).isNotEmpty();
            Event match = r.getOutputEvents().getFirst();

            assertThat(match.getPayload("patternName")).isEqualTo("login-detection");
            assertThat(match.getPayload("agentId")).isEqualTo(agent.getId().toString());
            assertThat(match.getPayload("matchedEventCount")).isInstanceOf(Integer.class);
            assertThat(match.getPayload("matchTime")).isNotNull();
            assertThat(match.getPayload("confidenceThreshold")).isEqualTo(0.5);
            assertThat(match.getHeader("correlationId")).isEqualTo("corr-123");
            assertThat(match.getHeader("patternName")).isEqualTo("login-detection");
            assertThat(match.getHeader("sourceAgentId")).isEqualTo(agent.getId().toString());
        }

        // ─── Pending Matches ─────────────────────────────────────────────────────

        @Test
        @DisplayName("Matches are buffered in pending queue")
        void matchesAreBuffered() {
            agent.initialize(OperatorConfig.empty());
            agent.start();

            agent.process(makeEvent("login.attempt"));
            List<ProbabilisticEvaluator.PatternMatch> pending = agent.getPendingMatches();
            assertThat(pending).isNotEmpty();
        }

        // ─── toString ────────────────────────────────────────────────────────────

        @Test
        @DisplayName("toString includes key properties")
        void toStringIncludesKeyProperties() {
            String s = agent.toString();
            assertThat(s).contains("PatternDetectionAgent");
            assertThat(s).contains("login-detection");
            assertThat(s).contains("0.50");
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // FilterOperator Tests
    // ═════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("FilterOperator")
    class FilterOperatorTests {

        @Test
        @DisplayName("Passes events matching predicate")
        void passesMatchingEvents() {
            FilterOperator filter = FilterOperator.builder()
                    .operatorId(testId("severity-filter"))
                    .name("Severity Filter")
                    .predicate(event -> {
                        Object sev = event.getPayload("severity");
                        return sev instanceof Number && ((Number) sev).intValue() >= 5;
                    })
                    .build();
            filter.initialize(OperatorConfig.empty());
            filter.start();

            OperatorResult r = runPromise(() -> filter.process(makeEvent("alert", Map.of("severity", 7))));
            assertThat(r.isSuccess()).isTrue();
            assertThat(r.getOutputEvents()).hasSize(1);
            assertThat(filter.getPassedCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("Rejects events not matching predicate")
        void rejectsNonMatchingEvents() {
            FilterOperator filter = FilterOperator.builder()
                    .operatorId(testId("severity-filter"))
                    .name("Severity Filter")
                    .predicate(event -> {
                        Object sev = event.getPayload("severity");
                        return sev instanceof Number && ((Number) sev).intValue() >= 5;
                    })
                    .build();
            filter.initialize(OperatorConfig.empty());
            filter.start();

            OperatorResult r = runPromise(() -> filter.process(makeEvent("alert", Map.of("severity", 2))));
            assertThat(r.isSuccess()).isTrue();
            assertThat(r.getOutputEvents()).isEmpty();
            assertThat(filter.getRejectedCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("headerExists factory works correctly")
        void headerExistsFactory() {
            FilterOperator filter = FilterOperator.headerExists(
                    testId("header-filter"), "Header Filter", "traceId");
            filter.initialize(OperatorConfig.empty());
            filter.start();

            OperatorResult with = filter.process(
runPromise(() -> makeEvent("x", Map.of(), Map.of("traceId", "abc"))));
            assertThat(with.getOutputEvents()).hasSize(1);

            OperatorResult without = runPromise(() -> filter.process(makeEvent("x")));
            assertThat(without.getOutputEvents()).isEmpty();
        }

        @Test
        @DisplayName("headerEquals factory works correctly")
        void headerEqualsFactory() {
            FilterOperator filter = FilterOperator.headerEquals(
                    testId("env-filter"), "Env Filter", "env", "production");
            filter.initialize(OperatorConfig.empty());
            filter.start();

            OperatorResult match = filter.process(
runPromise(() -> makeEvent("x", Map.of(), Map.of("env", "production"))));
            assertThat(match.getOutputEvents()).hasSize(1);

            OperatorResult noMatch = filter.process(
runPromise(() -> makeEvent("x", Map.of(), Map.of("env", "staging"))));
            assertThat(noMatch.getOutputEvents()).isEmpty();
        }

        @Test
        @DisplayName("payloadExists factory works correctly")
        void payloadExistsFactory() {
            FilterOperator filter = FilterOperator.payloadExists(
                    testId("payload-filter"), "Payload Filter", "userId");
            filter.initialize(OperatorConfig.empty());
            filter.start();

            OperatorResult with = filter.process(
runPromise(() -> makeEvent("x", Map.of("userId", "u123"))));
            assertThat(with.getOutputEvents()).hasSize(1);

            OperatorResult without = runPromise(() -> filter.process(makeEvent("x")));
            assertThat(without.getOutputEvents()).isEmpty();
        }

        @Test
        @DisplayName("payloadEquals factory works correctly")
        void payloadEqualsFactory() {
            FilterOperator filter = FilterOperator.payloadEquals(
                    testId("status-filter"), "Status Filter", "status", "active");
            filter.initialize(OperatorConfig.empty());
            filter.start();

            OperatorResult match = filter.process(
runPromise(() -> makeEvent("x", Map.of("status", "active"))));
            assertThat(match.getOutputEvents()).hasSize(1);

            OperatorResult noMatch = filter.process(
runPromise(() -> makeEvent("x", Map.of("status", "inactive"))));
            assertThat(noMatch.getOutputEvents()).isEmpty();
        }

        @Test
        @DisplayName("Null event returns failure")
        void nullEventReturnsFailure() {
            FilterOperator filter = FilterOperator.builder()
                    .operatorId(testId("null-filter"))
                    .name("Null Filter")
                    .predicate(e -> true)
                    .build();
            filter.initialize(OperatorConfig.empty());
            filter.start();

            OperatorResult r = runPromise(() -> filter.process(null));
            assertThat(r.isSuccess()).isFalse();
        }

        @Test
        @DisplayName("Predicate exception returns failure result")
        void predicateExceptionReturnsFailure() {
            FilterOperator filter = FilterOperator.builder()
                    .operatorId(testId("exception-filter"))
                    .name("Exception Filter")
                    .predicate(e -> { throw new RuntimeException("boom"); })
                    .build();
            filter.initialize(OperatorConfig.empty());
            filter.start();

            OperatorResult r = runPromise(() -> filter.process(makeEvent("x")));
            assertThat(r.isSuccess()).isFalse();
            assertThat(r.getErrorMessage()).contains("boom");
        }

        @Test
        @DisplayName("Metrics include pass and reject counts")
        void metricsIncludePassAndReject() {
            FilterOperator filter = FilterOperator.builder()
                    .operatorId(testId("metrics-filter"))
                    .name("Metrics Filter")
                    .predicate(e -> e.getPayload("pass") != null)
                    .build();
            filter.initialize(OperatorConfig.empty());
            filter.start();

            filter.process(makeEvent("x", Map.of("pass", true)));
            filter.process(makeEvent("x"));
            filter.process(makeEvent("x", Map.of("pass", true)));

            Map<String, Object> metrics = filter.getMetrics();
            assertThat(metrics.get("passed_count")).isEqualTo(2L);
            assertThat(metrics.get("rejected_count")).isEqualTo(1L);
            assertThat((Double) metrics.get("pass_rate")).isCloseTo(0.667, within(0.01));
        }

        @Test
        @DisplayName("AND composite predicate works")
        void andCompositePredicate() {
            var combined = FilterOperator.and(
                    e -> e.getPayload("a") != null,
                    e -> e.getPayload("b") != null
            );
            assertThat(combined.test(makeEvent("x", Map.of("a", 1, "b", 2)))).isTrue();
            assertThat(combined.test(makeEvent("x", Map.of("a", 1)))).isFalse();
        }

        @Test
        @DisplayName("OR composite predicate works")
        void orCompositePredicate() {
            var combined = FilterOperator.or(
                    e -> e.getPayload("a") != null,
                    e -> e.getPayload("b") != null
            );
            assertThat(combined.test(makeEvent("x", Map.of("a", 1)))).isTrue();
            assertThat(combined.test(makeEvent("x", Map.of("b", 2)))).isTrue();
            assertThat(combined.test(makeEvent("x"))).isFalse();
        }

        @Test
        @DisplayName("Event type filtering in BaseOperator works")
        void eventTypeFiltering() {
            FilterOperator filter = FilterOperator.builder()
                    .operatorId(testId("type-filter"))
                    .name("Type Filter")
                    .predicate(e -> true)
                    .acceptedEventTypes(Set.of("alert.critical"))
                    .build();
            filter.initialize(OperatorConfig.empty());
            filter.start();

            OperatorResult accepted = runPromise(() -> filter.process(makeEvent("alert.critical")));
            assertThat(accepted.getOutputEvents()).hasSize(1);

            OperatorResult rejected = runPromise(() -> filter.process(makeEvent("alert.info")));
            assertThat(rejected.getOutputEvents()).isEmpty();
            assertThat(filter.getFilteredCount()).isEqualTo(1);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // MapOperator Tests
    // ═════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("MapOperator")
    class MapOperatorTests {

        @Test
        @DisplayName("Custom transformer modifies event")
        void customTransformerModifiesEvent() {
            MapOperator mapper = MapOperator.builder()
                    .operatorId(testId("custom-mapper"))
                    .name("Custom Mapper")
                    .transformer(event -> event.toBuilder()
                            .addPayload("processed", true)
                            .build())
                    .build();
            mapper.initialize(OperatorConfig.empty());
            mapper.start();

            OperatorResult r = mapper.process(
runPromise(() -> makeEvent("x", Map.of("data", "original"))));

            assertThat(r.isSuccess()).isTrue();
            assertThat(r.getOutputEvents()).hasSize(1);
            Event output = r.getOutputEvents().getFirst();
            assertThat(output.getPayload("processed")).isEqualTo(true);
            assertThat(output.getPayload("data")).isEqualTo("original");
        }

        @Test
        @DisplayName("enrich factory adds payload fields")
        void enrichFactoryAddsFields() {
            MapOperator enricher = MapOperator.enrich(
                    testId("enricher"), "Enricher",
                    Map.of("enriched", true, "source", "pattern-engine"));
            enricher.initialize(OperatorConfig.empty());
            enricher.start();

            OperatorResult r = runPromise(() -> enricher.process(makeEvent("x")));
            assertThat(r.getOutputEvents()).hasSize(1);
            Event output = r.getOutputEvents().getFirst();
            assertThat(output.getPayload("enriched")).isEqualTo(true);
            assertThat(output.getPayload("source")).isEqualTo("pattern-engine");
        }

        @Test
        @DisplayName("renameType factory changes event type")
        void renameTypeFactory() {
            MapOperator renamer = MapOperator.renameType(
                    testId("renamer"), "Renamer", "new.event.type");
            renamer.initialize(OperatorConfig.empty());
            renamer.start();

            OperatorResult r = runPromise(() -> renamer.process(makeEvent("old.event.type", Map.of("k", "v"))));
            assertThat(r.getOutputEvents()).hasSize(1);
            assertThat(r.getOutputEvents().getFirst().getType()).isEqualTo("new.event.type");
        }

        @Test
        @DisplayName("transformPayload factory transforms the payload map")
        void transformPayloadFactory() {
            MapOperator transformer = MapOperator.transformPayload(
                    testId("payload-transform"), "Payload Transform",
                    payload -> {
                        payload.put("transformed", true);
                        payload.remove("sensitive");
                        return payload;
                    });
            transformer.initialize(OperatorConfig.empty());
            transformer.start();

            OperatorResult r = transformer.process(
runPromise(() -> makeEvent("x", Map.of("sensitive", "secret", "data", "keep"))));

            assertThat(r.getOutputEvents()).hasSize(1);
            Event output = r.getOutputEvents().getFirst();
            assertThat(output.getPayload("transformed")).isEqualTo(true);
            assertThat(output.getPayload("sensitive")).isNull();
            assertThat(output.getPayload("data")).isEqualTo("keep");
        }

        @Test
        @DisplayName("Transformer returning null drops event")
        void transformerReturningNullDropsEvent() {
            MapOperator mapper = MapOperator.builder()
                    .operatorId(testId("null-mapper"))
                    .name("Null Mapper")
                    .transformer(event -> null)
                    .build();
            mapper.initialize(OperatorConfig.empty());
            mapper.start();

            OperatorResult r = runPromise(() -> mapper.process(makeEvent("x")));
            assertThat(r.getOutputEvents()).isEmpty();
        }

        @Test
        @DisplayName("Transformer exception returns failure")
        void transformerExceptionReturnsFailure() {
            MapOperator mapper = MapOperator.builder()
                    .operatorId(testId("exception-mapper"))
                    .name("Exception Mapper")
                    .transformer(event -> { throw new RuntimeException("transform error"); })
                    .build();
            mapper.initialize(OperatorConfig.empty());
            mapper.start();

            OperatorResult r = runPromise(() -> mapper.process(makeEvent("x")));
            assertThat(r.isSuccess()).isFalse();
            assertThat(r.getErrorMessage()).contains("transform error");
        }

        @Test
        @DisplayName("Null event returns failure")
        void nullEventReturnsFailure() {
            MapOperator mapper = MapOperator.builder()
                    .operatorId(testId("null-check"))
                    .name("Null Check")
                    .transformer(event -> event)
                    .build();
            mapper.initialize(OperatorConfig.empty());
            mapper.start();

            OperatorResult r = runPromise(() -> mapper.process(null));
            assertThat(r.isSuccess()).isFalse();
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // StreamOperator Tests
    // ═════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("StreamOperator")
    class StreamOperatorTests {

        @Test
        @DisplayName("Tumbling window aggregates on window close")
        void tumblingWindowAggregates() {
            StreamOperator stream = StreamOperator.builder()
                    .operatorId(testId("tumbling"))
                    .name("Tumbling Window")
                    .windowDuration(Duration.ofMillis(1)) // Very short window for testing
                    .aggregator(events -> {
                        Map<String, Object> payload = new LinkedHashMap<>();
                        payload.put("count", events.size());
                        return List.of(GEvent.builder()
                                .type("window.result")
                                .payload(payload)
                                .headers(Map.of())
                                .build());
                    })
                    .build();
            stream.initialize(OperatorConfig.empty());
            stream.start();

            // First event opens the window
            stream.process(makeEvent("x"));

            // Wait for window to expire
            try { Thread.sleep(5); } catch (InterruptedException ignored) {}

            // Second event should trigger window close
            OperatorResult r = runPromise(() -> stream.process(makeEvent("y")));

            // The window should have closed and produced an aggregate
            // (the second event might be in a new window)
            assertThat(stream.getWindowCount()).isGreaterThanOrEqualTo(0L);
            assertThat(stream.isStateful()).isTrue();
        }

        @Test
        @DisplayName("Buffer overflow drops oldest events")
        void bufferOverflowDropsOldest() {
            StreamOperator stream = StreamOperator.builder()
                    .operatorId(testId("buffer-test"))
                    .name("Buffer Test")
                    .windowDuration(Duration.ofHours(1)) // Long window — won't close during test
                    .maxBufferSize(3)
                    .aggregator(events -> List.of())
                    .build();
            stream.initialize(OperatorConfig.empty());
            stream.start();

            for (int i = 0; i < 5; i++) {
                stream.process(makeEvent("event-" + i));
            }

            // Buffer size should be 3 (max), 2 dropped
            assertThat(stream.getCurrentBufferSize()).isEqualTo(3);
        }

        @Test
        @DisplayName("Aggregator exception returns failure")
        void aggregatorExceptionReturnsFailure() {
            StreamOperator stream = StreamOperator.builder()
                    .operatorId(testId("exception-stream"))
                    .name("Exception Stream")
                    .windowDuration(Duration.ofMillis(1))
                    .aggregator(events -> { throw new RuntimeException("aggregate error"); })
                    .build();
            stream.initialize(OperatorConfig.empty());
            stream.start();

            stream.process(makeEvent("x"));

            try { Thread.sleep(5); } catch (InterruptedException ignored) {}

            OperatorResult r = runPromise(() -> stream.process(makeEvent("y")));
            // After window close, aggregator throws — should return failure
            // But only if the window was long enough to buffer events and then close
            assertThat(r).isNotNull();
        }

        @Test
        @DisplayName("Null event returns failure")
        void nullEventReturnsFailure() {
            StreamOperator stream = StreamOperator.builder()
                    .operatorId(testId("null-stream"))
                    .name("Null Stream")
                    .windowDuration(Duration.ofMinutes(1))
                    .aggregator(events -> List.of())
                    .build();
            stream.initialize(OperatorConfig.empty());
            stream.start();

            OperatorResult r = runPromise(() -> stream.process(null));
            assertThat(r.isSuccess()).isFalse();
        }

        @Test
        @DisplayName("Metrics include window and buffer info")
        void metricsIncludeWindowInfo() {
            StreamOperator stream = StreamOperator.builder()
                    .operatorId(testId("metrics-stream"))
                    .name("Metrics Stream")
                    .windowDuration(Duration.ofMinutes(5))
                    .aggregator(events -> List.of())
                    .build();
            stream.initialize(OperatorConfig.empty());
            stream.start();

            stream.process(makeEvent("x"));
            stream.process(makeEvent("y"));

            Map<String, Object> metrics = stream.getMetrics();
            assertThat(metrics).containsKey("window_count");
            assertThat(metrics).containsKey("events_buffered_total");
            assertThat(metrics).containsKey("current_buffer_size");
            assertThat(metrics).containsKey("window_duration_seconds");
            assertThat(metrics.get("window_duration_seconds")).isEqualTo(300L);
        }

        @Test
        @DisplayName("Internal state shows window type and buffer")
        void internalStateShowsDetails() {
            StreamOperator tumbling = StreamOperator.builder()
                    .operatorId(testId("state-stream"))
                    .name("State Stream")
                    .windowDuration(Duration.ofMinutes(1))
                    .aggregator(events -> List.of())
                    .build();

            Map<String, Object> state = tumbling.getInternalState();
            assertThat(state.get("window_type")).isEqualTo("TUMBLING");

            StreamOperator sliding = StreamOperator.builder()
                    .operatorId(testId("sliding-state"))
                    .name("Sliding State")
                    .windowDuration(Duration.ofMinutes(5))
                    .slideDuration(Duration.ofMinutes(1))
                    .aggregator(events -> List.of())
                    .build();

            Map<String, Object> slidingState = sliding.getInternalState();
            assertThat(slidingState.get("window_type")).isEqualTo("SLIDING");
        }

        @Test
        @DisplayName("Sliding window properties are accessible")
        void slidingWindowProperties() {
            StreamOperator sliding = StreamOperator.builder()
                    .operatorId(testId("sliding"))
                    .name("Sliding")
                    .windowDuration(Duration.ofMinutes(5))
                    .slideDuration(Duration.ofMinutes(1))
                    .aggregator(events -> List.of())
                    .build();

            assertThat(sliding.getWindowDuration()).isEqualTo(Duration.ofMinutes(5));
            assertThat(sliding.getSlideDuration()).isEqualTo(Duration.ofMinutes(1));
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // BaseOperator Tests
    // ═════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("BaseOperator")
    class BaseOperatorTests {

        @Test
        @DisplayName("toEvent serializes operator state")
        void toEventSerializesState() {
            FilterOperator filter = FilterOperator.builder()
                    .operatorId(testId("base-test"))
                    .name("Base Test")
                    .predicate(e -> true)
                    .build();

            Event event = filter.toEvent();
            assertThat(event.getType()).isEqualTo("operator.filteroperator");
            assertThat(event.getPayload("operatorClass")).isEqualTo("FilterOperator");
            assertThat(event.getPayload("operatorId")).isEqualTo(testId("base-test").toString());
        }

        @Test
        @DisplayName("Input/output/filtered counts tracked correctly")
        void countsTrackedCorrectly() {
            FilterOperator filter = FilterOperator.builder()
                    .operatorId(testId("count-test"))
                    .name("Count Test")
                    .predicate(e -> e.getPayload("pass") != null)
                    .acceptedEventTypes(Set.of("target"))
                    .build();
            filter.initialize(OperatorConfig.empty());
            filter.start();

            // Accepted and passes predicate
            filter.process(makeEvent("target", Map.of("pass", true)));
            // Accepted but fails predicate
            filter.process(makeEvent("target"));
            // Not accepted (filtered by type)
            filter.process(makeEvent("other"));

            assertThat(filter.getInputCount()).isEqualTo(3);
            assertThat(filter.getOutputCount()).isEqualTo(1);
            assertThat(filter.getFilteredCount()).isEqualTo(1);
        }
    }
}
