package com.ghatana.digitalmarketing.persistence.agency;

import com.ghatana.digitalmarketing.application.agency.AgencyClientRepository;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.agency.AgencyClient;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * PostgreSQL adapter for AgencyClientRepository (DMOS-P3-003).
 *
 * @doc.type class
 * @doc.purpose PostgreSQL persistence adapter for agency clients
 * @doc.layer persistence
 * @doc.pattern Repository
 */
public final class PostgresAgencyClientRepository implements AgencyClientRepository {

    private static final Logger logger = LoggerFactory.getLogger(PostgresAgencyClientRepository.class);

    private final DataSource dataSource;
    private final Executor executor;

    public PostgresAgencyClientRepository(DataSource dataSource, Executor executor) {
        this.dataSource = dataSource;
        this.executor = executor;
    }

    @Override
    public Promise<AgencyClient> save(AgencyClient client) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                INSERT INTO dmos_agency_clients
                (client_id, tenant_id, workspace_id, client_name, contact_email, contact_phone, branding_theme, active, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (workspace_id) DO UPDATE SET
                client_name = EXCLUDED.client_name,
                contact_email = EXCLUDED.contact_email,
                contact_phone = EXCLUDED.contact_phone,
                branding_theme = EXCLUDED.branding_theme,
                active = EXCLUDED.active,
                updated_at = EXCLUDED.updated_at
                """;

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, client.getClientId());
                stmt.setString(2, client.getTenantId().getValue());
                stmt.setString(3, client.getWorkspaceId().getValue());
                stmt.setString(4, client.getClientName());
                stmt.setString(5, client.getContactEmail());
                stmt.setString(6, client.getContactPhone());
                stmt.setString(7, client.getBrandingTheme());
                stmt.setBoolean(8, client.isActive());
                stmt.setTimestamp(9, Timestamp.from(client.getCreatedAt()));
                stmt.setTimestamp(10, Timestamp.from(client.getUpdatedAt()));

                stmt.executeUpdate();
                logger.info("Agency client saved: {}", client.getClientId());
                return client;
            } catch (SQLException e) {
                logger.error("Failed to save agency client", e);
                throw new RuntimeException("Failed to save agency client", e);
            }
        });
    }

    @Override
    public Promise<Optional<AgencyClient>> findById(String clientId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT * FROM dmos_agency_clients WHERE client_id = ?";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, clientId);
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            } catch (SQLException e) {
                logger.error("Failed to find agency client by ID", e);
                throw new RuntimeException("Failed to find agency client", e);
            }
        });
    }

    @Override
    public Promise<List<AgencyClient>> findByTenant(DmTenantId tenantId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT * FROM dmos_agency_clients WHERE tenant_id = ? ORDER BY created_at DESC";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, tenantId.getValue());
                ResultSet rs = stmt.executeQuery();

                List<AgencyClient> clients = new ArrayList<>();
                while (rs.next()) {
                    clients.add(mapRow(rs));
                }
                return clients;
            } catch (SQLException e) {
                logger.error("Failed to find agency clients by tenant", e);
                throw new RuntimeException("Failed to find agency clients", e);
            }
        });
    }

    @Override
    public Promise<List<AgencyClient>> findActiveByTenant(DmTenantId tenantId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT * FROM dmos_agency_clients WHERE tenant_id = ? AND active = true ORDER BY created_at DESC";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, tenantId.getValue());
                ResultSet rs = stmt.executeQuery();

                List<AgencyClient> clients = new ArrayList<>();
                while (rs.next()) {
                    clients.add(mapRow(rs));
                }
                return clients;
            } catch (SQLException e) {
                logger.error("Failed to find active agency clients by tenant", e);
                throw new RuntimeException("Failed to find active agency clients", e);
            }
        });
    }

    @Override
    public Promise<Optional<AgencyClient>> findByWorkspace(DmWorkspaceId workspaceId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT * FROM dmos_agency_clients WHERE workspace_id = ?";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, workspaceId.getValue());
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            } catch (SQLException e) {
                logger.error("Failed to find agency client by workspace", e);
                throw new RuntimeException("Failed to find agency client", e);
            }
        });
    }

    @Override
    public Promise<AgencyClient> update(AgencyClient client) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                UPDATE dmos_agency_clients
                SET client_name = ?, contact_email = ?, contact_phone = ?, branding_theme = ?, active = ?, updated_at = ?
                WHERE client_id = ?
                """;

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, client.getClientName());
                stmt.setString(2, client.getContactEmail());
                stmt.setString(3, client.getContactPhone());
                stmt.setString(4, client.getBrandingTheme());
                stmt.setBoolean(5, client.isActive());
                stmt.setTimestamp(6, Timestamp.from(Instant.now()));
                stmt.setString(7, client.getClientId());

                stmt.executeUpdate();
                logger.info("Agency client updated: {}", client.getClientId());
                return client;
            } catch (SQLException e) {
                logger.error("Failed to update agency client", e);
                throw new RuntimeException("Failed to update agency client", e);
            }
        });
    }

    @Override
    public Promise<Void> delete(String clientId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "DELETE FROM dmos_agency_clients WHERE client_id = ?";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, clientId);
                stmt.executeUpdate();
                logger.info("Agency client deleted: {}", clientId);
                return null;
            } catch (SQLException e) {
                logger.error("Failed to delete agency client", e);
                throw new RuntimeException("Failed to delete agency client", e);
            }
        });
    }

    private AgencyClient mapRow(ResultSet rs) throws SQLException {
        return AgencyClient.builder()
            .clientId(rs.getString("client_id"))
            .tenantId(DmTenantId.of(rs.getString("tenant_id")))
            .workspaceId(DmWorkspaceId.of(rs.getString("workspace_id")))
            .clientName(rs.getString("client_name"))
            .contactEmail(rs.getString("contact_email"))
            .contactPhone(rs.getString("contact_phone"))
            .brandingTheme(rs.getString("branding_theme"))
            .active(rs.getBoolean("active"))
            .createdAt(rs.getTimestamp("created_at").toInstant())
            .updatedAt(rs.getTimestamp("updated_at").toInstant())
            .build();
    }
}
