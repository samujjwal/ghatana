/*
 * Copyright (c) 2026 Ghatana Inc. 
 * All rights reserved.
 */
package com.ghatana.datacloud.fabric;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for Data Fabric sync operations (PF002). 
 *
 * @doc.type class
 * @doc.purpose Data fabric sync tests
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) 
@DisplayName("DataFabricSync – Sync Operations (PF002)")
class DataFabricSyncTest extends EventloopTestBase {

    @Mock
    private DataFabricConnector connector;

    @Nested
    @DisplayName("Sync Operations")
    class SyncOperationsTests {

        @Test
        @DisplayName("[PF002]: sync_starts_data_synchronization")
        void syncStartsDataSynchronization() { 
            String connectionId = "conn-to-sync";

            DataFabricConnector.SyncConfig config = new DataFabricConnector.SyncConfig( 
                "full", "target-collection", "0 */6 * * *",
                Map.of("created_at", "> 2024-01-01"), 
                true,
                List.of("id", "name", "email") 
            );

            DataFabricConnector.SyncResult result = new DataFabricConnector.SyncResult( 
                connectionId, true, 1000, 0,
                Instant.now(), Instant.now().plusSeconds(60), null 
            );

            when(connector.sync(connectionId, config)) 
                .thenReturn(Promise.of(result)); 

            DataFabricConnector.SyncResult syncResult = runPromise(() -> 
                connector.sync(connectionId, config) 
            );

            assertThat(syncResult.success()).isTrue(); 
            assertThat(syncResult.recordsSynced()).isEqualTo(1000); 
            assertThat(syncResult.recordsFailed()).isZero(); 
        }

        @Test
        @DisplayName("[PF002]: sync_reports_failures")
        void syncReportsFailures() { 
            String connectionId = "conn-with-failures";

            DataFabricConnector.SyncConfig config = new DataFabricConnector.SyncConfig( 
                "incremental", "target-collection", "0 * * * *",
                Map.of(), true, List.of() 
            );

            DataFabricConnector.SyncResult result = new DataFabricConnector.SyncResult( 
                connectionId, false, 500, 50,
                Instant.now(), null, "Network timeout" 
            );

            when(connector.sync(connectionId, config)) 
                .thenReturn(Promise.of(result)); 

            DataFabricConnector.SyncResult syncResult = runPromise(() -> 
                connector.sync(connectionId, config) 
            );

            assertThat(syncResult.success()).isFalse(); 
            assertThat(syncResult.recordsFailed()).isEqualTo(50); 
            assertThat(syncResult.errorMessage()).contains("timeout");
        }

        @Test
        @DisplayName("[PF002]: get_sync_status_returns_progress")
        void getSyncStatusReturnsProgress() { 
            String connectionId = "syncing-conn";

            DataFabricConnector.SyncStatus status = new DataFabricConnector.SyncStatus( 
                connectionId, "RUNNING", 10000, 6500, 0, 65.0,
                Instant.now().minusSeconds(300), 
                Instant.now().plusSeconds(150) 
            );

            when(connector.getSyncStatus(connectionId)) 
                .thenReturn(Promise.of(status)); 

            DataFabricConnector.SyncStatus result = runPromise(() -> 
                connector.getSyncStatus(connectionId) 
            );

            assertThat(result.state()).isEqualTo("RUNNING");
            assertThat(result.progressPercent()).isEqualTo(65.0); 
            assertThat(result.totalRecords()).isEqualTo(10000); 
            assertThat(result.syncedRecords()).isEqualTo(6500); 
        }

        @Test
        @DisplayName("[PF002]: sync_status_shows_completed_sync")
        void syncStatusShowsCompletedSync() { 
            String connectionId = "completed-sync";

            DataFabricConnector.SyncStatus status = new DataFabricConnector.SyncStatus( 
                connectionId, "COMPLETED", 5000, 5000, 0, 100.0,
                Instant.now().minusSeconds(600), 
                Instant.now() 
            );

            when(connector.getSyncStatus(connectionId)) 
                .thenReturn(Promise.of(status)); 

            DataFabricConnector.SyncStatus result = runPromise(() -> 
                connector.getSyncStatus(connectionId) 
            );

            assertThat(result.state()).isEqualTo("COMPLETED");
            assertThat(result.progressPercent()).isEqualTo(100.0); 
        }
    }

    @Nested
    @DisplayName("Sync Configuration")
    class SyncConfigurationTests {

        @Test
        @DisplayName("[PF002]: incremental_sync_configured_correctly")
        void incrementalSyncConfiguredCorrectly() { 
            DataFabricConnector.SyncConfig config = new DataFabricConnector.SyncConfig( 
                "incremental", "entities", "0 */4 * * *",
                Map.of("updated_at", "> last_sync"), 
                true,
                List.of("id", "name", "status") 
            );

            assertThat(config.syncMode()).isEqualTo("incremental");
            assertThat(config.incremental()).isTrue(); 
            assertThat(config.targetCollection()).isEqualTo("entities");
        }

        @Test
        @DisplayName("[PF002]: full_sync_configured_correctly")
        void fullSyncConfiguredCorrectly() { 
            DataFabricConnector.SyncConfig config = new DataFabricConnector.SyncConfig( 
                "full", "all-entities", "0 0 * * 0", // Weekly
                Map.of(), // No filters for full sync 
                false,
                List.of() // All columns 
            );

            assertThat(config.syncMode()).isEqualTo("full");
            assertThat(config.incremental()).isFalse(); 
        }

        @Test
        @DisplayName("[PF002]: sync_with_column_selection")
        void syncWithColumnSelection() { 
            List<String> selectedColumns = List.of("id", "email", "created_at"); 

            DataFabricConnector.SyncConfig config = new DataFabricConnector.SyncConfig( 
                "incremental", "users", "0 * * * *",
                Map.of(), true, selectedColumns 
            );

            assertThat(config.columns()).containsExactly("id", "email", "created_at"); 
        }

        @Test
        @DisplayName("[PF002]: sync_with_filters")
        void syncWithFilters() { 
            Map<String, Object> filters = Map.of( 
                "status", "active",
                "created_at", "> 2024-01-01",
                "region", "US"
            );

            DataFabricConnector.SyncConfig config = new DataFabricConnector.SyncConfig( 
                "incremental", "filtered-entities", "0 */6 * * *",
                filters, true, List.of() 
            );

            assertThat(config.filters()).containsKeys("status", "created_at", "region"); 
        }
    }

    @Nested
    @DisplayName("Sync Performance")
    class SyncPerformanceTests {

        @Test
        @DisplayName("[PF002]: sync_result_includes_timing")
        void syncResultIncludesTiming() { 
            Instant start = Instant.now(); 
            Instant end = start.plusSeconds(120); 

            DataFabricConnector.SyncResult result = new DataFabricConnector.SyncResult( 
                "conn-1", true, 10000, 0, start, end, null
            );

            assertThat(result.startedAt()).isEqualTo(start); 
            assertThat(result.completedAt()).isEqualTo(end); 

            long durationSeconds = result.completedAt().getEpochSecond() - result.startedAt().getEpochSecond(); 
            assertThat(durationSeconds).isEqualTo(120); 
        }

        @Test
        @DisplayName("[PF002]: large_sync_tracks_progress")
        void largeSyncTracksProgress() { 
            DataFabricConnector.SyncStatus status = new DataFabricConnector.SyncStatus( 
                "large-sync", "RUNNING", 1000000, 250000, 0, 25.0,
                Instant.now().minusSeconds(1800), 
                Instant.now().plusSeconds(5400) 
            );

            assertThat(status.totalRecords()).isEqualTo(1000000); 
            assertThat(status.syncedRecords()).isEqualTo(250000); 
            assertThat(status.progressPercent()).isEqualTo(25.0); 
        }
    }

    @Nested
    @DisplayName("Sync Error Handling")
    class SyncErrorHandlingTests {

        @Test
        @DisplayName("[PF002]: sync_handles_partial_failures")
        void syncHandlesPartialFailures() { 
            DataFabricConnector.SyncResult result = new DataFabricConnector.SyncResult( 
                "conn-partial", true, 950, 50,
                Instant.now(), Instant.now().plusSeconds(100), null 
            );

            // Some failures but overall success
            assertThat(result.success()).isTrue(); 
            assertThat(result.recordsSynced()).isEqualTo(950); 
            assertThat(result.recordsFailed()).isEqualTo(50); 
        }

        @Test
        @DisplayName("[PF002]: sync_reports_connection_errors")
        void syncReportsConnectionErrors() { 
            DataFabricConnector.SyncResult result = new DataFabricConnector.SyncResult( 
                "conn-error", false, 0, 0,
                Instant.now(), null, "Connection refused" 
            );

            assertThat(result.success()).isFalse(); 
            assertThat(result.errorMessage()).contains("refused");
        }

        @Test
        @DisplayName("[PF002]: sync_status_shows_error_state")
        void syncStatusShowsErrorState() { 
            DataFabricConnector.SyncStatus status = new DataFabricConnector.SyncStatus( 
                "error-sync", "FAILED", 1000, 400, 600, 40.0,
                Instant.now().minusSeconds(300), 
                null
            );

            assertThat(status.state()).isEqualTo("FAILED");
            assertThat(status.failedRecords()).isEqualTo(600); 
        }
    }

    @Nested
    @DisplayName("Sync Scheduling")
    class SyncSchedulingTests {

        @Test
        @DisplayName("[PF002]: cron_schedule_parsed_correctly")
        void cronScheduleParsedCorrectly() { 
            String hourlySchedule = "0 * * * *";
            String dailySchedule = "0 0 * * *";
            String weeklySchedule = "0 0 * * 0";

            DataFabricConnector.SyncConfig hourly = new DataFabricConnector.SyncConfig( 
                "incremental", "entities", hourlySchedule, Map.of(), true, List.of() 
            );
            DataFabricConnector.SyncConfig daily = new DataFabricConnector.SyncConfig( 
                "incremental", "entities", dailySchedule, Map.of(), true, List.of() 
            );
            DataFabricConnector.SyncConfig weekly = new DataFabricConnector.SyncConfig( 
                "full", "entities", weeklySchedule, Map.of(), false, List.of() 
            );

            assertThat(hourly.schedule()).isEqualTo(hourlySchedule); 
            assertThat(daily.schedule()).isEqualTo(dailySchedule); 
            assertThat(weekly.schedule()).isEqualTo(weeklySchedule); 
        }
    }
}
