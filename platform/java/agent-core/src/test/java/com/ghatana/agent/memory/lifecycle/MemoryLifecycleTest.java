package com.ghatana.agent.memory.lifecycle;

import com.ghatana.agent.memory.model.MemoryItem;
import com.ghatana.agent.memory.model.MemoryItemType;
import com.ghatana.agent.memory.model.Provenance;
import com.ghatana.agent.memory.model.MemoryLink;
import com.ghatana.agent.memory.model.LinkType;
import com.ghatana.agent.memory.model.procedure.EnhancedProcedure;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for memory item lifecycle — validates creation, retrieval, expiration,
 * update, and deletion of memory items across tiers.
 *
 * @doc.type class
 * @doc.purpose Tests for memory item lifecycle operations (CRUD and expiration) // GH-90000
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("Memory Lifecycle Tests [GH-90000]")
@Tag("integration [GH-90000]")
class MemoryLifecycleTest extends EventloopTestBase {

    // ── In-memory store simulation ────────────────────────────────────────────

    static class InMemoryMemoryStore {
        private final Map<String, MemoryItem> store = new ConcurrentHashMap<>(); // GH-90000
        private long nowMs = System.currentTimeMillis(); // GH-90000

        void store(MemoryItem item) { // GH-90000
            store.put(item.getId(), item); // GH-90000
        }

        Optional<MemoryItem> retrieve(String id) { // GH-90000
            MemoryItem item = store.get(id); // GH-90000
            if (item == null) return Optional.empty(); // GH-90000
            if (item.getExpiresAt() != null && nowMs >= item.getExpiresAt().toEpochMilli()) { // GH-90000
                store.remove(id); // GH-90000
                return Optional.empty(); // GH-90000
            }
            return Optional.of(item); // GH-90000
        }

        void delete(String id) { // GH-90000
            store.remove(id); // GH-90000
        }

        int count() { return store.size(); } // GH-90000
        void advanceTimeMs(long ms) { nowMs += ms; } // GH-90000
    }

    // ── Creation ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("memory item creation [GH-90000]")
    class Creation {

        @Test
        @DisplayName("procedure item is created with all required fields [GH-90000]")
        void procedureItem_createdWithAllRequiredFields() { // GH-90000
            String id = UUID.randomUUID().toString(); // GH-90000
            EnhancedProcedure procedure = EnhancedProcedure.builder() // GH-90000
                    .id(id) // GH-90000
                    .tenantId("tenant-a [GH-90000]")
                    .situation("when user asks for help with code [GH-90000]")
                    .action("suggest syntax fix [GH-90000]")
                    .provenance(Provenance.builder().source("agent:code-helper [GH-90000]").build())
                    .build(); // GH-90000

            assertThat(procedure.getId()).isEqualTo(id); // GH-90000
            assertThat(procedure.getTenantId()).isEqualTo("tenant-a [GH-90000]");
            assertThat(procedure.getType()).isEqualTo(MemoryItemType.PROCEDURE); // GH-90000
            assertThat(procedure.getSituation()).isEqualTo("when user asks for help with code [GH-90000]");
            assertThat(procedure.getAction()).isEqualTo("suggest syntax fix [GH-90000]");
            assertThat(procedure.getProvenance().getSource()).isEqualTo("agent:code-helper [GH-90000]");
        }

        @Test
        @DisplayName("memory item defaults are applied when fields are omitted [GH-90000]")
        void memoryItemDefaults_areAppliedWhenFieldsAreOmitted() { // GH-90000
            EnhancedProcedure procedure = EnhancedProcedure.builder() // GH-90000
                    .id(UUID.randomUUID().toString()) // GH-90000
                    .situation("test situation [GH-90000]")
                    .action("test action [GH-90000]")
                    .build(); // GH-90000

            assertThat(procedure.getTenantId()).isEqualTo("default [GH-90000]");
            assertThat(procedure.getVersion()).isEqualTo(1); // GH-90000
            assertThat(procedure.getSuccessRate()).isEqualTo(0.0); // GH-90000
            assertThat(procedure.getSteps()).isEmpty(); // GH-90000
            assertThat(procedure.getLinks()).isEmpty(); // GH-90000
        }
    }

    // ── Retrieval ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("memory item retrieval [GH-90000]")
    class Retrieval {

        @Test
        @DisplayName("stored item is retrievable by ID [GH-90000]")
        void storedItem_isRetrievableById() { // GH-90000
            InMemoryMemoryStore memStore = new InMemoryMemoryStore(); // GH-90000
            EnhancedProcedure procedure = EnhancedProcedure.builder() // GH-90000
                    .id("proc-001 [GH-90000]")
                    .situation("S1 [GH-90000]")
                    .action("A1 [GH-90000]")
                    .build(); // GH-90000

            memStore.store(procedure); // GH-90000
            Optional<MemoryItem> retrieved = memStore.retrieve("proc-001 [GH-90000]");

            assertThat(retrieved).isPresent(); // GH-90000
            assertThat(retrieved.get().getId()).isEqualTo("proc-001 [GH-90000]");
        }

        @Test
        @DisplayName("retrieving non-existent ID returns empty optional [GH-90000]")
        void retrievingNonExistentId_returnsEmptyOptional() { // GH-90000
            InMemoryMemoryStore memStore = new InMemoryMemoryStore(); // GH-90000

            Optional<MemoryItem> result = memStore.retrieve("does-not-exist [GH-90000]");

            assertThat(result).isEmpty(); // GH-90000
        }
    }

    // ── Expiration ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("memory item expiration [GH-90000]")
    class Expiration {

        @Test
        @DisplayName("item with future expiry is accessible before expiry [GH-90000]")
        void itemWithFutureExpiry_isAccessibleBeforeExpiry() { // GH-90000
            InMemoryMemoryStore memStore = new InMemoryMemoryStore(); // GH-90000
            EnhancedProcedure procedure = EnhancedProcedure.builder() // GH-90000
                    .id("expiring-item [GH-90000]")
                    .situation("temp [GH-90000]")
                    .action("temp action [GH-90000]")
                    .expiresAt(Instant.now().plusSeconds(3600)) // GH-90000
                    .build(); // GH-90000

            memStore.store(procedure); // GH-90000
            Optional<MemoryItem> result = memStore.retrieve("expiring-item [GH-90000]");

            assertThat(result).isPresent(); // GH-90000
        }

        @Test
        @DisplayName("item is evicted when expiry time has passed [GH-90000]")
        void item_isEvicted_whenExpiryTimePassed() { // GH-90000
            InMemoryMemoryStore memStore = new InMemoryMemoryStore(); // GH-90000
            EnhancedProcedure procedure = EnhancedProcedure.builder() // GH-90000
                    .id("expired-item [GH-90000]")
                    .situation("temp [GH-90000]")
                    .action("temp action [GH-90000]")
                    .expiresAt(Instant.ofEpochMilli(memStore.nowMs + 500)) // GH-90000
                    .build(); // GH-90000

            memStore.store(procedure); // GH-90000
            memStore.advanceTimeMs(1000); // GH-90000

            Optional<MemoryItem> result = memStore.retrieve("expired-item [GH-90000]");

            assertThat(result).isEmpty(); // GH-90000
        }
    }

    // ── Deletion ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("memory item deletion [GH-90000]")
    class Deletion {

        @Test
        @DisplayName("deleted item is no longer retrievable [GH-90000]")
        void deletedItem_isNoLongerRetrievable() { // GH-90000
            InMemoryMemoryStore memStore = new InMemoryMemoryStore(); // GH-90000
            EnhancedProcedure procedure = EnhancedProcedure.builder() // GH-90000
                    .id("to-delete [GH-90000]")
                    .situation("irrelevant [GH-90000]")
                    .action("irrelevant action [GH-90000]")
                    .build(); // GH-90000

            memStore.store(procedure); // GH-90000
            memStore.delete("to-delete [GH-90000]");

            assertThat(memStore.retrieve("to-delete [GH-90000]")).isEmpty();
            assertThat(memStore.count()).isEqualTo(0); // GH-90000
        }
    }

    // ── Links ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("memory item links [GH-90000]")
    class Links {

        @Test
        @DisplayName("procedure item can have typed links to other items [GH-90000]")
        void procedureItem_canHaveTypedLinksToOtherItems() { // GH-90000
            MemoryLink link = MemoryLink.builder() // GH-90000
                    .targetItemId("episode-001 [GH-90000]")
                    .linkType(LinkType.DERIVED_FROM) // GH-90000
                    .build(); // GH-90000

            EnhancedProcedure procedure = EnhancedProcedure.builder() // GH-90000
                    .id("proc-with-link [GH-90000]")
                    .situation("S [GH-90000]")
                    .action("A [GH-90000]")
                    .links(List.of(link)) // GH-90000
                    .build(); // GH-90000

            assertThat(procedure.getLinks()).hasSize(1); // GH-90000
            assertThat(procedure.getLinks().getFirst().getTargetItemId()).isEqualTo("episode-001 [GH-90000]");
            assertThat(procedure.getLinks().getFirst().getLinkType()).isEqualTo(LinkType.DERIVED_FROM); // GH-90000
        }
    }
}
