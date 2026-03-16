package com.ghatana.appplatform.marketdata.feed;

import com.ghatana.appplatform.marketdata.domain.MarketTick;
import com.ghatana.appplatform.marketdata.domain.TickSource;
import com.ghatana.appplatform.marketdata.service.MarketDataIngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * @doc.type       Application Service
 * @doc.purpose    Manages all registered MarketDataFeedAdapters and implements the
 *                 multi-source priority failover logic.
 *
 *                 Priority tiers (from K-02 config):
 *                   PRIMARY   → first choice; adapter reports source = PRIMARY
 *                   SECONDARY → used when primary is disconnected
 *                   MANUAL    → manual corrections only; not auto-connected
 *
 *                 On PRIMARY adapter disconnect:
 *                   1. Logs a WARN with the adapter ID.
 *                   2. Activates the highest-priority SECONDARY adapter immediately.
 *                   3. Schedules a reconnect probe for the PRIMARY every 30 seconds.
 *                   4. Restores PRIMARY and deactivates SECONDARY on successful reconnect.
 *
 *                 D04-002: multi_source_normalization, source_priority_failover.
 * @doc.layer      Application Service
 * @doc.pattern    Registry / Failover Strategy
 */
public class MarketDataFeedAdapterRegistry {

    private static final Logger log = LoggerFactory.getLogger(MarketDataFeedAdapterRegistry.class);
    private static final int RECONNECT_INTERVAL_SECONDS = 30;

    private final MarketDataIngestionService ingestionService;
    private final ScheduledExecutorService scheduler;

    /** adapterId → adapter */
    private final Map<String, MarketDataFeedAdapter> adapters = new ConcurrentHashMap<>();
    /** adapterId → TickSource tier */
    private final Map<String, TickSource> tiers = new ConcurrentHashMap<>();
    /** adapterId → reconnect future (only set when the adapter is down) */
    private final Map<String, ScheduledFuture<?>> reconnectTasks = new ConcurrentHashMap<>();

    private volatile String activeAdapterId;

    public MarketDataFeedAdapterRegistry(MarketDataIngestionService ingestionService,
                                         ScheduledExecutorService scheduler) {
        this.ingestionService = ingestionService;
        this.scheduler = scheduler;
    }

    /**
     * Register an adapter with its priority tier and the list of instruments it covers.
     * The first PRIMARY adapter registered is automatically connected and made active.
     */
    public void register(MarketDataFeedAdapter adapter,
                         TickSource tier,
                         List<String> instrumentIds) {
        adapters.put(adapter.adapterId(), adapter);
        tiers.put(adapter.adapterId(), tier);

        Consumer<MarketTick> callback = tick -> handleTick(adapter.adapterId(), tick);
        adapter.onTick(callback);

        if (tier == TickSource.PRIMARY && activeAdapterId == null) {
            connect(adapter, instrumentIds);
            activeAdapterId = adapter.adapterId();
            log.info("marketdata.feed.primary.connected adapterId={}", adapter.adapterId());
        }
    }

    /**
     * Called by the monitoring layer when the active PRIMARY adapter reports a disconnect.
     * Activates the first available SECONDARY adapter and schedules a reconnect probe.
     */
    public void onPrimaryDisconnect(String failedAdapterId) {
        log.warn("marketdata.feed.primary.disconnected adapterId={}", failedAdapterId);

        // Activate the first available secondary
        adapters.entrySet().stream()
                .filter(e -> tiers.get(e.getKey()) == TickSource.SECONDARY)
                .findFirst()
                .ifPresent(e -> {
                    activeAdapterId = e.getKey();
                    e.getValue().connect();
                    log.info("marketdata.feed.secondary.activated adapterId={}", e.getKey());
                });

        // Schedule PRIMARY reconnect probe
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                () -> attemptReconnect(failedAdapterId),
                RECONNECT_INTERVAL_SECONDS, RECONNECT_INTERVAL_SECONDS, TimeUnit.SECONDS);
        reconnectTasks.put(failedAdapterId, future);
    }

    private void attemptReconnect(String adapterId) {
        MarketDataFeedAdapter adapter = adapters.get(adapterId);
        if (adapter == null) return;
        try {
            adapter.connect();
            // If connect() didn't throw, restore primary
            cancelReconnect(adapterId);
            activeAdapterId = adapterId;
            // Deactivate secondary adapters
            adapters.entrySet().stream()
                    .filter(e -> tiers.get(e.getKey()) == TickSource.SECONDARY
                            && !e.getKey().equals(adapterId))
                    .forEach(e -> e.getValue().disconnect());
            log.info("marketdata.feed.primary.restored adapterId={}", adapterId);
        } catch (Exception e) {
            log.debug("marketdata.feed.primary.reconnect.failed adapterId={} reason={}", adapterId, e.getMessage());
        }
    }

    private void cancelReconnect(String adapterId) {
        ScheduledFuture<?> f = reconnectTasks.remove(adapterId);
        if (f != null) f.cancel(false);
    }

    /** Deregister and disconnect an adapter. */
    public void deregister(String adapterId) {
        cancelReconnect(adapterId);
        MarketDataFeedAdapter adapter = adapters.remove(adapterId);
        tiers.remove(adapterId);
        if (adapter != null) adapter.disconnect();
    }

    private void connect(MarketDataFeedAdapter adapter, List<String> instrumentIds) {
        adapter.connect();
        adapter.subscribe(instrumentIds);
    }

    /**
     * Route an incoming tick from the given adapter to the ingestion service.
     * Ticks from non-active adapters are silently dropped to avoid duplicate writes
     * during failover transition.
     */
    private void handleTick(String fromAdapterId, MarketTick tick) {
        if (!fromAdapterId.equals(activeAdapterId)) return;
        ingestionService.ingest(tick)
                .whenException(ex -> log.warn("marketdata.feed.tick.ingest.failed adapterId={} error={}",
                        fromAdapterId, ex.getMessage()));
    }
}
