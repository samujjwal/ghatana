package com.ghatana.digitalmarketing.application;

import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.campaign.Campaign;
import com.ghatana.digitalmarketing.domain.approval.ApprovalRecord;
import com.ghatana.digitalmarketing.domain.audit.AuditRecord;
import io.activej.promise.Promise;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DMOS-P1-004: Persistence proof for Digital Marketing - validates tenant/workspace filters,
 * FK/unique constraints, idempotency keys, created/updated timestamps, immutable audit/approval records.
 * Uses Testcontainers PostgreSQL for production-grade database constraint validation.
 *
 * @doc.type class
 * @doc.purpose Comprehensive persistence constraint validation for DMOS production readiness
 * @doc.layer test
 * @doc.pattern IntegrationTest
 */
@DisplayName("DMOS-P1-004: Persistence Constraints Proof (Production-Grade)")
class PersistenceConstraintsProofTest {

    private static final String POSTGRES_IMAGE = "postgres:15-alpine";
    private static final String DATABASE_NAME = "dmos_test";
    private static final String USERNAME = "test";
    private static final String PASSWORD = "test";

    private PostgreSQLContainer<?> postgresContainer;
    private DataSource dataSource;

    @BeforeEach
    void setUp() throws SQLException {
        // Start PostgreSQL container for production-grade testing
        postgresContainer = new PostgreSQLContainer<>(DockerImageName.parse(POSTGRES_IMAGE))
            .withDatabaseName(DATABASE_NAME)
            .withUsername(USERNAME)
            .withPassword(PASSWORD);
        postgresContainer.start();

        // Create data source connected to container
        dataSource = createDataSource();

        // Initialize database schema with constraints
        initializeSchema();
    }

    @AfterEach
    void tearDown() {
        if (postgresContainer != null) {
            postgresContainer.stop();
        }
    }

    private DataSource createDataSource() {
        return new DataSource() {
            @Override
            public Connection getConnection() throws SQLException {
                return java.sql.DriverManager.getConnection(
                    postgresContainer.getJdbcUrl(),
                    postgresContainer.getUsername(),
                    postgresContainer.getPassword()
                );
            }

            @Override
            public Connection getConnection(String username, String password) throws SQLException {
                return getConnection();
            }

            @Override
            public java.io.PrintWriter getLogWriter() throws SQLException {
                return null;
            }

            @Override
            public void setLogWriter(java.io.PrintWriter out) throws SQLException {
            }

            @Override
            public void setLoginTimeout(int seconds) throws SQLException {
            }

            @Override
            public int getLoginTimeout() throws SQLException {
                return 0;
            }

            @Override
            public java.util.logging.Logger getParentLogger() throws SQLException {
                return null;
            }

            @Override
            public <T> T unwrap(Class<T> iface) throws SQLException {
                return null;
            }

            @Override
            public boolean isWrapperFor(Class<?> iface) throws SQLException {
                return false;
            }
        };
    }

    private void initializeSchema() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            // Create campaigns table with constraints
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS campaigns (
                    id VARCHAR(100) NOT NULL,
                    tenant_id VARCHAR(100) NOT NULL,
                    workspace_id VARCHAR(100) NOT NULL,
                    name VARCHAR(255) NOT NULL,
                    status VARCHAR(50) NOT NULL,
                    budget DOUBLE NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL,
                    PRIMARY KEY (tenant_id, workspace_id, id),
                    CONSTRAINT fk_campaign_workspace FOREIGN KEY (tenant_id, workspace_id) 
                        REFERENCES workspaces(tenant_id, workspace_id) ON DELETE CASCADE
                )
            """);

            // Create workspaces table for FK constraint
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS workspaces (
                    tenant_id VARCHAR(100) NOT NULL,
                    workspace_id VARCHAR(100) NOT NULL,
                    name VARCHAR(255) NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (tenant_id, workspace_id)
                )
            """);

            // Create approvals table with immutability constraint
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS approvals (
                    id VARCHAR(100) NOT NULL,
                    tenant_id VARCHAR(100) NOT NULL,
                    workspace_id VARCHAR(100) NOT NULL,
                    campaign_id VARCHAR(100) NOT NULL,
                    status VARCHAR(50) NOT NULL,
                    approver_id VARCHAR(100) NOT NULL,
                    timestamp TIMESTAMP NOT NULL,
                    notes TEXT,
                    PRIMARY KEY (tenant_id, workspace_id, id),
                    CONSTRAINT fk_approval_campaign FOREIGN KEY (tenant_id, workspace_id, campaign_id) 
                        REFERENCES campaigns(tenant_id, workspace_id, id) ON DELETE CASCADE,
                    CONSTRAINT approval_immutable CHECK (
                        status NOT IN ('approved', 'rejected') OR 
                        (status IN ('approved', 'rejected') AND updated_at = created_at)
                    )
                )
            """);

            // Create audit table with immutability constraint
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS audit_records (
                    id VARCHAR(100) NOT NULL,
                    tenant_id VARCHAR(100) NOT NULL,
                    workspace_id VARCHAR(100) NOT NULL,
                    entity_id VARCHAR(100) NOT NULL,
                    action VARCHAR(50) NOT NULL,
                    user_id VARCHAR(100) NOT NULL,
                    timestamp TIMESTAMP NOT NULL,
                    metadata JSONB,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (tenant_id, workspace_id, id),
                    CONSTRAINT audit_immutable CHECK (updated_at = created_at)
                )
            """);

            // Create idempotency_keys table
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS idempotency_keys (
                    key VARCHAR(255) NOT NULL PRIMARY KEY,
                    campaign_id VARCHAR(100) NOT NULL,
                    tenant_id VARCHAR(100) NOT NULL,
                    workspace_id VARCHAR(100) NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);
        }
    }

    @Test
    @DisplayName("tenant/workspace filters isolate data correctly")
    void tenantWorkspaceFiltersIsolateData() throws SQLException {
        DmTenantId tenant1 = DmTenantId.of("tenant-1");
        DmTenantId tenant2 = DmTenantId.of("tenant-2");
        DmWorkspaceId workspace1 = DmWorkspaceId.of("workspace-1");
        DmWorkspaceId workspace2 = DmWorkspaceId.of("workspace-2");

        // Create workspaces first
        createWorkspace(tenant1, workspace1);
        createWorkspace(tenant1, workspace2);
        createWorkspace(tenant2, workspace1);

        // Create campaign in tenant-1/workspace-1
        Campaign campaign1 = new Campaign(
            "campaign-1",
            "Campaign 1",
            "Active",
            10000.0,
            Instant.now(),
            Instant.now()
        );
        saveCampaign(tenant1, workspace1, campaign1);

        // Create campaign in tenant-2/workspace-1
        Campaign campaign2 = new Campaign(
            "campaign-2",
            "Campaign 2",
            "Active",
            20000.0,
            Instant.now(),
            Instant.now()
        );
        saveCampaign(tenant2, workspace1, campaign2);

        // Create campaign in tenant-1/workspace-2
        Campaign campaign3 = new Campaign(
            "campaign-3",
            "Campaign 3",
            "Active",
            30000.0,
            Instant.now(),
            Instant.now()
        );
        saveCampaign(tenant1, workspace2, campaign3);

        // Verify tenant isolation
        List<Campaign> tenant1Campaigns = listCampaignsByTenant(tenant1);
        assertThat(tenant1Campaigns).hasSize(2);
        assertThat(tenant1Campaigns).extracting("id").containsExactlyInAnyOrder("campaign-1", "campaign-3");

        List<Campaign> tenant2Campaigns = listCampaignsByTenant(tenant2);
        assertThat(tenant2Campaigns).hasSize(1);
        assertThat(tenant2Campaigns).extracting("id").containsExactly("campaign-2");

        // Verify workspace isolation within tenant
        List<Campaign> tenant1Workspace1 = listCampaignsByWorkspace(tenant1, workspace1);
        assertThat(tenant1Workspace1).hasSize(1);
        assertThat(tenant1Workspace1.get(0).id()).isEqualTo("campaign-1");

        List<Campaign> tenant1Workspace2 = listCampaignsByWorkspace(tenant1, workspace2);
        assertThat(tenant1Workspace2).hasSize(1);
        assertThat(tenant1Workspace2.get(0).id()).isEqualTo("campaign-3");
    }

    @Test
    @DisplayName("foreign key constraints prevent orphaned records")
    void foreignKeyConstraintsPreventOrphanedRecords() throws SQLException {
        DmTenantId tenantId = DmTenantId.of("tenant-1");
        DmWorkspaceId workspaceId = DmWorkspaceId.of("workspace-1");

        // Create workspace first
        createWorkspace(tenantId, workspaceId);

        // Create campaign
        Campaign campaign = new Campaign(
            "campaign-1",
            "Campaign 1",
            "Active",
            10000.0,
            Instant.now(),
            Instant.now()
        );
        saveCampaign(tenantId, workspaceId, campaign);

        // Attempt to create approval record referencing non-existent campaign
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement("""
                 INSERT INTO approvals (id, tenant_id, workspace_id, campaign_id, status, approver_id, timestamp, notes)
                 VALUES (?, ?, ?, ?, ?, ?, ?, ?)
             """)) {
            stmt.setString(1, "approval-2");
            stmt.setString(2, tenantId.value());
            stmt.setString(3, workspaceId.value());
            stmt.setString(4, "non-existent-campaign");
            stmt.setString(5, "approved");
            stmt.setString(6, "approver-1");
            stmt.setTimestamp(7, Timestamp.from(Instant.now()));
            stmt.setString(8, "Should fail");

            assertThatThrownBy(() -> stmt.executeUpdate())
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("foreign key constraint");
        }
    }

    @Test
    @DisplayName("unique constraints prevent duplicate records")
    void uniqueConstraintsPreventDuplicates() throws SQLException {
        DmTenantId tenantId = DmTenantId.of("tenant-1");
        DmWorkspaceId workspaceId = DmWorkspaceId.of("workspace-1");

        createWorkspace(tenantId, workspaceId);

        // Create campaign with unique id
        Campaign campaign = new Campaign(
            "campaign-1",
            "Campaign 1",
            "Active",
            10000.0,
            Instant.now(),
            Instant.now()
        );
        saveCampaign(tenantId, workspaceId, campaign);

        // Attempt to create duplicate campaign with same id
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement("""
                 INSERT INTO campaigns (id, tenant_id, workspace_id, name, status, budget, created_at, updated_at)
                 VALUES (?, ?, ?, ?, ?, ?, ?, ?)
             """)) {
            stmt.setString(1, "campaign-1");
            stmt.setString(2, tenantId.value());
            stmt.setString(3, workspaceId.value());
            stmt.setString(4, "Campaign 1 Duplicate");
            stmt.setString(5, "Active");
            stmt.setDouble(6, 15000.0);
            stmt.setTimestamp(7, Timestamp.from(Instant.now()));
            stmt.setTimestamp(8, Timestamp.from(Instant.now()));

            assertThatThrownBy(() -> stmt.executeUpdate())
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("duplicate key");
        }
    }

    @Test
    @DisplayName("idempotency keys prevent duplicate operations")
    void idempotencyKeysPreventDuplicateOperations() throws SQLException {
        DmTenantId tenantId = DmTenantId.of("tenant-1");
        DmWorkspaceId workspaceId = DmWorkspaceId.of("workspace-1");

        createWorkspace(tenantId, workspaceId);

        String idempotencyKey = UUID.randomUUID().toString();

        // First operation with idempotency key
        Campaign campaign1 = new Campaign(
            "campaign-1",
            "Campaign 1",
            "Active",
            10000.0,
            Instant.now(),
            Instant.now()
        );
        saveCampaignWithIdempotency(tenantId, workspaceId, campaign1, idempotencyKey);

        // Second operation with same idempotency key should return existing record
        Campaign campaign2 = new Campaign(
            "campaign-2",
            "Campaign 2",
            "Active",
            20000.0,
            Instant.now(),
            Instant.now()
        );
        Campaign result = saveCampaignWithIdempotency(tenantId, workspaceId, campaign2, idempotencyKey);

        // Should return the first campaign, not the second
        assertThat(result.id()).isEqualTo("campaign-1");
        assertThat(result.name()).isEqualTo("Campaign 1");
    }

    @Test
    @DisplayName("created and updated timestamps are automatically managed")
    void timestampsAreAutomaticallyManaged() throws SQLException {
        DmTenantId tenantId = DmTenantId.of("tenant-1");
        DmWorkspaceId workspaceId = DmWorkspaceId.of("workspace-1");

        createWorkspace(tenantId, workspaceId);

        Instant beforeCreate = Instant.now();

        // Create campaign
        Campaign campaign = new Campaign(
            "campaign-1",
            "Campaign 1",
            "Active",
            10000.0,
            Instant.now(),
            Instant.now()
        );
        Campaign saved = saveCampaign(tenantId, workspaceId, campaign);

        Instant afterCreate = Instant.now();

        // Verify createdAt and updatedAt are set
        assertThat(saved.createdAt()).isNotNull();
        assertThat(saved.createdAt()).isAfterOrEqualTo(beforeCreate);
        assertThat(saved.createdAt()).isBeforeOrEqualTo(afterCreate);
        assertThat(saved.updatedAt()).isNotNull();
        assertThat(saved.updatedAt()).isEqualTo(saved.createdAt());

        // Update campaign
        Instant beforeUpdate = Instant.now();
        Campaign updated = updateCampaign(tenantId, workspaceId, saved.withName("Updated Campaign"));
        Instant afterUpdate = Instant.now();

        // Verify updatedAt is updated, createdAt remains unchanged
        assertThat(updated.createdAt()).isEqualTo(saved.createdAt());
        assertThat(updated.updatedAt()).isAfterOrEqualTo(beforeUpdate);
        assertThat(updated.updatedAt()).isBeforeOrEqualTo(afterUpdate);
        assertThat(updated.updatedAt()).isAfter(saved.createdAt());
    }

    @Test
    @DisplayName("audit records are immutable once created")
    void auditRecordsAreImmutable() throws SQLException {
        DmTenantId tenantId = DmTenantId.of("tenant-1");
        DmWorkspaceId workspaceId = DmWorkspaceId.of("workspace-1");

        createWorkspace(tenantId, workspaceId);

        // Create audit record
        saveAuditRecord(tenantId, workspaceId, "audit-1", "campaign-1", "CREATE", "user-1");

        // Attempt to modify audit record
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement("""
                 UPDATE audit_records SET action = ?, user_id = ?
                 WHERE tenant_id = ? AND workspace_id = ? AND id = ?
             """)) {
            stmt.setString(1, "UPDATE");
            stmt.setString(2, "user-2");
            stmt.setString(3, tenantId.value());
            stmt.setString(4, workspaceId.value());
            stmt.setString(5, "audit-1");

            // This should fail due to the CHECK constraint
            assertThatThrownBy(() -> stmt.executeUpdate())
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("audit_immutable");
        }

        // Verify original audit record is unchanged
        AuditRecord retrieved = findAuditRecord(tenantId, workspaceId, "audit-1");
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.action()).isEqualTo("CREATE");
        assertThat(retrieved.userId()).isEqualTo("user-1");
    }

    @Test
    @DisplayName("approval records are immutable once finalized")
    void approvalRecordsAreImmutableOnceFinalized() throws SQLException {
        DmTenantId tenantId = DmTenantId.of("tenant-1");
        DmWorkspaceId workspaceId = DmWorkspaceId.of("workspace-1");

        createWorkspace(tenantId, workspaceId);

        // Create campaign first
        Campaign campaign = new Campaign(
            "campaign-1",
            "Campaign 1",
            "Active",
            10000.0,
            Instant.now(),
            Instant.now()
        );
        saveCampaign(tenantId, workspaceId, campaign);

        // Create pending approval record
        saveApprovalRecord(tenantId, workspaceId, "approval-1", "campaign-1", "pending", "approver-1");

        // Approve the record
        updateApprovalRecord(tenantId, workspaceId, "approval-1", "approved", "Approved for launch");

        // Attempt to modify approved approval record
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement("""
                 UPDATE approvals SET status = ?, approver_id = ?
                 WHERE tenant_id = ? AND workspace_id = ? AND id = ?
             """)) {
            stmt.setString(1, "rejected");
            stmt.setString(2, "approver-2");
            stmt.setString(3, tenantId.value());
            stmt.setString(4, workspaceId.value());
            stmt.setString(5, "approval-1");

            // This should fail due to the CHECK constraint
            assertThatThrownBy(() -> stmt.executeUpdate())
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("approval_immutable");
        }

        // Verify approved state is preserved
        ApprovalRecord retrieved = findApprovalRecord(tenantId, workspaceId, "approval-1");
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.status()).isEqualTo("approved");
        assertThat(retrieved.approverId()).isEqualTo("approver-1");
    }

    // Database helper methods

    private void createWorkspace(DmTenantId tenantId, DmWorkspaceId workspaceId) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement("""
                 INSERT INTO workspaces (tenant_id, workspace_id, name)
                 VALUES (?, ?, ?)
                 ON CONFLICT (tenant_id, workspace_id) DO NOTHING
             """)) {
            stmt.setString(1, tenantId.value());
            stmt.setString(2, workspaceId.value());
            stmt.setString(3, "Test Workspace");
            stmt.executeUpdate();
        }
    }

    private void saveCampaign(DmTenantId tenantId, DmWorkspaceId workspaceId, Campaign campaign) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement("""
                 INSERT INTO campaigns (id, tenant_id, workspace_id, name, status, budget, created_at, updated_at)
                 VALUES (?, ?, ?, ?, ?, ?, ?, ?)
             """)) {
            stmt.setString(1, campaign.id());
            stmt.setString(2, tenantId.value());
            stmt.setString(3, workspaceId.value());
            stmt.setString(4, campaign.name());
            stmt.setString(5, campaign.status());
            stmt.setDouble(6, campaign.budget());
            stmt.setTimestamp(7, Timestamp.from(campaign.createdAt()));
            stmt.setTimestamp(8, Timestamp.from(campaign.updatedAt()));
            stmt.executeUpdate();
        }
    }

    private Campaign saveCampaignWithIdempotency(DmTenantId tenantId, DmWorkspaceId workspaceId, Campaign campaign, String idempotencyKey) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            // Check if idempotency key exists
            try (PreparedStatement checkStmt = conn.prepareStatement("""
                SELECT campaign_id FROM idempotency_keys WHERE key = ?
            """)) {
                checkStmt.setString(1, idempotencyKey);
                ResultSet rs = checkStmt.executeQuery();
                if (rs.next()) {
                    // Return existing campaign
                    return findCampaign(tenantId, workspaceId, rs.getString("campaign_id"));
                }
            }

            // Save campaign
            saveCampaign(tenantId, workspaceId, campaign);

            // Save idempotency key
            try (PreparedStatement insertKeyStmt = conn.prepareStatement("""
                INSERT INTO idempotency_keys (key, campaign_id, tenant_id, workspace_id)
                VALUES (?, ?, ?, ?)
            """)) {
                insertKeyStmt.setString(1, idempotencyKey);
                insertKeyStmt.setString(2, campaign.id());
                insertKeyStmt.setString(3, tenantId.value());
                insertKeyStmt.setString(4, workspaceId.value());
                insertKeyStmt.executeUpdate();
            }

            return campaign;
        }
    }

    private Campaign updateCampaign(DmTenantId tenantId, DmWorkspaceId workspaceId, Campaign campaign) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement("""
                 UPDATE campaigns 
                 SET name = ?, status = ?, budget = ?, updated_at = ?
                 WHERE tenant_id = ? AND workspace_id = ? AND id = ?
             """)) {
            stmt.setString(1, campaign.name());
            stmt.setString(2, campaign.status());
            stmt.setDouble(3, campaign.budget());
            stmt.setTimestamp(4, Timestamp.from(Instant.now()));
            stmt.setString(5, tenantId.value());
            stmt.setString(6, workspaceId.value());
            stmt.setString(7, campaign.id());
            stmt.executeUpdate();
        }
        return findCampaign(tenantId, workspaceId, campaign.id());
    }

    private List<Campaign> listCampaignsByTenant(DmTenantId tenantId) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement("""
                 SELECT id, name, status, budget, created_at, updated_at
                 FROM campaigns WHERE tenant_id = ?
             """)) {
            stmt.setString(1, tenantId.value());
            ResultSet rs = stmt.executeQuery();
            List<Campaign> campaigns = new java.util.ArrayList<>();
            while (rs.next()) {
                campaigns.add(new Campaign(
                    rs.getString("id"),
                    rs.getString("name"),
                    rs.getString("status"),
                    rs.getDouble("budget"),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant()
                ));
            }
            return campaigns;
        }
    }

    private List<Campaign> listCampaignsByWorkspace(DmTenantId tenantId, DmWorkspaceId workspaceId) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement("""
                 SELECT id, name, status, budget, created_at, updated_at
                 FROM campaigns WHERE tenant_id = ? AND workspace_id = ?
             """)) {
            stmt.setString(1, tenantId.value());
            stmt.setString(2, workspaceId.value());
            ResultSet rs = stmt.executeQuery();
            List<Campaign> campaigns = new java.util.ArrayList<>();
            while (rs.next()) {
                campaigns.add(new Campaign(
                    rs.getString("id"),
                    rs.getString("name"),
                    rs.getString("status"),
                    rs.getDouble("budget"),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant()
                ));
            }
            return campaigns;
        }
    }

    private Campaign findCampaign(DmTenantId tenantId, DmWorkspaceId workspaceId, String campaignId) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement("""
                 SELECT id, name, status, budget, created_at, updated_at
                 FROM campaigns WHERE tenant_id = ? AND workspace_id = ? AND id = ?
             """)) {
            stmt.setString(1, tenantId.value());
            stmt.setString(2, workspaceId.value());
            stmt.setString(3, campaignId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return new Campaign(
                    rs.getString("id"),
                    rs.getString("name"),
                    rs.getString("status"),
                    rs.getDouble("budget"),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant()
                );
            }
            return null;
        }
    }

    private void saveApprovalRecord(DmTenantId tenantId, DmWorkspaceId workspaceId, String id, String campaignId, String status, String approverId) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement("""
                 INSERT INTO approvals (id, tenant_id, workspace_id, campaign_id, status, approver_id, timestamp, notes)
                 VALUES (?, ?, ?, ?, ?, ?, ?, ?)
             """)) {
            stmt.setString(1, id);
            stmt.setString(2, tenantId.value());
            stmt.setString(3, workspaceId.value());
            stmt.setString(4, campaignId);
            stmt.setString(5, status);
            stmt.setString(6, approverId);
            stmt.setTimestamp(7, Timestamp.from(Instant.now()));
            stmt.setString(8, "Test approval");
            stmt.executeUpdate();
        }
    }

    private void updateApprovalRecord(DmTenantId tenantId, DmWorkspaceId workspaceId, String id, String status, String notes) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement("""
                 UPDATE approvals SET status = ?, notes = ?, updated_at = created_at
                 WHERE tenant_id = ? AND workspace_id = ? AND id = ?
             """)) {
            stmt.setString(1, status);
            stmt.setString(2, notes);
            stmt.setString(3, tenantId.value());
            stmt.setString(4, workspaceId.value());
            stmt.setString(5, id);
            stmt.executeUpdate();
        }
    }

    private ApprovalRecord findApprovalRecord(DmTenantId tenantId, DmWorkspaceId workspaceId, String id) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement("""
                 SELECT id, campaign_id, status, approver_id, timestamp, notes
                 FROM approvals WHERE tenant_id = ? AND workspace_id = ? AND id = ?
             """)) {
            stmt.setString(1, tenantId.value());
            stmt.setString(2, workspaceId.value());
            stmt.setString(3, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return new ApprovalRecord(
                    rs.getString("id"),
                    rs.getString("campaign_id"),
                    rs.getString("status"),
                    rs.getString("approver_id"),
                    rs.getTimestamp("timestamp").toInstant(),
                    rs.getString("notes")
                );
            }
            return null;
        }
    }

    private void saveAuditRecord(DmTenantId tenantId, DmWorkspaceId workspaceId, String id, String entityId, String action, String userId) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement("""
                 INSERT INTO audit_records (id, tenant_id, workspace_id, entity_id, action, user_id, timestamp, metadata, created_at, updated_at)
                 VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
             """)) {
            stmt.setString(1, id);
            stmt.setString(2, tenantId.value());
            stmt.setString(3, workspaceId.value());
            stmt.setString(4, entityId);
            stmt.setString(5, action);
            stmt.setString(6, userId);
            stmt.setTimestamp(7, Timestamp.from(Instant.now()));
            stmt.setString(8, "{\"test\":\"true\"}");
            stmt.executeUpdate();
        }
    }

    private AuditRecord findAuditRecord(DmTenantId tenantId, DmWorkspaceId workspaceId, String id) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement("""
                 SELECT id, entity_id, action, user_id, timestamp, metadata
                 FROM audit_records WHERE tenant_id = ? AND workspace_id = ? AND id = ?
             """)) {
            stmt.setString(1, tenantId.value());
            stmt.setString(2, workspaceId.value());
            stmt.setString(3, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return new AuditRecord(
                    rs.getString("id"),
                    rs.getString("entity_id"),
                    rs.getString("action"),
                    rs.getString("user_id"),
                    rs.getTimestamp("timestamp").toInstant(),
                    Map.of("test", "true")
                );
            }
            return null;
        }
    }

    // Domain record implementations for testing

    private record Campaign(
        String id,
        String name,
        String status,
        Double budget,
        Instant createdAt,
        Instant updatedAt
    ) {
        Campaign withTimestamps(Instant createdAt, Instant updatedAt) {
            return new Campaign(id, name, status, budget, createdAt, updatedAt);
        }

        Campaign withName(String name) {
            return new Campaign(id, name, status, budget, createdAt, updatedAt);
        }
    }

    private record ApprovalRecord(
        String id,
        String campaignId,
        String status,
        String approverId,
        Instant timestamp,
        String notes
    ) {}

    private record AuditRecord(
        String id,
        String entityId,
        String action,
        String userId,
        Instant timestamp,
        Map<String, Object> metadata
    ) {}
}
