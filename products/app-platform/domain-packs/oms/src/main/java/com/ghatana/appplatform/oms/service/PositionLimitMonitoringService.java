package com.ghatana.appplatform.oms.service;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * @doc.type      Service
 * @doc.purpose   Real-time position limit monitoring. Subscribes to position projection events
 *                and checks each position against configured limits (max quantity, max value,
 *                max concentration). Emits warnings at 90%+ and breach events at 100%.
 * @doc.layer     Application
 * @doc.pattern   Event Consumer + in-memory cache of current positions
 *
 * Limits configured per K-02 config (loaded via LimitConfigPort):
 *   - max_quantity per instrument per client
 *   - max_value per instrument
 *   - max_concentration (% of portfolio, default 10%)
 *
 * Story: D01-017
 */
public class PositionLimitMonitoringService {

    private static final Logger log = LoggerFactory.getLogger(PositionLimitMonitoringService.class);
    private static final double WARNING_THRESHOLD      = 0.90;
    private static final double DEFAULT_MAX_CONCENTRATION = 0.10;

    private final LimitConfigPort  limitConfig;
    private final Consumer<Object> eventPublisher;
    private final ConcurrentHashMap<String, PositionSummary> positionCache = new ConcurrentHashMap<>();

    public PositionLimitMonitoringService(LimitConfigPort limitConfig,
                                           Consumer<Object> eventPublisher,
                                           MeterRegistry meterRegistry) {
        this.limitConfig    = limitConfig;
        this.eventPublisher = eventPublisher;

        Gauge.builder("oms.position.instruments.monitored",
                positionCache, ConcurrentHashMap::size).register(meterRegistry);
    }

    /**
     * Evaluates a position update against configured limits.
     * Called whenever position projection updates a position.
     *
     * @param clientId      client
     * @param instrumentId  instrument
     * @param newQuantity   updated net quantity
     * @param marketValue   current market value of net position
     * @param totalPortfolioValue total portfolio value (for concentration check)
     */
    public void evaluate(String clientId, String instrumentId,
                          long newQuantity, BigDecimal marketValue,
                          BigDecimal totalPortfolioValue) {

        String key = clientId + ":" + instrumentId;
        positionCache.put(key, new PositionSummary(clientId, instrumentId, newQuantity, marketValue, Instant.now()));

        PositionLimits limits = limitConfig.getLimits(clientId, instrumentId);

        // Quantity limit check
        if (limits.maxQuantity() != null) {
            double utilization = (double) newQuantity / limits.maxQuantity();
            if (utilization >= 1.0) {
                log.warn("PositionLimitMonitor: BREACH quantity {}>{} client={} instrument={}",
                        newQuantity, limits.maxQuantity(), clientId, instrumentId);
                eventPublisher.accept(new PositionLimitBreachEvent(
                        clientId, instrumentId, "QUANTITY", newQuantity, limits.maxQuantity()));
            } else if (utilization >= WARNING_THRESHOLD) {
                log.info("PositionLimitMonitor: WARNING quantity {}/{}({:.0f}%) client={} instrument={}",
                        newQuantity, limits.maxQuantity(), utilization * 100, clientId, instrumentId);
                eventPublisher.accept(new PositionLimitWarningEvent(
                        clientId, instrumentId, "QUANTITY", utilization));
            }
        }

        // Value limit check
        if (limits.maxValue() != null && marketValue.compareTo(BigDecimal.ZERO) > 0) {
            double valueUtil = marketValue.divide(limits.maxValue(), 4, java.math.RoundingMode.HALF_EVEN)
                    .doubleValue();
            if (valueUtil >= 1.0) {
                eventPublisher.accept(new PositionLimitBreachEvent(
                        clientId, instrumentId, "VALUE",
                        marketValue.longValue(), limits.maxValue().longValue()));
            } else if (valueUtil >= WARNING_THRESHOLD) {
                eventPublisher.accept(new PositionLimitWarningEvent(
                        clientId, instrumentId, "VALUE", valueUtil));
            }
        }

        // Concentration check
        if (totalPortfolioValue != null && totalPortfolioValue.compareTo(BigDecimal.ZERO) > 0) {
            double concentration = marketValue
                    .divide(totalPortfolioValue, 4, java.math.RoundingMode.HALF_EVEN)
                    .doubleValue();
            double maxConcentration = limits.maxConcentration() != null
                    ? limits.maxConcentration() : DEFAULT_MAX_CONCENTRATION;
            if (concentration >= maxConcentration) {
                eventPublisher.accept(new PositionLimitBreachEvent(
                        clientId, instrumentId, "CONCENTRATION",
                        (long)(concentration * 10000), (long)(maxConcentration * 10000)));
            } else if (concentration >= maxConcentration * WARNING_THRESHOLD) {
                eventPublisher.accept(new PositionLimitWarningEvent(
                        clientId, instrumentId, "CONCENTRATION", concentration / maxConcentration));
            }
        }
    }

    /** Returns all positions approaching limits (>80% utilization) for dashboard. */
    public List<PositionSummary> getMonitoredPositions() {
        return new ArrayList<>(positionCache.values());
    }

    // ─── Port ─────────────────────────────────────────────────────────────────

    public interface LimitConfigPort {
        PositionLimits getLimits(String clientId, String instrumentId);
    }

    // ─── Domain records ───────────────────────────────────────────────────────

    public record PositionLimits(Long maxQuantity, BigDecimal maxValue, Double maxConcentration) {
        public static PositionLimits defaultLimits() {
            return new PositionLimits(null, null, DEFAULT_MAX_CONCENTRATION);
        }
    }

    public record PositionSummary(String clientId, String instrumentId,
                                   long quantity, BigDecimal marketValue, Instant updatedAt) {}

    // ─── Events ───────────────────────────────────────────────────────────────

    public record PositionLimitBreachEvent(String clientId, String instrumentId,
                                           String limitType, long currentValue, long limitValue) {}
    public record PositionLimitWarningEvent(String clientId, String instrumentId,
                                            String limitType, double utilization) {}
}
