package com.ghatana.virtualorg.framework.cnp;

import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.virtualorg.framework.task.TaskDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the Contract Net Protocol implementation.
 */
@DisplayName("Contract Net Protocol (Task Market) Tests")
class TaskMarketTest {

    private TaskMarket market;
    private TenantId tenantId;

    @BeforeEach
    void setUp() {
        market = new TaskMarket();
        tenantId = TenantId.of("test-tenant");
    }

    private TaskDefinition createTestTask(String name) {
        return TaskDefinition.builder(tenantId)
                .name(name)
                .description("Test task: " + name)
                .estimatedDuration(Duration.ofHours(2))
                .build();
    }

    @Test
    @DisplayName("Should announce task and accept bids")
    void shouldAnnounceTaskAndAcceptBids() {
        TaskDefinition task = createTestTask("Code Review");

        TaskAnnouncement announcement = market.announce(
                task, "manager-1", "engineering", Duration.ofMinutes(5)
        ).getResult();

        assertThat(announcement.id()).isNotNull();
        assertThat(announcement.task()).isEqualTo(task);
        assertThat(announcement.isBiddingOpen()).isTrue();

        // Submit a bid
        TaskBid bid = TaskBid.builder(announcement.id(), "agent-1")
                .agentName("Alice")
                .estimatedDuration(Duration.ofHours(1))
                .confidence(0.9)
                .build();

        boolean accepted = market.submitBid(bid).getResult();
        assertThat(accepted).isTrue();

        List<TaskBid> bids = market.getBids(announcement.id()).getResult();
        assertThat(bids).hasSize(1);
    }

    @Test
    @DisplayName("Should award contract to best bidder")
    void shouldAwardContractToBestBidder() {
        TaskDefinition task = createTestTask("Feature Implementation");

        TaskAnnouncement announcement = market.announce(
                task, "manager-1", "engineering", Duration.ofMinutes(5)
        ).getResult();

        // Submit multiple bids with different scores
        TaskBid lowBid = TaskBid.builder(announcement.id(), "agent-low")
                .agentName("Low Performer")
                .estimatedDuration(Duration.ofHours(8))
                .confidence(0.5)
                .currentWorkload(5)
                .build();

        TaskBid highBid = TaskBid.builder(announcement.id(), "agent-high")
                .agentName("High Performer")
                .estimatedDuration(Duration.ofHours(2))
                .confidence(0.95)
                .currentWorkload(1)
                .build();

        market.submitBid(lowBid).getResult();
        market.submitBid(highBid).getResult();

        Optional<TaskBid> winner = market.awardContract(announcement.id()).getResult();

        assertThat(winner).isPresent();
        assertThat(winner.get().agentId()).isEqualTo("agent-high");
        assertThat(winner.get().status()).isEqualTo(TaskBid.BidStatus.ACCEPTED);
    }

    @Test
    @DisplayName("Should calculate bid score correctly")
    void shouldCalculateBidScoreCorrectly() {
        TaskBid highConfidenceBid = TaskBid.builder("ann-1", "agent-1")
                .confidence(0.9)
                .estimatedDuration(Duration.ofHours(2))
                .currentWorkload(1)
                .build();

        TaskBid lowConfidenceBid = TaskBid.builder("ann-1", "agent-2")
                .confidence(0.5)
                .estimatedDuration(Duration.ofHours(4))
                .currentWorkload(5)
                .build();

        assertThat(highConfidenceBid.calculateScore())
                .isGreaterThan(lowConfidenceBid.calculateScore());
    }

    @Test
    @DisplayName("Should reject bids after deadline")
    void shouldRejectBidsAfterDeadline() throws InterruptedException {
        TaskDefinition task = createTestTask("Urgent Task");

        TaskAnnouncement announcement = market.announce(
                task, "manager-1", "engineering", Duration.ofMillis(10)
        ).getResult();

        // Wait for bidding period to end
        Thread.sleep(50);

        TaskBid lateBid = TaskBid.builder(announcement.id(), "late-agent")
                .confidence(0.9)
                .build();

        boolean accepted = market.submitBid(lateBid).getResult();
        assertThat(accepted).isFalse();
    }

    @Test
    @DisplayName("Should handle no bids scenario")
    void shouldHandleNoBidsScenario() {
        TaskDefinition task = createTestTask("Unpopular Task");

        TaskAnnouncement announcement = market.announce(
                task, "manager-1", "engineering", Duration.ofMinutes(5)
        ).getResult();

        Optional<TaskBid> winner = market.awardContract(announcement.id()).getResult();

        assertThat(winner).isEmpty();
    }

    @Test
    @DisplayName("Should get open announcements")
    void shouldGetOpenAnnouncements() {
        TaskDefinition task1 = createTestTask("Task 1");
        TaskDefinition task2 = createTestTask("Task 2");

        market.announce(task1, "manager-1", "dept-1", Duration.ofMinutes(5)).getResult();
        market.announce(task2, "manager-1", "dept-1", Duration.ofMinutes(5)).getResult();

        List<TaskAnnouncement> openAnnouncements = market.getOpenAnnouncements().getResult();
        assertThat(openAnnouncements).hasSize(2);
    }
}
