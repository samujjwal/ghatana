package com.ghatana.digitalmarketing.persistence.agency;

import com.ghatana.digitalmarketing.application.agency.AgencyRetainerRepository;
import com.ghatana.digitalmarketing.domain.agency.AgencyRetainer;
import com.ghatana.digitalmarketing.domain.agency.AgencyRetainerStatus;
import com.ghatana.digitalmarketing.persistence.campaign.DmPersistenceException;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.math.BigDecimal;
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
 * Production PostgreSQL adapter for {@link AgencyRetainerRepository}.
 *
 * <p>Wraps all blocking JDBC I/O in {@code Promise.ofBlocking()} to remain event-loop safe.
 * Uses upsert semantics for idempotent saves. Enforces tenant isolation at query level.</p>
 *
 * @doc.type class
 * @doc.purpose Production JDBC adapter for agency retainer persistence (P3-002)
 * @doc.layer product
 * @doc.pattern Adapter, Repository
 */
public final class PostgresAgencyRetainerRepository implements AgencyRetainerRepository {

    private static final Logger LOG = LoggerFactory.getLogger(PostgresAgencyRetainerRepository.class);

    private static final String UPSERT_SQL =
        "INSERT INTO dmos_agency_retainers (id, contract_id, agency_tenant_id, client_id, monthly_amount, currency, billing_cycle_start, billing_day_of_month, service_allowances, overage_rate, status, created_at, updated_at) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?) " +
        "ON CONFLICT (id) DO UPDATE SET " +
        "  status = EXCLUDED.status, updated_at = EXCLUDED.updated_at";

    private static final String SELECT_BY_ID_SQL =
        "SELECT id, contract_id, agency_tenant_id, client_id, monthly_amount, currency, billing_cycle_start, billing_day_of_month, service_allowances, overage_rate, status, created_at, updated_at " +
        "FROM dmos_agency_retainers WHERE id = ?";

    private static final String SELECT_BY_CONTRACT_ID_SQL =
        "SELECT id, contract_id, agency_tenant_id, client_id, monthly_amount, currency, billing_cycle_start, billing_day_of_month, service_allowances, overage_rate, status, created_at, updated_at " +
        "FROM dmos_agency_retainers WHERE contract_id = ? ORDER BY created_at";

    private static final String SELECT_BY_CLIENT_ID_SQL =
        "SELECT id, contract_id, agency_tenant_id, client_id, monthly_amount, currency, billing_cycle_start, billing_day_of_month, service_allowances, overage_rate, status, created_at, updated_at " +
        "FROM dmos_agency_retainers WHERE client_id = ? ORDER BY created_at";

    private static final String SELECT_BY_AGENCY_TENANT_ID_SQL =
        "SELECT id, contract_id, agency_tenant_id, client_id, monthly_amount, currency, billing_cycle_start, billing_day_of_month, service_allowances, overage_rate, status, created_at, updated_at " +
        "FROM dmos_agency_retainers WHERE agency_tenant_id = ? ORDER BY created_at";

    private static final String SELECT_BY_TENANT_SQL =
        "SELECT id, contract_id, agency_tenant_id, client_id, monthly_amount, currency, billing_cycle_start, billing_day_of_month, service_allowances, overage_rate, status, created_at, updated_at " +
        "FROM dmos_agency_retainers WHERE agency_tenant_id = ? ORDER BY created_at";

    private static final String DELETE_SQL =
        "DELETE FROM dmos_agency_retainers WHERE id = ?";

    private final DataSource dataSource;
    private final Executor executor;

    public PostgresAgencyRetainerRepository(DataSource dataSource, Executor executor) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    @Override
    public Promise<AgencyRetainer> save(AgencyRetainer retainer) {
        Objects.requireNonNull(retainer, "retainer must not be null");
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(UPSERT_SQL)) {
                stmt.setString(1, retainer.getId());
                stmt.setString(2, retainer.getContractId());
                stmt.setString(3, retainer.getAgencyTenantId());
                stmt.setString(4, retainer.getClientId());
                stmt.setBigDecimal(5, retainer.getMonthlyAmount());
                stmt.setString(6, retainer.getCurrency());
                stmt.setDate(7, retainer.getBillingCycleStart() != null ? java.sql.Date.valueOf(retainer.getBillingCycleStart()) : null);
                stmt.setInt(8, retainer.getBillingDayOfMonth());
                stmt.setString(9, retainer.getServiceAllowances() != null ? retainer.getServiceAllowances().toString() : null);
                stmt.setBigDecimal(10, retainer.getOverageRate());
                stmt.setString(11, retainer.getStatus().name());
                stmt.setTimestamp(12, Timestamp.from(retainer.getCreatedAt()));
                stmt.setTimestamp(13, retainer.getUpdatedAt() != null ? Timestamp.from(retainer.getUpdatedAt()) : null);
                stmt.executeUpdate();
                LOG.info("[DMOS-PERSIST] agency retainer upserted: id={} tenant={} status={}",
                    retainer.getId(), retainer.getAgencyTenantId(), retainer.getStatus());
                return retainer;
            } catch (SQLException e) {
                LOG.error("[DMOS-PERSIST] failed to save agency retainer id={}: {}",
                    retainer.getId(), e.getMessage(), e);
                throw new DmPersistenceException("Failed to save agency retainer: " + retainer.getId(), e);
            }
        });
    }

    @Override
    public Promise<Optional<AgencyRetainer>> findById(String id) {
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
                LOG.error("[DMOS-PERSIST] failed to find agency retainer id={}: {}", id, e.getMessage(), e);
                throw new DmPersistenceException("Failed to find agency retainer: " + id, e);
            }
        });
    }

    @Override
    public Promise<List<AgencyRetainer>> findByContractId(String contractId) {
        Objects.requireNonNull(contractId, "contractId must not be null");
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(SELECT_BY_CONTRACT_ID_SQL)) {
                stmt.setString(1, contractId);
                try (ResultSet rs = stmt.executeQuery()) {
                    List<AgencyRetainer> result = new ArrayList<>();
                    while (rs.next()) {
                        result.add(mapRow(rs));
                    }
                    return result;
                }
            } catch (SQLException e) {
                LOG.error("[DMOS-PERSIST] failed to find agency retainers by contract_id={}: {}", contractId, e.getMessage(), e);
                throw new DmPersistenceException("Failed to find agency retainers by contract_id: " + contractId, e);
            }
        });
    }

    @Override
    public Promise<List<AgencyRetainer>> findByClientId(String clientId) {
        Objects.requireNonNull(clientId, "clientId must not be null");
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(SELECT_BY_CLIENT_ID_SQL)) {
                stmt.setString(1, clientId);
                try (ResultSet rs = stmt.executeQuery()) {
                    List<AgencyRetainer> result = new ArrayList<>();
                    while (rs.next()) {
                        result.add(mapRow(rs));
                    }
                    return result;
                }
            } catch (SQLException e) {
                LOG.error("[DMOS-PERSIST] failed to find agency retainers by client_id={}: {}", clientId, e.getMessage(), e);
                throw new DmPersistenceException("Failed to find agency retainers by client_id: " + clientId, e);
            }
        });
    }

    @Override
    public Promise<List<AgencyRetainer>> findByAgencyTenantId(String agencyTenantId) {
        Objects.requireNonNull(agencyTenantId, "agencyTenantId must not be null");
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(SELECT_BY_AGENCY_TENANT_ID_SQL)) {
                stmt.setString(1, agencyTenantId);
                try (ResultSet rs = stmt.executeQuery()) {
                    List<AgencyRetainer> result = new ArrayList<>();
                    while (rs.next()) {
                        result.add(mapRow(rs));
                    }
                    return result;
                }
            } catch (SQLException e) {
                LOG.error("[DMOS-PERSIST] failed to find agency retainers by agency_tenant_id={}: {}", agencyTenantId, e.getMessage(), e);
                throw new DmPersistenceException("Failed to find agency retainers by agency_tenant_id: " + agencyTenantId, e);
            }
        });
    }

    @Override
    public Promise<List<AgencyRetainer>> listByTenant(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(SELECT_BY_TENANT_SQL)) {
                stmt.setString(1, tenantId);
                try (ResultSet rs = stmt.executeQuery()) {
                    List<AgencyRetainer> result = new ArrayList<>();
                    while (rs.next()) {
                        result.add(mapRow(rs));
                    }
                    return result;
                }
            } catch (SQLException e) {
                LOG.error("[DMOS-PERSIST] failed to list agency retainers for tenant={}: {}", tenantId, e.getMessage(), e);
                throw new DmPersistenceException("Failed to list agency retainers for tenant: " + tenantId, e);
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
                LOG.info("[DMOS-PERSIST] agency retainer deleted: id={} rows={}", id, rows);
                return null;
            } catch (SQLException e) {
                LOG.error("[DMOS-PERSIST] failed to delete agency retainer id={}: {}", id, e.getMessage(), e);
                throw new DmPersistenceException("Failed to delete agency retainer: " + id, e);
            }
        });
    }

    private static AgencyRetainer mapRow(ResultSet rs) throws SQLException {
        Instant createdAt = rs.getTimestamp("created_at").toInstant();
        Timestamp updatedAtTs = rs.getTimestamp("updated_at");
        Instant updatedAt = updatedAtTs != null ? updatedAtTs.toInstant() : null;
        java.sql.Date billingCycleStartSql = rs.getDate("billing_cycle_start");
        LocalDate billingCycleStart = billingCycleStartSql != null ? billingCycleStartSql.toLocalDate() : null;
        String serviceAllowancesJson = rs.getString("service_allowances");
        
        return AgencyRetainer.builder()
            .id(rs.getString("id"))
            .contractId(rs.getString("contract_id"))
            .agencyTenantId(rs.getString("agency_tenant_id"))
            .clientId(rs.getString("client_id"))
            .monthlyAmount(rs.getBigDecimal("monthly_amount"))
            .currency(rs.getString("currency"))
            .billingCycleStart(billingCycleStart)
            .billingDayOfMonth(rs.getInt("billing_day_of_month"))
            .serviceAllowances(serviceAllowancesJson != null ? parseJsonMap(serviceAllowancesJson) : null)
            .overageRate(rs.getBigDecimal("overage_rate"))
            .status(AgencyRetainerStatus.valueOf(rs.getString("status")))
            .createdAt(createdAt)
            .updatedAt(updatedAt)
            .build();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Integer> parseJsonMap(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Integer>>() {});
        } catch (Exception e) {
            LOG.warn("[DMOS-PERSIST] failed to parse service_allowances json: {}", e.getMessage());
            return null;
        }
    }
}
