/*
 * Copyright (c) 2026 Ghatana Inc. 
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
    void setUp() { 
        repo = new InMemoryMemoryNamespaceRepository(); 
    }

    // ─────────────────── helpers ──────────────────────────────────────────────

    private MemoryNamespace episodicNs(String nsId) { 
        return MemoryNamespace.of(nsId, TENANT_ID, AGENT_ID, MemoryScope.EPISODIC, "Episodic Log", NOW); 
    }

    private MemoryNamespace promotableProceduralNs(String nsId) { 
        return new MemoryNamespace(nsId, TENANT_ID, AGENT_ID, MemoryScope.PROCEDURAL, 
                "Procedural Skills", "Promoted skills", 365, true, 1000, NOW, NOW, Map.of()); 
    }

    // ─────────────────── MemoryNamespace record validation ───────────────────

    @Nested
    @DisplayName("MemoryNamespace validation")
    class RecordValidation {

        @Test
        @DisplayName("factory method creates valid namespace")
        void factoryCreatesValidNamespace() { 
            MemoryNamespace ns = episodicNs("ns-1");
            assertThat(ns.namespaceId()).isEqualTo("ns-1");
            assertThat(ns.tenantId()).isEqualTo(TENANT_ID); 
            assertThat(ns.agentId()).isEqualTo(AGENT_ID); 
            assertThat(ns.scope()).isEqualTo(MemoryScope.EPISODIC); 
            assertThat(ns.label()).isEqualTo("Episodic Log");
            assertThat(ns.promotionEnabled()).isFalse(); 
            assertThat(ns.retentionDays()).isNull(); 
            assertThat(ns.maxEntries()).isNull(); 
        }

        @Test
        @DisplayName("blank namespaceId is rejected")
        void blankNamespaceIdRejected() { 
            assertThatThrownBy(() -> MemoryNamespace.of("  ", TENANT_ID, AGENT_ID, MemoryScope.EPISODIC, "Label", NOW)) 
                    .isInstanceOf(IllegalArgumentException.class) 
                    .hasMessageContaining("namespaceId");
        }

        @Test
        @DisplayName("blank label is rejected")
        void blankLabelRejected() { 
            assertThatThrownBy(() -> MemoryNamespace.of("ns-1", TENANT_ID, AGENT_ID, MemoryScope.EPISODIC, "  ", NOW)) 
                    .isInstanceOf(IllegalArgumentException.class) 
                    .hasMessageContaining("label");
        }

        @Test
        @DisplayName("negative retentionDays is rejected")
        void negativeRetentionDaysRejected() { 
            assertThatThrownBy(() -> new MemoryNamespace( 
                    "ns-1", TENANT_ID, AGENT_ID, MemoryScope.EPISODIC, "Label",
                    null, -1, false, null, NOW, NOW, Map.of())) 
                    .isInstanceOf(IllegalArgumentException.class) 
                    .hasMessageContaining("retentionDays");
        }

        @Test
        @DisplayName("data map is made immutable on construction")
        void dataMapIsImmutable() { 
            Map<String, Object> mutable = new java.util.HashMap<>(); 
            mutable.put("key", "value"); 
            MemoryNamespace ns = new MemoryNamespace( 
                    "ns-1", TENANT_ID, AGENT_ID, MemoryScope.SEMANTIC, "Label",
                    null, null, false, null, NOW, NOW, mutable);
            assertThatThrownBy(() -> ns.data().put("extra", "data")) 
                    .isInstanceOf(UnsupportedOperationException.class); 
        }

        @Test
        @DisplayName("null scope is rejected")
        void nullScopeRejected() { 
            assertThatThrownBy(() -> new MemoryNamespace( 
                    "ns-1", TENANT_ID, AGENT_ID, null, "Label",
                    null, null, false, null, NOW, NOW, Map.of())) 
                    .isInstanceOf(NullPointerException.class); 
        }
    }

    // ─────────────────── Repository: save + findById ───────────────────────

    @Nested
    @DisplayName("save and findById")
    class SaveAndFindById {

        @Test
        @DisplayName("save returns the same instance")
        void saveReturnsSameInstance() { 
            MemoryNamespace ns = episodicNs("ns-1");
            MemoryNamespace saved = runPromise(() -> repo.save(ns)); 
            assertThat(saved).isSameAs(ns); 
        }

        @Test
        @DisplayName("findById returns saved namespace")
        void findByIdReturnsSaved() { 
            MemoryNamespace ns = episodicNs("ns-1");
            runPromise(() -> repo.save(ns)); 
            Optional<MemoryNamespace> found = runPromise(() -> repo.findById("ns-1"));
            assertThat(found).isPresent().contains(ns); 
        }

        @Test
        @DisplayName("findById returns empty for unknown ID")
        void findByIdEmptyForUnknown() { 
            Optional<MemoryNamespace> result = runPromise(() -> repo.findById("unknown"));
            assertThat(result).isEmpty(); 
        }
    }

    // ─────────────────── Repository: findByAgent ───────────────────────────

    @Nested
    @DisplayName("findByAgent")
    class FindByAgent {

        @Test
        @DisplayName("returns all namespaces for an agent")
        void returnsAllForAgent() { 
            runPromise(() -> repo.save(episodicNs("ns-1")));
            runPromise(() -> repo.save(promotableProceduralNs("ns-2")));
            List<MemoryNamespace> list = runPromise(() -> repo.findByAgent(AGENT_ID, TENANT_ID)); 
            assertThat(list).hasSize(2); 
        }

        @Test
        @DisplayName("returns empty for unknown agent")
        void emptyForUnknownAgent() { 
            List<MemoryNamespace> list = runPromise(() -> repo.findByAgent("no-agent", TENANT_ID)); 
            assertThat(list).isEmpty(); 
        }

        @Test
        @DisplayName("findByAgentAndScope returns namespace for matching scope")
        void findByAgentAndScopeReturnsMatch() { 
            runPromise(() -> repo.save(episodicNs("ns-1")));
            runPromise(() -> repo.save(promotableProceduralNs("ns-2")));
            Optional<MemoryNamespace> result = runPromise( 
                    () -> repo.findByAgentAndScope(AGENT_ID, MemoryScope.PROCEDURAL, TENANT_ID)); 
            assertThat(result).isPresent(); 
            assertThat(result.get().scope()).isEqualTo(MemoryScope.PROCEDURAL); 
        }

        @Test
        @DisplayName("findByAgentAndScope returns empty when scope not found")
        void findByAgentAndScopeEmptyWhenMissing() { 
            runPromise(() -> repo.save(episodicNs("ns-1")));
            Optional<MemoryNamespace> result = runPromise( 
                    () -> repo.findByAgentAndScope(AGENT_ID, MemoryScope.SEMANTIC, TENANT_ID)); 
            assertThat(result).isEmpty(); 
        }
    }

    // ─────────────────── Repository: findPromotionEnabled ──────────────────

    @Nested
    @DisplayName("findPromotionEnabledByAgent")
    class FindPromotionEnabled {

        @Test
        @DisplayName("returns only promotion-enabled namespaces")
        void returnsOnlyPromotionEnabled() { 
            runPromise(() -> repo.save(episodicNs("ns-1")));          // not promotable
            runPromise(() -> repo.save(promotableProceduralNs("ns-2"))); // promotable
            List<MemoryNamespace> promotable = runPromise( 
                    () -> repo.findPromotionEnabledByAgent(AGENT_ID, TENANT_ID)); 
            assertThat(promotable).hasSize(1); 
            assertThat(promotable.getFirst().promotionEnabled()).isTrue(); 
        }

        @Test
        @DisplayName("returns empty when no promotion-enabled namespaces")
        void emptyWhenNonePromotable() { 
            runPromise(() -> repo.save(episodicNs("ns-1")));
            List<MemoryNamespace> promotable = runPromise( 
                    () -> repo.findPromotionEnabledByAgent(AGENT_ID, TENANT_ID)); 
            assertThat(promotable).isEmpty(); 
        }
    }

    // ─────────────────── Repository: delete ────────────────────────────────

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("deletes an existing namespace and returns true")
        void deletesExistingNamespace() { 
            runPromise(() -> repo.save(episodicNs("ns-1")));
            boolean deleted = runPromise(() -> repo.delete("ns-1", TENANT_ID)); 
            assertThat(deleted).isTrue(); 
            Optional<MemoryNamespace> after = runPromise(() -> repo.findById("ns-1"));
            assertThat(after).isEmpty(); 
        }

        @Test
        @DisplayName("returns false when namespace not found")
        void returnsFalseWhenNotFound() { 
            boolean deleted = runPromise(() -> repo.delete("no-such-ns", TENANT_ID)); 
            assertThat(deleted).isFalse(); 
        }

        @Test
        @DisplayName("returns false when tenant does not match")
        void returnsFalseWhenTenantMismatch() { 
            runPromise(() -> repo.save(episodicNs("ns-1")));
            boolean deleted = runPromise(() -> repo.delete("ns-1", "wrong-tenant")); 
            assertThat(deleted).isFalse(); 
            Optional<MemoryNamespace> still = runPromise(() -> repo.findById("ns-1"));
            assertThat(still).isPresent(); 
        }
    }
}
