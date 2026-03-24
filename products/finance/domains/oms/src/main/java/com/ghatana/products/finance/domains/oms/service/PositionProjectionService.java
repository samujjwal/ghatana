package com.ghatana.products.finance.domains.oms.service;


import com.ghatana.platform.core.event.EventBusPort;
import com.ghatana.products.finance.domains.oms.domain.*;
import com.ghatana.products.finance.domains.oms.port.PositionStore;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.Executor;

/**
 * OMS Position Projection Service — in-memory pre-settlement position tracking.
 *
 * <h2>Distinction from Post-Trade PositionProjectionService (F15)</h2>
 * <p>This service maintains <b>tentative</b> positions during the order lifecycle by
 * consuming FillReceived events and updating an in-memory {@link PositionStore} port.
 * The Post-Trade variant maintains the <b>authoritative settled position</b> in
 * PostgreSQL + Redis after settlement completion.</p>
 *
 * @doc.type    Service (Application)
 * @doc.purpose CQRS read-side projection: consumes FillReceived events → updates
 *              position records with running P&amp;L (D01-016, D01-017).
 * @doc.layer   Application Service
 * @doc.pattern CQRS Read Side, Event Consumer
 * @see com.ghatana.products.finance.domains.posttrade.service.PositionProjectionService Post-Trade settled variant
 */
public class PositionProjectionService {

    private static final Logger log = LoggerFactory.getLogger(PositionProjectionService.class);

    private final PositionStore positionStore;
    private final Executor executor;
    private final EventBusPort eventBusPort;

    public PositionProjectionService(PositionStore positionStore, Executor executor,
                                      EventBusPort eventBusPort,
                                      MeterRegistry meterRegistry) {
        this.positionStore = positionStore;
        this.executor = executor;
        this.eventBusPort = eventBusPort;
    }

    /** Apply fill to the position read model (called from FillReceived event handler). */
    public Promise<Position> onFillReceived(String clientId, String instrumentId, String accountId,
                                             BigDecimal fillQty, BigDecimal fillPrice, OrderSide side) {
        return Promise.ofBlocking(executor, () -> {
            Position current = positionStore.find(clientId, instrumentId, accountId).getResult()
                    .orElse(new Position(clientId, instrumentId, accountId,
                            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                            Instant.now()));

            Position updated = current.withFill(fillQty, fillPrice, side);
            positionStore.upsert(updated).getResult();

            log.debug("Position updated: client={} instrument={} qty={} avgCost={}",
                    clientId, instrumentId, updated.quantity(), updated.avgCost());
            return updated;
        });
    }
}
