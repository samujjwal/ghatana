package com.ghatana.appplatform.sanctions.service;

import com.ghatana.appplatform.sanctions.domain.*;
import com.ghatana.appplatform.sanctions.service.ScreeningApiService.ScreeningResultStore;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * @doc.type    Service (Application)
 * @doc.purpose Batch re-screening of all known clients against the current sanctions list (D14-011).
 *              Supports checkpoint/resume via a cursor in the DB. Configurable concurrency.
 *              Required after each major list update to catch newly added matches.
 * @doc.layer   Application Service
 * @doc.pattern Checkpoint-resume batch + Parallel partitioning
 */
public class BatchReScreeningService {

    private static final Logger log = LoggerFactory.getLogger(BatchReScreeningService.class);
    private static final int DEFAULT_CONCURRENCY = 10;
    private static final int PAGE_SIZE = 200;

    private final ScreeningEngineService engine;
    private final ScreeningResultStore resultStore;
    private final ClientNameRepository clientNameRepository;
    private final Consumer<Object> eventPublisher;

    public BatchReScreeningService(ScreeningEngineService engine,
                                    ScreeningResultStore resultStore,
                                    ClientNameRepository clientNameRepository,
                                    Consumer<Object> eventPublisher) {
        this.engine = engine;
        this.resultStore = resultStore;
        this.clientNameRepository = clientNameRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Run a full batch re-screening over all clients (D14-011).
     * Checkpoints after each page; resumes from last cursor on failure.
     *
     * @return Promise that resolves with a summary of the batch job.
     */
    public Promise<BatchSummary> runBatch(BatchConfig config) {
        ExecutorService pool = Executors.newFixedThreadPool(
                config.concurrency() > 0 ? config.concurrency() : DEFAULT_CONCURRENCY);
        var processed = new AtomicLong(0);
        var matches   = new AtomicLong(0);

        return Promise.ofBlocking(pool, () -> {
            String cursor = config.resumeFromCursor() != null
                    ? config.resumeFromCursor()
                    : clientNameRepository.firstCursor();

            log.info("Batch re-screening started, cursor={}", cursor);

            while (cursor != null) {
                List<ClientRecord> page = clientNameRepository.fetchPage(cursor, PAGE_SIZE);
                if (page.isEmpty()) break;

                // Screen entire page in parallel via the pool
                var futures = page.stream().map(client ->
                        pool.submit(() -> {
                            var request = new ScreeningRequest(
                                    java.util.UUID.randomUUID().toString(),
                                    client.fullName(),
                                    client.entityType(),
                                    client.nationality(),
                                    client.dateOfBirth(),
                                    java.util.Map.of("clientId", client.clientId()));
                            // Blocking call — already inside pool thread
                            ScreeningResult result = engine.screen(request, "BATCH-" + client.clientId())
                                    .toCompletableFuture().join();
                            resultStore.save(result);
                            if (result.matchFound()) matches.incrementAndGet();
                            processed.incrementAndGet();
                            return result;
                        })
                ).toList();

                for (var f : futures) f.get(); // Propagate any exception

                // Checkpoint cursor before moving to the next page
                cursor = clientNameRepository.nextCursor(page.getLast().clientId());
                if (cursor != null) {
                    clientNameRepository.saveCheckpoint(cursor);
                }
                log.debug("Batch re-screening progress: processed={} matches={}", processed, matches);
            }

            BatchSummary summary = new BatchSummary(processed.get(), matches.get());
            eventPublisher.accept(new BatchReScreeningCompletedEvent(summary));
            log.info("Batch re-screening complete: {}", summary);
            return summary;
        }).whenComplete(pool::shutdown);
    }

    // ─── Domain Ports ─────────────────────────────────────────────────────────

    /** Port to retrieve paginated client name data for batch re-screening. */
    public interface ClientNameRepository {
        String firstCursor();
        List<ClientRecord> fetchPage(String cursor, int pageSize);
        String nextCursor(String lastClientId);
        void saveCheckpoint(String cursor);
        String loadCheckpoint();
    }

    // ─── Supporting Types ─────────────────────────────────────────────────────

    public record ClientRecord(String clientId, String fullName, ScreeningEntityType entityType,
                                String nationality, String dateOfBirth) {}

    public record BatchConfig(int concurrency, String resumeFromCursor) {}

    public record BatchSummary(long processed, long matchesFound) {}

    public record BatchReScreeningCompletedEvent(BatchSummary summary) {}
}
