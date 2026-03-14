/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.iam.audit;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Read API for IAM audit events stored in the K-07 {@code audit_logs} table (K01-018).
 *
 * <p>Queries are scoped to IAM-domain actions (prefix {@code iam.}) and support filters:
 * <ul>
 *   <li>user / principal ID</li>
 *   <li>action — e.g., {@code "iam.login"}, {@code "iam.role.change"}</li>
 *   <li>inclusive date range ({@code from} / {@code to})</li>
 *   <li>outcome — {@code "SUCCESS"}, {@code "FAILURE"}, or {@code null} for all</li>
 * </ul>
 *
 * <p>Results are paginated (default page size 50, max 200).
 *
 * @doc.type class
 * @doc.purpose Paginated query endpoint for IAM audit trail (K01-018, K-07)
 * @doc.layer product
 * @doc.pattern Service
 */
public final class IamAuditQueryService {

    private static final Logger log = LoggerFactory.getLogger(IamAuditQueryService.class);

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 200;

    private final DataSource dataSource;
    private final Executor executor;

    public IamAuditQueryService(DataSource dataSource, Executor executor) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.executor   = Objects.requireNonNull(executor, "executor");
    }

    // ─── Query API ─────────────────────────────────────────────────────────────

    /**
     * Search IAM audit events with optional filters.
     *
     * @param filter    query parameters (may include userId, action, dateRange, outcome)
     * @param pageToken opaque page token for continuation (null = first page)
     * @param pageSize  desired page size (capped at {@value MAX_PAGE_SIZE})
     * @return paginated result page
     */
    public Promise<AuditPage> query(AuditFilter filter, Long pageToken, int pageSize) {
        int limit = Math.min(Math.max(1, pageSize), MAX_PAGE_SIZE);
        long offset = pageToken == null ? 0L : pageToken;

        return Promise.ofBlocking(executor, () -> {
            List<AuditEvent> events = new ArrayList<>();
            StringBuilder sql = buildSql(filter);
            sql.append(" ORDER BY sequence_number DESC LIMIT ? OFFSET ?");

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = buildStatement(conn, sql.toString(), filter, limit, offset);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    events.add(mapRow(rs));
                }
            }

            Long nextToken = events.size() == limit ? offset + limit : null;
            return new AuditPage(events, nextToken);
        });
    }

    // ─── SQL construction ──────────────────────────────────────────────────────

    private StringBuilder buildSql(AuditFilter f) {
        StringBuilder sb = new StringBuilder(
            "SELECT audit_id, action, actor::text, outcome, tenant_id, " +
            "       timestamp_gregorian, sequence_number " +
            "FROM audit_logs WHERE action LIKE 'iam.%' AND tenant_id = ?");
        if (f.userId() != null) sb.append(" AND actor->>'id' = ?");
        if (f.action()  != null) sb.append(" AND action = ?");
        if (f.from()    != null) sb.append(" AND timestamp_gregorian >= ?");
        if (f.to()      != null) sb.append(" AND timestamp_gregorian <= ?");
        if (f.outcome() != null) sb.append(" AND outcome = ?");
        return sb;
    }

    private PreparedStatement buildStatement(Connection conn, String sql,
                                              AuditFilter f, int limit, long offset)
            throws Exception {
        var ps = conn.prepareStatement(sql);
        int idx = 1;
        ps.setString(idx++, f.tenantId());
        if (f.userId() != null) ps.setString(idx++, f.userId());
        if (f.action()  != null) ps.setString(idx++, f.action());
        if (f.from()    != null) ps.setTimestamp(idx++, Timestamp.from(f.from()));
        if (f.to()      != null) ps.setTimestamp(idx++, Timestamp.from(f.to()));
        if (f.outcome() != null) ps.setString(idx++, f.outcome());
        ps.setInt(idx++, limit);
        ps.setLong(idx, offset);
        return ps;
    }

    private AuditEvent mapRow(ResultSet rs) throws Exception {
        return new AuditEvent(
            rs.getString("audit_id"),
            rs.getString("action"),
            rs.getString("actor"),
            rs.getString("outcome"),
            rs.getString("tenant_id"),
            rs.getTimestamp("timestamp_gregorian").toInstant(),
            rs.getLong("sequence_number")
        );
    }

    // ─── Domain types ──────────────────────────────────────────────────────────

    /**
     * Query filters for IAM audit events.
     *
     * @param tenantId mandatory tenant scope
     * @param userId   optional: filter by principal ID (actor.id JSONB field)
     * @param action   optional: exact action match, e.g., {@code "iam.login"}
     * @param from     optional: inclusive start timestamp
     * @param to       optional: inclusive end timestamp
     * @param outcome  optional: {@code "SUCCESS"} or {@code "FAILURE"}
     */
    public record AuditFilter(
            String tenantId,
            String userId,
            String action,
            Instant from,
            Instant to,
            String outcome
    ) {
        public AuditFilter {
            Objects.requireNonNull(tenantId, "tenantId");
        }
    }

    /** A single audit log row returned from the query. */
    public record AuditEvent(
            String auditId,
            String action,
            String actorJson,
            String outcome,
            String tenantId,
            Instant timestamp,
            long sequenceNumber
    ) {}

    /** A page of audit events with optional continuation token. */
    public record AuditPage(
            List<AuditEvent> events,
            Long nextPageToken  // null = no more pages
    ) {}
}
