/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.platform.incident;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.sql.Connection;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Integration/unit tests for {@link RedisGracefulDegradationManager}.
 *
 * <p>Uses a real PostgreSQL container for durable storage and mocks JedisPool
 * to isolate Redis behaviour (no Redis testcontainer is available in the version catalog). // GH-90000
 *
 * @doc.type class
 * @doc.purpose Integration tests for RedisGracefulDegradationManager
 * @doc.layer shared-service
 * @doc.pattern Test
 */
@Tag("integration")
@Testcontainers
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("RedisGracefulDegradationManager — integration tests")
class RedisGracefulDegradationManagerTest extends EventloopTestBase {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:15-alpine")
                    .withDatabaseName("aep_degradation_test")
                    .withUsername("aep_test")
                    .withPassword("aep_test");

    @Mock
    private JedisPool jedisPool;

    @Mock
    private Jedis jedis;

    private HikariDataSource dataSource;
    private RedisGracefulDegradationManager manager;

    @BeforeEach
    void setUp() throws Exception { // GH-90000
        HikariConfig config = new HikariConfig(); // GH-90000
        config.setJdbcUrl(POSTGRES.getJdbcUrl()); // GH-90000
        config.setUsername(POSTGRES.getUsername()); // GH-90000
        config.setPassword(POSTGRES.getPassword()); // GH-90000
        config.setMaximumPoolSize(5); // GH-90000
        dataSource = new HikariDataSource(config); // GH-90000

        try (Connection conn = dataSource.getConnection(); // GH-90000
             Statement stmt = conn.createStatement()) { // GH-90000
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS degradation_modes (
                        tenant_id  VARCHAR(255) PRIMARY KEY,
                        mode       VARCHAR(50)  NOT NULL DEFAULT 'FULL',
                        updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
                    )
                    """);
        }

        // Default: Redis returns null (cache miss) — forces fallback to Postgres // GH-90000
        lenient().when(jedisPool.getResource()).thenReturn(jedis); // GH-90000
        lenient().when(jedis.get(anyString())).thenReturn(null); // GH-90000

        manager = new RedisGracefulDegradationManager(jedisPool, dataSource); // GH-90000
    }

    @AfterEach
    void tearDown() throws Exception { // GH-90000
        try (Connection conn = dataSource.getConnection(); // GH-90000
             Statement stmt = conn.createStatement()) { // GH-90000
            stmt.execute("DROP TABLE IF EXISTS degradation_modes");
        }
        dataSource.close(); // GH-90000
    }

    @Test
    @DisplayName("getMode returns FULL by default for a tenant with no record")
    void getMode_unknownTenant_returnsFull() { // GH-90000
        DegradationMode mode = runPromise(() -> manager.getMode("tenant-new"));
        assertThat(mode).isEqualTo(DegradationMode.FULL); // GH-90000
    }

    @Test
    @DisplayName("setMode then getMode returns the stored mode via Postgres fallback")
    void setMode_thenGetMode_returnsPersistedMode() { // GH-90000
        runPromise(() -> manager.setMode("tenant-1", DegradationMode.READ_ONLY)); // GH-90000
        // Redis returns null → falls back to Postgres
        DegradationMode mode = runPromise(() -> manager.getMode("tenant-1"));
        assertThat(mode).isEqualTo(DegradationMode.READ_ONLY); // GH-90000
    }

    @Test
    @DisplayName("getMode returns mode from Redis cache when available")
    void getMode_redisHit_returnsCachedMode() { // GH-90000
        when(jedis.get("aep:degradation:tenant-cached")).thenReturn(DegradationMode.NOTIFICATIONS_ONLY.name());

        DegradationMode mode = runPromise(() -> manager.getMode("tenant-cached"));
        assertThat(mode).isEqualTo(DegradationMode.NOTIFICATIONS_ONLY); // GH-90000
    }

    @Test
    @DisplayName("isActionAllowed returns false in OFFLINE mode for any action")
    void isActionAllowed_offlineMode_returnsFalse() { // GH-90000
        runPromise(() -> manager.setMode("tenant-offline", DegradationMode.OFFLINE)); // GH-90000
        boolean allowed = runPromise(() -> manager.isActionAllowed("tenant-offline", "WRITE")); // GH-90000
        assertThat(allowed).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("isActionAllowed returns true for READ in READ_ONLY mode")
    void isActionAllowed_readOnlyMode_allowsRead() { // GH-90000
        runPromise(() -> manager.setMode("tenant-readonly", DegradationMode.READ_ONLY)); // GH-90000
        boolean allowed = runPromise(() -> manager.isActionAllowed("tenant-readonly", "READ")); // GH-90000
        assertThat(allowed).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("isActionAllowed returns false for WRITE in READ_ONLY mode")
    void isActionAllowed_readOnlyMode_blocksWrite() { // GH-90000
        runPromise(() -> manager.setMode("tenant-readonly2", DegradationMode.READ_ONLY)); // GH-90000
        boolean allowed = runPromise(() -> manager.isActionAllowed("tenant-readonly2", "WRITE")); // GH-90000
        assertThat(allowed).isFalse(); // GH-90000
    }
}
