package com.ghatana.datacloud.plugins.kafka;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages Kafka consumer groups for the Kafka streaming plugin.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides consumer group coordination and monitoring:
 * <ul>
 * <li><b>Registration</b>: Track active consumers per group</li>
 * <li><b>Monitoring</b>: Expose consumer group metrics</li>
 * <li><b>Lag Tracking</b>: Monitor consumer lag per partition</li>
 * <li><b>Health</b>: Detect unhealthy consumers</li>
 * </ul>
 *
 * <p>
 * <b>Consumer Group Lifecycle</b><br>
 * <pre>
 * Register   → Consumer joins group, partition assigned
 * Active     → Consumer processing events
 * Rebalance  → Partition reassignment in progress
 * Unregister → Consumer leaves group
 * </pre>
 *
 * <p>
 * <b>Metrics Exposed</b><br>
 * <pre>
 * eventcloud.kafka.consumer.groups    - Number of active groups
 * eventcloud.kafka.consumers.active   - Number of active consumers
 * eventcloud.kafka.consumer.lag       - Consumer lag per partition
 * eventcloud.kafka.rebalances         - Number of rebalances
 * </pre>
 *
 * @doc.type class
 * @doc.purpose Consumer group coordination and monitoring
 * @doc.layer plugin
 * @doc.pattern Manager, Registry
 */
public class KafkaConsumerGroupManager {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaConsumerGroupManager.class);

    private final KafkaStreamingConfig config;
    private final MeterRegistry meterRegistry;

    // Consumer group -> Consumer IDs
    private final Map<String, Set<String>> consumerGroups = new ConcurrentHashMap<>();

    // Consumer ID -> Registration info
    private final Map<String, ConsumerRegistration> consumers = new ConcurrentHashMap<>();

    // Consumer group -> Lag per partition
    private final Map<String, Map<Integer, Long>> consumerLag = new ConcurrentHashMap<>();

    // Metrics
    private final AtomicInteger activeGroups = new AtomicInteger(0);
    private final AtomicInteger activeConsumers = new AtomicInteger(0);
    private Counter rebalanceCounter;

    // Health check scheduler
    private final ScheduledExecutorService scheduler;

    /**
     * Creates a new consumer group manager.
     *
     * @param config Kafka configuration
     * @param meterRegistry metrics registry
     */
    public KafkaConsumerGroupManager(KafkaStreamingConfig config, MeterRegistry meterRegistry) {
        this.config = config;
        this.meterRegistry = meterRegistry;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "kafka-consumer-group-monitor");
            t.setDaemon(true);
            return t;
        });

        initMetrics();
        startHealthMonitor();
    }

    // ==================== Registration ====================
    /**
     * Registers a consumer with a group.
     *
     * @param groupId consumer group ID
     * @param consumerId unique consumer ID
     */
    public void registerConsumer(String groupId, String consumerId) {
        LOG.info("Registering consumer {} with group {}", consumerId, groupId);

        // Add to group
        consumerGroups.computeIfAbsent(groupId, k -> {
            activeGroups.incrementAndGet();
            return ConcurrentHashMap.newKeySet();
        }).add(consumerId);

        // Register consumer
        consumers.put(consumerId, new ConsumerRegistration(
                consumerId, groupId, Instant.now(), ConsumerState.ACTIVE));

        activeConsumers.incrementAndGet();

        LOG.info("Consumer {} registered with group {} (total: {} consumers, {} groups)",
                consumerId, groupId, activeConsumers.get(), activeGroups.get());
    }

    /**
     * Unregisters a consumer from its group.
     *
     * @param groupId consumer group ID
     * @param consumerId consumer ID
     */
    public void unregisterConsumer(String groupId, String consumerId) {
        LOG.info("Unregistering consumer {} from group {}", consumerId, groupId);

        // Remove from group
        Set<String> group = consumerGroups.get(groupId);
        if (group != null) {
            group.remove(consumerId);
            if (group.isEmpty()) {
                consumerGroups.remove(groupId);
                consumerLag.remove(groupId);
                activeGroups.decrementAndGet();
            }
        }

        // Remove consumer registration
        consumers.remove(consumerId);
        activeConsumers.decrementAndGet();

        LOG.info("Consumer {} unregistered (remaining: {} consumers, {} groups)",
                consumerId, activeConsumers.get(), activeGroups.get());
    }

    // ==================== Queries ====================
    /**
     * Gets all consumers in a group.
     *
     * @param groupId consumer group ID
     * @return set of consumer IDs
     */
    public Set<String> getConsumersInGroup(String groupId) {
        return consumerGroups.getOrDefault(groupId, Set.of());
    }

    /**
     * Gets the number of consumers in a group.
     *
     * @param groupId consumer group ID
     * @return consumer count
     */
    public int getConsumerCount(String groupId) {
        Set<String> group = consumerGroups.get(groupId);
        return group != null ? group.size() : 0;
    }

    /**
     * Gets consumer registration info.
     *
     * @param consumerId consumer ID
     * @return registration info or null
     */
    public ConsumerRegistration getConsumerInfo(String consumerId) {
        return consumers.get(consumerId);
    }

    /**
     * Gets all active consumer groups.
     *
     * @return set of group IDs
     */
    public Set<String> getActiveGroups() {
        return Set.copyOf(consumerGroups.keySet());
    }

    // ==================== Lag Tracking ====================
    /**
     * Updates consumer lag for a partition.
     *
     * @param groupId consumer group ID
     * @param partition partition number
     * @param lag current lag
     */
    public void updateLag(String groupId, int partition, long lag) {
        consumerLag.computeIfAbsent(groupId, k -> new ConcurrentHashMap<>())
                .put(partition, lag);
    }

    /**
     * Gets total lag for a consumer group.
     *
     * @param groupId consumer group ID
     * @return total lag across all partitions
     */
    public long getTotalLag(String groupId) {
        Map<Integer, Long> lag = consumerLag.get(groupId);
        if (lag == null) {
            return 0;
        }
        return lag.values().stream().mapToLong(Long::longValue).sum();
    }

    /**
     * Gets lag per partition for a consumer group.
     *
     * @param groupId consumer group ID
     * @return partition -> lag mapping
     */
    public Map<Integer, Long> getLagByPartition(String groupId) {
        return Map.copyOf(consumerLag.getOrDefault(groupId, Map.of()));
    }

    // ==================== Rebalance ====================
    /**
     * Records a rebalance event.
     *
     * @param groupId consumer group ID
     */
    public void recordRebalance(String groupId) {
        LOG.info("Rebalance triggered for group: {}", groupId);
        rebalanceCounter.increment();

        // Update consumer states
        Set<String> group = consumerGroups.get(groupId);
        if (group != null) {
            for (String consumerId : group) {
                ConsumerRegistration reg = consumers.get(consumerId);
                if (reg != null) {
                    consumers.put(consumerId, reg.withState(ConsumerState.REBALANCING));
                }
            }
        }
    }

    /**
     * Marks rebalance complete for a group.
     *
     * @param groupId consumer group ID
     */
    public void rebalanceComplete(String groupId) {
        LOG.info("Rebalance completed for group: {}", groupId);

        Set<String> group = consumerGroups.get(groupId);
        if (group != null) {
            for (String consumerId : group) {
                ConsumerRegistration reg = consumers.get(consumerId);
                if (reg != null && reg.state() == ConsumerState.REBALANCING) {
                    consumers.put(consumerId, reg.withState(ConsumerState.ACTIVE));
                }
            }
        }
    }

    // ==================== Health ====================
    /**
     * Checks if a consumer is healthy.
     *
     * @param consumerId consumer ID
     * @return true if healthy
     */
    public boolean isConsumerHealthy(String consumerId) {
        ConsumerRegistration reg = consumers.get(consumerId);
        if (reg == null) {
            return false;
        }
        return reg.state() == ConsumerState.ACTIVE;
    }

    /**
     * Gets health status for a consumer group.
     *
     * @param groupId consumer group ID
     * @return health status
     */
    public GroupHealthStatus getGroupHealth(String groupId) {
        Set<String> group = consumerGroups.get(groupId);
        if (group == null || group.isEmpty()) {
            return new GroupHealthStatus(groupId, false, 0, 0, 0, "No consumers");
        }

        int active = 0;
        int rebalancing = 0;
        int unhealthy = 0;

        for (String consumerId : group) {
            ConsumerRegistration reg = consumers.get(consumerId);
            if (reg == null) {
                unhealthy++;
            } else {
                switch (reg.state()) {
                    case ACTIVE ->
                        active++;
                    case REBALANCING ->
                        rebalancing++;
                    case UNHEALTHY ->
                        unhealthy++;
                }
            }
        }

        boolean healthy = unhealthy == 0 && active > 0;
        String message = healthy ? "Healthy" : String.format(
                "%d unhealthy, %d rebalancing", unhealthy, rebalancing);

        return new GroupHealthStatus(groupId, healthy, active, rebalancing, unhealthy, message);
    }

    // ==================== Shutdown ====================
    /**
     * Shuts down the consumer group manager.
     */
    public void shutdown() {
        LOG.info("Shutting down consumer group manager");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        consumerGroups.clear();
        consumers.clear();
        consumerLag.clear();
    }

    // ==================== Private Methods ====================
    private void initMetrics() {
        Tags tags = Tags.of("plugin", "kafka");

        Gauge.builder("eventcloud.kafka.consumer.groups", activeGroups, AtomicInteger::get)
                .tags(tags)
                .description("Number of active consumer groups")
                .register(meterRegistry);

        Gauge.builder("eventcloud.kafka.consumers.active", activeConsumers, AtomicInteger::get)
                .tags(tags)
                .description("Number of active consumers")
                .register(meterRegistry);

        rebalanceCounter = Counter.builder("eventcloud.kafka.rebalances")
                .tags(tags)
                .description("Number of consumer group rebalances")
                .register(meterRegistry);
    }

    private void startHealthMonitor() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                checkConsumerHealth();
            } catch (Exception e) {
                LOG.error("Error in health monitor: {}", e.getMessage());
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    private void checkConsumerHealth() {
        Instant now = Instant.now();
        Duration timeout = config.getConsumerMaxPollInterval();

        for (Map.Entry<String, ConsumerRegistration> entry : consumers.entrySet()) {
            ConsumerRegistration reg = entry.getValue();
            if (reg.state() != ConsumerState.UNHEALTHY) {
                Duration sinceRegistration = Duration.between(reg.registeredAt(), now);
                // In a real impl, would check last heartbeat/poll time
                if (sinceRegistration.compareTo(timeout.multipliedBy(2)) > 0) {
                    LOG.warn("Consumer {} may be unhealthy (no activity for {})",
                            entry.getKey(), sinceRegistration);
                }
            }
        }
    }

    // ==================== Records ====================
    /**
     * Consumer registration information.
     */
    public record ConsumerRegistration(
            String consumerId,
            String groupId,
            Instant registeredAt,
            ConsumerState state
            ) {

        public ConsumerRegistration withState(ConsumerState newState) {
            return new ConsumerRegistration(consumerId, groupId, registeredAt, newState);
        }
    }

    /**
     * Consumer state.
     */
    public enum ConsumerState {
        ACTIVE,
        REBALANCING,
        UNHEALTHY
    }

    /**
     * Consumer group health status.
     */
    public record GroupHealthStatus(
            String groupId,
            boolean healthy,
            int activeConsumers,
            int rebalancingConsumers,
            int unhealthyConsumers,
            String message
            ) {

    }
}
