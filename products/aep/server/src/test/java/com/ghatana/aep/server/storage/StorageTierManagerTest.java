/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.aep.server.storage;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.DataCloudClient.Entity;
import com.ghatana.datacloud.DataCloudClient.Query;
import com.ghatana.datacloud.StorageTier;
import com.ghatana.aep.server.storage.StorageTierManager.TieredEntity;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.ghatana.aep.server.storage.StorageTierManager.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link StorageTierManager}.
 *
 * <p>DataCloudClient is mocked with synchronous {@link Promise#of(Object)} // GH-90000
 * stubs so no Eventloop is required. Micrometer uses an in-memory
 * {@link SimpleMeterRegistry} to keep the registry state isolated.
 *
 * @doc.type class
 * @doc.purpose Unit tests for StorageTierManager — tiered lookups, promotion, and demotion
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("StorageTierManager [GH-90000]")
class StorageTierManagerTest {

    private static final String TENANT = "tenant-tier";
    private static final String COLL  = "aep_patterns";

    @Mock
    private DataCloudClient client;

    private StorageTierManager mgr;

    @BeforeEach
    void setUp() { // GH-90000
        mgr = new StorageTierManager(client, new SimpleMeterRegistry()); // GH-90000
    }

    // =========================================================================
    // save
    // =========================================================================

    @Nested
    @DisplayName("save() [GH-90000]")
    class SaveTests {

        @Test
        @DisplayName("save: writes to HOT (primary) collection [GH-90000]")
        void save_writesToHotCollection() { // GH-90000
            Map<String, Object> data = Map.of("id", "entity-1", "name", "Pattern Alpha"); // GH-90000
            Entity saved = entity("entity-1", data); // GH-90000
            when(client.save(eq(TENANT), eq(hotCollection(COLL)), anyMap())) // GH-90000
                    .thenReturn(Promise.of(saved)); // GH-90000

            Entity result = mgr.save(TENANT, COLL, data).getResult(); // GH-90000

            assertThat(result).isNotNull(); // GH-90000
            verify(client).save(eq(TENANT), eq(hotCollection(COLL)), anyMap()); // GH-90000
            verify(client, never()).save(eq(TENANT), startsWith(COLL + "_"), anyMap()); // GH-90000
        }

        @Test
        @DisplayName("save: enriches data with storageTier=HOT and lastAccessedAt [GH-90000]")
        void save_enrichesMetadata() { // GH-90000
            Map<String, Object> data = new HashMap<>(); // GH-90000
            data.put("id", "ent-42"); // GH-90000
            when(client.save(anyString(), anyString(), anyMap())) // GH-90000
                    .thenReturn(Promise.of(entity("ent-42", data))); // GH-90000

            mgr.save(TENANT, COLL, data).getResult(); // GH-90000

            var captor = mapCaptor(); // GH-90000
            verify(client).save(anyString(), anyString(), captor.capture()); // GH-90000
            assertThat(captor.getValue()).containsKey(TIER_FIELD); // GH-90000
            assertThat(captor.getValue().get(TIER_FIELD)).isEqualTo(StorageTier.HOT.name()); // GH-90000
            assertThat(captor.getValue()).containsKey(LAST_ACCESSED_FIELD); // GH-90000
        }

        @Test
        @DisplayName("save: propagates client failure as failed promise [GH-90000]")
        void save_whenClientFails_propagatesException() { // GH-90000
            when(client.save(anyString(), anyString(), anyMap())) // GH-90000
                    .thenReturn(Promise.ofException(new RuntimeException("DB offline [GH-90000]")));

            Promise<Entity> result = mgr.save(TENANT, COLL, Map.of("id", "ent")); // GH-90000

            assertThat(result.isException()).isTrue(); // GH-90000
        }
    }

    // =========================================================================
    // findById — tier cascade
    // =========================================================================

    @Nested
    @DisplayName("findById() — tier cascade [GH-90000]")
    class FindByIdTests {

        @Test
        @DisplayName("findById: returns HOT hit without checking lower tiers [GH-90000]")
        void findById_hotHit_noLowerTierLookup() { // GH-90000
            String id = "hot-entity";
            when(client.findById(TENANT, hotCollection(COLL), id)) // GH-90000
                    .thenReturn(Promise.of(Optional.of(entity(id, entData(id, StorageTier.HOT))))); // GH-90000

            Optional<TieredEntity> result = mgr.findById(TENANT, COLL, id).getResult(); // GH-90000

            assertThat(result).isPresent(); // GH-90000
            assertThat(result.get().tier()).isEqualTo(StorageTier.HOT); // GH-90000
            verify(client, never()).findById(TENANT, warmCollection(COLL), id); // GH-90000
            verify(client, never()).findById(TENANT, coolCollection(COLL), id); // GH-90000
        }

        @Test
        @DisplayName("findById: falls through to WARM when not in HOT [GH-90000]")
        void findById_warmHit_afterHotMiss() { // GH-90000
            String id = "warm-entity";
            when(client.findById(TENANT, hotCollection(COLL), id)) // GH-90000
                    .thenReturn(Promise.of(Optional.empty())); // GH-90000
            when(client.findById(TENANT, warmCollection(COLL), id)) // GH-90000
                    .thenReturn(Promise.of(Optional.of(entity(id, entData(id, StorageTier.WARM))))); // GH-90000
            // touchTimestamp on warm hit — save called with updated lastAccessedAt
            when(client.save(eq(TENANT), eq(warmCollection(COLL)), anyMap())) // GH-90000
                    .thenReturn(Promise.of(entity(id, entData(id, StorageTier.WARM)))); // GH-90000

            Optional<TieredEntity> result = mgr.findById(TENANT, COLL, id).getResult(); // GH-90000

            assertThat(result).isPresent(); // GH-90000
            assertThat(result.get().tier()).isEqualTo(StorageTier.WARM); // GH-90000
        }

        @Test
        @DisplayName("findById: promotes entity from COOL to WARM on hit [GH-90000]")
        void findById_coolHit_promotesToWarm() { // GH-90000
            String id = "cool-entity";
            Map<String, Object> coolData = entData(id, StorageTier.COOL); // GH-90000

            when(client.findById(TENANT, hotCollection(COLL), id)).thenReturn(Promise.of(Optional.empty())); // GH-90000
            when(client.findById(TENANT, warmCollection(COLL), id)).thenReturn(Promise.of(Optional.empty())); // GH-90000
            // First call: initial tier-cascade lookup; second call: deleteIfExists check inside promoteToWarm
            when(client.findById(TENANT, coolCollection(COLL), id)) // GH-90000
                    .thenReturn(Promise.of(Optional.of(entity(id, coolData)))) // GH-90000
                    .thenReturn(Promise.of(Optional.of(entity(id, coolData)))); // GH-90000
            // deleteIfExists also checks coldCollection — entity absent there
            when(client.findById(TENANT, coldCollection(COLL), id)) // GH-90000
                    .thenReturn(Promise.of(Optional.empty())); // GH-90000
            // Promotion: save to WARM, then delete from cool tier
            when(client.save(eq(TENANT), eq(warmCollection(COLL)), anyMap())) // GH-90000
                    .thenReturn(Promise.of(entity(id, coolData))); // GH-90000
            when(client.delete(anyString(), anyString(), anyString())) // GH-90000
                    .thenReturn(Promise.of((Void) null)); // GH-90000

            Optional<TieredEntity> result = mgr.findById(TENANT, COLL, id).getResult(); // GH-90000

            assertThat(result).isPresent(); // GH-90000
            assertThat(result.get().tier()).isEqualTo(StorageTier.COOL); // GH-90000
            verify(client).save(eq(TENANT), eq(warmCollection(COLL)), anyMap()); // GH-90000
        }

        @Test
        @DisplayName("findById: returns empty when entity absent from all tiers [GH-90000]")
        void findById_totalMiss_returnsEmpty() { // GH-90000
            String id = "ghost-entity";
            when(client.findById(anyString(), anyString(), eq(id))) // GH-90000
                    .thenReturn(Promise.of(Optional.empty())); // GH-90000

            Optional<TieredEntity> result = mgr.findById(TENANT, COLL, id).getResult(); // GH-90000

            assertThat(result).isEmpty(); // GH-90000
        }
    }

    // =========================================================================
    // queryHot / queryHotAndWarm
    // =========================================================================

    @Nested
    @DisplayName("queryHot() / queryHotAndWarm() [GH-90000]")
    class QueryTests {

        @Test
        @DisplayName("queryHot: queries only HOT collection [GH-90000]")
        void queryHot_queriesHotOnly() { // GH-90000
            Query q = Query.builder().limit(10).build(); // GH-90000
            when(client.query(TENANT, hotCollection(COLL), q)) // GH-90000
                    .thenReturn(Promise.of(List.of( // GH-90000
                            entity("e1", entData("e1", StorageTier.HOT)), // GH-90000
                            entity("e2", entData("e2", StorageTier.HOT)) // GH-90000
                    )));

            List<TieredEntity> results = mgr.queryHot(TENANT, COLL, q).getResult(); // GH-90000

            assertThat(results).hasSize(2); // GH-90000
            assertThat(results).extracting(TieredEntity::tier).containsOnly(StorageTier.HOT); // GH-90000
            verify(client, never()).query(eq(TENANT), eq(warmCollection(COLL)), any()); // GH-90000
        }

        @Test
        @DisplayName("queryHotAndWarm: merges results; HOT entity takes precedence over WARM duplicate [GH-90000]")
        void queryHotAndWarm_hotPrecedenceOnDuplicate() { // GH-90000
            Query q = Query.builder().limit(10).build(); // GH-90000
            String sharedId = "shared-ent";

            when(client.query(TENANT, hotCollection(COLL), q)) // GH-90000
                    .thenReturn(Promise.of(List.of(entity(sharedId, entData(sharedId, StorageTier.HOT))))); // GH-90000
            when(client.query(TENANT, warmCollection(COLL), q)) // GH-90000
                    .thenReturn(Promise.of(List.of(entity(sharedId, entData(sharedId, StorageTier.WARM))))); // GH-90000

            List<TieredEntity> results = mgr.queryHotAndWarm(TENANT, COLL, q).getResult(); // GH-90000

            assertThat(results).hasSize(1); // GH-90000
            assertThat(results.get(0).tier()).isEqualTo(StorageTier.HOT); // GH-90000
        }
    }

    // =========================================================================
    // demoteIdleEntities (HOT → WARM) // GH-90000
    // =========================================================================

    @Nested
    @DisplayName("demoteIdleEntities() [GH-90000]")
    class DemoteTests {

        @Test
        @DisplayName("demoteIdleEntities: moves idle HOT entities to WARM and deletes from HOT [GH-90000]")
        void demoteIdleEntities_movesHotToWarm() { // GH-90000
            Instant threshold = Instant.now().minusSeconds(3600); // GH-90000
            List<Entity> idle = List.of( // GH-90000
                    entity("e1", entData("e1", StorageTier.HOT)), // GH-90000
                    entity("e2", entData("e2", StorageTier.HOT)) // GH-90000
            );
            when(client.query(eq(TENANT), eq(hotCollection(COLL)), any(Query.class))) // GH-90000
                    .thenReturn(Promise.of(idle)); // GH-90000
            when(client.save(eq(TENANT), eq(warmCollection(COLL)), anyMap())) // GH-90000
                    .thenReturn(Promise.of(entity("ex", Map.of()))); // GH-90000
            when(client.delete(eq(TENANT), eq(hotCollection(COLL)), anyString())) // GH-90000
                    .thenReturn(Promise.of((Void) null)); // GH-90000

            int count = mgr.demoteIdleEntities(TENANT, COLL, threshold).getResult(); // GH-90000

            assertThat(count).isEqualTo(2); // GH-90000
            verify(client, times(2)).save(eq(TENANT), eq(warmCollection(COLL)), anyMap()); // GH-90000
            verify(client, times(2)).delete(eq(TENANT), eq(hotCollection(COLL)), anyString()); // GH-90000
        }

        @Test
        @DisplayName("demoteIdleEntities: returns 0 when no entities are idle [GH-90000]")
        void demoteIdleEntities_nothingToPromote() { // GH-90000
            when(client.query(eq(TENANT), eq(hotCollection(COLL)), any(Query.class))) // GH-90000
                    .thenReturn(Promise.of(List.of())); // GH-90000

            int count = mgr.demoteIdleEntities(TENANT, COLL, Instant.now()).getResult(); // GH-90000

            assertThat(count).isZero(); // GH-90000
            verify(client, never()).save(anyString(), anyString(), anyMap()); // GH-90000
        }
    }

    // =========================================================================
    // deleteFromAllTiers
    // =========================================================================

    @Nested
    @DisplayName("deleteFromAllTiers() [GH-90000]")
    class DeleteTests {

        @Test
        @DisplayName("deleteFromAllTiers: checks and deletes from all 4 tier collections [GH-90000]")
        void deleteFromAllTiers_checksAllTiers() { // GH-90000
            String id = "to-erase";
            // Entity exists in HOT only
            when(client.findById(TENANT, hotCollection(COLL), id)) // GH-90000
                    .thenReturn(Promise.of(Optional.of(entity(id, entData(id, StorageTier.HOT))))); // GH-90000
            when(client.findById(TENANT, warmCollection(COLL), id)) // GH-90000
                    .thenReturn(Promise.of(Optional.empty())); // GH-90000
            when(client.findById(TENANT, coolCollection(COLL), id)) // GH-90000
                    .thenReturn(Promise.of(Optional.empty())); // GH-90000
            when(client.findById(TENANT, coldCollection(COLL), id)) // GH-90000
                    .thenReturn(Promise.of(Optional.empty())); // GH-90000
            when(client.delete(eq(TENANT), eq(hotCollection(COLL)), eq(id))) // GH-90000
                    .thenReturn(Promise.of((Void) null)); // GH-90000

            mgr.deleteFromAllTiers(TENANT, COLL, id).getResult(); // GH-90000

            // Verify all tiers were checked
            verify(client).findById(TENANT, hotCollection(COLL), id); // GH-90000
            verify(client).findById(TENANT, warmCollection(COLL), id); // GH-90000
            verify(client).findById(TENANT, coolCollection(COLL), id); // GH-90000
            verify(client).findById(TENANT, coldCollection(COLL), id); // GH-90000
            // Delete called only for HOT (where entity existed) // GH-90000
            verify(client, times(1)).delete(eq(TENANT), anyString(), eq(id)); // GH-90000
        }
    }

    // =========================================================================
    // Collection name helpers
    // =========================================================================

    @Test
    @DisplayName("Collection name helpers return correct suffixes [GH-90000]")
    void collectionNameHelpers_returnExpectedSuffixes() { // GH-90000
        String base = "entities";
        assertThat(hotCollection(base)).isEqualTo("entities [GH-90000]");
        assertThat(warmCollection(base)).isEqualTo("entities_warm [GH-90000]");
        assertThat(coolCollection(base)).isEqualTo("entities_cool [GH-90000]");
        assertThat(coldCollection(base)).isEqualTo("entities_cold [GH-90000]");
    }

    // =========================================================================
    // TieredEntity helpers
    // =========================================================================

    @Test
    @DisplayName("TieredEntity.needsPromotionConsideration: true for non-HOT tiers [GH-90000]")
    void tieredEntity_needsPromotionConsideration_trueForNonHot() { // GH-90000
        Entity e = entity("x", entData("x", StorageTier.WARM)); // GH-90000
        TieredEntity hot  = new TieredEntity(e, StorageTier.HOT); // GH-90000
        TieredEntity warm = new TieredEntity(e, StorageTier.WARM); // GH-90000
        TieredEntity cold = new TieredEntity(e, StorageTier.COLD); // GH-90000

        assertThat(hot.needsPromotionConsideration()).isFalse(); // GH-90000
        assertThat(warm.needsPromotionConsideration()).isTrue(); // GH-90000
        assertThat(cold.needsPromotionConsideration()).isTrue(); // GH-90000
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static Entity entity(String id, Map<String, Object> data) { // GH-90000
        return new Entity(id, COLL, data, Instant.now(), Instant.now(), 1L); // GH-90000
    }

        @SuppressWarnings({"unchecked", "rawtypes"}) // GH-90000
        private static org.mockito.ArgumentCaptor<Map<String, Object>> mapCaptor() { // GH-90000
                return (org.mockito.ArgumentCaptor) org.mockito.ArgumentCaptor.forClass(Map.class); // GH-90000
        }

    private static Map<String, Object> entData(String id, StorageTier tier) { // GH-90000
        Map<String, Object> d = new HashMap<>(); // GH-90000
        d.put("id", id); // GH-90000
        d.put(TIER_FIELD, tier.name()); // GH-90000
        d.put(LAST_ACCESSED_FIELD, Instant.now().minusSeconds(7200).toString()); // GH-90000
        return d;
    }
}
