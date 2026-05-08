package com.ghatana.digitalmarketing.persistence.preflight;

import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.campaign.Campaign;
import com.ghatana.digitalmarketing.domain.campaign.CampaignStatus;
import com.ghatana.digitalmarketing.domain.campaign.CampaignType;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@DisplayName("PostgresCampaignPreflightDataProvider Integration Tests")
class PostgresCampaignPreflightDataProviderTest extends EventloopTestBase {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("dmos_test")
        .withUsername("dmos")
        .withPassword("dmos_secret");

    private static PostgresCampaignPreflightDataProvider provider;

    @BeforeAll
    static void setup() {
        Flyway flyway = Flyway.configure()
            .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .locations("filesystem:src/main/resources/db/migration")
            .load();
        flyway.migrate();

        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(postgres.getJdbcUrl());
        dataSource.setUser(postgres.getUsername());
        dataSource.setPassword(postgres.getPassword());

        Executor executor = Runnable::run;
        provider = new PostgresCampaignPreflightDataProvider(dataSource, executor);
    }

    @BeforeEach
    void cleanup() throws Exception {
        try (var connection = postgres.createConnection("")) {
            connection.createStatement().executeUpdate("DELETE FROM dmos_budget_recommendations");
            connection.createStatement().executeUpdate("DELETE FROM dmos_ai_action_log");
        }
    }

    @Test
    @DisplayName("resolve should derive budget and approved content counts from PostgreSQL")
    void resolveShouldDerivePreflightData() throws Exception {
        String workspaceId = "ws-1";
        String campaignId = "camp-1";

        try (var connection = postgres.createConnection("")) {
            connection.createStatement().executeUpdate(
                "INSERT INTO dmos_budget_recommendations " +
                    "(recommendation_id, workspace_id, monthly_budget, channel_split, daily_caps, risk_level, rationale, assumptions, model_version, generated_at, generated_by, strategy_id) " +
                    "VALUES ('rec-1', '" + workspaceId + "', 5000, '{}'::jsonb, '{}'::jsonb, 2, 'r', 'a', 'v1', NOW(), 'tester', 'strat-1')"
            );

            connection.createStatement().executeUpdate(
                "INSERT INTO dmos_ai_action_log " +
                    "(action_id, workspace_id, correlation_id, action_type, status, actor, initiated_by_ai, confidence, evidence_links, policy_checks, summary, details, related_entity_id, occurred_at, version, tenant_id) " +
                    "VALUES ('log-1', '" + workspaceId + "', 'corr-1', 'CONTENT_APPROVAL', 'APPROVED', 'tester', true, 0.9, '{}'::text[], '{}'::text[], 's', 'd', '" + campaignId + "', NOW(), 1, 'tenant-1')"
            );
        }

        DmOperationContext ctx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of(workspaceId))
            .actor(ActorRef.user("tester"))
            .correlationId(DmCorrelationId.generate())
            .build();

        Campaign campaign = Campaign.builder()
            .id(campaignId)
            .workspaceId(DmWorkspaceId.of(workspaceId))
            .name("Test Campaign")
            .status(CampaignStatus.DRAFT)
            .type(CampaignType.PAID_SEARCH)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .createdBy("tester")
            .build();

        var data = runPromise(() -> provider.resolve(ctx, campaign));

        assertThat(data.budgetApproved()).isTrue();
        assertThat(data.approvedBudget()).isEqualTo(5000.0);
        assertThat(data.approvedContentCount()).isEqualTo(1);
        assertThat(data.targetAudienceCount()).isEqualTo(1);
    }
}
