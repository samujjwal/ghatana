package com.ghatana.appplatform.marketdata.service;

import com.ghatana.appplatform.marketdata.domain.MarketTick;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * @doc.type    Service (Application)
 * @doc.purpose Bulk historical tick backfill and sequence gap detection/recovery (D04-012).
 *              Imports ticks from CSV streams (exchange dump format) and persists via
 *              {@link MarketDataStore} port. Detects gaps by comparing actual vs expected
 *              sequence numbers. On gap: logs and emits {@link GapDetectedEvent} for
 *              manual or automated recovery.
 * @doc.layer   Application Service
 * @doc.pattern Batch import + gap detection; Hexagonal Architecture application service
 */
public class TickBackfillService {

    private static final Logger log = LoggerFactory.getLogger(TickBackfillService.class);
    private static final String CSV_DELIMITER = ",";

    private final MarketDataStore marketDataStore;
    private final Executor executor;
    private final Consumer<Object> eventPublisher;

    public TickBackfillService(MarketDataStore marketDataStore,
                                Executor executor,
                                Consumer<Object> eventPublisher) {
        this.marketDataStore = marketDataStore;
        this.executor = executor;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Import ticks from a CSV stream (D04-012).
     * Expected CSV header: instrumentId,price,volume,sequence,timestamp
     *
     * @param csvStream  InputStream of the CSV file.
     * @param source     Label for audit/logging.
     * @return BackfillSummary with counts of imported ticks and detected gaps.
     */
    public Promise<BackfillSummary> backfill(InputStream csvStream, String source) {
        return Promise.ofBlocking(executor, () -> {
            var imported = new AtomicLong(0);
            var gaps     = new AtomicLong(0);
            var lastSeqByInstrument = new HashMap<String, Long>();
            var batch = new ArrayList<MarketTick>(500);

            try (var reader = new BufferedReader(new InputStreamReader(csvStream))) {
                String line = reader.readLine(); // skip header
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) continue;
                    var cols = line.split(CSV_DELIMITER, -1);
                    if (cols.length < 5) continue;

                    String instrumentId = cols[0].trim();
                    BigDecimal price    = new BigDecimal(cols[1].trim());
                    long volume         = Long.parseLong(cols[2].trim());
                    long seq            = Long.parseLong(cols[3].trim());
                    Instant ts          = Instant.ofEpochMilli(Long.parseLong(cols[4].trim()));

                    // Gap detection
                    Long lastSeq = lastSeqByInstrument.put(instrumentId, seq);
                    if (lastSeq != null && seq != lastSeq + 1) {
                        long gapSize = seq - lastSeq - 1;
                        gaps.addAndGet(gapSize);
                        eventPublisher.accept(new GapDetectedEvent(instrumentId, lastSeq + 1, seq - 1, source));
                        log.warn("Tick gap: instrument={} from={} to={} size={}",
                                instrumentId, lastSeq + 1, seq - 1, gapSize);
                    }

                    batch.add(new MarketTick(instrumentId, price, volume, seq, ts, source));
                    if (batch.size() >= 500) {
                        marketDataStore.saveBatch(List.copyOf(batch));
                        imported.addAndGet(batch.size());
                        batch.clear();
                    }
                }
                if (!batch.isEmpty()) {
                    marketDataStore.saveBatch(List.copyOf(batch));
                    imported.addAndGet(batch.size());
                }
            }

            var summary = new BackfillSummary(source, imported.get(), gaps.get());
            eventPublisher.accept(new BackfillCompletedEvent(summary));
            log.info("Backfill complete: {}", summary);
            return summary;
        });
    }

    // ─── Port ────────────────────────────────────────────────────────────────

    public interface MarketDataStore {
        void saveBatch(List<MarketTick> ticks);
    }

    // ─── Supporting Types ─────────────────────────────────────────────────────

    public record BackfillSummary(String source, long ticksImported, long gapsDetected) {}
    public record BackfillCompletedEvent(BackfillSummary summary) {}
    public record GapDetectedEvent(String instrumentId, long fromSeq, long toSeq, String source) {}
}
