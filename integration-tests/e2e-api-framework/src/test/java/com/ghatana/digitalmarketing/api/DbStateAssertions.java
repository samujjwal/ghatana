package com.ghatana.digitalmarketing.api;

import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * P1-045: Database state assertions for E2E tests.
 *
 * <p>Provides fluent assertions for verifying database state after API operations:
 * <ul>
 *   <li>Campaign state verification</li>
 *   <li>Budget amount validation</li>
 *   <li>Approval status checks</li>
 *   <li>Audit log verification</li>
 *   <li>Tenant isolation validation</li>
 *   <li>Change history verification</li>
 * </ul>
 *
 * <p>Example usage:</p>
 * <pre>
 * DbStateAssertions.assertThat(dataSource)
 *     .hasCampaignWithId(campaignId)
 *     .hasStatus(CampaignStatus.APPROVED)
 *     .hasVersion(2)
 *     .hasBudgetAmount(100000);
 * </pre>
 *
 * @doc.type class
 * @doc.purpose Database state assertions for E2E test validation (P1-045)
 * @doc.layer test
 * @doc.pattern Fluent API, Assertions, Testing
 */
public final class DbStateAssertions {

    private DbStateAssertions() {}

    /**
     * Entry point for database state assertions.
     */
    public static DatabaseAssert assertThat(DataSource dataSource) {
        return new DatabaseAssert(dataSource);
    }

    /**
     * Fluent assertion class for database state.
     */
    public static class DatabaseAssert extends AbstractAssert<DatabaseAssert, DataSource> {

        private String currentCampaignId;
        private String currentTenantId;

        public DatabaseAssert(DataSource dataSource) {
            super(dataSource, DatabaseAssert.class);
        }

        /**
         * P1-045: Sets the campaign context for subsequent assertions.
         */
        public DatabaseAssert hasCampaignWithId(String campaignId) {
            this.currentCampaignId = campaignId;

            executeQuery(
                "SELECT 1 FROM dmos_campaigns WHERE id = ?",
                stmt -> stmt.setString(1, campaignId),
                rs -> {
                    if (!rs.next()) {
                        failWithMessage("Expected campaign with id <%s> to exist", campaignId);
                    }
                    return null;
                }
            );

            return this;
        }

        /**
         * P1-045: Sets the tenant context for tenant-scoped assertions.
         */
        public DatabaseAssert forTenant(String tenantId) {
            this.currentTenantId = tenantId;
            return this;
        }

        /**
         * P1-045: Asserts campaign has expected status.
         */
        public DatabaseAssert hasStatus(String expectedStatus) {
            assertCampaignSelected();

            String actualStatus = executeQuery(
                "SELECT status FROM dmos_campaigns WHERE id = ?",
                stmt -> stmt.setString(1, currentCampaignId),
                rs -> rs.next() ? rs.getString("status") : null
            );

            Assertions.assertThat(actualStatus)
                .as("Campaign <%s> status", currentCampaignId)
                .isEqualTo(expectedStatus);

            return this;
        }

        /**
         * P1-045: Asserts campaign has expected version.
         */
        public DatabaseAssert hasVersion(int expectedVersion) {
            assertCampaignSelected();

            Integer actualVersion = executeQuery(
                "SELECT version FROM dmos_campaigns WHERE id = ?",
                stmt -> stmt.setString(1, currentCampaignId),
                rs -> rs.next() ? rs.getInt("version") : null
            );

            Assertions.assertThat(actualVersion)
                .as("Campaign <%s> version", currentCampaignId)
                .isEqualTo(expectedVersion);

            return this;
        }

        /**
         * P1-045: Asserts campaign has expected budget amount.
         */
        public DatabaseAssert hasBudgetAmount(long expectedAmount) {
            assertCampaignSelected();

            Long actualAmount = executeQuery(
                "SELECT total_budget FROM dmos_budgets WHERE campaign_id = ? AND status = 'ACTIVE'",
                stmt -> stmt.setString(1, currentCampaignId),
                rs -> rs.next() ? rs.getLong("total_budget") : null
            );

            Assertions.assertThat(actualAmount)
                .as("Campaign <%s> budget amount", currentCampaignId)
                .isEqualTo(expectedAmount);

            return this;
        }

        /**
         * P1-045: Asserts campaign has expected strategy status.
         */
        public DatabaseAssert hasStrategyStatus(String expectedStatus) {
            assertCampaignSelected();

            String actualStatus = executeQuery(
                "SELECT status FROM dmos_strategies WHERE campaign_id = ? ORDER BY created_at DESC LIMIT 1",
                stmt -> stmt.setString(1, currentCampaignId),
                rs -> rs.next() ? rs.getString("status") : null
            );

            Assertions.assertThat(actualStatus)
                .as("Campaign <%s> strategy status", currentCampaignId)
                .isEqualTo(expectedStatus);

            return this;
        }

        /**
         * P1-045: Asserts campaign has expected approval status.
         */
        public DatabaseAssert hasApprovalStatus(String expectedStatus) {
            assertCampaignSelected();

            String actualStatus = executeQuery(
                "SELECT status FROM dmos_approvals WHERE entity_id = ? AND entity_type = 'CAMPAIGN' ORDER BY created_at DESC LIMIT 1",
                stmt -> stmt.setString(1, currentCampaignId),
                rs -> rs.next() ? rs.getString("status") : null
            );

            Assertions.assertThat(actualStatus)
                .as("Campaign <%s> approval status", currentCampaignId)
                .isEqualTo(expectedStatus);

            return this;
        }

        /**
         * P1-045: Asserts campaign belongs to expected tenant.
         */
        public DatabaseAssert belongsToTenant(String expectedTenantId) {
            assertCampaignSelected();

            String actualTenantId = executeQuery(
                "SELECT tenant_id FROM dmos_campaigns WHERE id = ?",
                stmt -> stmt.setString(1, currentCampaignId),
                rs -> rs.next() ? rs.getString("tenant_id") : null
            );

            Assertions.assertThat(actualTenantId)
                .as("Campaign <%s> tenant", currentCampaignId)
                .isEqualTo(expectedTenantId);

            return this;
        }

        /**
         * P1-045: Asserts campaign has audit log entry.
         */
        public DatabaseAssert hasAuditLogEntry(String expectedAction) {
            assertCampaignSelected();

            Integer count = executeQuery(
                "SELECT COUNT(*) as cnt FROM dmos_audit_log WHERE entity_id = ? AND action = ?",
                stmt -> {
                    stmt.setString(1, currentCampaignId);
                    stmt.setString(2, expectedAction);
                },
                rs -> rs.next() ? rs.getInt("cnt") : 0
            );

            Assertions.assertThat(count)
                .as("Campaign <%s> audit log entry for action <%s>", currentCampaignId, expectedAction)
                .isGreaterThan(0);

            return this;
        }

        /**
         * P1-045: Asserts campaign has no pending changes.
         */
        public DatabaseAssert hasNoPendingChanges() {
            assertCampaignSelected();

            Integer pendingCount = executeQuery(
                "SELECT COUNT(*) as cnt FROM dmos_campaign_changes WHERE campaign_id = ? AND status = 'PENDING_APPROVAL'",
                stmt -> stmt.setString(1, currentCampaignId),
                rs -> rs.next() ? rs.getInt("cnt") : 0
            );

            Assertions.assertThat(pendingCount)
                .as("Campaign <%s> pending changes", currentCampaignId)
                .isEqualTo(0);

            return this;
        }

        /**
         * P1-045: Asserts campaign has expected number of change history entries.
         */
        public DatabaseAssert hasChangeHistoryCount(int expectedCount) {
            assertCampaignSelected();

            Integer actualCount = executeQuery(
                "SELECT COUNT(*) as cnt FROM dmos_campaign_changes WHERE campaign_id = ?",
                stmt -> stmt.setString(1, currentCampaignId),
                rs -> rs.next() ? rs.getInt("cnt") : 0
            );

            Assertions.assertThat(actualCount)
                .as("Campaign <%s> change history count", currentCampaignId)
                .isEqualTo(expectedCount);

            return this;
        }

        /**
         * P1-045: Asserts campaign has been modified by expected user.
         */
        public DatabaseAssert wasModifiedBy(String expectedPrincipalId) {
            assertCampaignSelected();

            String actualModifier = executeQuery(
                "SELECT modified_by FROM dmos_campaigns WHERE id = ?",
                stmt -> stmt.setString(1, currentCampaignId),
                rs -> rs.next() ? rs.getString("modified_by") : null
            );

            Assertions.assertThat(actualModifier)
                .as("Campaign <%s> modified by", currentCampaignId)
                .isEqualTo(expectedPrincipalId);

            return this;
        }

        /**
         * P1-045: Asserts campaign has expected external ID.
         */
        public DatabaseAssert hasExternalCampaignId(String expectedExternalId) {
            assertCampaignSelected();

            String actualExternalId = executeQuery(
                "SELECT external_campaign_id FROM dmos_campaigns WHERE id = ?",
                stmt -> stmt.setString(1, currentCampaignId),
                rs -> rs.next() ? rs.getString("external_campaign_id") : null
            );

            Assertions.assertThat(actualExternalId)
                .as("Campaign <%s> external ID", currentCampaignId)
                .isEqualTo(expectedExternalId);

            return this;
        }

        /**
         * P1-045: Asserts campaign has outbox entry.
         */
        public DatabaseAssert hasOutboxEntry(String expectedType) {
            assertCampaignSelected();

            Integer count = executeQuery(
                "SELECT COUNT(*) as cnt FROM dmos_outbox WHERE campaign_id = ? AND type = ?",
                stmt -> {
                    stmt.setString(1, currentCampaignId);
                    stmt.setString(2, expectedType);
                },
                rs -> rs.next() ? rs.getInt("cnt") : 0
            );

            Assertions.assertThat(count)
                .as("Campaign <%s> outbox entry for type <%s>", currentCampaignId, expectedType)
                .isGreaterThan(0);

            return this;
        }

        /**
         * P1-045: Asserts tenant isolation - campaign not visible in other tenants.
         */
        public DatabaseAssert isNotVisibleInTenant(String otherTenantId) {
            assertCampaignSelected();

            Integer count = executeQuery(
                "SELECT COUNT(*) as cnt FROM dmos_campaigns WHERE id = ? AND tenant_id = ?",
                stmt -> {
                    stmt.setString(1, currentCampaignId);
                    stmt.setString(2, otherTenantId);
                },
                rs -> rs.next() ? rs.getInt("cnt") : 0
            );

            Assertions.assertThat(count)
                .as("Campaign <%s> should not be visible in tenant <%s>", currentCampaignId, otherTenantId)
                .isEqualTo(0);

            return this;
        }

        /**
         * P1-045: Asserts AI action log has entry for campaign.
         */
        public DatabaseAssert hasAiActionLogEntry(String expectedAction) {
            assertCampaignSelected();

            Integer count = executeQuery(
                "SELECT COUNT(*) as cnt FROM dmos_ai_action_log WHERE entity_id = ? AND action = ?",
                stmt -> {
                    stmt.setString(1, currentCampaignId);
                    stmt.setString(2, expectedAction);
                },
                rs -> rs.next() ? rs.getInt("cnt") : 0
            );

            Assertions.assertThat(count)
                .as("Campaign <%s> AI action log entry for <%s>", currentCampaignId, expectedAction)
                .isGreaterThan(0);

            return this;
        }

        /**
         * P1-045: Asserts compensation log has entry for campaign.
         */
        public DatabaseAssert hasCompensationEntry(String expectedType) {
            assertCampaignSelected();

            Integer count = executeQuery(
                "SELECT COUNT(*) as cnt FROM dmos_compensation_log WHERE campaign_id = ? AND compensation_type = ?",
                stmt -> {
                    stmt.setString(1, currentCampaignId);
                    stmt.setString(2, expectedType);
                },
                rs -> rs.next() ? rs.getInt("cnt") : 0
            );

            Assertions.assertThat(count)
                .as("Campaign <%s> compensation entry for type <%s>", currentCampaignId, expectedType)
                .isGreaterThan(0);

            return this;
        }

        /**
         * P1-045: Gets campaign data as map for custom assertions.
         */
        public Map<String, Object> getCampaignData() {
            assertCampaignSelected();

            return executeQuery(
                "SELECT * FROM dmos_campaigns WHERE id = ?",
                stmt -> stmt.setString(1, currentCampaignId),
                rs -> {
                    if (rs.next()) {
                        Map<String, Object> data = new HashMap<>();
                        data.put("id", rs.getString("id"));
                        data.put("tenantId", rs.getString("tenant_id"));
                        data.put("workspaceId", rs.getString("workspace_id"));
                        data.put("name", rs.getString("name"));
                        data.put("status", rs.getString("status"));
                        data.put("version", rs.getInt("version"));
                        data.put("createdBy", rs.getString("created_by"));
                        data.put("modifiedBy", rs.getString("modified_by"));
                        data.put("createdAt", rs.getTimestamp("created_at").toInstant());
                        data.put("externalCampaignId", rs.getString("external_campaign_id"));
                        return data;
                    }
                    return null;
                }
            );
        }

        private void assertCampaignSelected() {
            if (currentCampaignId == null) {
                throw new IllegalStateException("No campaign selected. Call hasCampaignWithId() first.");
            }
        }

        private <T> T executeQuery(String sql, QueryPreparer preparer, ResultSetExtractor<T> extractor) {
            try (Connection conn = actual.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                preparer.prepare(stmt);
                try (ResultSet rs = stmt.executeQuery()) {
                    return extractor.extract(rs);
                }
            } catch (SQLException e) {
                throw new RuntimeException("Database query failed", e);
            }
        }
    }

    @FunctionalInterface
    private interface QueryPreparer {
        void prepare(PreparedStatement stmt) throws SQLException;
    }

    @FunctionalInterface
    private interface ResultSetExtractor<T> {
        T extract(ResultSet rs) throws SQLException;
    }
}
