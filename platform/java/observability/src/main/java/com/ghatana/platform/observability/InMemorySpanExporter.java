package com.ghatana.platform.observability;

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * In-memory implementation of the SpanExporter interface for testing.
 *
 * <p>InMemorySpanExporter stores spans in memory instead of exporting to external collectors.
 * This is useful for unit tests and integration tests where you need to assert on span data.</p>
 *
 * <p><b>Features:</b></p>
 * <ul>
 *   <li>Thread-safe span storage using synchronized list</li>
 *   <li>Queryable span data via {@link #getFinishedSpans()}</li>
 *   <li>Clear and size operations for test assertions</li>
 *   <li>Lifecycle management (shutdown stops accepting spans)</li>
 * </ul>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * InMemorySpanExporter exporter = new InMemorySpanExporter();
 * SdkTracerProvider provider = SdkTracerProvider.builder()
 *     .addSpanProcessor(SimpleSpanProcessor.create(exporter))
 *     .build();
 *
 * // Create and end spans
 * Span span = tracer.spanBuilder("test").startSpan();
 * span.end();
 *
 * // Assert on captured spans
 * List<SpanData> spans = exporter.getFinishedSpans();
 * assertEquals(1, spans.size());
 * assertEquals("test", spans.get(0).getName());
 *
 * // Clean up
 * exporter.clear();
 * }</pre>
 *
 * <p><b>Thread-Safety:</b> Thread-safe via synchronized list.</p>
 *
 * <p><b>Performance:</b> O(1) add, O(n) iteration. Suitable for testing only (not production).</p>
 *
 * @see TracingManager#createForTesting for typical usage pattern
 *
 * @author Platform Team
 * @created 2024-10-01
 * @updated 2025-10-29
 * @version 1.0.0
 * @type Testing Utility (SpanExporter Implementation)
 * @purpose In-memory span storage for unit and integration tests
 * @pattern Test Double pattern (fake implementation)
 * @responsibility Store spans in memory, provide queryable access, lifecycle management
 * @usage Created for test scenarios; use with SimpleSpanProcessor for synchronous export
 * @examples See class-level JavaDoc usage example; used in TracingManager.createForTesting()
 * @testing Test export, shutdown, clear operations; verify thread-safety
 * @notes For testing only; do not use in production (unbounded memory growth)
 
 *
 * @doc.type class
 * @doc.purpose In memory span exporter
 * @doc.layer core
 * @doc.pattern Component
*/
public class InMemorySpanExporter implements SpanExporter {

    private static final Logger log = LoggerFactory.getLogger(InMemorySpanExporter.class);
    
    /**
     * Thread-safe list of finished spans.
     */
    private final List<SpanData> finishedSpans = Collections.synchronizedList(new ArrayList<>());
    
    /**
     * Flag indicating whether the exporter has been shutdown.
     */
    private boolean isStopped = false;

    /**
     * Exports a collection of spans by storing them in memory.
     *
     * <p>If the exporter is stopped, returns failure. Otherwise, appends spans
     * to the internal list and returns success.</p>
     *
     * @param spans the spans to export
     * @return success if exported, failure if stopped
     */
    @Override
    public CompletableResultCode export(Collection<SpanData> spans) {
        if (isStopped) {
            return CompletableResultCode.ofFailure();
        }
        
        finishedSpans.addAll(spans);
        log.debug("Exported {} spans", spans.size());
        
        return CompletableResultCode.ofSuccess();
    }

    /**
     * Flushes any buffered spans (no-op for in-memory storage).
     *
     * @return success (always)
     */
    @Override
    public CompletableResultCode flush() {
        return CompletableResultCode.ofSuccess();
    }

    /**
     * Shuts down the exporter and clears all stored spans.
     *
     * <p>After shutdown, subsequent {@link #export} calls will fail.</p>
     *
     * @return success (always)
     */
    @Override
    public CompletableResultCode shutdown() {
        isStopped = true;
        finishedSpans.clear();
        return CompletableResultCode.ofSuccess();
    }

    /**
     * Gets all finished spans as an unmodifiable list.
     *
     * <p>This is the primary query method for test assertions.</p>
     *
     * @return unmodifiable list of finished spans
     */
    public List<SpanData> getFinishedSpans() {
        return Collections.unmodifiableList(finishedSpans);
    }

    /**
     * Clears all finished spans without shutting down the exporter.
     *
     * <p>Use this between test cases to reset state.</p>
     */
    public void clear() {
        finishedSpans.clear();
    }

    /**
     * Gets the number of finished spans.
     *
     * @return the count of stored spans
     */
    public int size() {
        return finishedSpans.size();
    }

    /**
     * Checks if there are any finished spans.
     *
     * @return true if no spans stored, false otherwise
     */
    public boolean isEmpty() {
        return finishedSpans.isEmpty();
    }
}
