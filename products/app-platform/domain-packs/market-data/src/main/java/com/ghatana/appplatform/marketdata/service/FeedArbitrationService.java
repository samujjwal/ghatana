package com.ghatana.appplatform.marketdata.service;

import com.ghatana.appplatform.marketdata.domain.TickSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * @doc.type    Service (Application)
 * @doc.purpose Feed arbitration engine with primary/secondary failover (D04-007).
 *              Monitors primary feed health via heartbeat + staleness check.
 *              If primary stale > configurable threshold (default 10s), switches to secondary.
 *              Returns to primary after it recovers and remains stable for 60s (stability window).
 *              State machine: PRIMARY → DEGRADED → SECONDARY → STABILIZING → PRIMARY.
 *              Emits {@link FeedFailoverEvent} and {@link FeedRecoveryEvent}.
 * @doc.layer   Application Service
 * @doc.pattern State machine + Hexagonal Architecture application service
 */
public class FeedArbitrationService {

    private static final Logger log = LoggerFactory.getLogger(FeedArbitrationService.class);

    private static final Duration DEFAULT_STALE_THRESHOLD  = Duration.ofSeconds(10);
    private static final Duration DEFAULT_STABILITY_WINDOW = Duration.ofSeconds(60);

    /** Simple 3-state machine: PRIMARY, SECONDARY, STABILIZING (primary recovered, confirming). */
    public enum FeedState { PRIMARY, SECONDARY, STABILIZING }

    private final AtomicReference<FeedState> state = new AtomicReference<>(FeedState.PRIMARY);
    private final Map<TickSource, Instant> lastHeartbeats;
    /** When did primary recover? Used to enforce stability window. */
    private volatile Instant primaryRecoveredAt = null;

    private final Duration staleThreshold;
    private final Duration stabilityWindow;
    private final Executor executor;
    private final Consumer<Object> eventPublisher;
    private final Counter failoverCounter;
    private final Counter recoveryCounter;

    public FeedArbitrationService(Executor executor,
                                   Consumer<Object> eventPublisher,
                                   MeterRegistry meterRegistry) {
        this(DEFAULT_STALE_THRESHOLD, DEFAULT_STABILITY_WINDOW, executor,
                eventPublisher, meterRegistry);
    }

    public FeedArbitrationService(Duration staleThreshold,
                                   Duration stabilityWindow,
                                   Executor executor,
                                   Consumer<Object> eventPublisher,
                                   MeterRegistry meterRegistry) {
        this.staleThreshold = staleThreshold;
        this.stabilityWindow = stabilityWindow;
        this.executor = executor;
        this.eventPublisher = eventPublisher;
        this.lastHeartbeats = new java.util.concurrent.ConcurrentHashMap<>();
        this.failoverCounter = meterRegistry.counter("marketdata.feed.failover");
        this.recoveryCounter = meterRegistry.counter("marketdata.feed.recovery");
    }

    /** Record a heartbeat (or any tick) from a feed source. */
    public void recordHeartbeat(TickSource source) {
        lastHeartbeats.put(source, Instant.now());

        if (source == TickSource.PRIMARY && state.get() == FeedState.SECONDARY) {
            // Primary recovered — start stability window
            if (primaryRecoveredAt == null) {
                primaryRecoveredAt = Instant.now();
                state.set(FeedState.STABILIZING);
                log.info("Primary feed recovered — entering STABILIZING state");
            }
        }
    }

    /**
     * Called periodically (e.g., every second) to evaluate feed health and drive state transitions.
     *
     * @return The active feed source to use for routing.
     */
    public Promise<TickSource> evaluate() {
        return Promise.ofBlocking(executor, () -> {
            Instant primaryLast = lastHeartbeats.getOrDefault(TickSource.PRIMARY, Instant.EPOCH);
            boolean primaryStale = Duration.between(primaryLast, Instant.now())
                    .compareTo(staleThreshold) > 0;

            return switch (state.get()) {
                case PRIMARY -> {
                    if (primaryStale) {
                        state.set(FeedState.SECONDARY);
                        primaryRecoveredAt = null;
                        failoverCounter.increment();
                        log.warn("Primary feed stale — failing over to SECONDARY");
                        eventPublisher.accept(new FeedFailoverEvent(TickSource.PRIMARY, TickSource.SECONDARY));
                    }
                    yield primaryStale ? TickSource.SECONDARY : TickSource.PRIMARY;
                }
                case SECONDARY -> {
                    // Still in secondary; stability window will promote back when ready
                    yield TickSource.SECONDARY;
                }
                case STABILIZING -> {
                    if (!primaryStale && primaryRecoveredAt != null) {
                        Duration stable = Duration.between(primaryRecoveredAt, Instant.now());
                        if (stable.compareTo(stabilityWindow) >= 0) {
                            state.set(FeedState.PRIMARY);
                            primaryRecoveredAt = null;
                            recoveryCounter.increment();
                            log.info("Primary feed stable for {}s — returning to PRIMARY", stable.toSeconds());
                            eventPublisher.accept(new FeedRecoveryEvent(TickSource.SECONDARY, TickSource.PRIMARY));
                            yield TickSource.PRIMARY;
                        }
                    } else if (primaryStale) {
                        // Primary became stale again during stability window — back to SECONDARY
                        state.set(FeedState.SECONDARY);
                        primaryRecoveredAt = null;
                        log.warn("Primary feed stale again during stability window — back to SECONDARY");
                    }
                    yield TickSource.SECONDARY;
                }
            };
        });
    }

    public FeedState getCurrentState() {
        return state.get();
    }

    // ─── Events ──────────────────────────────────────────────────────────────

    public record FeedFailoverEvent(TickSource from, TickSource to) {}
    public record FeedRecoveryEvent(TickSource from, TickSource to) {}
}
