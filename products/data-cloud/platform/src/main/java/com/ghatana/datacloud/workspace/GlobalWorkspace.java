package com.ghatana.datacloud.workspace;

import com.ghatana.datacloud.client.ContextDocument;
import com.ghatana.datacloud.client.ContextGateway;
import com.ghatana.datacloud.client.LearningSignal;
import com.ghatana.datacloud.client.LearningSignalStore;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Global workspace for shared cognitive spotlight.
 *
 * <p><b>Purpose</b><br>
 * The GlobalWorkspace is the shared "consciousness" of the organizational brain.
 * It maintains a spotlight of high-salience items that require cross-system attention:
 * <ul>
 *   <li>Spotlight items are visible to all subscribers</li>
 *   <li>Emergency broadcasts notify all systems immediately</li>
 *   <li>Items are automatically evicted based on TTL</li>
 *   <li>Context documents are created for LLM access</li>
 * </ul>
 *
 * <p><b>Architecture</b><br>
 * <pre>
 * ┌─────────────────────────────────────────────────────────────┐
 * │                    GLOBAL WORKSPACE                          │
 * ├─────────────────────────────────────────────────────────────┤
 * │  ┌─────────────────────────────────────────────────────┐    │
 * │  │                   SPOTLIGHT                          │    │
 * │  │  • Priority Queue (by salience)                      │    │
 * │  │  • Max capacity with LRU eviction                    │    │
 * │  │  • TTL-based expiration                              │    │
 * │  └─────────────────────────────────────────────────────┘    │
 * │                           │                                  │
 * │                           ▼                                  │
 * │  ┌─────────────────────────────────────────────────────┐    │
 * │  │               NOTIFICATION SYSTEM                    │    │
 * │  │  • Normal: Notify interested subscribers             │    │
 * │  │  • Broadcast: Notify ALL subscribers                 │    │
 * │  │  • Redis pub-sub for distributed systems             │    │
 * │  └─────────────────────────────────────────────────────┘    │
 * │                           │                                  │
 * │                           ▼                                  │
 * │  ┌─────────────────────────────────────────────────────┐    │
 * │  │               CONTEXT INTEGRATION                    │    │
 * │  │  • ContextGateway for LLM access                     │    │
 * │  │  • Learning signals for feedback                     │    │
 * │  └─────────────────────────────────────────────────────┘    │
 * └─────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * GlobalWorkspace workspace = GlobalWorkspace.builder()
 *     .contextGateway(contextGateway)
 *     .signalStore(signalStore)
 *     .metricsCollector(metrics)
 *     .maxSpotlightSize(100)
 *     .build();
 *
 * // Subscribe to spotlight updates
 * workspace.subscribe(item -> {
 *     System.out.println("New spotlight item: " + item.getSummary());
 * });
 *
 * // Add item to spotlight
 * workspace.spotlight(item).getResult();
 *
 * // Emergency broadcast
 * workspace.broadcast(emergencyItem).getResult();
 * }</pre>
 *
 * @see SpotlightItem
 * @see com.ghatana.datacloud.attention.AttentionManager
 * @doc.type class
 * @doc.purpose Global cognitive workspace
 * @doc.layer core
 * @doc.pattern Publish-Subscribe, Observer
 */
@Slf4j
@Builder
public class GlobalWorkspace {

    /**
     * Default maximum spotlight size.
     */
    public static final int DEFAULT_MAX_SPOTLIGHT_SIZE = 100;

    /**
     * Default cleanup interval.
     */
    public static final Duration DEFAULT_CLEANUP_INTERVAL = Duration.ofMinutes(1);

    // Dependencies (optional)
    private final ContextGateway contextGateway;
    private final LearningSignalStore signalStore;
    private final MetricsCollector metricsCollector;

    // Configuration
    @Builder.Default
    private final int maxSpotlightSize = DEFAULT_MAX_SPOTLIGHT_SIZE;

    @Builder.Default
    private final Duration defaultTtl = SpotlightItem.DEFAULT_TTL;

    // State - using ConcurrentHashMap for thread-safe access
    private final ConcurrentHashMap<String, SpotlightItem> spotlight = new ConcurrentHashMap<>();

    // Subscribers
    private final CopyOnWriteArrayList<WorkspaceSubscriber> subscribers = new CopyOnWriteArrayList<>();

    // Metrics
    private final AtomicLong totalSpotlighted = new AtomicLong(0);
    private final AtomicLong totalBroadcasts = new AtomicLong(0);
    private final AtomicLong totalEvictions = new AtomicLong(0);

    /**
     * Add an item to the spotlight.
     *
     * @param item The item to spotlight
     * @return Promise completing when item is spotlighted
     */
    public Promise<Void> spotlight(SpotlightItem item) {
        Objects.requireNonNull(item, "item cannot be null");

        log.info("Adding item {} to spotlight (salience={})",
                item.getId(), item.getSalienceScore().getScore());

        // Enforce capacity limit
        enforceCapacity();

        // Add to spotlight
        spotlight.put(item.getId(), item);
        totalSpotlighted.incrementAndGet();

        // Notify subscribers
        notifySubscribers(item, NotificationType.SPOTLIGHT);

        // Create context document for LLM access
        Promise<Void> contextPromise = createContextDocument(item);

        // Emit learning signal
        Promise<Void> signalPromise = emitLearningSignal(item, "spotlight");

        // Record metrics
        recordMetrics("spotlight", item);

        return Promises.all(contextPromise, signalPromise)
                .map(v -> null);
    }

    /**
     * Broadcast an emergency item to all subscribers.
     *
     * @param item The emergency item to broadcast
     * @return Promise completing when broadcast is done
     */
    public Promise<Void> broadcast(SpotlightItem item) {
        Objects.requireNonNull(item, "item cannot be null");

        log.warn("BROADCAST: Emergency item {} (salience={})",
                item.getId(), item.getSalienceScore().getScore());

        // Add to spotlight with high priority
        SpotlightItem emergencyItem = item.toBuilder()
                .emergency(true)
                .priority(1)
                .build();

        spotlight.put(emergencyItem.getId(), emergencyItem);
        totalSpotlighted.incrementAndGet();
        totalBroadcasts.incrementAndGet();

        // Notify ALL subscribers immediately
        notifySubscribers(emergencyItem, NotificationType.BROADCAST);

        // Create context document
        Promise<Void> contextPromise = createContextDocument(emergencyItem);

        // Emit learning signal
        Promise<Void> signalPromise = emitLearningSignal(emergencyItem, "broadcast");

        // Record metrics
        recordMetrics("broadcast", emergencyItem);

        return Promises.all(contextPromise, signalPromise)
                .map(v -> null);
    }

    /**
     * Get an item from the spotlight.
     *
     * @param itemId The item ID
     * @return Optional containing the item if found
     */
    public Optional<SpotlightItem> get(String itemId) {
        SpotlightItem item = spotlight.get(itemId);
        if (item != null) {
            // Update access count
            SpotlightItem accessed = item.withAccess();
            spotlight.put(itemId, accessed);
            return Optional.of(accessed);
        }
        return Optional.empty();
    }

    /**
     * Remove an item from the spotlight.
     *
     * @param itemId The item ID to remove
     * @return true if item was removed
     */
    public boolean remove(String itemId) {
        SpotlightItem removed = spotlight.remove(itemId);
        if (removed != null) {
            notifySubscribers(removed, NotificationType.REMOVED);
            return true;
        }
        return false;
    }

    /**
     * Get all items in the spotlight.
     *
     * @return Unmodifiable list of spotlight items sorted by priority
     */
    public List<SpotlightItem> getAll() {
        return spotlight.values().stream()
                .filter(item -> !item.isExpired())
                .sorted((a, b) -> {
                    // Sort by priority (lower = higher priority)
                    int priorityCompare = Integer.compare(a.getPriority(), b.getPriority());
                    if (priorityCompare != 0) return priorityCompare;
                    // Then by salience score (higher = better)
                    return Double.compare(b.getSalienceScore().getScore(), a.getSalienceScore().getScore());
                })
                .toList();
    }

    /**
     * Get items matching a filter.
     *
     * @param filter Predicate to filter items
     * @return List of matching items
     */
    public List<SpotlightItem> query(Predicate<SpotlightItem> filter) {
        return spotlight.values().stream()
                .filter(item -> !item.isExpired())
                .filter(filter)
                .toList();
    }

    /**
     * Get items for a specific tenant.
     *
     * @param tenantId The tenant ID
     * @return List of items for the tenant
     */
    public List<SpotlightItem> getByTenant(String tenantId) {
        return query(item -> tenantId.equals(item.getTenantId()));
    }

    /**
     * Get emergency items only.
     *
     * @return List of emergency items
     */
    public List<SpotlightItem> getEmergencies() {
        return query(SpotlightItem::isEmergency);
    }

    /**
     * Subscribe to workspace updates.
     *
     * @param handler Consumer for spotlight items
     * @return Subscription handle for unsubscribing
     */
    public Subscription subscribe(Consumer<SpotlightItem> handler) {
        return subscribe(handler, item -> true);
    }

    /**
     * Subscribe to workspace updates with a filter.
     *
     * @param handler Consumer for spotlight items
     * @param filter  Predicate to filter items
     * @return Subscription handle for unsubscribing
     */
    public Subscription subscribe(Consumer<SpotlightItem> handler, Predicate<SpotlightItem> filter) {
        WorkspaceSubscriber subscriber = new WorkspaceSubscriber(
                UUID.randomUUID().toString(),
                handler,
                filter,
                false
        );
        subscribers.add(subscriber);

        log.debug("New subscriber {} added (total: {})", subscriber.id, subscribers.size());

        return new Subscription() {
            @Override
            public String getId() {
                return subscriber.id;
            }

            @Override
            public void unsubscribe() {
                subscribers.remove(subscriber);
                log.debug("Subscriber {} removed (total: {})", subscriber.id, subscribers.size());
            }

            @Override
            public boolean isActive() {
                return subscribers.contains(subscriber);
            }
        };
    }

    /**
     * Subscribe to broadcasts only.
     *
     * @param handler Consumer for broadcast items
     * @return Subscription handle
     */
    public Subscription subscribeToBroadcasts(Consumer<SpotlightItem> handler) {
        WorkspaceSubscriber subscriber = new WorkspaceSubscriber(
                UUID.randomUUID().toString(),
                handler,
                item -> true,
                true
        );
        subscribers.add(subscriber);
        return new Subscription() {
            @Override
            public String getId() {
                return subscriber.id;
            }

            @Override
            public void unsubscribe() {
                subscribers.remove(subscriber);
            }

            @Override
            public boolean isActive() {
                return subscribers.contains(subscriber);
            }
        };
    }

    /**
     * Clean up expired items.
     *
     * @return Number of items removed
     */
    public int cleanup() {
        int removed = 0;
        Iterator<Map.Entry<String, SpotlightItem>> iterator = spotlight.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<String, SpotlightItem> entry = iterator.next();
            if (entry.getValue().isExpired()) {
                iterator.remove();
                removed++;
                totalEvictions.incrementAndGet();
                notifySubscribers(entry.getValue(), NotificationType.EXPIRED);
            }
        }

        if (removed > 0) {
            log.info("Cleaned up {} expired spotlight items", removed);
        }

        return removed;
    }

    /**
     * Get current spotlight size.
     *
     * @return Number of items in spotlight
     */
    public int size() {
        return spotlight.size();
    }

    /**
     * Check if spotlight is empty.
     *
     * @return true if no items in spotlight
     */
    public boolean isEmpty() {
        return spotlight.isEmpty();
    }

    /**
     * Clear all items from spotlight.
     */
    public void clear() {
        spotlight.clear();
        log.info("Spotlight cleared");
    }

    /**
     * Get workspace statistics.
     *
     * @return WorkspaceStats snapshot
     */
    public WorkspaceStats getStats() {
        long emergencyCount = spotlight.values().stream()
                .filter(SpotlightItem::isEmergency)
                .count();

        return new WorkspaceStats(
                spotlight.size(),
                maxSpotlightSize,
                subscribers.size(),
                totalSpotlighted.get(),
                totalBroadcasts.get(),
                totalEvictions.get(),
                emergencyCount
        );
    }

    /**
     * Get all current spotlight items.
     *
     * @return List of spotlight items
     */
    public List<SpotlightItem> getSpotlightItems() {
        return new ArrayList<>(spotlight.values());
    }

    // ==================== Private Methods ====================

    private void enforceCapacity() {
        while (spotlight.size() >= maxSpotlightSize) {
            // Remove lowest priority, oldest item
            Optional<String> toRemove = spotlight.entrySet().stream()
                    .filter(e -> !e.getValue().isEmergency()) // Never evict emergencies
                    .min((a, b) -> {
                        SpotlightItem itemA = a.getValue();
                        SpotlightItem itemB = b.getValue();
                        // Higher priority number = lower priority
                        int priorityCompare = Integer.compare(itemB.getPriority(), itemA.getPriority());
                        if (priorityCompare != 0) return priorityCompare;
                        // Older items evicted first
                        return itemA.getSpotlightedAt().compareTo(itemB.getSpotlightedAt());
                    })
                    .map(Map.Entry::getKey);

            if (toRemove.isPresent()) {
                SpotlightItem evicted = spotlight.remove(toRemove.get());
                totalEvictions.incrementAndGet();
                notifySubscribers(evicted, NotificationType.EVICTED);
                log.debug("Evicted item {} to enforce capacity", toRemove.get());
            } else {
                break;
            }
        }
    }

    private void notifySubscribers(SpotlightItem item, NotificationType type) {
        for (WorkspaceSubscriber subscriber : subscribers) {
            try {
                // Broadcast-only subscribers only get broadcasts
                if (subscriber.broadcastOnly && type != NotificationType.BROADCAST) {
                    continue;
                }

                // Apply filter
                if (!subscriber.filter.test(item)) {
                    continue;
                }

                subscriber.handler.accept(item);
            } catch (Exception e) {
                log.warn("Error notifying subscriber {}: {}", subscriber.id, e.getMessage());
            }
        }
    }

    private Promise<Void> createContextDocument(SpotlightItem item) {
        if (contextGateway == null) {
            return Promise.complete();
        }

        ContextDocument ctx = ContextDocument.builder()
                .contextType(ContextDocument.ContextType.PATTERN)
                .tenantId(item.getTenantId())
                .content(item.getSummary())
                .structuredData(Map.of(
                        "spotlightId", item.getId(),
                        "salienceScore", item.getSalienceScore().getScore(),
                        "emergency", item.isEmergency(),
                        "priority", item.getPriority()
                ))
                .confidence(item.getSalienceScore().getScore())
                .determinism(com.ghatana.datacloud.spi.ai.PredictionCapability.DeterminismLevel.MEDIUM)
                .ttl(item.getTtl())
                .build();

        return contextGateway.store(ctx)
                .map(v -> (Void) null)
                .whenException(ex -> log.warn("Failed to store context document: {}", ex.getMessage()));
    }

    private Promise<Void> emitLearningSignal(SpotlightItem item, String action) {
        if (signalStore == null) {
            return Promise.complete();
        }

        LearningSignal signal = LearningSignal.builder()
                .signalType(LearningSignal.SignalType.OPERATIONAL)
                .tenantId(item.getTenantId())
                .source(LearningSignal.SignalSource.builder()
                        .plugin("global-workspace")
                        .build())
                .features(Map.of(
                        "action", action,
                        "salienceScore", item.getSalienceScore().getScore(),
                        "emergency", item.isEmergency(),
                        "priority", item.getPriority()
                ))
                .build();

        return signalStore.store(signal)
                .map(v -> (Void) null)
                .whenException(ex -> log.warn("Failed to store learning signal: {}", ex.getMessage()));
    }

    private void recordMetrics(String action, SpotlightItem item) {
        if (metricsCollector == null) {
            return;
        }

        metricsCollector.incrementCounter("workspace." + action);
        // Note: MetricsCollector doesn't have recordGauge, using incrementCounter for action tracking only
        if (item.isEmergency()) {
            metricsCollector.incrementCounter("workspace.emergency");
        }
    }

    // ==================== Inner Classes ====================

    /**
     * Subscription handle for managing workspace subscriptions.
     */
    public interface Subscription {
        String getId();
        void unsubscribe();
        boolean isActive();
    }

    /**
     * Notification type for subscriber callbacks.
     */
    private enum NotificationType {
        SPOTLIGHT,
        BROADCAST,
        REMOVED,
        EXPIRED,
        EVICTED
    }

    /**
     * Internal subscriber record.
     */
    private record WorkspaceSubscriber(
            String id,
            Consumer<SpotlightItem> handler,
            Predicate<SpotlightItem> filter,
            boolean broadcastOnly
    ) {}

    /**
     * Workspace statistics snapshot.
     */
    public record WorkspaceStats(
            int currentSize,
            int maxSize,
            int subscriberCount,
            long totalSpotlighted,
            long totalBroadcasts,
            long totalEvictions,
            long emergencyCount
    ) {
        public double utilizationPercent() {
            return maxSize > 0 ? (double) currentSize / maxSize * 100 : 0.0;
        }
    }
}
