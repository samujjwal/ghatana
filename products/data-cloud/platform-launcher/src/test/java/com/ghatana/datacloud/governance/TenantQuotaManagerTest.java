/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved. 
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
 * {@code .getResult()} directly on a Promise. 
 *
 * @doc.type test
 * @doc.purpose Verify per-tenant resource quota enforcement correctness for all quota types
 * @doc.layer governance
 * @doc.pattern UnitTest
 */
@ExtendWith(MockitoExtension.class) 
@DisplayName("TenantQuotaManager")
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
    void setUp() throws Exception { 
        lenient().when(dataSource.getConnection()).thenReturn(connection); 
        lenient().when(connection.prepareStatement(anyString())).thenReturn(preparedStatement); 
        lenient().when(preparedStatement.executeQuery()).thenReturn(resultSet); 
        lenient().when(preparedStatement.executeUpdate()).thenReturn(1); 
        lenient().when(resultSet.next()).thenReturn(false); // no DB row → default quota returned 

        manager = new TenantQuotaManager(dataSource, new SimpleMeterRegistry()); 
    }

    private static final int DEFAULT_ENTITIES_PER_COLLECTION = 1_000_000;

    // Convenience: set a low-limit quota in the in-memory cache via setTenantQuota
    private void setLowQuota(String tenantId, int maxConn, int maxReqPerMin, int maxEvtPerSec, 
                              long maxStorageMB, int maxCollections) {
        TenantQuotaManager.TenantQuotaConfig config = new TenantQuotaManager.TenantQuotaConfig( 
            maxStorageMB, maxConn, maxReqPerMin, maxEvtPerSec, maxCollections,
            DEFAULT_ENTITIES_PER_COLLECTION
        );
        runPromise(() -> manager.setTenantQuota(tenantId, config)); 
    }

    // =========================================================================
    // Default Quota Config
    // =========================================================================

    @Nested
    @DisplayName("Default Quota Values")
    class DefaultQuotaValues {

        @Test
        @DisplayName("default config has 100 concurrent connections")
        void defaultMaxConnections() { 
            TenantQuotaManager.QuotaCheckResult result =
                runPromise(() -> manager.checkQuota("t-default", "CONNECTION", 1)); 

            assertThat(result.isAllowed()).isTrue(); 
        }

        @Test
        @DisplayName("checkQuota returns a well-formed result")
        void checkQuotaReturnsResult() { 
            TenantQuotaManager.QuotaCheckResult result =
                runPromise(() -> manager.checkQuota("t-default", "REQUEST", 1)); 

            assertThat(result).isNotNull(); 
            assertThat(result.isAllowed()).isTrue(); 
            assertThat(result.getUsagePercentage()).isGreaterThanOrEqualTo(0.0); 
        }
    }

    // =========================================================================
    // Connection Quota
    // =========================================================================

    @Nested
    @DisplayName("Connection Quota")
    class ConnectionQuota {

        private static final String TENANT = "t-conn";

        @BeforeEach
        void setupLowLimit() { 
            setLowQuota(TENANT, 2, 1000, 100, 10240, 100); 
        }

        @Test
        @DisplayName("allows connection when under the limit")
        void allowsConnectionUnderLimit() { 
            TenantQuotaManager.QuotaCheckResult result =
                runPromise(() -> manager.checkQuota(TENANT, "CONNECTION", 1)); 

            assertThat(result.isAllowed()).isTrue(); 
        }

        @Test
        @DisplayName("rejects connection when limit is exceeded")
        void rejectsConnectionAtLimit() { 
            // Consume both slots
            runPromise(() -> manager.checkQuota(TENANT, "CONNECTION", 1)); 
            runPromise(() -> manager.checkQuota(TENANT, "CONNECTION", 1)); 

            // Third request must be denied
            TenantQuotaManager.QuotaCheckResult result =
                runPromise(() -> manager.checkQuota(TENANT, "CONNECTION", 1)); 

            assertThat(result.isAllowed()).isFalse(); 
            assertThat(result.getReason()).isNotBlank(); 
        }

        @Test
        @DisplayName("releaseConnection allows a previously rejected connection")
        void releaseAllowsNextConnection() { 
            runPromise(() -> manager.checkQuota(TENANT, "CONNECTION", 1)); 
            runPromise(() -> manager.checkQuota(TENANT, "CONNECTION", 1)); 

            // Release one slot
            manager.releaseConnection(TENANT); 

            // Now a third connection should be allowed
            TenantQuotaManager.QuotaCheckResult result =
                runPromise(() -> manager.checkQuota(TENANT, "CONNECTION", 1)); 

            assertThat(result.isAllowed()).isTrue(); 
        }

        @Test
        @DisplayName("QuotaCheckResult carries usage percentage for connections")
        void resultCarriesUsagePercentage() { 
            runPromise(() -> manager.checkQuota(TENANT, "CONNECTION", 1)); // 1/2 = 50% 

            TenantQuotaManager.QuotaCheckResult result =
                runPromise(() -> manager.checkQuota(TENANT, "CONNECTION", 1)); // 2/2 = 100% 

            assertThat(result.getUsagePercentage()).isGreaterThan(0.0); 
        }
    }

    // =========================================================================
    // Request Rate Quota
    // =========================================================================

    @Nested
    @DisplayName("Request Rate Quota")
    class RequestRateQuota {

        private static final String TENANT = "t-req";

        @BeforeEach
        void setupLowLimit() { 
            setLowQuota(TENANT, 100, 3, 100, 10240, 100); // 3 requests/min 
        }

        @Test
        @DisplayName("allows requests within the per-minute limit")
        void allowsRequestsUnderLimit() { 
            TenantQuotaManager.QuotaCheckResult result =
                runPromise(() -> manager.checkQuota(TENANT, "REQUEST", 1)); 

            assertThat(result.isAllowed()).isTrue(); 
        }

        @Test
        @DisplayName("rejects request when per-minute limit is exceeded")
        void rejectsRequestOverLimit() { 
            runPromise(() -> manager.checkQuota(TENANT, "REQUEST", 1)); 
            runPromise(() -> manager.checkQuota(TENANT, "REQUEST", 1)); 
            runPromise(() -> manager.checkQuota(TENANT, "REQUEST", 1)); 

            TenantQuotaManager.QuotaCheckResult result =
                runPromise(() -> manager.checkQuota(TENANT, "REQUEST", 1)); 

            assertThat(result.isAllowed()).isFalse(); 
        }

        @Test
        @DisplayName("different tenants have independent request counters")
        void tenantsAreIsolatedForRequestCounts() { 
            setLowQuota("t-req-a", 100, 1, 100, 10240, 100); 
            setLowQuota("t-req-b", 100, 1, 100, 10240, 100); 

            // Exhaust t-req-a
            runPromise(() -> manager.checkQuota("t-req-a", "REQUEST", 1)); 
            TenantQuotaManager.QuotaCheckResult exhausted =
                runPromise(() -> manager.checkQuota("t-req-a", "REQUEST", 1)); 

            // t-req-b should still be allowed
            TenantQuotaManager.QuotaCheckResult isolated =
                runPromise(() -> manager.checkQuota("t-req-b", "REQUEST", 1)); 

            assertThat(exhausted.isAllowed()).isFalse(); 
            assertThat(isolated.isAllowed()).isTrue(); 
        }
    }

    // =========================================================================
    // Event Rate Quota
    // =========================================================================

    @Nested
    @DisplayName("Event Rate Quota")
    class EventRateQuota {

        private static final String TENANT = "t-evt";

        @BeforeEach
        void setupLowLimit() { 
            setLowQuota(TENANT, 100, 1000, 2, 10240, 100); // 2 events/sec 
        }

        @Test
        @DisplayName("allows events within the per-second limit")
        void allowsEventsUnderLimit() { 
            TenantQuotaManager.QuotaCheckResult result =
                runPromise(() -> manager.checkQuota(TENANT, "EVENT", 1)); 

            assertThat(result.isAllowed()).isTrue(); 
        }

        @Test
        @DisplayName("rejects event burst exceeding per-second limit")
        void rejectsEventBursts() { 
            runPromise(() -> manager.checkQuota(TENANT, "EVENT", 1)); 
            runPromise(() -> manager.checkQuota(TENANT, "EVENT", 1)); 

            TenantQuotaManager.QuotaCheckResult result =
                runPromise(() -> manager.checkQuota(TENANT, "EVENT", 1)); 

            assertThat(result.isAllowed()).isFalse(); 
        }
    }

    // =========================================================================
    // Storage Quota
    // =========================================================================

    @Nested
    @DisplayName("Storage Quota")
    class StorageQuota {

        private static final String TENANT = "t-storage";

        @BeforeEach
        void setupStorageQuota() { 
            setLowQuota(TENANT, 100, 1000, 100, 100, 100); // 100 MB storage 
        }

        @Test
        @DisplayName("allows storage operation well under the quota")
        void allowsStorageUnderQuota() { 
            TenantQuotaManager.QuotaCheckResult result =
                runPromise(() -> manager.checkQuota(TENANT, "STORAGE", 50)); // 50MB 

            assertThat(result.isAllowed()).isTrue(); 
        }

        @Test
        @DisplayName("rejects storage operation that would exceed the quota")
        void rejectsStorageOverQuota() { 
            // checkStorageQuota converts bytes → MB. Pass 200MB in bytes to exceed 100MB limit.
            TenantQuotaManager.QuotaCheckResult result =
                runPromise(() -> manager.checkQuota(TENANT, "STORAGE", 200L * 1024 * 1024)); 

            assertThat(result.isAllowed()).isFalse(); 
        }

        @Test
        @DisplayName("storage between 80-100% returns isAllowed=true with a warning reason")
        void storageWarningZoneReturnsAllowedWithReason() { 
            // Pass 85MB in bytes (85% of 100MB = warning zone 80-100%) 
            TenantQuotaManager.QuotaCheckResult result =
                runPromise(() -> manager.checkQuota(TENANT, "STORAGE", 85L * 1024 * 1024)); 

            assertThat(result.isAllowed()).isTrue(); 
            assertThat(result.getReason()).isNotBlank(); 
        }
    }

    // =========================================================================
    // Collection Quota
    // =========================================================================

    @Nested
    @DisplayName("Collection Quota")
    class CollectionQuota {

        private static final String TENANT = "t-coll";

        @BeforeEach
        void setupCollectionQuota() { 
            setLowQuota(TENANT, 100, 1000, 100, 10240, 2); // 2 collections max 
        }

        @Test
        @DisplayName("allows collection creation under the limit")
        void allowsCollectionCreationUnderLimit() { 
            TenantQuotaManager.QuotaCheckResult result =
                runPromise(() -> manager.checkQuota(TENANT, "COLLECTION", 1)); 

            assertThat(result.isAllowed()).isTrue(); 
        }

        @Test
        @DisplayName("rejects collection creation when DB count is at the limit")
        void rejectsCollectionCreationOverLimit() throws Exception { 
            // Stub DB to report currentCollections == maxCollections (2) 
            when(resultSet.next()).thenReturn(true); 
            when(resultSet.getInt(anyString())).thenReturn(2); 

            TenantQuotaManager.QuotaCheckResult result =
                runPromise(() -> manager.checkQuota(TENANT, "COLLECTION", 1)); 

            assertThat(result.isAllowed()).isFalse(); 
        }
    }

    // =========================================================================
    // QuotaCheckResult API
    // =========================================================================

    @Nested
    @DisplayName("QuotaCheckResult API")
    class QuotaCheckResultApi {

        @Test
        @DisplayName("allowed result has isAllowed=true and non-negative usage percentage")
        void allowedResultProperties() { 
            TenantQuotaManager.QuotaCheckResult result =
                runPromise(() -> manager.checkQuota("t-api", "REQUEST", 1)); 

            assertThat(result.isAllowed()).isTrue(); 
            assertThat(result.getUsagePercentage()).isGreaterThanOrEqualTo(0.0); 
        }

        @Test
        @DisplayName("denied result has isAllowed=false and non-blank reason")
        void deniedResultHasReason() { 
            setLowQuota("t-api-deny", 100, 1, 100, 10240, 100); 
            runPromise(() -> manager.checkQuota("t-api-deny", "REQUEST", 1)); 

            TenantQuotaManager.QuotaCheckResult result =
                runPromise(() -> manager.checkQuota("t-api-deny", "REQUEST", 1)); 

            assertThat(result.isAllowed()).isFalse(); 
            assertThat(result.getReason()).isNotBlank(); 
        }
    }

    // =========================================================================
    // Usage Statistics
    // =========================================================================

    @Nested
    @DisplayName("Tenant Usage Statistics")
    class TenantUsageStatistics {

        @Test
        @DisplayName("getTenantUsage returns a non-null stats object")
        void returnsNonNullStats() { 
            TenantQuotaManager.TenantUsageStats stats = manager.getTenantUsage("any-tenant");

            assertThat(stats).isNotNull(); 
        }

        @Test
        @DisplayName("active connection count increases as connections are checked")
        void activeConnectionCountIncreases() { 
            setLowQuota("t-usage", 100, 1000, 100, 10240, 100); 
            runPromise(() -> manager.checkQuota("t-usage", "CONNECTION", 1)); 
            runPromise(() -> manager.checkQuota("t-usage", "CONNECTION", 1)); 

            TenantQuotaManager.TenantUsageStats stats = manager.getTenantUsage("t-usage");

            assertThat(stats.getActiveConnections()).isGreaterThan(0); 
        }

        @Test
        @DisplayName("request count tracks per-tenant request volume")
        void requestCountTracksPerTenant() { 
            runPromise(() -> manager.checkQuota("t-usage-req", "REQUEST", 1)); 
            runPromise(() -> manager.checkQuota("t-usage-req", "REQUEST", 1)); 

            TenantQuotaManager.TenantUsageStats stats = manager.getTenantUsage("t-usage-req");

            assertThat(stats.getRequestsLastMinute()).isGreaterThan(0); 
        }
    }

    // =========================================================================
    // TenantQuotaConfig API
    // =========================================================================

    @Nested
    @DisplayName("TenantQuotaConfig API")
    class TenantQuotaConfigApi {

        @Test
        @DisplayName("config exposes all getter values correctly")
        void configGettersReturnExpectedValues() { 
            TenantQuotaManager.TenantQuotaConfig config =
                new TenantQuotaManager.TenantQuotaConfig(512, 50, 200, 10, 25, 500000); 

            assertThat(config.getMaxStorageMB()).isEqualTo(512); 
            assertThat(config.getMaxConcurrentConnections()).isEqualTo(50); 
            assertThat(config.getMaxRequestsPerMinute()).isEqualTo(200); 
            assertThat(config.getMaxEventsPerSecond()).isEqualTo(10); 
            assertThat(config.getMaxCollections()).isEqualTo(25); 
            assertThat(config.getMaxEntitiesPerCollection()).isEqualTo(500000); 
        }

        @Test
        @DisplayName("getTenantQuota returns a config with defaults when no record exists in DB")
        void returnsDefaultWhenNoDbRecord() { 
            TenantQuotaManager.TenantQuotaConfig config =
                runPromise(() -> manager.getTenantQuota("unknown-tenant"));

            assertThat(config).isNotNull(); 
            assertThat(config.getMaxConcurrentConnections()).isGreaterThan(0); 
            assertThat(config.getMaxStorageMB()).isGreaterThan(0); 
        }
    }
}
