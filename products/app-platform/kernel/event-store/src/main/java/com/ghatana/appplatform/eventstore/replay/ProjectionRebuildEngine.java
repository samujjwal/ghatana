package com.ghatana.appplatform.eventstore.replay;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Orchestrates rebuilding of event-sourced projections by clearing their state and
 * re-applying historical events through the {@link EventReplayEngine}.
 *
 * <h2>Rebuild phases</h2>
 * <ol>
 *   <li>Call {@link RebuildableProjection#clear()} to empty the projection's read store.</li>
 *   <li>Invoke {@link EventReplayEngine#replay} with the {@link ReplayFilter} scoped to the tenant.</li>
 *   <li>Each replayed event is forwarded to {@link RebuildableProjection#applyEvent}.</li>
 *   <li>Track progress through the returned {@link ReplayProgress}.</li>
 * </ol>
 *
 * <p>Rebuilds run on a single worker thread per call so they never block the ActiveJ eventloop.
 * Concurrent rebuilds for <em>different</em> projections execute in parallel; concurrent
 * rebuilds for the <em>same</em> projection are rejected (idempotency guard).
 *
 * @doc.type class
 * @doc.purpose Manages projection state clearing and event replay for rebuilds (STORY-K05-022)
 * @doc.layer product
 * @doc.pattern Service
 */
public final class ProjectionRebuildEngine {

    private static final Logger log = LoggerFactory.getLogger(ProjectionRebuildEngine.class);

    private final EventReplayEngine replayEngine;
    private final ExecutorService executor;

    /** Tracks in-progress rebuilds to prevent concurrent duplicates. */
    private final Map<String, Future<ReplayProgress>> activeRebuilds = new ConcurrentHashMap<>();

    /**
     * @param replayEngine engine that reads events from the event store
     */
    public ProjectionRebuildEngine(EventReplayEngine replayEngine) {
        this.replayEngine = Objects.requireNonNull(replayEngine, "replayEngine");
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "projection-rebuild-worker");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Triggers an asynchronous rebuild of the given projection for the specified tenant.
     *
     * <p>If a rebuild for the same projection + tenant is already running, returns the
     * existing future without starting a new one.
     *
     * @param projection the projection to rebuild
     * @param filter     constrains which events are replayed (typically {@code ReplayFilter.forTenant(tenantId)})
     * @return a Future containing the final {@link ReplayProgress} when the rebuild completes
     * @throws IllegalStateException if a rebuild for this projection is already in progress
     */
    public Future<ReplayProgress> startRebuild(RebuildableProjection projection, ReplayFilter filter) {
        Objects.requireNonNull(projection, "projection");
        Objects.requireNonNull(filter, "filter");

        String key = rebuildKey(projection.projectionName(), filter.tenantId());
        Future<ReplayProgress> existing = activeRebuilds.get(key);
        if (existing != null && !existing.isDone()) {
            log.warn("[ProjectionRebuildEngine] Rebuild already in progress for projection={} tenant={}",
                projection.projectionName(), filter.tenantId());
            return existing;
        }

        Future<ReplayProgress> future = executor.submit(() -> {
            log.info("[ProjectionRebuildEngine] Starting rebuild: projection={} tenant={}",
                projection.projectionName(), filter.tenantId());
            try {
                projection.clear();
                log.debug("[ProjectionRebuildEngine] Cleared projection={}", projection.projectionName());

                ReplayProgress progress = replayEngine.replay(filter, projection::applyEvent);

                log.info("[ProjectionRebuildEngine] Rebuild complete: projection={} tenant={} " +
                         "replayed={} failed={} elapsed={}",
                    projection.projectionName(),
                    filter.tenantId(),
                    progress.eventsReplayed(),
                    progress.eventsFailed(),
                    java.time.Duration.between(progress.startedAt(), progress.lastEventAt())
                );
                return progress;
            } catch (Exception e) {
                log.error("[ProjectionRebuildEngine] Rebuild failed: projection={} tenant={}",
                    projection.projectionName(), filter.tenantId(), e);
                throw e;
            } finally {
                activeRebuilds.remove(key);
            }
        });

        activeRebuilds.put(key, future);
        return future;
    }

    /**
     * Returns {@code true} if a rebuild for the given projection + tenant is currently running.
     */
    public boolean isRebuildInProgress(String projectionName, String tenantId) {
        Future<ReplayProgress> f = activeRebuilds.get(rebuildKey(projectionName, tenantId));
        return f != null && !f.isDone();
    }

    private static String rebuildKey(String projectionName, String tenantId) {
        return projectionName + ":" + tenantId;
    }
}
