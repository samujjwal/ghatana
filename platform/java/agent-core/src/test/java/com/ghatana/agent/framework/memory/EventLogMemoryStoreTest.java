package com.ghatana.agent.framework.memory;

import com.ghatana.agent.context.version.VersionContext;
import com.ghatana.agent.context.version.VersionContextCodec;
import com.ghatana.agent.mastery.MasteryState;
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
    void setUp() { 
        memoryStore = new EventLogMemoryStore(); 
    }

    @Test
    @DisplayName("Should store and retrieve episodic memory")
    void shouldStoreEpisode() { 
        // Given
        Episode episode = Episode.builder() 
                .agentId("test-agent")
                .turnId("turn-1")
                .timestamp(Instant.now()) 
                .input("User asks for help")
                .output("Agent provides guidance")
                .action("delegate_to_expert")
                .tags(List.of("help", "delegation")) 
                .reward(0.8) 
                .build(); 

        // When
        Episode stored = runPromise(() -> memoryStore.storeEpisode(episode)); 

        // Then
        assertThat(stored).isNotNull(); 
        assertThat(stored.getId()).isNotNull().startsWith("episode-");
        assertThat(stored.getAgentId()).isEqualTo("test-agent");
        assertThat(stored.getInput()).isEqualTo("User asks for help");
        assertThat(stored.getTags()).contains("help", "delegation"); 
    }

    @Test
    @DisplayName("Should query episodes by agent with filter")
    void shouldQueryEpisodes() { 
        // Given
        Instant now = Instant.now(); 
        Episode ep1 = Episode.builder() 
                .agentId("agent-1")
                .turnId("turn-1")
                .timestamp(now.minus(2, ChronoUnit.HOURS)) 
                .input("First request")
                .tags(List.of("tag-1"))
                .build(); 
        Episode ep2 = Episode.builder() 
                .agentId("agent-1")
                .turnId("turn-2")
                .timestamp(now.minus(1, ChronoUnit.HOURS)) 
                .input("Second request")
                .tags(List.of("tag-2"))
                .build(); 

        runPromise(() -> memoryStore.storeEpisode(ep1)); 
        runPromise(() -> memoryStore.storeEpisode(ep2)); 

        // When
        MemoryFilter filter = MemoryFilter.builder() 
                .agentId("agent-1")
                .startTime(now.minus(3, ChronoUnit.HOURS)) 
                .build(); 
        List<Episode> results = runPromise(() -> memoryStore.queryEpisodes(filter, 10)); 

        // Then
        assertThat(results).hasSize(2); 
        assertThat(results).allMatch(e -> e.getAgentId().equals("agent-1"));
        // Should be reverse chronological
        assertThat(results.get(0).getInput()).isEqualTo("Second request");
        assertThat(results.get(1).getInput()).isEqualTo("First request");
    }

    @Test
    @DisplayName("Should search episodes by content")
    void shouldSearchEpisodes() { 
        // Given
        Episode ep1 = Episode.builder() 
                .agentId("agent-1")
                .turnId("turn-1")
                .timestamp(Instant.now()) 
                .input("Deploy to production")
                .output("Deployment initiated")
                .build(); 
        Episode ep2 = Episode.builder() 
                .agentId("agent-1")
                .turnId("turn-2")
                .timestamp(Instant.now()) 
                .input("Check staging status")
                .output("Staging is healthy")
                .build(); 

        runPromise(() -> memoryStore.storeEpisode(ep1)); 
        runPromise(() -> memoryStore.storeEpisode(ep2)); 

        // When
        List<Episode> results = runPromise(() -> memoryStore.searchEpisodes("staging", 10)); 

        // Then
        assertThat(results).hasSize(1); 
        assertThat(results.get(0).getInput()).contains("staging");
    }

    @Test
    @DisplayName("Should store and retrieve semantic facts")
    void shouldStoreFact() { 
        // Given
        Fact fact = Fact.builder() 
                .agentId("agent-1")
                .subject("User123")
                .predicate("prefers")
                .object("dark_mode")
                .confidence(0.95) 
                .source("user-preference-survey")
                .build(); 

        // When
        Fact stored = runPromise(() -> memoryStore.storeFact(fact)); 

        // Then
        assertThat(stored).isNotNull(); 
        assertThat(stored.getId()).isNotNull().startsWith("fact-");
        assertThat(stored.getSubject()).isEqualTo("User123");
        assertThat(stored.getPredicate()).isEqualTo("prefers");
        assertThat(stored.getObject()).isEqualTo("dark_mode");
        assertThat(stored.getConfidence()).isEqualTo(0.95); 
    }

    @Test
    @DisplayName("Should query facts by subject-predicate-object")
    void shouldQueryFacts() { 
        // Given
        Fact f1 = Fact.builder() 
                .agentId("agent-1")
                .subject("ServiceA")
                .predicate("depends_on")
                .object("ServiceB")
                .confidence(1.0) 
                .build(); 
        Fact f2 = Fact.builder() 
                .agentId("agent-1")
                .subject("ServiceA")
                .predicate("depends_on")
                .object("ServiceC")
                .confidence(0.8) 
                .build(); 

        runPromise(() -> memoryStore.storeFact(f1)); 
        runPromise(() -> memoryStore.storeFact(f2)); 

        // When - Query all dependencies of ServiceA
        List<Fact> results = runPromise(() -> memoryStore.queryFacts("ServiceA", "depends_on", null)); 

        // Then
        assertThat(results).hasSize(2); 
        assertThat(results).allMatch(f -> f.getSubject().equals("ServiceA"));
        // Should be ordered by confidence
        assertThat(results.get(0).getConfidence()).isEqualTo(1.0); 
        assertThat(results.get(1).getConfidence()).isEqualTo(0.8); 
    }

    @Test
    @DisplayName("Should store and retrieve procedural policies")
    void shouldStorePolicy() { 
        // Given
        Policy policy = Policy.builder() 
                .agentId("ProductOwnerAgent")
                .situation("requirement lacks acceptance criteria")
                .action("delegate to QA Lead to define testable criteria")
                .confidence(0.92) 
                .learnedFromEpisodes("episode-123,episode-456")
                .build(); 

        // When
        Policy stored = runPromise(() -> memoryStore.storePolicy(policy)); 

        // Then
        assertThat(stored).isNotNull(); 
        assertThat(stored.getId()).isNotNull().startsWith("policy-");
        assertThat(stored.getSituation()).contains("acceptance criteria");
        assertThat(stored.getAction()).contains("delegate to QA");
        assertThat(stored.getConfidence()).isEqualTo(0.92); 
    }

    @Test
    @DisplayName("Should query policies by situation and confidence")
    void shouldQueryPolicies() { 
        // Given
        Policy p1 = Policy.builder() 
                .agentId("agent-1")
                .situation("User reports bug in production")
                .action("Create incident, rollback deployment")
                .confidence(0.95) 
                .build(); 
        Policy p2 = Policy.builder() 
                .agentId("agent-1")
                .situation("User reports bug in staging")
                .action("Create issue, investigate")
                .confidence(0.85) 
                .build(); 

        runPromise(() -> memoryStore.storePolicy(p1)); 
        runPromise(() -> memoryStore.storePolicy(p2)); 

        // When - Find high-confidence policies for bug situations
        List<Policy> results = runPromise(() -> memoryStore.queryPolicies("bug", 0.7)); 

        // Then
        assertThat(results).hasSize(2); 
        assertThat(results).allMatch(p -> p.getConfidence() >= 0.7); 
        // Should be ordered by confidence
        assertThat(results.get(0).getConfidence()).isEqualTo(0.95); 
    }

    @Test
    @DisplayName("Should store and retrieve preferences")
    void shouldStorePreference() { 
        // Given
        Preference pref = Preference.builder() 
                .agentId("agent-1")
                .namespace("ui")
                .key("theme")
                .value("dark")
                .build(); 

        // When
        Preference stored = runPromise(() -> memoryStore.storePreference(pref)); 

        // Then
        assertThat(stored).isNotNull(); 
        assertThat(stored.getKey()).isEqualTo("theme");
        assertThat(stored.getValue()).isEqualTo("dark");
        assertThat(stored.getNamespace()).isEqualTo("ui");
    }

    @Test
    @DisplayName("Should get preference by key")
    void shouldGetPreference() { 
        // Given
        Preference pref = Preference.builder() 
                .agentId("agent-1")
                .namespace("notifications")
                .key("email_enabled")
                .value("true")
                .build(); 
        runPromise(() -> memoryStore.storePreference(pref)); 

        // When
        String value = runPromise(() -> memoryStore.getPreference("email_enabled"));

        // Then
        assertThat(value).isEqualTo("true");
    }

    @Test
    @DisplayName("Should get all preferences by namespace")
    void shouldGetPreferences() { 
        // Given
        Preference p1 = Preference.builder() 
                .agentId("agent-1")
                .namespace("ui")
                .key("theme")
                .value("dark")
                .build(); 
        Preference p2 = Preference.builder() 
                .agentId("agent-1")
                .namespace("ui")
                .key("language")
                .value("en")
                .build(); 

        runPromise(() -> memoryStore.storePreference(p1)); 
        runPromise(() -> memoryStore.storePreference(p2)); 

        // When
        Map<String, String> uiPrefs = runPromise(() -> memoryStore.getPreferences("ui"));

        // Then
        assertThat(uiPrefs).hasSize(2); 
        assertThat(uiPrefs).containsEntry("theme", "dark"); 
        assertThat(uiPrefs).containsEntry("language", "en"); 
    }

    @Test
    @DisplayName("Should clear all memory")
    void shouldClearMemory() { 
        // Given
        Episode episode = Episode.builder() 
                .agentId("agent-1")
                .turnId("turn-1")
                .timestamp(Instant.now()) 
                .input("test")
                .build(); 
        Fact fact = Fact.builder() 
                .agentId("agent-1")
                .subject("A")
                .predicate("is")
                .object("B")
                .build(); 

        runPromise(() -> memoryStore.storeEpisode(episode)); 
        runPromise(() -> memoryStore.storeFact(fact)); 

        // When
        int cleared = runPromise(() -> memoryStore.clearMemory()); 

        // Then
        assertThat(cleared).isEqualTo(2); 

        // Verify all empty
        MemoryStats stats = runPromise(() -> memoryStore.getStats()); 
        assertThat(stats.getEpisodeCount()).isEqualTo(0); 
        assertThat(stats.getFactCount()).isEqualTo(0); 
    }

    @Test
    @DisplayName("Should filter negative knowledge by mastery state metadata")
    void shouldFilterNegativeKnowledgeByMasteryState() {
        // Given
        NegativeKnowledge practiced = NegativeKnowledge.builder()
                .id("nk-practiced")
                .skillId("skill-1")
                .failureMode("timeout")
                .description("Observed timeout under high load")
                .timestamp(Instant.now().minus(2, ChronoUnit.MINUTES))
                .tenantId("tenant-a")
                .metadata(Map.of("masteryState", "PRACTICED"))
                .build();
        NegativeKnowledge mastered = NegativeKnowledge.builder()
                .id("nk-mastered")
                .skillId("skill-1")
                .failureMode("schema")
                .description("Schema drift in mastered flow")
                .timestamp(Instant.now().minus(1, ChronoUnit.MINUTES))
                .tenantId("tenant-a")
                .metadata(Map.of("masteryState", "MASTERED"))
                .build();

        runPromise(() -> memoryStore.storeNegativeKnowledge(practiced));
        runPromise(() -> memoryStore.storeNegativeKnowledge(mastered));

        // When
        List<NegativeKnowledge> results = runPromise(() ->
                memoryStore.queryNegativeKnowledgeByMasteryState(MasteryState.PRACTICED, 10));

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).id()).isEqualTo("nk-practiced");
    }

    @Test
    @DisplayName("Should filter negative knowledge by version context digest")
    void shouldFilterNegativeKnowledgeByVersionContext() {
        // Given
        VersionContext matchingContext = VersionContext.of(
                Map.of("spring", "3.2.0"),
                Map.of("jvm", "21"),
                Map.of("gradle", "8.9"),
                Map.of("data-cloud-api", "v1"),
                "git:abc123",
                Instant.now());
        String matchingDigest = VersionContextCodec.INSTANCE.encodeWithDigest(matchingContext).digest();

        NegativeKnowledge matching = NegativeKnowledge.builder()
                .id("nk-version-match")
                .skillId("skill-2")
                .failureMode("compatibility")
                .description("Fails with incompatible Spring version")
                .timestamp(Instant.now().minus(2, ChronoUnit.MINUTES))
                .tenantId("tenant-a")
                .metadata(Map.of("versionContextDigest", matchingDigest))
                .build();
        NegativeKnowledge nonMatching = NegativeKnowledge.builder()
                .id("nk-version-miss")
                .skillId("skill-2")
                .failureMode("compatibility")
                .description("Different runtime context")
                .timestamp(Instant.now().minus(1, ChronoUnit.MINUTES))
                .tenantId("tenant-a")
                .metadata(Map.of("versionContextDigest", "0000000000000000"))
                .build();

        runPromise(() -> memoryStore.storeNegativeKnowledge(matching));
        runPromise(() -> memoryStore.storeNegativeKnowledge(nonMatching));

        // When
        List<NegativeKnowledge> results = runPromise(() ->
                memoryStore.queryNegativeKnowledgeByVersionContext(matchingContext, 10));

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).id()).isEqualTo("nk-version-match");
    }

    @Test
    @DisplayName("Should filter negative knowledge by tenant")
    void shouldFilterNegativeKnowledgeByTenant() {
        // Given
        NegativeKnowledge tenantA = NegativeKnowledge.builder()
                .id("nk-tenant-a")
                .skillId("skill-3")
                .failureMode("security")
                .description("Tenant A specific guardrail")
                .timestamp(Instant.now().minus(2, ChronoUnit.MINUTES))
                .tenantId("tenant-a")
                .metadata(Map.of())
                .build();
        NegativeKnowledge tenantB = NegativeKnowledge.builder()
                .id("nk-tenant-b")
                .skillId("skill-3")
                .failureMode("security")
                .description("Tenant B specific guardrail")
                .timestamp(Instant.now().minus(1, ChronoUnit.MINUTES))
                .tenantId("tenant-b")
                .metadata(Map.of())
                .build();

        runPromise(() -> memoryStore.storeNegativeKnowledge(tenantA));
        runPromise(() -> memoryStore.storeNegativeKnowledge(tenantB));

        // When
        List<NegativeKnowledge> results = runPromise(() -> memoryStore.queryNegativeKnowledgeByTenant("tenant-a", 10));

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).id()).isEqualTo("nk-tenant-a");
    }

    @Test
    @DisplayName("Should return memory statistics")
    void shouldGetStats() { 
        // Given
        Episode ep = Episode.builder() 
                .agentId("agent-1")
                .turnId("turn-1")
                .timestamp(Instant.now()) 
                .input("test")
                .build(); 
        Fact fact = Fact.builder() 
                .agentId("agent-1")
                .subject("A")
                .predicate("is")
                .object("B")
                .build(); 

        runPromise(() -> memoryStore.storeEpisode(ep)); 
        runPromise(() -> memoryStore.storeFact(fact)); 

        // When
        MemoryStats stats = runPromise(() -> memoryStore.getStats()); 

        // Then
        assertThat(stats.getEpisodeCount()).isEqualTo(1); 
        assertThat(stats.getFactCount()).isEqualTo(1); 
        assertThat(stats.getPolicyCount()).isEqualTo(0); 
        assertThat(stats.getPreferenceCount()).isEqualTo(0); 
        assertThat(stats.getTotalSizeBytes()).isGreaterThan(0); 
    }
}
