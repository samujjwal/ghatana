package com.ghatana.appplatform.oms.service;

import com.ghatana.appplatform.oms.domain.*;
import com.ghatana.appplatform.oms.port.PositionStore;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * @doc.type    Service (Application)
 * @doc.purpose CQRS read-side projection: consumes FillReceived events → updates
 *              position records with running P&L (D01-016, D01-017).
 * @doc.layer   Application Service
 * @doc.pattern CQRS Read Side, Event Consumer
 */
public class PositionProjectionService {

    private static final Logger log = LoggerFactory.getLogger(PositionProjectionService.class);

    private final PositionStore positionStore;
    private final Executor executor;
    private final Consumer<Object> eventPublisher;

    public PositionProjectionService(PositionStore positionStore, Executor executor,
                                      Consumer<Object> eventPublisher,
                                      MeterRegistry meterRegistry) {
        this.positionStore = positionStore;
        this.executor = executor;
        this.eventPublisher = eventPublisher;
    }

    /** Apply fill to the position read model (called from FillReceived event handler). */
    public Promise<Position> onFillReceived(String clientId, String instrumentId, String accountId,
                                             BigDecimal fillQty, BigDecimal fillPrice, OrderSide side) {
        return Promise.ofBlocking(executor, () -> {
            Position current = positionStore.find(clientId, instrumentId, accountId).get()
                    .orElse(new Position(clientId, instrumentId, accountId,
                            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                            Instant.now()));

            Position updated = current.withFill(fillQty, fillPrice, side);
            positionStore.upsert(updated).get();

            log.debug("Position updated: client={} instrument={} qty={} avgCost={}",
                    clientId, instrumentId, updated.quantity(), updated.avgCost());
            return updated;
        });
    }
}
