/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Services Platform — Key Rotation Service
 */
package com.ghatana.yappc.services.security;

import com.ghatana.yappc.infrastructure.security.EncryptionService;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages encryption-key lifecycle for YAPPC secret fields, enabling zero-downtime key rotation.
 *
 * <p>Key lifecycle:
 * <ol>
 *   <li>The initial key is registered as {@code ACTIVE} via {@link #registerKey}.</li>
 *   <li>When a new key is ready, call {@link #rotateKey} to atomically move the previous
 *       key to {@code SUPERSEDED} and make the new one {@code ACTIVE}.  A rotation job
 *       row is created in {@code key_rotation_jobs} for background re-encryption tracking.</li>
 *   <li>Consumers call {@link #currentEncryptionService} to get the {@code EncryptionService}
 *       backed by the active key, and {@link #decryptWithVersion} to decrypt older
 *       ciphertexts produced by a superseded key.</li>
 * </ol>
 *
 * <p>Schema: {@code V6_0_0__YAPPC_KEY_VERSIONS.sql}
 *
 * @doc.type class
 * @doc.purpose Zero-downtime encryption key rotation for YAPPC secret fields
 * @doc.layer product
 * @doc.pattern Service
 */
public final class KeyRotationService {

    private static final Logger log = LoggerFactory.getLogger(KeyRotationService.class);

    // SQL — key_versions table
    private static final String SQL_INSERT_VERSION =
            "INSERT INTO key_versions (version_id, key_alias, status, created_by) VALUES (?, ?, ?, ?)";

    private static final String SQL_SUPERSEDE_KEY =
            "UPDATE key_versions SET status='SUPERSEDED', superseded_at=? WHERE version_id=? AND status='ACTIVE'";

    private static final String SQL_ACTIVATE_KEY =
            "UPDATE key_versions SET status='ACTIVE' WHERE version_id=?";

    private static final String SQL_SELECT_ACTIVE =
            "SELECT version_id FROM key_versions WHERE key_alias=? AND status='ACTIVE' LIMIT 1";

    private static final String SQL_SELECT_VERSION =
            "SELECT status FROM key_versions WHERE version_id=?";

    // SQL — key_rotation_jobs table
    private static final String SQL_INSERT_JOB =
            "INSERT INTO key_rotation_jobs " +
            "  (job_id, key_alias, old_version_id, new_version_id, status, created_at) " +
            "VALUES (?, ?, ?, ?, 'PENDING', ?)";

    private static final String SQL_COMPLETE_JOB =
            "UPDATE key_rotation_jobs SET status=?, completed_at=?, processed=?, failed_records=?, error_detail=? " +
            "WHERE job_id=?";

    private final DataSource dataSource;
    private final Eventloop  eventloop;

    /**
     * @param dataSource JDBC datasource for the YAPPC platform schema
     * @param eventloop  ActiveJ event loop for scheduling blocking JDBC calls off the loop
     */
    public KeyRotationService(@NotNull DataSource dataSource, @NotNull Eventloop eventloop) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.eventloop  = Objects.requireNonNull(eventloop,  "eventloop must not be null");
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Registers a new key version with {@code ACTIVE} status.
     *
     * @param keyAlias    logical name for the key (e.g. {@code "yappc-main-key"})
     * @param createdBy   identity registering the key (user ID or system service name)
     * @return promise of the generated {@code version_id} UUID string
     */
    public Promise<String> registerKey(@NotNull String keyAlias, @NotNull String createdBy) {
        Objects.requireNonNull(keyAlias,   "keyAlias must not be null");
        Objects.requireNonNull(createdBy,  "createdBy must not be null");

        String versionId = UUID.randomUUID().toString();
        return Promise.ofBlocking(eventloop, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SQL_INSERT_VERSION)) {
                ps.setString(1, versionId);
                ps.setString(2, keyAlias);
                ps.setString(3, "ACTIVE");
                ps.setString(4, createdBy);
                ps.executeUpdate();
                log.info("Registered key version {} for alias '{}'", versionId, keyAlias);
                return versionId;
            }
        });
    }

    /**
     * Performs a key rotation: supersedes the current active key and activates the new one.
     *
     * <p>A {@code key_rotation_jobs} row is created with status {@code PENDING}. The
     * actual re-encryption of existing data is performed separately (background job).
     *
     * @param keyAlias       logical key alias to rotate
     * @param newVersionId   the {@code version_id} of the newly registered key to make active
     * @param rotatedBy      identity performing the rotation
     * @return promise of the created rotation job ID
     * @throws IllegalStateException if no currently active key is found for the alias
     */
    public Promise<String> rotateKey(
            @NotNull String keyAlias,
            @NotNull String newVersionId,
            @NotNull String rotatedBy) {

        Objects.requireNonNull(keyAlias,     "keyAlias must not be null");
        Objects.requireNonNull(newVersionId, "newVersionId must not be null");
        Objects.requireNonNull(rotatedBy,    "rotatedBy must not be null");

        return Promise.ofBlocking(eventloop, () -> {
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    // 1. Find current active version
                    String oldVersionId = findActiveVersion(conn, keyAlias)
                            .orElseThrow(() -> new IllegalStateException(
                                    "No active key found for alias: " + keyAlias));

                    Instant now = Instant.now();

                    // 2. Supersede old key
                    try (PreparedStatement ps = conn.prepareStatement(SQL_SUPERSEDE_KEY)) {
                        ps.setTimestamp(1, Timestamp.from(now));
                        ps.setString(2, oldVersionId);
                        int rows = ps.executeUpdate();
                        if (rows == 0) {
                            throw new IllegalStateException(
                                    "Failed to supersede key version: " + oldVersionId);
                        }
                    }

                    // 3. Activate new key
                    try (PreparedStatement ps = conn.prepareStatement(SQL_ACTIVATE_KEY)) {
                        ps.setString(1, newVersionId);
                        ps.executeUpdate();
                    }

                    // 4. Create rotation job
                    String jobId = UUID.randomUUID().toString();
                    try (PreparedStatement ps = conn.prepareStatement(SQL_INSERT_JOB)) {
                        ps.setString(1, jobId);
                        ps.setString(2, keyAlias);
                        ps.setString(3, oldVersionId);
                        ps.setString(4, newVersionId);
                        ps.setTimestamp(5, Timestamp.from(now));
                        ps.executeUpdate();
                    }

                    conn.commit();
                    log.info("Key rotated for alias '{}': {} → {}; rotationJob={}",
                            keyAlias, oldVersionId, newVersionId, jobId);
                    return jobId;
                } catch (Exception ex) {
                    conn.rollback();
                    throw ex;
                } finally {
                    conn.setAutoCommit(true);
                }
            }
        });
    }

    /**
     * Marks a rotation job as completed (or failed) with final counts.
     *
     * @param jobId          the job ID returned by {@link #rotateKey}
     * @param processed      number of records successfully re-encrypted
     * @param failedRecords  number of records that could not be re-encrypted
     * @param errorDetail    optional error summary; {@code null} if job completed cleanly
     * @return promise of completion
     */
    public Promise<Void> completeRotationJob(
            @NotNull String jobId,
            long processed,
            long failedRecords,
            @Nullable String errorDetail) {

        Objects.requireNonNull(jobId, "jobId must not be null");
        String finalStatus = failedRecords == 0 ? "COMPLETE" : "FAILED";

        return Promise.ofBlocking(eventloop, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SQL_COMPLETE_JOB)) {
                ps.setString(1, finalStatus);
                ps.setTimestamp(2, Timestamp.from(Instant.now()));
                ps.setLong(3, processed);
                ps.setLong(4, failedRecords);
                ps.setString(5, errorDetail);
                ps.setString(6, jobId);
                ps.executeUpdate();
                log.info("Rotation job {} marked {}: processed={} failed={}",
                        jobId, finalStatus, processed, failedRecords);
                return null;
            }
        });
    }

    /**
     * Returns the version ID of the currently {@code ACTIVE} key for the given alias.
     *
     * @param keyAlias logical key alias
     * @return promise of the version ID, or an empty optional if none exists
     */
    public Promise<Optional<String>> getActiveVersionId(@NotNull String keyAlias) {
        Objects.requireNonNull(keyAlias, "keyAlias must not be null");
        return Promise.ofBlocking(eventloop, () -> {
            try (Connection conn = dataSource.getConnection()) {
                return findActiveVersion(conn, keyAlias);
            }
        });
    }

    /**
     * Returns an {@link EncryptionService} backed by the given raw AES key bytes.
     *
     * <p>Callers retrieve the key material from a secret store (e.g. Vault, KMS) by
     * {@code version_id}, then construct the {@code EncryptionService} via this helper.
     *
     * @param keyBytes raw 32-byte (256-bit) AES key material for the requested version
     * @return EncryptionService scoped to that key
     */
    public EncryptionService encryptionServiceForKey(@NotNull byte[] keyBytes) {
        Objects.requireNonNull(keyBytes, "keyBytes must not be null");
        return new EncryptionService(keyBytes);
    }

    /**
     * Decrypts a ciphertext using the AES key bytes supplied by the caller.
     *
     * <p>This method is intended for re-encryption flows: the caller fetches the old
     * key material by {@code version_id} from a secret store, passes it here to obtain
     * the plaintext, then re-encrypts with the current active key.
     *
     * @param ciphertext ciphertext produced by the superseded key
     * @param oldKeyBytes raw 32-byte AES key material for the superseded version
     * @return decrypted plaintext
     */
    public String decryptWithOldKey(@NotNull String ciphertext, @NotNull byte[] oldKeyBytes) {
        Objects.requireNonNull(ciphertext, "ciphertext must not be null");
        Objects.requireNonNull(oldKeyBytes, "oldKeyBytes must not be null");
        return new EncryptionService(oldKeyBytes).decrypt(ciphertext);
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private Optional<String> findActiveVersion(Connection conn, String keyAlias)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SQL_SELECT_ACTIVE)) {
            ps.setString(1, keyAlias);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getString("version_id"));
                }
                return Optional.empty();
            }
        }
    }
}
