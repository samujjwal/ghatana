package com.ghatana.digitalmarketing.persistence.agency;

import com.ghatana.digitalmarketing.application.agency.AgencyContractRepository;
import com.ghatana.digitalmarketing.domain.agency.AgencyContract;
import com.ghatana.digitalmarketing.domain.agency.AgencyContractStatus;
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
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * Production PostgreSQL adapter for {@link AgencyContractRepository}.
 *
 * <p>Wraps all blocking JDBC I/O in {@code Promise.ofBlocking()} to remain event-loop safe.
 * Uses upsert semantics for idempotent saves. Enforces tenant isolation at query level.</p>
 *
 * @doc.type class
 * @doc.purpose Production JDBC adapter for agency contract persistence (P3-002)
 * @doc.layer product
 * @doc.pattern Adapter, Repository
 */
public final class PostgresAgencyContractRepository implements AgencyContractRepository {

    private static final Logger LOG = LoggerFactory.getLogger(PostgresAgencyContractRepository.class);

    private static final String UPSERT_SQL =
        "INSERT INTO dmos_agency_contracts (id, agency_tenant_id, client_id, contract_number, contract_type, start_date, end_date, monthly_retainer, currency, status, terms, created_at, updated_at) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
        "ON CONFLICT (id) DO UPDATE SET " +
        "  status = EXCLUDED.status, end_date = EXCLUDED.end_date, updated_at = EXCLUDED.updated_at";

    private static final String SELECT_BY_ID_SQL =
        "SELECT id, agency_tenant_id, client_id, contract_number, contract_type, start_date, end_date, monthly_retainer, currency, status, terms, created_at, updated_at " +
        "FROM dmos_agency_contracts WHERE id = ?";

    private static final String SELECT_BY_CLIENT_ID_SQL =
        "SELECT id, agency_tenant_id, client_id, contract_number, contract_type, start_date, end_date, monthly_retainer, currency, status, terms, created_at, updated_at " +
        "FROM dmos_agency_contracts WHERE client_id = ? ORDER BY created_at";

    private static final String SELECT_BY_AGENCY_TENANT_ID_SQL =
        "SELECT id, agency_tenant_id, client_id, contract_number, contract_type, start_date, end_date, monthly_retainer, currency, status, terms, created_at, updated_at " +
        "FROM dmos_agency_contracts WHERE agency_tenant_id = ? ORDER BY created_at";

    private static final String SELECT_BY_TENANT_SQL =
        "SELECT id, agency_tenant_id, client_id, contract_number, contract_type, start_date, end_date, monthly_retainer, currency, status, terms, created_at, updated_at " +
        "FROM dmos_agency_contracts WHERE agency_tenant_id = ? ORDER BY created_at";

    private static final String DELETE_SQL =
        "DELETE FROM dmos_agency_contracts WHERE id = ?";

    private final DataSource dataSource;
    private final Executor executor;

    public PostgresAgencyContractRepository(DataSource dataSource, Executor executor) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    @Override
    public Promise<AgencyContract> save(AgencyContract contract) {
        Objects.requireNonNull(contract, "contract must not be null");
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(UPSERT_SQL)) {
                stmt.setString(1, contract.getId());
                stmt.setString(2, contract.getAgencyTenantId());
                stmt.setString(3, contract.getClientId());
                stmt.setString(4, contract.getContractNumber());
                stmt.setString(5, contract.getContractType());
                stmt.setDate(6, contract.getStartDate() != null ? java.sql.Date.valueOf(contract.getStartDate()) : null);
                stmt.setDate(7, contract.getEndDate() != null ? java.sql.Date.valueOf(contract.getEndDate()) : null);
                stmt.setBigDecimal(8, contract.getMonthlyRetainer());
                stmt.setString(9, contract.getCurrency());
                stmt.setString(10, contract.getStatus().name());
                stmt.setString(11, contract.getTerms());
                stmt.setTimestamp(12, Timestamp.from(contract.getCreatedAt()));
                stmt.setTimestamp(13, contract.getUpdatedAt() != null ? Timestamp.from(contract.getUpdatedAt()) : null);
                stmt.executeUpdate();
                LOG.info("[DMOS-PERSIST] agency contract upserted: id={} tenant={} status={}",
                    contract.getId(), contract.getAgencyTenantId(), contract.getStatus());
                return contract;
            } catch (SQLException e) {
                LOG.error("[DMOS-PERSIST] failed to save agency contract id={}: {}",
                    contract.getId(), e.getMessage(), e);
                throw new DmPersistenceException("Failed to save agency contract: " + contract.getId(), e);
            }
        });
    }

    @Override
    public Promise<Optional<AgencyContract>> findById(String id) {
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
                LOG.error("[DMOS-PERSIST] failed to find agency contract id={}: {}", id, e.getMessage(), e);
                throw new DmPersistenceException("Failed to find agency contract: " + id, e);
            }
        });
    }

    @Override
    public Promise<List<AgencyContract>> findByClientId(String clientId) {
        Objects.requireNonNull(clientId, "clientId must not be null");
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(SELECT_BY_CLIENT_ID_SQL)) {
                stmt.setString(1, clientId);
                try (ResultSet rs = stmt.executeQuery()) {
                    List<AgencyContract> result = new ArrayList<>();
                    while (rs.next()) {
                        result.add(mapRow(rs));
                    }
                    return result;
                }
            } catch (SQLException e) {
                LOG.error("[DMOS-PERSIST] failed to find agency contracts by client_id={}: {}", clientId, e.getMessage(), e);
                throw new DmPersistenceException("Failed to find agency contracts by client_id: " + clientId, e);
            }
        });
    }

    @Override
    public Promise<List<AgencyContract>> findByAgencyTenantId(String agencyTenantId) {
        Objects.requireNonNull(agencyTenantId, "agencyTenantId must not be null");
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(SELECT_BY_AGENCY_TENANT_ID_SQL)) {
                stmt.setString(1, agencyTenantId);
                try (ResultSet rs = stmt.executeQuery()) {
                    List<AgencyContract> result = new ArrayList<>();
                    while (rs.next()) {
                        result.add(mapRow(rs));
                    }
                    return result;
                }
            } catch (SQLException e) {
                LOG.error("[DMOS-PERSIST] failed to find agency contracts by agency_tenant_id={}: {}", agencyTenantId, e.getMessage(), e);
                throw new DmPersistenceException("Failed to find agency contracts by agency_tenant_id: " + agencyTenantId, e);
            }
        });
    }

    @Override
    public Promise<List<AgencyContract>> listByTenant(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(SELECT_BY_TENANT_SQL)) {
                stmt.setString(1, tenantId);
                try (ResultSet rs = stmt.executeQuery()) {
                    List<AgencyContract> result = new ArrayList<>();
                    while (rs.next()) {
                        result.add(mapRow(rs));
                    }
                    return result;
                }
            } catch (SQLException e) {
                LOG.error("[DMOS-PERSIST] failed to list agency contracts for tenant={}: {}", tenantId, e.getMessage(), e);
                throw new DmPersistenceException("Failed to list agency contracts for tenant: " + tenantId, e);
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
                LOG.info("[DMOS-PERSIST] agency contract deleted: id={} rows={}", id, rows);
                return null;
            } catch (SQLException e) {
                LOG.error("[DMOS-PERSIST] failed to delete agency contract id={}: {}", id, e.getMessage(), e);
                throw new DmPersistenceException("Failed to delete agency contract: " + id, e);
            }
        });
    }

    private static AgencyContract mapRow(ResultSet rs) throws SQLException {
        Instant createdAt = rs.getTimestamp("created_at").toInstant();
        Timestamp updatedAtTs = rs.getTimestamp("updated_at");
        Instant updatedAt = updatedAtTs != null ? updatedAtTs.toInstant() : null;
        java.sql.Date startDateSql = rs.getDate("start_date");
        LocalDate startDate = startDateSql != null ? startDateSql.toLocalDate() : null;
        java.sql.Date endDateSql = rs.getDate("end_date");
        LocalDate endDate = endDateSql != null ? endDateSql.toLocalDate() : null;
        
        return AgencyContract.builder()
            .id(rs.getString("id"))
            .agencyTenantId(rs.getString("agency_tenant_id"))
            .clientId(rs.getString("client_id"))
            .contractNumber(rs.getString("contract_number"))
            .contractType(rs.getString("contract_type"))
            .startDate(startDate)
            .endDate(endDate)
            .monthlyRetainer(rs.getBigDecimal("monthly_retainer"))
            .currency(rs.getString("currency"))
            .status(AgencyContractStatus.valueOf(rs.getString("status")))
            .terms(rs.getString("terms"))
            .createdAt(createdAt)
            .updatedAt(updatedAt)
            .build();
    }
}
