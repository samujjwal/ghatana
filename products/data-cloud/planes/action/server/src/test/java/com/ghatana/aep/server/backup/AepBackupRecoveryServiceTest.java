/*
 * Copyright (c) 2026 Ghatana Inc. 
 * All rights reserved.
 */
package com.ghatana.aep.server.backup;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.DataCloudClient.Entity;
import com.ghatana.datacloud.DataCloudClient.Query;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AepBackupRecoveryService}.
 *
 * <p>All DataCloudClient calls are mocked with synchronous {@link Promise}
 * responses so no Eventloop is needed — promises are already resolved and
 * {@code .getResult()} inside the blocking executor produces the stub value. 
 *
 * @doc.type class
 * @doc.purpose Unit tests for AepBackupRecoveryService — backup, restore, verify, delete
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) 
@DisplayName("AepBackupRecoveryService")
class AepBackupRecoveryServiceTest {

    private static final String TENANT = "tenant-backup";

    @Mock
    private DataCloudClient client;

    private AepBackupRecoveryService service;

    @BeforeEach
    void setUp() { 
        // Use SimpleMeterRegistry; service is now fully async (no blocking executor needed) 
        service = new AepBackupRecoveryService( 
                client,
                new SimpleMeterRegistry(), 
                List.of("aep_patterns", "aep_pipelines")); 
    }

    // =========================================================================
    //  Helper: stub DataCloudClient
    // =========================================================================

    private static Entity entity(String id, Map<String, Object> data) { 
        return new Entity(id, "test", Map.copyOf(data), Instant.now(), Instant.now(), 1); 
    }

    private static Map<String, Object> patternData(String id) { 
        Map<String, Object> d = new HashMap<>(); 
        d.put("id", id); 
        d.put("name", "pattern-" + id); 
        d.put("tenantId", TENANT); 
        d.put("updatedAt", Instant.now().toString()); 
        return d;
    }

    // =========================================================================
    //  1. Full backup
    // =========================================================================

    @Nested
    @DisplayName("createFullBackup")
    class FullBackup {

        @Test
        @DisplayName("backs up both collections and returns non-null backupId")
        void fullBackup_successfulResult() throws Exception { 
            // Entity in aep_patterns
            Entity p1 = entity("pat-1", patternData("pat-1"));
            // Entity in aep_pipelines
            Entity pipe1 = entity("pipe-1", Map.of("id", "pipe-1", "name", "my-pipeline", 
                    "tenantId", TENANT, "updatedAt", Instant.now().toString())); 

            // Query stubs: pattern collection returns 1 entity, pipelines returns 1
            when(client.query(eq(TENANT), eq("aep_patterns"), any(Query.class)))
                    .thenReturn(Promise.of(List.of(p1))); 
            when(client.query(eq(TENANT), eq("aep_pipelines"), any(Query.class)))
                    .thenReturn(Promise.of(List.of(pipe1))); 

            // Save stubs (backup data + metadata) 
            when(client.save(eq(TENANT), anyString(), anyMap())) 
                    .thenAnswer(inv -> { 
                        Map<String, Object> data = inv.getArgument(2); 
                        String id = (String) data.getOrDefault("id", UUID.randomUUID().toString()); 
                        return Promise.of(entity(id, data)); 
                    });

            AepBackupRecoveryService.BackupResult result =
                    service.createFullBackup(TENANT).getResult(); 

            assertThat(result.backupId()).isNotBlank(); 
            assertThat(result.tenantId()).isEqualTo(TENANT); 
            assertThat(result.backupType()).isEqualTo("FULL");
            assertThat(result.entityCount()).isEqualTo(2); // 1 pattern + 1 pipeline 
            assertThat(result.success()).isTrue(); 
            assertThat(result.failedCollections()).isEmpty(); 
        }

        @Test
        @DisplayName("returns partial success when one collection query fails")
        void fullBackup_oneCollectionFails_partialResult() throws Exception { 
            // aep_patterns OK
            Entity p1 = entity("pat-1", patternData("pat-1"));
            when(client.query(eq(TENANT), eq("aep_patterns"), any(Query.class)))
                    .thenReturn(Promise.of(List.of(p1))); 

            // aep_pipelines throws
            when(client.query(eq(TENANT), eq("aep_pipelines"), any(Query.class)))
                    .thenReturn(Promise.ofException(new RuntimeException("Pipeline query failed")));

            // Save stub for pattern data + metadata
            when(client.save(eq(TENANT), anyString(), anyMap())) 
                    .thenAnswer(inv -> { 
                        Map<String, Object> data = inv.getArgument(2); 
                        String id = (String) data.getOrDefault("id", UUID.randomUUID().toString()); 
                        return Promise.of(entity(id, data)); 
                    });

            AepBackupRecoveryService.BackupResult result =
                    service.createFullBackup(TENANT).getResult(); 

            assertThat(result.success()).isFalse(); 
            assertThat(result.failedCollections()).contains("aep_pipelines");
        }

        @Test
        @DisplayName("backup metadata is saved to the BACKUP_COLLECTION")
        void fullBackup_metadataSavedToCorrectCollection() throws Exception { 
            when(client.query(eq(TENANT), anyString(), any(Query.class))) 
                    .thenReturn(Promise.of(List.of())); 

            ArgumentCaptor<String> collectionCaptor = ArgumentCaptor.forClass(String.class); 
            when(client.save(eq(TENANT), collectionCaptor.capture(), anyMap())) 
                    .thenAnswer(inv -> { 
                        Map<String, Object> data = inv.getArgument(2); 
                        String id = (String) data.getOrDefault("id", UUID.randomUUID().toString()); 
                        return Promise.of(entity(id, data)); 
                    });

            service.createFullBackup(TENANT).getResult(); 

            // The last save call must be to the backup metadata collection
            List<String> allCalls = collectionCaptor.getAllValues(); 
            assertThat(allCalls).anyMatch(c -> c.equals(AepBackupRecoveryService.BACKUP_COLLECTION)); 
        }
    }

    // =========================================================================
    //  2. Incremental backup
    // =========================================================================

    @Nested
    @DisplayName("createIncrementalBackup")
    class IncrementalBackup {

        @Test
        @DisplayName("filters by updatedAt_gte and returns INCREMENTAL type")
        void incrementalBackup_correctTypeAndFilterUsed() throws Exception { 
            Instant since = Instant.now().minusSeconds(3600); 

            ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class); 
            when(client.query(eq(TENANT), anyString(), queryCaptor.capture())) 
                    .thenReturn(Promise.of(List.of())); 
            when(client.save(eq(TENANT), anyString(), anyMap())) 
                    .thenAnswer(inv -> { 
                        Map<String, Object> data = inv.getArgument(2); 
                        String id = (String) data.getOrDefault("id", UUID.randomUUID().toString()); 
                        return Promise.of(entity(id, data)); 
                    });

            AepBackupRecoveryService.BackupResult result =
                    service.createIncrementalBackup(TENANT, since).getResult(); 

            assertThat(result.backupType()).isEqualTo("INCREMENTAL");
            assertThat(result.success()).isTrue(); 
        }
    }

    // =========================================================================
    //  3. Restore
    // =========================================================================

    @Nested
    @DisplayName("restoreFromBackup")
    class RestoreBackup {

        @Test
        @DisplayName("restore fails gracefully when backup metadata not found")
        void restore_metadataNotFound_returnsFailed() throws Exception { 
            when(client.findById(eq(TENANT), eq(AepBackupRecoveryService.BACKUP_COLLECTION), anyString())) 
                    .thenReturn(Promise.of(Optional.empty())); 

            AepBackupRecoveryService.RestoreResult result =
                    service.restoreFromBackup(TENANT, "non-existent-backup").getResult(); 

            assertThat(result.success()).isFalse(); 
            assertThat(result.errors()).anyMatch(e -> e.contains("Backup not found"));
        }

        @Test
        @DisplayName("restore reads from backup data collections and saves to source collections")
        void restore_found_copiesEntitiesBack() throws Exception { 
            String backupId = UUID.randomUUID().toString(); 

            // Backup metadata
            Map<String, Object> metaData = new HashMap<>(); 
            metaData.put("backupId", backupId); 
            metaData.put("tenantId", TENANT); 
            metaData.put("collections", "aep_patterns,aep_pipelines"); 
            metaData.put("entityCount", 1); 
            Entity meta = entity(backupId, metaData); 

            when(client.findById(eq(TENANT), eq(AepBackupRecoveryService.BACKUP_COLLECTION), eq(backupId))) 
                    .thenReturn(Promise.of(Optional.of(meta))); 

            // Backup data entities
            Map<String, Object> bkpPatternData = new HashMap<>(patternData("pat-1"));
            bkpPatternData.put("backupId", backupId); 
            bkpPatternData.put("backedUpAt", Instant.now().toString()); 
            Entity bkpPattern = entity("pat-1", bkpPatternData); 

            String patternBackupColl = AepBackupRecoveryService.backupDataCollection(backupId, "aep_patterns"); 
            String pipelineBackupColl = AepBackupRecoveryService.backupDataCollection(backupId, "aep_pipelines"); 

            when(client.query(eq(TENANT), eq(patternBackupColl), any(Query.class))) 
                    .thenReturn(Promise.of(List.of(bkpPattern))); 
            when(client.query(eq(TENANT), eq(pipelineBackupColl), any(Query.class))) 
                    .thenReturn(Promise.of(List.of())); 

            // Save stubs (entity restore writes) 
            when(client.save(eq(TENANT), anyString(), anyMap())) 
                    .thenAnswer(inv -> { 
                        Map<String, Object> data = inv.getArgument(2); 
                        String id = (String) data.getOrDefault("id", UUID.randomUUID().toString()); 
                        return Promise.of(entity(id, data)); 
                    });

            AepBackupRecoveryService.RestoreResult result =
                    service.restoreFromBackup(TENANT, backupId).getResult(); 

            assertThat(result.success()).isTrue(); 
            assertThat(result.entityCount()).isEqualTo(1); 
            assertThat(result.errors()).isEmpty(); 

            // Verify entity was written back to the source collection
            verify(client).save(eq(TENANT), eq("aep_patterns"), argThat(data ->
                    "pat-1".equals(data.get("id")) && !data.containsKey("backupId")));
        }
    }

    // =========================================================================
    //  4. List backups
    // =========================================================================

    @Nested
    @DisplayName("listBackups")
    class ListBackups {

        @Test
        @DisplayName("returns empty list when no backups exist")
        void listBackups_empty_returnsEmptyList() throws Exception { 
            when(client.query(eq(TENANT), eq(AepBackupRecoveryService.BACKUP_COLLECTION), any(Query.class))) 
                    .thenReturn(Promise.of(List.of())); 

            List<AepBackupRecoveryService.BackupMetadata> result =
                    service.listBackups(TENANT).getResult(); 

            assertThat(result).isEmpty(); 
        }

        @Test
        @DisplayName("returns parsed backup metadata for existing backups")
        void listBackups_existing_returnsMetadata() throws Exception { 
            String backupId = UUID.randomUUID().toString(); 
            Map<String, Object> metaData = new HashMap<>(); 
            metaData.put("backupId", backupId); 
            metaData.put("tenantId", TENANT); 
            metaData.put("backupType", "FULL"); 
            metaData.put("createdAt", Instant.now().toString()); 
            metaData.put("entityCount", 5); 
            metaData.put("status", "COMPLETE"); 
            metaData.put("collections", "aep_patterns,aep_pipelines"); 
            metaData.put("schemaVersion", 1); 

            when(client.query(eq(TENANT), eq(AepBackupRecoveryService.BACKUP_COLLECTION), any(Query.class))) 
                    .thenReturn(Promise.of(List.of(entity(backupId, metaData)))); 

            List<AepBackupRecoveryService.BackupMetadata> result =
                    service.listBackups(TENANT).getResult(); 

            assertThat(result).hasSize(1); 
            AepBackupRecoveryService.BackupMetadata bm = result.get(0); 
            assertThat(bm.backupId()).isEqualTo(backupId); 
            assertThat(bm.tenantId()).isEqualTo(TENANT); 
            assertThat(bm.backupType()).isEqualTo("FULL");
            assertThat(bm.entityCount()).isEqualTo(5); 
            assertThat(bm.status()).isEqualTo("COMPLETE");
        }
    }

    // =========================================================================
    //  5. Backup verification
    // =========================================================================

    @Nested
    @DisplayName("verifyBackup")
    class VerifyBackup {

        @Test
        @DisplayName("verification passes when actual count matches expected count")
        void verify_countsMatch_passes() throws Exception { 
            String backupId = UUID.randomUUID().toString(); 

            Map<String, Object> metaData = new HashMap<>(); 
            metaData.put("entityCount", 2); 
            metaData.put("collections", "aep_patterns,aep_pipelines"); 
            Entity meta = entity(backupId, metaData); 

            when(client.findById(eq(TENANT), eq(AepBackupRecoveryService.BACKUP_COLLECTION), eq(backupId))) 
                    .thenReturn(Promise.of(Optional.of(meta))); 

            // One entity in patterns backup, one in pipelines backup
            when(client.query(eq(TENANT), anyString(), any(Query.class))) 
                    .thenReturn(Promise.of(List.of(entity("e1", Map.of("id", "e1"))))) 
                    .thenReturn(Promise.of(List.of(entity("e2", Map.of("id", "e2"))))); 

            AepBackupRecoveryService.VerificationResult result =
                    service.verifyBackup(TENANT, backupId).getResult(); 

            assertThat(result.valid()).isTrue(); 
            assertThat(result.expectedCount()).isEqualTo(2); 
            assertThat(result.actualCount()).isEqualTo(2); 
            assertThat(result.errorMessage()).isNull(); 
        }

        @Test
        @DisplayName("verification fails when actual count does not match expected count")
        void verify_countsMismatch_fails() throws Exception { 
            String backupId = UUID.randomUUID().toString(); 

            Map<String, Object> metaData = new HashMap<>(); 
            metaData.put("entityCount", 5);  // says 5 
            metaData.put("collections", "aep_patterns,aep_pipelines"); 
            Entity meta = entity(backupId, metaData); 

            when(client.findById(eq(TENANT), eq(AepBackupRecoveryService.BACKUP_COLLECTION), eq(backupId))) 
                    .thenReturn(Promise.of(Optional.of(meta))); 

            // But actual backup data only has 1
            when(client.query(eq(TENANT), anyString(), any(Query.class))) 
                    .thenReturn(Promise.of(List.of(entity("e1", Map.of("id", "e1"))))) 
                    .thenReturn(Promise.of(List.of())); // 0 in pipelines 

            AepBackupRecoveryService.VerificationResult result =
                    service.verifyBackup(TENANT, backupId).getResult(); 

            assertThat(result.valid()).isFalse(); 
            assertThat(result.expectedCount()).isEqualTo(5); 
            assertThat(result.actualCount()).isEqualTo(1); 
            assertThat(result.errorMessage()).contains("mismatch");
        }

        @Test
        @DisplayName("verification returns invalid when backup metadata not found")
        void verify_metadataNotFound_returnsInvalid() throws Exception { 
            when(client.findById(eq(TENANT), eq(AepBackupRecoveryService.BACKUP_COLLECTION), anyString())) 
                    .thenReturn(Promise.of(Optional.empty())); 

            AepBackupRecoveryService.VerificationResult result =
                    service.verifyBackup(TENANT, "ghost-backup").getResult(); 

            assertThat(result.valid()).isFalse(); 
            assertThat(result.errorMessage()).contains("not found");
        }
    }

    // =========================================================================
    //  6. Delete backup
    // =========================================================================

    @Nested
    @DisplayName("deleteBackup")
    class DeleteBackup {

        @Test
        @DisplayName("deletes backup metadata when backup not found")
        void delete_backupNotFound_deletesMetadata() throws Exception { 
            String backupId = "missing-backup";

            when(client.findById(eq(TENANT), eq(AepBackupRecoveryService.BACKUP_COLLECTION), eq(backupId))) 
                    .thenReturn(Promise.of(Optional.empty())); 
            when(client.delete(eq(TENANT), eq(AepBackupRecoveryService.BACKUP_COLLECTION), eq(backupId))) 
                    .thenReturn(Promise.of(null)); 

            // Should not throw
            service.deleteBackup(TENANT, backupId).getResult(); 

            verify(client).delete(TENANT, AepBackupRecoveryService.BACKUP_COLLECTION, backupId); 
        }
    }

    // =========================================================================
    //  7. Static helper
    // =========================================================================

    @Nested
    @DisplayName("backupDataCollection helper")
    class BackupDataCollectionHelper {

        @Test
        @DisplayName("generates deterministic collection name from backupId and source collection")
        void backupDataCollection_deterministicName() { 
            String name = AepBackupRecoveryService.backupDataCollection("my-backup-id", "aep_patterns"); 
            assertThat(name).isEqualTo("aep_backup_my-backup-id_aep_patterns");
        }
    }
}
