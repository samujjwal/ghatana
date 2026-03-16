package com.ghatana.appplatform.operator;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose CRUD registry of regulatory jurisdictions and their mapping to tenants.
 *              Jurisdictions carry: code, name, regulator, calendar_id (K-15 link),
 *              default settlement cycle (T+N days), currency, language, and
 *              supported document types (e.g. TRADE_CONFIRM, SETTLEMENT_NOTICE).
 *              Tenants are assigned to one or more jurisdictions by the operator.
 *              Deletes are soft (status → INACTIVE) to preserve historical tenant mappings.
 * @doc.layer   Operator Workflows (O-01)
 * @doc.pattern Port-Adapter; HikariCP + JDBC; Promise.ofBlocking; Micrometer
 *
 * STORY-O01-009: Jurisdiction registry
 *
 * DDL:
 * <pre>
 * CREATE TABLE IF NOT EXISTS operator_jurisdictions (
 *   jurisdiction_id     TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   code                TEXT NOT NULL UNIQUE,   -- e.g. NP, IN, US
 *   name                TEXT NOT NULL,
 *   regulator           TEXT NOT NULL,
 *   calendar_id         TEXT,                   -- K-15 calendar reference
 *   settlement_cycle_t  INT NOT NULL DEFAULT 2, -- T+N days
 *   currency            TEXT NOT NULL,
 *   language            TEXT NOT NULL DEFAULT 'en',
 *   supported_doc_types JSONB NOT NULL DEFAULT '[]',
 *   status              TEXT NOT NULL DEFAULT 'ACTIVE', -- ACTIVE | INACTIVE
 *   created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 *   updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
 * );
 * CREATE TABLE IF NOT EXISTS tenant_jurisdictions (
 *   tenant_id         TEXT NOT NULL,
 *   jurisdiction_id   TEXT NOT NULL REFERENCES operator_jurisdictions(jurisdiction_id),
 *   assigned_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 *   PRIMARY KEY(tenant_id, jurisdiction_id)
 * );
 * </pre>
 */
public class JurisdictionRegistryService {

    // ── Inner port interfaces ─────────────────────────────────────────────────

    public interface AuditPort {
        void record(String actorId, String action, String detail) throws Exception;
    }

    // ── Value types ───────────────────────────────────────────────────────────

    public record Jurisdiction(
        String jurisdictionId, String code, String name, String regulator,
        String calendarId, int settlementCycleT, String currency,
        String language, List<String> supportedDocTypes, String status
    ) {}

    public record TenantJurisdictionAssignment(String tenantId, String jurisdictionId) {}

    // ── Fields ────────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final AuditPort audit;
    private final Executor executor;
    private final Counter createCounter;
    private final Counter assignCounter;

    public JurisdictionRegistryService(
        javax.sql.DataSource ds,
        AuditPort audit,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds            = ds;
        this.audit         = audit;
        this.executor      = executor;
        this.createCounter = Counter.builder("operator.jurisdiction.created").register(registry);
        this.assignCounter = Counter.builder("operator.jurisdiction.tenant_assigned").register(registry);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Create a new jurisdiction. Code must be unique. */
    public Promise<Jurisdiction> createJurisdiction(Jurisdiction j, String operatorId) {
        return Promise.ofBlocking(executor, () -> {
            String docJson = toJsonArray(j.supportedDocTypes());
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO operator_jurisdictions " +
                     "(code, name, regulator, calendar_id, settlement_cycle_t, currency, language, supported_doc_types) " +
                     "VALUES (?,?,?,?,?,?,?,?::jsonb) RETURNING jurisdiction_id"
                 )) {
                ps.setString(1, j.code()); ps.setString(2, j.name()); ps.setString(3, j.regulator());
                ps.setString(4, j.calendarId()); ps.setInt(5, j.settlementCycleT());
                ps.setString(6, j.currency()); ps.setString(7, j.language());
                ps.setString(8, docJson);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    String id = rs.getString("jurisdiction_id");
                    createCounter.increment();
                    audit.record(operatorId, "JURISDICTION_CREATED", "code=" + j.code());
                    return new Jurisdiction(id, j.code(), j.name(), j.regulator(), j.calendarId(),
                        j.settlementCycleT(), j.currency(), j.language(), j.supportedDocTypes(), "ACTIVE");
                }
            }
        });
    }

    /** Update mutable fields of an existing jurisdiction. */
    public Promise<Void> updateJurisdiction(Jurisdiction j, String operatorId) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE operator_jurisdictions SET name=?, regulator=?, calendar_id=?, " +
                     "settlement_cycle_t=?, currency=?, language=?, supported_doc_types=?::jsonb, updated_at=NOW() " +
                     "WHERE jurisdiction_id=?"
                 )) {
                ps.setString(1, j.name()); ps.setString(2, j.regulator()); ps.setString(3, j.calendarId());
                ps.setInt(4, j.settlementCycleT()); ps.setString(5, j.currency());
                ps.setString(6, j.language()); ps.setString(7, toJsonArray(j.supportedDocTypes()));
                ps.setString(8, j.jurisdictionId());
                ps.executeUpdate();
            }
            audit.record(operatorId, "JURISDICTION_UPDATED", "id=" + j.jurisdictionId());
            return null;
        });
    }

    /** Soft-delete a jurisdiction (sets status → INACTIVE). */
    public Promise<Void> deactivate(String jurisdictionId, String operatorId) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE operator_jurisdictions SET status='INACTIVE', updated_at=NOW() WHERE jurisdiction_id=?"
                 )) {
                ps.setString(1, jurisdictionId); ps.executeUpdate();
            }
            audit.record(operatorId, "JURISDICTION_DEACTIVATED", "id=" + jurisdictionId);
            return null;
        });
    }

    /** Get a single jurisdiction by ID. */
    public Promise<Optional<Jurisdiction>> get(String jurisdictionId) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM operator_jurisdictions WHERE jurisdiction_id=?"
                 )) {
                ps.setString(1, jurisdictionId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return Optional.empty();
                    return Optional.of(fromResultSet(rs));
                }
            }
        });
    }

    /** List all active jurisdictions. */
    public Promise<List<Jurisdiction>> listActive() {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM operator_jurisdictions WHERE status='ACTIVE' ORDER BY code"
                 )) {
                List<Jurisdiction> result = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) result.add(fromResultSet(rs));
                }
                return result;
            }
        });
    }

    /** Assign a tenant to a jurisdiction. Idempotent. */
    public Promise<Void> assignTenantToJurisdiction(String tenantId, String jurisdictionId, String operatorId) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO tenant_jurisdictions (tenant_id, jurisdiction_id) VALUES (?,?) ON CONFLICT DO NOTHING"
                 )) {
                ps.setString(1, tenantId); ps.setString(2, jurisdictionId); ps.executeUpdate();
            }
            assignCounter.increment();
            audit.record(operatorId, "TENANT_JURISDICTION_ASSIGNED",
                "tenant=" + tenantId + " jurisdiction=" + jurisdictionId);
            return null;
        });
    }

    /** List jurisdictions assigned to a tenant. */
    public Promise<List<Jurisdiction>> getForTenant(String tenantId) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT j.* FROM operator_jurisdictions j " +
                     "JOIN tenant_jurisdictions tj USING (jurisdiction_id) " +
                     "WHERE tj.tenant_id=? ORDER BY j.code"
                 )) {
                ps.setString(1, tenantId);
                List<Jurisdiction> result = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) result.add(fromResultSet(rs));
                }
                return result;
            }
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Jurisdiction fromResultSet(ResultSet rs) throws SQLException {
        String docTypesRaw = rs.getString("supported_doc_types");
        List<String> docTypes = docTypesRaw == null || docTypesRaw.equals("[]")
            ? List.of()
            : Arrays.asList(docTypesRaw.replaceAll("[\\[\\]\"\\s]", "").split(","));
        return new Jurisdiction(
            rs.getString("jurisdiction_id"), rs.getString("code"), rs.getString("name"),
            rs.getString("regulator"), rs.getString("calendar_id"), rs.getInt("settlement_cycle_t"),
            rs.getString("currency"), rs.getString("language"), docTypes, rs.getString("status")
        );
    }

    private String toJsonArray(List<String> items) {
        if (items == null || items.isEmpty()) return "[]";
        return "[" + String.join(",", items.stream().map(v -> "\"" + v + "\"").toList()) + "]";
    }
}
