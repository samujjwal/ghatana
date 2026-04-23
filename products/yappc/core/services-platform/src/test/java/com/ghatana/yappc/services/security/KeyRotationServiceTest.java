/*
 * Copyright (c) 2026 Ghatana Technologies // GH-90000
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
@ExtendWith(MockitoExtension.class) // GH-90000
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

    /** Generated 32-byte test key (AES-256). */ // GH-90000
    private static final byte[] TEST_KEY_BYTES = new byte[32];

    @BeforeEach
    void setUp() throws SQLException { // GH-90000
        lenient().when(dataSource.getConnection()).thenReturn(connection); // GH-90000
        lenient().when(connection.prepareStatement(anyString())).thenReturn(insertVersionStmt); // GH-90000
        lenient().when(insertVersionStmt.executeUpdate()).thenReturn(1); // GH-90000
        lenient().when(supersedeStmt.executeUpdate()).thenReturn(1); // GH-90000
        lenient().when(activateStmt.executeUpdate()).thenReturn(1); // GH-90000
        lenient().when(insertJobStmt.executeUpdate()).thenReturn(1); // GH-90000
        lenient().when(completeJobStmt.executeUpdate()).thenReturn(1); // GH-90000

        service = new KeyRotationService(dataSource, eventloop()); // GH-90000
    }

    // ── registerKey ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("registerKey: inserts ACTIVE key_versions row and returns version ID")
    void registerKey_insertsActiveRow() throws SQLException { // GH-90000
        String versionId = runPromise(() -> service.registerKey("yappc-main-key", "system")); // GH-90000

        assertThat(versionId).isNotBlank(); // GH-90000
        verify(connection).prepareStatement(contains("INSERT INTO key_versions"));
        verify(insertVersionStmt).setString(2, "yappc-main-key"); // GH-90000
        verify(insertVersionStmt).setString(3, "ACTIVE"); // GH-90000
        verify(insertVersionStmt).setString(4, "system"); // GH-90000
        verify(insertVersionStmt).executeUpdate(); // GH-90000
    }

    @Test
    @DisplayName("registerKey: returns unique UUID for each call")
    void registerKey_returnsUniqueIds() { // GH-90000
        String id1 = runPromise(() -> service.registerKey("key-alias", "system")); // GH-90000
        String id2 = runPromise(() -> service.registerKey("key-alias", "system")); // GH-90000

        assertThat(id1).isNotEqualTo(id2); // GH-90000
    }

    @Test
    @DisplayName("registerKey: null keyAlias throws NullPointerException")
    void registerKey_nullAlias_throws() { // GH-90000
        assertThatThrownBy(() -> runPromise(() -> service.registerKey(null, "system"))) // GH-90000
                .isInstanceOf(NullPointerException.class) // GH-90000
                .hasMessageContaining("keyAlias");
    }

    @Test
    @DisplayName("registerKey: null createdBy throws NullPointerException")
    void registerKey_nullCreatedBy_throws() { // GH-90000
        assertThatThrownBy(() -> runPromise(() -> service.registerKey("key-alias", null))) // GH-90000
                .isInstanceOf(NullPointerException.class) // GH-90000
                .hasMessageContaining("createdBy");
    }

    // ── rotateKey ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("rotateKey: supersedes old key, activates new key, creates job row")
    void rotateKey_fullWorkflow() throws SQLException { // GH-90000
        // rotateKey opens one Connection and calls prepareStatement 4 times in order:
        //   1. SELECT (findActiveVersion) // GH-90000
        //   2. UPDATE … SUPERSEDED
        //   3. UPDATE … ACTIVE
        //   4. INSERT INTO key_rotation_jobs
        when(connection.prepareStatement(anyString())) // GH-90000
                .thenReturn(selectActiveStmt)   // call 1: SELECT // GH-90000
                .thenReturn(supersedeStmt)      // call 2: SUPERSEDED update // GH-90000
                .thenReturn(activateStmt)       // call 3: ACTIVE update // GH-90000
                .thenReturn(insertJobStmt);     // call 4: job insert // GH-90000

        when(selectActiveStmt.executeQuery()).thenReturn(resultSet); // GH-90000
        when(resultSet.next()).thenReturn(true); // GH-90000
        when(resultSet.getString("version_id")).thenReturn("old-version-id");
        when(supersedeStmt.executeUpdate()).thenReturn(1); // GH-90000
        when(activateStmt.executeUpdate()).thenReturn(1); // GH-90000
        when(insertJobStmt.executeUpdate()).thenReturn(1); // GH-90000

        String jobId = runPromise(() -> // GH-90000
                service.rotateKey("yappc-main-key", "new-version-id", "admin")); // GH-90000

        assertThat(jobId).isNotBlank(); // GH-90000

        verify(connection).setAutoCommit(false); // GH-90000
        verify(supersedeStmt).setString(2, "old-version-id"); // GH-90000
        verify(insertJobStmt).setString(3, "old-version-id"); // GH-90000
        verify(insertJobStmt).setString(4, "new-version-id"); // GH-90000
        verify(connection).commit(); // GH-90000
    }

    @Test
    @DisplayName("rotateKey: rolls back transaction when no active key found")
    void rotateKey_noActiveKey_rollsBack() throws SQLException { // GH-90000
        when(connection.prepareStatement(contains("SELECT version_id"))).thenReturn(selectActiveStmt);
        when(selectActiveStmt.executeQuery()).thenReturn(resultSet); // GH-90000
        when(resultSet.next()).thenReturn(false);    // no active key // GH-90000

        assertThatThrownBy(() -> // GH-90000
                runPromise(() -> service.rotateKey("yappc-main-key", "new-id", "admin"))) // GH-90000
                .isInstanceOf(IllegalStateException.class) // GH-90000
                .hasMessageContaining("No active key found for alias");

        verify(connection).rollback(); // GH-90000
    }

    @Test
    @DisplayName("rotateKey: null keyAlias throws NullPointerException")
    void rotateKey_nullAlias_throws() { // GH-90000
        assertThatThrownBy(() -> // GH-90000
                runPromise(() -> service.rotateKey(null, "new-id", "admin"))) // GH-90000
                .isInstanceOf(NullPointerException.class) // GH-90000
                .hasMessageContaining("keyAlias");
    }

    // ── completeRotationJob ──────────────────────────────────────────────────

    @Test
    @DisplayName("completeRotationJob: marks job COMPLETE when zero failures")
    void completeRotationJob_zeroFailures_marksComplete() throws SQLException { // GH-90000
        when(connection.prepareStatement(anyString())).thenReturn(completeJobStmt); // GH-90000
        when(completeJobStmt.executeUpdate()).thenReturn(1); // GH-90000

        runPromise(() -> service.completeRotationJob("job-1", 500L, 0L, null)); // GH-90000

        verify(completeJobStmt).setString(1, "COMPLETE"); // GH-90000
        verify(completeJobStmt).setLong(3, 500L); // GH-90000
        verify(completeJobStmt).setLong(4, 0L); // GH-90000
        verify(completeJobStmt).setString(5, null); // GH-90000
        verify(completeJobStmt).setString(6, "job-1"); // GH-90000
    }

    @Test
    @DisplayName("completeRotationJob: marks job FAILED when failures > 0")
    void completeRotationJob_withFailures_marksFailed() throws SQLException { // GH-90000
        when(connection.prepareStatement(anyString())).thenReturn(completeJobStmt); // GH-90000
        when(completeJobStmt.executeUpdate()).thenReturn(1); // GH-90000

        runPromise(() -> service.completeRotationJob("job-2", 490L, 10L, "some error")); // GH-90000

        verify(completeJobStmt).setString(1, "FAILED"); // GH-90000
        verify(completeJobStmt).setLong(4, 10L); // GH-90000
        verify(completeJobStmt).setString(5, "some error"); // GH-90000
    }

    @Test
    @DisplayName("completeRotationJob: null jobId throws NullPointerException")
    void completeRotationJob_nullJobId_throws() { // GH-90000
        assertThatThrownBy(() -> // GH-90000
                runPromise(() -> service.completeRotationJob(null, 0L, 0L, null))) // GH-90000
                .isInstanceOf(NullPointerException.class) // GH-90000
                .hasMessageContaining("jobId");
    }

    // ── getActiveVersionId ───────────────────────────────────────────────────

    @Test
    @DisplayName("getActiveVersionId: returns version ID when ACTIVE key exists")
    void getActiveVersionId_found() throws SQLException { // GH-90000
        when(connection.prepareStatement(anyString())).thenReturn(selectActiveStmt); // GH-90000
        when(selectActiveStmt.executeQuery()).thenReturn(resultSet); // GH-90000
        when(resultSet.next()).thenReturn(true); // GH-90000
        when(resultSet.getString("version_id")).thenReturn("v-abc-123");

        Optional<String> result = runPromise(() -> // GH-90000
                service.getActiveVersionId("yappc-main-key"));

        assertThat(result).isPresent().hasValue("v-abc-123");
    }

    @Test
    @DisplayName("getActiveVersionId: returns empty when no ACTIVE key for alias")
    void getActiveVersionId_notFound() throws SQLException { // GH-90000
        when(connection.prepareStatement(anyString())).thenReturn(selectActiveStmt); // GH-90000
        when(selectActiveStmt.executeQuery()).thenReturn(resultSet); // GH-90000
        when(resultSet.next()).thenReturn(false); // GH-90000

        Optional<String> result = runPromise(() -> // GH-90000
                service.getActiveVersionId("yappc-main-key"));

        assertThat(result).isEmpty(); // GH-90000
    }

    // ── encryptionServiceForKey ──────────────────────────────────────────────

    @Test
    @DisplayName("encryptionServiceForKey: returns EncryptionService for given key bytes")
    void encryptionServiceForKey_returnsInstance() { // GH-90000
        EncryptionService enc = service.encryptionServiceForKey(TEST_KEY_BYTES); // GH-90000
        assertThat(enc).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("encryptionServiceForKey: null keyBytes throws NullPointerException")
    void encryptionServiceForKey_nullThrows() { // GH-90000
        assertThatThrownBy(() -> service.encryptionServiceForKey(null)) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }

    // ── decryptWithOldKey ────────────────────────────────────────────────────

    @Test
    @DisplayName("decryptWithOldKey: decrypts ciphertext produced by EncryptionService")
    void decryptWithOldKey_roundTrip() { // GH-90000
        EncryptionService enc = new EncryptionService(TEST_KEY_BYTES); // GH-90000
        String plaintext  = "my-secret-env-value";
        String ciphertext = enc.encrypt(plaintext); // GH-90000

        String decrypted = service.decryptWithOldKey(ciphertext, TEST_KEY_BYTES); // GH-90000

        assertThat(decrypted).isEqualTo(plaintext); // GH-90000
    }

    @Test
    @DisplayName("decryptWithOldKey: null ciphertext throws NullPointerException")
    void decryptWithOldKey_nullCiphertext_throws() { // GH-90000
        assertThatThrownBy(() -> service.decryptWithOldKey(null, TEST_KEY_BYTES)) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }

    // ── Constructor validation ────────────────────────────────────────────────

    @Test
    @DisplayName("null dataSource throws NullPointerException")
    void constructor_nullDataSource_throws() { // GH-90000
        assertThatThrownBy(() -> new KeyRotationService(null, eventloop())) // GH-90000
                .isInstanceOf(NullPointerException.class) // GH-90000
                .hasMessageContaining("dataSource");
    }

    @Test
    @DisplayName("null eventloop throws NullPointerException")
    void constructor_nullEventloop_throws() { // GH-90000
        assertThatThrownBy(() -> new KeyRotationService(dataSource, null)) // GH-90000
                .isInstanceOf(NullPointerException.class) // GH-90000
                .hasMessageContaining("eventloop");
    }
}
