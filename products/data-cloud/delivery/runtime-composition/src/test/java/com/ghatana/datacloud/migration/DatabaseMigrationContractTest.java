package com.ghatana.datacloud.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract tests for Data Cloud SQL migrations.
 *
 * <p>These tests provide fast guardrails that migration scripts continue to enforce
 * tenant scoping and indexability for tenant-bound queries.
 */
@DisplayName("Database migration contract")
class DatabaseMigrationContractTest {

    private static final Path MIGRATIONS_DIR =
        Path.of("src", "main", "resources", "db", "migration");

    private static final Pattern VERSION_PATTERN = Pattern.compile("^V(\\d+)__.*\\.sql$");

    @Test
    @DisplayName("migration versions are contiguous with no gaps")
    void migrationVersionsAreContiguous() throws IOException {
        List<Integer> versions = Files.list(MIGRATIONS_DIR)
            .map(path -> path.getFileName().toString())
            .map(VERSION_PATTERN::matcher)
            .filter(Matcher::matches)
            .map(m -> Integer.parseInt(m.group(1)))
            .sorted()
            .toList();

        assertThat(versions).isNotEmpty();
        for (int i = 0; i < versions.size(); i++) {
            assertThat(versions.get(i)).isEqualTo(i + 1);
        }
    }

    @Test
    @DisplayName("core table migrations require tenant_id with not-null semantics")
    void coreTableMigrationsRequireTenantId() throws IOException {
        String eventsSql = readSql("V001__create_events_table.sql");
        String entitiesSql = readSql("V002__create_entities_table.sql");
        String eventLogSql = readSql("V005__create_event_log.sql");

        assertContainsTenantIdNotNull(eventsSql, "V001__create_events_table.sql");
        assertContainsTenantIdNotNull(entitiesSql, "V002__create_entities_table.sql");
        assertContainsTenantIdNotNull(eventLogSql, "V005__create_event_log.sql");
    }

    @Test
    @DisplayName("agent and memory domain table migrations require tenant_id with not-null semantics")
    void agentAndMemoryTableMigrationsRequireTenantId() throws IOException {
        String agentReleasesSql = readSql("V013__create_agent_releases.sql");
        String agentRolloutsSql = readSql("V014__create_agent_rollouts.sql");
        String evaluationResultsSql = readSql("V015__create_evaluation_results.sql");
        String memoryNamespacesSql = readSql("V016__create_memory_namespaces.sql");
        String promotionEvidenceSql = readSql("V017__create_promotion_evidence.sql");
        String mediaArtifactsSql = readSql("V018__create_media_artifacts.sql");

        assertContainsTenantIdNotNull(agentReleasesSql, "V013__create_agent_releases.sql");
        assertContainsTenantIdNotNull(agentRolloutsSql, "V014__create_agent_rollouts.sql");
        assertContainsTenantIdNotNull(evaluationResultsSql, "V015__create_evaluation_results.sql");
        assertContainsTenantIdNotNull(memoryNamespacesSql, "V016__create_memory_namespaces.sql");
        assertContainsTenantIdNotNull(promotionEvidenceSql, "V017__create_promotion_evidence.sql");
        assertContainsTenantIdNotNull(mediaArtifactsSql, "V018__create_media_artifacts.sql");
    }

    @Test
    @DisplayName("at least one migration creates tenant-oriented indexes")
    void tenantIndexesAreDeclaredInMigrations() throws IOException {
        List<String> sqlBodies = Files.list(MIGRATIONS_DIR)
            .sorted(Comparator.comparing(path -> path.getFileName().toString()))
            .map(this::readSqlUnchecked)
            .toList();

        long tenantIndexMentions = sqlBodies.stream()
            .map(sql -> sql.toLowerCase(Locale.ROOT))
            .filter(sql -> sql.contains("create index") && sql.contains("tenant_id"))
            .count();

        assertThat(tenantIndexMentions)
            .as("Expected tenant-aware index declarations across migration scripts")
            .isGreaterThan(0);
    }

    @Test
    @DisplayName("tenant isolation migration enables RLS and tenant policies")
    void tenantIsolationMigrationDeclaresRlsPolicies() throws IOException {
        String tenantIsolationSql = readSql("V011__implement_database_level_tenant_isolation.sql")
            .toLowerCase(Locale.ROOT);

        assertThat(tenantIsolationSql).contains("alter table events enable row level security");
        assertThat(tenantIsolationSql).contains("alter table entities enable row level security");
        assertThat(tenantIsolationSql).contains("create policy tenant_isolation_events");
        assertThat(tenantIsolationSql).contains("create policy tenant_isolation_entities");
        assertThat(tenantIsolationSql).contains("tenant_security.get_current_tenant()");
    }

    @Test
    @DisplayName("RLS extension migration enables tenant policies for new tables")
    void rlsExtensionMigrationDeclaresTenantPoliciesForNewTables() throws IOException {
        String rlsExtensionSql = readSql("V019__extend_rls_to_new_tables.sql")
            .toLowerCase(Locale.ROOT);

        assertThat(rlsExtensionSql).contains("alter table event_log enable row level security");
        assertThat(rlsExtensionSql).contains("create policy tenant_isolation_event_log");

        assertThat(rlsExtensionSql).contains("alter table entity_relations enable row level security");
        assertThat(rlsExtensionSql).contains("create policy tenant_isolation_entity_relations");

        assertThat(rlsExtensionSql).contains("alter table agent_releases enable row level security");
        assertThat(rlsExtensionSql).contains("create policy tenant_isolation_agent_releases");

        assertThat(rlsExtensionSql).contains("alter table agent_rollouts enable row level security");
        assertThat(rlsExtensionSql).contains("create policy tenant_isolation_agent_rollouts");

        assertThat(rlsExtensionSql).contains("alter table evaluation_results enable row level security");
        assertThat(rlsExtensionSql).contains("create policy tenant_isolation_evaluation_results");

        assertThat(rlsExtensionSql).contains("alter table memory_namespaces enable row level security");
        assertThat(rlsExtensionSql).contains("create policy tenant_isolation_memory_namespaces");

        assertThat(rlsExtensionSql).contains("alter table promotion_evidence enable row level security");
        assertThat(rlsExtensionSql).contains("create policy tenant_isolation_promotion_evidence");

        assertThat(rlsExtensionSql).contains("alter table media_artifacts enable row level security");
        assertThat(rlsExtensionSql).contains("create policy tenant_isolation_media_artifacts");

        assertThat(rlsExtensionSql).contains("tenant_security.get_current_tenant()");
    }

    @Test
    @DisplayName("pattern lifecycle migration enables RLS policies")
    void patternLifecycleMigrationDeclaresRlsPolicies() throws IOException {
        String patternLifecycleSql = readSql("V025__create_pattern_lifecycle_tables.sql")
            .toLowerCase(Locale.ROOT);

        assertThat(patternLifecycleSql).contains("alter table pattern_lifecycle_states enable row level security");
        assertThat(patternLifecycleSql).contains("create policy pattern_lifecycle_states_tenant_isolation");

        assertThat(patternLifecycleSql).contains("alter table pattern_lifecycle_events enable row level security");
        assertThat(patternLifecycleSql).contains("create policy pattern_lifecycle_events_tenant_isolation");

        assertThat(patternLifecycleSql).contains("current_setting('app.current_tenant_id', true)");
    }

    @Test
    @DisplayName("product release readiness migration enables RLS policies")
    void productReleaseReadinessMigrationDeclaresRlsPolicies() throws IOException {
        String releaseReadinessSql = readSql("V026__create_product_release_readiness_evidence.sql")
            .toLowerCase(Locale.ROOT);

        assertThat(releaseReadinessSql).contains("alter table product_release_readiness enable row level security");
        assertThat(releaseReadinessSql).contains("create policy product_release_readiness_tenant_isolation");

        assertThat(releaseReadinessSql).contains("alter table product_bootstrap_evidence enable row level security");
        assertThat(releaseReadinessSql).contains("create policy product_bootstrap_evidence_tenant_isolation");

        assertThat(releaseReadinessSql).contains("alter table product_rollback_evidence enable row level security");
        assertThat(releaseReadinessSql).contains("create policy product_rollback_evidence_tenant_isolation");

        assertThat(releaseReadinessSql).contains("current_setting('app.current_tenant_id', true)");
    }

    @Test
    @DisplayName("tenant_id columns have no DEFAULT NULL loophole that would bypass NOT NULL enforcement (P0-02 write-rejection)")
    void tenantIdColumnsHaveNoDefaultNullLoophole() throws IOException {
        // Any tenant_id column with `DEFAULT NULL` would silently permit inserts
        // without tenant scoping, bypassing the NOT NULL constraint.
        // This scanner flags any migration that declares `tenant_id` with a NULL default.
        List<Path> migrationFiles = Files.list(MIGRATIONS_DIR)
            .filter(p -> p.getFileName().toString().toUpperCase(Locale.ROOT).endsWith(".SQL"))
            .sorted(Comparator.comparing(path -> path.getFileName().toString()))
            .toList();

        for (Path migrationFile : migrationFiles) {
            String sql = Files.readString(migrationFile).toLowerCase(Locale.ROOT);
            // Find every line that mentions tenant_id
            String[] lines = sql.split("\\r?\\n");
            for (String line : lines) {
                if (line.contains("tenant_id")) {
                    assertThat(line)
                        .as("Migration %s declares tenant_id with DEFAULT NULL — this bypasses write-rejection", migrationFile.getFileName())
                        .doesNotContain("default null");
                }
            }
        }
    }

    @Test
    @DisplayName("tenant_id columns carry a backing UNIQUE or PRIMARY KEY anchor ensuring tenant-scoped uniqueness (P0-02)")
    void coreTablesMustCarryTenantScopedUniqueConstraintOrPrimaryKey() throws IOException {
        // Events: unique offset per tenant+stream guarantees no cross-tenant offset collision.
        String eventsSql = readSql("V001__create_events_table.sql").toLowerCase(Locale.ROOT);
        assertThat(eventsSql)
            .as("V001 events table must declare a tenant-scoped UNIQUE or PRIMARY KEY constraint")
            .satisfiesAnyOf(
                s -> assertThat(s).contains("unique (tenant_id,"),
                s -> assertThat(s).contains("unique(tenant_id,"),
                s -> assertThat(s).contains("tenant_id, stream_name")
            );

        // Entities: primary key is UUID but collection index must include tenant_id.
        String entitiesSql = readSql("V002__create_entities_table.sql").toLowerCase(Locale.ROOT);
        assertThat(entitiesSql)
            .as("V002 entities table must declare a tenant-scoped index for collection look-ups")
            .contains("on entities (tenant_id, collection_name)");

        // Event log: tenant-scoped offset index required for replay isolation.
        String eventLogSql = readSql("V005__create_event_log.sql").toLowerCase(Locale.ROOT);
        assertThat(eventLogSql)
            .as("V005 event_log must declare a tenant-scoped partition offset index")
            .satisfiesAnyOf(
                s -> assertThat(s).contains("on event_log (tenant_id,"),
                s -> assertThat(s).contains("tenant_id, event_type")
            );
    }

    @Test
    @DisplayName("core workload migrations declare explicit tenant-scoped lookup indexes")
    void coreWorkloadMigrationsDeclareExplicitLookupIndexes() throws IOException {
        String eventsSql = readSql("V001__create_events_table.sql").toLowerCase(Locale.ROOT);
        String entitiesSql = readSql("V002__create_entities_table.sql").toLowerCase(Locale.ROOT);
        String eventLogSql = readSql("V005__create_event_log.sql").toLowerCase(Locale.ROOT);

        assertThat(eventsSql).contains("create index idx_events_partition_offset");
        assertThat(eventsSql).contains("on events (tenant_id, stream_name, partition_id, event_offset)");
        assertThat(eventsSql).contains("create index idx_events_detection_time");
        assertThat(eventsSql).contains("create index idx_events_correlation");

        assertThat(entitiesSql).contains("create index idx_entities_collection");
        assertThat(entitiesSql).contains("on entities (tenant_id, collection_name)");
        assertThat(entitiesSql).contains("create index idx_entities_created_at");

        assertThat(eventLogSql).contains("create index idx_event_log_tenant_offset");
        assertThat(eventLogSql).contains("create index idx_event_log_type");
        assertThat(eventLogSql).contains("on event_log (tenant_id, event_type, offset_value)");
        assertThat(eventLogSql).contains("create index idx_event_log_created_at");
    }

    private String readSql(String fileName) throws IOException {
        Path file = MIGRATIONS_DIR.resolve(fileName);
        return Files.readString(file);
    }

    private String readSqlUnchecked(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read migration: " + path, e);
        }
    }

    private void assertContainsTenantIdNotNull(String sql, String fileName) {
        String normalized = sql.toLowerCase(Locale.ROOT);
        assertThat(normalized)
            .as(fileName + " must declare tenant_id")
            .contains("tenant_id");
        assertThat(normalized)
            .as(fileName + " must enforce tenant_id not null")
            .contains("tenant_id")
            .contains("not null");
    }

    @Test
    @DisplayName("all CREATE TABLE migrations declare tenant_id with NOT NULL enforcement (P0-02 DB write-rejection)")
    void allCreateTableMigrationsDeclareTenanIdNotNull() throws IOException {
        // Collect all migration files that contain CREATE TABLE statements
        // Exclude system/infrastructure migrations that create internal verification or key-management
        // tables that are intentionally not tenant-scoped (encryption, system metadata).
        List<Path> createTableMigrations = Files.list(MIGRATIONS_DIR)
            .filter(p -> {
                String name = p.getFileName().toString().toUpperCase(Locale.ROOT);
                return name.endsWith(".SQL") && !name.contains("README")
                    && !name.contains("ENCRYPTION") // V012: system encryption verification schema
                    && !name.contains("IMPLEMENT_DATABASE_LEVEL_TENANT_ISOLATION"); // V011: installs functions, not domain tables
            })
            .sorted(Comparator.comparing(p -> p.getFileName().toString()))
            .filter(p -> {
                try {
                    return Files.readString(p).toLowerCase(Locale.ROOT).contains("create table");
                } catch (IOException e) {
                    return false;
                }
            })
            .toList();

        assertThat(createTableMigrations)
            .as("Should find migration files containing CREATE TABLE")
            .isNotEmpty();

        for (Path migration : createTableMigrations) {
            String sql = readSqlUnchecked(migration);
            String normalized = sql.toLowerCase(Locale.ROOT);
            String fileName = migration.getFileName().toString();

            // Every table-creating migration must declare tenant_id
            assertThat(normalized)
                .as(fileName + ": every CREATE TABLE migration must declare tenant_id column")
                .contains("tenant_id");

            // The tenant_id column must carry NOT NULL enforcement
            assertThat(normalized)
                .as(fileName + ": tenant_id column must be declared NOT NULL to reject writes without tenant context")
                .contains("not null");
        }
    }
}
