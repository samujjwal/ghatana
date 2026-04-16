package com.ghatana.agent.memory.persistence;

import com.ghatana.agent.memory.model.Provenance;
import com.ghatana.agent.memory.model.Validity;
import com.ghatana.agent.memory.model.ValidityStatus;
import com.ghatana.agent.memory.model.episode.EnhancedEpisode;
import com.ghatana.agent.memory.store.MemoryQuery;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Postgres integration tests for {@link JdbcMemoryItemRepository}.
 *
 * @doc.type class
 * @doc.purpose Verify episodic memory items persist and can be queried from PostgreSQL
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("JdbcMemoryItemRepositoryIntegrationTest")
class JdbcMemoryItemRepositoryIntegrationTest extends EventloopTestBase {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("aep_memory")
            .withUsername("aep")
            .withPassword("aep");

    @Test
    @DisplayName("save persists an episode that can be queried by tenant")
    void savePersistsEpisode() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(POSTGRES.getJdbcUrl());
        config.setUsername(POSTGRES.getUsername());
        config.setPassword(POSTGRES.getPassword());

        try (HikariDataSource dataSource = new HikariDataSource(config);
             Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE IF NOT EXISTS memory_items (
                    id TEXT PRIMARY KEY,
                    type TEXT NOT NULL,
                    tenant_id TEXT NOT NULL,
                    sphere_id TEXT,
                    content JSONB NOT NULL,
                    classification TEXT NOT NULL DEFAULT 'UNCLASSIFIED',
                    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                    expires_at TIMESTAMPTZ,
                    deleted_at TIMESTAMPTZ
                )
                """);

            JdbcMemoryItemRepository repository = new JdbcMemoryItemRepository(dataSource);
            EnhancedEpisode episode = EnhancedEpisode.builder()
                .id("episode-1")
                .tenantId("tenant-a")
                .agentId("agent-1")
                .turnId("turn-1")
                .input("hello")
                .output("world")
                .context(Map.of("channel", "test"))
                .provenance(Provenance.builder().source("integration-test").build())
                .validity(Validity.builder().confidence(1.0).status(ValidityStatus.ACTIVE).build())
                .createdAt(Instant.parse("2026-04-15T12:00:00Z"))
                .updatedAt(Instant.parse("2026-04-15T12:00:00Z"))
                .build();

            runPromise(() -> repository.save(episode));
            List<com.ghatana.agent.memory.model.MemoryItem> results = runPromise(() -> repository.findByQuery(
                MemoryQuery.builder().tenantId("tenant-a").limit(10).offset(0).build()));

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getId()).isEqualTo("episode-1");
            assertThat(results.get(0).getTenantId()).isEqualTo("tenant-a");
        }
    }
}