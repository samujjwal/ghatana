package com.ghatana.yappc.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type test
 * @doc.purpose Integration tests for repository snapshot schema migrations, verifying clean DB
 *              installation and upgrade paths from legacy V15-shaped schemas.
 * @doc.layer test
 * @doc.pattern Integration Test
 */
@Testcontainers
@DisplayName("RepositorySnapshot Migration Tests")
class RepositorySnapshotMigrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("yappc_test")
        .withUsername("test")
        .withPassword("test");

    @Test
    @DisplayName("V23 migration adds canonical snapshot_id column and backfills from legacy id")
    void v23MigrationAddsSnapshotIdColumnAndBackfills() throws SQLException, IOException {
        // Start with V15 schema
        executeSql(readMigration("db/migration/V15__create_repository_snapshots.sql"));

        // Insert a V15-shaped record
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO repository_snapshots (id, tenant_id, workspace_id, project_id, provider, repo_id, commit_sha, local_root_path, content_checksum) " +
                "VALUES ('snap-123', 'tenant-1', 'workspace-1', 'project-1', 'github', 'owner/repo', 'abc123', '/tmp/root', 'checksum-1')");
        }

        // Run V23 migration
        executeSql(readMigration("db/migration/V23__align_repository_snapshot_schema.sql"));

        // Verify snapshot_id column exists and is backfilled
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(
            "SELECT snapshot_id, id FROM repository_snapshots WHERE id = 'snap-123'")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("snapshot_id")).isEqualTo("snap-123");
            assertThat(rs.getString("id")).isEqualTo("snap-123");
        }
    }

    @Test
    @DisplayName("V23 migration adds materialized_root column and backfills from local_root_path")
    void v23MigrationAddsMaterializedRootColumnAndBackfills() throws SQLException, IOException {
        // Start with V15 schema
        executeSql(readMigration("db/migration/V15__create_repository_snapshots.sql"));

        // Insert a V15-shaped record
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO repository_snapshots (id, tenant_id, workspace_id, project_id, provider, repo_id, commit_sha, local_root_path, content_checksum) " +
                "VALUES ('snap-123', 'tenant-1', 'workspace-1', 'project-1', 'github', 'owner/repo', 'abc123', '/tmp/root', 'checksum-1')");
        }

        // Run V23 migration
        executeSql(readMigration("db/migration/V23__align_repository_snapshot_schema.sql"));

        // Verify materialized_root column exists and is backfilled
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(
            "SELECT materialized_root, local_root_path FROM repository_snapshots WHERE id = 'snap-123'")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("materialized_root")).isEqualTo("/tmp/root");
            assertThat(rs.getString("local_root_path")).isEqualTo("/tmp/root");
        }
    }

    @Test
    @DisplayName("V23 migration adds checksum column and backfills from content_checksum")
    void v23MigrationAddsChecksumColumnAndBackfills() throws SQLException, IOException {
        // Start with V15 schema
        executeSql(readMigration("db/migration/V15__create_repository_snapshots.sql"));

        // Insert a V15-shaped record
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO repository_snapshots (id, tenant_id, workspace_id, project_id, provider, repo_id, commit_sha, local_root_path, content_checksum) " +
                "VALUES ('snap-123', 'tenant-1', 'workspace-1', 'project-1', 'github', 'owner/repo', 'abc123', '/tmp/root', 'checksum-1')");
        }

        // Run V23 migration
        executeSql(readMigration("db/migration/V23__align_repository_snapshot_schema.sql"));

        // Verify checksum column exists and is backfilled
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(
            "SELECT checksum, content_checksum FROM repository_snapshots WHERE id = 'snap-123'")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("checksum")).isEqualTo("checksum-1");
            assertThat(rs.getString("content_checksum")).isEqualTo("checksum-1");
        }
    }

    @Test
    @DisplayName("V23 migration adds content_hash column for dedup queries")
    void v23MigrationAddsContentHashColumn() throws SQLException, IOException {
        // Start with V15 schema
        executeSql(readMigration("db/migration/V15__create_repository_snapshots.sql"));

        // Run V23 migration
        executeSql(readMigration("db/migration/V23__align_repository_snapshot_schema.sql"));

        // Verify content_hash column exists
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = conn.executeQuery(
            "SELECT column_name FROM information_schema.columns WHERE table_name = 'repository_snapshots' AND column_name = 'content_hash'")) {
            assertThat(rs.next()).isTrue();
        }
    }

    @Test
    @DisplayName("V23 migration adds source_locator_json column for audit")
    void v23MigrationAddsSourceLocatorJsonColumn() throws SQLException, IOException {
        // Start with V15 schema
        executeSql(readMigration("db/migration/V15__create_repository_snapshots.sql"));

        // Run V23 migration
        executeSql(readMigration("db/migration/V23__align_repository_snapshot_schema.sql"));

        // Verify source_locator_json column exists
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = conn.executeQuery(
            "SELECT column_name FROM information_schema.columns WHERE table_name = 'repository_snapshots' AND column_name = 'source_locator_json'")) {
            assertThat(rs.next()).isTrue();
        }
    }

    @Test
    @DisplayName("V23 migration creates unique index on snapshot_id")
    void v23MigrationCreatesUniqueIndexOnSnapshotId() throws SQLException, IOException {
        // Start with V15 schema
        executeSql(readMigration("db/migration/V15__create_repository_snapshots.sql"));

        // Run V23 migration
        executeSql(readMigration("db/migration/V23__align_repository_snapshot_schema.sql"));

        // Verify unique index exists
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = conn.executeQuery(
            "SELECT indexname FROM pg_indexes WHERE indexname = 'uk_repository_snapshots_snapshot_id'")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("indexname")).isEqualTo("uk_repository_snapshots_snapshot_id");
        }
    }

    @Test
    @DisplayName("V23 migration creates index on content_hash for dedup queries")
    void v23MigrationCreatesIndexOnContentHash() throws SQLException, IOException {
        // Start with V15 schema
        executeSql(readMigration("db/migration/V15__create_repository_snapshots.sql"));

        // Run V23 migration
        executeSql(readMigration("db/migration/V23__align_repository_snapshot_schema.sql"));

        // Verify index exists
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = conn.executeQuery(
            "SELECT indexname FROM pg_indexes WHERE indexname = 'idx_repository_snapshots_content_hash'")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("indexname")).isEqualTo("idx_repository_snapshots_content_hash");
        }
    }

    @Test
    @DisplayName("V23 migration aligns repository_snapshot_files with content_checksum")
    void v23MigrationAlignsSnapshotFilesWithContentChecksum() throws SQLException, IOException {
        // Start with V15 schema
        executeSql(readMigration("db/migration/V15__create_repository_snapshots.sql"));

        // Run V23 migration
        executeSql(readMigration("db/migration/V23__align_repository_snapshot_schema.sql"));

        // Verify content_checksum column exists in files table
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = conn.executeQuery(
            "SELECT column_name FROM information_schema.columns WHERE table_name = 'repository_snapshot_files' AND column_name = 'content_checksum'")) {
            assertThat(rs.next()).isTrue();
        }
    }

    @Test
    @DisplayName("V23 migration aligns repository_snapshot_files with file_type")
    void v23MigrationAlignsSnapshotFilesWithFileType() throws SQLException, IOException {
        // Start with V15 schema
        executeSql(readMigration("db/migration/V15__create_repository_snapshots.sql"));

        // Run V23 migration
        executeSql(readMigration("db/migration/V23__align_repository_snapshot_schema.sql"));

        // Verify file_type column exists in files table
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = conn.executeQuery(
            "SELECT column_name FROM information_schema.columns WHERE table_name = 'repository_snapshot_files' AND column_name = 'file_type'")) {
            assertThat(rs.next()).isTrue();
        }
    }

    @Test
    @DisplayName("Clean DB migration - all migrations run successfully from scratch")
    void cleanDbMigrationAllMigrationsRunSuccessfully() throws SQLException, IOException {
        // Run all migrations in order
        executeSql(readMigration("db/migration/V15__create_repository_snapshots.sql"));
        executeSql(readMigration("db/migration/V17__repository_snapshots_and_inventory.sql"));
        executeSql(readMigration("db/migration/V21__add_snapshot_source_locator.sql"));
        executeSql(readMigration("db/migration/V23__align_repository_snapshot_schema.sql"));

        // Verify all expected tables exist
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' AND table_name LIKE 'repository_%'")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("table_name")).isEqualTo("repository_snapshots");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("table_name")).isEqualTo("repository_snapshot_files");
        }
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(
            POSTGRES.getJdbcUrl(),
            POSTGRES.getUsername(),
            POSTGRES.getPassword()
        );
    }

    private void executeSql(String sql) throws SQLException {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            for (String statement : sql.split(";")) {
                String trimmed = statement.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("--")) {
                    stmt.execute(trimmed);
                }
            }
        }
    }

    private String readMigration(String resourcePath) throws IOException {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            assertThat(inputStream).isNotNull();
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
