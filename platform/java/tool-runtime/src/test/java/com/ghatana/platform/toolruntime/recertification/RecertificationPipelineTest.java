/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.toolruntime.recertification;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link InMemoryRecertificationPipeline}.
 *
 * @doc.type class
 * @doc.purpose Tests for recertification campaign lifecycle
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("InMemoryRecertificationPipeline")
class RecertificationPipelineTest extends EventloopTestBase {

    private InMemoryRecertificationPipeline pipeline;

    @BeforeEach
    void setUp() {
        pipeline = new InMemoryRecertificationPipeline();
    }

    @Nested
    @DisplayName("createCampaign")
    class CreateCampaign {

        @Test
        @DisplayName("creates campaign with IN_PROGRESS status and populated items")
        void createsWithItemsInProgress() {
            RecertificationCampaign campaign = runPromise(() ->
                pipeline.createCampaign("tenant-1", "Q1 2026 Review", RecertificationScope.AGENT_PERMISSIONS));

            assertThat(campaign.tenantId()).isEqualTo("tenant-1");
            assertThat(campaign.campaignName()).isEqualTo("Q1 2026 Review");
            assertThat(campaign.scope()).isEqualTo(RecertificationScope.AGENT_PERMISSIONS);
            assertThat(campaign.status()).isEqualTo(CampaignStatus.IN_PROGRESS);
            assertThat(campaign.totalItems()).isGreaterThan(0);
            assertThat(campaign.certifiedCount()).isZero();
            assertThat(campaign.revokedCount()).isZero();
            assertThat(campaign.createdAt()).isNotNull();
            assertThat(campaign.completedAt()).isNull();
        }

        @Test
        @DisplayName("FULL scope generates more items than a targeted scope")
        void fullScopeHasMoreItems() {
            RecertificationCampaign agentOnly = runPromise(() ->
                pipeline.createCampaign("tenant-1", "Agent only", RecertificationScope.AGENT_PERMISSIONS));
            RecertificationCampaign full = runPromise(() ->
                pipeline.createCampaign("tenant-1", "Full review", RecertificationScope.FULL));

            assertThat(full.totalItems()).isGreaterThan(agentOnly.totalItems());
        }
    }

    @Nested
    @DisplayName("getItems")
    class GetItems {

        @Test
        @DisplayName("all items start as PENDING")
        void allItemsPending() {
            RecertificationCampaign campaign = runPromise(() ->
                pipeline.createCampaign("tenant-1", "c1", RecertificationScope.TOOL_REGISTRATIONS));

            List<RecertificationItem> items = runPromise(() ->
                pipeline.getItems(campaign.campaignId()));

            assertThat(items).isNotEmpty();
            assertThat(items).allMatch(i -> i.decision() == ItemDecision.PENDING);
        }

        @Test
        @DisplayName("returns not-found error for unknown campaign")
        void notFoundForUnknownCampaign() {
            assertThatThrownBy(() -> runPromise(() -> pipeline.getItems("no-such-campaign")))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("certify")
    class Certify {

        @Test
        @DisplayName("transitions item from PENDING to CERTIFIED")
        void certifiesItem() {
            RecertificationCampaign campaign = runPromise(() ->
                pipeline.createCampaign("tenant-1", "c1", RecertificationScope.AGENT_PERMISSIONS));
            List<RecertificationItem> items = runPromise(() ->
                pipeline.getItems(campaign.campaignId()));
            String itemId = items.get(0).itemId();

            RecertificationItem certified = runPromise(() ->
                pipeline.certify(campaign.campaignId(), itemId, "reviewer@example.com"));

            assertThat(certified.decision()).isEqualTo(ItemDecision.CERTIFIED);
            assertThat(certified.certifierId()).isEqualTo("reviewer@example.com");
            assertThat(certified.reviewedAt()).isNotNull();
        }

        @Test
        @DisplayName("cannot certify an already-reviewed item")
        void cannotCertifyTwice() {
            RecertificationCampaign campaign = runPromise(() ->
                pipeline.createCampaign("tenant-1", "c1", RecertificationScope.AGENT_PERMISSIONS));
            List<RecertificationItem> items = runPromise(() ->
                pipeline.getItems(campaign.campaignId()));
            String itemId = items.get(0).itemId();
            runPromise(() -> pipeline.certify(campaign.campaignId(), itemId, "reviewer-1"));

            assertThatThrownBy(() -> runPromise(() ->
                pipeline.certify(campaign.campaignId(), itemId, "reviewer-2")))
                .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("revoke")
    class Revoke {

        @Test
        @DisplayName("transitions item from PENDING to REVOKED with reason")
        void revokesItem() {
            RecertificationCampaign campaign = runPromise(() ->
                pipeline.createCampaign("tenant-1", "c1", RecertificationScope.TOOL_REGISTRATIONS));
            List<RecertificationItem> items = runPromise(() ->
                pipeline.getItems(campaign.campaignId()));
            String itemId = items.get(0).itemId();

            RecertificationItem revoked = runPromise(() ->
                pipeline.revoke(campaign.campaignId(), itemId,
                    "auditor@example.com", "Unused for 6 months"));

            assertThat(revoked.decision()).isEqualTo(ItemDecision.REVOKED);
            assertThat(revoked.decisionNotes()).isEqualTo("Unused for 6 months");
            assertThat(revoked.certifierId()).isEqualTo("auditor@example.com");
        }

        @Test
        @DisplayName("returns error if revocation reason is blank")
        void requiresNonBlankReason() {
            RecertificationCampaign campaign = runPromise(() ->
                pipeline.createCampaign("tenant-1", "c1", RecertificationScope.POLICIES));
            List<RecertificationItem> items = runPromise(() ->
                pipeline.getItems(campaign.campaignId()));
            String itemId = items.get(0).itemId();

            assertThatThrownBy(() -> runPromise(() ->
                pipeline.revoke(campaign.campaignId(), itemId, "reviewer", "")))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("generateReport")
    class GenerateReport {

        @Test
        @DisplayName("report reflects certified + revoked + pending counts correctly")
        void reportCountsCorrect() {
            RecertificationCampaign campaign = runPromise(() ->
                pipeline.createCampaign("tenant-1", "c1", RecertificationScope.AGENT_PERMISSIONS));
            List<RecertificationItem> items = runPromise(() ->
                pipeline.getItems(campaign.campaignId()));

            // Certify first item, leave rest pending
            runPromise(() -> pipeline.certify(campaign.campaignId(),
                items.get(0).itemId(), "reviewer-1"));

            RecertificationReport report = runPromise(() ->
                pipeline.generateReport(campaign.campaignId()));

            assertThat(report.certifiedCount()).isEqualTo(1);
            assertThat(report.revokedCount()).isZero();
            assertThat(report.pendingCount()).isEqualTo(items.size() - 1);
            assertThat(report.totalItems()).isEqualTo(items.size());
        }

        @Test
        @DisplayName("campaign marked COMPLETED when all items reviewed")
        void campaignCompletedWhenAllReviewed() {
            RecertificationCampaign campaign = runPromise(() ->
                pipeline.createCampaign("tenant-1", "c1", RecertificationScope.AGENT_PERMISSIONS));
            List<RecertificationItem> items = runPromise(() ->
                pipeline.getItems(campaign.campaignId()));

            // Review all items
            for (RecertificationItem item : items) {
                runPromise(() -> pipeline.certify(campaign.campaignId(), item.itemId(), "r"));
            }

            RecertificationReport report = runPromise(() ->
                pipeline.generateReport(campaign.campaignId()));
            assertThat(report.pendingCount()).isZero();

            RecertificationCampaign updated = runPromise(() ->
                pipeline.getCampaign(campaign.campaignId()));
            assertThat(updated.status()).isEqualTo(CampaignStatus.COMPLETED);
            assertThat(updated.completedAt()).isNotNull();
        }

        @Test
        @DisplayName("report includes revoked items list for audit trail")
        void reportIncludesRevokedItems() {
            RecertificationCampaign campaign = runPromise(() ->
                pipeline.createCampaign("tenant-1", "c1", RecertificationScope.TOOL_REGISTRATIONS));
            List<RecertificationItem> items = runPromise(() ->
                pipeline.getItems(campaign.campaignId()));

            runPromise(() -> pipeline.revoke(campaign.campaignId(),
                items.get(0).itemId(), "auditor", "Risk too high"));

            RecertificationReport report = runPromise(() ->
                pipeline.generateReport(campaign.campaignId()));
            assertThat(report.revokedItems()).hasSize(1);
            assertThat(report.revokedItems().get(0).decision()).isEqualTo(ItemDecision.REVOKED);
        }

        @Test
        @DisplayName("certificationRate is 1.0 when all items certified")
        void certificationRateOneWhenAllCertified() {
            RecertificationCampaign campaign = runPromise(() ->
                pipeline.createCampaign("tenant-1", "c1", RecertificationScope.POLICIES));
            List<RecertificationItem> items = runPromise(() ->
                pipeline.getItems(campaign.campaignId()));

            for (RecertificationItem item : items) {
                runPromise(() -> pipeline.certify(campaign.campaignId(), item.itemId(), "r"));
            }

            RecertificationReport report = runPromise(() ->
                pipeline.generateReport(campaign.campaignId()));
            assertThat(report.certificationRate()).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("listCampaigns")
    class ListCampaigns {

        @Test
        @DisplayName("returns only campaigns for the queried tenant")
        void filtersByTenant() {
            runPromise(() -> pipeline.createCampaign("tenant-1", "c1", RecertificationScope.POLICIES));
            runPromise(() -> pipeline.createCampaign("tenant-1", "c2", RecertificationScope.AGENT_PERMISSIONS));
            runPromise(() -> pipeline.createCampaign("tenant-2", "c3", RecertificationScope.FULL));

            List<RecertificationCampaign> t1 = runPromise(() -> pipeline.listCampaigns("tenant-1"));
            assertThat(t1).hasSize(2);
            assertThat(t1).allMatch(c -> c.tenantId().equals("tenant-1"));
        }
    }
}
