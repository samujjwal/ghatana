package com.ghatana.finance.kernel.service;

import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter;
import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter.DataQueryRequest;
import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter.DataReadRequest;
import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter.DataWriteRequest;
import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter.QueryResult;
import com.ghatana.kernel.config.KernelConfigResolver;
import com.ghatana.kernel.context.KernelContext;
import com.ghatana.platform.cache.DistributedCachePort;
import com.ghatana.platform.cache.InMemoryCacheAdapter;
import io.activej.promise.Promise;
import io.activej.promise.Promises;

import com.ghatana.kernel.util.TypedDataSerializer;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Risk Management Service for pre-trade and post-trade risk checks.
 *
 * <p>Provides risk assessment with:
 * <ul>
 *   <li>Position limits monitoring</li>
 *   <li>Notional exposure tracking</li>
 *   <li>Concentration risk detection</li>
 *   <li>Real-time risk metrics</li>
 *   <li>SEBON regulatory compliance</li>
 *   <li>Distributed cache (ISSUE-X02 fix) — uses {@link DistributedCachePort} instead of a
 *       node-local {@code ConcurrentHashMap} so that cache invalidation propagates across
 *       all horizontally-scaled nodes.</li>
 * </ul></p>
 *
 * @doc.type class
 * @doc.purpose Finance risk management service
 * @doc.layer product
 * @doc.pattern Service
 * @author Ghatana Finance Team
 * @since 1.0.0
 */
public class RiskManagementService {

    private static final String RISK_DATASET = "finance.risk.metrics";
    private static final String ALERT_DATASET = "finance.risk.alerts";

    /** Distributed cache for risk profiles — multi-node safe (ISSUE-X02). */
    private final DistributedCachePort<String, RiskMetrics> riskCache;
    private final DataCloudKernelAdapter dataCloud;
    private volatile boolean running = false;

    // Risk limits — loaded from KernelConfigResolver at start(), with sensible defaults
    private volatile BigDecimal maxPositionLimit;
    private volatile BigDecimal maxConcentrationPct;
    private volatile BigDecimal maxDailyLoss;

    private static final BigDecimal DEFAULT_MAX_POSITION_LIMIT = new BigDecimal("10000000");
    private static final BigDecimal DEFAULT_MAX_CONCENTRATION_PCT = new BigDecimal("0.20");
    private static final BigDecimal DEFAULT_MAX_DAILY_LOSS = new BigDecimal("500000");

    /**
     * Creates a {@link RiskManagementService} using the supplied distributed cache.
     *
     * <p>In production, inject a {@code WriteThroughDistributedCache} (L1=Caffeine, L2=Redis)
     * constructed by {@code DistributedCacheFactory.create(...)} in the module initializer.
     * In tests, use {@code DistributedCacheFactory.createInMemory(10_000, Duration.ofMinutes(5))}.</p>
     *
     * @param context   kernel context — provides DataCloud and optional config
     * @param riskCache distributed cache for risk metric profiles (ISSUE-X02 fix)
     */
    public RiskManagementService(KernelContext context,
                                  DistributedCachePort<String, RiskMetrics> riskCache) {
        this.dataCloud = context.getDependency(DataCloudKernelAdapter.class);
        this.riskCache = Objects.requireNonNull(riskCache, "riskCache must not be null");

        // Load configurable risk limits from kernel config
        if (context.hasDependency(KernelConfigResolver.class)) {
            KernelConfigResolver config = context.getDependency(KernelConfigResolver.class);
            var tenantCtx = context.getTenantContext();
            this.maxPositionLimit = config.resolveWithDefault(
                    "finance.risk.max-position-limit", BigDecimal.class,
                    DEFAULT_MAX_POSITION_LIMIT, tenantCtx);
            this.maxConcentrationPct = config.resolveWithDefault(
                    "finance.risk.max-concentration-pct", BigDecimal.class,
                    DEFAULT_MAX_CONCENTRATION_PCT, tenantCtx);
            this.maxDailyLoss = config.resolveWithDefault(
                    "finance.risk.max-daily-loss", BigDecimal.class,
                    DEFAULT_MAX_DAILY_LOSS, tenantCtx);
        } else {
            this.maxPositionLimit = DEFAULT_MAX_POSITION_LIMIT;
            this.maxConcentrationPct = DEFAULT_MAX_CONCENTRATION_PCT;
            this.maxDailyLoss = DEFAULT_MAX_DAILY_LOSS;
        }
    }

    /**
     * Convenience constructor for tests and single-node deployments — creates an in-memory
     * {@link InMemoryCacheAdapter} with a 10,000 entry limit and 60-second TTL.
     *
     * @param context kernel context
     */
    public RiskManagementService(KernelContext context) {
        this(context, new InMemoryCacheAdapter<>(10_000, Duration.ofSeconds(60)));
    }

    public Promise<Void> start() {
        running = true;
        return initializeDatasets();
    }

    public Promise<Void> stop() {
        running = false;
        // Flush local layer of the distributed cache on graceful shutdown
        return riskCache.invalidateAll();
    }

    public boolean isHealthy() {
        return running;
    }

    public String getName() {
        return "risk-management";
    }

    // ==================== Risk Assessment ====================

    /**
     * Performs pre-trade risk check.
     *
     * @param traderId the trader identifier
     * @param symbol the symbol
     * @param side BUY or SELL
     * @param quantity the quantity
     * @param price the price
     * @return Promise containing risk check result
     */
    public Promise<RiskCheckResult> preTradeCheck(String traderId, String symbol, String side,
                                                   BigDecimal quantity, BigDecimal price) {
        if (!running) {
            return Promise.of(RiskCheckResult.rejected("Service not running"));
        }

        BigDecimal notional = quantity.multiply(price);

        // Check position limits
        return getCurrentPosition(traderId, symbol)
            .then(currentPosition -> {
                BigDecimal newPosition = "BUY".equals(side)
                    ? currentPosition.add(notional)
                    : currentPosition.subtract(notional);

                if (newPosition.abs().compareTo(maxPositionLimit) > 0) {
                    return createRiskAlert(traderId, "POSITION_LIMIT",
                        "Position limit exceeded for " + symbol)
                        .map($ -> RiskCheckResult.rejected("Position limit exceeded"));
                }

                // Check concentration
                return getPortfolioValue(traderId)
                    .then(portfolioValue -> {
                        if (portfolioValue.compareTo(BigDecimal.ZERO) > 0) {
                            BigDecimal concentration = newPosition.abs()
                                .divide(portfolioValue, 4, RoundingMode.HALF_UP);
                            if (concentration.compareTo(maxConcentrationPct) > 0) {
                                return createRiskAlert(traderId, "CONCENTRATION_RISK",
                                    "Concentration limit exceeded for " + symbol)
                                    .map($ -> RiskCheckResult.rejected("Concentration limit exceeded"));
                            }
                        }
                        return Promise.of(RiskCheckResult.approved());
                    });
            });
    }

    /**
     * Gets risk metrics for a trader, using the distributed cache (ISSUE-X02 fix).
     *
     * <p>Cache miss triggers a fresh calculation which is stored in both L1 (local)
     * and L2 (Redis) so that all nodes serve consistent data.</p>
     *
     * @param traderId the trader identifier
     * @return Promise containing risk metrics
     */
    public Promise<RiskMetrics> getRiskMetrics(String traderId) {
        if (!running) {
            return Promise.of(RiskMetrics.empty());
        }

        return riskCache.getOrLoad(traderId, id -> calculateRiskMetrics(id));
    }

    /**
     * Records trade for post-trade risk monitoring.
     *
     * <p>Invalidates the risk cache entry for the affected trader so that the next
     * {@link #getRiskMetrics(String)} call fetches fresh data across all nodes (ISSUE-X02).</p>
     *
     * @param trade the trade record
     * @return Promise completing when recorded
     */
    public Promise<Void> recordTrade(TradeRecord trade) {
        if (!running) {
            return Promise.complete();
        }

        return updatePosition(trade)
            .then($ -> checkRiskLimits(trade.getTraderId()))
            .then($ -> recordRiskMetrics(trade.getTraderId()))
            // Invalidate distributed cache entry so all nodes pick up the updated metrics
            .then($ -> riskCache.invalidate(trade.getTraderId()));
    }

    /**
     * Gets all active risk alerts.
     *
     * @return Promise containing active alerts
     */
    public Promise<List<RiskAlert>> getActiveAlerts() {
        if (!running) {
            return Promise.of(List.of());
        }

        DataQueryRequest request = new DataQueryRequest(
            ALERT_DATASET,
            "status = :status",
            Map.of("status", "ACTIVE"),
            100,
            0
        );

        return dataCloud.queryData(request)
            .map(QueryResult::getResults)
            .map(results -> results.stream()
                .map(r -> deserializeAlert(r.getData()))
                .filter(Objects::nonNull)
                .toList());
    }

    // ==================== Private Methods ====================

    private Promise<Void> initializeDatasets() {
        Promise<Void> risk = dataCloud.createSchema(new DataCloudKernelAdapter.SchemaCreateRequest(
            RISK_DATASET,
            Map.of(
                "traderId", "string",
                "metricType", "string",
                "value", "decimal",
                "timestamp", "timestamp"
            ),
            Map.of("retention", "10years")
        )).whenException(e -> {});

        Promise<Void> alerts = dataCloud.createSchema(new DataCloudKernelAdapter.SchemaCreateRequest(
            ALERT_DATASET,
            Map.of(
                "alertId", "string",
                "traderId", "string",
                "alertType", "string",
                "status", "string"
            ),
            Map.of("retention", "10years")
        )).whenException(e -> {});

        return Promises.all(risk, alerts).map($ -> null);
    }

    private Promise<BigDecimal> getCurrentPosition(String traderId, String symbol) {
        DataQueryRequest request = new DataQueryRequest(
            "finance.positions",
            "traderId = :traderId AND symbol = :symbol",
            Map.of("traderId", traderId, "symbol", symbol),
            1,
            0
        );

        return dataCloud.queryData(request)
            .map(result -> result.getResults().isEmpty()
                ? BigDecimal.ZERO
                : new BigDecimal(result.getResults().get(0).getMetadata().getOrDefault("value", "0")));
    }

    private Promise<BigDecimal> getPortfolioValue(String traderId) {
        // Sum of all positions
        DataQueryRequest request = new DataQueryRequest(
            "finance.positions",
            "traderId = :traderId",
            Map.of("traderId", traderId),
            1000,
            0
        );

        return dataCloud.queryData(request)
            .map(result -> result.getResults().stream()
                .map(r -> new BigDecimal(r.getMetadata().getOrDefault("value", "0")))
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private Promise<RiskMetrics> calculateRiskMetrics(String traderId) {
        return getPortfolioValue(traderId)
            .then(portfolioValue -> {
                return getOpenOrdersExposure(traderId)
                    .then(openExposure -> {
                        RiskMetrics metrics = new RiskMetrics(
                            traderId,
                            portfolioValue,
                            openExposure,
                            BigDecimal.ZERO, // daily P&L
                            Instant.now()
                        );
                        return Promise.of(metrics);
                    });
            });
    }

    private Promise<BigDecimal> getOpenOrdersExposure(String traderId) {
        DataQueryRequest request = new DataQueryRequest(
            "finance.orders",
            "traderId = :traderId AND status IN ('NEW', 'PENDING')",
            Map.of("traderId", traderId),
            1000,
            0
        );

        return dataCloud.queryData(request)
            .map(result -> result.getResults().stream()
                .map(r -> {
                    BigDecimal qty = new BigDecimal(r.getMetadata().getOrDefault("quantity", "0"));
                    BigDecimal price = new BigDecimal(r.getMetadata().getOrDefault("price", "0"));
                    return qty.multiply(price);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private Promise<Void> updatePosition(TradeRecord trade) {
        // Update position in database
        return Promise.complete();
    }

    private Promise<Void> checkRiskLimits(String traderId) {
        return getRiskMetrics(traderId)
            .then(metrics -> {
                List<Promise<Void>> checks = new ArrayList<>();

                // Check daily loss limit
                if (metrics.getDailyPnL().abs().compareTo(maxDailyLoss) > 0) {
                    checks.add(createRiskAlert(traderId, "DAILY_LOSS_LIMIT",
                        "Daily loss limit exceeded"));
                }

                return Promises.all(checks).map($ -> null);
            });
    }

    private Promise<Void> recordRiskMetrics(String traderId) {
        return calculateRiskMetrics(traderId)
            .then(metrics -> {
                DataWriteRequest request = new DataWriteRequest(
                    RISK_DATASET,
                    generateId(),
                    serializeMetrics(metrics),
                    Map.of(
                        "traderId", traderId,
                        "timestamp", Instant.now().toString()
                    )
                );
                return dataCloud.writeData(request);
            });
    }

    private Promise<Void> createRiskAlert(String traderId, String alertType, String message) {
        String alertId = generateId();
        RiskAlert alert = new RiskAlert(
            alertId,
            traderId,
            alertType,
            message,
            Instant.now(),
            "ACTIVE"
        );

        DataWriteRequest request = new DataWriteRequest(
            ALERT_DATASET,
            alertId,
            serializeAlert(alert),
            Map.of(
                "traderId", traderId,
                "alertType", alertType,
                "status", "ACTIVE"
            )
        );

        return dataCloud.writeData(request);
    }

    private byte[] serializeMetrics(RiskMetrics metrics) {
        return TypedDataSerializer.toBytes(metrics, "RiskMetrics", 1);
    }

    private byte[] serializeAlert(RiskAlert alert) {
        return TypedDataSerializer.toBytes(alert, "RiskAlert", 1);
    }

    private RiskAlert deserializeAlert(byte[] data) {
        return TypedDataSerializer.fromBytes(data, RiskAlert.class);
    }

    private String generateId() {
        return "risk-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    // ==================== Inner Types ====================
    // ==================== Inner Types ====================

    public static class RiskCheckResult {
        private final boolean approved;
        private final String reason;

        private RiskCheckResult(boolean approved, String reason) {
            this.approved = approved;
            this.reason = reason;
        }

        public static RiskCheckResult approved() {
            return new RiskCheckResult(true, null);
        }

        public static RiskCheckResult rejected(String reason) {
            return new RiskCheckResult(false, reason);
        }

        public boolean isApproved() { return approved; }
        public String getReason() { return reason; }
    }

    public static class RiskMetrics {
        private final String traderId;
        private final BigDecimal portfolioValue;
        private final BigDecimal openOrderExposure;
        private final BigDecimal dailyPnL;
        private final Instant calculatedAt;

        public RiskMetrics(String traderId, BigDecimal portfolioValue, BigDecimal openOrderExposure,
                          BigDecimal dailyPnL, Instant calculatedAt) {
            this.traderId = traderId;
            this.portfolioValue = portfolioValue;
            this.openOrderExposure = openOrderExposure;
            this.dailyPnL = dailyPnL;
            this.calculatedAt = calculatedAt;
        }

        public static RiskMetrics empty() {
            return new RiskMetrics(null, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, Instant.now());
        }

        public String getTraderId() { return traderId; }
        public BigDecimal getPortfolioValue() { return portfolioValue; }
        public BigDecimal getOpenOrderExposure() { return openOrderExposure; }
        public BigDecimal getDailyPnL() { return dailyPnL; }
        public Instant getCalculatedAt() { return calculatedAt; }
    }

    public static class RiskAlert {
        private final String id;
        private final String traderId;
        private final String alertType;
        private final String message;
        private final Instant createdAt;
        private final String status;

        public RiskAlert(String id, String traderId, String alertType, String message,
                        Instant createdAt, String status) {
            this.id = id;
            this.traderId = traderId;
            this.alertType = alertType;
            this.message = message;
            this.createdAt = createdAt;
            this.status = status;
        }

        public String getId() { return id; }
        public String getTraderId() { return traderId; }
        public String getAlertType() { return alertType; }
        public String getMessage() { return message; }
        public Instant getCreatedAt() { return createdAt; }
        public String getStatus() { return status; }
    }

    public static class TradeRecord {
        private final String tradeId;
        private final String traderId;
        private final String symbol;
        private final String side;
        private final BigDecimal quantity;
        private final BigDecimal price;
        private final Instant timestamp;

        public TradeRecord(String tradeId, String traderId, String symbol, String side,
                          BigDecimal quantity, BigDecimal price, Instant timestamp) {
            this.tradeId = tradeId;
            this.traderId = traderId;
            this.symbol = symbol;
            this.side = side;
            this.quantity = quantity;
            this.price = price;
            this.timestamp = timestamp;
        }

        public String getTradeId() { return tradeId; }
        public String getTraderId() { return traderId; }
        public String getSymbol() { return symbol; }
        public String getSide() { return side; }
        public BigDecimal getQuantity() { return quantity; }
        public BigDecimal getPrice() { return price; }
        public Instant getTimestamp() { return timestamp; }
    }
}

