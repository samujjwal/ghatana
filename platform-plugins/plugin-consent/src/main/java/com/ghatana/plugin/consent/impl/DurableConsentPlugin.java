package com.ghatana.plugin.consent.impl;

import com.ghatana.platform.plugin.PluginContext;
import com.ghatana.platform.plugin.PluginMetadata;
import com.ghatana.platform.plugin.PluginState;
import com.ghatana.platform.plugin.PluginType;
import com.ghatana.plugin.consent.ConsentPlugin;
import com.ghatana.plugin.consent.event.ConsentRevocationEvent;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/**
 * Durable, JDBC-backed implementation of {@link ConsentPlugin}.
 *
 * <p>Persists consent records in a relational table so consent history survives
 * restarts and node replacement. The most-recent consent record per
 * {@code (subjectId, purpose)} pair determines the effective consent status.
 * Expired consents are detected at read-time; no background cleanup job is
 * needed — though one can optionally run via {@link #purgeExpiredConsents()}.
 *
 * <p>Call {@link #ensureSchema()} once during application startup (e.g. from a
 * Flyway migration or an initialization hook) before the plugin is started.
 *
 * <p>For development and testing where durability is not required, prefer
 * {@link StandardConsentPlugin} instead.
 *
 * @doc.type class
 * @doc.purpose Durable JDBC-backed consent management plugin with expiry support
 * @doc.layer platform
 * @doc.pattern Plugin Implementation, Adapter
 * @since 1.1.0
 */
public final class DurableConsentPlugin implements ConsentPlugin {

    private static final Logger LOG = LoggerFactory.getLogger(DurableConsentPlugin.class);
    private static final String TABLE = "plugin_consent_records";

    private final DataSource dataSource;
    private PluginState state = PluginState.UNLOADED;
    /** Nullable — set during initialize(); used for event publishing. */
    private volatile PluginContext pluginContext;

    /**
     * Creates a new durable consent plugin backed by the given data source.
     *
     * @param dataSource the JDBC data source; must not be null
     */
    public DurableConsentPlugin(DataSource dataSource) {
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
                  consent_id    VARCHAR(128) PRIMARY KEY,
                  subject_id    VARCHAR(256) NOT NULL,
                  purpose       VARCHAR(256) NOT NULL,
                  status        VARCHAR(32)  NOT NULL,
                  action        VARCHAR(32)  NOT NULL,
                  legal_basis   VARCHAR(256),
                  granted_at    BIGINT       NOT NULL,
                  expires_at    BIGINT,
                  revoked_at    BIGINT,
                  metadata      TEXT
                )
                """.formatted(TABLE);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(ddl)) {
            ps.executeUpdate();
            LOG.info("DurableConsentPlugin: schema ensured for table '{}'", TABLE);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialise consent plugin schema", e);
        }
    }

    // -------------------------------------------------------------------------
    // Plugin lifecycle
    // -------------------------------------------------------------------------

    @Override
    public PluginMetadata metadata() {
        return PluginMetadata.builder()
                .id("durable-consent-plugin")
                .name("Durable Consent Plugin")
                .version("1.1.0")
                .description("JDBC-backed durable consent management with expiry support")
                .type(PluginType.GOVERNANCE)
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
        this.pluginContext = context;
        state = PluginState.INITIALIZED;
        LOG.info("DurableConsentPlugin initialized");
        return Promise.complete();
    }

    @Override
    public Promise<Void> start() {
        state = PluginState.STARTED;
        LOG.info("DurableConsentPlugin started");
        return Promise.complete();
    }

    @Override
    public Promise<Void> stop() {
        state = PluginState.STOPPED;
        LOG.info("DurableConsentPlugin stopped");
        return Promise.complete();
    }

    @Override
    public Promise<Void> shutdown() {
        state = PluginState.UNLOADED;
        LOG.info("DurableConsentPlugin shutdown");
        return Promise.complete();
    }

    // -------------------------------------------------------------------------
    // ConsentPlugin operations
    // -------------------------------------------------------------------------

    @Override
    public Promise<ConsentRecord> recordConsent(String subjectId, String purpose, ConsentAction action) {
        Objects.requireNonNull(subjectId, "subjectId cannot be null");
        Objects.requireNonNull(purpose, "purpose cannot be null");
        Objects.requireNonNull(action, "action cannot be null");

        String consentId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        ConsentStatus status = actionToStatus(action);
        String legalBasis = determineLegalBasis(purpose);
        Instant expiresAt = calculateExpiry(purpose, now);
        Instant revokedAt = action == ConsentAction.WITHDRAW ? now : null;

        ConsentRecord record = new ConsentRecord(
                consentId, subjectId, purpose, status, action,
                legalBasis, now, expiresAt, revokedAt, null);

        String sql = """
                INSERT INTO %s
                  (consent_id, subject_id, purpose, status, action,
                   legal_basis, granted_at, expires_at, revoked_at, metadata)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.formatted(TABLE);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, consentId);
            ps.setString(2, subjectId);
            ps.setString(3, purpose);
            ps.setString(4, status.name());
            ps.setString(5, action.name());
            ps.setString(6, legalBasis);
            ps.setLong(7, now.toEpochMilli());
            ps.setObject(8, expiresAt != null ? expiresAt.toEpochMilli() : null);
            ps.setObject(9, revokedAt != null ? revokedAt.toEpochMilli() : null);
            ps.setString(10, null);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to record consent for " + subjectId, e);
        }

        LOG.info("Recorded {} consent ({}) for {} on purpose {}",
                action, consentId, subjectId, purpose);

        // Emit a revocation event for WITHDRAW so downstream consumers can invalidate caches
        if (action == ConsentAction.WITHDRAW) {
            publishRevocationEvent(
                new ConsentRevocationEvent(
                    consentId,
                    subjectId,
                    purpose,
                    ConsentRevocationEvent.RevocationReason.WITHDRAWAL,
                    now
                ),
                "withdrawal"
            );
        }
        return Promise.of(record);
    }

    @Override
    public Promise<Boolean> verifyConsent(String subjectId, String purpose) {
        Objects.requireNonNull(subjectId, "subjectId cannot be null");
        Objects.requireNonNull(purpose, "purpose cannot be null");
        ConsentStatus current = resolveCurrentStatus(subjectId, purpose);
        return Promise.of(current == ConsentStatus.GRANTED);
    }

    @Override
    public Promise<Void> revokeConsent(String consentId) {
        Objects.requireNonNull(consentId, "consentId cannot be null");
        Instant now = Instant.now();

        // Fetch subject + purpose first so we can emit the revocation event
        String selectSql = "SELECT subject_id, purpose FROM %s WHERE consent_id = ?".formatted(TABLE);
        String subjectId = null;
        String purpose = null;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(selectSql)) {
            ps.setString(1, consentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    subjectId = rs.getString("subject_id");
                    purpose = rs.getString("purpose");
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to look up consent " + consentId + " for revocation", e);
        }

        String updateSql = "UPDATE %s SET status = 'REVOKED', revoked_at = ? WHERE consent_id = ?"
                .formatted(TABLE);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(updateSql)) {
            ps.setLong(1, now.toEpochMilli());
            ps.setString(2, consentId);
            int rows = ps.executeUpdate();
            if (rows == 0) {
                LOG.warn("revokeConsent: consent {} not found", consentId);
                return Promise.complete();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to revoke consent " + consentId, e);
        }

        // Emit revocation event so downstream consumers can propagate the change
        if (subjectId != null) {
            publishRevocationEvent(
                new ConsentRevocationEvent(
                    consentId,
                    subjectId,
                    purpose,
                    ConsentRevocationEvent.RevocationReason.EXPLICIT_REVOCATION,
                    now
                ),
                "explicit revocation of subject=" + subjectId
            );
        }
        return Promise.complete();
    }

    @Override
    public Promise<List<ConsentRecord>> getConsentHistory(String subjectId) {
        Objects.requireNonNull(subjectId, "subjectId cannot be null");
        String sql = """
                SELECT consent_id, subject_id, purpose, status, action,
                       legal_basis, granted_at, expires_at, revoked_at, metadata
                  FROM %s
                 WHERE subject_id = ?
                 ORDER BY granted_at ASC
                """.formatted(TABLE);
        List<ConsentRecord> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, subjectId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(rowToRecord(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load consent history for " + subjectId, e);
        }
        return Promise.of(result);
    }

    @Override
    public Promise<ConsentStatus> getCurrentConsent(String subjectId, String purpose) {
        Objects.requireNonNull(subjectId, "subjectId cannot be null");
        Objects.requireNonNull(purpose, "purpose cannot be null");
        return Promise.of(resolveCurrentStatus(subjectId, purpose));
    }

    @Override
    public Promise<Integer> deleteAllForSubject(String subjectId) {
        Objects.requireNonNull(subjectId, "subjectId cannot be null");
        String sql = "DELETE FROM %s WHERE subject_id = ?".formatted(TABLE);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, subjectId);
            int deleted = ps.executeUpdate();
            LOG.info("deleteAllForSubject: erased {} consent record(s) for subject {}", deleted, subjectId);
            return Promise.of(deleted);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to erase consent data for subject " + subjectId, e);
        }
    }

    // -------------------------------------------------------------------------
    // Maintenance
    // -------------------------------------------------------------------------

    /**
     * Hard-deletes consent records whose {@code expires_at} is in the past
     * and whose status is {@code EXPIRED}.
     *
     * <p>This method is optional; the plugin functions correctly without it.
     * Callers may schedule it at off-peak intervals for table hygiene.
     *
     * @return number of rows deleted
     */
    public int purgeExpiredConsents() {
        // First mark any GRANTED consents whose expiry has passed
        String markSql = """
                UPDATE %s SET status = 'EXPIRED'
                 WHERE status = 'GRANTED'
                   AND expires_at IS NOT NULL
                   AND expires_at < ?
                """.formatted(TABLE);
        String deleteSql = "DELETE FROM %s WHERE status = 'EXPIRED' AND expires_at < ?".formatted(TABLE);
        long now = Instant.now().toEpochMilli();
        try (Connection conn = dataSource.getConnection()) {
            int marked;
            try (PreparedStatement ps = conn.prepareStatement(markSql)) {
                ps.setLong(1, now);
                marked = ps.executeUpdate();
            }
            int deleted;
            try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                ps.setLong(1, now);
                deleted = ps.executeUpdate();
            }
            LOG.info("purgeExpiredConsents: marked={} deleted={}", marked, deleted);
            return deleted;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to purge expired consents", e);
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private ConsentStatus resolveCurrentStatus(String subjectId, String purpose) {
        String sql = """
                SELECT status, expires_at FROM %s
                 WHERE subject_id = ? AND purpose = ?
                 ORDER BY granted_at DESC
                 LIMIT 1
                """.formatted(TABLE);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, subjectId);
            ps.setString(2, purpose);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return ConsentStatus.NOT_REQUESTED;
                }
                ConsentStatus dbStatus = ConsentStatus.valueOf(rs.getString("status"));
                long expiresAtMs = rs.getLong("expires_at");
                if (!rs.wasNull() && Instant.now().toEpochMilli() > expiresAtMs) {
                    return ConsentStatus.EXPIRED;
                }
                return dbStatus;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to resolve consent status for " + subjectId, e);
        }
    }

    private ConsentRecord rowToRecord(ResultSet rs) throws SQLException {
        long expiresAtMs = rs.getLong("expires_at");
        Instant expiresAt = rs.wasNull() ? null : Instant.ofEpochMilli(expiresAtMs);
        long revokedAtMs = rs.getLong("revoked_at");
        Instant revokedAt = rs.wasNull() ? null : Instant.ofEpochMilli(revokedAtMs);
        return new ConsentRecord(
                rs.getString("consent_id"),
                rs.getString("subject_id"),
                rs.getString("purpose"),
                ConsentStatus.valueOf(rs.getString("status")),
                ConsentAction.valueOf(rs.getString("action")),
                rs.getString("legal_basis"),
                Instant.ofEpochMilli(rs.getLong("granted_at")),
                expiresAt,
                revokedAt,
                rs.getString("metadata"));
    }

    private void publishRevocationEvent(ConsentRevocationEvent event, String reason) {
        if (pluginContext == null) {
            return;
        }

        var interactionBus = pluginContext.getInteractionBus();
        if (interactionBus == null) {
            LOG.debug("Skipping {} event publication because interaction bus is unavailable", ConsentRevocationEvent.TOPIC);
            return;
        }

        interactionBus.publish(ConsentRevocationEvent.TOPIC, event);
        LOG.info("Emitted {} event for consent {} ({})",
            ConsentRevocationEvent.TOPIC, event.consentId(), reason);
    }

    private static ConsentStatus actionToStatus(ConsentAction action) {
        return switch (action) {
            case GRANT -> ConsentStatus.GRANTED;
            case DENY -> ConsentStatus.DENIED;
            case WITHDRAW -> ConsentStatus.REVOKED;
        };
    }

    private static String determineLegalBasis(String purpose) {
        // Legal basis is configurable per purpose via the plugin binding manifest.
        // The platform does not know which regulatory framework applies — products
        // register that mapping through configuration. Default is explicit-consent.
        return "explicit-consent";
    }

    private static Instant calculateExpiry(String purpose, Instant from) {
        // Default expiry is 1 year. Products configure purpose-specific expiry
        // (purposeVocabulary.expiryDays) in their plugin binding manifest.
        return from.plusSeconds(365L * 24 * 3600);
    }
}
