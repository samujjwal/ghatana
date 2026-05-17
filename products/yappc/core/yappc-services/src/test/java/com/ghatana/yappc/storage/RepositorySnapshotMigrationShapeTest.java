package com.ghatana.yappc.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type test
 * @doc.purpose Validates repository snapshot migrations align to canonical repository schema expectations
 * @doc.layer test
 * @doc.pattern ContractTest
 */
@DisplayName("Repository Snapshot Migration Shape Tests")
class RepositorySnapshotMigrationShapeTest {

    private static final Path MIGRATION_ROOT = Path.of(
        "src/main/resources/db/migration"
    );

    @Test
    @DisplayName("V23 must align legacy and canonical repository snapshot columns")
    void v23MustAlignLegacyAndCanonicalColumns() throws Exception {
        String v23 = Files.readString(MIGRATION_ROOT.resolve("V23__align_repository_snapshot_schema.sql"));

        assertThat(v23).contains("ADD COLUMN IF NOT EXISTS snapshot_id");
        assertThat(v23).contains("SET snapshot_id = id");
        assertThat(v23).contains("ADD COLUMN IF NOT EXISTS materialized_root");
        assertThat(v23).contains("SET materialized_root = local_root_path");
        assertThat(v23).contains("ADD COLUMN IF NOT EXISTS checksum");
        assertThat(v23).contains("SET checksum = content_checksum");
        assertThat(v23).contains("ADD COLUMN IF NOT EXISTS repo_id");
        assertThat(v23).contains("SET repo_id = repository_id");
        assertThat(v23).contains("CREATE UNIQUE INDEX IF NOT EXISTS uk_repository_snapshots_snapshot_id");
        assertThat(v23).contains("repository_snapshot_files");
        assertThat(v23).contains("content_checksum");
        assertThat(v23).contains("file_type");
    }

    @Test
    @DisplayName("migration chain must include legacy source, additive drift, and fix-forward alignment")
    void migrationChainMustIncludeLegacyAndFixForwardSteps() throws Exception {
        String v15 = Files.readString(MIGRATION_ROOT.resolve("V15__create_repository_snapshots.sql"));
        String v17 = Files.readString(MIGRATION_ROOT.resolve("V17__repository_snapshots_and_inventory.sql"));
        String v21 = Files.readString(MIGRATION_ROOT.resolve("V21__add_snapshot_source_locator.sql"));
        String v23 = Files.readString(MIGRATION_ROOT.resolve("V23__align_repository_snapshot_schema.sql"));

        assertThat(v15).contains("CREATE TABLE IF NOT EXISTS repository_snapshots");
        assertThat(v15).contains("id                    VARCHAR(255) PRIMARY KEY");
        assertThat(v15).contains("local_root_path");

        assertThat(v17).contains("CREATE TABLE IF NOT EXISTS repository_snapshots");
        assertThat(v17).contains("snapshot_id");
        assertThat(v17).contains("repository_id");

        assertThat(v21).contains("ADD COLUMN IF NOT EXISTS source_locator_json");
        assertThat(v21).contains("ADD COLUMN IF NOT EXISTS checksum");

        assertThat(v23).contains("SET snapshot_id = id");
        assertThat(v23).contains("SET materialized_root = local_root_path");
        assertThat(v23).contains("SET repo_id = repository_id");
    }
}
