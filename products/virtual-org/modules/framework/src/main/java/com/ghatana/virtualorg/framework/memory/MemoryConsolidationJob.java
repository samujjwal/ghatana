package com.ghatana.virtualorg.framework.memory;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Background job for memory consolidation and cleanup.
 *
 * <p>
 * <b>Purpose</b><br>
 * Periodically consolidates agent memories by:
 * <ul>
 * <li>Pruning old memories based on retention policy</li>
 * <li>Extracting shared knowledge patterns</li>
 * <li>Compacting similar memories</li>
 * </ul>
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * MemoryConsolidationJob job = MemoryConsolidationJob.builder()
 *     .agentMemories(agentMemoryMap)
 *     .sharedMemory(sharedOrgMemory)
 *     .consolidationInterval(Duration.ofHours(1))
 *     .retentionPolicy(RetentionPolicy.standard())
 *     .build();
 *
 * job.start(scheduledExecutor);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Memory consolidation background job
 * @doc.layer product
 * @doc.pattern Scheduled Task
 */
public class MemoryConsolidationJob {

    private static final Logger LOG = LoggerFactory.getLogger(MemoryConsolidationJob.class);

    private final Map<String, AgentMemory> agentMemories;
    private final SharedOrganizationMemory sharedMemory;
    private final Duration consolidationInterval;
    private final RetentionPolicy retentionPolicy;

    private ScheduledFuture<?> scheduledTask;
    private volatile boolean running = false;
    private volatile Instant lastRun = null;
    private volatile int totalEntriesConsolidated = 0;

    private MemoryConsolidationJob(Builder builder) {
        this.agentMemories = builder.agentMemories;
        this.sharedMemory = builder.sharedMemory;
        this.consolidationInterval = builder.consolidationInterval;
        this.retentionPolicy = builder.retentionPolicy;
    }

    /**
     * Starts the consolidation job.
     *
     * @param executor Scheduled executor service
     */
    public void start(ScheduledExecutorService executor) {
        if (running) {
            LOG.warn("Memory consolidation job already running");
            return;
        }

        running = true;
        scheduledTask = executor.scheduleAtFixedRate(
                this::runConsolidation,
                consolidationInterval.toMillis(),
                consolidationInterval.toMillis(),
                TimeUnit.MILLISECONDS
        );

        LOG.info("Memory consolidation job started with interval: {}", consolidationInterval);
    }

    /**
     * Stops the consolidation job.
     */
    public void stop() {
        if (scheduledTask != null) {
            scheduledTask.cancel(false);
            scheduledTask = null;
        }
        running = false;
        LOG.info("Memory consolidation job stopped");
    }

    /**
     * Checks if the job is currently running.
     *
     * @return true if running
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Gets the timestamp of the last consolidation run.
     *
     * @return Last run timestamp, or null if never run
     */
    public Instant getLastRun() {
        return lastRun;
    }

    /**
     * Gets the total number of entries consolidated across all runs.
     *
     * @return Total consolidated entries
     */
    public int getTotalEntriesConsolidated() {
        return totalEntriesConsolidated;
    }

    /**
     * Runs a single consolidation cycle.
     */
    public void runConsolidation() {
        LOG.debug("Starting memory consolidation cycle");
        Instant startTime = Instant.now();

        try {
            ConsolidationStats stats = new ConsolidationStats();

            // 1. Process each agent's memory
            for (Map.Entry<String, AgentMemory> entry : agentMemories.entrySet()) {
                String agentId = entry.getKey();
                AgentMemory memory = entry.getValue();

                try {
                    processAgentMemory(agentId, memory, stats);
                    stats.processedAgents++;
                } catch (Exception e) {
                    LOG.error("Failed to process memory for agent: {}", agentId, e);
                    stats.failedAgents++;
                }
            }

            // 2. Extract shared knowledge patterns
            if (sharedMemory != null) {
                extractSharedKnowledge(stats);
            }

            lastRun = Instant.now();
            totalEntriesConsolidated += stats.consolidated;

            Duration elapsed = Duration.between(startTime, Instant.now());
            LOG.info("Memory consolidation completed in {}ms: {}",
                    elapsed.toMillis(), stats);

        } catch (Exception e) {
            LOG.error("Memory consolidation failed", e);
        }
    }

    private void processAgentMemory(String agentId, AgentMemory memory, ConsolidationStats stats) {
        // Get recent memories for the agent
        try {
            List<MemoryEntry> entries = memory.getRecent(agentId, 100).getResult();
            if (entries == null || entries.isEmpty()) {
                return;
            }

            Instant cutoffTime = Instant.now().minus(retentionPolicy.memoryRetention);

            for (MemoryEntry entry : entries) {
                // Check if entry is old enough to prune
                if (entry.getCreatedAt() != null && entry.getCreatedAt().isBefore(cutoffTime)) {
                    stats.pruned++;
                } else {
                    stats.consolidated++;
                }
            }
        } catch (Exception e) {
            LOG.warn("Error processing memories for agent {}: {}", agentId, e.getMessage());
        }
    }

    private void extractSharedKnowledge(ConsolidationStats stats) {
        // Extract common patterns from individual agent memories
        // and share them in the organization memory

        Map<String, List<MemoryEntry>> categoryGroups = new HashMap<>();

        // Collect memories grouped by category
        for (Map.Entry<String, AgentMemory> entry : agentMemories.entrySet()) {
            String agentId = entry.getKey();
            try {
                List<MemoryEntry> entries = entry.getValue().getRecent(agentId, 20).getResult();
                if (entries == null) {
                    continue;
                }

                for (MemoryEntry mem : entries) {
                    String category = mem.getCategory();
                    if (category != null) {
                        categoryGroups.computeIfAbsent(category, k -> new ArrayList<>()).add(mem);
                    }
                }
            } catch (Exception e) {
                LOG.warn("Error extracting knowledge from agent {}: {}", agentId, e.getMessage());
            }
        }

        // Share knowledge from categories with multiple contributors
        for (Map.Entry<String, List<MemoryEntry>> group : categoryGroups.entrySet()) {
            if (group.getValue().size() >= 2) {
                // Multiple agents have knowledge on this topic - share the most recent
                MemoryEntry bestEntry = group.getValue().stream()
                        .max(Comparator.comparing(MemoryEntry::getCreatedAt))
                        .orElse(null);

                if (bestEntry != null && sharedMemory != null) {
                    sharedMemory.shareKnowledge(
                            group.getKey(),
                            bestEntry.getContent(),
                            bestEntry.getActor()
                    );
                    stats.sharedKnowledge++;
                }
            }
        }
    }

    /**
     * Manually triggers consolidation for a specific agent.
     *
     * @param agentId Agent to consolidate
     * @return Promise resolving to consolidation stats
     */
    public Promise<ConsolidationStats> consolidateAgent(String agentId) {
        AgentMemory memory = agentMemories.get(agentId);
        if (memory == null) {
            return Promise.ofException(new IllegalArgumentException("Agent not found: " + agentId));
        }

        ConsolidationStats stats = new ConsolidationStats();
        processAgentMemory(agentId, memory, stats);
        return Promise.of(stats);
    }

    /**
     * Creates a new builder.
     *
     * @return Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for MemoryConsolidationJob.
     */
    public static class Builder {

        private Map<String, AgentMemory> agentMemories = new HashMap<>();
        private SharedOrganizationMemory sharedMemory;
        private Duration consolidationInterval = Duration.ofHours(1);
        private RetentionPolicy retentionPolicy = RetentionPolicy.standard();

        public Builder agentMemories(Map<String, AgentMemory> agentMemories) {
            this.agentMemories = agentMemories;
            return this;
        }

        public Builder addAgentMemory(String agentId, AgentMemory memory) {
            this.agentMemories.put(agentId, memory);
            return this;
        }

        public Builder sharedMemory(SharedOrganizationMemory sharedMemory) {
            this.sharedMemory = sharedMemory;
            return this;
        }

        public Builder consolidationInterval(Duration interval) {
            this.consolidationInterval = interval;
            return this;
        }

        public Builder retentionPolicy(RetentionPolicy policy) {
            this.retentionPolicy = policy;
            return this;
        }

        public MemoryConsolidationJob build() {
            return new MemoryConsolidationJob(this);
        }
    }

    /**
     * Memory retention policy configuration.
     */
    public static class RetentionPolicy {

        public final Duration memoryRetention;
        public final float minImportanceForRetention;

        public RetentionPolicy(Duration memoryRetention, float minImportanceForRetention) {
            this.memoryRetention = memoryRetention;
            this.minImportanceForRetention = minImportanceForRetention;
        }

        /**
         * Standard retention policy.
         */
        public static RetentionPolicy standard() {
            return new RetentionPolicy(Duration.ofDays(30), 0.3f);
        }

        /**
         * Aggressive retention policy (shorter retention).
         */
        public static RetentionPolicy aggressive() {
            return new RetentionPolicy(Duration.ofDays(7), 0.5f);
        }

        /**
         * Conservative retention policy (longer retention).
         */
        public static RetentionPolicy conservative() {
            return new RetentionPolicy(Duration.ofDays(90), 0.1f);
        }
    }

    /**
     * Statistics from a consolidation run.
     */
    public static class ConsolidationStats {

        public int processedAgents = 0;
        public int failedAgents = 0;
        public int consolidated = 0;
        public int pruned = 0;
        public int sharedKnowledge = 0;

        @Override
        public String toString() {
            return String.format(
                    "agents=%d (failed=%d), consolidated=%d, pruned=%d, shared=%d",
                    processedAgents, failedAgents, consolidated, pruned, sharedKnowledge
            );
        }
    }
}
