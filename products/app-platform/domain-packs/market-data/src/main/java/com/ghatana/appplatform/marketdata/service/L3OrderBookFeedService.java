package com.ghatana.appplatform.marketdata.service;

import com.ghatana.appplatform.marketdata.domain.MarketTick;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

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

    /** Per-subscriber token bucket: subscriber → remaining tokens. */
    private final ConcurrentHashMap<String, RateBucket> rateBuckets = new ConcurrentHashMap<>();

    private final Executor executor;
    private final Consumer<Object> eventPublisher;

    public L3OrderBookFeedService(Executor executor, Consumer<Object> eventPublisher) {
        this.executor = executor;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Publish an L3 order event if the subscriber is authorized and within rate limit (D04-006).
     *
     * @param event      The full order event to publish.
     * @param subscriberId The requesting subscriber's identifier (used for rate limiting).
     */
    public Promise<Boolean> publishL3Event(L3OrderEvent event, String subscriberId) {
        return Promise.ofBlocking(executor, () -> {
            var bucket = rateBuckets.computeIfAbsent(subscriberId, RateBucket::new);
            if (!bucket.tryConsume()) {
                log.debug("L3 rate limit exceeded for subscriber={}", subscriberId);
                return false;
            }
            eventPublisher.accept(event);
            return true;
        });
    }

    /** Register a subscriber with a given message-per-second rate limit. */
    public void registerSubscriber(String subscriberId, int maxMessagesPerSecond) {
        rateBuckets.put(subscriberId, new RateBucket(subscriberId, maxMessagesPerSecond));
    }

    public void deregisterSubscriber(String subscriberId) {
        rateBuckets.remove(subscriberId);
    }

    // ─── Simple Token Bucket ─────────────────────────────────────────────────

    private static final class RateBucket {
        private static final int DEFAULT_MAX_PER_SEC = 100;
        private final int maxTokens;
        private long tokens;
        private long lastRefillMs;

        RateBucket(String subscriberId) {
            this(subscriberId, DEFAULT_MAX_PER_SEC);
        }

        RateBucket(String subscriberId, int maxTokens) {
            this.maxTokens = maxTokens;
            this.tokens = maxTokens;
            this.lastRefillMs = System.currentTimeMillis();
        }

        synchronized boolean tryConsume() {
            refillIfNeeded();
            if (tokens <= 0) return false;
            tokens--;
            return true;
        }

        private void refillIfNeeded() {
            long now = System.currentTimeMillis();
            long elapsed = now - lastRefillMs;
            if (elapsed >= 1000) {
                tokens = maxTokens;
                lastRefillMs = now;
            }
        }
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
