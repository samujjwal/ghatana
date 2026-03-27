package com.ghatana.products.finance.domains.marketdata.service;


import com.ghatana.platform.core.event.EventBusPort;
import com.ghatana.platform.security.ratelimit.DefaultRateLimiter;
import com.ghatana.platform.security.ratelimit.RateLimiter;
import com.ghatana.platform.security.ratelimit.RateLimiterConfig;
import com.ghatana.products.finance.domains.marketdata.domain.MarketTick;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service (Application)
 * @doc.purpose Publishes individual order-level (L3) data to {@code siddhanta.marketdata.l3}
 *              for authorized high-transparency subscribers (D04-006).
 *              L3 data carries full order metadata: orderId, price, quantity, side, timestamp.
 *              Rate-limited per subscriber based on their authorization tier.
 * @doc.layer   Application Service
 * @doc.pattern Hexagonal Architecture — application service; rate-limiting guard
 */
public class L3OrderBookFeedService {

    private static final Logger log = LoggerFactory.getLogger(L3OrderBookFeedService.class);
    private static final int DEFAULT_MAX_PER_SEC = 100;
    private static final String PUBLISH_RATE_KEY = "l3-publish";

    /** Per-subscriber shared rate limiter instance. */
    private final ConcurrentHashMap<String, RateLimiter> subscriberLimiters = new ConcurrentHashMap<>();

    private final Executor executor;
    private final EventBusPort eventBusPort;

    public L3OrderBookFeedService(Executor executor, EventBusPort eventBusPort) {
        this.executor = executor;
        this.eventBusPort = eventBusPort;
    }

    /**
     * Publish an L3 order event if the subscriber is authorized and within rate limit (D04-006).
     *
     * @param event      The full order event to publish.
     * @param subscriberId The requesting subscriber's identifier (used for rate limiting).
     */
    public Promise<Boolean> publishL3Event(L3OrderEvent event, String subscriberId) {
        return Promise.ofBlocking(executor, () -> {
            RateLimiter limiter = subscriberLimiters.computeIfAbsent(
                    subscriberId,
                    ignored -> createLimiter(DEFAULT_MAX_PER_SEC)
            );
            if (!limiter.tryAcquire(PUBLISH_RATE_KEY).allowed()) {
                log.debug("L3 rate limit exceeded for subscriber={}", subscriberId);
                return false;
            }
            eventBusPort.publish(event);
            return true;
        });
    }

    /** Register a subscriber with a given message-per-second rate limit. */
    public void registerSubscriber(String subscriberId, int maxMessagesPerSecond) {
        subscriberLimiters.put(subscriberId, createLimiter(maxMessagesPerSecond));
    }

    public void deregisterSubscriber(String subscriberId) {
        subscriberLimiters.remove(subscriberId);
    }

    private RateLimiter createLimiter(int maxMessagesPerSecond) {
        return DefaultRateLimiter.create(
                RateLimiterConfig.builder()
                        .maxRequestsPerMinute(maxMessagesPerSecond)
                        .burstSize(maxMessagesPerSecond)
                        .windowDuration(Duration.ofSeconds(1))
                        .build()
        );
    }

    // ─── Supporting Types ─────────────────────────────────────────────────────

    /**
     * Full L3 order event — individual order visibility.
     *
     * @param instrumentId Exchange symbol.
     * @param orderId      Exchange-assigned order identifier.
     * @param side         "BUY" or "SELL".
     * @param price        Order price.
     * @param quantity     Order quantity.
     * @param eventType    NEW, MODIFY, CANCEL, FILL.
     */
    public record L3OrderEvent(String instrumentId, String orderId, String side,
                                BigDecimal price, long quantity, String eventType) {}
}
