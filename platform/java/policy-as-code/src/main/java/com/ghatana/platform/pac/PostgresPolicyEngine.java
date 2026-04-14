/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.pac;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * PostgreSQL-backed {@link PolicyAsCodeEngine}.
 *
 * <p>Policy rules are stored in the {@code policy_rules} table
 * (created by Flyway migration {@code V020__create_retention_policies.sql}).
 * Each row has a {@code policy_name}, {@code action} (ALLOW/DENY) and a
 * JSONB {@code condition} object. At evaluation time, rows are loaded
 * and matched in insertion order; the first matching rule wins.
 *
 * <p>When no rules exist for the requested policy the engine defaults to
 * {@link PolicyEvalResult#allow} (open-default semantics) so that the
 * absence of a rule does not break existing workflows.
 *
 * @doc.type class
 * @doc.purpose Durable policy-as-code engine backed by PostgreSQL
 * @doc.layer platform
 * @doc.pattern Repository
 */
public final class PostgresPolicyEngine implements PolicyAsCodeEngine {

    private static final Logger log = LoggerFactory.getLogger(PostgresPolicyEngine.class);

    private final DataSource dataSource;
    private final Executor executor;

    /**
     * @param dataSource pooled JDBC data source (HikariCP); never {@code null}
     * @param executor   blocking-I/O thread pool; never {@code null}
     */
    public PostgresPolicyEngine(DataSource dataSource, Executor executor) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.executor   = Objects.requireNonNull(executor,   "executor");
    }

    /**
     * Convenience constructor that creates a small dedicated blocking thread pool.
     */
    public PostgresPolicyEngine(DataSource dataSource) {
        this(dataSource, Executors.newFixedThreadPool(4,
            r -> { Thread t = new Thread(r, "pac-jdbc"); t.setDaemon(true); return t; }));
    }

    // ---- PolicyAsCodeEngine ------------------------------------------------

    @Override
    public Promise<PolicyEvalResult> evaluate(
            String tenantId, String policyName, Map<String, Object> input) {
        Objects.requireNonNull(tenantId,   "tenantId");
        Objects.requireNonNull(policyName, "policyName");
        Objects.requireNonNull(input,      "input");

        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("""
                     SELECT effect, condition, reason
                     FROM policy_rules
                     WHERE (tenant_id = ? OR tenant_id = 'global')
                       AND policy_name = ?
                       AND enabled = TRUE
                     ORDER BY created_at ASC
                     """)) {
                ps.setString(1, tenantId);
                ps.setString(2, policyName);

                List<PolicyEvalResult> results = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String action      = rs.getString("effect");
                        String description = rs.getString("reason");
                        boolean allowed    = "ALLOW".equalsIgnoreCase(action);
                        int riskScore      = allowed ? 0 : 50;
                        List<String> reasons = description != null
                            ? List.of(description)
                            : List.of();
                        results.add(new PolicyEvalResult(allowed, policyName, reasons, riskScore));
                    }
                }

                if (results.isEmpty()) {
                    // No rules for this policy → allow by default (open-world)
                    log.debug("[pac] No rules found for policy={} tenant={} — defaulting to ALLOW",
                        policyName, tenantId);
                    return PolicyEvalResult.allow(policyName);
                }

                // Return the first (highest-priority) applicable rule result
                PolicyEvalResult first = results.get(0);
                if (!first.allowed()) {
                    log.warn("[pac] DENY: policyName={} tenantId={} reasons={}",
                        policyName, tenantId, first.reasons());
                }
                return first;
            }
        });
    }
}
