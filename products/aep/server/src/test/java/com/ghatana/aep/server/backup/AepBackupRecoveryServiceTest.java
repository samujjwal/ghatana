/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
 * {@code .getResult()} inside the blocking executor produces the stub value. // GH-90000
 *
 * @doc.type class
 * @doc.purpose Unit tests for AepBackupRecoveryService — backup, restore, verify, delete
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("AepBackupRecoveryService [GH-90000]")
class AepBackupRecoveryServiceTest {

    private static final String TENANT = "tenant-backup";

    @Mock
    private DataCloudClient client;

    private AepBackupRecoveryService service;

    @BeforeEach
    void setUp() { // GH-90000
        // Use SimpleMeterRegistry; service is now fully async (no blocking executor needed) // GH-90000
        service = new AepBackupRecoveryService( // GH-90000
                client,
                new SimpleMeterRegistry(), // GH-90000
                List.of("aep_patterns", "aep_pipelines")); // GH-90000
    }

    // =========================================================================
    //  Helper: stub DataCloudClient
    // =========================================================================

    private static Entity entity(String id, Map<String, Object> data) { // GH-90000
        return new Entity(id, "test", Map.copyOf(data), Instant.now(), Instant.now(), 1); // GH-90000
    }

    private static Map<String, Object> patternData(String id) { // GH-90000
        Map<String, Object> d = new HashMap<>(); // GH-90000
        d.put("id", id); // GH-90000
        d.put("name", "pattern-" + id); // GH-90000
        d.put("tenantId", TENANT); // GH-90000
        d.put("updatedAt", Instant.now().toString()); // GH-90000
        return d;
    }

    // =========================================================================
    //  1. Full backup
    // =========================================================================

    @Nested
    @DisplayName("createFullBackup [GH-90000]")
    class FullBackup {

        @Test
        @DisplayName("backs up both collections and returns non-null backupId [GH-90000]")
        void fullBackup_successfulResult() throws Exception { // GH-90000
            // Entity in aep_patterns
            Entity p1 = entity("pat-1", patternData("pat-1 [GH-90000]"));
            // Entity in aep_pipelines
            Entity pipe1 = entity("pipe-1", Map.of("id", "pipe-1", "name", "my-pipeline", // GH-90000
                    "tenantId", TENANT, "updatedAt", Instant.now().toString())); // GH-90000

            // Query stubs: pattern collection returns 1 entity, pipelines returns 1
            when(client.query(eq(TENANT), eq("aep_patterns [GH-90000]"), any(Query.class)))
                    .thenReturn(Promise.of(List.of(p1))); // GH-90000
            when(client.query(eq(TENANT), eq("aep_pipelines [GH-90000]"), any(Query.class)))
                    .thenReturn(Promise.of(List.of(pipe1))); // GH-90000

            // Save stubs (backup data + metadata) // GH-90000
            when(client.save(eq(TENANT), anyString(), anyMap())) // GH-90000
                    .thenAnswer(inv -> { // GH-90000
                        Map<String, Object> data = inv.getArgument(2); // GH-90000
                        String id = (String) data.getOrDefault("id", UUID.randomUUID().toString()); // GH-90000
                        return Promise.of(entity(id, data)); // GH-90000
                    });

            AepBackupRecoveryService.BackupResult result =
                    service.createFullBackup(TENANT).getResult(); // GH-90000

            assertThat(result.backupId()).isNotBlank(); // GH-90000
            assertThat(result.tenantId()).isEqualTo(TENANT); // GH-90000
            assertThat(result.backupType()).isEqualTo("FULL [GH-90000]");
            assertThat(result.entityCount()).isEqualTo(2); // 1 pattern + 1 pipeline // GH-90000
            assertThat(result.success()).isTrue(); // GH-90000
            assertThat(result.failedCollections()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("returns partial success when one collection query fails [GH-90000]")
        void fullBackup_oneCollectionFails_partialResult() throws Exception { // GH-90000
            // aep_patterns OK
            Entity p1 = entity("pat-1", patternData("pat-1 [GH-90000]"));
            when(client.query(eq(TENANT), eq("aep_patterns [GH-90000]"), any(Query.class)))
                    .thenReturn(Promise.of(List.of(p1))); // GH-90000

            // aep_pipelines throws
            when(client.query(eq(TENANT), eq("aep_pipelines [GH-90000]"), any(Query.class)))
                    .thenReturn(Promise.ofException(new RuntimeException("Pipeline query failed [GH-90000]")));

            // Save stub for pattern data + metadata
            when(client.save(eq(TENANT), anyString(), anyMap())) // GH-90000
                    .thenAnswer(inv -> { // GH-90000
                        Map<String, Object> data = inv.getArgument(2); // GH-90000
                        String id = (String) data.getOrDefault("id", UUID.randomUUID().toString()); // GH-90000
                        return Promise.of(entity(id, data)); // GH-90000
                    });

            AepBackupRecoveryService.BackupResult result =
                    service.createFullBackup(TENANT).getResult(); // GH-90000

            assertThat(result.success()).isFalse(); // GH-90000
            assertThat(result.failedCollections()).contains("aep_pipelines [GH-90000]");
        }

        @Test
        @DisplayName("backup metadata is saved to the BACKUP_COLLECTION [GH-90000]")
        void fullBackup_metadataSavedToCorrectCollection() throws Exception { // GH-90000
            when(client.query(eq(TENANT), anyString(), any(Query.class))) // GH-90000
                    .thenReturn(Promise.of(List.of())); // GH-90000

            ArgumentCaptor<String> collectionCaptor = ArgumentCaptor.forClass(String.class); // GH-90000
            when(client.save(eq(TENANT), collectionCaptor.capture(), anyMap())) // GH-90000
                    .thenAnswer(inv -> { // GH-90000
                        Map<String, Object> data = inv.getArgument(2); // GH-90000
                        String id = (String) data.getOrDefault("id", UUID.randomUUID().toString()); // GH-90000
                        return Promise.of(entity(id, data)); // GH-90000
                    });

            service.createFullBackup(TENANT).getResult(); // GH-90000

            // The last save call must be to the backup metadata collection
            List<String> allCalls = collectionCaptor.getAllValues(); // GH-90000
            assertThat(allCalls).anyMatch(c -> c.equals(AepBackupRecoveryService.BACKUP_COLLECTION)); // GH-90000
        }
    }

    // =========================================================================
    //  2. Incremental backup
    // =========================================================================

    @Nested
    @DisplayName("createIncrementalBackup [GH-90000]")
    class IncrementalBackup {

        @Test
        @DisplayName("filters by updatedAt_gte and returns INCREMENTAL type [GH-90000]")
        void incrementalBackup_correctTypeAndFilterUsed() throws Exception { // GH-90000
            Instant since = Instant.now().minusSeconds(3600); // GH-90000

            ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class); // GH-90000
            when(client.query(eq(TENANT), anyString(), queryCaptor.capture())) // GH-90000
                    .thenReturn(Promise.of(List.of())); // GH-90000
            when(client.save(eq(TENANT), anyString(), anyMap())) // GH-90000
                    .thenAnswer(inv -> { // GH-90000
                        Map<String, Object> data = inv.getArgument(2); // GH-90000
                        String id = (String) data.getOrDefault("id", UUID.randomUUID().toString()); // GH-90000
                        return Promise.of(entity(id, data)); // GH-90000
                    });

            AepBackupRecoveryService.BackupResult result =
                    service.createIncrementalBackup(TENANT, since).getResult(); // GH-90000

            assertThat(result.backupType()).isEqualTo("INCREMENTAL [GH-90000]");
            assertThat(result.success()).isTrue(); // GH-90000
        }
    }

    // =========================================================================
    //  3. Restore
    // =========================================================================

    @Nested
    @DisplayName("restoreFromBackup [GH-90000]")
    class RestoreBackup {

        @Test
        @DisplayName("restore fails gracefully when backup metadata not found [GH-90000]")
        void restore_metadataNotFound_returnsFailed() throws Exception { // GH-90000
            when(client.findById(eq(TENANT), eq(AepBackupRecoveryService.BACKUP_COLLECTION), anyString())) // GH-90000
                    .thenReturn(Promise.of(Optional.empty())); // GH-90000

            AepBackupRecoveryService.RestoreResult result =
                    service.restoreFromBackup(TENANT, "non-existent-backup").getResult(); // GH-90000

            assertThat(result.success()).isFalse(); // GH-90000
            assertThat(result.errors()).anyMatch(e -> e.contains("Backup not found [GH-90000]"));
        }

        @Test
        @DisplayName("restore reads from backup data collections and saves to source collections [GH-90000]")
        void restore_found_copiesEntitiesBack() throws Exception { // GH-90000
            String backupId = UUID.randomUUID().toString(); // GH-90000

            // Backup metadata
            Map<String, Object> metaData = new HashMap<>(); // GH-90000
            metaData.put("backupId", backupId); // GH-90000
            metaData.put("tenantId", TENANT); // GH-90000
            metaData.put("collections", "aep_patterns,aep_pipelines"); // GH-90000
            metaData.put("entityCount", 1); // GH-90000
            Entity meta = entity(backupId, metaData); // GH-90000

            when(client.findById(eq(TENANT), eq(AepBackupRecoveryService.BACKUP_COLLECTION), eq(backupId))) // GH-90000
                    .thenReturn(Promise.of(Optional.of(meta))); // GH-90000

            // Backup data entities
            Map<String, Object> bkpPatternData = new HashMap<>(patternData("pat-1 [GH-90000]"));
            bkpPatternData.put("backupId", backupId); // GH-90000
            bkpPatternData.put("backedUpAt", Instant.now().toString()); // GH-90000
            Entity bkpPattern = entity("pat-1", bkpPatternData); // GH-90000

            String patternBackupColl = AepBackupRecoveryService.backupDataCollection(backupId, "aep_patterns"); // GH-90000
            String pipelineBackupColl = AepBackupRecoveryService.backupDataCollection(backupId, "aep_pipelines"); // GH-90000

            when(client.query(eq(TENANT), eq(patternBackupColl), any(Query.class))) // GH-90000
                    .thenReturn(Promise.of(List.of(bkpPattern))); // GH-90000
            when(client.query(eq(TENANT), eq(pipelineBackupColl), any(Query.class))) // GH-90000
                    .thenReturn(Promise.of(List.of())); // GH-90000

            // Save stubs (entity restore writes) // GH-90000
            when(client.save(eq(TENANT), anyString(), anyMap())) // GH-90000
                    .thenAnswer(inv -> { // GH-90000
                        Map<String, Object> data = inv.getArgument(2); // GH-90000
                        String id = (String) data.getOrDefault("id", UUID.randomUUID().toString()); // GH-90000
                        return Promise.of(entity(id, data)); // GH-90000
                    });

            AepBackupRecoveryService.RestoreResult result =
                    service.restoreFromBackup(TENANT, backupId).getResult(); // GH-90000

            assertThat(result.success()).isTrue(); // GH-90000
            assertThat(result.entityCount()).isEqualTo(1); // GH-90000
            assertThat(result.errors()).isEmpty(); // GH-90000

            // Verify entity was written back to the source collection
            verify(client).save(eq(TENANT), eq("aep_patterns [GH-90000]"), argThat(data ->
                    "pat-1".equals(data.get("id [GH-90000]")) && !data.containsKey("backupId [GH-90000]")));
        }
    }

    // =========================================================================
    //  4. List backups
    // =========================================================================

    @Nested
    @DisplayName("listBackups [GH-90000]")
    class ListBackups {

        @Test
        @DisplayName("returns empty list when no backups exist [GH-90000]")
        void listBackups_empty_returnsEmptyList() throws Exception { // GH-90000
            when(client.query(eq(TENANT), eq(AepBackupRecoveryService.BACKUP_COLLECTION), any(Query.class))) // GH-90000
                    .thenReturn(Promise.of(List.of())); // GH-90000

            List<AepBackupRecoveryService.BackupMetadata> result =
                    service.listBackups(TENANT).getResult(); // GH-90000

            assertThat(result).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("returns parsed backup metadata for existing backups [GH-90000]")
        void listBackups_existing_returnsMetadata() throws Exception { // GH-90000
            String backupId = UUID.randomUUID().toString(); // GH-90000
            Map<String, Object> metaData = new HashMap<>(); // GH-90000
            metaData.put("backupId", backupId); // GH-90000
            metaData.put("tenantId", TENANT); // GH-90000
            metaData.put("backupType", "FULL"); // GH-90000
            metaData.put("createdAt", Instant.now().toString()); // GH-90000
            metaData.put("entityCount", 5); // GH-90000
            metaData.put("status", "COMPLETE"); // GH-90000
            metaData.put("collections", "aep_patterns,aep_pipelines"); // GH-90000
            metaData.put("schemaVersion", 1); // GH-90000

            when(client.query(eq(TENANT), eq(AepBackupRecoveryService.BACKUP_COLLECTION), any(Query.class))) // GH-90000
                    .thenReturn(Promise.of(List.of(entity(backupId, metaData)))); // GH-90000

            List<AepBackupRecoveryService.BackupMetadata> result =
                    service.listBackups(TENANT).getResult(); // GH-90000

            assertThat(result).hasSize(1); // GH-90000
            AepBackupRecoveryService.BackupMetadata bm = result.get(0); // GH-90000
            assertThat(bm.backupId()).isEqualTo(backupId); // GH-90000
            assertThat(bm.tenantId()).isEqualTo(TENANT); // GH-90000
            assertThat(bm.backupType()).isEqualTo("FULL [GH-90000]");
            assertThat(bm.entityCount()).isEqualTo(5); // GH-90000
            assertThat(bm.status()).isEqualTo("COMPLETE [GH-90000]");
        }
    }

    // =========================================================================
    //  5. Backup verification
    // =========================================================================

    @Nested
    @DisplayName("verifyBackup [GH-90000]")
    class VerifyBackup {

        @Test
        @DisplayName("verification passes when actual count matches expected count [GH-90000]")
        void verify_countsMatch_passes() throws Exception { // GH-90000
            String backupId = UUID.randomUUID().toString(); // GH-90000

            Map<String, Object> metaData = new HashMap<>(); // GH-90000
            metaData.put("entityCount", 2); // GH-90000
            metaData.put("collections", "aep_patterns,aep_pipelines"); // GH-90000
            Entity meta = entity(backupId, metaData); // GH-90000

            when(client.findById(eq(TENANT), eq(AepBackupRecoveryService.BACKUP_COLLECTION), eq(backupId))) // GH-90000
                    .thenReturn(Promise.of(Optional.of(meta))); // GH-90000

            // One entity in patterns backup, one in pipelines backup
            when(client.query(eq(TENANT), anyString(), any(Query.class))) // GH-90000
                    .thenReturn(Promise.of(List.of(entity("e1", Map.of("id", "e1"))))) // GH-90000
                    .thenReturn(Promise.of(List.of(entity("e2", Map.of("id", "e2"))))); // GH-90000

            AepBackupRecoveryService.VerificationResult result =
                    service.verifyBackup(TENANT, backupId).getResult(); // GH-90000

            assertThat(result.valid()).isTrue(); // GH-90000
            assertThat(result.expectedCount()).isEqualTo(2); // GH-90000
            assertThat(result.actualCount()).isEqualTo(2); // GH-90000
            assertThat(result.errorMessage()).isNull(); // GH-90000
        }

        @Test
        @DisplayName("verification fails when actual count does not match expected count [GH-90000]")
        void verify_countsMismatch_fails() throws Exception { // GH-90000
            String backupId = UUID.randomUUID().toString(); // GH-90000

            Map<String, Object> metaData = new HashMap<>(); // GH-90000
            metaData.put("entityCount", 5);  // says 5 // GH-90000
            metaData.put("collections", "aep_patterns,aep_pipelines"); // GH-90000
            Entity meta = entity(backupId, metaData); // GH-90000

            when(client.findById(eq(TENANT), eq(AepBackupRecoveryService.BACKUP_COLLECTION), eq(backupId))) // GH-90000
                    .thenReturn(Promise.of(Optional.of(meta))); // GH-90000

            // But actual backup data only has 1
            when(client.query(eq(TENANT), anyString(), any(Query.class))) // GH-90000
                    .thenReturn(Promise.of(List.of(entity("e1", Map.of("id", "e1"))))) // GH-90000
                    .thenReturn(Promise.of(List.of())); // 0 in pipelines // GH-90000

            AepBackupRecoveryService.VerificationResult result =
                    service.verifyBackup(TENANT, backupId).getResult(); // GH-90000

            assertThat(result.valid()).isFalse(); // GH-90000
            assertThat(result.expectedCount()).isEqualTo(5); // GH-90000
            assertThat(result.actualCount()).isEqualTo(1); // GH-90000
            assertThat(result.errorMessage()).contains("mismatch [GH-90000]");
        }

        @Test
        @DisplayName("verification returns invalid when backup metadata not found [GH-90000]")
        void verify_metadataNotFound_returnsInvalid() throws Exception { // GH-90000
            when(client.findById(eq(TENANT), eq(AepBackupRecoveryService.BACKUP_COLLECTION), anyString())) // GH-90000
                    .thenReturn(Promise.of(Optional.empty())); // GH-90000

            AepBackupRecoveryService.VerificationResult result =
                    service.verifyBackup(TENANT, "ghost-backup").getResult(); // GH-90000

            assertThat(result.valid()).isFalse(); // GH-90000
            assertThat(result.errorMessage()).contains("not found [GH-90000]");
        }
    }

    // =========================================================================
    //  6. Delete backup
    // =========================================================================

    @Nested
    @DisplayName("deleteBackup [GH-90000]")
    class DeleteBackup {

        @Test
        @DisplayName("deletes backup metadata when backup not found [GH-90000]")
        void delete_backupNotFound_deletesMetadata() throws Exception { // GH-90000
            String backupId = "missing-backup";

            when(client.findById(eq(TENANT), eq(AepBackupRecoveryService.BACKUP_COLLECTION), eq(backupId))) // GH-90000
                    .thenReturn(Promise.of(Optional.empty())); // GH-90000
            when(client.delete(eq(TENANT), eq(AepBackupRecoveryService.BACKUP_COLLECTION), eq(backupId))) // GH-90000
                    .thenReturn(Promise.of(null)); // GH-90000

            // Should not throw
            service.deleteBackup(TENANT, backupId).getResult(); // GH-90000

            verify(client).delete(TENANT, AepBackupRecoveryService.BACKUP_COLLECTION, backupId); // GH-90000
        }
    }

    // =========================================================================
    //  7. Static helper
    // =========================================================================

    @Nested
    @DisplayName("backupDataCollection helper [GH-90000]")
    class BackupDataCollectionHelper {

        @Test
        @DisplayName("generates deterministic collection name from backupId and source collection [GH-90000]")
        void backupDataCollection_deterministicName() { // GH-90000
            String name = AepBackupRecoveryService.backupDataCollection("my-backup-id", "aep_patterns"); // GH-90000
            assertThat(name).isEqualTo("aep_backup_my-backup-id_aep_patterns [GH-90000]");
        }
    }
}
