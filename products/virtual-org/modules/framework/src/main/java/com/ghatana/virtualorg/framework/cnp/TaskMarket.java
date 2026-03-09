package com.ghatana.virtualorg.framework.cnp;

import com.ghatana.virtualorg.framework.task.TaskDefinition;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Implements the Contract Net Protocol for task allocation.
 *
 * <p><b>Purpose</b><br>
 * The TaskMarket is a marketplace where tasks are announced and agents
 * bid for execution rights. This enables dynamic, market-based task
 * allocation optimizing for efficiency and agent capabilities.
 *
 * <p><b>Protocol Flow</b><br>
 * <pre>
 *     Manager                    TaskMarket                    Agents
 *        |                           |                            |
 *        |-- announce(task) -------->|                            |
 *        |                           |-- broadcast --------------->|
 *        |                           |<-- bid --------------------|
 *        |                           |<-- bid --------------------|
 *        |                           |                            |
 *        |<-- evaluateBids() --------|                            |
 *        |-- awardContract() ------->|                            |
 *        |                           |-- notify winner ----------->|
 *        |                           |-- notify losers ----------->|
 * </pre>
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * TaskMarket market = new TaskMarket();
 *
 * // Manager announces a task
 * TaskAnnouncement announcement = market.announce(task, "manager-1", "dept-1",
 *     Duration.ofMinutes(5)).getResult();
 *
 * // Agents submit bids
 * market.submitBid(TaskBid.builder(announcement.id(), "agent-1")
 *     .estimatedDuration(Duration.ofHours(2))
 *     .confidence(0.9)
 *     .build());
 *
 * // Manager awards contract
 * Optional<TaskBid> winner = market.awardContract(announcement.id()).getResult();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Contract Net Protocol task marketplace
 * @doc.layer platform
 * @doc.pattern Mediator
 */
public class TaskMarket {

    private static final Logger LOG = LoggerFactory.getLogger(TaskMarket.class);

    private final Map<String, TaskAnnouncement> announcements = new ConcurrentHashMap<>();
    private final Map<String, List<TaskBid>> bids = new ConcurrentHashMap<>();
    private final Map<String, Contract> contracts = new ConcurrentHashMap<>();
    private final List<Consumer<TaskAnnouncement>> announcementListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final List<Consumer<Contract>> contractListeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    /**
     * Cleans up old announcements and bids.
     *
     * @param maxAge the maximum age of announcements to keep
     * @return number of removed announcements
     */
    public int cleanup(Duration maxAge) {
        Instant threshold = Instant.now().minus(maxAge);
        Set<String> toRemove = announcements.values().stream()
                .filter(a -> a.biddingDeadline().isBefore(threshold))
                .map(TaskAnnouncement::id)
                .collect(Collectors.toSet());

        for (String id : toRemove) {
            announcements.remove(id);
            bids.remove(id);
            // We might want to keep contracts longer, but for now let's keep them separate
        }
        
        if (!toRemove.isEmpty()) {
            LOG.info("Cleaned up {} expired task announcements", toRemove.size());
        }
        return toRemove.size();
    }

    /**
     * Announces a task for bidding.
     *
     * @param task the task to announce
     * @param managerId the manager announcing the task
     * @param departmentId the department
     * @param biddingDuration how long to accept bids
     * @return promise with the announcement
     */
    public Promise<TaskAnnouncement> announce(
            TaskDefinition task,
            String managerId,
            String departmentId,
            Duration biddingDuration) {

        Instant deadline = Instant.now().plus(biddingDuration);
        TaskAnnouncement announcement = TaskAnnouncement.create(
                task, managerId, departmentId, deadline
        );

        announcements.put(announcement.id(), announcement);
        bids.put(announcement.id(), Collections.synchronizedList(new ArrayList<>()));

        LOG.info("Task announced: '{}' by manager '{}', bidding ends at {}",
                task.name(), managerId, deadline);

        // Notify listeners
        announcementListeners.forEach(l -> l.accept(announcement));

        return Promise.of(announcement);
    }

    /**
     * Submits a bid for a task.
     *
     * @param bid the bid to submit
     * @return promise with true if bid accepted, false if bidding closed
     */
    public Promise<Boolean> submitBid(TaskBid bid) {
        TaskAnnouncement announcement = announcements.get(bid.announcementId());
        if (announcement == null) {
            return Promise.of(false);
        }

        if (!announcement.isBiddingOpen()) {
            LOG.warn("Bid rejected: bidding closed for announcement {}", bid.announcementId());
            return Promise.of(false);
        }

        bids.get(bid.announcementId()).add(bid);
        LOG.debug("Bid received from agent '{}' for task '{}', confidence: {}",
                bid.agentId(), announcement.task().name(), bid.confidenceScore());

        return Promise.of(true);
    }

    /**
     * Gets all bids for an announcement.
     *
     * @param announcementId the announcement ID
     * @return promise with list of bids
     */
    public Promise<List<TaskBid>> getBids(String announcementId) {
        List<TaskBid> bidList = bids.get(announcementId);
        return Promise.of(bidList != null ? new ArrayList<>(bidList) : List.of());
    }

    /**
     * Awards the contract to the best bidder.
     *
     * @param announcementId the announcement ID
     * @return promise with winning bid, or empty if no bids
     */
    public Promise<Optional<TaskBid>> awardContract(String announcementId) {
        return awardContract(announcementId, this::defaultBidComparator);
    }

    /**
     * Awards the contract using a custom comparator.
     *
     * @param announcementId the announcement ID
     * @param comparator the bid comparator
     * @return promise with winning bid
     */
    public Promise<Optional<TaskBid>> awardContract(
            String announcementId,
            Comparator<TaskBid> comparator) {

        TaskAnnouncement announcement = announcements.get(announcementId);
        if (announcement == null) {
            return Promise.of(Optional.empty());
        }

        List<TaskBid> bidList = bids.get(announcementId);
        if (bidList == null || bidList.isEmpty()) {
            LOG.warn("No bids received for task '{}'", announcement.task().name());
            announcements.put(announcementId, announcement.withStatus(
                    TaskAnnouncement.AnnouncementStatus.CANCELLED));
            return Promise.of(Optional.empty());
        }

        // Sort and select winner
        TaskBid winner = bidList.stream()
                .sorted(comparator.reversed())
                .findFirst()
                .orElseThrow();

        // Update statuses
        TaskBid acceptedBid = winner.withStatus(TaskBid.BidStatus.ACCEPTED);
        announcements.put(announcementId, announcement.withStatus(
                TaskAnnouncement.AnnouncementStatus.AWARDED));

        // Update other bids to rejected
        for (int i = 0; i < bidList.size(); i++) {
            if (!bidList.get(i).agentId().equals(winner.agentId())) {
                bidList.set(i, bidList.get(i).withStatus(TaskBid.BidStatus.REJECTED));
            }
        }

        // Create contract
        Contract contract = new Contract(
                UUID.randomUUID().toString(),
                announcement,
                acceptedBid,
                Instant.now(),
                Contract.ContractStatus.ACTIVE
        );
        contracts.put(contract.id(), contract);

        LOG.info("Contract awarded to agent '{}' for task '{}' (score: {})",
                winner.agentId(), announcement.task().name(), winner.calculateScore());

        // Notify listeners
        contractListeners.forEach(l -> l.accept(contract));

        return Promise.of(Optional.of(acceptedBid));
    }

    /**
     * Adds a listener for new announcements.
     */
    public void addAnnouncementListener(Consumer<TaskAnnouncement> listener) {
        announcementListeners.add(listener);
    }

    /**
     * Adds a listener for awarded contracts.
     */
    public void addContractListener(Consumer<Contract> listener) {
        contractListeners.add(listener);
    }

    /**
     * Gets all open announcements.
     */
    public Promise<List<TaskAnnouncement>> getOpenAnnouncements() {
        return Promise.of(announcements.values().stream()
                .filter(TaskAnnouncement::isBiddingOpen)
                .collect(Collectors.toList()));
    }

    /**
     * Gets active contracts for an agent.
     */
    public Promise<List<Contract>> getAgentContracts(String agentId) {
        return Promise.of(contracts.values().stream()
                .filter(c -> c.winningBid().agentId().equals(agentId))
                .filter(c -> c.status() == Contract.ContractStatus.ACTIVE)
                .collect(Collectors.toList()));
    }

    private int defaultBidComparator(TaskBid a, TaskBid b) {
        return Double.compare(a.calculateScore(), b.calculateScore());
    }

    /**
     * Represents an awarded contract.
     */
    public record Contract(
            String id,
            TaskAnnouncement announcement,
            TaskBid winningBid,
            Instant awardedAt,
            ContractStatus status
    ) {
        public enum ContractStatus {
            ACTIVE,
            COMPLETED,
            FAILED,
            CANCELLED
        }
    }
}
