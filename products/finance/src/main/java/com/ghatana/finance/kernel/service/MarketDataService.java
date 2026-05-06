package com.ghatana.finance.kernel.service;

import com.ghatana.kernel.context.KernelContext;
import io.activej.promise.Promise;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Market Data Service for real-time market data ingestion and distribution.
 *
 * <p>Manages market data with:
 * <ul>
 *   <li>Real-time quote ingestion and normalization</li>
 *   <li>Trade data processing and storage</li>
 *   <li>Order book snapshots and updates</li>
 *   <li>Market data distribution to subscribers</li>
 * </ul></p>
 *
 * @doc.type class
 * @doc.purpose Finance market data service
 * @doc.layer product
 * @doc.pattern Service
 * @author Ghatana Finance Team
 * @since 1.0.0
 */
public class MarketDataService extends FinanceServiceBase {

    private static final String QUOTE_DATASET = "finance.market.quotes";
    private static final String TRADE_DATASET = "finance.market.trades";

    private final Map<String, Quote> latestQuotes = new ConcurrentHashMap<>();

    public MarketDataService(KernelContext context) {
        super(context);
    }

    @Override
    public String getName() {
        return "market-data";
    }

    @Override
    protected Promise<Void> initializeDatasets() {
        Promise<Void> quoteSchema = createSchema(
            QUOTE_DATASET,
            Map.of(
                "quoteId", "string",
                "symbol", "string",
                "exchange", "string",
                "bidPrice", "decimal",
                "askPrice", "decimal",
                "timestamp", "timestamp"
            ),
            Map.of("retention", "1year")
        ).whenException(e -> {});

        Promise<Void> tradeSchema = createSchema(
            TRADE_DATASET,
            Map.of(
                "tradeId", "string",
                "symbol", "string",
                "exchange", "string",
                "price", "decimal",
                "quantity", "decimal",
                "timestamp", "timestamp"
            ),
            Map.of("retention", "10years")
        ).whenException(e -> {});

        return quoteSchema.then($ -> tradeSchema);
    }

    @Override
    public Promise<Void> stop() {
        latestQuotes.clear();
        return Promise.complete();
    }

    public Promise<Quote> ingestQuote(QuoteRequest request) {
        ensureRunning();

        String quoteId = generateId("qt");
        Instant now = Instant.now();

        Quote quote = new Quote(
            quoteId,
            request.getSymbol(),
            request.getExchange(),
            request.getBidPrice(),
            request.getAskPrice(),
            request.getBidSize(),
            request.getAskSize(),
            now
        );

        latestQuotes.put(request.getSymbol(), quote);

        return createRecord(
            QUOTE_DATASET,
            quoteId,
            quote,
            Map.of(
                "symbol", quote.getSymbol(),
                "exchange", quote.getExchange(),
                "timestamp", now.toString()
            ),
            "Quote",
            1
        ).then(stored -> audit("QUOTE_INGEST", stored.getSymbol(),
            "Quote ingested: " + stored.getSymbol() + " @ " + stored.getBidPrice() + "/" + stored.getAskPrice())
            .map($ -> stored));
    }

    public Promise<Optional<Quote>> getLatestQuote(String symbol) {
        ensureRunning();

        Quote cached = latestQuotes.get(symbol);
        if (cached != null) {
            return Promise.of(Optional.of(cached));
        }

        return queryRecords(
            QUOTE_DATASET,
            "symbol = :symbol",
            Map.of("symbol", symbol),
            1,
            0,
            Quote.class
        ).map(results -> results.isEmpty() ? Optional.empty() : Optional.of(results.get(0)));
    }

    public Promise<List<Quote>> getQuoteHistory(String symbol, Instant from, Instant to) {
        ensureRunning();

        return queryRecords(
            QUOTE_DATASET,
            "symbol = :symbol AND timestamp >= :from AND timestamp <= :to",
            Map.of("symbol", symbol, "from", from, "to", to),
            10000,
            0,
            Quote.class
        );
    }

    public Promise<Trade> recordTrade(TradeRequest request) {
        ensureRunning();

        String tradeId = generateId("tr");
        Instant now = Instant.now();

        Trade trade = new Trade(
            tradeId,
            request.getSymbol(),
            request.getExchange(),
            request.getPrice(),
            request.getQuantity(),
            request.getSide(),
            now
        );

        return createRecord(
            TRADE_DATASET,
            tradeId,
            trade,
            Map.of(
                "symbol", trade.getSymbol(),
                "exchange", trade.getExchange(),
                "timestamp", now.toString()
            ),
            "Trade",
            1
        ).then(stored -> audit("TRADE_RECORD", stored.getSymbol(),
            "Trade recorded: " + stored.getSymbol() + " " + stored.getQuantity() + " @ " + stored.getPrice())
            .map($ -> stored));
    }

    // ==================== Inner Types ====================
    public static class Quote {
        private final String id;
        private final String symbol;
        private final String exchange;
        private final BigDecimal bidPrice;
        private final BigDecimal askPrice;
        private final BigDecimal bidSize;
        private final BigDecimal askSize;
        private final Instant timestamp;

        public Quote(String id, String symbol, String exchange, BigDecimal bidPrice,
                    BigDecimal askPrice, BigDecimal bidSize, BigDecimal askSize, Instant timestamp) {
            this.id = id;
            this.symbol = symbol;
            this.exchange = exchange;
            this.bidPrice = bidPrice;
            this.askPrice = askPrice;
            this.bidSize = bidSize;
            this.askSize = askSize;
            this.timestamp = timestamp;
        }

        public String getId() { return id; }
        public String getSymbol() { return symbol; }
        public String getExchange() { return exchange; }
        public BigDecimal getBidPrice() { return bidPrice; }
        public BigDecimal getAskPrice() { return askPrice; }
        public BigDecimal getBidSize() { return bidSize; }
        public BigDecimal getAskSize() { return askSize; }
        public Instant getTimestamp() { return timestamp; }
    }

    public static class QuoteRequest {
        private final String symbol;
        private final String exchange;
        private final BigDecimal bidPrice;
        private final BigDecimal askPrice;
        private final BigDecimal bidSize;
        private final BigDecimal askSize;

        public QuoteRequest(String symbol, String exchange, BigDecimal bidPrice,
                          BigDecimal askPrice, BigDecimal bidSize, BigDecimal askSize) {
            this.symbol = symbol;
            this.exchange = exchange;
            this.bidPrice = bidPrice;
            this.askPrice = askPrice;
            this.bidSize = bidSize;
            this.askSize = askSize;
        }

        public String getSymbol() { return symbol; }
        public String getExchange() { return exchange; }
        public BigDecimal getBidPrice() { return bidPrice; }
        public BigDecimal getAskPrice() { return askPrice; }
        public BigDecimal getBidSize() { return bidSize; }
        public BigDecimal getAskSize() { return askSize; }
    }

    public static class Trade {
        private final String id;
        private final String symbol;
        private final String exchange;
        private final BigDecimal price;
        private final BigDecimal quantity;
        private final String side;
        private final Instant timestamp;

        public Trade(String id, String symbol, String exchange, BigDecimal price,
                    BigDecimal quantity, String side, Instant timestamp) {
            this.id = id;
            this.symbol = symbol;
            this.exchange = exchange;
            this.price = price;
            this.quantity = quantity;
            this.side = side;
            this.timestamp = timestamp;
        }

        public String getId() { return id; }
        public String getSymbol() { return symbol; }
        public String getExchange() { return exchange; }
        public BigDecimal getPrice() { return price; }
        public BigDecimal getQuantity() { return quantity; }
        public String getSide() { return side; }
        public Instant getTimestamp() { return timestamp; }
    }

    public static class TradeRequest {
        private final String symbol;
        private final String exchange;
        private final BigDecimal price;
        private final BigDecimal quantity;
        private final String side;

        public TradeRequest(String symbol, String exchange, BigDecimal price,
                          BigDecimal quantity, String side) {
            this.symbol = symbol;
            this.exchange = exchange;
            this.price = price;
            this.quantity = quantity;
            this.side = side;
        }

        public String getSymbol() { return symbol; }
        public String getExchange() { return exchange; }
        public BigDecimal getPrice() { return price; }
        public BigDecimal getQuantity() { return quantity; }
        public String getSide() { return side; }
    }
}
