/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Services Platform — Secret Access Logger
 */
package com.ghatana.yappc.services.security;

import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Append-only audit logger for every encryption and decryption operation on secret fields.
 *
 * <p>Every read ({@code DECRYPT}) or write ({@code ENCRYPT}) of an encrypted field is
 * recorded with the acting principal, tenant scope, field name, outcome, and timestamp.
 * This satisfies compliance requirements for secret-access traceability and enables
 * efficient forensic investigation via the {@code secret_access_audit} table.
 *
 * <p>Persistence is fire-and-forget ({@link Promise#ofBlocking} keeps the ActiveJ event
 * loop unblocked). A write failure is logged at WARN level but never rethrown so that
 * audit failures do not interrupt the primary operation.
 *
 * <p>Schema: {@code V5_0_0__YAPPC_SECRET_ACCESS_AUDIT.sql}
 *
 * @doc.type class
 * @doc.purpose Append-only audit log for secret field encrypt/decrypt operations
 * @doc.layer product
 * @doc.pattern Service
 */
public final class SecretAccessLogger {

    private static final Logger log = LoggerFactory.getLogger(SecretAccessLogger.class);

    private static final String INSERT_SQL =
            "INSERT INTO secret_access_audit " +
            "  (id, tenant_id, principal, field_name, action, outcome, detail, occurred_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    /** Direction of secret-field access. */
    public enum Action { ENCRYPT, DECRYPT }

    /** Result of the access attempt. */
    public enum Outcome { SUCCESS, FAILURE }

    private final DataSource dataSource;
    private final Eventloop eventloop;

    /**
     * @param dataSource JDBC datasource for the YAPPC platform schema
     * @param eventloop  ActiveJ event loop used to schedule blocking JDBC writes off the loop
     */
    public SecretAccessLogger(@NotNull DataSource dataSource, @NotNull Eventloop eventloop) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.eventloop  = Objects.requireNonNull(eventloop,  "eventloop must not be null");
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Records a successful encryption event.
     *
     * @param principal  userId or agentId performing the operation
     * @param tenantId   tenant scope
     * @param fieldName  dotted field path, e.g. {@code "project.environmentVariables"}
     */
    public void recordEncrypt(
            @NotNull String principal,
            @NotNull String tenantId,
            @NotNull String fieldName) {
        persistAsync(principal, tenantId, fieldName, Action.ENCRYPT, Outcome.SUCCESS, null);
    }

    /**
     * Records a failed encryption event.
     *
     * @param principal  userId or agentId performing the operation
     * @param tenantId   tenant scope
     * @param fieldName  dotted field path
     * @param detail     error detail for forensic investigation
     */
    public void recordEncryptFailure(
            @NotNull String principal,
            @NotNull String tenantId,
            @NotNull String fieldName,
            @Nullable String detail) {
        persistAsync(principal, tenantId, fieldName, Action.ENCRYPT, Outcome.FAILURE, detail);
    }

    /**
     * Records a successful decryption event.
     *
     * @param principal  userId or agentId performing the operation
     * @param tenantId   tenant scope
     * @param fieldName  dotted field path, e.g. {@code "project.environmentVariables"}
     */
    public void recordDecrypt(
            @NotNull String principal,
            @NotNull String tenantId,
            @NotNull String fieldName) {
        persistAsync(principal, tenantId, fieldName, Action.DECRYPT, Outcome.SUCCESS, null);
    }

    /**
     * Records a failed decryption event.
     *
     * @param principal  userId or agentId performing the operation
     * @param tenantId   tenant scope
     * @param fieldName  dotted field path
     * @param detail     error detail for forensic investigation
     */
    public void recordDecryptFailure(
            @NotNull String principal,
            @NotNull String tenantId,
            @NotNull String fieldName,
            @Nullable String detail) {
        persistAsync(principal, tenantId, fieldName, Action.DECRYPT, Outcome.FAILURE, detail);
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    /** Fire-and-forget wrapper used by all public log methods. */
    private void persistAsync(
            String principal, String tenantId, String fieldName,
            Action action, Outcome outcome, @Nullable String detail) {
        persistPromise(principal, tenantId, fieldName, action, outcome, detail)
                .whenException(ex ->
                        log.warn("Failed to write secret access audit record: principal={} field={} action={} — {}",
                                principal, fieldName, action, ex.getMessage())
                );
    }

    /**
     * Package-private for testing: returns a {@link Promise} that callers can await.
     * Production code calls {@link #persistAsync} instead (fire-and-forget).
     */
    Promise<Void> persistPromise(
            String principal,
            String tenantId,
            String fieldName,
            Action action,
            Outcome outcome,
            @Nullable String detail) {

        String id = UUID.randomUUID().toString();
        Instant now = Instant.now();

        return Promise.ofBlocking(eventloop, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(INSERT_SQL)) {

                stmt.setString(1, id);
                stmt.setString(2, tenantId);
                stmt.setString(3, principal);
                stmt.setString(4, fieldName);
                stmt.setString(5, action.name());
                stmt.setString(6, outcome.name());
                stmt.setString(7, detail);
                stmt.setTimestamp(8, Timestamp.from(now));
                stmt.executeUpdate();

                log.debug("Secret access audit recorded: id={} principal={} field={} action={} outcome={}",
                        id, principal, fieldName, action, outcome);
                return null;
            }
        });
    }
}
