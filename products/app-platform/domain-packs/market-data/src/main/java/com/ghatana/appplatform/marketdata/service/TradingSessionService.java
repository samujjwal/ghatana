package com.ghatana.appplatform.marketdata.service;

import com.ghatana.appplatform.marketdata.domain.TradingSession;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * @doc.type    Service (Application)
 * @doc.purpose Manages the NEPSE market trading session lifecycle (D04-010).
 *              Session schedule comes from K-02 configuration. Holiday awareness
 *              provided by the K-15 holiday calendar — on holidays all sessions are CLOSED.
 *              Emits {@link SessionStateChangedEvent} on each transition.
 *              The OMS queries {@link #currentSession()} before routing orders.
 * @doc.layer   Application Service
 * @doc.pattern Hexagonal Architecture — application service; State machine
 */
public class TradingSessionService {

    private static final Logger log = LoggerFactory.getLogger(TradingSessionService.class);

    /**
     * Default NEPSE session schedule (Nepal Standard Time, UTC+5:45).
     * From K-02 in production; these are override-able via constructor.
     */
    private static final ZoneId NST = ZoneId.of("Asia/Kathmandu");
    private static final SessionSchedule DEFAULT_SCHEDULE = new SessionSchedule(
            LocalTime.of(10, 0),   // PRE_OPEN start
            LocalTime.of(11, 0),   // OPEN_AUCTION start
            LocalTime.of(11, 15),  // CONTINUOUS_TRADING start
            LocalTime.of(14, 45),  // CLOSE_AUCTION start
            LocalTime.of(15, 0),   // POST_CLOSE start
            LocalTime.of(15, 30)   // CLOSED start
    );

    private final AtomicReference<TradingSession> current =
            new AtomicReference<>(TradingSession.CLOSED);
    private final SessionSchedule schedule;
    private final HolidayCalendarPort holidayCalendar;
    private final Executor executor;
    private final Consumer<Object> eventPublisher;

    public TradingSessionService(HolidayCalendarPort holidayCalendar,
                                  Executor executor,
                                  Consumer<Object> eventPublisher) {
        this(DEFAULT_SCHEDULE, holidayCalendar, executor, eventPublisher);
    }

    public TradingSessionService(SessionSchedule schedule,
                                  HolidayCalendarPort holidayCalendar,
                                  Executor executor,
                                  Consumer<Object> eventPublisher) {
        this.schedule = schedule;
        this.holidayCalendar = holidayCalendar;
        this.executor = executor;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Evaluate the current wall-clock time and drive a session transition if needed.
     * Designed to be called once per minute by a scheduler.
     */
    public Promise<TradingSession> tick() {
        return Promise.ofBlocking(executor, () -> {
            LocalDate today = LocalDate.now(NST);
            if (holidayCalendar.isHoliday(today)) {
                return transitionTo(TradingSession.CLOSED);
            }

            LocalTime now = LocalTime.now(NST);
            TradingSession target = resolveSession(now);
            return transitionTo(target);
        });
    }

    /** Return the current session (non-blocking, for OMS order routing checks). */
    public TradingSession currentSession() {
        return current.get();
    }

    /** Manually override the session (admin or emergency use). */
    public Promise<Void> forceSession(TradingSession session, String reason) {
        return Promise.ofBlocking(executor, () -> {
            log.info("Session manually overridden to {} — reason: {}", session, reason);
            transitionTo(session);
            return (Void) null;
        });
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private TradingSession resolveSession(LocalTime now) {
        if (now.isBefore(schedule.preOpenStart())) return TradingSession.CLOSED;
        if (now.isBefore(schedule.openAuctionStart())) return TradingSession.PRE_OPEN;
        if (now.isBefore(schedule.continuousTradingStart())) return TradingSession.OPEN_AUCTION;
        if (now.isBefore(schedule.closeAuctionStart())) return TradingSession.CONTINUOUS_TRADING;
        if (now.isBefore(schedule.postCloseStart())) return TradingSession.CLOSE_AUCTION;
        if (now.isBefore(schedule.closedStart())) return TradingSession.POST_CLOSE;
        return TradingSession.CLOSED;
    }

    private TradingSession transitionTo(TradingSession next) {
        TradingSession prev = current.getAndSet(next);
        if (prev != next) {
            log.info("Trading session changed: {} → {}", prev, next);
            eventPublisher.accept(new SessionStateChangedEvent(prev, next));
        }
        return next;
    }

    // ─── Port ────────────────────────────────────────────────────────────────

    public interface HolidayCalendarPort {
        boolean isHoliday(LocalDate date);
    }

    // ─── Supporting Types ─────────────────────────────────────────────────────

    /**
     * Configurable session boundary times (all in Nepal Standard Time).
     */
    public record SessionSchedule(
            LocalTime preOpenStart,
            LocalTime openAuctionStart,
            LocalTime continuousTradingStart,
            LocalTime closeAuctionStart,
            LocalTime postCloseStart,
            LocalTime closedStart
    ) {}

    public record SessionStateChangedEvent(TradingSession from, TradingSession to) {}
}
