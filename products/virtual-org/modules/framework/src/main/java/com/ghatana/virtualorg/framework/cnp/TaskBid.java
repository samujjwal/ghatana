package com.ghatana.virtualorg.framework.cnp;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a bid submitted by an agent for a task.
 *
 * <p><b>Purpose</b><br>
 * Agents submit bids indicating their capability and estimated effort
 * for completing a task. The manager evaluates bids and awards the contract.
 *
 * <p><b>Bid Evaluation Criteria</b><br>
 * - Estimated duration (lower is better)
 * - Confidence score (higher is better)
 * - Agent's past performance
 * - Current workload
 *
 * @doc.type record
 * @doc.purpose Agent bid for CNP task
 * @doc.layer platform
 * @doc.pattern Value Object
 */
public record TaskBid(
        String id,
        String announcementId,
        String agentId,
        String agentName,
        Duration estimatedDuration,
        double confidenceScore,
        int currentWorkload,
        Map<String, Object> capabilities,
        String justification,
        Instant submittedAt,
        BidStatus status
) {

    /**
     * Compact constructor with defaults.
     */
    public TaskBid {
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        if (confidenceScore < 0 || confidenceScore > 1) {
            throw new IllegalArgumentException("Confidence score must be between 0 and 1");
        }
        capabilities = capabilities != null ? Map.copyOf(capabilities) : Map.of();
        justification = justification != null ? justification : "";
        submittedAt = submittedAt != null ? submittedAt : Instant.now();
        status = status != null ? status : BidStatus.PENDING;
    }

    /**
     * Creates a new bid.
     */
    public static Builder builder(String announcementId, String agentId) {
        return new Builder(announcementId, agentId);
    }

    /**
     * Creates a copy with updated status.
     */
    public TaskBid withStatus(BidStatus newStatus) {
        return new TaskBid(
                id, announcementId, agentId, agentName, estimatedDuration,
                confidenceScore, currentWorkload, capabilities, justification, submittedAt, newStatus
        );
    }

    /**
     * Calculates a bid score for comparison.
     * Higher score = better bid.
     */
    public double calculateScore() {
        // Weighted scoring: confidence (40%), speed (40%), availability (20%)
        double speedScore = 1.0 / (1.0 + estimatedDuration.toHours());
        double availabilityScore = 1.0 / (1.0 + currentWorkload);
        return (confidenceScore * 0.4) + (speedScore * 0.4) + (availabilityScore * 0.2);
    }

    /**
     * Bid status.
     */
    public enum BidStatus {
        PENDING,    // Under evaluation
        ACCEPTED,   // Won the contract
        REJECTED,   // Not selected
        WITHDRAWN   // Agent withdrew bid
    }

    /**
     * Builder for TaskBid.
     */
    public static class Builder {
        private final String announcementId;
        private final String agentId;
        private String agentName = "";
        private Duration estimatedDuration = Duration.ofHours(1);
        private double confidenceScore = 0.5;
        private int currentWorkload = 0;
        private Map<String, Object> capabilities = Map.of();
        private String justification = "";

        private Builder(String announcementId, String agentId) {
            this.announcementId = announcementId;
            this.agentId = agentId;
        }

        public Builder agentName(String name) {
            this.agentName = name;
            return this;
        }

        public Builder estimatedDuration(Duration duration) {
            this.estimatedDuration = duration;
            return this;
        }

        public Builder confidence(double score) {
            this.confidenceScore = score;
            return this;
        }

        public Builder currentWorkload(int workload) {
            this.currentWorkload = workload;
            return this;
        }

        public Builder capabilities(Map<String, Object> caps) {
            this.capabilities = caps;
            return this;
        }

        public Builder justification(String text) {
            this.justification = text;
            return this;
        }

        public TaskBid build() {
            return new TaskBid(
                    null, announcementId, agentId, agentName, estimatedDuration,
                    confidenceScore, currentWorkload, capabilities, justification, null, null
            );
        }
    }
}
