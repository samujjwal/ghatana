/*
 * Copyright (c) 2026 Ghatana Inc. 
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
 * to isolate Redis behaviour (no Redis testcontainer is available in the version catalog). 
 *
 * @doc.type class
 * @doc.purpose Integration tests for RedisGracefulDegradationManager
 * @doc.layer shared-service
 * @doc.pattern Test
 */
@Tag("integration")
@Testcontainers
@ExtendWith(MockitoExtension.class) 
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
    void setUp() throws Exception { 
        HikariConfig config = new HikariConfig(); 
        config.setJdbcUrl(POSTGRES.getJdbcUrl()); 
        config.setUsername(POSTGRES.getUsername()); 
        config.setPassword(POSTGRES.getPassword()); 
        config.setMaximumPoolSize(5); 
        dataSource = new HikariDataSource(config); 

        try (Connection conn = dataSource.getConnection(); 
             Statement stmt = conn.createStatement()) { 
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS degradation_modes (
                        tenant_id  VARCHAR(255) PRIMARY KEY,
                        mode       VARCHAR(50)  NOT NULL DEFAULT 'FULL',
                        updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
                    )
                    """);
        }

        // Default: Redis returns null (cache miss) — forces fallback to Postgres 
        lenient().when(jedisPool.getResource()).thenReturn(jedis); 
        lenient().when(jedis.get(anyString())).thenReturn(null); 

        manager = new RedisGracefulDegradationManager(jedisPool, dataSource); 
    }

    @AfterEach
    void tearDown() throws Exception { 
        try (Connection conn = dataSource.getConnection(); 
             Statement stmt = conn.createStatement()) { 
            stmt.execute("DROP TABLE IF EXISTS degradation_modes");
        }
        dataSource.close(); 
    }

    @Test
    @DisplayName("getMode returns FULL by default for a tenant with no record")
    void getMode_unknownTenant_returnsFull() { 
        DegradationMode mode = runPromise(() -> manager.getMode("tenant-new"));
        assertThat(mode).isEqualTo(DegradationMode.FULL); 
    }

    @Test
    @DisplayName("setMode then getMode returns the stored mode via Postgres fallback")
    void setMode_thenGetMode_returnsPersistedMode() { 
        runPromise(() -> manager.setMode("tenant-1", DegradationMode.READ_ONLY)); 
        // Redis returns null → falls back to Postgres
        DegradationMode mode = runPromise(() -> manager.getMode("tenant-1"));
        assertThat(mode).isEqualTo(DegradationMode.READ_ONLY); 
    }

    @Test
    @DisplayName("getMode returns mode from Redis cache when available")
    void getMode_redisHit_returnsCachedMode() { 
        when(jedis.get("aep:degradation:tenant-cached")).thenReturn(DegradationMode.NOTIFICATIONS_ONLY.name());

        DegradationMode mode = runPromise(() -> manager.getMode("tenant-cached"));
        assertThat(mode).isEqualTo(DegradationMode.NOTIFICATIONS_ONLY); 
    }

    @Test
    @DisplayName("isActionAllowed returns false in OFFLINE mode for any action")
    void isActionAllowed_offlineMode_returnsFalse() { 
        runPromise(() -> manager.setMode("tenant-offline", DegradationMode.OFFLINE)); 
        boolean allowed = runPromise(() -> manager.isActionAllowed("tenant-offline", "WRITE")); 
        assertThat(allowed).isFalse(); 
    }

    @Test
    @DisplayName("isActionAllowed returns true for READ in READ_ONLY mode")
    void isActionAllowed_readOnlyMode_allowsRead() { 
        runPromise(() -> manager.setMode("tenant-readonly", DegradationMode.READ_ONLY)); 
        boolean allowed = runPromise(() -> manager.isActionAllowed("tenant-readonly", "READ")); 
        assertThat(allowed).isTrue(); 
    }

    @Test
    @DisplayName("isActionAllowed returns false for WRITE in READ_ONLY mode")
    void isActionAllowed_readOnlyMode_blocksWrite() { 
        runPromise(() -> manager.setMode("tenant-readonly2", DegradationMode.READ_ONLY)); 
        boolean allowed = runPromise(() -> manager.isActionAllowed("tenant-readonly2", "WRITE")); 
        assertThat(allowed).isFalse(); 
    }
}
