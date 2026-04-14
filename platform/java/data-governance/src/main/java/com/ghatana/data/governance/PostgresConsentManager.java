/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.data.governance;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * PostgreSQL-backed {@link ConsentManager}.
 *
 * <p>Consent records are stored in the {@code consent_records} table
 * (created by Flyway migration {@code V020__create_retention_policies.sql}).
 * All JDBC calls are dispatched off the ActiveJ event-loop via
 * {@link Promise#ofBlocking} to keep the event loop non-blocking.
 *
 * @doc.type class
 * @doc.purpose Durable GDPR consent management backed by PostgreSQL
 * @doc.layer platform
 * @doc.pattern Repository
 */
public final class PostgresConsentManager implements ConsentManager {

    private static final Logger log = LoggerFactory.getLogger(PostgresConsentManager.class);

    private final DataSource dataSource;
    private final Executor executor;

    /**
     * @param dataSource pooled JDBC data source (HikariCP); never {@code null}
     * @param executor   blocking-I/O thread pool; never {@code null}
     */
    public PostgresConsentManager(DataSource dataSource, Executor executor) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.executor   = Objects.requireNonNull(executor,   "executor");
    }

    /**
     * Convenience constructor that creates a small dedicated blocking thread pool.
     */
    public PostgresConsentManager(DataSource dataSource) {
        this(dataSource, Executors.newFixedThreadPool(4,
            r -> { Thread t = new Thread(r, "consent-jdbc"); t.setDaemon(true); return t; }));
    }

    // ---- ConsentManager ----------------------------------------------------

    @Override
    public Promise<Void> recordConsent(String tenantId, String subjectId, String purpose) {
        Objects.requireNonNull(tenantId,  "tenantId");
        Objects.requireNonNull(subjectId, "subjectId");
        Objects.requireNonNull(purpose,   "purpose");

        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("""
                     INSERT INTO consent_records (tenant_id, subject_id, purpose, granted, granted_at, revoked_at)
                     VALUES (?, ?, ?, TRUE, NOW(), NULL)
                     ON CONFLICT (tenant_id, subject_id, purpose)
                     DO UPDATE SET granted = TRUE, granted_at = NOW(), revoked_at = NULL
                     """)) {
                ps.setString(1, tenantId);
                ps.setString(2, subjectId);
                ps.setString(3, purpose);
                ps.executeUpdate();
                log.debug("[consent] Recorded consent: tenant={} subject={} purpose={}", tenantId, subjectId, purpose);
            }
            return null;
        });
    }

    @Override
    public Promise<Void> withdrawConsent(String tenantId, String subjectId, String purpose) {
        Objects.requireNonNull(tenantId,  "tenantId");
        Objects.requireNonNull(subjectId, "subjectId");
        Objects.requireNonNull(purpose,   "purpose");

        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("""
                     UPDATE consent_records
                     SET granted = FALSE, revoked_at = NOW()
                     WHERE tenant_id = ? AND subject_id = ? AND purpose = ?
                     """)) {
                ps.setString(1, tenantId);
                ps.setString(2, subjectId);
                ps.setString(3, purpose);
                ps.executeUpdate();
                log.info("[consent] Withdrawn: tenant={} subject={} purpose={}", tenantId, subjectId, purpose);
            }
            return null;
        });
    }

    @Override
    public Promise<Boolean> hasConsent(String tenantId, String subjectId, String purpose) {
        Objects.requireNonNull(tenantId,  "tenantId");
        Objects.requireNonNull(subjectId, "subjectId");
        Objects.requireNonNull(purpose,   "purpose");

        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("""
                     SELECT granted FROM consent_records
                     WHERE tenant_id = ? AND subject_id = ? AND purpose = ?
                     """)) {
                ps.setString(1, tenantId);
                ps.setString(2, subjectId);
                ps.setString(3, purpose);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getBoolean("granted");
                    }
                    // No record → no consent
                    return false;
                }
            }
        });
    }

    @Override
    public Promise<Void> enforceConsent(String tenantId, String subjectId, String purpose) {
        return hasConsent(tenantId, subjectId, purpose).then(hasIt -> {
            if (!hasIt) {
                log.warn("[consent] Consent not found: tenant={} subject={} purpose={}",
                    tenantId, subjectId, purpose);
                return Promise.ofException(new ConsentRequiredException(tenantId, subjectId, purpose));
            }
            return Promise.complete();
        });
    }
}
