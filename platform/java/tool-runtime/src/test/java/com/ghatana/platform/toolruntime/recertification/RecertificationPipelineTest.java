/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
    void setUp() { // GH-90000
        pipeline = new InMemoryRecertificationPipeline(); // GH-90000
    }

    @Nested
    @DisplayName("createCampaign")
    class CreateCampaign {

        @Test
        @DisplayName("creates campaign with IN_PROGRESS status and populated items")
        void createsWithItemsInProgress() { // GH-90000
            RecertificationCampaign campaign = runPromise(() -> // GH-90000
                pipeline.createCampaign("tenant-1", "Q1 2026 Review", RecertificationScope.AGENT_PERMISSIONS)); // GH-90000

            assertThat(campaign.tenantId()).isEqualTo("tenant-1");
            assertThat(campaign.campaignName()).isEqualTo("Q1 2026 Review");
            assertThat(campaign.scope()).isEqualTo(RecertificationScope.AGENT_PERMISSIONS); // GH-90000
            assertThat(campaign.status()).isEqualTo(CampaignStatus.IN_PROGRESS); // GH-90000
            assertThat(campaign.totalItems()).isGreaterThan(0); // GH-90000
            assertThat(campaign.certifiedCount()).isZero(); // GH-90000
            assertThat(campaign.revokedCount()).isZero(); // GH-90000
            assertThat(campaign.createdAt()).isNotNull(); // GH-90000
            assertThat(campaign.completedAt()).isNull(); // GH-90000
        }

        @Test
        @DisplayName("FULL scope generates more items than a targeted scope")
        void fullScopeHasMoreItems() { // GH-90000
            RecertificationCampaign agentOnly = runPromise(() -> // GH-90000
                pipeline.createCampaign("tenant-1", "Agent only", RecertificationScope.AGENT_PERMISSIONS)); // GH-90000
            RecertificationCampaign full = runPromise(() -> // GH-90000
                pipeline.createCampaign("tenant-1", "Full review", RecertificationScope.FULL)); // GH-90000

            assertThat(full.totalItems()).isGreaterThan(agentOnly.totalItems()); // GH-90000
        }
    }

    @Nested
    @DisplayName("getItems")
    class GetItems {

        @Test
        @DisplayName("all items start as PENDING")
        void allItemsPending() { // GH-90000
            RecertificationCampaign campaign = runPromise(() -> // GH-90000
                pipeline.createCampaign("tenant-1", "c1", RecertificationScope.TOOL_REGISTRATIONS)); // GH-90000

            List<RecertificationItem> items = runPromise(() -> // GH-90000
                pipeline.getItems(campaign.campaignId())); // GH-90000

            assertThat(items).isNotEmpty(); // GH-90000
            assertThat(items).allMatch(i -> i.decision() == ItemDecision.PENDING); // GH-90000
        }

        @Test
        @DisplayName("returns not-found error for unknown campaign")
        void notFoundForUnknownCampaign() { // GH-90000
            assertThatThrownBy(() -> runPromise(() -> pipeline.getItems("no-such-campaign")))
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }
    }

    @Nested
    @DisplayName("certify")
    class Certify {

        @Test
        @DisplayName("transitions item from PENDING to CERTIFIED")
        void certifiesItem() { // GH-90000
            RecertificationCampaign campaign = runPromise(() -> // GH-90000
                pipeline.createCampaign("tenant-1", "c1", RecertificationScope.AGENT_PERMISSIONS)); // GH-90000
            List<RecertificationItem> items = runPromise(() -> // GH-90000
                pipeline.getItems(campaign.campaignId())); // GH-90000
            String itemId = items.get(0).itemId(); // GH-90000

            RecertificationItem certified = runPromise(() -> // GH-90000
                pipeline.certify(campaign.campaignId(), itemId, "reviewer@example.com")); // GH-90000

            assertThat(certified.decision()).isEqualTo(ItemDecision.CERTIFIED); // GH-90000
            assertThat(certified.certifierId()).isEqualTo("reviewer@example.com");
            assertThat(certified.reviewedAt()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("cannot certify an already-reviewed item")
        void cannotCertifyTwice() { // GH-90000
            RecertificationCampaign campaign = runPromise(() -> // GH-90000
                pipeline.createCampaign("tenant-1", "c1", RecertificationScope.AGENT_PERMISSIONS)); // GH-90000
            List<RecertificationItem> items = runPromise(() -> // GH-90000
                pipeline.getItems(campaign.campaignId())); // GH-90000
            String itemId = items.get(0).itemId(); // GH-90000
            runPromise(() -> pipeline.certify(campaign.campaignId(), itemId, "reviewer-1")); // GH-90000

            assertThatThrownBy(() -> runPromise(() -> // GH-90000
                pipeline.certify(campaign.campaignId(), itemId, "reviewer-2"))) // GH-90000
                .isInstanceOf(IllegalStateException.class); // GH-90000
        }
    }

    @Nested
    @DisplayName("revoke")
    class Revoke {

        @Test
        @DisplayName("transitions item from PENDING to REVOKED with reason")
        void revokesItem() { // GH-90000
            RecertificationCampaign campaign = runPromise(() -> // GH-90000
                pipeline.createCampaign("tenant-1", "c1", RecertificationScope.TOOL_REGISTRATIONS)); // GH-90000
            List<RecertificationItem> items = runPromise(() -> // GH-90000
                pipeline.getItems(campaign.campaignId())); // GH-90000
            String itemId = items.get(0).itemId(); // GH-90000

            RecertificationItem revoked = runPromise(() -> // GH-90000
                pipeline.revoke(campaign.campaignId(), itemId, // GH-90000
                    "auditor@example.com", "Unused for 6 months"));

            assertThat(revoked.decision()).isEqualTo(ItemDecision.REVOKED); // GH-90000
            assertThat(revoked.decisionNotes()).isEqualTo("Unused for 6 months");
            assertThat(revoked.certifierId()).isEqualTo("auditor@example.com");
        }

        @Test
        @DisplayName("returns error if revocation reason is blank")
        void requiresNonBlankReason() { // GH-90000
            RecertificationCampaign campaign = runPromise(() -> // GH-90000
                pipeline.createCampaign("tenant-1", "c1", RecertificationScope.POLICIES)); // GH-90000
            List<RecertificationItem> items = runPromise(() -> // GH-90000
                pipeline.getItems(campaign.campaignId())); // GH-90000
            String itemId = items.get(0).itemId(); // GH-90000

            assertThatThrownBy(() -> runPromise(() -> // GH-90000
                pipeline.revoke(campaign.campaignId(), itemId, "reviewer", ""))) // GH-90000
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }
    }

    @Nested
    @DisplayName("generateReport")
    class GenerateReport {

        @Test
        @DisplayName("report reflects certified + revoked + pending counts correctly")
        void reportCountsCorrect() { // GH-90000
            RecertificationCampaign campaign = runPromise(() -> // GH-90000
                pipeline.createCampaign("tenant-1", "c1", RecertificationScope.AGENT_PERMISSIONS)); // GH-90000
            List<RecertificationItem> items = runPromise(() -> // GH-90000
                pipeline.getItems(campaign.campaignId())); // GH-90000

            // Certify first item, leave rest pending
            runPromise(() -> pipeline.certify(campaign.campaignId(), // GH-90000
                items.get(0).itemId(), "reviewer-1")); // GH-90000

            RecertificationReport report = runPromise(() -> // GH-90000
                pipeline.generateReport(campaign.campaignId())); // GH-90000

            assertThat(report.certifiedCount()).isEqualTo(1); // GH-90000
            assertThat(report.revokedCount()).isZero(); // GH-90000
            assertThat(report.pendingCount()).isEqualTo(items.size() - 1); // GH-90000
            assertThat(report.totalItems()).isEqualTo(items.size()); // GH-90000
        }

        @Test
        @DisplayName("campaign marked COMPLETED when all items reviewed")
        void campaignCompletedWhenAllReviewed() { // GH-90000
            RecertificationCampaign campaign = runPromise(() -> // GH-90000
                pipeline.createCampaign("tenant-1", "c1", RecertificationScope.AGENT_PERMISSIONS)); // GH-90000
            List<RecertificationItem> items = runPromise(() -> // GH-90000
                pipeline.getItems(campaign.campaignId())); // GH-90000

            // Review all items
            for (RecertificationItem item : items) { // GH-90000
                runPromise(() -> pipeline.certify(campaign.campaignId(), item.itemId(), "r")); // GH-90000
            }

            RecertificationReport report = runPromise(() -> // GH-90000
                pipeline.generateReport(campaign.campaignId())); // GH-90000
            assertThat(report.pendingCount()).isZero(); // GH-90000

            RecertificationCampaign updated = runPromise(() -> // GH-90000
                pipeline.getCampaign(campaign.campaignId())); // GH-90000
            assertThat(updated.status()).isEqualTo(CampaignStatus.COMPLETED); // GH-90000
            assertThat(updated.completedAt()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("report includes revoked items list for audit trail")
        void reportIncludesRevokedItems() { // GH-90000
            RecertificationCampaign campaign = runPromise(() -> // GH-90000
                pipeline.createCampaign("tenant-1", "c1", RecertificationScope.TOOL_REGISTRATIONS)); // GH-90000
            List<RecertificationItem> items = runPromise(() -> // GH-90000
                pipeline.getItems(campaign.campaignId())); // GH-90000

            runPromise(() -> pipeline.revoke(campaign.campaignId(), // GH-90000
                items.get(0).itemId(), "auditor", "Risk too high")); // GH-90000

            RecertificationReport report = runPromise(() -> // GH-90000
                pipeline.generateReport(campaign.campaignId())); // GH-90000
            assertThat(report.revokedItems()).hasSize(1); // GH-90000
            assertThat(report.revokedItems().get(0).decision()).isEqualTo(ItemDecision.REVOKED); // GH-90000
        }

        @Test
        @DisplayName("certificationRate is 1.0 when all items certified")
        void certificationRateOneWhenAllCertified() { // GH-90000
            RecertificationCampaign campaign = runPromise(() -> // GH-90000
                pipeline.createCampaign("tenant-1", "c1", RecertificationScope.POLICIES)); // GH-90000
            List<RecertificationItem> items = runPromise(() -> // GH-90000
                pipeline.getItems(campaign.campaignId())); // GH-90000

            for (RecertificationItem item : items) { // GH-90000
                runPromise(() -> pipeline.certify(campaign.campaignId(), item.itemId(), "r")); // GH-90000
            }

            RecertificationReport report = runPromise(() -> // GH-90000
                pipeline.generateReport(campaign.campaignId())); // GH-90000
            assertThat(report.certificationRate()).isEqualTo(1.0); // GH-90000
        }
    }

    @Nested
    @DisplayName("listCampaigns")
    class ListCampaigns {

        @Test
        @DisplayName("returns only campaigns for the queried tenant")
        void filtersByTenant() { // GH-90000
            runPromise(() -> pipeline.createCampaign("tenant-1", "c1", RecertificationScope.POLICIES)); // GH-90000
            runPromise(() -> pipeline.createCampaign("tenant-1", "c2", RecertificationScope.AGENT_PERMISSIONS)); // GH-90000
            runPromise(() -> pipeline.createCampaign("tenant-2", "c3", RecertificationScope.FULL)); // GH-90000

            List<RecertificationCampaign> t1 = runPromise(() -> pipeline.listCampaigns("tenant-1"));
            assertThat(t1).hasSize(2); // GH-90000
            assertThat(t1).allMatch(c -> c.tenantId().equals("tenant-1"));
        }
    }
}
