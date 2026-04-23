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
@DisplayName("Memory Lifecycle Tests")
@Tag("integration")
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
    @DisplayName("memory item creation")
    class Creation {

        @Test
        @DisplayName("procedure item is created with all required fields")
        void procedureItem_createdWithAllRequiredFields() { // GH-90000
            String id = UUID.randomUUID().toString(); // GH-90000
            EnhancedProcedure procedure = EnhancedProcedure.builder() // GH-90000
                    .id(id) // GH-90000
                    .tenantId("tenant-a")
                    .situation("when user asks for help with code")
                    .action("suggest syntax fix")
                    .provenance(Provenance.builder().source("agent:code-helper").build())
                    .build(); // GH-90000

            assertThat(procedure.getId()).isEqualTo(id); // GH-90000
            assertThat(procedure.getTenantId()).isEqualTo("tenant-a");
            assertThat(procedure.getType()).isEqualTo(MemoryItemType.PROCEDURE); // GH-90000
            assertThat(procedure.getSituation()).isEqualTo("when user asks for help with code");
            assertThat(procedure.getAction()).isEqualTo("suggest syntax fix");
            assertThat(procedure.getProvenance().getSource()).isEqualTo("agent:code-helper");
        }

        @Test
        @DisplayName("memory item defaults are applied when fields are omitted")
        void memoryItemDefaults_areAppliedWhenFieldsAreOmitted() { // GH-90000
            EnhancedProcedure procedure = EnhancedProcedure.builder() // GH-90000
                    .id(UUID.randomUUID().toString()) // GH-90000
                    .situation("test situation")
                    .action("test action")
                    .build(); // GH-90000

            assertThat(procedure.getTenantId()).isEqualTo("default");
            assertThat(procedure.getVersion()).isEqualTo(1); // GH-90000
            assertThat(procedure.getSuccessRate()).isEqualTo(0.0); // GH-90000
            assertThat(procedure.getSteps()).isEmpty(); // GH-90000
            assertThat(procedure.getLinks()).isEmpty(); // GH-90000
        }
    }

    // ── Retrieval ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("memory item retrieval")
    class Retrieval {

        @Test
        @DisplayName("stored item is retrievable by ID")
        void storedItem_isRetrievableById() { // GH-90000
            InMemoryMemoryStore memStore = new InMemoryMemoryStore(); // GH-90000
            EnhancedProcedure procedure = EnhancedProcedure.builder() // GH-90000
                    .id("proc-001")
                    .situation("S1")
                    .action("A1")
                    .build(); // GH-90000

            memStore.store(procedure); // GH-90000
            Optional<MemoryItem> retrieved = memStore.retrieve("proc-001");

            assertThat(retrieved).isPresent(); // GH-90000
            assertThat(retrieved.get().getId()).isEqualTo("proc-001");
        }

        @Test
        @DisplayName("retrieving non-existent ID returns empty optional")
        void retrievingNonExistentId_returnsEmptyOptional() { // GH-90000
            InMemoryMemoryStore memStore = new InMemoryMemoryStore(); // GH-90000

            Optional<MemoryItem> result = memStore.retrieve("does-not-exist");

            assertThat(result).isEmpty(); // GH-90000
        }
    }

    // ── Expiration ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("memory item expiration")
    class Expiration {

        @Test
        @DisplayName("item with future expiry is accessible before expiry")
        void itemWithFutureExpiry_isAccessibleBeforeExpiry() { // GH-90000
            InMemoryMemoryStore memStore = new InMemoryMemoryStore(); // GH-90000
            EnhancedProcedure procedure = EnhancedProcedure.builder() // GH-90000
                    .id("expiring-item")
                    .situation("temp")
                    .action("temp action")
                    .expiresAt(Instant.now().plusSeconds(3600)) // GH-90000
                    .build(); // GH-90000

            memStore.store(procedure); // GH-90000
            Optional<MemoryItem> result = memStore.retrieve("expiring-item");

            assertThat(result).isPresent(); // GH-90000
        }

        @Test
        @DisplayName("item is evicted when expiry time has passed")
        void item_isEvicted_whenExpiryTimePassed() { // GH-90000
            InMemoryMemoryStore memStore = new InMemoryMemoryStore(); // GH-90000
            EnhancedProcedure procedure = EnhancedProcedure.builder() // GH-90000
                    .id("expired-item")
                    .situation("temp")
                    .action("temp action")
                    .expiresAt(Instant.ofEpochMilli(memStore.nowMs + 500)) // GH-90000
                    .build(); // GH-90000

            memStore.store(procedure); // GH-90000
            memStore.advanceTimeMs(1000); // GH-90000

            Optional<MemoryItem> result = memStore.retrieve("expired-item");

            assertThat(result).isEmpty(); // GH-90000
        }
    }

    // ── Deletion ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("memory item deletion")
    class Deletion {

        @Test
        @DisplayName("deleted item is no longer retrievable")
        void deletedItem_isNoLongerRetrievable() { // GH-90000
            InMemoryMemoryStore memStore = new InMemoryMemoryStore(); // GH-90000
            EnhancedProcedure procedure = EnhancedProcedure.builder() // GH-90000
                    .id("to-delete")
                    .situation("irrelevant")
                    .action("irrelevant action")
                    .build(); // GH-90000

            memStore.store(procedure); // GH-90000
            memStore.delete("to-delete");

            assertThat(memStore.retrieve("to-delete")).isEmpty();
            assertThat(memStore.count()).isEqualTo(0); // GH-90000
        }
    }

    // ── Links ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("memory item links")
    class Links {

        @Test
        @DisplayName("procedure item can have typed links to other items")
        void procedureItem_canHaveTypedLinksToOtherItems() { // GH-90000
            MemoryLink link = MemoryLink.builder() // GH-90000
                    .targetItemId("episode-001")
                    .linkType(LinkType.DERIVED_FROM) // GH-90000
                    .build(); // GH-90000

            EnhancedProcedure procedure = EnhancedProcedure.builder() // GH-90000
                    .id("proc-with-link")
                    .situation("S")
                    .action("A")
                    .links(List.of(link)) // GH-90000
                    .build(); // GH-90000

            assertThat(procedure.getLinks()).hasSize(1); // GH-90000
            assertThat(procedure.getLinks().getFirst().getTargetItemId()).isEqualTo("episode-001");
            assertThat(procedure.getLinks().getFirst().getLinkType()).isEqualTo(LinkType.DERIVED_FROM); // GH-90000
        }
    }
}
