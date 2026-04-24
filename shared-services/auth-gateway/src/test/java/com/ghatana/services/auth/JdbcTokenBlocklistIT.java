/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.services.auth;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose DB-backed token revocation integration tests
 * @doc.layer shared-services
 * @doc.pattern Test
 */
@Tag("integration")
@Tag("infrastructure-backed")
@Testcontainers
@DisplayName("JdbcTokenBlocklist Integration Tests")
@SuppressWarnings("resource")
class JdbcTokenBlocklistIntegrationTest extends EventloopTestBase {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("auth_blocklist_test")
            .withUsername("auth")
            .withPassword("auth");

    private HikariDataSource dataSource;
    private JdbcTokenBlocklist blocklist;

    @BeforeEach
    void setUp() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(POSTGRES.getJdbcUrl());
        config.setUsername(POSTGRES.getUsername());
        config.setPassword(POSTGRES.getPassword());
        config.setMaximumPoolSize(4);
        dataSource = new HikariDataSource(config);

        blocklist = new JdbcTokenBlocklist(dataSource);
        blocklist.ensureSchema();
    }

    @AfterEach
    void tearDown() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE TABLE auth_token_blocklist");
        }
        dataSource.close();
    }

    @Test
    @DisplayName("blocks token JTI and reports it as blocked")
    void shouldBlockTokenJti() {
        long expiresAt = System.currentTimeMillis() + 60_000;

        runPromise(() -> blocklist.block("jti-1", expiresAt));

        assertThat(runPromise(() -> blocklist.isBlocked("jti-1"))).isTrue();
        assertThat(runPromise(() -> blocklist.isBlocked("jti-unknown"))).isFalse();
    }

    @Test
    @DisplayName("expired JTIs are not treated as blocked")
    void shouldIgnoreExpiredJti() {
        long expiredAt = System.currentTimeMillis() - 5_000;

        runPromise(() -> blocklist.block("jti-expired", expiredAt));

        assertThat(runPromise(() -> blocklist.isBlocked("jti-expired"))).isFalse();
    }

    @Test
    @DisplayName("cleanup removes only expired entries")
    void shouldCleanupOnlyExpiredEntries() {
        long now = System.currentTimeMillis();

        runPromise(() -> blocklist.block("jti-old-1", now - 10_000));
        runPromise(() -> blocklist.block("jti-old-2", now - 5_000));
        runPromise(() -> blocklist.block("jti-live", now + 60_000));

        int removed = runPromise(() -> blocklist.cleanupExpired());

        assertThat(removed).isEqualTo(2);
        assertThat(runPromise(() -> blocklist.isBlocked("jti-live"))).isTrue();
    }

    @Test
    @DisplayName("blocking the same JTI twice remains idempotent")
    void shouldBeIdempotentForDuplicateJti() {
        long now = System.currentTimeMillis();

        runPromise(() -> blocklist.block("jti-dup", now + 30_000));
        runPromise(() -> blocklist.block("jti-dup", now + 60_000));

        assertThat(runPromise(() -> blocklist.isBlocked("jti-dup"))).isTrue();
    }
}
