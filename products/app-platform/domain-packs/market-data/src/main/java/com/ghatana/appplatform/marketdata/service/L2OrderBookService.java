package com.ghatana.appplatform.marketdata.service;

import com.ghatana.appplatform.marketdata.domain.L2OrderBook;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * @doc.type    Service (Application)
 * @doc.purpose Maintains in-memory L2 (depth-of-book) order books per instrument (D04-005).
 *              Aggregates individual order events into price-level snapshots.
 *              Top-N levels (default 10) on each side; publishes snapshots to
 *              {@code siddhanta.marketdata.l2} after each update.
 * @doc.layer   Application Service
 * @doc.pattern In-memory aggregation + Kafka publish; Hexagonal Architecture
 */
public class L2OrderBookService {

    private static final Logger log = LoggerFactory.getLogger(L2OrderBookService.class);
    private static final int DEFAULT_DEPTH = 10;

    /** instrument → {side → {price → {qty, count}}}. */
    private final ConcurrentHashMap<String, InstrumentBook> books = new ConcurrentHashMap<>();
    private final int depth;
    private final Executor executor;
    private final Consumer<Object> eventPublisher;

    public L2OrderBookService(Executor executor, Consumer<Object> eventPublisher,
                               MeterRegistry meterRegistry) {
        this(DEFAULT_DEPTH, executor, eventPublisher, meterRegistry);
    }

    public L2OrderBookService(int depth, Executor executor, Consumer<Object> eventPublisher,
                               MeterRegistry meterRegistry) {
        this.depth = depth;
        this.executor = executor;
        this.eventPublisher = eventPublisher;
        meterRegistry.gaugeMapSize("marketdata.l2.instruments", Tags.empty(), books);
    }

    /** Apply an individual order event to update the order book. */
    public Promise<L2OrderBook> applyOrderEvent(OrderBookEvent event) {
        return Promise.ofBlocking(executor, () -> {
            var book = books.computeIfAbsent(event.instrumentId(), InstrumentBook::new);
            book.apply(event);
            var snapshot = book.snapshot(depth);
            eventPublisher.accept(new L2BookUpdatedEvent(snapshot));
            return snapshot;
        });
    }

    /** Get the current snapshot for an instrument (REST query). */
    public Optional<L2OrderBook> getBook(String instrumentId) {
        var book = books.get(instrumentId);
        return book == null ? Optional.empty() : Optional.of(book.snapshot(depth));
    }

    // ─── Internal book state ──────────────────────────────────────────────────

    private static final class InstrumentBook {
        private final String instrumentId;
        private long sequence = 0;

        /** price → [totalQty, orderCount] for each side. */
        private final TreeMap<BigDecimal, long[]> bids =
                new TreeMap<>(Comparator.reverseOrder()); // descending
        private final TreeMap<BigDecimal, long[]> asks =
                new TreeMap<>();                           // ascending

        InstrumentBook(String instrumentId) {
            this.instrumentId = instrumentId;
        }

        synchronized void apply(OrderBookEvent event) {
            var levels = "BUY".equalsIgnoreCase(event.side()) ? bids : asks;
            switch (event.type()) {
                case ADD, MODIFY -> {
                    var level = levels.computeIfAbsent(event.price(), p -> new long[]{0, 0});
                    level[0] += event.quantity();
                    level[1]++;
                }
                case REMOVE -> {
                    var level = levels.get(event.price());
                    if (level != null) {
                        level[0] = Math.max(0, level[0] - event.quantity());
                        level[1] = Math.max(0, level[1] - 1);
                        if (level[0] == 0) levels.remove(event.price());
                    }
                }
            }
            sequence++;
        }

        synchronized L2OrderBook snapshot(int depth) {
            return new L2OrderBook(instrumentId,
                    topN(bids, depth), topN(asks, depth), sequence, Instant.now());
        }

        private List<L2OrderBook.PriceLevel> topN(TreeMap<BigDecimal, long[]> levels, int n) {
            var result = new ArrayList<L2OrderBook.PriceLevel>(n);
            for (var entry : levels.entrySet()) {
                if (result.size() >= n) break;
                long[] v = entry.getValue();
                if (v[0] > 0) {
                    result.add(new L2OrderBook.PriceLevel(entry.getKey(), v[0], (int) v[1]));
                }
            }
            return Collections.unmodifiableList(result);
        }
    }

    // ─── Supporting Types ─────────────────────────────────────────────────────

    public record OrderBookEvent(String instrumentId, String side, BigDecimal price,
                                  long quantity, EventType type) {
        public enum EventType { ADD, MODIFY, REMOVE }
    }

    public record L2BookUpdatedEvent(L2OrderBook book) {}

    // Micrometer Tags helper (avoids a full micrometer import in callers)
    private static final class Tags {
        static io.micrometer.core.instrument.Tags empty() {
            return io.micrometer.core.instrument.Tags.empty();
        }
    }
}
