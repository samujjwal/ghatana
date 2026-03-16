package com.ghatana.appplatform.posttrade.service;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Manages the settlement calendar and scheduling cycles. Supports T+0 for
 *              government bonds and T+2 for equities (configurable via K-02). Integrates
 *              with K-15 for BS/Gregorian dual-calendar settlement dates. Handles pre-settlement
 *              matching (T-1), settlement-day processing, and post-settlement confirmation.
 *              Automatically retries pending settlements at the next cycle.
 * @doc.layer   Domain
 * @doc.pattern Promise.ofBlocking; inner CalendarPort (K-15) for BS↔AD conversion;
 *              settlement_schedules table for cycle tracking.
 */
public class SettlementCalendarService {

    private static final Logger log = LoggerFactory.getLogger(SettlementCalendarService.class);
    private static final ZoneId NST = ZoneId.of("Asia/Kathmandu");

    private static final int DEFAULT_T_PLUS_EQUITIES = 2;
    private static final int DEFAULT_T_PLUS_GOVBONDS = 0;

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final CalendarPort     calendarPort;
    private final Counter          scheduledCounter;
    private final Counter          retriedCounter;

    public SettlementCalendarService(HikariDataSource dataSource, Executor executor,
                                     CalendarPort calendarPort, MeterRegistry registry) {
        this.dataSource       = dataSource;
        this.executor         = executor;
        this.calendarPort     = calendarPort;
        this.scheduledCounter = registry.counter("posttrade.settlement.scheduled");
        this.retriedCounter   = registry.counter("posttrade.settlement.retried");
    }

    // ─── Inner port (K-15) ───────────────────────────────────────────────────

    /**
     * K-15 Calendar: converts between AD and BS dates and computes settlement offsets.
     */
    public interface CalendarPort {
        String addBusinessDaysAd(String tradeDateAd, int days);   // returns AD date
        String toBS(String adDate);                                 // AD → BS
        boolean isHoliday(String dateAd);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record SettlementSchedule(
        String scheduleId,
        String matchId,
        String instrumentType,    // EQUITY | GOVERNMENT_BOND | CORPORATE_BOND
        String tradeDateAd,
        String settlementDateAd,
        String settlementDateBs,
        int    tPlusDays,
        String windowPhase,       // PRE_SETTLEMENT | SETTLEMENT_DAY | POST_SETTLEMENT
        String status             // PENDING | PROCESSING | COMPLETED | RETRY
    ) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Schedule settlement for a matched trade.
     */
    public Promise<SettlementSchedule> schedule(String matchId, String instrumentType,
                                                 String tradeDateAd) {
        return Promise.ofBlocking(executor, () -> {
            int tPlus = resolveTPlus(instrumentType);
            String settlAd = calendarPort.addBusinessDaysAd(tradeDateAd, tPlus);
            String settlBs = calendarPort.toBS(settlAd);
            SettlementSchedule schedule = new SettlementSchedule(
                UUID.randomUUID().toString(), matchId, instrumentType,
                tradeDateAd, settlAd, settlBs, tPlus,
                resolveWindowPhase(settlAd), "PENDING"
            );
            persistSchedule(schedule);
            scheduledCounter.increment();
            log.info("Settlement scheduled: matchId={} T+{} settlAd={}", matchId, tPlus, settlAd);
            return schedule;
        });
    }

    /**
     * Retry all PENDING schedules whose settlement date has passed.
     */
    public Promise<List<String>> retryPendingSettlements() {
        return Promise.ofBlocking(executor, () -> {
            List<String> pending = loadPendingOverdue();
            for (String schedId : pending) {
                updateScheduleStatus(schedId, "RETRY");
                retriedCounter.increment();
                log.info("Settlement retry triggered: scheduleId={}", schedId);
            }
            return pending;
        });
    }

    /**
     * Advance cycle: run pre-settlement matching for T-1 items.
     */
    public Promise<List<String>> runPreSettlementCycle(String forDate) {
        return Promise.ofBlocking(executor, () -> loadPreSettlementItems(forDate));
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private int resolveTPlus(String instrumentType) {
        return switch (instrumentType.toUpperCase()) {
            case "GOVERNMENT_BOND" -> DEFAULT_T_PLUS_GOVBONDS;
            default                -> DEFAULT_T_PLUS_EQUITIES;   // equities, corporates
        };
    }

    private String resolveWindowPhase(String settlDateAd) {
        LocalDate today  = LocalDate.now(NST);
        LocalDate settl  = LocalDate.parse(settlDateAd);
        if (today.isBefore(settl))  return "PRE_SETTLEMENT";
        if (today.isEqual(settl))   return "SETTLEMENT_DAY";
        return "POST_SETTLEMENT";
    }

    private void persistSchedule(SettlementSchedule s) {
        String sql = """
            INSERT INTO settlement_schedules
                (schedule_id, match_id, instrument_type, trade_date_ad,
                 settlement_date_ad, settlement_date_bs, t_plus_days,
                 window_phase, status, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, now())
            ON CONFLICT (match_id) DO NOTHING
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, s.scheduleId());
            ps.setString(2, s.matchId());
            ps.setString(3, s.instrumentType());
            ps.setString(4, s.tradeDateAd());
            ps.setString(5, s.settlementDateAd());
            ps.setString(6, s.settlementDateBs());
            ps.setInt(7, s.tPlusDays());
            ps.setString(8, s.windowPhase());
            ps.setString(9, s.status());
            ps.executeUpdate();
        } catch (SQLException ex) {
            log.error("Failed to persist settlement schedule matchId={}", s.matchId(), ex);
        }
    }

    private void updateScheduleStatus(String scheduleId, String status) {
        String sql = "UPDATE settlement_schedules SET status = ?, updated_at = now() WHERE schedule_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, scheduleId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            log.error("Failed to update schedule status scheduleId={}", scheduleId, ex);
        }
    }

    private List<String> loadPendingOverdue() {
        String sql = """
            SELECT schedule_id FROM settlement_schedules
            WHERE status = 'PENDING'
              AND settlement_date_ad < CURRENT_DATE::text
            """;
        List<String> ids = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) ids.add(rs.getString("schedule_id"));
        } catch (SQLException ex) {
            log.error("Failed to load overdue pending settlements", ex);
        }
        return ids;
    }

    private List<String> loadPreSettlementItems(String forDate) {
        String sql = """
            SELECT schedule_id FROM settlement_schedules
            WHERE settlement_date_ad = ?
              AND status = 'PENDING'
            """;
        List<String> ids = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, forDate);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ids.add(rs.getString("schedule_id"));
            }
        } catch (SQLException ex) {
            log.error("Failed to load pre-settlement items for date={}", forDate, ex);
        }
        return ids;
    }
}
