package com.ghatana.appplatform.risk.service;

import com.ghatana.appplatform.risk.domain.*;
import com.ghatana.appplatform.risk.domain.RiskCheckResult.RiskStatus;
import com.ghatana.appplatform.risk.port.PositionLimitStore;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * @doc.type    Service (Application)
 * @doc.purpose Enforces position limits per client/instrument and prohibits short selling (D06-002).
 *              Rules:
 *                1. No short selling — SELL quantity cannot exceed current held quantity.
 *                2. BUY quantity cannot push position above the configured max long.
 *                3. If no max long is configured, platform default (Long.MAX_VALUE) is used.
 * @doc.layer   Application Service
 * @doc.pattern Hexagonal Architecture — application service
 */
public class PositionLimitService {

    private static final Logger log = LoggerFactory.getLogger(PositionLimitService.class);
    private static final long DEFAULT_MAX_LONG = Long.MAX_VALUE;

    private final PositionLimitStore limitStore;
    private final Executor executor;
    private final Consumer<Object> eventPublisher;

    public PositionLimitService(PositionLimitStore limitStore,
                                 Executor executor,
                                 Consumer<Object> eventPublisher) {
        this.limitStore = limitStore;
        this.executor = executor;
        this.eventPublisher = eventPublisher;
    }

    /** Check position limits for the given order (D06-002). */
    public Promise<RiskCheckResult> check(RiskCheckRequest request) {
        return Promise.ofBlocking(executor, () -> {
            long current = limitStore.findCurrentQuantity(
                    request.clientId(), request.instrumentId(), request.accountId());

            if ("SELL".equalsIgnoreCase(request.side())) {
                // No short selling — cannot sell more than held
                if (request.quantity() > current) {
                    log.warn("Short selling attempt: clientId={} instrument={} held={} sell={}",
                            request.clientId(), request.instrumentId(), current, request.quantity());
                    return deny("Short selling not permitted: held=" + current
                            + " sell=" + request.quantity());
                }
            } else {
                // BUY — check max long position limit
                long maxLong = limitStore.findMaxLong(request.clientId(), request.instrumentId())
                        .orElse(DEFAULT_MAX_LONG);
                long projected = current + request.quantity();
                if (projected > maxLong) {
                    log.warn("Position limit breach: clientId={} instrument={} projected={} max={}",
                            request.clientId(), request.instrumentId(), projected, maxLong);
                    return deny("Position limit breach: projected=" + projected + " max=" + maxLong);
                }
            }

            return new RiskCheckResult(RiskStatus.APPROVE, null,
                    BigDecimal.ZERO, BigDecimal.ZERO, 0.0);
        });
    }

    private RiskCheckResult deny(String reason) {
        return new RiskCheckResult(RiskStatus.DENY, reason,
                BigDecimal.ZERO, BigDecimal.ZERO, 0.0);
    }
}
