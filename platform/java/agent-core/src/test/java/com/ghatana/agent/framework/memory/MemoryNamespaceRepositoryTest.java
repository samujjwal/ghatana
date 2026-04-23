/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.agent.framework.memory;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link MemoryNamespace} record validation and
 * {@link InMemoryMemoryNamespaceRepository} contract behaviour.
 *
 * @doc.type class
 * @doc.purpose MemoryNamespace + InMemoryMemoryNamespaceRepository tests
 * @doc.layer framework
 * @doc.pattern Test
 */
@DisplayName("MemoryNamespace + InMemoryMemoryNamespaceRepository")
class MemoryNamespaceRepositoryTest extends EventloopTestBase {

    private static final String AGENT_ID  = "agent-ns-001";
    private static final String TENANT_ID = "tenant-ns-test";
    private static final Instant NOW      = Instant.parse("2026-04-01T10:00:00Z");

    private InMemoryMemoryNamespaceRepository repo;

    @BeforeEach
    void setUp() { // GH-90000
        repo = new InMemoryMemoryNamespaceRepository(); // GH-90000
    }

    // ─────────────────── helpers ──────────────────────────────────────────────

    private MemoryNamespace episodicNs(String nsId) { // GH-90000
        return MemoryNamespace.of(nsId, TENANT_ID, AGENT_ID, MemoryScope.EPISODIC, "Episodic Log", NOW); // GH-90000
    }

    private MemoryNamespace promotableProceduralNs(String nsId) { // GH-90000
        return new MemoryNamespace(nsId, TENANT_ID, AGENT_ID, MemoryScope.PROCEDURAL, // GH-90000
                "Procedural Skills", "Promoted skills", 365, true, 1000, NOW, NOW, Map.of()); // GH-90000
    }

    // ─────────────────── MemoryNamespace record validation ───────────────────

    @Nested
    @DisplayName("MemoryNamespace validation")
    class RecordValidation {

        @Test
        @DisplayName("factory method creates valid namespace")
        void factoryCreatesValidNamespace() { // GH-90000
            MemoryNamespace ns = episodicNs("ns-1");
            assertThat(ns.namespaceId()).isEqualTo("ns-1");
            assertThat(ns.tenantId()).isEqualTo(TENANT_ID); // GH-90000
            assertThat(ns.agentId()).isEqualTo(AGENT_ID); // GH-90000
            assertThat(ns.scope()).isEqualTo(MemoryScope.EPISODIC); // GH-90000
            assertThat(ns.label()).isEqualTo("Episodic Log");
            assertThat(ns.promotionEnabled()).isFalse(); // GH-90000
            assertThat(ns.retentionDays()).isNull(); // GH-90000
            assertThat(ns.maxEntries()).isNull(); // GH-90000
        }

        @Test
        @DisplayName("blank namespaceId is rejected")
        void blankNamespaceIdRejected() { // GH-90000
            assertThatThrownBy(() -> MemoryNamespace.of("  ", TENANT_ID, AGENT_ID, MemoryScope.EPISODIC, "Label", NOW)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("namespaceId");
        }

        @Test
        @DisplayName("blank label is rejected")
        void blankLabelRejected() { // GH-90000
            assertThatThrownBy(() -> MemoryNamespace.of("ns-1", TENANT_ID, AGENT_ID, MemoryScope.EPISODIC, "  ", NOW)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("label");
        }

        @Test
        @DisplayName("negative retentionDays is rejected")
        void negativeRetentionDaysRejected() { // GH-90000
            assertThatThrownBy(() -> new MemoryNamespace( // GH-90000
                    "ns-1", TENANT_ID, AGENT_ID, MemoryScope.EPISODIC, "Label",
                    null, -1, false, null, NOW, NOW, Map.of())) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("retentionDays");
        }

        @Test
        @DisplayName("data map is made immutable on construction")
        void dataMapIsImmutable() { // GH-90000
            Map<String, Object> mutable = new java.util.HashMap<>(); // GH-90000
            mutable.put("key", "value"); // GH-90000
            MemoryNamespace ns = new MemoryNamespace( // GH-90000
                    "ns-1", TENANT_ID, AGENT_ID, MemoryScope.SEMANTIC, "Label",
                    null, null, false, null, NOW, NOW, mutable);
            assertThatThrownBy(() -> ns.data().put("extra", "data")) // GH-90000
                    .isInstanceOf(UnsupportedOperationException.class); // GH-90000
        }

        @Test
        @DisplayName("null scope is rejected")
        void nullScopeRejected() { // GH-90000
            assertThatThrownBy(() -> new MemoryNamespace( // GH-90000
                    "ns-1", TENANT_ID, AGENT_ID, null, "Label",
                    null, null, false, null, NOW, NOW, Map.of())) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }
    }

    // ─────────────────── Repository: save + findById ───────────────────────

    @Nested
    @DisplayName("save and findById")
    class SaveAndFindById {

        @Test
        @DisplayName("save returns the same instance")
        void saveReturnsSameInstance() { // GH-90000
            MemoryNamespace ns = episodicNs("ns-1");
            MemoryNamespace saved = runPromise(() -> repo.save(ns)); // GH-90000
            assertThat(saved).isSameAs(ns); // GH-90000
        }

        @Test
        @DisplayName("findById returns saved namespace")
        void findByIdReturnsSaved() { // GH-90000
            MemoryNamespace ns = episodicNs("ns-1");
            runPromise(() -> repo.save(ns)); // GH-90000
            Optional<MemoryNamespace> found = runPromise(() -> repo.findById("ns-1"));
            assertThat(found).isPresent().contains(ns); // GH-90000
        }

        @Test
        @DisplayName("findById returns empty for unknown ID")
        void findByIdEmptyForUnknown() { // GH-90000
            Optional<MemoryNamespace> result = runPromise(() -> repo.findById("unknown"));
            assertThat(result).isEmpty(); // GH-90000
        }
    }

    // ─────────────────── Repository: findByAgent ───────────────────────────

    @Nested
    @DisplayName("findByAgent")
    class FindByAgent {

        @Test
        @DisplayName("returns all namespaces for an agent")
        void returnsAllForAgent() { // GH-90000
            runPromise(() -> repo.save(episodicNs("ns-1")));
            runPromise(() -> repo.save(promotableProceduralNs("ns-2")));
            List<MemoryNamespace> list = runPromise(() -> repo.findByAgent(AGENT_ID, TENANT_ID)); // GH-90000
            assertThat(list).hasSize(2); // GH-90000
        }

        @Test
        @DisplayName("returns empty for unknown agent")
        void emptyForUnknownAgent() { // GH-90000
            List<MemoryNamespace> list = runPromise(() -> repo.findByAgent("no-agent", TENANT_ID)); // GH-90000
            assertThat(list).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("findByAgentAndScope returns namespace for matching scope")
        void findByAgentAndScopeReturnsMatch() { // GH-90000
            runPromise(() -> repo.save(episodicNs("ns-1")));
            runPromise(() -> repo.save(promotableProceduralNs("ns-2")));
            Optional<MemoryNamespace> result = runPromise( // GH-90000
                    () -> repo.findByAgentAndScope(AGENT_ID, MemoryScope.PROCEDURAL, TENANT_ID)); // GH-90000
            assertThat(result).isPresent(); // GH-90000
            assertThat(result.get().scope()).isEqualTo(MemoryScope.PROCEDURAL); // GH-90000
        }

        @Test
        @DisplayName("findByAgentAndScope returns empty when scope not found")
        void findByAgentAndScopeEmptyWhenMissing() { // GH-90000
            runPromise(() -> repo.save(episodicNs("ns-1")));
            Optional<MemoryNamespace> result = runPromise( // GH-90000
                    () -> repo.findByAgentAndScope(AGENT_ID, MemoryScope.SEMANTIC, TENANT_ID)); // GH-90000
            assertThat(result).isEmpty(); // GH-90000
        }
    }

    // ─────────────────── Repository: findPromotionEnabled ──────────────────

    @Nested
    @DisplayName("findPromotionEnabledByAgent")
    class FindPromotionEnabled {

        @Test
        @DisplayName("returns only promotion-enabled namespaces")
        void returnsOnlyPromotionEnabled() { // GH-90000
            runPromise(() -> repo.save(episodicNs("ns-1")));          // not promotable
            runPromise(() -> repo.save(promotableProceduralNs("ns-2"))); // promotable
            List<MemoryNamespace> promotable = runPromise( // GH-90000
                    () -> repo.findPromotionEnabledByAgent(AGENT_ID, TENANT_ID)); // GH-90000
            assertThat(promotable).hasSize(1); // GH-90000
            assertThat(promotable.getFirst().promotionEnabled()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("returns empty when no promotion-enabled namespaces")
        void emptyWhenNonePromotable() { // GH-90000
            runPromise(() -> repo.save(episodicNs("ns-1")));
            List<MemoryNamespace> promotable = runPromise( // GH-90000
                    () -> repo.findPromotionEnabledByAgent(AGENT_ID, TENANT_ID)); // GH-90000
            assertThat(promotable).isEmpty(); // GH-90000
        }
    }

    // ─────────────────── Repository: delete ────────────────────────────────

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("deletes an existing namespace and returns true")
        void deletesExistingNamespace() { // GH-90000
            runPromise(() -> repo.save(episodicNs("ns-1")));
            boolean deleted = runPromise(() -> repo.delete("ns-1", TENANT_ID)); // GH-90000
            assertThat(deleted).isTrue(); // GH-90000
            Optional<MemoryNamespace> after = runPromise(() -> repo.findById("ns-1"));
            assertThat(after).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("returns false when namespace not found")
        void returnsFalseWhenNotFound() { // GH-90000
            boolean deleted = runPromise(() -> repo.delete("no-such-ns", TENANT_ID)); // GH-90000
            assertThat(deleted).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("returns false when tenant does not match")
        void returnsFalseWhenTenantMismatch() { // GH-90000
            runPromise(() -> repo.save(episodicNs("ns-1")));
            boolean deleted = runPromise(() -> repo.delete("ns-1", "wrong-tenant")); // GH-90000
            assertThat(deleted).isFalse(); // GH-90000
            Optional<MemoryNamespace> still = runPromise(() -> repo.findById("ns-1"));
            assertThat(still).isPresent(); // GH-90000
        }
    }
}
