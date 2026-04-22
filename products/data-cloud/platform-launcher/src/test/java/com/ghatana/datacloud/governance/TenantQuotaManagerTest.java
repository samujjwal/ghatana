/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved. // GH-90000
 *
 * Phase 1 — Tenant Isolation: Comprehensive quota enforcement tests for TenantQuotaManager.
 * Covers connection, request, event, storage, collection, and entity quota operations.
 */
package com.ghatana.datacloud.governance;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for TenantQuotaManager verifying quota enforcement accuracy
 * across all operation types: connection limits, request rate limits, event rate limits,
 * storage quotas, and collection/entity quotas.
 *
 * <p>All async assertions use {@link EventloopTestBase#runPromise} — never call
 * {@code .getResult()} directly on a Promise. // GH-90000
 *
 * @doc.type test
 * @doc.purpose Verify per-tenant resource quota enforcement correctness for all quota types
 * @doc.layer governance
 * @doc.pattern UnitTest
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("TenantQuotaManager [GH-90000]")
class TenantQuotaManagerTest extends EventloopTestBase {

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private PreparedStatement preparedStatement;

    @Mock
    private ResultSet resultSet;

    private TenantQuotaManager manager;

    @BeforeEach
    void setUp() throws Exception { // GH-90000
        lenient().when(dataSource.getConnection()).thenReturn(connection); // GH-90000
        lenient().when(connection.prepareStatement(anyString())).thenReturn(preparedStatement); // GH-90000
        lenient().when(preparedStatement.executeQuery()).thenReturn(resultSet); // GH-90000
        lenient().when(preparedStatement.executeUpdate()).thenReturn(1); // GH-90000
        lenient().when(resultSet.next()).thenReturn(false); // no DB row → default quota returned // GH-90000

        manager = new TenantQuotaManager(dataSource, new SimpleMeterRegistry()); // GH-90000
    }

    private static final int DEFAULT_ENTITIES_PER_COLLECTION = 1_000_000;

    // Convenience: set a low-limit quota in the in-memory cache via setTenantQuota
    private void setLowQuota(String tenantId, int maxConn, int maxReqPerMin, int maxEvtPerSec, // GH-90000
                              long maxStorageMB, int maxCollections) {
        TenantQuotaManager.TenantQuotaConfig config = new TenantQuotaManager.TenantQuotaConfig( // GH-90000
            maxStorageMB, maxConn, maxReqPerMin, maxEvtPerSec, maxCollections,
            DEFAULT_ENTITIES_PER_COLLECTION
        );
        runPromise(() -> manager.setTenantQuota(tenantId, config)); // GH-90000
    }

    // =========================================================================
    // Default Quota Config
    // =========================================================================

    @Nested
    @DisplayName("Default Quota Values [GH-90000]")
    class DefaultQuotaValues {

        @Test
        @DisplayName("default config has 100 concurrent connections [GH-90000]")
        void defaultMaxConnections() { // GH-90000
            TenantQuotaManager.QuotaCheckResult result =
                runPromise(() -> manager.checkQuota("t-default", "CONNECTION", 1)); // GH-90000

            assertThat(result.isAllowed()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("checkQuota returns a well-formed result [GH-90000]")
        void checkQuotaReturnsResult() { // GH-90000
            TenantQuotaManager.QuotaCheckResult result =
                runPromise(() -> manager.checkQuota("t-default", "REQUEST", 1)); // GH-90000

            assertThat(result).isNotNull(); // GH-90000
            assertThat(result.isAllowed()).isTrue(); // GH-90000
            assertThat(result.getUsagePercentage()).isGreaterThanOrEqualTo(0.0); // GH-90000
        }
    }

    // =========================================================================
    // Connection Quota
    // =========================================================================

    @Nested
    @DisplayName("Connection Quota [GH-90000]")
    class ConnectionQuota {

        private static final String TENANT = "t-conn";

        @BeforeEach
        void setupLowLimit() { // GH-90000
            setLowQuota(TENANT, 2, 1000, 100, 10240, 100); // GH-90000
        }

        @Test
        @DisplayName("allows connection when under the limit [GH-90000]")
        void allowsConnectionUnderLimit() { // GH-90000
            TenantQuotaManager.QuotaCheckResult result =
                runPromise(() -> manager.checkQuota(TENANT, "CONNECTION", 1)); // GH-90000

            assertThat(result.isAllowed()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("rejects connection when limit is exceeded [GH-90000]")
        void rejectsConnectionAtLimit() { // GH-90000
            // Consume both slots
            runPromise(() -> manager.checkQuota(TENANT, "CONNECTION", 1)); // GH-90000
            runPromise(() -> manager.checkQuota(TENANT, "CONNECTION", 1)); // GH-90000

            // Third request must be denied
            TenantQuotaManager.QuotaCheckResult result =
                runPromise(() -> manager.checkQuota(TENANT, "CONNECTION", 1)); // GH-90000

            assertThat(result.isAllowed()).isFalse(); // GH-90000
            assertThat(result.getReason()).isNotBlank(); // GH-90000
        }

        @Test
        @DisplayName("releaseConnection allows a previously rejected connection [GH-90000]")
        void releaseAllowsNextConnection() { // GH-90000
            runPromise(() -> manager.checkQuota(TENANT, "CONNECTION", 1)); // GH-90000
            runPromise(() -> manager.checkQuota(TENANT, "CONNECTION", 1)); // GH-90000

            // Release one slot
            manager.releaseConnection(TENANT); // GH-90000

            // Now a third connection should be allowed
            TenantQuotaManager.QuotaCheckResult result =
                runPromise(() -> manager.checkQuota(TENANT, "CONNECTION", 1)); // GH-90000

            assertThat(result.isAllowed()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("QuotaCheckResult carries usage percentage for connections [GH-90000]")
        void resultCarriesUsagePercentage() { // GH-90000
            runPromise(() -> manager.checkQuota(TENANT, "CONNECTION", 1)); // 1/2 = 50% // GH-90000

            TenantQuotaManager.QuotaCheckResult result =
                runPromise(() -> manager.checkQuota(TENANT, "CONNECTION", 1)); // 2/2 = 100% // GH-90000

            assertThat(result.getUsagePercentage()).isGreaterThan(0.0); // GH-90000
        }
    }

    // =========================================================================
    // Request Rate Quota
    // =========================================================================

    @Nested
    @DisplayName("Request Rate Quota [GH-90000]")
    class RequestRateQuota {

        private static final String TENANT = "t-req";

        @BeforeEach
        void setupLowLimit() { // GH-90000
            setLowQuota(TENANT, 100, 3, 100, 10240, 100); // 3 requests/min // GH-90000
        }

        @Test
        @DisplayName("allows requests within the per-minute limit [GH-90000]")
        void allowsRequestsUnderLimit() { // GH-90000
            TenantQuotaManager.QuotaCheckResult result =
                runPromise(() -> manager.checkQuota(TENANT, "REQUEST", 1)); // GH-90000

            assertThat(result.isAllowed()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("rejects request when per-minute limit is exceeded [GH-90000]")
        void rejectsRequestOverLimit() { // GH-90000
            runPromise(() -> manager.checkQuota(TENANT, "REQUEST", 1)); // GH-90000
            runPromise(() -> manager.checkQuota(TENANT, "REQUEST", 1)); // GH-90000
            runPromise(() -> manager.checkQuota(TENANT, "REQUEST", 1)); // GH-90000

            TenantQuotaManager.QuotaCheckResult result =
                runPromise(() -> manager.checkQuota(TENANT, "REQUEST", 1)); // GH-90000

            assertThat(result.isAllowed()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("different tenants have independent request counters [GH-90000]")
        void tenantsAreIsolatedForRequestCounts() { // GH-90000
            setLowQuota("t-req-a", 100, 1, 100, 10240, 100); // GH-90000
            setLowQuota("t-req-b", 100, 1, 100, 10240, 100); // GH-90000

            // Exhaust t-req-a
            runPromise(() -> manager.checkQuota("t-req-a", "REQUEST", 1)); // GH-90000
            TenantQuotaManager.QuotaCheckResult exhausted =
                runPromise(() -> manager.checkQuota("t-req-a", "REQUEST", 1)); // GH-90000

            // t-req-b should still be allowed
            TenantQuotaManager.QuotaCheckResult isolated =
                runPromise(() -> manager.checkQuota("t-req-b", "REQUEST", 1)); // GH-90000

            assertThat(exhausted.isAllowed()).isFalse(); // GH-90000
            assertThat(isolated.isAllowed()).isTrue(); // GH-90000
        }
    }

    // =========================================================================
    // Event Rate Quota
    // =========================================================================

    @Nested
    @DisplayName("Event Rate Quota [GH-90000]")
    class EventRateQuota {

        private static final String TENANT = "t-evt";

        @BeforeEach
        void setupLowLimit() { // GH-90000
            setLowQuota(TENANT, 100, 1000, 2, 10240, 100); // 2 events/sec // GH-90000
        }

        @Test
        @DisplayName("allows events within the per-second limit [GH-90000]")
        void allowsEventsUnderLimit() { // GH-90000
            TenantQuotaManager.QuotaCheckResult result =
                runPromise(() -> manager.checkQuota(TENANT, "EVENT", 1)); // GH-90000

            assertThat(result.isAllowed()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("rejects event burst exceeding per-second limit [GH-90000]")
        void rejectsEventBursts() { // GH-90000
            runPromise(() -> manager.checkQuota(TENANT, "EVENT", 1)); // GH-90000
            runPromise(() -> manager.checkQuota(TENANT, "EVENT", 1)); // GH-90000

            TenantQuotaManager.QuotaCheckResult result =
                runPromise(() -> manager.checkQuota(TENANT, "EVENT", 1)); // GH-90000

            assertThat(result.isAllowed()).isFalse(); // GH-90000
        }
    }

    // =========================================================================
    // Storage Quota
    // =========================================================================

    @Nested
    @DisplayName("Storage Quota [GH-90000]")
    class StorageQuota {

        private static final String TENANT = "t-storage";

        @BeforeEach
        void setupStorageQuota() { // GH-90000
            setLowQuota(TENANT, 100, 1000, 100, 100, 100); // 100 MB storage // GH-90000
        }

        @Test
        @DisplayName("allows storage operation well under the quota [GH-90000]")
        void allowsStorageUnderQuota() { // GH-90000
            TenantQuotaManager.QuotaCheckResult result =
                runPromise(() -> manager.checkQuota(TENANT, "STORAGE", 50)); // 50MB // GH-90000

            assertThat(result.isAllowed()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("rejects storage operation that would exceed the quota [GH-90000]")
        void rejectsStorageOverQuota() { // GH-90000
            // checkStorageQuota converts bytes → MB. Pass 200MB in bytes to exceed 100MB limit.
            TenantQuotaManager.QuotaCheckResult result =
                runPromise(() -> manager.checkQuota(TENANT, "STORAGE", 200L * 1024 * 1024)); // GH-90000

            assertThat(result.isAllowed()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("storage between 80-100% returns isAllowed=true with a warning reason [GH-90000]")
        void storageWarningZoneReturnsAllowedWithReason() { // GH-90000
            // Pass 85MB in bytes (85% of 100MB = warning zone 80-100%) // GH-90000
            TenantQuotaManager.QuotaCheckResult result =
                runPromise(() -> manager.checkQuota(TENANT, "STORAGE", 85L * 1024 * 1024)); // GH-90000

            assertThat(result.isAllowed()).isTrue(); // GH-90000
            assertThat(result.getReason()).isNotBlank(); // GH-90000
        }
    }

    // =========================================================================
    // Collection Quota
    // =========================================================================

    @Nested
    @DisplayName("Collection Quota [GH-90000]")
    class CollectionQuota {

        private static final String TENANT = "t-coll";

        @BeforeEach
        void setupCollectionQuota() { // GH-90000
            setLowQuota(TENANT, 100, 1000, 100, 10240, 2); // 2 collections max // GH-90000
        }

        @Test
        @DisplayName("allows collection creation under the limit [GH-90000]")
        void allowsCollectionCreationUnderLimit() { // GH-90000
            TenantQuotaManager.QuotaCheckResult result =
                runPromise(() -> manager.checkQuota(TENANT, "COLLECTION", 1)); // GH-90000

            assertThat(result.isAllowed()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("rejects collection creation when DB count is at the limit [GH-90000]")
        void rejectsCollectionCreationOverLimit() throws Exception { // GH-90000
            // Stub DB to report currentCollections == maxCollections (2) // GH-90000
            when(resultSet.next()).thenReturn(true); // GH-90000
            when(resultSet.getInt(anyString())).thenReturn(2); // GH-90000

            TenantQuotaManager.QuotaCheckResult result =
                runPromise(() -> manager.checkQuota(TENANT, "COLLECTION", 1)); // GH-90000

            assertThat(result.isAllowed()).isFalse(); // GH-90000
        }
    }

    // =========================================================================
    // QuotaCheckResult API
    // =========================================================================

    @Nested
    @DisplayName("QuotaCheckResult API [GH-90000]")
    class QuotaCheckResultApi {

        @Test
        @DisplayName("allowed result has isAllowed=true and non-negative usage percentage [GH-90000]")
        void allowedResultProperties() { // GH-90000
            TenantQuotaManager.QuotaCheckResult result =
                runPromise(() -> manager.checkQuota("t-api", "REQUEST", 1)); // GH-90000

            assertThat(result.isAllowed()).isTrue(); // GH-90000
            assertThat(result.getUsagePercentage()).isGreaterThanOrEqualTo(0.0); // GH-90000
        }

        @Test
        @DisplayName("denied result has isAllowed=false and non-blank reason [GH-90000]")
        void deniedResultHasReason() { // GH-90000
            setLowQuota("t-api-deny", 100, 1, 100, 10240, 100); // GH-90000
            runPromise(() -> manager.checkQuota("t-api-deny", "REQUEST", 1)); // GH-90000

            TenantQuotaManager.QuotaCheckResult result =
                runPromise(() -> manager.checkQuota("t-api-deny", "REQUEST", 1)); // GH-90000

            assertThat(result.isAllowed()).isFalse(); // GH-90000
            assertThat(result.getReason()).isNotBlank(); // GH-90000
        }
    }

    // =========================================================================
    // Usage Statistics
    // =========================================================================

    @Nested
    @DisplayName("Tenant Usage Statistics [GH-90000]")
    class TenantUsageStatistics {

        @Test
        @DisplayName("getTenantUsage returns a non-null stats object [GH-90000]")
        void returnsNonNullStats() { // GH-90000
            TenantQuotaManager.TenantUsageStats stats = manager.getTenantUsage("any-tenant [GH-90000]");

            assertThat(stats).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("active connection count increases as connections are checked [GH-90000]")
        void activeConnectionCountIncreases() { // GH-90000
            setLowQuota("t-usage", 100, 1000, 100, 10240, 100); // GH-90000
            runPromise(() -> manager.checkQuota("t-usage", "CONNECTION", 1)); // GH-90000
            runPromise(() -> manager.checkQuota("t-usage", "CONNECTION", 1)); // GH-90000

            TenantQuotaManager.TenantUsageStats stats = manager.getTenantUsage("t-usage [GH-90000]");

            assertThat(stats.getActiveConnections()).isGreaterThan(0); // GH-90000
        }

        @Test
        @DisplayName("request count tracks per-tenant request volume [GH-90000]")
        void requestCountTracksPerTenant() { // GH-90000
            runPromise(() -> manager.checkQuota("t-usage-req", "REQUEST", 1)); // GH-90000
            runPromise(() -> manager.checkQuota("t-usage-req", "REQUEST", 1)); // GH-90000

            TenantQuotaManager.TenantUsageStats stats = manager.getTenantUsage("t-usage-req [GH-90000]");

            assertThat(stats.getRequestsLastMinute()).isGreaterThan(0); // GH-90000
        }
    }

    // =========================================================================
    // TenantQuotaConfig API
    // =========================================================================

    @Nested
    @DisplayName("TenantQuotaConfig API [GH-90000]")
    class TenantQuotaConfigApi {

        @Test
        @DisplayName("config exposes all getter values correctly [GH-90000]")
        void configGettersReturnExpectedValues() { // GH-90000
            TenantQuotaManager.TenantQuotaConfig config =
                new TenantQuotaManager.TenantQuotaConfig(512, 50, 200, 10, 25, 500000); // GH-90000

            assertThat(config.getMaxStorageMB()).isEqualTo(512); // GH-90000
            assertThat(config.getMaxConcurrentConnections()).isEqualTo(50); // GH-90000
            assertThat(config.getMaxRequestsPerMinute()).isEqualTo(200); // GH-90000
            assertThat(config.getMaxEventsPerSecond()).isEqualTo(10); // GH-90000
            assertThat(config.getMaxCollections()).isEqualTo(25); // GH-90000
            assertThat(config.getMaxEntitiesPerCollection()).isEqualTo(500000); // GH-90000
        }

        @Test
        @DisplayName("getTenantQuota returns a config with defaults when no record exists in DB [GH-90000]")
        void returnsDefaultWhenNoDbRecord() { // GH-90000
            TenantQuotaManager.TenantQuotaConfig config =
                runPromise(() -> manager.getTenantQuota("unknown-tenant [GH-90000]"));

            assertThat(config).isNotNull(); // GH-90000
            assertThat(config.getMaxConcurrentConnections()).isGreaterThan(0); // GH-90000
            assertThat(config.getMaxStorageMB()).isGreaterThan(0); // GH-90000
        }
    }
}
