/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.services.auth.e2e;

import com.ghatana.platform.security.port.JwtTokenProvider;
import com.ghatana.platform.security.port.JwtTokenProviders;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.services.auth.JdbcCredentialStore;
import com.ghatana.services.auth.JdbcTokenBlocklist;
import com.ghatana.services.auth.PasswordHasher;
import com.ghatana.services.auth.TenantExtractor;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Real-infrastructure auth-gateway E2E flow test
 * @doc.layer shared-services
 * @doc.pattern E2ETest
 */
@Tag("integration")
@Testcontainers
@DisplayName("Auth Gateway Real Infrastructure E2E Tests")
@SuppressWarnings("resource")
class AuthGatewayRealInfrastructureE2ETest extends EventloopTestBase {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("auth_e2e_test")
            .withUsername("auth")
            .withPassword("auth");

    private HikariDataSource dataSource;
    private JdbcCredentialStore credentialStore;
    private JdbcTokenBlocklist tokenBlocklist;
    private TenantExtractor tenantExtractor;
    private JwtTokenProvider tokenProvider;

    @BeforeEach
    void setUp() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(POSTGRES.getJdbcUrl());
        config.setUsername(POSTGRES.getUsername());
        config.setPassword(POSTGRES.getPassword());
        config.setMaximumPoolSize(4);
        dataSource = new HikariDataSource(config);

        credentialStore = new JdbcCredentialStore(dataSource);
        tokenBlocklist = new JdbcTokenBlocklist(dataSource);
        tenantExtractor = new TenantExtractor();
        tokenProvider = JwtTokenProviders.fromSharedSecret(
            "0123456789abcdef0123456789abcdef",
            300_000
        );

        credentialStore.ensureSchema();
        tokenBlocklist.ensureSchema();
    }

    @AfterEach
    void tearDown() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE TABLE auth_users");
            stmt.execute("TRUNCATE TABLE auth_token_blocklist");
        }
        dataSource.close();
    }

    @Test
    @DisplayName("register-authenticate-extract-tenant-revoke flow works against real Postgres")
    void shouldExecuteAuthFlowAgainstRealInfrastructure() {
        String username = "e2e-user";
        String plainPassword = "StrongPassword123!";
        String tenantId = "tenant-e2e";

        String passwordHash = PasswordHasher.hash(plainPassword);
        runPromise(() -> credentialStore.createUser(
            username,
            passwordHash,
            "e2e-user@ghatana.com",
            List.of("USER"),
            tenantId
        ));

        Optional<com.ghatana.services.auth.CredentialStore.StoredUser> loaded =
            runPromise(() -> credentialStore.findByUsername(username));

        assertThat(loaded).isPresent();
        assertThat(PasswordHasher.verify(plainPassword, loaded.get().passwordHash())).isTrue();
        assertThat(loaded.get().tenantId()).isEqualTo(tenantId);

        String token = tokenProvider.createToken(
            username,
            List.of("USER"),
            Map.of("tenantId", tenantId)
        );

        HttpRequest request = HttpRequest.get("http://localhost:8080/api/private")
            .withHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .build();

        String extractedTenant = tenantExtractor.extract(request, tokenProvider);
        assertThat(extractedTenant).isEqualTo(tenantId);

        long expiresAt = System.currentTimeMillis() + 60_000;
        runPromise(() -> tokenBlocklist.block("jti-e2e", expiresAt));
        assertThat(runPromise(() -> tokenBlocklist.isBlocked("jti-e2e"))).isTrue();
    }
}
