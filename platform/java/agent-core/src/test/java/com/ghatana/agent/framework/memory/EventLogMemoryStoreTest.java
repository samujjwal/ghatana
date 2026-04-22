package com.ghatana.agent.framework.memory;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for EventLogMemoryStore - in-memory event-sourced memory implementation.
 *
 * @doc.type class
 * @doc.purpose Test event-sourced memory store implementation
 * @doc.layer framework
 * @doc.pattern Test
 */
@DisplayName("EventLogMemoryStore Tests [GH-90000]")
class EventLogMemoryStoreTest extends EventloopTestBase {

    private EventLogMemoryStore memoryStore;

    @BeforeEach
    void setUp() { // GH-90000
        memoryStore = new EventLogMemoryStore(); // GH-90000
    }

    @Test
    @DisplayName("Should store and retrieve episodic memory [GH-90000]")
    void shouldStoreEpisode() { // GH-90000
        // Given
        Episode episode = Episode.builder() // GH-90000
                .agentId("test-agent [GH-90000]")
                .turnId("turn-1 [GH-90000]")
                .timestamp(Instant.now()) // GH-90000
                .input("User asks for help [GH-90000]")
                .output("Agent provides guidance [GH-90000]")
                .action("delegate_to_expert [GH-90000]")
                .tags(List.of("help", "delegation")) // GH-90000
                .reward(0.8) // GH-90000
                .build(); // GH-90000

        // When
        Episode stored = runPromise(() -> memoryStore.storeEpisode(episode)); // GH-90000

        // Then
        assertThat(stored).isNotNull(); // GH-90000
        assertThat(stored.getId()).isNotNull().startsWith("episode- [GH-90000]");
        assertThat(stored.getAgentId()).isEqualTo("test-agent [GH-90000]");
        assertThat(stored.getInput()).isEqualTo("User asks for help [GH-90000]");
        assertThat(stored.getTags()).contains("help", "delegation"); // GH-90000
    }

    @Test
    @DisplayName("Should query episodes by agent with filter [GH-90000]")
    void shouldQueryEpisodes() { // GH-90000
        // Given
        Instant now = Instant.now(); // GH-90000
        Episode ep1 = Episode.builder() // GH-90000
                .agentId("agent-1 [GH-90000]")
                .turnId("turn-1 [GH-90000]")
                .timestamp(now.minus(2, ChronoUnit.HOURS)) // GH-90000
                .input("First request [GH-90000]")
                .tags(List.of("tag-1 [GH-90000]"))
                .build(); // GH-90000
        Episode ep2 = Episode.builder() // GH-90000
                .agentId("agent-1 [GH-90000]")
                .turnId("turn-2 [GH-90000]")
                .timestamp(now.minus(1, ChronoUnit.HOURS)) // GH-90000
                .input("Second request [GH-90000]")
                .tags(List.of("tag-2 [GH-90000]"))
                .build(); // GH-90000

        runPromise(() -> memoryStore.storeEpisode(ep1)); // GH-90000
        runPromise(() -> memoryStore.storeEpisode(ep2)); // GH-90000

        // When
        MemoryFilter filter = MemoryFilter.builder() // GH-90000
                .agentId("agent-1 [GH-90000]")
                .startTime(now.minus(3, ChronoUnit.HOURS)) // GH-90000
                .build(); // GH-90000
        List<Episode> results = runPromise(() -> memoryStore.queryEpisodes(filter, 10)); // GH-90000

        // Then
        assertThat(results).hasSize(2); // GH-90000
        assertThat(results).allMatch(e -> e.getAgentId().equals("agent-1 [GH-90000]"));
        // Should be reverse chronological
        assertThat(results.get(0).getInput()).isEqualTo("Second request [GH-90000]");
        assertThat(results.get(1).getInput()).isEqualTo("First request [GH-90000]");
    }

    @Test
    @DisplayName("Should search episodes by content [GH-90000]")
    void shouldSearchEpisodes() { // GH-90000
        // Given
        Episode ep1 = Episode.builder() // GH-90000
                .agentId("agent-1 [GH-90000]")
                .turnId("turn-1 [GH-90000]")
                .timestamp(Instant.now()) // GH-90000
                .input("Deploy to production [GH-90000]")
                .output("Deployment initiated [GH-90000]")
                .build(); // GH-90000
        Episode ep2 = Episode.builder() // GH-90000
                .agentId("agent-1 [GH-90000]")
                .turnId("turn-2 [GH-90000]")
                .timestamp(Instant.now()) // GH-90000
                .input("Check staging status [GH-90000]")
                .output("Staging is healthy [GH-90000]")
                .build(); // GH-90000

        runPromise(() -> memoryStore.storeEpisode(ep1)); // GH-90000
        runPromise(() -> memoryStore.storeEpisode(ep2)); // GH-90000

        // When
        List<Episode> results = runPromise(() -> memoryStore.searchEpisodes("staging", 10)); // GH-90000

        // Then
        assertThat(results).hasSize(1); // GH-90000
        assertThat(results.get(0).getInput()).contains("staging [GH-90000]");
    }

    @Test
    @DisplayName("Should store and retrieve semantic facts [GH-90000]")
    void shouldStoreFact() { // GH-90000
        // Given
        Fact fact = Fact.builder() // GH-90000
                .agentId("agent-1 [GH-90000]")
                .subject("User123 [GH-90000]")
                .predicate("prefers [GH-90000]")
                .object("dark_mode [GH-90000]")
                .confidence(0.95) // GH-90000
                .source("user-preference-survey [GH-90000]")
                .build(); // GH-90000

        // When
        Fact stored = runPromise(() -> memoryStore.storeFact(fact)); // GH-90000

        // Then
        assertThat(stored).isNotNull(); // GH-90000
        assertThat(stored.getId()).isNotNull().startsWith("fact- [GH-90000]");
        assertThat(stored.getSubject()).isEqualTo("User123 [GH-90000]");
        assertThat(stored.getPredicate()).isEqualTo("prefers [GH-90000]");
        assertThat(stored.getObject()).isEqualTo("dark_mode [GH-90000]");
        assertThat(stored.getConfidence()).isEqualTo(0.95); // GH-90000
    }

    @Test
    @DisplayName("Should query facts by subject-predicate-object [GH-90000]")
    void shouldQueryFacts() { // GH-90000
        // Given
        Fact f1 = Fact.builder() // GH-90000
                .agentId("agent-1 [GH-90000]")
                .subject("ServiceA [GH-90000]")
                .predicate("depends_on [GH-90000]")
                .object("ServiceB [GH-90000]")
                .confidence(1.0) // GH-90000
                .build(); // GH-90000
        Fact f2 = Fact.builder() // GH-90000
                .agentId("agent-1 [GH-90000]")
                .subject("ServiceA [GH-90000]")
                .predicate("depends_on [GH-90000]")
                .object("ServiceC [GH-90000]")
                .confidence(0.8) // GH-90000
                .build(); // GH-90000

        runPromise(() -> memoryStore.storeFact(f1)); // GH-90000
        runPromise(() -> memoryStore.storeFact(f2)); // GH-90000

        // When - Query all dependencies of ServiceA
        List<Fact> results = runPromise(() -> memoryStore.queryFacts("ServiceA", "depends_on", null)); // GH-90000

        // Then
        assertThat(results).hasSize(2); // GH-90000
        assertThat(results).allMatch(f -> f.getSubject().equals("ServiceA [GH-90000]"));
        // Should be ordered by confidence
        assertThat(results.get(0).getConfidence()).isEqualTo(1.0); // GH-90000
        assertThat(results.get(1).getConfidence()).isEqualTo(0.8); // GH-90000
    }

    @Test
    @DisplayName("Should store and retrieve procedural policies [GH-90000]")
    void shouldStorePolicy() { // GH-90000
        // Given
        Policy policy = Policy.builder() // GH-90000
                .agentId("ProductOwnerAgent [GH-90000]")
                .situation("requirement lacks acceptance criteria [GH-90000]")
                .action("delegate to QA Lead to define testable criteria [GH-90000]")
                .confidence(0.92) // GH-90000
                .learnedFromEpisodes("episode-123,episode-456 [GH-90000]")
                .build(); // GH-90000

        // When
        Policy stored = runPromise(() -> memoryStore.storePolicy(policy)); // GH-90000

        // Then
        assertThat(stored).isNotNull(); // GH-90000
        assertThat(stored.getId()).isNotNull().startsWith("policy- [GH-90000]");
        assertThat(stored.getSituation()).contains("acceptance criteria [GH-90000]");
        assertThat(stored.getAction()).contains("delegate to QA [GH-90000]");
        assertThat(stored.getConfidence()).isEqualTo(0.92); // GH-90000
    }

    @Test
    @DisplayName("Should query policies by situation and confidence [GH-90000]")
    void shouldQueryPolicies() { // GH-90000
        // Given
        Policy p1 = Policy.builder() // GH-90000
                .agentId("agent-1 [GH-90000]")
                .situation("User reports bug in production [GH-90000]")
                .action("Create incident, rollback deployment [GH-90000]")
                .confidence(0.95) // GH-90000
                .build(); // GH-90000
        Policy p2 = Policy.builder() // GH-90000
                .agentId("agent-1 [GH-90000]")
                .situation("User reports bug in staging [GH-90000]")
                .action("Create issue, investigate [GH-90000]")
                .confidence(0.85) // GH-90000
                .build(); // GH-90000

        runPromise(() -> memoryStore.storePolicy(p1)); // GH-90000
        runPromise(() -> memoryStore.storePolicy(p2)); // GH-90000

        // When - Find high-confidence policies for bug situations
        List<Policy> results = runPromise(() -> memoryStore.queryPolicies("bug", 0.7)); // GH-90000

        // Then
        assertThat(results).hasSize(2); // GH-90000
        assertThat(results).allMatch(p -> p.getConfidence() >= 0.7); // GH-90000
        // Should be ordered by confidence
        assertThat(results.get(0).getConfidence()).isEqualTo(0.95); // GH-90000
    }

    @Test
    @DisplayName("Should store and retrieve preferences [GH-90000]")
    void shouldStorePreference() { // GH-90000
        // Given
        Preference pref = Preference.builder() // GH-90000
                .agentId("agent-1 [GH-90000]")
                .namespace("ui [GH-90000]")
                .key("theme [GH-90000]")
                .value("dark [GH-90000]")
                .build(); // GH-90000

        // When
        Preference stored = runPromise(() -> memoryStore.storePreference(pref)); // GH-90000

        // Then
        assertThat(stored).isNotNull(); // GH-90000
        assertThat(stored.getKey()).isEqualTo("theme [GH-90000]");
        assertThat(stored.getValue()).isEqualTo("dark [GH-90000]");
        assertThat(stored.getNamespace()).isEqualTo("ui [GH-90000]");
    }

    @Test
    @DisplayName("Should get preference by key [GH-90000]")
    void shouldGetPreference() { // GH-90000
        // Given
        Preference pref = Preference.builder() // GH-90000
                .agentId("agent-1 [GH-90000]")
                .namespace("notifications [GH-90000]")
                .key("email_enabled [GH-90000]")
                .value("true [GH-90000]")
                .build(); // GH-90000
        runPromise(() -> memoryStore.storePreference(pref)); // GH-90000

        // When
        String value = runPromise(() -> memoryStore.getPreference("email_enabled [GH-90000]"));

        // Then
        assertThat(value).isEqualTo("true [GH-90000]");
    }

    @Test
    @DisplayName("Should get all preferences by namespace [GH-90000]")
    void shouldGetPreferences() { // GH-90000
        // Given
        Preference p1 = Preference.builder() // GH-90000
                .agentId("agent-1 [GH-90000]")
                .namespace("ui [GH-90000]")
                .key("theme [GH-90000]")
                .value("dark [GH-90000]")
                .build(); // GH-90000
        Preference p2 = Preference.builder() // GH-90000
                .agentId("agent-1 [GH-90000]")
                .namespace("ui [GH-90000]")
                .key("language [GH-90000]")
                .value("en [GH-90000]")
                .build(); // GH-90000

        runPromise(() -> memoryStore.storePreference(p1)); // GH-90000
        runPromise(() -> memoryStore.storePreference(p2)); // GH-90000

        // When
        Map<String, String> uiPrefs = runPromise(() -> memoryStore.getPreferences("ui [GH-90000]"));

        // Then
        assertThat(uiPrefs).hasSize(2); // GH-90000
        assertThat(uiPrefs).containsEntry("theme", "dark"); // GH-90000
        assertThat(uiPrefs).containsEntry("language", "en"); // GH-90000
    }

    @Test
    @DisplayName("Should clear all memory [GH-90000]")
    void shouldClearMemory() { // GH-90000
        // Given
        Episode episode = Episode.builder() // GH-90000
                .agentId("agent-1 [GH-90000]")
                .turnId("turn-1 [GH-90000]")
                .timestamp(Instant.now()) // GH-90000
                .input("test [GH-90000]")
                .build(); // GH-90000
        Fact fact = Fact.builder() // GH-90000
                .agentId("agent-1 [GH-90000]")
                .subject("A [GH-90000]")
                .predicate("is [GH-90000]")
                .object("B [GH-90000]")
                .build(); // GH-90000

        runPromise(() -> memoryStore.storeEpisode(episode)); // GH-90000
        runPromise(() -> memoryStore.storeFact(fact)); // GH-90000

        // When
        int cleared = runPromise(() -> memoryStore.clearMemory()); // GH-90000

        // Then
        assertThat(cleared).isEqualTo(2); // GH-90000

        // Verify all empty
        MemoryStats stats = runPromise(() -> memoryStore.getStats()); // GH-90000
        assertThat(stats.getEpisodeCount()).isEqualTo(0); // GH-90000
        assertThat(stats.getFactCount()).isEqualTo(0); // GH-90000
    }

    @Test
    @DisplayName("Should return memory statistics [GH-90000]")
    void shouldGetStats() { // GH-90000
        // Given
        Episode ep = Episode.builder() // GH-90000
                .agentId("agent-1 [GH-90000]")
                .turnId("turn-1 [GH-90000]")
                .timestamp(Instant.now()) // GH-90000
                .input("test [GH-90000]")
                .build(); // GH-90000
        Fact fact = Fact.builder() // GH-90000
                .agentId("agent-1 [GH-90000]")
                .subject("A [GH-90000]")
                .predicate("is [GH-90000]")
                .object("B [GH-90000]")
                .build(); // GH-90000

        runPromise(() -> memoryStore.storeEpisode(ep)); // GH-90000
        runPromise(() -> memoryStore.storeFact(fact)); // GH-90000

        // When
        MemoryStats stats = runPromise(() -> memoryStore.getStats()); // GH-90000

        // Then
        assertThat(stats.getEpisodeCount()).isEqualTo(1); // GH-90000
        assertThat(stats.getFactCount()).isEqualTo(1); // GH-90000
        assertThat(stats.getPolicyCount()).isEqualTo(0); // GH-90000
        assertThat(stats.getPreferenceCount()).isEqualTo(0); // GH-90000
        assertThat(stats.getTotalSizeBytes()).isGreaterThan(0); // GH-90000
    }
}
