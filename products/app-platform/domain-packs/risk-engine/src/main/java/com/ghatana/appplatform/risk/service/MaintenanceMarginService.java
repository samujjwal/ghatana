package com.ghatana.appplatform.risk.service;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import javax.sql.DataSource;

/**
 * @doc.type    Service (Application)
 * @doc.purpose Calculates maintenance margin and performs daily mark-to-market
 *              on client positions, triggering MarginDeficitDetected events when needed (D06-010).
 * @doc.layer   Domain — risk engine
 * @doc.pattern Observer — emits event to MarginCallService when deficit detected
 */
public class MaintenanceMarginService {

    // NEPSE equities: maintenance margin = 30% of position value;
    // initial margin = 50% (held in MarginSufficiencyService).
    private static final BigDecimal MAINTENANCE_RATE_EQUITY = new BigDecimal("0.30");
    private static final BigDecimal MAINTENANCE_RATE_BOND   = new BigDecimal("0.08");

    public record MaintenanceMarginResult(
        String clientId,
        BigDecimal positionValue,
        BigDecimal requiredMaintenance,
        BigDecimal currentEquity,       // cash posted as collateral
        BigDecimal excess,              // positive = safe, negative = deficit
        boolean deficitDetected,
        Instant evaluatedAt
    ) {}

    public record MarginDeficitEvent(String clientId, BigDecimal deficit, Instant detectedAt) {}

    private final DataSource dataSource;
    private final Executor executor;
    private final Consumer<MarginDeficitEvent> deficitListener;
    private final AtomicInteger deficitsOpen = new AtomicInteger(0);
    private final Counter mtmBatchCounter;

    public MaintenanceMarginService(DataSource dataSource, Executor executor,
                                     Consumer<MarginDeficitEvent> deficitListener,
                                     MeterRegistry registry) {
        this.dataSource = dataSource;
        this.executor = executor;
        this.deficitListener = deficitListener;
        this.mtmBatchCounter = Counter.builder("risk.margin.mtm_evaluations_total")
            .register(registry);
        Gauge.builder("risk.margin.deficits_open", deficitsOpen, AtomicInteger::get)
            .register(registry);
    }

    /** Evaluate maintenance margin for a single client (triggered on price update or on-demand). */
    public Promise<MaintenanceMarginResult> evaluate(String clientId) {
        return Promise.ofBlocking(executor, () -> {
            BigDecimal positionValue = loadPositionValue(clientId);
            BigDecimal maintenanceRequired = positionValue.multiply(MAINTENANCE_RATE_EQUITY)
                .setScale(2, RoundingMode.HALF_UP);
            BigDecimal equity = loadPostedCollateral(clientId);
            BigDecimal excess = equity.subtract(maintenanceRequired);
            boolean deficit = excess.compareTo(BigDecimal.ZERO) < 0;

            if (deficit) {
                deficitsOpen.incrementAndGet();
                deficitListener.accept(new MarginDeficitEvent(clientId, excess.negate(),
                    Instant.now()));
            }
            mtmBatchCounter.increment();
            return new MaintenanceMarginResult(clientId, positionValue, maintenanceRequired,
                equity, excess, deficit, Instant.now());
        });
    }

    /** EOD batch: evaluate all clients with open margin positions. */
    public Promise<Integer> runEodMtm() {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT DISTINCT client_id FROM margin_positions WHERE status = 'OPEN'";
            int evaluated = 0;
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String clientId = rs.getString("client_id");
                    evaluate(clientId);  // synchronous in blocking executor context
                    evaluated++;
                }
            }
            return evaluated;
        });
    }

    private BigDecimal loadPositionValue(String clientId) throws Exception {
        String sql = "SELECT COALESCE(SUM(quantity * last_price), 0) " +
                     "FROM margin_positions mp " +
                     "JOIN market_prices p ON mp.instrument_id = p.instrument_id " +
                     "WHERE mp.client_id = ? AND mp.status = 'OPEN'";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, UUID.fromString(clientId));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO;
            }
        }
    }

    private BigDecimal loadPostedCollateral(String clientId) throws Exception {
        String sql = "SELECT COALESCE(SUM(amount), 0) FROM margin_collateral " +
                     "WHERE client_id = ? AND status = 'POSTED'";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, UUID.fromString(clientId));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO;
            }
        }
    }
}
