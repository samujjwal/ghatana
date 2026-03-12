package com.ghatana.statestore.checkpoint;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link PostgresCheckpointStorage} using a real PostgreSQL
 * container (Testcontainers). Tests verify UPSERT semantics, load/delete operations,
 * and SAVEPOINT row protection from deletion.
 *
 * <p>Schema is applied inline (without Flyway) to isolate the test from migration
 * ordering concerns.
 *
 * @doc.type class
 * @doc.purpose Integration tests for durable PostgreSQL checkpoint storage
 * @doc.layer product
 * @doc.pattern Test
 */
@Testcontainers
@DisplayName("PostgresCheckpointStorage")
class PostgresCheckpointStorageTest extends EventloopTestBase {

    @Container
    @SuppressWarnings("resource")
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("aep_test")
                    .withUsername("aep_user")
                    .withPassword("aep_pass");

    /** Schema from V007__create_aep_checkpoints.sql — applied once per class. */
    private static final String DDL = """
            CREATE TABLE IF NOT EXISTS aep_checkpoints (
                id             VARCHAR(255) PRIMARY KEY,
                type           VARCHAR(30)  NOT NULL CHECK (type IN ('CHECKPOINT','SAVEPOINT')),
                status         VARCHAR(30)  NOT NULL CHECK (status IN ('IN_PROGRESS','COMPLETED','FAILED','ABORTED')),
                start_time     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                complete_time  TIMESTAMPTZ,
                failure_reason TEXT,
                operator_acks  JSONB        NOT NULL DEFAULT '{}'
            );
            CREATE INDEX IF NOT EXISTS idx_aep_checkpoints_status ON aep_checkpoints (status);
            CREATE INDEX IF NOT EXISTS idx_aep_checkpoints_type_status ON aep_checkpoints (type, status)
                WHERE type = 'CHECKPOINT';
            """;

    private static HikariDataSource dataSource;
    private PostgresCheckpointStorage storage;

    @BeforeAll
    static void createSchema() throws Exception {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(POSTGRES.getJdbcUrl());
        cfg.setUsername(POSTGRES.getUsername());
        cfg.setPassword(POSTGRES.getPassword());
        cfg.setMaximumPoolSize(5);
        dataSource = new HikariDataSource(cfg);

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(DDL);
        }
    }

    @AfterAll
    static void tearDown() {
        if (dataSource != null) dataSource.close();
    }

    @BeforeEach
    void setUp() throws Exception {
        storage = new PostgresCheckpointStorage(dataSource);
        // Truncate between tests for isolation
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE TABLE aep_checkpoints");
        }
    }

    // =========================================================================
    //  saveCheckpoint()
    // =========================================================================

    @Nested
    @DisplayName("saveCheckpoint()")
    class SaveCheckpointTests {

        @Test
        @DisplayName("saves a new checkpoint and retrieves it")
        void savesAndLoadsCheckpoint() {
            CheckpointId id = CheckpointId.checkpoint("cp-001");
            CheckpointMetadata meta = CheckpointMetadata.builder(id)
                    .status(CheckpointStatus.IN_PROGRESS)
                    .startTime(Instant.now())
                    .build();

            CheckpointMetadata saved = runPromise(() -> storage.saveCheckpoint(meta));

            assertThat(saved.getCheckpointId().getId()).isEqualTo("cp-001");
            assertThat(saved.getStatus()).isEqualTo(CheckpointStatus.IN_PROGRESS);
        }

        @Test
        @DisplayName("UPSERT: updating an existing checkpoint changes its status")
        void upsertUpdatesExistingCheckpoint() {
            CheckpointId id = CheckpointId.checkpoint("cp-upsert");
            Instant start = Instant.now();

            CheckpointMetadata initial = CheckpointMetadata.builder(id)
                    .status(CheckpointStatus.IN_PROGRESS)
                    .startTime(start)
                    .build();
            runPromise(() -> storage.saveCheckpoint(initial));

            CheckpointMetadata updated = CheckpointMetadata.builder(id)
                    .status(CheckpointStatus.COMPLETED)
                    .startTime(start)
                    .completeTime(Instant.now())
                    .build();
            runPromise(() -> storage.saveCheckpoint(updated));

            CheckpointMetadata loaded = runPromise(() -> storage.loadCheckpoint(id));
            assertThat(loaded.getStatus()).isEqualTo(CheckpointStatus.COMPLETED);
            assertThat(loaded.getCompleteTime()).isNotNull();
        }

        @Test
        @DisplayName("saves checkpoint with failure reason")
        void savesCheckpointWithFailureReason() {
            CheckpointId id = CheckpointId.checkpoint("cp-failed");
            CheckpointMetadata meta = CheckpointMetadata.builder(id)
                    .status(CheckpointStatus.FAILED)
                    .startTime(Instant.now())
                    .failureReason("Operator timeout after 30s")
                    .build();

            runPromise(() -> storage.saveCheckpoint(meta));
            CheckpointMetadata loaded = runPromise(() -> storage.loadCheckpoint(id));

            assertThat(loaded.getFailureReason()).isEqualTo("Operator timeout after 30s");
        }

        @Test
        @DisplayName("saves checkpoint with operator acknowledgements")
        void savesCheckpointWithOperatorAcks() {
            CheckpointId id = CheckpointId.checkpoint("cp-acks");
            CheckpointMetadata meta = CheckpointMetadata.builder(id)
                    .status(CheckpointStatus.COMPLETED)
                    .startTime(Instant.now())
                    .operatorAck("op-1", new OperatorCheckpointInfo("op-1", "SUCCESS", Instant.now(), 100L))
                    .operatorAck("op-2", new OperatorCheckpointInfo("op-2", "SUCCESS", Instant.now(), 200L))
                    .build();

            runPromise(() -> storage.saveCheckpoint(meta));
            CheckpointMetadata loaded = runPromise(() -> storage.loadCheckpoint(id));

            assertThat(loaded.getOperatorAcks()).containsKeys("op-1", "op-2");
        }
    }

    // =========================================================================
    //  loadCheckpoint()
    // =========================================================================

    @Nested
    @DisplayName("loadCheckpoint()")
    class LoadCheckpointTests {

        @Test
        @DisplayName("returns null (or throws) for unknown checkpoint ID")
        void unknownIdReturnsNull() {
            CheckpointId id = CheckpointId.checkpoint("non-existent");
            CheckpointMetadata result = runPromise(() -> storage.loadCheckpoint(id));
            assertThat(result).isNull();
        }
    }

    // =========================================================================
    //  deleteCheckpoint()
    // =========================================================================

    @Nested
    @DisplayName("deleteCheckpoint()")
    class DeleteCheckpointTests {

        @Test
        @DisplayName("deletes a CHECKPOINT type row")
        void deletesCheckpointTypeRow() {
            CheckpointId id = CheckpointId.checkpoint("cp-delete");
            CheckpointMetadata meta = CheckpointMetadata.builder(id)
                    .status(CheckpointStatus.COMPLETED)
                    .startTime(Instant.now())
                    .build();
            runPromise(() -> storage.saveCheckpoint(meta));

            runPromise(() -> storage.deleteCheckpoint(id));

            CheckpointMetadata loaded = runPromise(() -> storage.loadCheckpoint(id));
            assertThat(loaded).isNull();
        }

        @Test
        @DisplayName("SAVEPOINT rows are protected from deletion")
        void savepointRowsAreProtectedFromDeletion() {
            CheckpointId spId = CheckpointId.savepoint("sp-protected");
            CheckpointMetadata meta = CheckpointMetadata.builder(spId)
                    .status(CheckpointStatus.COMPLETED)
                    .startTime(Instant.now())
                    .build();
            runPromise(() -> storage.saveSavepoint(meta));

            // deleteCheckpoint only deletes type='CHECKPOINT', not type='SAVEPOINT'
            runPromise(() -> storage.deleteCheckpoint(spId));

            CheckpointMetadata loaded = runPromise(() -> storage.loadCheckpoint(spId));
            assertThat(loaded).isNotNull(); // still exists
        }
    }

    // =========================================================================
    //  saveSavepoint()
    // =========================================================================

    @Nested
    @DisplayName("saveSavepoint()")
    class SaveSavepointTests {

        @Test
        @DisplayName("saves a savepoint with SAVEPOINT type in database")
        void savesSavepointType() {
            CheckpointId id = CheckpointId.savepoint("sp-v1.0");
            CheckpointMetadata meta = CheckpointMetadata.builder(id)
                    .status(CheckpointStatus.COMPLETED)
                    .startTime(Instant.now())
                    .build();

            runPromise(() -> storage.saveSavepoint(meta));
            CheckpointMetadata loaded = runPromise(() -> storage.loadCheckpoint(id));

            assertThat(loaded).isNotNull();
            assertThat(loaded.getCheckpointId().getType()).isEqualTo(CheckpointType.SAVEPOINT);
        }
    }
}
