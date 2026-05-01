/*
 * Copyright (c) 2026 Ghatana Inc. 
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
 * <p>DataCloudClient is mocked with synchronous {@link Promise#of(Object)} 
 * stubs so no Eventloop is required. Micrometer uses an in-memory
 * {@link SimpleMeterRegistry} to keep the registry state isolated.
 *
 * @doc.type class
 * @doc.purpose Unit tests for StorageTierManager — tiered lookups, promotion, and demotion
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) 
@DisplayName("StorageTierManager")
class StorageTierManagerTest {

    private static final String TENANT = "tenant-tier";
    private static final String COLL  = "aep_patterns";

    @Mock
    private DataCloudClient client;

    private StorageTierManager mgr;

    @BeforeEach
    void setUp() { 
        mgr = new StorageTierManager(client, new SimpleMeterRegistry()); 
    }

    // =========================================================================
    // save
    // =========================================================================

    @Nested
    @DisplayName("save()")
    class SaveTests {

        @Test
        @DisplayName("save: writes to HOT (primary) collection")
        void save_writesToHotCollection() { 
            Map<String, Object> data = Map.of("id", "entity-1", "name", "Pattern Alpha"); 
            Entity saved = entity("entity-1", data); 
            when(client.save(eq(TENANT), eq(hotCollection(COLL)), anyMap())) 
                    .thenReturn(Promise.of(saved)); 

            Entity result = mgr.save(TENANT, COLL, data).getResult(); 

            assertThat(result).isNotNull(); 
            verify(client).save(eq(TENANT), eq(hotCollection(COLL)), anyMap()); 
            verify(client, never()).save(eq(TENANT), startsWith(COLL + "_"), anyMap()); 
        }

        @Test
        @DisplayName("save: enriches data with storageTier=HOT and lastAccessedAt")
        void save_enrichesMetadata() { 
            Map<String, Object> data = new HashMap<>(); 
            data.put("id", "ent-42"); 
            when(client.save(anyString(), anyString(), anyMap())) 
                    .thenReturn(Promise.of(entity("ent-42", data))); 

            mgr.save(TENANT, COLL, data).getResult(); 

            var captor = mapCaptor(); 
            verify(client).save(anyString(), anyString(), captor.capture()); 
            assertThat(captor.getValue()).containsKey(TIER_FIELD); 
            assertThat(captor.getValue().get(TIER_FIELD)).isEqualTo(StorageTier.HOT.name()); 
            assertThat(captor.getValue()).containsKey(LAST_ACCESSED_FIELD); 
        }

        @Test
        @DisplayName("save: propagates client failure as failed promise")
        void save_whenClientFails_propagatesException() { 
            when(client.save(anyString(), anyString(), anyMap())) 
                    .thenReturn(Promise.ofException(new RuntimeException("DB offline")));

            Promise<Entity> result = mgr.save(TENANT, COLL, Map.of("id", "ent")); 

            assertThat(result.isException()).isTrue(); 
        }
    }

    // =========================================================================
    // findById — tier cascade
    // =========================================================================

    @Nested
    @DisplayName("findById() — tier cascade")
    class FindByIdTests {

        @Test
        @DisplayName("findById: returns HOT hit without checking lower tiers")
        void findById_hotHit_noLowerTierLookup() { 
            String id = "hot-entity";
            when(client.findById(TENANT, hotCollection(COLL), id)) 
                    .thenReturn(Promise.of(Optional.of(entity(id, entData(id, StorageTier.HOT))))); 

            Optional<TieredEntity> result = mgr.findById(TENANT, COLL, id).getResult(); 

            assertThat(result).isPresent(); 
            assertThat(result.get().tier()).isEqualTo(StorageTier.HOT); 
            verify(client, never()).findById(TENANT, warmCollection(COLL), id); 
            verify(client, never()).findById(TENANT, coolCollection(COLL), id); 
        }

        @Test
        @DisplayName("findById: falls through to WARM when not in HOT")
        void findById_warmHit_afterHotMiss() { 
            String id = "warm-entity";
            when(client.findById(TENANT, hotCollection(COLL), id)) 
                    .thenReturn(Promise.of(Optional.empty())); 
            when(client.findById(TENANT, warmCollection(COLL), id)) 
                    .thenReturn(Promise.of(Optional.of(entity(id, entData(id, StorageTier.WARM))))); 
            // touchTimestamp on warm hit — save called with updated lastAccessedAt
            when(client.save(eq(TENANT), eq(warmCollection(COLL)), anyMap())) 
                    .thenReturn(Promise.of(entity(id, entData(id, StorageTier.WARM)))); 

            Optional<TieredEntity> result = mgr.findById(TENANT, COLL, id).getResult(); 

            assertThat(result).isPresent(); 
            assertThat(result.get().tier()).isEqualTo(StorageTier.WARM); 
        }

        @Test
        @DisplayName("findById: promotes entity from COOL to WARM on hit")
        void findById_coolHit_promotesToWarm() { 
            String id = "cool-entity";
            Map<String, Object> coolData = entData(id, StorageTier.COOL); 

            when(client.findById(TENANT, hotCollection(COLL), id)).thenReturn(Promise.of(Optional.empty())); 
            when(client.findById(TENANT, warmCollection(COLL), id)).thenReturn(Promise.of(Optional.empty())); 
            // First call: initial tier-cascade lookup; second call: deleteIfExists check inside promoteToWarm
            when(client.findById(TENANT, coolCollection(COLL), id)) 
                    .thenReturn(Promise.of(Optional.of(entity(id, coolData)))) 
                    .thenReturn(Promise.of(Optional.of(entity(id, coolData)))); 
            // deleteIfExists also checks coldCollection — entity absent there
            when(client.findById(TENANT, coldCollection(COLL), id)) 
                    .thenReturn(Promise.of(Optional.empty())); 
            // Promotion: save to WARM, then delete from cool tier
            when(client.save(eq(TENANT), eq(warmCollection(COLL)), anyMap())) 
                    .thenReturn(Promise.of(entity(id, coolData))); 
            when(client.delete(anyString(), anyString(), anyString())) 
                    .thenReturn(Promise.of((Void) null)); 

            Optional<TieredEntity> result = mgr.findById(TENANT, COLL, id).getResult(); 

            assertThat(result).isPresent(); 
            assertThat(result.get().tier()).isEqualTo(StorageTier.COOL); 
            verify(client).save(eq(TENANT), eq(warmCollection(COLL)), anyMap()); 
        }

        @Test
        @DisplayName("findById: returns empty when entity absent from all tiers")
        void findById_totalMiss_returnsEmpty() { 
            String id = "ghost-entity";
            when(client.findById(anyString(), anyString(), eq(id))) 
                    .thenReturn(Promise.of(Optional.empty())); 

            Optional<TieredEntity> result = mgr.findById(TENANT, COLL, id).getResult(); 

            assertThat(result).isEmpty(); 
        }
    }

    // =========================================================================
    // queryHot / queryHotAndWarm
    // =========================================================================

    @Nested
    @DisplayName("queryHot() / queryHotAndWarm()")
    class QueryTests {

        @Test
        @DisplayName("queryHot: queries only HOT collection")
        void queryHot_queriesHotOnly() { 
            Query q = Query.builder().limit(10).build(); 
            when(client.query(TENANT, hotCollection(COLL), q)) 
                    .thenReturn(Promise.of(List.of( 
                            entity("e1", entData("e1", StorageTier.HOT)), 
                            entity("e2", entData("e2", StorageTier.HOT)) 
                    )));

            List<TieredEntity> results = mgr.queryHot(TENANT, COLL, q).getResult(); 

            assertThat(results).hasSize(2); 
            assertThat(results).extracting(TieredEntity::tier).containsOnly(StorageTier.HOT); 
            verify(client, never()).query(eq(TENANT), eq(warmCollection(COLL)), any()); 
        }

        @Test
        @DisplayName("queryHotAndWarm: merges results; HOT entity takes precedence over WARM duplicate")
        void queryHotAndWarm_hotPrecedenceOnDuplicate() { 
            Query q = Query.builder().limit(10).build(); 
            String sharedId = "shared-ent";

            when(client.query(TENANT, hotCollection(COLL), q)) 
                    .thenReturn(Promise.of(List.of(entity(sharedId, entData(sharedId, StorageTier.HOT))))); 
            when(client.query(TENANT, warmCollection(COLL), q)) 
                    .thenReturn(Promise.of(List.of(entity(sharedId, entData(sharedId, StorageTier.WARM))))); 

            List<TieredEntity> results = mgr.queryHotAndWarm(TENANT, COLL, q).getResult(); 

            assertThat(results).hasSize(1); 
            assertThat(results.get(0).tier()).isEqualTo(StorageTier.HOT); 
        }
    }

    // =========================================================================
    // demoteIdleEntities (HOT → WARM) 
    // =========================================================================

    @Nested
    @DisplayName("demoteIdleEntities()")
    class DemoteTests {

        @Test
        @DisplayName("demoteIdleEntities: moves idle HOT entities to WARM and deletes from HOT")
        void demoteIdleEntities_movesHotToWarm() { 
            Instant threshold = Instant.now().minusSeconds(3600); 
            List<Entity> idle = List.of( 
                    entity("e1", entData("e1", StorageTier.HOT)), 
                    entity("e2", entData("e2", StorageTier.HOT)) 
            );
            when(client.query(eq(TENANT), eq(hotCollection(COLL)), any(Query.class))) 
                    .thenReturn(Promise.of(idle)); 
            when(client.save(eq(TENANT), eq(warmCollection(COLL)), anyMap())) 
                    .thenReturn(Promise.of(entity("ex", Map.of()))); 
            when(client.delete(eq(TENANT), eq(hotCollection(COLL)), anyString())) 
                    .thenReturn(Promise.of((Void) null)); 

            int count = mgr.demoteIdleEntities(TENANT, COLL, threshold).getResult(); 

            assertThat(count).isEqualTo(2); 
            verify(client, times(2)).save(eq(TENANT), eq(warmCollection(COLL)), anyMap()); 
            verify(client, times(2)).delete(eq(TENANT), eq(hotCollection(COLL)), anyString()); 
        }

        @Test
        @DisplayName("demoteIdleEntities: returns 0 when no entities are idle")
        void demoteIdleEntities_nothingToPromote() { 
            when(client.query(eq(TENANT), eq(hotCollection(COLL)), any(Query.class))) 
                    .thenReturn(Promise.of(List.of())); 

            int count = mgr.demoteIdleEntities(TENANT, COLL, Instant.now()).getResult(); 

            assertThat(count).isZero(); 
            verify(client, never()).save(anyString(), anyString(), anyMap()); 
        }
    }

    // =========================================================================
    // deleteFromAllTiers
    // =========================================================================

    @Nested
    @DisplayName("deleteFromAllTiers()")
    class DeleteTests {

        @Test
        @DisplayName("deleteFromAllTiers: checks and deletes from all 4 tier collections")
        void deleteFromAllTiers_checksAllTiers() { 
            String id = "to-erase";
            // Entity exists in HOT only
            when(client.findById(TENANT, hotCollection(COLL), id)) 
                    .thenReturn(Promise.of(Optional.of(entity(id, entData(id, StorageTier.HOT))))); 
            when(client.findById(TENANT, warmCollection(COLL), id)) 
                    .thenReturn(Promise.of(Optional.empty())); 
            when(client.findById(TENANT, coolCollection(COLL), id)) 
                    .thenReturn(Promise.of(Optional.empty())); 
            when(client.findById(TENANT, coldCollection(COLL), id)) 
                    .thenReturn(Promise.of(Optional.empty())); 
            when(client.delete(eq(TENANT), eq(hotCollection(COLL)), eq(id))) 
                    .thenReturn(Promise.of((Void) null)); 

            mgr.deleteFromAllTiers(TENANT, COLL, id).getResult(); 

            // Verify all tiers were checked
            verify(client).findById(TENANT, hotCollection(COLL), id); 
            verify(client).findById(TENANT, warmCollection(COLL), id); 
            verify(client).findById(TENANT, coolCollection(COLL), id); 
            verify(client).findById(TENANT, coldCollection(COLL), id); 
            // Delete called only for HOT (where entity existed) 
            verify(client, times(1)).delete(eq(TENANT), anyString(), eq(id)); 
        }
    }

    // =========================================================================
    // Collection name helpers
    // =========================================================================

    @Test
    @DisplayName("Collection name helpers return correct suffixes")
    void collectionNameHelpers_returnExpectedSuffixes() { 
        String base = "entities";
        assertThat(hotCollection(base)).isEqualTo("entities");
        assertThat(warmCollection(base)).isEqualTo("entities_warm");
        assertThat(coolCollection(base)).isEqualTo("entities_cool");
        assertThat(coldCollection(base)).isEqualTo("entities_cold");
    }

    // =========================================================================
    // TieredEntity helpers
    // =========================================================================

    @Test
    @DisplayName("TieredEntity.needsPromotionConsideration: true for non-HOT tiers")
    void tieredEntity_needsPromotionConsideration_trueForNonHot() { 
        Entity e = entity("x", entData("x", StorageTier.WARM)); 
        TieredEntity hot  = new TieredEntity(e, StorageTier.HOT); 
        TieredEntity warm = new TieredEntity(e, StorageTier.WARM); 
        TieredEntity cold = new TieredEntity(e, StorageTier.COLD); 

        assertThat(hot.needsPromotionConsideration()).isFalse(); 
        assertThat(warm.needsPromotionConsideration()).isTrue(); 
        assertThat(cold.needsPromotionConsideration()).isTrue(); 
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static Entity entity(String id, Map<String, Object> data) { 
        return new Entity(id, COLL, data, Instant.now(), Instant.now(), 1L); 
    }

        @SuppressWarnings({"unchecked", "rawtypes"}) 
        private static org.mockito.ArgumentCaptor<Map<String, Object>> mapCaptor() { 
                return (org.mockito.ArgumentCaptor) org.mockito.ArgumentCaptor.forClass(Map.class); 
        }

    private static Map<String, Object> entData(String id, StorageTier tier) { 
        Map<String, Object> d = new HashMap<>(); 
        d.put("id", id); 
        d.put(TIER_FIELD, tier.name()); 
        d.put(LAST_ACCESSED_FIELD, Instant.now().minusSeconds(7200).toString()); 
        return d;
    }
}
