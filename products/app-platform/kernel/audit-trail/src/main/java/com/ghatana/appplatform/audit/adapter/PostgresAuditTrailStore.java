package com.ghatana.appplatform.audit.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.appplatform.audit.chain.HashChainService;
import com.ghatana.appplatform.audit.domain.AuditEntry;
import com.ghatana.appplatform.audit.domain.AuditReceipt;
import com.ghatana.appplatform.audit.domain.ChainVerificationResult;
import com.ghatana.appplatform.audit.domain.ChainVerificationResult.ChainViolation;
import com.ghatana.appplatform.audit.port.AuditTrailStore;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * JDBC adapter for {@link AuditTrailStore} backed by PostgreSQL {@code audit_logs}.
 *
 * <p>Serializes hash chain computation and INSERT within a single JDBC transaction using
 * a SELECT FOR UPDATE lock on the previous row to prevent concurrent hash chain splits.
 * All blocking JDBC calls are wrapped in {@code Promise.ofBlocking(executor, …)}.
 *
 * @doc.type class
 * @doc.purpose PostgreSQL JDBC adapter for the cryptographic audit trail store
 * @doc.layer product
 * @doc.pattern Adapter, Repository
 */
public final class PostgresAuditTrailStore implements AuditTrailStore {

    private static final Logger log = LoggerFactory.getLogger(PostgresAuditTrailStore.class);

    private static final String SQL_LAST_CHAIN =
        "SELECT sequence_number, current_hash FROM audit_logs "
        + "WHERE tenant_id = ? ORDER BY sequence_number DESC LIMIT 1 FOR UPDATE";

    private static final String SQL_INSERT =
        "INSERT INTO audit_logs "
        + "(audit_id, action, actor, resource, details, outcome, tenant_id, trace_id, "
        + " previous_hash, current_hash, timestamp_bs, timestamp_gregorian) "
        + "VALUES (?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?, ?, ?)";

    private static final String SQL_VERIFY_RANGE =
        "SELECT audit_id, sequence_number, action, actor, resource, details, outcome, "
        + "tenant_id, trace_id, timestamp_bs, timestamp_gregorian, previous_hash, current_hash "
        + "FROM audit_logs "
        + "WHERE tenant_id = ? AND sequence_number >= ? "
        + "ORDER BY sequence_number ASC";

    private static final String SQL_VERIFY_RANGE_TO =
        SQL_VERIFY_RANGE.replace("ORDER BY", "AND sequence_number <= ? ORDER BY");

    private final DataSource dataSource;
    private final Executor blockingExecutor;
    private final HashChainService chainService;
    private final ObjectMapper mapper;

    public PostgresAuditTrailStore(DataSource dataSource,
                                   Executor blockingExecutor,
                                   HashChainService chainService) {
        this.dataSource       = dataSource;
        this.blockingExecutor = blockingExecutor;
        this.chainService     = chainService;
        this.mapper           = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    /** {@inheritDoc} */
    @Override
    public Promise<AuditReceipt> log(AuditEntry entry) {
        return Promise.ofBlocking(blockingExecutor, () -> {
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    // Lock the latest chain row for this tenant to serialise hash assignment
                    String previousHash = HashChainService.GENESIS_HASH;
                    long nextSeq        = 0L;

                    try (PreparedStatement ps = conn.prepareStatement(SQL_LAST_CHAIN)) {
                        ps.setString(1, entry.tenantId());
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                nextSeq      = rs.getLong("sequence_number") + 1;
                                previousHash = rs.getString("current_hash");
                            }
                        }
                    }

                    String currentHash = chainService.computeHash(previousHash, entry, nextSeq);
                    Instant now        = entry.timestampGregorian();

                    try (PreparedStatement ps = conn.prepareStatement(SQL_INSERT)) {
                        ps.setString(1, entry.id());
                        ps.setString(2, entry.action());
                        ps.setString(3, toJson(Map.of(
                            "user_id",    entry.actor().userId(),
                            "role",       entry.actor().role(),
                            "ip_address", entry.actor().ipAddress(),
                            "session_id", entry.actor().sessionId())));
                        ps.setString(4, toJson(Map.of(
                            "type",      entry.resource().type(),
                            "id",        entry.resource().id(),
                            "parent_id", entry.resource().parentId())));
                        ps.setString(5, toJson(entry.details()));
                        ps.setString(6, entry.outcome().name());
                        ps.setString(7, entry.tenantId());
                        ps.setString(8, entry.traceId());
                        ps.setString(9, previousHash);
                        ps.setString(10, currentHash);
                        ps.setString(11, entry.timestampBs());
                        ps.setTimestamp(12, Timestamp.from(now));
                        ps.executeUpdate();
                    }

                    conn.commit();
                    log.debug("Audit logged id={} action={} seq={}", entry.id(), entry.action(), nextSeq);

                    return new AuditReceipt(entry.id(), nextSeq, previousHash, currentHash, now);

                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                }
            }
        });
    }

    /** {@inheritDoc} */
    @Override
    public Promise<ChainVerificationResult> verifyChain(
            String tenantId, long fromSequence, Long toSequence) {

        return Promise.ofBlocking(blockingExecutor, () -> {
            String sql = toSequence != null ? SQL_VERIFY_RANGE_TO : SQL_VERIFY_RANGE;

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setString(1, tenantId);
                ps.setLong(2, fromSequence);
                if (toSequence != null) {
                    ps.setLong(3, toSequence);
                }

                List<ChainViolation> violations = new ArrayList<>();
                String runningPrevHash = HashChainService.GENESIS_HASH;

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String storedPrevHash = rs.getString("previous_hash");
                        // Verify that stored previous_hash matches what we expect in the chain
                        if (!runningPrevHash.equals(storedPrevHash)) {
                            violations.add(new ChainViolation(
                                rs.getLong("sequence_number"),
                                runningPrevHash,
                                storedPrevHash));
                        }

                        AuditEntry entry = mapRowToEntry(rs);
                        long seq = rs.getLong("sequence_number");
                        String expectedHash = chainService.computeHash(storedPrevHash, entry, seq);
                        String storedHash   = rs.getString("current_hash");

                        if (!chainService.verify(storedPrevHash, entry, seq, storedHash)) {
                            violations.add(new ChainViolation(seq, expectedHash, storedHash));
                        }

                        runningPrevHash = storedHash;
                    }
                }

                return new ChainVerificationResult(violations.isEmpty(), violations);
            }
        });
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private AuditEntry mapRowToEntry(ResultSet rs) throws SQLException {
        Map<String, Object> actorMap    = fromJson(rs.getString("actor"));
        Map<String, Object> resourceMap = fromJson(rs.getString("resource"));
        Map<String, Object> detailsMap  = fromJson(rs.getString("details"));

        return AuditEntry.builder()
            .id(rs.getString("audit_id"))
            .action(rs.getString("action"))
            .actor(new AuditEntry.Actor(
                (String) actorMap.get("user_id"),
                (String) actorMap.get("role"),
                (String) actorMap.get("ip_address"),
                (String) actorMap.get("session_id")))
            .resource(new AuditEntry.Resource(
                (String) resourceMap.get("type"),
                (String) resourceMap.get("id"),
                (String) resourceMap.get("parent_id")))
            .details(detailsMap)
            .outcome(AuditEntry.Outcome.valueOf(rs.getString("outcome")))
            .tenantId(rs.getString("tenant_id"))
            .traceId(rs.getString("trace_id"))
            .timestampBs(rs.getString("timestamp_bs"))
            .timestampGregorian(rs.getTimestamp("timestamp_gregorian").toInstant())
            .build();
    }

    private String toJson(Object obj) {
        if (obj == null) return "{}";
        try {
            return mapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot serialize to JSON: " + obj, e);
        }
    }

    private Map<String, Object> fromJson(String json) {
        if (json == null) return Map.of();
        try {
            return mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot deserialize JSONB: " + json, e);
        }
    }
}
