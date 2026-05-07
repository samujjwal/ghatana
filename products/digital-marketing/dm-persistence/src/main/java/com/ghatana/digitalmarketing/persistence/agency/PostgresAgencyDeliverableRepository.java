package com.ghatana.digitalmarketing.persistence.agency;

import com.ghatana.digitalmarketing.application.agency.AgencyDeliverableRepository;
import com.ghatana.digitalmarketing.domain.agency.AgencyDeliverable;
import com.ghatana.digitalmarketing.domain.agency.AgencyDeliverableStatus;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * Production PostgreSQL adapter for {@link AgencyDeliverableRepository}.
 *
 * <p>Wraps all blocking JDBC I/O in {@code Promise.ofBlocking()} to remain event-loop safe.
 * Uses upsert semantics for idempotent saves. Enforces tenant isolation at query level.</p>
 *
 * @doc.type class
 * @doc.purpose Production JDBC adapter for agency deliverable persistence (P3-002)
 * @doc.layer product
 * @doc.pattern Adapter, Repository
 */
public final class PostgresAgencyDeliverableRepository implements AgencyDeliverableRepository {

    private static final Logger LOG = LoggerFactory.getLogger(PostgresAgencyDeliverableRepository.class);

    private static final String UPSERT_SQL =
        "INSERT INTO dmos_agency_deliverables (id, contract_id, agency_tenant_id, client_id, deliverable_type, title, description, due_date, completed_date, assigned_to, metadata, status, created_at, updated_at) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?) " +
        "ON CONFLICT (id) DO UPDATE SET " +
        "  status = EXCLUDED.status, completed_date = EXCLUDED.completed_date, updated_at = EXCLUDED.updated_at";

    private static final String SELECT_BY_ID_SQL =
        "SELECT id, contract_id, agency_tenant_id, client_id, deliverable_type, title, description, due_date, completed_date, assigned_to, metadata, status, created_at, updated_at " +
        "FROM dmos_agency_deliverables WHERE id = ?";

    private static final String SELECT_BY_CONTRACT_ID_SQL =
        "SELECT id, contract_id, agency_tenant_id, client_id, deliverable_type, title, description, due_date, completed_date, assigned_to, metadata, status, created_at, updated_at " +
        "FROM dmos_agency_deliverables WHERE contract_id = ? ORDER BY due_date";

    private static final String SELECT_BY_ASSIGNED_TO_SQL =
        "SELECT id, contract_id, agency_tenant_id, client_id, deliverable_type, title, description, due_date, completed_date, assigned_to, metadata, status, created_at, updated_at " +
        "FROM dmos_agency_deliverables WHERE assigned_to = ? ORDER BY due_date";

    private static final String SELECT_BY_CLIENT_ID_SQL =
        "SELECT id, contract_id, agency_tenant_id, client_id, deliverable_type, title, description, due_date, completed_date, assigned_to, metadata, status, created_at, updated_at " +
        "FROM dmos_agency_deliverables WHERE client_id = ? ORDER BY due_date";

    private static final String SELECT_OVERDUE_BY_CLIENT_ID_SQL =
        "SELECT id, contract_id, agency_tenant_id, client_id, deliverable_type, title, description, due_date, completed_date, assigned_to, metadata, status, created_at, updated_at " +
        "FROM dmos_agency_deliverables WHERE client_id = ? AND due_date < CURRENT_DATE AND status NOT IN ('COMPLETED', 'CANCELLED') ORDER BY due_date";

    private static final String SELECT_BY_AGENCY_TENANT_ID_SQL =
        "SELECT id, contract_id, agency_tenant_id, client_id, deliverable_type, title, description, due_date, completed_date, assigned_to, metadata, status, created_at, updated_at " +
        "FROM dmos_agency_deliverables WHERE agency_tenant_id = ? ORDER BY due_date";

    private static final String SELECT_BY_TENANT_SQL =
        "SELECT id, contract_id, agency_tenant_id, client_id, deliverable_type, title, description, due_date, completed_date, assigned_to, metadata, status, created_at, updated_at " +
        "FROM dmos_agency_deliverables WHERE agency_tenant_id = ? ORDER BY due_date";

    private static final String DELETE_SQL =
        "DELETE FROM dmos_agency_deliverables WHERE id = ?";

    private final DataSource dataSource;
    private final Executor executor;

    public PostgresAgencyDeliverableRepository(DataSource dataSource, Executor executor) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    @Override
    public Promise<AgencyDeliverable> save(AgencyDeliverable deliverable) {
        Objects.requireNonNull(deliverable, "deliverable must not be null");
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(UPSERT_SQL)) {
                stmt.setString(1, deliverable.getId());
                stmt.setString(2, deliverable.getContractId());
                stmt.setString(3, deliverable.getAgencyTenantId());
                stmt.setString(4, deliverable.getClientId());
                stmt.setString(5, deliverable.getDeliverableType());
                stmt.setString(6, deliverable.getTitle());
                stmt.setString(7, deliverable.getDescription());
                stmt.setDate(8, deliverable.getDueDate() != null ? java.sql.Date.valueOf(deliverable.getDueDate()) : null);
                stmt.setDate(9, deliverable.getCompletedDate() != null ? java.sql.Date.valueOf(deliverable.getCompletedDate()) : null);
                stmt.setString(10, deliverable.getAssignedTo());
                stmt.setString(11, deliverable.getMetadata() != null ? deliverable.getMetadata().toString() : null);
                stmt.setString(12, deliverable.getStatus().name());
                stmt.setTimestamp(13, Timestamp.from(deliverable.getCreatedAt()));
                stmt.setTimestamp(14, deliverable.getUpdatedAt() != null ? Timestamp.from(deliverable.getUpdatedAt()) : null);
                stmt.executeUpdate();
                LOG.info("[DMOS-PERSIST] agency deliverable upserted: id={} tenant={} status={}",
                    deliverable.getId(), deliverable.getAgencyTenantId(), deliverable.getStatus());
                return deliverable;
            } catch (SQLException e) {
                LOG.error("[DMOS-PERSIST] failed to save agency deliverable id={}: {}",
                    deliverable.getId(), e.getMessage(), e);
                throw new DmPersistenceException("Failed to save agency deliverable: " + deliverable.getId(), e);
            }
        });
    }

    @Override
    public Promise<Optional<AgencyDeliverable>> findById(String id) {
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
                LOG.error("[DMOS-PERSIST] failed to find agency deliverable id={}: {}", id, e.getMessage(), e);
                throw new DmPersistenceException("Failed to find agency deliverable: " + id, e);
            }
        });
    }

    @Override
    public Promise<List<AgencyDeliverable>> findByContractId(String contractId) {
        Objects.requireNonNull(contractId, "contractId must not be null");
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(SELECT_BY_CONTRACT_ID_SQL)) {
                stmt.setString(1, contractId);
                try (ResultSet rs = stmt.executeQuery()) {
                    List<AgencyDeliverable> result = new ArrayList<>();
                    while (rs.next()) {
                        result.add(mapRow(rs));
                    }
                    return result;
                }
            } catch (SQLException e) {
                LOG.error("[DMOS-PERSIST] failed to find agency deliverables by contract_id={}: {}", contractId, e.getMessage(), e);
                throw new DmPersistenceException("Failed to find agency deliverables by contract_id: " + contractId, e);
            }
        });
    }

    @Override
    public Promise<List<AgencyDeliverable>> findByAssignedTo(String assignedTo) {
        Objects.requireNonNull(assignedTo, "assignedTo must not be null");
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(SELECT_BY_ASSIGNED_TO_SQL)) {
                stmt.setString(1, assignedTo);
                try (ResultSet rs = stmt.executeQuery()) {
                    List<AgencyDeliverable> result = new ArrayList<>();
                    while (rs.next()) {
                        result.add(mapRow(rs));
                    }
                    return result;
                }
            } catch (SQLException e) {
                LOG.error("[DMOS-PERSIST] failed to find agency deliverables by assigned_to={}: {}", assignedTo, e.getMessage(), e);
                throw new DmPersistenceException("Failed to find agency deliverables by assigned_to: " + assignedTo, e);
            }
        });
    }

    @Override
    public Promise<List<AgencyDeliverable>> findByClientId(String clientId) {
        Objects.requireNonNull(clientId, "clientId must not be null");
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(SELECT_BY_CLIENT_ID_SQL)) {
                stmt.setString(1, clientId);
                try (ResultSet rs = stmt.executeQuery()) {
                    List<AgencyDeliverable> result = new ArrayList<>();
                    while (rs.next()) {
                        result.add(mapRow(rs));
                    }
                    return result;
                }
            } catch (SQLException e) {
                LOG.error("[DMOS-PERSIST] failed to find agency deliverables by client_id={}: {}", clientId, e.getMessage(), e);
                throw new DmPersistenceException("Failed to find agency deliverables by client_id: " + clientId, e);
            }
        });
    }

    @Override
    public Promise<List<AgencyDeliverable>> findOverdueByClientId(String clientId) {
        Objects.requireNonNull(clientId, "clientId must not be null");
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(SELECT_OVERDUE_BY_CLIENT_ID_SQL)) {
                stmt.setString(1, clientId);
                try (ResultSet rs = stmt.executeQuery()) {
                    List<AgencyDeliverable> result = new ArrayList<>();
                    while (rs.next()) {
                        result.add(mapRow(rs));
                    }
                    return result;
                }
            } catch (SQLException e) {
                LOG.error("[DMOS-PERSIST] failed to find overdue agency deliverables by client_id={}: {}", clientId, e.getMessage(), e);
                throw new DmPersistenceException("Failed to find overdue agency deliverables by client_id: " + clientId, e);
            }
        });
    }

    @Override
    public Promise<List<AgencyDeliverable>> findByAgencyTenantId(String agencyTenantId) {
        Objects.requireNonNull(agencyTenantId, "agencyTenantId must not be null");
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(SELECT_BY_AGENCY_TENANT_ID_SQL)) {
                stmt.setString(1, agencyTenantId);
                try (ResultSet rs = stmt.executeQuery()) {
                    List<AgencyDeliverable> result = new ArrayList<>();
                    while (rs.next()) {
                        result.add(mapRow(rs));
                    }
                    return result;
                }
            } catch (SQLException e) {
                LOG.error("[DMOS-PERSIST] failed to find agency deliverables by agency_tenant_id={}: {}", agencyTenantId, e.getMessage(), e);
                throw new DmPersistenceException("Failed to find agency deliverables by agency_tenant_id: " + agencyTenantId, e);
            }
        });
    }

    @Override
    public Promise<List<AgencyDeliverable>> listByTenant(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(SELECT_BY_TENANT_SQL)) {
                stmt.setString(1, tenantId);
                try (ResultSet rs = stmt.executeQuery()) {
                    List<AgencyDeliverable> result = new ArrayList<>();
                    while (rs.next()) {
                        result.add(mapRow(rs));
                    }
                    return result;
                }
            } catch (SQLException e) {
                LOG.error("[DMOS-PERSIST] failed to list agency deliverables for tenant={}: {}", tenantId, e.getMessage(), e);
                throw new DmPersistenceException("Failed to list agency deliverables for tenant: " + tenantId, e);
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
                LOG.info("[DMOS-PERSIST] agency deliverable deleted: id={} rows={}", id, rows);
                return null;
            } catch (SQLException e) {
                LOG.error("[DMOS-PERSIST] failed to delete agency deliverable id={}: {}", id, e.getMessage(), e);
                throw new DmPersistenceException("Failed to delete agency deliverable: " + id, e);
            }
        });
    }

    private static AgencyDeliverable mapRow(ResultSet rs) throws SQLException {
        Instant createdAt = rs.getTimestamp("created_at").toInstant();
        Timestamp updatedAtTs = rs.getTimestamp("updated_at");
        Instant updatedAt = updatedAtTs != null ? updatedAtTs.toInstant() : null;
        java.sql.Date dueDateSql = rs.getDate("due_date");
        LocalDate dueDate = dueDateSql != null ? dueDateSql.toLocalDate() : null;
        java.sql.Date completedDateSql = rs.getDate("completed_date");
        LocalDate completedDate = completedDateSql != null ? completedDateSql.toLocalDate() : null;
        String metadataJson = rs.getString("metadata");
        
        return AgencyDeliverable.builder()
            .id(rs.getString("id"))
            .contractId(rs.getString("contract_id"))
            .agencyTenantId(rs.getString("agency_tenant_id"))
            .clientId(rs.getString("client_id"))
            .deliverableType(rs.getString("deliverable_type"))
            .title(rs.getString("title"))
            .description(rs.getString("description"))
            .dueDate(dueDate)
            .completedDate(completedDate)
            .assignedTo(rs.getString("assigned_to"))
            .metadata(metadataJson != null ? parseJsonMap(metadataJson) : null)
            .status(AgencyDeliverableStatus.valueOf(rs.getString("status")))
            .createdAt(createdAt)
            .updatedAt(updatedAt)
            .build();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            LOG.warn("[DMOS-PERSIST] failed to parse metadata json: {}", e.getMessage());
            return null;
        }
    }
}
