package com.ghatana.appplatform.regulator;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.*;

import java.security.SecureRandom;
import java.sql.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Authentication for the Regulator Portal.
 *              All regulator accounts carry READ_ONLY scope — no write operations allowed.
 *              MFA is mandatory (TOTP-based); session limited to 4 hours.
 *              Every authentication event and page access is fully audited.
 *              Accounts are per-tenant assignments; a regulator can be granted access
 *              to one or more specific tenants only.
 * @doc.layer   Regulator Portal (R-01)
 * @doc.pattern Port-Adapter; HikariCP + JDBC; Promise.ofBlocking; Micrometer
 *
 * STORY-R01-001: Regulator portal authentication
 *
 * DDL:
 * <pre>
 * CREATE TABLE IF NOT EXISTS regulator_users (
 *   user_id        TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   username       TEXT NOT NULL UNIQUE,
 *   email          TEXT NOT NULL UNIQUE,
 *   totp_secret    TEXT NOT NULL,           -- encrypted TOTP seed
 *   status         TEXT NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE | SUSPENDED | REVOKED
 *   created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 *   last_login_at  TIMESTAMPTZ
 * );
 * CREATE TABLE IF NOT EXISTS regulator_sessions (
 *   session_id     TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   user_id        TEXT NOT NULL REFERENCES regulator_users(user_id),
 *   scope          TEXT NOT NULL DEFAULT 'READ_ONLY',
 *   created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 *   expires_at     TIMESTAMPTZ NOT NULL,
 *   revoked_at     TIMESTAMPTZ,
 *   ip_address     TEXT
 * );
 * CREATE TABLE IF NOT EXISTS regulator_access_audit (
 *   audit_id       TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   user_id        TEXT NOT NULL,
 *   action         TEXT NOT NULL,
 *   detail         TEXT,
 *   ip_address     TEXT,
 *   recorded_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
 * );
 * </pre>
 */
public class RegulatorAuthService {

    // ── Inner port interfaces ─────────────────────────────────────────────────

    /** TOTP validation adapter. */
    public interface TotpValidatorPort {
        boolean validate(String totpSecret, String otp) throws Exception;
        String generateSecret() throws Exception;
    }

    /** Encrypt/decrypt TOTP secret at rest. */
    public interface EncryptionPort {
        String encrypt(String plaintext) throws Exception;
        String decrypt(String ciphertext) throws Exception;
    }

    // ── Value types ───────────────────────────────────────────────────────────

    private static final int SESSION_HOURS = 4;

    public record RegulatorUser(
        String userId, String username, String email, String status
    ) {}

    public record SessionToken(
        String sessionId, String userId, Instant expiresAt, String scope
    ) {}

    // ── Fields ────────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final TotpValidatorPort totp;
    private final EncryptionPort encryption;
    private final Executor executor;
    private final Counter loginSuccessCounter;
    private final Counter loginFailCounter;
    private final Counter sessionRevokeCounter;

    public RegulatorAuthService(
        javax.sql.DataSource ds,
        TotpValidatorPort totp,
        EncryptionPort encryption,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds                  = ds;
        this.totp                = totp;
        this.encryption          = encryption;
        this.executor            = executor;
        this.loginSuccessCounter = Counter.builder("regulator.auth.login_success").register(registry);
        this.loginFailCounter    = Counter.builder("regulator.auth.login_fail").register(registry);
        this.sessionRevokeCounter = Counter.builder("regulator.auth.session_revoked").register(registry);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Authenticate with username + TOTP OTP. Returns a 4-hour READ_ONLY session token.
     */
    public Promise<SessionToken> authenticate(String username, String totpOtp, String ipAddress) {
        return Promise.ofBlocking(executor, () -> {
            RegulatorUser user = loadUser(username);
            if (user == null || !"ACTIVE".equals(user.status())) {
                loginFailCounter.increment();
                recordAudit(username, "AUTH_FAIL", "user not found or inactive", ipAddress);
                throw new SecurityException("Authentication failed");
            }

            String encryptedSecret = loadTotpSecret(user.userId());
            String secret = encryption.decrypt(encryptedSecret);
            if (!totp.validate(secret, totpOtp)) {
                loginFailCounter.increment();
                recordAudit(user.userId(), "AUTH_FAIL", "invalid TOTP", ipAddress);
                throw new SecurityException("Authentication failed");
            }

            Instant expiresAt = Instant.now().plus(SESSION_HOURS, ChronoUnit.HOURS);
            String sessionId  = createSession(user.userId(), expiresAt, ipAddress);

            touchLastLogin(user.userId());
            loginSuccessCounter.increment();
            recordAudit(user.userId(), "AUTH_SUCCESS", "session=" + sessionId, ipAddress);

            return new SessionToken(sessionId, user.userId(), expiresAt, "READ_ONLY");
        });
    }

    /**
     * Validate an existing session token. Returns the session if VALID.
     */
    public Promise<Optional<SessionToken>> validateSession(String sessionId) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT user_id, scope, expires_at FROM regulator_sessions " +
                     "WHERE session_id=? AND revoked_at IS NULL AND expires_at > NOW()"
                 )) {
                ps.setString(1, sessionId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return Optional.empty();
                    return Optional.of(new SessionToken(
                        sessionId, rs.getString("user_id"),
                        rs.getTimestamp("expires_at").toInstant(), rs.getString("scope")));
                }
            }
        });
    }

    /**
     * Revoke a session (logout).
     */
    public Promise<Void> revokeSession(String sessionId, String userId) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE regulator_sessions SET revoked_at=NOW() WHERE session_id=? AND user_id=?"
                 )) {
                ps.setString(1, sessionId); ps.setString(2, userId); ps.executeUpdate();
            }
            sessionRevokeCounter.increment();
            recordAudit(userId, "SESSION_REVOKED", "session=" + sessionId, null);
            return null;
        });
    }

    /**
     * Operator creates a regulator user account with a TOTP secret.
     */
    public Promise<RegulatorUser> createUser(String username, String email, String operatorId) {
        return Promise.ofBlocking(executor, () -> {
            String totpSecret    = totp.generateSecret();
            String encryptedSec  = encryption.encrypt(totpSecret);
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO regulator_users (username, email, totp_secret) VALUES (?,?,?) RETURNING user_id"
                 )) {
                ps.setString(1, username); ps.setString(2, email); ps.setString(3, encryptedSec);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    String userId = rs.getString("user_id");
                    recordAudit(operatorId, "REGULATOR_USER_CREATED", "username=" + username, null);
                    return new RegulatorUser(userId, username, email, "ACTIVE");
                }
            }
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private RegulatorUser loadUser(String username) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT user_id, username, email, status FROM regulator_users WHERE username=?"
             )) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new RegulatorUser(rs.getString("user_id"), rs.getString("username"),
                    rs.getString("email"), rs.getString("status"));
            }
        }
    }

    private String loadTotpSecret(String userId) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT totp_secret FROM regulator_users WHERE user_id=?"
             )) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("User not found: " + userId);
                return rs.getString("totp_secret");
            }
        }
    }

    private String createSession(String userId, Instant expiresAt, String ipAddress) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO regulator_sessions (user_id, expires_at, ip_address) VALUES (?,?,?) RETURNING session_id"
             )) {
            ps.setString(1, userId); ps.setTimestamp(2, Timestamp.from(expiresAt));
            ps.setString(3, ipAddress);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getString("session_id"); }
        }
    }

    private void touchLastLogin(String userId) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE regulator_users SET last_login_at=NOW() WHERE user_id=?"
             )) {
            ps.setString(1, userId); ps.executeUpdate();
        }
    }

    private void recordAudit(String userId, String action, String detail, String ip) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO regulator_access_audit (user_id, action, detail, ip_address) VALUES (?,?,?,?)"
             )) {
            ps.setString(1, userId); ps.setString(2, action);
            ps.setString(3, detail); ps.setString(4, ip);
            ps.executeUpdate();
        }
    }
}
