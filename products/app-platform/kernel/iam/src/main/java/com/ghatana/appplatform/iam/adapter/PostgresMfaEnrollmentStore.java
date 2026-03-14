/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.iam.adapter;

import com.ghatana.appplatform.iam.mfa.MfaEnrollmentStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * PostgreSQL-backed implementation of {@link MfaEnrollmentStore} (STORY-K01-004).
 *
 * <p>Stores TOTP secrets and hashed backup codes in {@code iam_mfa_enrollments}
 * (created by {@code V003__iam_mfa.sql}). All JDBC operations are wrapped in
 * {@code Promise.ofBlocking} by callers.
 *
 * <p>Backup code consumption uses a single UPDATE…RETURNING to ensure atomicity
 * without an extra round-trip.
 *
 * @doc.type class
 * @doc.purpose PostgreSQL adapter for MFA enrollment persistence (K01-004)
 * @doc.layer product
 * @doc.pattern Repository
 */
public final class PostgresMfaEnrollmentStore implements MfaEnrollmentStore {

    private static final Logger log = LoggerFactory.getLogger(PostgresMfaEnrollmentStore.class);

    // ──────────────────────────────────────────────────────────────────────
    // SQL
    // ──────────────────────────────────────────────────────────────────────

    private static final String UPSERT = """
            INSERT INTO iam_mfa_enrollments
                        (user_id, tenant_id, totp_secret_b32, backup_code_hashes)
            VALUES      (?, ?, ?, ?)
            ON CONFLICT (user_id, tenant_id) DO UPDATE
               SET totp_secret_b32   = EXCLUDED.totp_secret_b32,
                   backup_code_hashes = EXCLUDED.backup_code_hashes,
                   updated_at         = NOW()
            """;

    private static final String SELECT = """
            SELECT totp_secret_b32, backup_code_hashes
              FROM iam_mfa_enrollments
             WHERE user_id = ? AND tenant_id = ?
            """;

    /**
     * Removes a specific hash from the array and returns the new length only when
     * the hash was present (so RETURNING is non-null iff removal happened).
     * Uses {@code array_remove} which is set-like and returns the same array if
     * the element is absent — we distinguish by checking cardinality change.
     */
    private static final String CONSUME_CODE = """
            UPDATE iam_mfa_enrollments
               SET backup_code_hashes = array_remove(backup_code_hashes, ?::text),
                   updated_at          = NOW()
             WHERE user_id = ?
               AND tenant_id = ?
               AND ? = ANY(backup_code_hashes)
            """;

    private static final String DELETE = """
            DELETE FROM iam_mfa_enrollments
             WHERE user_id = ? AND tenant_id = ?
            """;

    // ──────────────────────────────────────────────────────────────────────
    // Fields
    // ──────────────────────────────────────────────────────────────────────

    private final DataSource dataSource;
    private final Executor executor;

    /**
     * @param dataSource JDBC datasource (connection pool)
     * @param executor   blocking executor for JDBC operations
     */
    public PostgresMfaEnrollmentStore(DataSource dataSource, Executor executor) {
        this.dataSource = dataSource;
        this.executor = executor;
    }

    // ──────────────────────────────────────────────────────────────────────
    // MfaEnrollmentStore implementation
    // ──────────────────────────────────────────────────────────────────────

    @Override
    public void save(String userId, String tenantId, String totpSecretB32, List<String> hashedBackups) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPSERT)) {
            Array backupArray = conn.createArrayOf("text", hashedBackups.toArray());
            ps.setString(1, userId);
            ps.setString(2, tenantId);
            ps.setString(3, totpSecretB32);
            ps.setArray(4, backupArray);
            ps.executeUpdate();
            log.debug("Saved MFA enrollment for user={} tenant={}", userId, tenantId);
        } catch (SQLException e) {
            throw new MfaStoreException("Failed to save MFA enrollment for user=" + userId, e);
        }
    }

    @Override
    public Optional<MfaEnrollment> find(String userId, String tenantId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT)) {
            ps.setString(1, userId);
            ps.setString(2, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                String secret = rs.getString("totp_secret_b32");
                Array arr = rs.getArray("backup_code_hashes");
                List<String> hashes = arr == null
                    ? new ArrayList<>()
                    : new ArrayList<>(List.of((String[]) arr.getArray()));
                return Optional.of(new MfaEnrollment(userId, tenantId, secret, hashes));
            }
        } catch (SQLException e) {
            throw new MfaStoreException("Failed to read MFA enrollment for user=" + userId, e);
        }
    }

    @Override
    public boolean consumeBackupCode(String userId, String tenantId, String hashedCode) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(CONSUME_CODE)) {
            ps.setString(1, hashedCode);
            ps.setString(2, userId);
            ps.setString(3, tenantId);
            ps.setString(4, hashedCode);
            int rows = ps.executeUpdate();
            // rows == 1 iff the code was present (WHERE clause matched)
            return rows == 1;
        } catch (SQLException e) {
            throw new MfaStoreException("Failed to consume backup code for user=" + userId, e);
        }
    }

    @Override
    public void delete(String userId, String tenantId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(DELETE)) {
            ps.setString(1, userId);
            ps.setString(2, tenantId);
            ps.executeUpdate();
            log.info("Deleted MFA enrollment for user={} tenant={}", userId, tenantId);
        } catch (SQLException e) {
            throw new MfaStoreException("Failed to delete MFA enrollment for user=" + userId, e);
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Exception
    // ──────────────────────────────────────────────────────────────────────

    /** Unchecked wrapper for JDBC failures in the MFA store. */
    public static final class MfaStoreException extends RuntimeException {
        public MfaStoreException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
