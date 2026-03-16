package com.ghatana.appplatform.sdk;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;

/**
 * @doc.type    Service
 * @doc.purpose Provides in-memory test doubles for platform infrastructure,
 *              enabling fast, isolated unit tests without real service dependencies.
 *              Provides: TestEventBus, TestConfigStore, TestAuditStore, TestRulesEngine,
 *              EventFactory (typed builders), and Given/When/Then BDD helpers.
 * @doc.layer   Application
 * @doc.pattern Inner-Port
 */
public class TestHarnessSdkService {

    // -----------------------------------------------------------------------
    // In-memory Test Event Bus
    // -----------------------------------------------------------------------

    public static class TestEventBus {
        private final Map<String, List<ReceivedEvent>>          publishedEvents   = new ConcurrentHashMap<>();
        private final Map<String, List<EventSubscription>>      subscriptions     = new ConcurrentHashMap<>();
        private final Counter publishedTotal;

        public TestEventBus(MeterRegistry meterRegistry) {
            this.publishedTotal = Counter.builder("sdk.test.event_bus.published_total")
                    .description("Events published through TestEventBus")
                    .register(meterRegistry);
        }

        /** Publish an event and immediately deliver it to matching subscribers. */
        public Promise<String> publish(String topic, String eventType, String payloadJson,
                                       Map<String, String> headers) {
            String eventId = "test-" + System.nanoTime();
            publishedEvents.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>())
                           .add(new ReceivedEvent(eventId, topic, eventType, payloadJson, headers));
            publishedTotal.increment();
            // Deliver synchronously to subscribers in test context
            List<EventSubscription> subs = subscriptions.getOrDefault(topic, List.of());
            for (EventSubscription sub : subs) {
                sub.handler().apply(eventType, payloadJson);
            }
            return Promise.of(eventId);
        }

        /** Register a test subscriber — handler receives (eventType, payloadJson). */
        public void subscribe(String topic, BiFunction<String, String, Void> handler) {
            subscriptions.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>())
                         .add(new EventSubscription(topic, handler));
        }

        /** Assert the count of events published to a topic. */
        public int publishedCount(String topic) {
            return publishedEvents.getOrDefault(topic, List.of()).size();
        }

        /** Get all events published to a topic. */
        public List<ReceivedEvent> eventsFor(String topic) {
            return List.copyOf(publishedEvents.getOrDefault(topic, List.of()));
        }

        /** Reset state between tests. */
        public void reset() {
            publishedEvents.clear();
        }

        public record ReceivedEvent(String eventId, String topic, String eventType,
                                     String payloadJson, Map<String, String> headers) {}

        private record EventSubscription(String topic, BiFunction<String, String, Void> handler) {}
    }

    // -----------------------------------------------------------------------
    // In-memory Test Config Store
    // -----------------------------------------------------------------------

    public static class TestConfigStore {
        private final Map<String, Map<String, String>> store = new ConcurrentHashMap<>();

        /** Pre-populate a config value. */
        public void set(String namespace, String key, String value) {
            store.computeIfAbsent(namespace, k -> new ConcurrentHashMap<>()).put(key, value);
        }

        /** Retrieve a config value (returns null when absent). */
        public Promise<String> get(String namespace, String key) {
            return Promise.of(store.getOrDefault(namespace, Map.of()).get(key));
        }

        /** List all keys under a namespace prefix. */
        public Promise<List<String>> listKeys(String namespacePrefix) {
            List<String> keys = new ArrayList<>();
            store.forEach((ns, values) -> {
                if (ns.startsWith(namespacePrefix)) {
                    values.keySet().forEach(k -> keys.add(ns + "." + k));
                }
            });
            return Promise.of(keys);
        }

        public void reset() { store.clear(); }
    }

    // -----------------------------------------------------------------------
    // In-memory Test Audit Store
    // -----------------------------------------------------------------------

    public static class TestAuditStore {
        private final List<AuditEntry> entries = new CopyOnWriteArrayList<>();

        public Promise<Void> log(String service, String action, String actor, String entityId,
                                  String entityType, String beforeJson, String afterJson) {
            entries.add(new AuditEntry(service, action, actor, entityId, entityType, beforeJson, afterJson));
            return Promise.of(null);
        }

        /** Get all audit entries matching a given action. */
        public List<AuditEntry> entriesForAction(String action) {
            return entries.stream().filter(e -> action.equals(e.action())).toList();
        }

        /** Return total number of recorded audit entries. */
        public int size() { return entries.size(); }

        public void reset() { entries.clear(); }

        public record AuditEntry(String service, String action, String actor, String entityId,
                                  String entityType, String beforeJson, String afterJson) {}
    }

    // -----------------------------------------------------------------------
    // In-memory Test Rules Engine
    // -----------------------------------------------------------------------

    public static class TestRulesEngine {
        private final Map<String, String> cannedResponses = new ConcurrentHashMap<>();

        /** Configure a canned result for a rule-set/facts combination. */
        public void whenEvaluate(String ruleSetId, String expectedFactsJson, String resultJson) {
            cannedResponses.put(ruleSetId + "::" + expectedFactsJson, resultJson);
        }

        /** Configure a default response for a rule-set regardless of facts. */
        public void defaultResult(String ruleSetId, String resultJson) {
            cannedResponses.put(ruleSetId + "::*", resultJson);
        }

        public Promise<String> evaluate(String ruleSetId, String factsJson) {
            String keyed = cannedResponses.get(ruleSetId + "::" + factsJson);
            if (keyed != null) return Promise.of(keyed);
            String fallback = cannedResponses.get(ruleSetId + "::*");
            if (fallback != null) return Promise.of(fallback);
            return Promise.of("{\"result\":\"PASS\",\"reason\":\"test-default\"}");
        }

        public void reset() { cannedResponses.clear(); }
    }

    // -----------------------------------------------------------------------
    // Event Factory (typed builders for common domain events)
    // -----------------------------------------------------------------------

    public static class EventFactory {

        /** Build a typed OrderFilled event payload JSON with optional overrides. */
        public static String orderFilled(Map<String, Object> overrides) {
            Map<String, Object> defaults = new HashMap<>(Map.of(
                "orderId",      "ord-test-" + System.nanoTime(),
                "symbol",       "NABIL",
                "side",         "BUY",
                "quantity",     100,
                "filledPrice",  450.0,
                "currency",     "NPR",
                "filledAt",     "2026-09-01T09:30:00Z"
            ));
            defaults.putAll(overrides);
            return toJson(defaults);
        }

        /** Build a typed TradeFilled event payload JSON with optional overrides. */
        public static String tradeFilled(Map<String, Object> overrides) {
            Map<String, Object> defaults = new HashMap<>(Map.of(
                "tradeId",       "trd-test-" + System.nanoTime(),
                "orderId",       "ord-test-ref",
                "symbol",        "NABIL",
                "quantity",      100,
                "tradePrice",    450.0,
                "currency",      "NPR",
                "counterparty",  "BROKER-B",
                "settleDate",    "2026-09-03"
            ));
            defaults.putAll(overrides);
            return toJson(defaults);
        }

        /** Build a typed ClientOnboardingRequested event payload JSON. */
        public static String clientOnboardingRequested(Map<String, Object> overrides) {
            Map<String, Object> defaults = new HashMap<>(Map.of(
                "requestId",    "req-test-" + System.nanoTime(),
                "clientType",   "INDIVIDUAL",
                "firstName",    "Test",
                "lastName",     "User",
                "email",        "test@example.com",
                "nationality",  "NPL",
                "requestedAt",  "2026-09-01T10:00:00Z"
            ));
            defaults.putAll(overrides);
            return toJson(defaults);
        }

        private static String toJson(Map<String, Object> map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if (!first) sb.append(",");
                first = false;
                sb.append("\"").append(entry.getKey()).append("\":");
                Object val = entry.getValue();
                if (val instanceof String s) {
                    sb.append("\"").append(s.replace("\"", "\\\"")).append("\"");
                } else {
                    sb.append(val);
                }
            }
            return sb.append("}").toString();
        }
    }

    // -----------------------------------------------------------------------
    // BDD (Given/When/Then) Helpers
    // -----------------------------------------------------------------------

    /**
     * Fluent DSL for writing BDD-style platform service tests.
     * Usage:
     * <pre>
     *   BddScenario.given("a published order-filled event")
     *       .when(() -> service.processEvent(...))
     *       .then(() -> assertThat(auditStore.size()).isEqualTo(1));
     * </pre>
     */
    public static class BddScenario {
        private final String description;

        private BddScenario(String description) { this.description = description; }

        public static BddScenario given(String situation) { return new BddScenario(situation); }

        public WhenStep when(Runnable action) {
            return new WhenStep(description, action);
        }

        public static class WhenStep {
            private final String description;
            private final Runnable action;

            WhenStep(String description, Runnable action) {
                this.description = description;
                this.action = action;
            }

            public void then(Runnable assertion) {
                try {
                    action.run();
                    assertion.run();
                } catch (AssertionError | RuntimeException e) {
                    throw new AssertionError("BDD scenario failed [" + description + "]: " + e.getMessage(), e);
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Isolation guard (prevent cross-test contamination)
    // -----------------------------------------------------------------------

    /** Reset all provided test doubles between tests. */
    public static void resetAll(TestEventBus bus, TestConfigStore config,
                                 TestAuditStore audit, TestRulesEngine rules) {
        bus.reset();
        config.reset();
        audit.reset();
        rules.reset();
    }
}
