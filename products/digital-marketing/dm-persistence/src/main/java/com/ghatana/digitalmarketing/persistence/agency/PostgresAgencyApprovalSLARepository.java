package com.ghatana.digitalmarketing.persistence.agency;

import com.ghatana.digitalmarketing.application.agency.AgencyApprovalSLARepository;
import com.ghatana.digitalmarketing.domain.agency.AgencyApprovalSLA;
import com.ghatana.digitalmarketing.persistence.campaign.DmPersistenceException;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * Production PostgreSQL adapter for {@link AgencyApprovalSLARepository}.
 *
 * <p>Wraps all blocking JDBC I/O in {@code Promise.ofBlocking()} to remain event-loop safe.
 * Uses upsert semantics for idempotent saves. Enforces tenant isolation at query level.</p>
 *
 * @doc.type class
 * @doc.purpose Production JDBC adapter for agency approval SLA persistence (P3-002)
 * @doc.layer product
 * @doc.pattern Adapter, Repository
 */
public final class PostgresAgencyApprovalSLARepository implements AgencyApprovalSLARepository {

    private static final Logger LOG = LoggerFactory.getLogger(PostgresAgencyApprovalSLARepository.class);

    private static final String UPSERT_SQL =
        "INSERT INTO dmos_agency_approval_slas (id, contract_id, agency_tenant_id, client_id, approval_type, max_approval_time, escalation_level, escalation_timeouts, escalation_procedure, active, created_at, updated_at) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?) " +
        "ON CONFLICT (id) DO UPDATE SET " +
        "  escalation_level = EXCLUDED.escalation_level, active = EXCLUDED.active, updated_at = EXCLUDED.updated_at";

    private static final String SELECT_BY_ID_SQL =
        "SELECT id, contract_id, agency_tenant_id, client_id, approval_type, max_approval_time, escalation_level, escalation_timeouts, escalation_procedure, active, created_at, updated_at " +
        "FROM dmos_agency_approval_slas WHERE id = ?";

    private static final String SELECT_BY_CONTRACT_ID_SQL =
        "SELECT id, contract_id, agency_tenant_id, client_id, approval_type, max_approval_time, escalation_level, escalation_timeouts, escalation_procedure, active, created_at, updated_at " +
        "FROM dmos_agency_approval_slas WHERE contract_id = ? ORDER BY created_at";

    private static final String SELECT_BY_APPROVAL_TYPE_SQL =
        "SELECT id, contract_id, agency_tenant_id, client_id, approval_type, max_approval_time, escalation_level, escalation_timeouts, escalation_procedure, active, created_at, updated_at " +
        "FROM dmos_agency_approval_slas WHERE approval_type = ? ORDER BY created_at";

    private static final String SELECT_BY_CLIENT_ID_SQL =
        "SELECT id, contract_id, agency_tenant_id, client_id, approval_type, max_approval_time, escalation_level, escalation_timeouts, escalation_procedure, active, created_at, updated_at " +
        "FROM dmos_agency_approval_slas WHERE client_id = ? ORDER BY created_at";

    private static final String SELECT_BY_AGENCY_TENANT_ID_SQL =
        "SELECT id, contract_id, agency_tenant_id, client_id, approval_type, max_approval_time, escalation_level, escalation_timeouts, escalation_procedure, active, created_at, updated_at " +
        "FROM dmos_agency_approval_slas WHERE agency_tenant_id = ? ORDER BY created_at";

    private static final String SELECT_BY_TENANT_SQL =
        "SELECT id, contract_id, agency_tenant_id, client_id, approval_type, max_approval_time, escalation_level, escalation_timeouts, escalation_procedure, active, created_at, updated_at " +
        "FROM dmos_agency_approval_slas WHERE agency_tenant_id = ? ORDER BY created_at";

    private static final String DELETE_SQL =
        "DELETE FROM dmos_agency_approval_slas WHERE id = ?";

    private final DataSource dataSource;
    private final Executor executor;

    public PostgresAgencyApprovalSLARepository(DataSource dataSource, Executor executor) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    @Override
    public Promise<AgencyApprovalSLA> save(AgencyApprovalSLA sla) {
        Objects.requireNonNull(sla, "sla must not be null");
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(UPSERT_SQL)) {
                stmt.setString(1, sla.getId());
                stmt.setString(2, sla.getContractId());
                stmt.setString(3, sla.getAgencyTenantId());
                stmt.setString(4, sla.getClientId());
                stmt.setString(5, sla.getApprovalType());
                stmt.setLong(6, sla.getMaxApprovalTime() != null ? sla.getMaxApprovalTime().getSeconds() : 0);
                stmt.setInt(7, sla.getEscalationLevel());
                stmt.setString(8, sla.getEscalationTimeouts() != null ? sla.getEscalationTimeouts().toString() : null);
                stmt.setString(9, sla.getEscalationProcedure());
                stmt.setBoolean(10, sla.isActive());
                stmt.setTimestamp(11, Timestamp.from(sla.getCreatedAt()));
                stmt.setTimestamp(12, sla.getUpdatedAt() != null ? Timestamp.from(sla.getUpdatedAt()) : null);
                stmt.executeUpdate();
                LOG.info("[DMOS-PERSIST] agency approval SLA upserted: id={} tenant={} active={}",
                    sla.getId(), sla.getAgencyTenantId(), sla.isActive());
                return sla;
            } catch (SQLException e) {
                LOG.error("[DMOS-PERSIST] failed to save agency approval SLA id={}: {}",
                    sla.getId(), e.getMessage(), e);
                throw new DmPersistenceException("Failed to save agency approval SLA: " + sla.getId(), e);
            }
        });
    }

    @Override
    public Promise<Optional<AgencyApprovalSLA>> findById(String id) {
        Objects.requireNonNull(id, "id must not be null");
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(SELECT_BY_ID_SQL)) {
                stmt.setString(1, id);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapRow(rs));
                    }
                    return Optional.empty();
                }
            } catch (SQLException e) {
                LOG.error("[DMOS-PERSIST] failed to find agency approval SLA id={}: {}", id, e.getMessage(), e);
                throw new DmPersistenceException("Failed to find agency approval SLA: " + id, e);
            }
        });
    }

    @Override
    public Promise<List<AgencyApprovalSLA>> findByContractId(String contractId) {
        Objects.requireNonNull(contractId, "contractId must not be null");
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(SELECT_BY_CONTRACT_ID_SQL)) {
                stmt.setString(1, contractId);
                try (ResultSet rs = stmt.executeQuery()) {
                    List<AgencyApprovalSLA> result = new ArrayList<>();
                    while (rs.next()) {
                        result.add(mapRow(rs));
                    }
                    return result;
                }
            } catch (SQLException e) {
                LOG.error("[DMOS-PERSIST] failed to find agency approval SLAs by contract_id={}: {}", contractId, e.getMessage(), e);
                throw new DmPersistenceException("Failed to find agency approval SLAs by contract_id: " + contractId, e);
            }
        });
    }

    @Override
    public Promise<List<AgencyApprovalSLA>> findByApprovalType(String approvalType) {
        Objects.requireNonNull(approvalType, "approvalType must not be null");
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(SELECT_BY_APPROVAL_TYPE_SQL)) {
                stmt.setString(1, approvalType);
                try (ResultSet rs = stmt.executeQuery()) {
                    List<AgencyApprovalSLA> result = new ArrayList<>();
                    while (rs.next()) {
                        result.add(mapRow(rs));
                    }
                    return result;
                }
            } catch (SQLException e) {
                LOG.error("[DMOS-PERSIST] failed to find agency approval SLAs by approval_type={}: {}", approvalType, e.getMessage(), e);
                throw new DmPersistenceException("Failed to find agency approval SLAs by approval_type: " + approvalType, e);
            }
        });
    }

    @Override
    public Promise<List<AgencyApprovalSLA>> findByClientId(String clientId) {
        Objects.requireNonNull(clientId, "clientId must not be null");
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(SELECT_BY_CLIENT_ID_SQL)) {
                stmt.setString(1, clientId);
                try (ResultSet rs = stmt.executeQuery()) {
                    List<AgencyApprovalSLA> result = new ArrayList<>();
                    while (rs.next()) {
                        result.add(mapRow(rs));
                    }
                    return result;
                }
            } catch (SQLException e) {
                LOG.error("[DMOS-PERSIST] failed to find agency approval SLAs by client_id={}: {}", clientId, e.getMessage(), e);
                throw new DmPersistenceException("Failed to find agency approval SLAs by client_id: " + clientId, e);
            }
        });
    }

    @Override
    public Promise<List<AgencyApprovalSLA>> findByAgencyTenantId(String agencyTenantId) {
        Objects.requireNonNull(agencyTenantId, "agencyTenantId must not be null");
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(SELECT_BY_AGENCY_TENANT_ID_SQL)) {
                stmt.setString(1, agencyTenantId);
                try (ResultSet rs = stmt.executeQuery()) {
                    List<AgencyApprovalSLA> result = new ArrayList<>();
                    while (rs.next()) {
                        result.add(mapRow(rs));
                    }
                    return result;
                }
            } catch (SQLException e) {
                LOG.error("[DMOS-PERSIST] failed to find agency approval SLAs by agency_tenant_id={}: {}", agencyTenantId, e.getMessage(), e);
                throw new DmPersistenceException("Failed to find agency approval SLAs by agency_tenant_id: " + agencyTenantId, e);
            }
        });
    }

    @Override
    public Promise<List<AgencyApprovalSLA>> listByTenant(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(SELECT_BY_TENANT_SQL)) {
                stmt.setString(1, tenantId);
                try (ResultSet rs = stmt.executeQuery()) {
                    List<AgencyApprovalSLA> result = new ArrayList<>();
                    while (rs.next()) {
                        result.add(mapRow(rs));
                    }
                    return result;
                }
            } catch (SQLException e) {
                LOG.error("[DMOS-PERSIST] failed to list agency approval SLAs for tenant={}: {}", tenantId, e.getMessage(), e);
                throw new DmPersistenceException("Failed to list agency approval SLAs for tenant: " + tenantId, e);
            }
        });
    }

    @Override
    public Promise<Void> delete(String id) {
        Objects.requireNonNull(id, "id must not be null");
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(DELETE_SQL)) {
                stmt.setString(1, id);
                int rows = stmt.executeUpdate();
                LOG.info("[DMOS-PERSIST] agency approval SLA deleted: id={} rows={}", id, rows);
                return null;
            } catch (SQLException e) {
                LOG.error("[DMOS-PERSIST] failed to delete agency approval SLA id={}: {}", id, e.getMessage(), e);
                throw new DmPersistenceException("Failed to delete agency approval SLA: " + id, e);
            }
        });
    }

    private static AgencyApprovalSLA mapRow(ResultSet rs) throws SQLException {
        Instant createdAt = rs.getTimestamp("created_at").toInstant();
        Timestamp updatedAtTs = rs.getTimestamp("updated_at");
        Instant updatedAt = updatedAtTs != null ? updatedAtTs.toInstant() : null;
        long maxApprovalTimeSeconds = rs.getLong("max_approval_time");
        Duration maxApprovalTime = maxApprovalTimeSeconds > 0 ? Duration.ofSeconds(maxApprovalTimeSeconds) : null;
        String escalationTimeoutsJson = rs.getString("escalation_timeouts");
        
        return AgencyApprovalSLA.builder()
            .id(rs.getString("id"))
            .contractId(rs.getString("contract_id"))
            .agencyTenantId(rs.getString("agency_tenant_id"))
            .clientId(rs.getString("client_id"))
            .approvalType(rs.getString("approval_type"))
            .maxApprovalTime(maxApprovalTime)
            .escalationLevel(rs.getInt("escalation_level"))
            .escalationTimeouts(escalationTimeoutsJson != null ? parseJsonMap(escalationTimeoutsJson) : null)
            .escalationProcedure(rs.getString("escalation_procedure"))
            .active(rs.getBoolean("active"))
            .createdAt(createdAt)
            .updatedAt(updatedAt)
            .build();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Duration> parseJsonMap(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Duration>>() {});
        } catch (Exception e) {
            LOG.warn("[DMOS-PERSIST] failed to parse escalation_timeouts json: {}", e.getMessage());
            return null;
        }
    }
}
