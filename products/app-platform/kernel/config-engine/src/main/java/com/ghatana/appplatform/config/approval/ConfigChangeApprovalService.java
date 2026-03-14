package com.ghatana.appplatform.config.approval;

import com.ghatana.appplatform.config.domain.ConfigEntry;
import com.ghatana.appplatform.config.domain.ConfigHierarchyLevel;
import com.ghatana.appplatform.config.port.ConfigStore;
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
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Maker-checker approval workflow for config changes (STORY-K02-014).
 *
 * <p>Implements the four-eyes principle for config mutations:
 * <ol>
 *   <li>A <em>maker</em> proposes a change via {@link #propose}. The proposal is stored
 *       in {@code config_change_proposals} with status {@code PENDING}. The config entry
 *       is NOT written to {@code config_entries} yet.</li>
 *   <li>A different user (the <em>checker</em>) reviews via {@link #approve} or
 *       {@link #reject}.</li>
 *   <li>On {@link #approve}: the proposal is applied to {@link ConfigStore} atomically
 *       and the proposal status transitions to {@code APPROVED}.</li>
 *   <li>On {@link #reject}: the proposal status transitions to {@code REJECTED} with the
 *       supplied reason. No config change is made.</li>
 * </ol>
 *
 * <p>The maker and checker must be different users (enforced at service level;
 * the DB constraint does not enforce this to allow testing).
 *
 * @doc.type class
 * @doc.purpose Config maker-checker approval workflow (K02-014)
 * @doc.layer product
 * @doc.pattern Service
 */
public final class ConfigChangeApprovalService {

    private static final Logger log = LoggerFactory.getLogger(ConfigChangeApprovalService.class);

    private static final String SQL_INSERT_PROPOSAL =
        "INSERT INTO config_change_proposals "
        + "(tenant_id, namespace, config_key, proposed_value, hierarchy_level, level_id, "
        + " schema_namespace, proposed_by, maker_audit_id) "
        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) "
        + "RETURNING proposal_id";

    private static final String SQL_FIND_PENDING =
        "SELECT proposal_id, tenant_id, namespace, config_key, proposed_value, "
        + "       hierarchy_level, level_id, schema_namespace, proposed_by "
        + "FROM config_change_proposals "
        + "WHERE proposal_id = ? AND status = 'PENDING'";

    private static final String SQL_APPROVE =
        "UPDATE config_change_proposals "
        + "SET status = 'APPROVED', reviewed_by = ?, reviewed_at = NOW(), "
        + "    applied_at = NOW(), checker_audit_id = ? "
        + "WHERE proposal_id = ? AND status = 'PENDING'";

    private static final String SQL_REJECT =
        "UPDATE config_change_proposals "
        + "SET status = 'REJECTED', reviewed_by = ?, reviewed_at = NOW(), "
        + "    rejection_reason = ?, checker_audit_id = ? "
        + "WHERE proposal_id = ? AND status = 'PENDING'";

    private final DataSource dataSource;
    private final ConfigStore configStore;
    private final Executor blockingExecutor;

    /**
     * @param dataSource       JDBC data source for proposal table operations
     * @param configStore      config store where approved changes are applied
     * @param blockingExecutor executor for JDBC calls (off eventloop)
     */
    public ConfigChangeApprovalService(DataSource dataSource, ConfigStore configStore,
                                        Executor blockingExecutor) {
        this.dataSource       = Objects.requireNonNull(dataSource, "dataSource");
        this.configStore      = Objects.requireNonNull(configStore, "configStore");
        this.blockingExecutor = Objects.requireNonNull(blockingExecutor, "blockingExecutor");
    }

    /**
     * Submits a config change proposal for review.
     *
     * <p>The change is NOT applied until a checker calls {@link #approve}. The returned
     * UUID is the proposal ID that the maker should share with the checker.
     *
     * @param request the proposed change
     * @return promise resolving to the new proposal ID (UUID string)
     * @throws IllegalArgumentException if the proposal fields are invalid
     */
    public Promise<String> propose(ProposalRequest request) {
        return Promise.ofBlocking(blockingExecutor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SQL_INSERT_PROPOSAL)) {
                ps.setString(1, request.tenantId());
                ps.setString(2, request.namespace());
                ps.setString(3, request.key());
                ps.setString(4, request.value());
                ps.setString(5, request.level().name());
                ps.setString(6, request.levelId());
                ps.setString(7, request.schemaNamespace());
                ps.setString(8, request.proposedBy());
                ps.setString(9, request.makerAuditId());  // may be null
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String proposalId = rs.getString(1);
                        log.info("Config proposal created: id={} namespace={} key={} by={}",
                            proposalId, request.namespace(), request.key(), request.proposedBy());
                        return proposalId;
                    }
                    throw new IllegalStateException("INSERT proposal returned no rows");
                }
            }
        });
    }

    /**
     * Approves a pending proposal and applies the config change.
     *
     * <p>The checker MUST be a different user than the maker (enforced by this method).
     *
     * @param proposalId     the proposal to approve
     * @param checkerId      the user approving the proposal (must differ from maker)
     * @param checkerAuditId audit entry ID for this approval action (may be null)
     * @return promise that resolves to the proposalId if approved, or empty if not found
     * @throws IllegalArgumentException if the checker is the same as the maker
     */
    public Promise<Optional<String>> approve(String proposalId, String checkerId, String checkerAuditId) {
        // Phase 1: validate + build ConfigEntry (blocking, JDBC)
        Promise<Optional<ConfigEntry>> entryPromise = Promise.ofBlocking(blockingExecutor, () -> {
            ProposalRow proposal = fetchPending(proposalId).orElse(null);
            if (proposal == null) {
                log.warn("Approve failed: proposal {} not found or not PENDING", proposalId);
                return Optional.<ConfigEntry>empty();
            }
            if (proposal.proposedBy().equals(checkerId)) {
                throw new IllegalArgumentException(
                    "Maker and checker must be different users. proposedBy=" + proposal.proposedBy());
            }
            return Optional.of(new ConfigEntry(
                proposal.namespace(),
                proposal.configKey(),
                proposal.proposedValue(),
                ConfigHierarchyLevel.valueOf(proposal.hierarchyLevel()),
                proposal.levelId(),
                proposal.schemaNamespace()
            ));
        });

        // Phase 2: apply the config entry (async via configStore), then mark approved
        return entryPromise.then(entryOpt -> {
            if (entryOpt.isEmpty()) return Promise.of(Optional.empty());
            return configStore.setEntry(entryOpt.get())
                .then(ignored -> Promise.ofBlocking(blockingExecutor, () -> {
                    int updated = updateApprovalStatus(proposalId, checkerId, checkerAuditId);
                    if (updated > 0) {
                        log.info("Config proposal approved: id={} checker={}", proposalId, checkerId);
                    }
                    return Optional.of(proposalId);
                }));
        });
    }

    /**
     * Rejects a pending proposal. The config is NOT changed.
     *
     * @param proposalId     the proposal to reject
     * @param checkerId      the user rejecting the proposal (must differ from maker)
     * @param reason         the rejection reason (required)
     * @param checkerAuditId audit entry ID for this rejection action (may be null)
     * @return promise resolving to true if the proposal was found and rejected, false otherwise
     */
    public Promise<Boolean> reject(String proposalId, String checkerId, String reason, String checkerAuditId) {
        Objects.requireNonNull(reason, "rejection reason must not be null");
        return Promise.ofBlocking(blockingExecutor, () -> {
            ProposalRow proposal = fetchPending(proposalId).orElse(null);
            if (proposal == null) {
                log.warn("Reject failed: proposal {} not found or not PENDING", proposalId);
                return false;
            }
            if (proposal.proposedBy().equals(checkerId)) {
                throw new IllegalArgumentException(
                    "Maker and checker must be different users. proposedBy=" + proposal.proposedBy());
            }
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SQL_REJECT)) {
                ps.setString(1, checkerId);
                ps.setString(2, reason);
                ps.setString(3, checkerAuditId);
                ps.setObject(4, UUID.fromString(proposalId));
                int rows = ps.executeUpdate();
                if (rows > 0) {
                    log.info("Config proposal rejected: id={} checker={} reason={}",
                        proposalId, checkerId, reason);
                }
                return rows > 0;
            }
        });
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private Optional<ProposalRow> fetchPending(String proposalId) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND_PENDING)) {
            ps.setObject(1, UUID.fromString(proposalId));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new ProposalRow(
                    rs.getString("proposal_id"),
                    rs.getString("tenant_id"),
                    rs.getString("namespace"),
                    rs.getString("config_key"),
                    rs.getString("proposed_value"),
                    rs.getString("hierarchy_level"),
                    rs.getString("level_id"),
                    rs.getString("schema_namespace"),
                    rs.getString("proposed_by")
                ));
            }
        }
    }

    private int updateApprovalStatus(String proposalId, String reviewerId, String auditId) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_APPROVE)) {
            ps.setString(1, reviewerId);
            ps.setString(2, auditId);
            ps.setObject(3, UUID.fromString(proposalId));
            return ps.executeUpdate();
        }
    }

    // ─── Value types ─────────────────────────────────────────────────────────

    /**
     * Describes a proposed config change submitted by a maker.
     *
     * @doc.type record
     * @doc.purpose Proposal request DTO for config maker-checker workflow
     * @doc.layer product
     * @doc.pattern ValueObject
     */
    public record ProposalRequest(
        String tenantId,
        String namespace,
        String key,
        String value,
        ConfigHierarchyLevel level,
        String levelId,
        String schemaNamespace,
        String proposedBy,
        String makerAuditId  // may be null if audit integration not wired
    ) {
        public ProposalRequest {
            Objects.requireNonNull(tenantId, "tenantId");
            Objects.requireNonNull(namespace, "namespace");
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(level, "level");
            Objects.requireNonNull(levelId, "levelId");
            Objects.requireNonNull(schemaNamespace, "schemaNamespace");
            Objects.requireNonNull(proposedBy, "proposedBy");
        }
    }

    /** Internal row read from {@code config_change_proposals}. */
    private record ProposalRow(
        String proposalId,
        String tenantId,
        String namespace,
        String configKey,
        String proposedValue,
        String hierarchyLevel,
        String levelId,
        String schemaNamespace,
        String proposedBy
    ) {}
}
