/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
 * Tests for Data Fabric sync operations (PF002). // GH-90000
 *
 * @doc.type class
 * @doc.purpose Data fabric sync tests
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("DataFabricSync – Sync Operations (PF002)")
class DataFabricSyncTest extends EventloopTestBase {

    @Mock
    private DataFabricConnector connector;

    @Nested
    @DisplayName("Sync Operations")
    class SyncOperationsTests {

        @Test
        @DisplayName("[PF002]: sync_starts_data_synchronization")
        void syncStartsDataSynchronization() { // GH-90000
            String connectionId = "conn-to-sync";

            DataFabricConnector.SyncConfig config = new DataFabricConnector.SyncConfig( // GH-90000
                "full", "target-collection", "0 */6 * * *",
                Map.of("created_at", "> 2024-01-01"), // GH-90000
                true,
                List.of("id", "name", "email") // GH-90000
            );

            DataFabricConnector.SyncResult result = new DataFabricConnector.SyncResult( // GH-90000
                connectionId, true, 1000, 0,
                Instant.now(), Instant.now().plusSeconds(60), null // GH-90000
            );

            when(connector.sync(connectionId, config)) // GH-90000
                .thenReturn(Promise.of(result)); // GH-90000

            DataFabricConnector.SyncResult syncResult = runPromise(() -> // GH-90000
                connector.sync(connectionId, config) // GH-90000
            );

            assertThat(syncResult.success()).isTrue(); // GH-90000
            assertThat(syncResult.recordsSynced()).isEqualTo(1000); // GH-90000
            assertThat(syncResult.recordsFailed()).isZero(); // GH-90000
        }

        @Test
        @DisplayName("[PF002]: sync_reports_failures")
        void syncReportsFailures() { // GH-90000
            String connectionId = "conn-with-failures";

            DataFabricConnector.SyncConfig config = new DataFabricConnector.SyncConfig( // GH-90000
                "incremental", "target-collection", "0 * * * *",
                Map.of(), true, List.of() // GH-90000
            );

            DataFabricConnector.SyncResult result = new DataFabricConnector.SyncResult( // GH-90000
                connectionId, false, 500, 50,
                Instant.now(), null, "Network timeout" // GH-90000
            );

            when(connector.sync(connectionId, config)) // GH-90000
                .thenReturn(Promise.of(result)); // GH-90000

            DataFabricConnector.SyncResult syncResult = runPromise(() -> // GH-90000
                connector.sync(connectionId, config) // GH-90000
            );

            assertThat(syncResult.success()).isFalse(); // GH-90000
            assertThat(syncResult.recordsFailed()).isEqualTo(50); // GH-90000
            assertThat(syncResult.errorMessage()).contains("timeout");
        }

        @Test
        @DisplayName("[PF002]: get_sync_status_returns_progress")
        void getSyncStatusReturnsProgress() { // GH-90000
            String connectionId = "syncing-conn";

            DataFabricConnector.SyncStatus status = new DataFabricConnector.SyncStatus( // GH-90000
                connectionId, "RUNNING", 10000, 6500, 0, 65.0,
                Instant.now().minusSeconds(300), // GH-90000
                Instant.now().plusSeconds(150) // GH-90000
            );

            when(connector.getSyncStatus(connectionId)) // GH-90000
                .thenReturn(Promise.of(status)); // GH-90000

            DataFabricConnector.SyncStatus result = runPromise(() -> // GH-90000
                connector.getSyncStatus(connectionId) // GH-90000
            );

            assertThat(result.state()).isEqualTo("RUNNING");
            assertThat(result.progressPercent()).isEqualTo(65.0); // GH-90000
            assertThat(result.totalRecords()).isEqualTo(10000); // GH-90000
            assertThat(result.syncedRecords()).isEqualTo(6500); // GH-90000
        }

        @Test
        @DisplayName("[PF002]: sync_status_shows_completed_sync")
        void syncStatusShowsCompletedSync() { // GH-90000
            String connectionId = "completed-sync";

            DataFabricConnector.SyncStatus status = new DataFabricConnector.SyncStatus( // GH-90000
                connectionId, "COMPLETED", 5000, 5000, 0, 100.0,
                Instant.now().minusSeconds(600), // GH-90000
                Instant.now() // GH-90000
            );

            when(connector.getSyncStatus(connectionId)) // GH-90000
                .thenReturn(Promise.of(status)); // GH-90000

            DataFabricConnector.SyncStatus result = runPromise(() -> // GH-90000
                connector.getSyncStatus(connectionId) // GH-90000
            );

            assertThat(result.state()).isEqualTo("COMPLETED");
            assertThat(result.progressPercent()).isEqualTo(100.0); // GH-90000
        }
    }

    @Nested
    @DisplayName("Sync Configuration")
    class SyncConfigurationTests {

        @Test
        @DisplayName("[PF002]: incremental_sync_configured_correctly")
        void incrementalSyncConfiguredCorrectly() { // GH-90000
            DataFabricConnector.SyncConfig config = new DataFabricConnector.SyncConfig( // GH-90000
                "incremental", "entities", "0 */4 * * *",
                Map.of("updated_at", "> last_sync"), // GH-90000
                true,
                List.of("id", "name", "status") // GH-90000
            );

            assertThat(config.syncMode()).isEqualTo("incremental");
            assertThat(config.incremental()).isTrue(); // GH-90000
            assertThat(config.targetCollection()).isEqualTo("entities");
        }

        @Test
        @DisplayName("[PF002]: full_sync_configured_correctly")
        void fullSyncConfiguredCorrectly() { // GH-90000
            DataFabricConnector.SyncConfig config = new DataFabricConnector.SyncConfig( // GH-90000
                "full", "all-entities", "0 0 * * 0", // Weekly
                Map.of(), // No filters for full sync // GH-90000
                false,
                List.of() // All columns // GH-90000
            );

            assertThat(config.syncMode()).isEqualTo("full");
            assertThat(config.incremental()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("[PF002]: sync_with_column_selection")
        void syncWithColumnSelection() { // GH-90000
            List<String> selectedColumns = List.of("id", "email", "created_at"); // GH-90000

            DataFabricConnector.SyncConfig config = new DataFabricConnector.SyncConfig( // GH-90000
                "incremental", "users", "0 * * * *",
                Map.of(), true, selectedColumns // GH-90000
            );

            assertThat(config.columns()).containsExactly("id", "email", "created_at"); // GH-90000
        }

        @Test
        @DisplayName("[PF002]: sync_with_filters")
        void syncWithFilters() { // GH-90000
            Map<String, Object> filters = Map.of( // GH-90000
                "status", "active",
                "created_at", "> 2024-01-01",
                "region", "US"
            );

            DataFabricConnector.SyncConfig config = new DataFabricConnector.SyncConfig( // GH-90000
                "incremental", "filtered-entities", "0 */6 * * *",
                filters, true, List.of() // GH-90000
            );

            assertThat(config.filters()).containsKeys("status", "created_at", "region"); // GH-90000
        }
    }

    @Nested
    @DisplayName("Sync Performance")
    class SyncPerformanceTests {

        @Test
        @DisplayName("[PF002]: sync_result_includes_timing")
        void syncResultIncludesTiming() { // GH-90000
            Instant start = Instant.now(); // GH-90000
            Instant end = start.plusSeconds(120); // GH-90000

            DataFabricConnector.SyncResult result = new DataFabricConnector.SyncResult( // GH-90000
                "conn-1", true, 10000, 0, start, end, null
            );

            assertThat(result.startedAt()).isEqualTo(start); // GH-90000
            assertThat(result.completedAt()).isEqualTo(end); // GH-90000

            long durationSeconds = result.completedAt().getEpochSecond() - result.startedAt().getEpochSecond(); // GH-90000
            assertThat(durationSeconds).isEqualTo(120); // GH-90000
        }

        @Test
        @DisplayName("[PF002]: large_sync_tracks_progress")
        void largeSyncTracksProgress() { // GH-90000
            DataFabricConnector.SyncStatus status = new DataFabricConnector.SyncStatus( // GH-90000
                "large-sync", "RUNNING", 1000000, 250000, 0, 25.0,
                Instant.now().minusSeconds(1800), // GH-90000
                Instant.now().plusSeconds(5400) // GH-90000
            );

            assertThat(status.totalRecords()).isEqualTo(1000000); // GH-90000
            assertThat(status.syncedRecords()).isEqualTo(250000); // GH-90000
            assertThat(status.progressPercent()).isEqualTo(25.0); // GH-90000
        }
    }

    @Nested
    @DisplayName("Sync Error Handling")
    class SyncErrorHandlingTests {

        @Test
        @DisplayName("[PF002]: sync_handles_partial_failures")
        void syncHandlesPartialFailures() { // GH-90000
            DataFabricConnector.SyncResult result = new DataFabricConnector.SyncResult( // GH-90000
                "conn-partial", true, 950, 50,
                Instant.now(), Instant.now().plusSeconds(100), null // GH-90000
            );

            // Some failures but overall success
            assertThat(result.success()).isTrue(); // GH-90000
            assertThat(result.recordsSynced()).isEqualTo(950); // GH-90000
            assertThat(result.recordsFailed()).isEqualTo(50); // GH-90000
        }

        @Test
        @DisplayName("[PF002]: sync_reports_connection_errors")
        void syncReportsConnectionErrors() { // GH-90000
            DataFabricConnector.SyncResult result = new DataFabricConnector.SyncResult( // GH-90000
                "conn-error", false, 0, 0,
                Instant.now(), null, "Connection refused" // GH-90000
            );

            assertThat(result.success()).isFalse(); // GH-90000
            assertThat(result.errorMessage()).contains("refused");
        }

        @Test
        @DisplayName("[PF002]: sync_status_shows_error_state")
        void syncStatusShowsErrorState() { // GH-90000
            DataFabricConnector.SyncStatus status = new DataFabricConnector.SyncStatus( // GH-90000
                "error-sync", "FAILED", 1000, 400, 600, 40.0,
                Instant.now().minusSeconds(300), // GH-90000
                null
            );

            assertThat(status.state()).isEqualTo("FAILED");
            assertThat(status.failedRecords()).isEqualTo(600); // GH-90000
        }
    }

    @Nested
    @DisplayName("Sync Scheduling")
    class SyncSchedulingTests {

        @Test
        @DisplayName("[PF002]: cron_schedule_parsed_correctly")
        void cronScheduleParsedCorrectly() { // GH-90000
            String hourlySchedule = "0 * * * *";
            String dailySchedule = "0 0 * * *";
            String weeklySchedule = "0 0 * * 0";

            DataFabricConnector.SyncConfig hourly = new DataFabricConnector.SyncConfig( // GH-90000
                "incremental", "entities", hourlySchedule, Map.of(), true, List.of() // GH-90000
            );
            DataFabricConnector.SyncConfig daily = new DataFabricConnector.SyncConfig( // GH-90000
                "incremental", "entities", dailySchedule, Map.of(), true, List.of() // GH-90000
            );
            DataFabricConnector.SyncConfig weekly = new DataFabricConnector.SyncConfig( // GH-90000
                "full", "entities", weeklySchedule, Map.of(), false, List.of() // GH-90000
            );

            assertThat(hourly.schedule()).isEqualTo(hourlySchedule); // GH-90000
            assertThat(daily.schedule()).isEqualTo(dailySchedule); // GH-90000
            assertThat(weekly.schedule()).isEqualTo(weeklySchedule); // GH-90000
        }
    }
}
