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
@DisplayName("EventLogMemoryStore Tests")
class EventLogMemoryStoreTest extends EventloopTestBase {

    private EventLogMemoryStore memoryStore;

    @BeforeEach
    void setUp() { // GH-90000
        memoryStore = new EventLogMemoryStore(); // GH-90000
    }

    @Test
    @DisplayName("Should store and retrieve episodic memory")
    void shouldStoreEpisode() { // GH-90000
        // Given
        Episode episode = Episode.builder() // GH-90000
                .agentId("test-agent")
                .turnId("turn-1")
                .timestamp(Instant.now()) // GH-90000
                .input("User asks for help")
                .output("Agent provides guidance")
                .action("delegate_to_expert")
                .tags(List.of("help", "delegation")) // GH-90000
                .reward(0.8) // GH-90000
                .build(); // GH-90000

        // When
        Episode stored = runPromise(() -> memoryStore.storeEpisode(episode)); // GH-90000

        // Then
        assertThat(stored).isNotNull(); // GH-90000
        assertThat(stored.getId()).isNotNull().startsWith("episode-");
        assertThat(stored.getAgentId()).isEqualTo("test-agent");
        assertThat(stored.getInput()).isEqualTo("User asks for help");
        assertThat(stored.getTags()).contains("help", "delegation"); // GH-90000
    }

    @Test
    @DisplayName("Should query episodes by agent with filter")
    void shouldQueryEpisodes() { // GH-90000
        // Given
        Instant now = Instant.now(); // GH-90000
        Episode ep1 = Episode.builder() // GH-90000
                .agentId("agent-1")
                .turnId("turn-1")
                .timestamp(now.minus(2, ChronoUnit.HOURS)) // GH-90000
                .input("First request")
                .tags(List.of("tag-1"))
                .build(); // GH-90000
        Episode ep2 = Episode.builder() // GH-90000
                .agentId("agent-1")
                .turnId("turn-2")
                .timestamp(now.minus(1, ChronoUnit.HOURS)) // GH-90000
                .input("Second request")
                .tags(List.of("tag-2"))
                .build(); // GH-90000

        runPromise(() -> memoryStore.storeEpisode(ep1)); // GH-90000
        runPromise(() -> memoryStore.storeEpisode(ep2)); // GH-90000

        // When
        MemoryFilter filter = MemoryFilter.builder() // GH-90000
                .agentId("agent-1")
                .startTime(now.minus(3, ChronoUnit.HOURS)) // GH-90000
                .build(); // GH-90000
        List<Episode> results = runPromise(() -> memoryStore.queryEpisodes(filter, 10)); // GH-90000

        // Then
        assertThat(results).hasSize(2); // GH-90000
        assertThat(results).allMatch(e -> e.getAgentId().equals("agent-1"));
        // Should be reverse chronological
        assertThat(results.get(0).getInput()).isEqualTo("Second request");
        assertThat(results.get(1).getInput()).isEqualTo("First request");
    }

    @Test
    @DisplayName("Should search episodes by content")
    void shouldSearchEpisodes() { // GH-90000
        // Given
        Episode ep1 = Episode.builder() // GH-90000
                .agentId("agent-1")
                .turnId("turn-1")
                .timestamp(Instant.now()) // GH-90000
                .input("Deploy to production")
                .output("Deployment initiated")
                .build(); // GH-90000
        Episode ep2 = Episode.builder() // GH-90000
                .agentId("agent-1")
                .turnId("turn-2")
                .timestamp(Instant.now()) // GH-90000
                .input("Check staging status")
                .output("Staging is healthy")
                .build(); // GH-90000

        runPromise(() -> memoryStore.storeEpisode(ep1)); // GH-90000
        runPromise(() -> memoryStore.storeEpisode(ep2)); // GH-90000

        // When
        List<Episode> results = runPromise(() -> memoryStore.searchEpisodes("staging", 10)); // GH-90000

        // Then
        assertThat(results).hasSize(1); // GH-90000
        assertThat(results.get(0).getInput()).contains("staging");
    }

    @Test
    @DisplayName("Should store and retrieve semantic facts")
    void shouldStoreFact() { // GH-90000
        // Given
        Fact fact = Fact.builder() // GH-90000
                .agentId("agent-1")
                .subject("User123")
                .predicate("prefers")
                .object("dark_mode")
                .confidence(0.95) // GH-90000
                .source("user-preference-survey")
                .build(); // GH-90000

        // When
        Fact stored = runPromise(() -> memoryStore.storeFact(fact)); // GH-90000

        // Then
        assertThat(stored).isNotNull(); // GH-90000
        assertThat(stored.getId()).isNotNull().startsWith("fact-");
        assertThat(stored.getSubject()).isEqualTo("User123");
        assertThat(stored.getPredicate()).isEqualTo("prefers");
        assertThat(stored.getObject()).isEqualTo("dark_mode");
        assertThat(stored.getConfidence()).isEqualTo(0.95); // GH-90000
    }

    @Test
    @DisplayName("Should query facts by subject-predicate-object")
    void shouldQueryFacts() { // GH-90000
        // Given
        Fact f1 = Fact.builder() // GH-90000
                .agentId("agent-1")
                .subject("ServiceA")
                .predicate("depends_on")
                .object("ServiceB")
                .confidence(1.0) // GH-90000
                .build(); // GH-90000
        Fact f2 = Fact.builder() // GH-90000
                .agentId("agent-1")
                .subject("ServiceA")
                .predicate("depends_on")
                .object("ServiceC")
                .confidence(0.8) // GH-90000
                .build(); // GH-90000

        runPromise(() -> memoryStore.storeFact(f1)); // GH-90000
        runPromise(() -> memoryStore.storeFact(f2)); // GH-90000

        // When - Query all dependencies of ServiceA
        List<Fact> results = runPromise(() -> memoryStore.queryFacts("ServiceA", "depends_on", null)); // GH-90000

        // Then
        assertThat(results).hasSize(2); // GH-90000
        assertThat(results).allMatch(f -> f.getSubject().equals("ServiceA"));
        // Should be ordered by confidence
        assertThat(results.get(0).getConfidence()).isEqualTo(1.0); // GH-90000
        assertThat(results.get(1).getConfidence()).isEqualTo(0.8); // GH-90000
    }

    @Test
    @DisplayName("Should store and retrieve procedural policies")
    void shouldStorePolicy() { // GH-90000
        // Given
        Policy policy = Policy.builder() // GH-90000
                .agentId("ProductOwnerAgent")
                .situation("requirement lacks acceptance criteria")
                .action("delegate to QA Lead to define testable criteria")
                .confidence(0.92) // GH-90000
                .learnedFromEpisodes("episode-123,episode-456")
                .build(); // GH-90000

        // When
        Policy stored = runPromise(() -> memoryStore.storePolicy(policy)); // GH-90000

        // Then
        assertThat(stored).isNotNull(); // GH-90000
        assertThat(stored.getId()).isNotNull().startsWith("policy-");
        assertThat(stored.getSituation()).contains("acceptance criteria");
        assertThat(stored.getAction()).contains("delegate to QA");
        assertThat(stored.getConfidence()).isEqualTo(0.92); // GH-90000
    }

    @Test
    @DisplayName("Should query policies by situation and confidence")
    void shouldQueryPolicies() { // GH-90000
        // Given
        Policy p1 = Policy.builder() // GH-90000
                .agentId("agent-1")
                .situation("User reports bug in production")
                .action("Create incident, rollback deployment")
                .confidence(0.95) // GH-90000
                .build(); // GH-90000
        Policy p2 = Policy.builder() // GH-90000
                .agentId("agent-1")
                .situation("User reports bug in staging")
                .action("Create issue, investigate")
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
    @DisplayName("Should store and retrieve preferences")
    void shouldStorePreference() { // GH-90000
        // Given
        Preference pref = Preference.builder() // GH-90000
                .agentId("agent-1")
                .namespace("ui")
                .key("theme")
                .value("dark")
                .build(); // GH-90000

        // When
        Preference stored = runPromise(() -> memoryStore.storePreference(pref)); // GH-90000

        // Then
        assertThat(stored).isNotNull(); // GH-90000
        assertThat(stored.getKey()).isEqualTo("theme");
        assertThat(stored.getValue()).isEqualTo("dark");
        assertThat(stored.getNamespace()).isEqualTo("ui");
    }

    @Test
    @DisplayName("Should get preference by key")
    void shouldGetPreference() { // GH-90000
        // Given
        Preference pref = Preference.builder() // GH-90000
                .agentId("agent-1")
                .namespace("notifications")
                .key("email_enabled")
                .value("true")
                .build(); // GH-90000
        runPromise(() -> memoryStore.storePreference(pref)); // GH-90000

        // When
        String value = runPromise(() -> memoryStore.getPreference("email_enabled"));

        // Then
        assertThat(value).isEqualTo("true");
    }

    @Test
    @DisplayName("Should get all preferences by namespace")
    void shouldGetPreferences() { // GH-90000
        // Given
        Preference p1 = Preference.builder() // GH-90000
                .agentId("agent-1")
                .namespace("ui")
                .key("theme")
                .value("dark")
                .build(); // GH-90000
        Preference p2 = Preference.builder() // GH-90000
                .agentId("agent-1")
                .namespace("ui")
                .key("language")
                .value("en")
                .build(); // GH-90000

        runPromise(() -> memoryStore.storePreference(p1)); // GH-90000
        runPromise(() -> memoryStore.storePreference(p2)); // GH-90000

        // When
        Map<String, String> uiPrefs = runPromise(() -> memoryStore.getPreferences("ui"));

        // Then
        assertThat(uiPrefs).hasSize(2); // GH-90000
        assertThat(uiPrefs).containsEntry("theme", "dark"); // GH-90000
        assertThat(uiPrefs).containsEntry("language", "en"); // GH-90000
    }

    @Test
    @DisplayName("Should clear all memory")
    void shouldClearMemory() { // GH-90000
        // Given
        Episode episode = Episode.builder() // GH-90000
                .agentId("agent-1")
                .turnId("turn-1")
                .timestamp(Instant.now()) // GH-90000
                .input("test")
                .build(); // GH-90000
        Fact fact = Fact.builder() // GH-90000
                .agentId("agent-1")
                .subject("A")
                .predicate("is")
                .object("B")
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
    @DisplayName("Should return memory statistics")
    void shouldGetStats() { // GH-90000
        // Given
        Episode ep = Episode.builder() // GH-90000
                .agentId("agent-1")
                .turnId("turn-1")
                .timestamp(Instant.now()) // GH-90000
                .input("test")
                .build(); // GH-90000
        Fact fact = Fact.builder() // GH-90000
                .agentId("agent-1")
                .subject("A")
                .predicate("is")
                .object("B")
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
