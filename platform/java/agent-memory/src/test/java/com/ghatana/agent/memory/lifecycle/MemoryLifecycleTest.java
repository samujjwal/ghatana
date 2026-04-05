package com.ghatana.agent.memory.lifecycle;

import com.ghatana.agent.memory.model.MemoryItem;
import com.ghatana.agent.memory.model.MemoryItemType;
import com.ghatana.agent.memory.model.Provenance;
import com.ghatana.agent.memory.model.Validity;
import com.ghatana.agent.memory.model.ValidityStatus;
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
 * @doc.purpose Tests for memory item lifecycle operations (CRUD and expiration)
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("Memory Lifecycle Tests")
@Tag("integration")
class MemoryLifecycleTest extends EventloopTestBase {

    // ── In-memory store simulation ────────────────────────────────────────────

    static class InMemoryMemoryStore {
        private final Map<String, MemoryItem> store = new ConcurrentHashMap<>();
        private long nowMs = System.currentTimeMillis();

        void store(MemoryItem item) {
            store.put(item.getId(), item);
        }

        Optional<MemoryItem> retrieve(String id) {
            MemoryItem item = store.get(id);
            if (item == null) return Optional.empty();
            if (item.getExpiresAt() != null && nowMs >= item.getExpiresAt().toEpochMilli()) {
                store.remove(id);
                return Optional.empty();
            }
            return Optional.of(item);
        }

        void delete(String id) {
            store.remove(id);
        }

        int count() { return store.size(); }
        void advanceTimeMs(long ms) { nowMs += ms; }
    }

    // ── Creation ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("memory item creation")
    class Creation {

        @Test
        @DisplayName("procedure item is created with all required fields")
        void procedureItem_createdWithAllRequiredFields() {
            String id = UUID.randomUUID().toString();
            EnhancedProcedure procedure = EnhancedProcedure.builder()
                    .id(id)
                    .tenantId("tenant-a")
                    .situation("when user asks for help with code")
                    .action("suggest syntax fix")
                    .provenance(Provenance.builder().source("agent:code-helper").build())
                    .build();

            assertThat(procedure.getId()).isEqualTo(id);
            assertThat(procedure.getTenantId()).isEqualTo("tenant-a");
            assertThat(procedure.getType()).isEqualTo(MemoryItemType.PROCEDURE);
            assertThat(procedure.getSituation()).isEqualTo("when user asks for help with code");
            assertThat(procedure.getAction()).isEqualTo("suggest syntax fix");
            assertThat(procedure.getProvenance().getSource()).isEqualTo("agent:code-helper");
        }

        @Test
        @DisplayName("memory item defaults are applied when fields are omitted")
        void memoryItemDefaults_areAppliedWhenFieldsAreOmitted() {
            EnhancedProcedure procedure = EnhancedProcedure.builder()
                    .id(UUID.randomUUID().toString())
                    .situation("test situation")
                    .action("test action")
                    .build();

            assertThat(procedure.getTenantId()).isEqualTo("default");
            assertThat(procedure.getVersion()).isEqualTo(1);
            assertThat(procedure.getSuccessRate()).isEqualTo(0.0);
            assertThat(procedure.getSteps()).isEmpty();
            assertThat(procedure.getLinks()).isEmpty();
        }
    }

    // ── Retrieval ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("memory item retrieval")
    class Retrieval {

        @Test
        @DisplayName("stored item is retrievable by ID")
        void storedItem_isRetrievableById() {
            InMemoryMemoryStore memStore = new InMemoryMemoryStore();
            EnhancedProcedure procedure = EnhancedProcedure.builder()
                    .id("proc-001")
                    .situation("S1")
                    .action("A1")
                    .build();

            memStore.store(procedure);
            Optional<MemoryItem> retrieved = memStore.retrieve("proc-001");

            assertThat(retrieved).isPresent();
            assertThat(retrieved.get().getId()).isEqualTo("proc-001");
        }

        @Test
        @DisplayName("retrieving non-existent ID returns empty optional")
        void retrievingNonExistentId_returnsEmptyOptional() {
            InMemoryMemoryStore memStore = new InMemoryMemoryStore();

            Optional<MemoryItem> result = memStore.retrieve("does-not-exist");

            assertThat(result).isEmpty();
        }
    }

    // ── Expiration ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("memory item expiration")
    class Expiration {

        @Test
        @DisplayName("item with future expiry is accessible before expiry")
        void itemWithFutureExpiry_isAccessibleBeforeExpiry() {
            InMemoryMemoryStore memStore = new InMemoryMemoryStore();
            EnhancedProcedure procedure = EnhancedProcedure.builder()
                    .id("expiring-item")
                    .situation("temp")
                    .action("temp action")
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .build();

            memStore.store(procedure);
            Optional<MemoryItem> result = memStore.retrieve("expiring-item");

            assertThat(result).isPresent();
        }

        @Test
        @DisplayName("item is evicted when expiry time has passed")
        void item_isEvicted_whenExpiryTimePassed() {
            InMemoryMemoryStore memStore = new InMemoryMemoryStore();
            EnhancedProcedure procedure = EnhancedProcedure.builder()
                    .id("expired-item")
                    .situation("temp")
                    .action("temp action")
                    .expiresAt(Instant.ofEpochMilli(memStore.nowMs + 500))
                    .build();

            memStore.store(procedure);
            memStore.advanceTimeMs(1000);

            Optional<MemoryItem> result = memStore.retrieve("expired-item");

            assertThat(result).isEmpty();
        }
    }

    // ── Deletion ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("memory item deletion")
    class Deletion {

        @Test
        @DisplayName("deleted item is no longer retrievable")
        void deletedItem_isNoLongerRetrievable() {
            InMemoryMemoryStore memStore = new InMemoryMemoryStore();
            EnhancedProcedure procedure = EnhancedProcedure.builder()
                    .id("to-delete")
                    .situation("irrelevant")
                    .action("irrelevant action")
                    .build();

            memStore.store(procedure);
            memStore.delete("to-delete");

            assertThat(memStore.retrieve("to-delete")).isEmpty();
            assertThat(memStore.count()).isEqualTo(0);
        }
    }

    // ── Links ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("memory item links")
    class Links {

        @Test
        @DisplayName("procedure item can have typed links to other items")
        void procedureItem_canHaveTypedLinksToOtherItems() {
            MemoryLink link = MemoryLink.builder()
                    .targetId("episode-001")
                    .linkType(LinkType.DERIVED_FROM)
                    .build();

            EnhancedProcedure procedure = EnhancedProcedure.builder()
                    .id("proc-with-link")
                    .situation("S")
                    .action("A")
                    .links(List.of(link))
                    .build();

            assertThat(procedure.getLinks()).hasSize(1);
            assertThat(procedure.getLinks().getFirst().getTargetId()).isEqualTo("episode-001");
            assertThat(procedure.getLinks().getFirst().getLinkType()).isEqualTo(LinkType.DERIVED_FROM);
        }
    }
}
