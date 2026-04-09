package com.ghatana.aep.devtools;

import com.ghatana.core.domain.pipeline.PipelineSpec;
import com.ghatana.core.domain.pipeline.PipelineStageSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Interactive pipeline debug console for AEP developer tooling (AEP-009.2).
 *
 * <p>Captures a chronological debug log of events flowing through each pipeline stage,
 * providing developers with a lightweight, in-process observability surface during
 * local development and testing without requiring a full Grafana/Prometheus stack.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * PipelineDebugConsole console = PipelineDebugConsole.create("my-pipeline");
 *
 * // Record stage entry and exit
 * console.recordStageEntry("enrichment-stage", event);
 * console.recordStageExit("enrichment-stage", outputEventCount, durationMs);
 * console.recordError("enrichment-stage", "upstream timeout", event);
 *
 * // Print a formatted report
 * console.printReport();
 *
 * // Query captured entries
 * List<DebugEntry> errors = console.entries().stream()
 *     .filter(e -> e.kind() == DebugEntry.Kind.ERROR)
 *     .toList();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Developer debug console for AEP pipeline event tracing
 * @doc.layer product
 * @doc.pattern Observer
 */
public final class PipelineDebugConsole {

    private static final Logger LOG = LoggerFactory.getLogger(PipelineDebugConsole.class);

    private final String pipelineName;
    private final CopyOnWriteArrayList<DebugEntry> entries;

    private PipelineDebugConsole(String pipelineName) {
        this.pipelineName = pipelineName;
        this.entries = new CopyOnWriteArrayList<>();
    }

    /**
     * Creates a new debug console for the given pipeline.
     *
     * @param pipelineName the pipeline name to attach to all debug entries
     * @return a fresh console with an empty log
     * @throws NullPointerException if pipelineName is null
     */
    public static PipelineDebugConsole create(String pipelineName) {
        Objects.requireNonNull(pipelineName, "pipelineName must not be null");
        return new PipelineDebugConsole(pipelineName);
    }

    /**
     * Attaches a console to a {@link PipelineSpec}, recording stage metadata entries
     * for each stage defined in the spec.
     *
     * @param spec the pipeline spec to attach to
     * @return a console pre-populated with stage metadata entries
     * @throws NullPointerException if spec is null
     */
    public static PipelineDebugConsole attachTo(PipelineSpec spec) {
        Objects.requireNonNull(spec, "spec must not be null");
        PipelineDebugConsole console = new PipelineDebugConsole(spec.getName());
        for (PipelineStageSpec stage : spec.getStages()) {
            console.record(DebugEntry.metadata(spec.getName(), stage.getId(), stage.getStageType()));
        }
        return console;
    }

    // ─── recording API ────────────────────────────────────────────────────────

    /**
     * Records entry into a pipeline stage.
     *
     * @param stageId    the stage receiving the event
     * @param eventSummary a short human-readable description of the event
     */
    public void recordStageEntry(String stageId, String eventSummary) {
        Objects.requireNonNull(stageId, "stageId must not be null");
        record(DebugEntry.entry(pipelineName, stageId, eventSummary));
        LOG.debug("[debug-console] [{}] ENTRY  stage={} event={}", pipelineName, stageId, eventSummary);
    }

    /**
     * Records successful exit from a pipeline stage.
     *
     * @param stageId          the stage that completed processing
     * @param outputEventCount number of output events produced
     * @param durationMs       processing duration in milliseconds
     */
    public void recordStageExit(String stageId, int outputEventCount, long durationMs) {
        Objects.requireNonNull(stageId, "stageId must not be null");
        record(DebugEntry.exit(pipelineName, stageId, outputEventCount, durationMs));
        LOG.debug("[debug-console] [{}] EXIT   stage={} outputs={} durationMs={}",
                pipelineName, stageId, outputEventCount, durationMs);
    }

    /**
     * Records an error that occurred in a pipeline stage.
     *
     * @param stageId      the stage where the error occurred
     * @param errorMessage a short description of the error
     * @param eventSummary the event that was being processed when the error occurred
     */
    public void recordError(String stageId, String errorMessage, String eventSummary) {
        Objects.requireNonNull(stageId, "stageId must not be null");
        Objects.requireNonNull(errorMessage, "errorMessage must not be null");
        record(DebugEntry.error(pipelineName, stageId, errorMessage, eventSummary));
        LOG.warn("[debug-console] [{}] ERROR  stage={} error={} event={}",
                pipelineName, stageId, errorMessage, eventSummary);
    }

    /**
     * Clears all captured debug entries.
     */
    public void clear() {
        entries.clear();
    }

    // ─── query API ────────────────────────────────────────────────────────────

    /**
     * @return an unmodifiable snapshot of all captured debug entries in chronological order
     */
    public List<DebugEntry> entries() {
        return Collections.unmodifiableList(new ArrayList<>(entries));
    }

    /**
     * @return the pipeline name this console is attached to
     */
    public String pipelineName() {
        return pipelineName;
    }

    /**
     * @return number of ERROR entries captured
     */
    public long errorCount() {
        return entries.stream().filter(e -> e.kind() == DebugEntry.Kind.ERROR).count();
    }

    /**
     * @return number of EXIT entries captured
     */
    public long exitCount() {
        return entries.stream().filter(e -> e.kind() == DebugEntry.Kind.EXIT).count();
    }

    /**
     * @return number of ENTRY entries captured
     */
    public long entryCount() {
        return entries.stream().filter(e -> e.kind() == DebugEntry.Kind.ENTRY).count();
    }

    /**
     * Prints a formatted debug report to STDOUT (for developer use during local testing).
     */
    public void printReport() {
        System.out.printf("══ PipelineDebugConsole: %s ══%n", pipelineName);
        System.out.printf("  Total entries: %d (ENTRY=%d, EXIT=%d, ERROR=%d)%n",
                entries.size(), entryCount(), exitCount(), errorCount());
        System.out.println("  ── Entries ──");
        for (DebugEntry entry : entries) {
            System.out.printf("  [%s] %-8s stage=%-30s %s%n",
                    entry.timestamp(), entry.kind(), entry.stageId(), entry.message());
        }
    }

    // ─── internal ─────────────────────────────────────────────────────────────

    private void record(DebugEntry entry) {
        entries.add(entry);
    }

    // ─── DebugEntry ──────────────────────────────────────────────────────────

    /**
     * An immutable debug log entry.
     *
     * @param pipelineName the pipeline this entry belongs to
     * @param stageId      the stage this entry belongs to
     * @param kind         the type of entry (ENTRY, EXIT, ERROR, METADATA)
     * @param message      a human-readable message
     * @param timestamp    the wall-clock time this entry was recorded
     */
    public record DebugEntry(
            String pipelineName,
            String stageId,
            Kind kind,
            String message,
            Instant timestamp
    ) {
        /** Entry kind taxonomy. */
        public enum Kind { ENTRY, EXIT, ERROR, METADATA }

        static DebugEntry entry(String pipeline, String stage, String eventSummary) {
            return new DebugEntry(pipeline, stage, Kind.ENTRY,
                    "event=" + eventSummary, Instant.now());
        }

        static DebugEntry exit(String pipeline, String stage, int outputs, long durationMs) {
            return new DebugEntry(pipeline, stage, Kind.EXIT,
                    "outputs=" + outputs + " durationMs=" + durationMs, Instant.now());
        }

        static DebugEntry error(String pipeline, String stage, String error, String event) {
            return new DebugEntry(pipeline, stage, Kind.ERROR,
                    "error=" + error + " event=" + event, Instant.now());
        }

        static DebugEntry metadata(String pipeline, String stage, String stageType) {
            return new DebugEntry(pipeline, stage, Kind.METADATA,
                    "type=" + stageType, Instant.now());
        }
    }
}
