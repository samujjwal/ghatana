package com.ghatana.appplatform.compliance.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.time.Instant;
import java.util.function.Consumer;

/**
 * @doc.type      Service
 * @doc.purpose   Calculates beneficial ownership percentages by aggregating positions across
 *                related entities (using D-11 entity relationship graph). Tracks against
 *                regulatory thresholds: 5%, 10%, 25%. Recalculates on every fill event.
 * @doc.layer     Application
 * @doc.pattern   Aggregation over entity graph with threshold monitoring
 *
 * Ownership = (total_shares_held_by_group / total_outstanding_shares) × 100
 *
 * Story: D07-009
 */
public class BeneficialOwnershipCalculationService {

    private static final Logger log = LoggerFactory.getLogger(BeneficialOwnershipCalculationService.class);
    private static final double THRESHOLD_DISCLOSURE   = 0.05;
    private static final double THRESHOLD_ENHANCED     = 0.10;
    private static final double THRESHOLD_BOARD        = 0.25;

    private final EntityGroupPort  entityGroupPort;
    private final PositionPort     positionPort;
    private final DataSource       dataSource;
    private final Consumer<Object> eventPublisher;
    private final Counter disclosureTriggered;
    private final Counter enhancedMonitoringTriggered;

    public BeneficialOwnershipCalculationService(EntityGroupPort entityGroupPort,
                                                  PositionPort positionPort,
                                                  DataSource dataSource,
                                                  Consumer<Object> eventPublisher,
                                                  MeterRegistry meterRegistry) {
        this.entityGroupPort           = entityGroupPort;
        this.positionPort              = positionPort;
        this.dataSource                = dataSource;
        this.eventPublisher            = eventPublisher;
        this.disclosureTriggered        = meterRegistry.counter("compliance.ownership.disclosure.triggered");
        this.enhancedMonitoringTriggered = meterRegistry.counter("compliance.ownership.enhanced.triggered");
    }

    /**
     * Recalculates beneficial ownership for a client group in an instrument after a fill.
     *
     * @param clientId            triggering client
     * @param instrumentId        instrument affected
     * @param totalOutstandingShares total outstanding shares from reference data
     */
    public OwnershipResult calculate(String clientId, String instrumentId, long totalOutstandingShares) {

        // Aggregate shares held by all related entities in the group
        java.util.List<String> groupIds = entityGroupPort.getRelatedEntityIds(clientId);
        groupIds.add(clientId);

        long totalHeld = groupIds.stream()
                .mapToLong(id -> positionPort.getQuantity(id, instrumentId))
                .sum();

        if (totalOutstandingShares == 0) return OwnershipResult.zero(clientId, instrumentId);

        BigDecimal ownershipPct = BigDecimal.valueOf(totalHeld)
                .divide(BigDecimal.valueOf(totalOutstandingShares), 6, RoundingMode.HALF_EVEN)
                .multiply(BigDecimal.valueOf(100));

        double pct = ownershipPct.doubleValue() / 100.0;

        saveOwnership(clientId, instrumentId, totalHeld, ownershipPct);
        checkThresholds(clientId, instrumentId, pct, totalHeld);

        log.debug("BeneficialOwnership: client={} instrument={} held={} pct={}%",
                clientId, instrumentId, totalHeld, ownershipPct.toPlainString());

        return new OwnershipResult(clientId, instrumentId, totalHeld, ownershipPct, Instant.now());
    }

    private void checkThresholds(String clientId, String instrumentId, double pct, long totalHeld) {
        if (pct >= THRESHOLD_BOARD) {
            log.warn("BeneficialOwnership: BOARD NOTIFICATION threshold crossed client={} {}%",
                    clientId, String.format("%.2f", pct * 100));
            eventPublisher.accept(new OwnershipThresholdEvent(clientId, instrumentId,
                    "BOARD_NOTIFICATION", pct, totalHeld));
        } else if (pct >= THRESHOLD_ENHANCED) {
            enhancedMonitoringTriggered.increment();
            eventPublisher.accept(new OwnershipThresholdEvent(clientId, instrumentId,
                    "ENHANCED_MONITORING", pct, totalHeld));
        } else if (pct >= THRESHOLD_DISCLOSURE) {
            disclosureTriggered.increment();
            eventPublisher.accept(new OwnershipThresholdEvent(clientId, instrumentId,
                    "DISCLOSURE_REQUIRED", pct, totalHeld));
        }
    }

    private void saveOwnership(String clientId, String instrumentId,
                                long totalHeld, BigDecimal ownershipPct) {
        String sql = "INSERT INTO beneficial_ownership(client_id, instrument_id, shares_held, "
                   + "ownership_pct, calculated_at) VALUES(?,?,?,?,?) "
                   + "ON CONFLICT(client_id, instrument_id) DO UPDATE "
                   + "SET shares_held = EXCLUDED.shares_held, "
                   + "ownership_pct = EXCLUDED.ownership_pct, "
                   + "calculated_at = EXCLUDED.calculated_at";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, clientId);
            ps.setString(2, instrumentId);
            ps.setLong(3, totalHeld);
            ps.setBigDecimal(4, ownershipPct);
            ps.setTimestamp(5, Timestamp.from(Instant.now()));
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("BeneficialOwnership: DB save error client={} instrument={}", clientId, instrumentId, e);
        }
    }

    // ─── Ports ────────────────────────────────────────────────────────────────

    public interface EntityGroupPort {
        java.util.List<String> getRelatedEntityIds(String entityId);
    }

    public interface PositionPort {
        long getQuantity(String clientId, String instrumentId);
    }

    // ─── Domain records ───────────────────────────────────────────────────────

    public record OwnershipResult(String clientId, String instrumentId,
                                   long sharesHeld, BigDecimal ownershipPct, Instant calculatedAt) {
        public static OwnershipResult zero(String clientId, String instrumentId) {
            return new OwnershipResult(clientId, instrumentId, 0, BigDecimal.ZERO, Instant.now());
        }
    }

    // ─── Events ───────────────────────────────────────────────────────────────

    public record OwnershipThresholdEvent(String clientId, String instrumentId,
                                          String thresholdType, double ownershipPct, long sharesHeld) {}
}
