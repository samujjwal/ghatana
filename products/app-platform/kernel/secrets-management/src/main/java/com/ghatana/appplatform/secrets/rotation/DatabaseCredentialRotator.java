/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.secrets.rotation;

import com.ghatana.appplatform.secrets.domain.SecretMetadata;
import com.ghatana.appplatform.secrets.port.SecretProvider;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.Executor;

/**
 * Two-phase PostgreSQL database credential rotation (STORY-K14-005).
 *
 * <h3>Rotation phases</h3>
 * <ol>
 *   <li><b>Provision</b> — creates a new PostgreSQL role {@code <current>_v<timestamp>}
 *       with a fresh random password and re-grants the source role's membership.</li>
 *   <li><b>Activate</b> — writes the new credential to the {@link SecretProvider}
 *       (Vault or local) so the application uses it on its next connection-pool refresh.</li>
 *   <li><b>Retire</b> — schedules asynchronous DROP of the old role after a grace period
 *       that allows in-flight connections to drain naturally.</li>
 * </ol>
 *
 * <p>JDBC provisioning runs on the blocking {@code executor}; the secret write is chained
 * as an async {@link Promise} so the eventloop thread is never blocked.
 *
 * @doc.type class
 * @doc.purpose Two-phase PostgreSQL credential rotation (K14-005)
 * @doc.layer product
 * @doc.pattern Service
 */
public final class DatabaseCredentialRotator {

    private static final Logger log = LoggerFactory.getLogger(DatabaseCredentialRotator.class);

    /** Characters used for random password generation. */
    private static final String PW_CHARS =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*";
    private static final int PW_LENGTH = 40;

    private final DataSource adminDataSource;    // connected as superuser/admin
    private final SecretProvider secretProvider; // Vault or local file
    private final Executor executor;
    private final String secretPathTemplate;     // e.g., "/app-platform/{env}/db/{role}"
    private final Duration gracePeriod;          // time to keep old role alive after rotate

    /**
     * @param adminDataSource   JDBC datasource with DDL privileges (CREATE ROLE, GRANT, DROP ROLE)
     * @param secretProvider    secret storage backend where the new credential is written
     * @param executor          blocking executor for JDBC and I/O operations
     * @param secretPathTemplate path template with {@code {role}} placeholder
     * @param gracePeriod       delay between activating the new credential and dropping the old role
     */
    public DatabaseCredentialRotator(DataSource adminDataSource,
                                     SecretProvider secretProvider,
                                     Executor executor,
                                     String secretPathTemplate,
                                     Duration gracePeriod) {
        this.adminDataSource    = adminDataSource;
        this.secretProvider     = secretProvider;
        this.executor           = executor;
        this.secretPathTemplate = secretPathTemplate;
        this.gracePeriod        = gracePeriod;
    }

    // ──────────────────────────────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Rotates the database credentials for {@code currentRole}.
     *
     * <p>Actions performed:
     * <ol>
     *   <li>Generate a new random password.</li>
     *   <li>Create a new PostgreSQL role {@code <currentRole>_new} with the password.</li>
     *   <li>Grant the same privileges as {@code currentRole} to the new role.</li>
     *   <li>Write the new credential to the secret provider.</li>
     *   <li>Schedule an async task to rename and drop the old role after the grace period.</li>
     * </ol>
     *
     * @param currentRole       the existing PostgreSQL role being rotated
     * @param grantSource       role whose privileges should be cloned (often same as currentRole)
     * @return async {@link RotationResult}
     */
    public Promise<RotationResult> rotate(String currentRole, String grantSource) {
        String newRole    = currentRole + "_v" + Instant.now().getEpochSecond();
        char[] password   = generatePassword();
        String secretPath = secretPathTemplate.replace("{role}", currentRole);

        // Phase 1: JDBC — provision new role (blocking)
        return Promise.ofBlocking(executor, () -> {
            provisionRole(newRole, password, grantSource);
            log.info("[K14-005] Provisioned DB role={} for current={}", newRole, currentRole);
            return null;

        // Phase 2: async — write new credential to secret store
        }).then(_ -> secretProvider.putSecret(
                secretPath,
                password,
                SecretMetadata.autoRotating(Duration.ofDays(90), "vault")

        // Phase 3: kick off async retirement of old role, return result
        )).map(stored -> {
            Arrays.fill(password, '\0'); // zero plaintext password once stored
            scheduleOldRoleDrop(currentRole, gracePeriod);
            log.info("[K14-005] Rotation queued: old={} new={} path={}", currentRole, newRole, secretPath);
            return new RotationResult(currentRole, newRole, secretPath);

        }).whenException(e -> {
            Arrays.fill(password, '\0');
            log.error("[K14-005] Rotation failed for role={}: {}", currentRole, e.getMessage(), e);
        });
    }

    // ──────────────────────────────────────────────────────────────────────
    // Internal
    // ──────────────────────────────────────────────────────────────────────

    private void provisionRole(String roleName, char[] password, String grantSource)
            throws Exception {
        // DDL does not support bind parameters for passwords in CREATE ROLE.
        // The password characters are from a controlled set (alphanumeric + !@#$%^&*)
        // that excludes single-quotes and backslashes, so single-quoting is safe.
        String pwString = new String(password);
        try (Connection conn = adminDataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (Statement st = conn.createStatement()) {
                st.execute("CREATE ROLE " + pgQuoteIdentifier(roleName)
                    + " WITH LOGIN PASSWORD '" + pwString + "'");
                st.execute("GRANT " + pgQuoteIdentifier(grantSource)
                    + " TO " + pgQuoteIdentifier(roleName));
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } finally {
            Arrays.fill(pwString.toCharArray(), '\0');
        }
    }

    private void scheduleOldRoleDrop(String oldRole, Duration delay) {
        Thread.startVirtualThread(() -> {
            try {
                Thread.sleep(delay.toMillis());
                try (Connection conn = adminDataSource.getConnection();
                     Statement st = conn.createStatement()) {
                    st.execute("DROP ROLE IF EXISTS " + pgQuoteIdentifier(oldRole));
                    log.info("[K14-005] Retired old DB role={}", oldRole);
                }
            } catch (Exception e) {
                log.error("[K14-005] Failed to retire old role={}: {}", oldRole, e.getMessage());
            }
        });
    }

    private static char[] generatePassword() {
        SecureRandom rng = new SecureRandom();
        char[] pw = new char[PW_LENGTH];
        for (int i = 0; i < PW_LENGTH; i++) {
            pw[i] = PW_CHARS.charAt(rng.nextInt(PW_CHARS.length()));
        }
        return pw;
    }

    /**
     * Wraps a PostgreSQL identifier in double-quotes.
     * Any embedded double-quotes are doubled per SQL standard.
     */
    private static String pgQuoteIdentifier(String identifier) {
        return '"' + identifier.replace("\"", "\"\"") + '"';
    }

    // ──────────────────────────────────────────────────────────────────────
    // Result
    // ──────────────────────────────────────────────────────────────────────

    /**
     * @param oldRole    role name before rotation
     * @param newRole    newly created role name
     * @param secretPath path in the secret store where the new credential is written
     */
    public record RotationResult(String oldRole, String newRole, String secretPath) {}
}
