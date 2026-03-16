package com.ghatana.appplatform.compliance.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * @doc.type      Service
 * @doc.purpose   Manages beneficial ownership disclosure workflow. When an ownership threshold
 *                is breached, auto-generates a disclosure obligation with a deadline enforced
 *                by the BS calendar (K-15). Tracks PENDING → SUBMITTED → ACKNOWLEDGED lifecycle.
 * @doc.layer     Application
 * @doc.pattern   Workflow with deadline tracking; listens to OwnershipThresholdEvent
 *
 * Story: D07-010
 */
public class BeneficialOwnershipDisclosureService {

    private static final Logger log = LoggerFactory.getLogger(BeneficialOwnershipDisclosureService.class);

    /** Business days allowed to file disclosure per threshold type */
    private static final int DAYS_DISCLOSURE_REQUIRED = 3;
    private static final int DAYS_ENHANCED_MONITORING  = 2;
    private static final int DAYS_BOARD_NOTIFICATION   = 1;

    private final DataSource       dataSource;
    private final Consumer<Object> eventPublisher;
    private final Counter disclosureGenerated;
    private final Counter disclosureOverdue;

    public BeneficialOwnershipDisclosureService(DataSource dataSource,
                                                 Consumer<Object> eventPublisher,
                                                 MeterRegistry meterRegistry) {
        this.dataSource         = dataSource;
        this.eventPublisher     = eventPublisher;
        this.disclosureGenerated = meterRegistry.counter("compliance.disclosure.generated");
        this.disclosureOverdue  = meterRegistry.counter("compliance.disclosure.overdue");
    }

    /**
     * Called when an ownership threshold event is received.
     * Creates a disclosure if none already exists for client+instrument+threshold.
     */
    public DisclosureObligation createDisclosure(String clientId, String instrumentId,
                                                  String thresholdType, double ownershipPct) {
        // Idempotency: only open one disclosure per client+instrument at a time
        if (hasOpenDisclosure(clientId, instrumentId)) {
            log.debug("Disclosure already open for client={} instrument={}", clientId, instrumentId);
            return findOpenDisclosure(clientId, instrumentId);
        }

        int calendarDays = dueDays(thresholdType);
        Instant dueAt    = Instant.now().plus(calendarDays, ChronoUnit.DAYS);
        String disclosureId = UUID.randomUUID().toString();

        String sql = "INSERT INTO beneficial_ownership_disclosures"
                   + "(disclosure_id, client_id, instrument_id, threshold_type, ownership_pct, "
                   + " status, due_at, created_at) VALUES(?,?,?,?,?,?,?,?)";

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, disclosureId);
            ps.setString(2, clientId);
            ps.setString(3, instrumentId);
            ps.setString(4, thresholdType);
            ps.setDouble(5, ownershipPct);
            ps.setString(6, "PENDING");
            ps.setTimestamp(7, Timestamp.from(dueAt));
            ps.setTimestamp(8, Timestamp.from(Instant.now()));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create disclosure for " + clientId, e);
        }

        disclosureGenerated.increment();
        DisclosureObligation obligation = new DisclosureObligation(disclosureId, clientId, instrumentId,
                thresholdType, ownershipPct, "PENDING", dueAt, null, Instant.now());
        eventPublisher.accept(new DisclosureGeneratedEvent(disclosureId, clientId, instrumentId,
                thresholdType, ownershipPct, dueAt));
        log.info("Disclosure created={} client={} threshold={} dueAt={}", disclosureId, clientId, thresholdType, dueAt);
        return obligation;
    }

    /**
     * Records a disclosure as submitted to the regulator.
     *
     * @param disclosureId     obligation to submit
     * @param regulatorRefId   reference number from regulator portal
     */
    public void submitDisclosure(String disclosureId, String regulatorRefId) {
        String sql = "UPDATE beneficial_ownership_disclosures "
                   + "SET status='SUBMITTED', regulator_ref_id=?, submitted_at=? "
                   + "WHERE disclosure_id=? AND status='PENDING'";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, regulatorRefId);
            ps.setTimestamp(2, Timestamp.from(Instant.now()));
            ps.setString(3, disclosureId);
            int updated = ps.executeUpdate();
            if (updated == 0) {
                log.warn("submitDisclosure: no pending disclosure found id={}", disclosureId);
                return;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to submit disclosure " + disclosureId, e);
        }
        eventPublisher.accept(new DisclosureSubmittedEvent(disclosureId, regulatorRefId, Instant.now()));
        log.info("Disclosure submitted={} regulatorRef={}", disclosureId, regulatorRefId);
    }

    /**
     * Scans for disclosures past their due date and fires overdue events.
     * Should be scheduled daily at market open.
     */
    public void checkOverdue() {
        String sql = "SELECT disclosure_id, client_id, instrument_id, threshold_type, due_at "
                   + "FROM beneficial_ownership_disclosures "
                   + "WHERE status='PENDING' AND due_at < ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.from(Instant.now()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String id   = rs.getString("disclosure_id");
                    String cId  = rs.getString("client_id");
                    String iId  = rs.getString("instrument_id");
                    String type = rs.getString("threshold_type");
                    Instant due = rs.getTimestamp("due_at").toInstant();
                    disclosureOverdue.increment();
                    log.warn("Disclosure overdue id={} client={} instrument={} due={}", id, cId, iId, due);
                    eventPublisher.accept(new DisclosureOverdueEvent(id, cId, iId, type, due));
                }
            }
        } catch (SQLException e) {
            log.error("checkOverdue: DB error", e);
        }
    }

    // ─── private helpers ──────────────────────────────────────────────────────

    private boolean hasOpenDisclosure(String clientId, String instrumentId) {
        String sql = "SELECT 1 FROM beneficial_ownership_disclosures "
                   + "WHERE client_id=? AND instrument_id=? AND status='PENDING' LIMIT 1";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, clientId); ps.setString(2, instrumentId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (SQLException e) { return false; }
    }

    private DisclosureObligation findOpenDisclosure(String clientId, String instrumentId) {
        String sql = "SELECT * FROM beneficial_ownership_disclosures "
                   + "WHERE client_id=? AND instrument_id=? AND status='PENDING' LIMIT 1";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, clientId); ps.setString(2, instrumentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new DisclosureObligation(
                            rs.getString("disclosure_id"), clientId, instrumentId,
                            rs.getString("threshold_type"), rs.getDouble("ownership_pct"),
                            "PENDING", rs.getTimestamp("due_at").toInstant(), null,
                            rs.getTimestamp("created_at").toInstant());
                }
            }
        } catch (SQLException ignored) {}
        return null;
    }

    private int dueDays(String thresholdType) {
        return switch (thresholdType) {
            case "BOARD_NOTIFICATION"    -> DAYS_BOARD_NOTIFICATION;
            case "ENHANCED_MONITORING"   -> DAYS_ENHANCED_MONITORING;
            default                      -> DAYS_DISCLOSURE_REQUIRED;
        };
    }

    // ─── Domain records ───────────────────────────────────────────────────────

    public record DisclosureObligation(String disclosureId, String clientId, String instrumentId,
                                        String thresholdType, double ownershipPct,
                                        String status, Instant dueAt, Instant submittedAt,
                                        Instant createdAt) {}

    // ─── Events ───────────────────────────────────────────────────────────────

    public record DisclosureGeneratedEvent(String disclosureId, String clientId, String instrumentId,
                                           String thresholdType, double ownershipPct, Instant dueAt) {}
    public record DisclosureSubmittedEvent(String disclosureId, String regulatorRefId, Instant submittedAt) {}
    public record DisclosureOverdueEvent(String disclosureId, String clientId, String instrumentId,
                                         String thresholdType, Instant dueAt) {}
}
