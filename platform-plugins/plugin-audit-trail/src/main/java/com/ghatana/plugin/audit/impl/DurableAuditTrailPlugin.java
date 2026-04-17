package com.ghatana.plugin.audit.impl;

import com.ghatana.platform.plugin.PluginContext;
import com.ghatana.platform.plugin.PluginMetadata;
import com.ghatana.platform.plugin.PluginState;
import com.ghatana.platform.plugin.PluginType;
import com.ghatana.plugin.audit.AuditTrailPlugin;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Durable, JDBC-backed implementation of {@link AuditTrailPlugin}.
 *
 * <p>Persists audit entries in a relational table so the hash chain survives
 * restarts and node replacement. Callers must supply a {@link DataSource} —
 * this class does not manage the connection pool itself.
 *
 * <p>Call {@link #ensureSchema()} once during application startup (e.g. from a
 * Flyway migration or an initialization hook) before the plugin is started.
 *
 * <p>For development and testing where durability is not required, prefer
 * {@link StandardAuditTrailPlugin} instead.
 *
 * @doc.type class
 * @doc.purpose Durable PostgreSQL-backed audit trail plugin with hash-chain integrity
 * @doc.layer platform
 * @doc.pattern Plugin Implementation, Adapter
 * @since 1.1.0
 */
public final class DurableAuditTrailPlugin implements AuditTrailPlugin {

    private static final Logger LOG = LoggerFactory.getLogger(DurableAuditTrailPlugin.class);
    private static final String TABLE = "plugin_audit_entries";

    private final DataSource dataSource;
    private PluginState state = PluginState.UNLOADED;

    /**
     * Creates a new durable audit trail plugin backed by the given data source.
     *
     * @param dataSource the JDBC data source; must not be null
     */
    public DurableAuditTrailPlugin(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource cannot be null");
    }

    /**
     * Creates the backing table if it does not yet exist.
     *
     * <p>Idempotent — safe to call on every application startup.
     */
    public void ensureSchema() {
        String ddl = """
                CREATE TABLE IF NOT EXISTS %s (
                  entry_id       VARCHAR(128)  PRIMARY KEY,
                  entity_id      VARCHAR(256)  NOT NULL,
                  action         VARCHAR(256)  NOT NULL,
                  actor_id       VARCHAR(256),
                  details        TEXT,
                  previous_hash  VARCHAR(256),
                  entry_hash     VARCHAR(256)  NOT NULL,
                  entry_ts       BIGINT        NOT NULL
                )
                """.formatted(TABLE);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(ddl)) {
            ps.executeUpdate();
            LOG.info("DurableAuditTrailPlugin: schema ensured for table '{}'", TABLE);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize audit plugin schema", e);
        }
    }

    // -------------------------------------------------------------------------
    // Plugin lifecycle
    // -------------------------------------------------------------------------

    @Override
    public PluginMetadata metadata() {
        return PluginMetadata.builder()
                .id("durable-audit-trail-plugin")
                .name("Durable Audit Trail Plugin")
                .version("1.1.0")
                .description("JDBC-backed durable audit trail with hash-chain integrity")
                .type(PluginType.CUSTOM)
                .author("Ghatana")
                .license("Apache-2.0")
                .build();
    }

    @Override
    public PluginState getState() {
        return state;
    }

    @Override
    public Promise<Void> initialize(PluginContext context) {
        state = PluginState.INITIALIZED;
        LOG.info("DurableAuditTrailPlugin initialized");
        return Promise.complete();
    }

    @Override
    public Promise<Void> start() {
        state = PluginState.STARTED;
        LOG.info("DurableAuditTrailPlugin started");
        return Promise.complete();
    }

    @Override
    public Promise<Void> stop() {
        state = PluginState.STOPPED;
        LOG.info("DurableAuditTrailPlugin stopped");
        return Promise.complete();
    }

    @Override
    public Promise<Void> shutdown() {
        state = PluginState.UNLOADED;
        LOG.info("DurableAuditTrailPlugin shutdown");
        return Promise.complete();
    }

    // -------------------------------------------------------------------------
    // AuditTrailPlugin operations
    // -------------------------------------------------------------------------

    @Override
    public Promise<AuditEntry> logEvent(String entityId, String action, Map<String, Object> details) {
        Objects.requireNonNull(entityId, "entityId cannot be null");
        Objects.requireNonNull(action, "action cannot be null");

        String entryId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        // Fetch the latest hash for the entity to maintain chain integrity
        String previousHash = loadLatestHash(entityId);

        String actorId = details != null ? String.valueOf(details.getOrDefault("actorId", "system")) : "system";
        String hash = computeHash(entryId, entityId, action, actorId, now.toEpochMilli(), previousHash);

        AuditEntry entry = new AuditEntry(
                entryId, entityId, action,
                details != null ? Map.copyOf(details) : Map.of(),
                actorId, hash, previousHash, now);

        persist(entry);
        return Promise.of(entry);
    }

    @Override
    public Promise<List<AuditEntry>> getTrail(String entityId) {
        Objects.requireNonNull(entityId, "entityId cannot be null");
        return Promise.of(loadTrail(entityId));
    }

    @Override
    public Promise<VerificationResult> verifyIntegrity(String entityId) {
        Objects.requireNonNull(entityId, "entityId cannot be null");
        List<AuditEntry> trail = loadTrail(entityId);
        List<String> violations = new ArrayList<>();

        String expectedPrevious = null;
        for (AuditEntry entry : trail) {
            if (!Objects.equals(entry.previousHash(), expectedPrevious)) {
                violations.add("Chain break at entry " + entry.entryId()
                        + ": expected previousHash=" + expectedPrevious
                        + " but got=" + entry.previousHash());
            }
            String recomputed = computeHash(
                    entry.entryId(), entry.entityId(), entry.action(),
                    entry.actorId(), entry.timestamp().toEpochMilli(), entry.previousHash());
            if (!recomputed.equals(entry.hash())) {
                violations.add("Hash mismatch at entry " + entry.entryId());
            }
            expectedPrevious = entry.hash();
        }

        return Promise.of(new VerificationResult(
                entityId, violations.isEmpty(), trail.size(), violations, Instant.now()));
    }

    @Override
    public Promise<Void> exportTrail(String entityId, ExportFormat format, OutputStream out) {
        Objects.requireNonNull(entityId, "entityId cannot be null");
        Objects.requireNonNull(out, "out cannot be null");
        List<AuditEntry> trail = loadTrail(entityId);
        try {
            String data = switch (format) {
                case JSON -> toJson(trail);
                case CSV -> toCsv(trail);
                case XML -> toXml(trail);
                case PDF -> throw new UnsupportedOperationException("PDF export is not supported");
            };
            out.write(data.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            return Promise.ofException(e);
        }
        return Promise.complete();
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private void persist(AuditEntry entry) {
        String sql = """
                INSERT INTO %s
                  (entry_id, entity_id, action, actor_id, details, previous_hash, entry_hash, entry_ts)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """.formatted(TABLE);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entry.entryId());
            ps.setString(2, entry.entityId());
            ps.setString(3, entry.action());
            ps.setString(4, entry.actorId());
            ps.setString(5, mapToJsonString(entry.details()));
            ps.setString(6, entry.previousHash());
            ps.setString(7, entry.hash());
            ps.setLong(8, entry.timestamp().toEpochMilli());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to persist audit entry " + entry.entryId(), e);
        }
    }

    private List<AuditEntry> loadTrail(String entityId) {
        String sql = """
                SELECT entry_id, entity_id, action, actor_id, details,
                       previous_hash, entry_hash, entry_ts
                  FROM %s
                 WHERE entity_id = ?
                 ORDER BY entry_ts ASC
                """.formatted(TABLE);
        List<AuditEntry> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entityId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(rowToEntry(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load audit trail for entity " + entityId, e);
        }
        return result;
    }

    private String loadLatestHash(String entityId) {
        String sql = """
                SELECT entry_hash
                  FROM %s
                 WHERE entity_id = ?
                 ORDER BY entry_ts DESC
                 LIMIT 1
                """.formatted(TABLE);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entityId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("entry_hash") : null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load latest hash for entity " + entityId, e);
        }
    }

    private AuditEntry rowToEntry(ResultSet rs) throws SQLException {
        return new AuditEntry(
                rs.getString("entry_id"),
                rs.getString("entity_id"),
                rs.getString("action"),
                jsonStringToMap(rs.getString("details")),
                rs.getString("actor_id"),
                rs.getString("entry_hash"),
                rs.getString("previous_hash"),
                Instant.ofEpochMilli(rs.getLong("entry_ts")));
    }

    private static String computeHash(String entryId, String entityId, String action,
                                       String actorId, long epochMilli, String previousHash) {
        String input = entryId + "|" + entityId + "|" + action + "|"
                + actorId + "|" + epochMilli + "|" + Objects.toString(previousHash, "");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    // Minimal JSON helpers — avoids pulling in a JSON library just for map serialization
    private static String mapToJsonString(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(escapeJson(entry.getKey())).append("\":");
            Object v = entry.getValue();
            if (v == null) {
                sb.append("null");
            } else {
                sb.append("\"").append(escapeJson(String.valueOf(v))).append("\"");
            }
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> jsonStringToMap(String json) {
        // Minimal parse back — adequate for non-nested audit detail values
        if (json == null || json.isBlank() || "{}".equals(json.trim())) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        String trimmed = json.trim().replaceAll("^\\{|\\}$", "");
        for (String pair : trimmed.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)")) {
            String[] kv = pair.split(":", 2);
            if (kv.length == 2) {
                String k = kv[0].trim().replaceAll("^\"|\"$", "");
                String v = kv[1].trim().replaceAll("^\"|\"$", "");
                result.put(k, "null".equals(v) ? null : v);
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static String toJson(List<AuditEntry> trail) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (AuditEntry e : trail) {
            if (!first) sb.append(",");
            sb.append("{\"entryId\":\"").append(e.entryId()).append("\"")
              .append(",\"entityId\":\"").append(e.entityId()).append("\"")
              .append(",\"action\":\"").append(e.action()).append("\"")
              .append(",\"actorId\":\"").append(e.actorId()).append("\"")
              .append(",\"hash\":\"").append(e.hash()).append("\"")
              .append(",\"previousHash\":").append(e.previousHash() != null
                      ? "\"" + e.previousHash() + "\"" : "null")
              .append(",\"timestamp\":\"").append(e.timestamp()).append("\"")
              .append("}");
            first = false;
        }
        sb.append("]");
        return sb.toString();
    }

    private static String toCsv(List<AuditEntry> trail) {
        StringBuilder sb = new StringBuilder("entryId,entityId,action,actorId,hash,previousHash,timestamp\n");
        for (AuditEntry e : trail) {
            sb.append(e.entryId()).append(",")
              .append(e.entityId()).append(",")
              .append(e.action()).append(",")
              .append(e.actorId()).append(",")
              .append(e.hash()).append(",")
              .append(e.previousHash() != null ? e.previousHash() : "").append(",")
              .append(e.timestamp()).append("\n");
        }
        return sb.toString();
    }

    private static String toXml(List<AuditEntry> trail) {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<auditTrail>\n");
        for (AuditEntry e : trail) {
            sb.append("  <entry>\n")
              .append("    <entryId>").append(e.entryId()).append("</entryId>\n")
              .append("    <entityId>").append(e.entityId()).append("</entityId>\n")
              .append("    <action>").append(e.action()).append("</action>\n")
              .append("    <actorId>").append(e.actorId()).append("</actorId>\n")
              .append("    <hash>").append(e.hash()).append("</hash>\n")
              .append("    <timestamp>").append(e.timestamp()).append("</timestamp>\n")
              .append("  </entry>\n");
        }
        sb.append("</auditTrail>");
        return sb.toString();
    }
}
