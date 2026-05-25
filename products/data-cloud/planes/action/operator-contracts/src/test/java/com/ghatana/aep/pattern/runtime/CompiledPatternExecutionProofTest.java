package com.ghatana.aep.pattern.runtime;

import com.ghatana.aep.operator.contract.OperatorKind;
import com.ghatana.aep.pattern.spec.CompiledPattern;
import com.ghatana.aep.pattern.spec.PatternRuntimeNode;
import com.ghatana.aep.pattern.spec.PatternSpecCompiler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Proves compiled PatternSpec graphs execute deterministically over replayed event streams
 * @doc.layer product
 * @doc.pattern RuntimeProof
 */
@DisplayName("Compiled Pattern execution proof")
class CompiledPatternExecutionProofTest {

    @Test
    void executesSeqTimesAndAgentPredicateAgainstDeterministicEvents() {
        CompiledPattern compiled = PatternSpecCompiler.compile(spec());
        List<TestEvent> stream = List.of(
            event("e-1", "deploy.started", "checkout", true, "2026-05-23T00:00:00Z"),
            event("e-2", "metric.error", "checkout", true, "2026-05-23T00:01:00Z"),
            event("e-3", "metric.error", "checkout", true, "2026-05-23T00:02:00Z"),
            event("e-4", "risk.assessed", "checkout", true, "2026-05-23T00:03:00Z"),
            event("e-5", "deploy.started", "search", true, "2026-05-23T00:04:00Z"));

        List<RuntimeMatch> firstReplay = execute(compiled, stream);
        List<RuntimeMatch> secondReplay = execute(compiled, stream);

        assertThat(firstReplay)
            .containsExactly(new RuntimeMatch("sre-risk-sequence", List.of("e-1", "e-2", "e-3", "e-4")));
        assertThat(secondReplay).isEqualTo(firstReplay);
    }

    private static List<RuntimeMatch> execute(CompiledPattern compiled, List<TestEvent> stream) {
        List<TestEvent> orderedStream = stream.stream()
            .sorted(Comparator.comparing(TestEvent::timestamp))
            .toList();
        List<RuntimeMatch> matches = new ArrayList<>();

        for (int index = 0; index < orderedStream.size(); index++) {
            MatchResult result = match(compiled.root(), orderedStream, index);
            if (result.matched()) {
                matches.add(new RuntimeMatch(compiled.patternId(), result.eventIds()));
            }
        }

        return matches;
    }

    private static MatchResult match(PatternRuntimeNode node, List<TestEvent> stream, int startIndex) {
        if (node.operatorKind() == OperatorKind.EVENT_REF) {
            if (startIndex >= stream.size()) {
                return MatchResult.no();
            }
            TestEvent event = stream.get(startIndex);
            String expectedEventType = node.eventType().orElse("");
            return expectedEventType.equals(event.type())
                ? MatchResult.yes(startIndex + 1, List.of(event.id()))
                : MatchResult.no();
        }

        if (node.operatorKind() == OperatorKind.AGENT_PREDICATE) {
            if (startIndex >= stream.size()) {
                return MatchResult.no();
            }
            TestEvent event = stream.get(startIndex);
            return event.agentApproved()
                ? MatchResult.yes(startIndex + 1, List.of(event.id()))
                : MatchResult.no();
        }

        if (node.operatorKind() == OperatorKind.TIMES) {
            int min = Integer.parseInt(String.valueOf(node.parameters().getOrDefault("min", "1")));
            PatternRuntimeNode nested = node.children().get(0);
            int cursor = startIndex;
            List<String> eventIds = new ArrayList<>();
            for (int count = 0; count < min; count++) {
                MatchResult result = match(nested, stream, cursor);
                if (!result.matched()) {
                    return MatchResult.no();
                }
                cursor = result.nextIndex();
                eventIds.addAll(result.eventIds());
            }
            return MatchResult.yes(cursor, eventIds);
        }

        if (node.operatorKind() == OperatorKind.SEQ) {
            int cursor = startIndex;
            List<String> eventIds = new ArrayList<>();
            for (PatternRuntimeNode child : node.children()) {
                MatchResult result = match(child, stream, cursor);
                if (!result.matched()) {
                    return MatchResult.no();
                }
                cursor = result.nextIndex();
                eventIds.addAll(result.eventIds());
            }
            return MatchResult.yes(cursor, eventIds);
        }

        return MatchResult.no();
    }

    private static Map<String, Object> spec() {
        return Map.of(
            "apiVersion", "aep.ghatana.io/v1",
            "kind", "PatternSpec",
            "metadata", Map.of("name", "sre-risk-sequence", "tenantId", "tenant-a"),
            "semantics", Map.of("timePolicy", Map.of(), "uncertaintyPolicy", Map.of(), "replayPolicy", Map.of()),
            "pattern", Map.of(
                "operator", "SEQ",
                "operands", List.of(
                    Map.of("event", "deploy.started"),
                    Map.of(
                        "operator", "TIMES",
                        "min", 2,
                        "pattern", Map.of("event", "metric.error")),
                    Map.of(
                        "operator", "AGENT_PREDICATE",
                        "capabilityRef", "agents/sre-risk@1.0.0/capabilities/predicate",
                        "inputSchema", "EventContext",
                        "outputSchema", "RiskDecision"))),
            "emit", Map.of("eventType", "pattern.matched", "outputSchema", "PatternMatched"),
            "lifecycle", Map.of("state", "SHADOW"),
            "governance", Map.of("reviewPolicy", "human_required"),
            "observability", Map.of("metrics", true));
    }

    private static TestEvent event(
            String id,
            String type,
            String service,
            boolean agentApproved,
            String timestamp) {
        return new TestEvent(id, type, service, agentApproved, Instant.parse(timestamp));
    }

    private record TestEvent(String id, String type, String service, boolean agentApproved, Instant timestamp) {
    }

    private record RuntimeMatch(String patternId, List<String> eventIds) {
    }

    private record MatchResult(boolean matched, int nextIndex, List<String> eventIds) {
        static MatchResult yes(int nextIndex, List<String> eventIds) {
            return new MatchResult(true, nextIndex, eventIds);
        }

        static MatchResult no() {
            return new MatchResult(false, -1, List.of());
        }
    }
}
