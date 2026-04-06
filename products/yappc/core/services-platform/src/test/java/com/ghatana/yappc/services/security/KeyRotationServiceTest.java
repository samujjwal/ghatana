/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Services Platform — KeyRotationService Tests
 */
package com.ghatana.yappc.services.security;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.infrastructure.security.EncryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.sql.*;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link KeyRotationService}.
 *
 * <p>All JDBC operations are mocked. The test verifies that:
 * <ul>
 *   <li>{@link KeyRotationService#registerKey} inserts a key_versions row with ACTIVE status</li>
 *   <li>{@link KeyRotationService#rotateKey} supersedes the old key, activates the new one,
 *       and creates a key_rotation_jobs row — all in a transaction</li>
 *   <li>{@link KeyRotationService#completeRotationJob} updates the job to COMPLETE/FAILED</li>
 *   <li>{@link KeyRotationService#getActiveVersionId} returns the active version or empty</li>
 *   <li>{@link KeyRotationService#encryptionServiceForKey} and {@link KeyRotationService#decryptWithOldKey}
 *       delegate correctly to {@link EncryptionService}</li>
 *   <li>Constructor rejects null arguments</li>
 * </ul>
 */
@DisplayName("KeyRotationService")
@ExtendWith(MockitoExtension.class)
class KeyRotationServiceTest extends EventloopTestBase {

    @Mock private DataSource        dataSource;
    @Mock private Connection        connection;
    @Mock private PreparedStatement insertVersionStmt;
    @Mock private PreparedStatement supersedeStmt;
    @Mock private PreparedStatement activateStmt;
    @Mock private PreparedStatement insertJobStmt;
    @Mock private PreparedStatement completeJobStmt;
    @Mock private PreparedStatement selectActiveStmt;
    @Mock private ResultSet         resultSet;

    private KeyRotationService service;

    /** Generated 32-byte test key (AES-256). */
    private static final byte[] TEST_KEY_BYTES = new byte[32];

    @BeforeEach
    void setUp() throws SQLException {
        lenient().when(dataSource.getConnection()).thenReturn(connection);
        lenient().when(connection.prepareStatement(anyString())).thenReturn(insertVersionStmt);
        lenient().when(insertVersionStmt.executeUpdate()).thenReturn(1);
        lenient().when(supersedeStmt.executeUpdate()).thenReturn(1);
        lenient().when(activateStmt.executeUpdate()).thenReturn(1);
        lenient().when(insertJobStmt.executeUpdate()).thenReturn(1);
        lenient().when(completeJobStmt.executeUpdate()).thenReturn(1);

        service = new KeyRotationService(dataSource, eventloop());
    }

    // ── registerKey ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("registerKey: inserts ACTIVE key_versions row and returns version ID")
    void registerKey_insertsActiveRow() throws SQLException {
        String versionId = runPromise(() -> service.registerKey("yappc-main-key", "system"));

        assertThat(versionId).isNotBlank();
        verify(connection).prepareStatement(contains("INSERT INTO key_versions"));
        verify(insertVersionStmt).setString(2, "yappc-main-key");
        verify(insertVersionStmt).setString(3, "ACTIVE");
        verify(insertVersionStmt).setString(4, "system");
        verify(insertVersionStmt).executeUpdate();
    }

    @Test
    @DisplayName("registerKey: returns unique UUID for each call")
    void registerKey_returnsUniqueIds() {
        String id1 = runPromise(() -> service.registerKey("key-alias", "system"));
        String id2 = runPromise(() -> service.registerKey("key-alias", "system"));

        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    @DisplayName("registerKey: null keyAlias throws NullPointerException")
    void registerKey_nullAlias_throws() {
        assertThatThrownBy(() -> runPromise(() -> service.registerKey(null, "system")))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("keyAlias");
    }

    @Test
    @DisplayName("registerKey: null createdBy throws NullPointerException")
    void registerKey_nullCreatedBy_throws() {
        assertThatThrownBy(() -> runPromise(() -> service.registerKey("key-alias", null)))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("createdBy");
    }

    // ── rotateKey ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("rotateKey: supersedes old key, activates new key, creates job row")
    void rotateKey_fullWorkflow() throws SQLException {
        // rotateKey opens one Connection and calls prepareStatement 4 times in order:
        //   1. SELECT (findActiveVersion)
        //   2. UPDATE … SUPERSEDED
        //   3. UPDATE … ACTIVE
        //   4. INSERT INTO key_rotation_jobs
        when(connection.prepareStatement(anyString()))
                .thenReturn(selectActiveStmt)   // call 1: SELECT
                .thenReturn(supersedeStmt)      // call 2: SUPERSEDED update
                .thenReturn(activateStmt)       // call 3: ACTIVE update
                .thenReturn(insertJobStmt);     // call 4: job insert

        when(selectActiveStmt.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getString("version_id")).thenReturn("old-version-id");
        when(supersedeStmt.executeUpdate()).thenReturn(1);
        when(activateStmt.executeUpdate()).thenReturn(1);
        when(insertJobStmt.executeUpdate()).thenReturn(1);

        String jobId = runPromise(() ->
                service.rotateKey("yappc-main-key", "new-version-id", "admin"));

        assertThat(jobId).isNotBlank();

        verify(connection).setAutoCommit(false);
        verify(supersedeStmt).setString(2, "old-version-id");
        verify(insertJobStmt).setString(3, "old-version-id");
        verify(insertJobStmt).setString(4, "new-version-id");
        verify(connection).commit();
    }

    @Test
    @DisplayName("rotateKey: rolls back transaction when no active key found")
    void rotateKey_noActiveKey_rollsBack() throws SQLException {
        when(connection.prepareStatement(contains("SELECT version_id"))).thenReturn(selectActiveStmt);
        when(selectActiveStmt.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);    // no active key

        assertThatThrownBy(() ->
                runPromise(() -> service.rotateKey("yappc-main-key", "new-id", "admin")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No active key found for alias");

        verify(connection).rollback();
    }

    @Test
    @DisplayName("rotateKey: null keyAlias throws NullPointerException")
    void rotateKey_nullAlias_throws() {
        assertThatThrownBy(() ->
                runPromise(() -> service.rotateKey(null, "new-id", "admin")))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("keyAlias");
    }

    // ── completeRotationJob ──────────────────────────────────────────────────

    @Test
    @DisplayName("completeRotationJob: marks job COMPLETE when zero failures")
    void completeRotationJob_zeroFailures_marksComplete() throws SQLException {
        when(connection.prepareStatement(anyString())).thenReturn(completeJobStmt);
        when(completeJobStmt.executeUpdate()).thenReturn(1);

        runPromise(() -> service.completeRotationJob("job-1", 500L, 0L, null));

        verify(completeJobStmt).setString(1, "COMPLETE");
        verify(completeJobStmt).setLong(3, 500L);
        verify(completeJobStmt).setLong(4, 0L);
        verify(completeJobStmt).setString(5, null);
        verify(completeJobStmt).setString(6, "job-1");
    }

    @Test
    @DisplayName("completeRotationJob: marks job FAILED when failures > 0")
    void completeRotationJob_withFailures_marksFailed() throws SQLException {
        when(connection.prepareStatement(anyString())).thenReturn(completeJobStmt);
        when(completeJobStmt.executeUpdate()).thenReturn(1);

        runPromise(() -> service.completeRotationJob("job-2", 490L, 10L, "some error"));

        verify(completeJobStmt).setString(1, "FAILED");
        verify(completeJobStmt).setLong(4, 10L);
        verify(completeJobStmt).setString(5, "some error");
    }

    @Test
    @DisplayName("completeRotationJob: null jobId throws NullPointerException")
    void completeRotationJob_nullJobId_throws() {
        assertThatThrownBy(() ->
                runPromise(() -> service.completeRotationJob(null, 0L, 0L, null)))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("jobId");
    }

    // ── getActiveVersionId ───────────────────────────────────────────────────

    @Test
    @DisplayName("getActiveVersionId: returns version ID when ACTIVE key exists")
    void getActiveVersionId_found() throws SQLException {
        when(connection.prepareStatement(anyString())).thenReturn(selectActiveStmt);
        when(selectActiveStmt.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getString("version_id")).thenReturn("v-abc-123");

        Optional<String> result = runPromise(() ->
                service.getActiveVersionId("yappc-main-key"));

        assertThat(result).isPresent().hasValue("v-abc-123");
    }

    @Test
    @DisplayName("getActiveVersionId: returns empty when no ACTIVE key for alias")
    void getActiveVersionId_notFound() throws SQLException {
        when(connection.prepareStatement(anyString())).thenReturn(selectActiveStmt);
        when(selectActiveStmt.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        Optional<String> result = runPromise(() ->
                service.getActiveVersionId("yappc-main-key"));

        assertThat(result).isEmpty();
    }

    // ── encryptionServiceForKey ──────────────────────────────────────────────

    @Test
    @DisplayName("encryptionServiceForKey: returns EncryptionService for given key bytes")
    void encryptionServiceForKey_returnsInstance() {
        EncryptionService enc = service.encryptionServiceForKey(TEST_KEY_BYTES);
        assertThat(enc).isNotNull();
    }

    @Test
    @DisplayName("encryptionServiceForKey: null keyBytes throws NullPointerException")
    void encryptionServiceForKey_nullThrows() {
        assertThatThrownBy(() -> service.encryptionServiceForKey(null))
                .isInstanceOf(NullPointerException.class);
    }

    // ── decryptWithOldKey ────────────────────────────────────────────────────

    @Test
    @DisplayName("decryptWithOldKey: decrypts ciphertext produced by EncryptionService")
    void decryptWithOldKey_roundTrip() {
        EncryptionService enc = new EncryptionService(TEST_KEY_BYTES);
        String plaintext  = "my-secret-env-value";
        String ciphertext = enc.encrypt(plaintext);

        String decrypted = service.decryptWithOldKey(ciphertext, TEST_KEY_BYTES);

        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    @DisplayName("decryptWithOldKey: null ciphertext throws NullPointerException")
    void decryptWithOldKey_nullCiphertext_throws() {
        assertThatThrownBy(() -> service.decryptWithOldKey(null, TEST_KEY_BYTES))
                .isInstanceOf(NullPointerException.class);
    }

    // ── Constructor validation ────────────────────────────────────────────────

    @Test
    @DisplayName("null dataSource throws NullPointerException")
    void constructor_nullDataSource_throws() {
        assertThatThrownBy(() -> new KeyRotationService(null, eventloop()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("dataSource");
    }

    @Test
    @DisplayName("null eventloop throws NullPointerException")
    void constructor_nullEventloop_throws() {
        assertThatThrownBy(() -> new KeyRotationService(dataSource, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("eventloop");
    }
}
