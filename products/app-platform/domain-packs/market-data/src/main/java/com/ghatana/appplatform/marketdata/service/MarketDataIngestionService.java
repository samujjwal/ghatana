package com.ghatana.appplatform.marketdata.service;

import com.ghatana.appplatform.marketdata.domain.MarketTick;
import com.ghatana.appplatform.marketdata.domain.TickSource;
import com.ghatana.appplatform.marketdata.port.MarketDataStore;
import com.ghatana.appplatform.refdata.service.InstrumentService;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * @doc.type       Application Service
 * @doc.purpose    Entry point for all market tick writes.
 *                 Validates that the instrument exists in the D11 reference data
 *                 store before persisting (rejects unknown symbols early).
 *                 After persistence, publishes the raw tick to the Kafka topic
 *                 {@code siddhanta.marketdata.ticks} for downstream processing.
 *                 D04-001: tick_write_api, instrument_exists_check.
 *                 D04-002: multi_source_normalization — dispatched from feed
 *                 adapter registry, source recorded on each tick.
 * @doc.layer      Application Service
 * @doc.pattern    Hexagonal / Application Service
 */
public class MarketDataIngestionService {

    private static final Logger log = LoggerFactory.getLogger(MarketDataIngestionService.class);

    private final MarketDataStore store;
    private final InstrumentService instrumentService;
    private final TickValidationService validationService;
    private final Consumer<MarketTick> tickPublisher;     // wired to KafkaEventPublisher
    private final Counter ticksIngested;
    private final Counter ticksRejected;

    public MarketDataIngestionService(MarketDataStore store,
                                      InstrumentService instrumentService,
                                      TickValidationService validationService,
                                      Consumer<MarketTick> tickPublisher,
                                      MeterRegistry metrics) {
        this.store = store;
        this.instrumentService = instrumentService;
        this.validationService = validationService;
        this.tickPublisher = tickPublisher;
        this.ticksIngested = metrics.counter("marketdata.ticks.ingested");
        this.ticksRejected = metrics.counter("marketdata.ticks.rejected");
    }

    /**
     * Ingest a market tick.  Pipeline:
     * 1. Verify instrument exists (D11 reference data lookup).
     * 2. Run TickValidationService — sets anomalyFlag or throws for fatal errors.
     * 3. Persist to TimescaleDB.
     * 4. Publish to Kafka siddhanta.marketdata.ticks.
     *
     * @throws UnknownInstrumentException if the instrument id is not in D11.
     */
    public Promise<MarketTick> ingest(MarketTick tick) {
        return instrumentService.findById(UUID.fromString(tick.instrumentId()))
                .then(optInstrument -> {
                    if (optInstrument.isEmpty()) {
                        ticksRejected.increment();
                        return Promise.ofException(
                                new UnknownInstrumentException(tick.instrumentId()));
                    }
                    return validationService.validate(tick, optInstrument.get());
                })
                .then(validatedTick -> store.ingestTick(validatedTick)
                        .map(ignored -> validatedTick))
                .whenResult(stored -> {
                    ticksIngested.increment();
                    tickPublisher.accept(stored);
                    log.debug("marketdata.tick.ingested instrument={} seq={} source={}",
                            stored.instrumentId(), stored.sequence(), stored.source());
                });
    }

    // -----------------------------------------------------------------------
    // Checked exception
    // -----------------------------------------------------------------------

    public static final class UnknownInstrumentException extends RuntimeException {
        public UnknownInstrumentException(String instrumentId) {
            super("Market tick rejected: unknown instrument id=" + instrumentId);
        }
    }
}
