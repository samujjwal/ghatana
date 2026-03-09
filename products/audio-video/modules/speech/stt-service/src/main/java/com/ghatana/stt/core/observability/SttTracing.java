package com.ghatana.stt.core.observability;

import com.ghatana.platform.observability.TracingManager;
import com.ghatana.platform.observability.TracingProvider;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.function.Supplier;

/**
 * STT-specific distributed tracing using OpenTelemetry.
 *
 * <p>Provides span creation and context propagation for STT operations.
 * Integrates with the platform TracingManager for consistent tracing
 * across all services.
 *
 * <p><b>Span Naming Convention:</b> {@code stt.<operation>}
 *
 * @doc.type class
 * @doc.purpose STT distributed tracing with OpenTelemetry
 * @doc.layer observability
 * @doc.pattern Facade
 */
public final class SttTracing {

    private static final Logger LOG = LoggerFactory.getLogger(SttTracing.class);

    private static final String INSTRUMENTATION_NAME = "stt-core";

    private final TracingProvider provider;
    private final Tracer tracer;

    /**
     * Creates STT tracing with the provided tracing manager.
     *
     * @param tracingManager the platform tracing manager
     */
    public SttTracing(TracingManager tracingManager) {
        this.provider = tracingManager.getProvider(INSTRUMENTATION_NAME);
        this.tracer = provider.getTracer();
        LOG.info("STT tracing initialized");
    }

    /**
     * Creates a new span for a transcription operation.
     *
     * @param audioLengthMs audio duration in milliseconds
     * @param modelId model being used
     * @param language target language
     * @return the span builder for further customization
     */
    public SpanBuilder transcription(long audioLengthMs, String modelId, String language) {
        return new SpanBuilder("stt.transcribe")
            .attribute("stt.audio.length_ms", audioLengthMs)
            .attribute("stt.model.id", modelId)
            .attribute("stt.language", language != null ? language : "auto");
    }

    /**
     * Creates a span for streaming session operations.
     *
     * @param sessionId session identifier
     * @param operation operation name (create, feed, close)
     * @return the span builder
     */
    public SpanBuilder streamingSession(String sessionId, String operation) {
        return new SpanBuilder("stt.session." + operation)
            .attribute("stt.session.id", sessionId);
    }

    /**
     * Creates a span for model loading.
     *
     * @param modelId model identifier
     * @param modelPath path to model file
     * @return the span builder
     */
    public SpanBuilder modelLoad(String modelId, String modelPath) {
        return new SpanBuilder("stt.model.load")
            .attribute("stt.model.id", modelId)
            .attribute("stt.model.path", modelPath);
    }

    /**
     * Creates a span for feature extraction.
     *
     * @param audioSamples number of audio samples
     * @return the span builder
     */
    public SpanBuilder featureExtraction(int audioSamples) {
        return new SpanBuilder("stt.feature.extraction")
            .attribute("stt.audio.samples", audioSamples);
    }

    /**
     * Creates a span for model inference.
     *
     * @param modelId model identifier
     * @param inputFrames number of input frames
     * @return the span builder
     */
    public SpanBuilder inference(String modelId, int inputFrames) {
        return new SpanBuilder("stt.inference")
            .attribute("stt.model.id", modelId)
            .attribute("stt.input.frames", inputFrames);
    }

    /**
     * Creates a span for adaptation operations.
     *
     * @param profileId user profile
     * @param adaptationType type of adaptation
     * @return the span builder
     */
    public SpanBuilder adaptation(String profileId, String adaptationType) {
        return new SpanBuilder("stt.adaptation")
            .attribute("stt.profile.id", profileId)
            .attribute("stt.adaptation.type", adaptationType);
    }

    /**
     * Creates a span for profile operations.
     *
     * @param profileId profile identifier
     * @param operation operation name (load, save, create)
     * @return the span builder
     */
    public SpanBuilder profile(String profileId, String operation) {
        return new SpanBuilder("stt.profile." + operation)
            .attribute("stt.profile.id", profileId);
    }

    /**
     * Executes a supplier within a span context.
     *
     * @param spanName span name
     * @param supplier the operation to execute
     * @param <T> return type
     * @return the result of the supplier
     */
    public <T> T trace(String spanName, Supplier<T> supplier) {
        Span span = tracer.spanBuilder(spanName)
            .setSpanKind(SpanKind.INTERNAL)
            .startSpan();

        try (Scope scope = span.makeCurrent()) {
            return supplier.get();
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            throw e;
        } finally {
            span.end();
        }
    }

    /**
     * Executes a runnable within a span context.
     *
     * @param spanName span name
     * @param runnable the operation to execute
     */
    public void trace(String spanName, Runnable runnable) {
        Span span = tracer.spanBuilder(spanName)
            .setSpanKind(SpanKind.INTERNAL)
            .startSpan();

        try (Scope scope = span.makeCurrent()) {
            runnable.run();
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            throw e;
        } finally {
            span.end();
        }
    }

    /**
     * Gets the current span from context.
     *
     * @return the current span, or a no-op span if none
     */
    public Span currentSpan() {
        return Span.current();
    }

    /**
     * Builder for creating and executing spans.
     */
    public class SpanBuilder {
        private final io.opentelemetry.api.trace.SpanBuilder builder;

        SpanBuilder(String name) {
            this.builder = tracer.spanBuilder(name)
                .setSpanKind(SpanKind.INTERNAL);
        }

        /**
         * Adds a string attribute.
         */
        public SpanBuilder attribute(String key, String value) {
            if (value != null) {
                builder.setAttribute(key, value);
            }
            return this;
        }

        /**
         * Adds a long attribute.
         */
        public SpanBuilder attribute(String key, long value) {
            builder.setAttribute(key, value);
            return this;
        }

        /**
         * Adds a double attribute.
         */
        public SpanBuilder attribute(String key, double value) {
            builder.setAttribute(key, value);
            return this;
        }

        /**
         * Adds a boolean attribute.
         */
        public SpanBuilder attribute(String key, boolean value) {
            builder.setAttribute(key, value);
            return this;
        }

        /**
         * Sets the span kind.
         */
        public SpanBuilder kind(SpanKind kind) {
            builder.setSpanKind(kind);
            return this;
        }

        /**
         * Sets the parent context.
         */
        public SpanBuilder parent(Context parent) {
            builder.setParent(parent);
            return this;
        }

        /**
         * Starts the span and returns it.
         */
        public Span start() {
            return builder.startSpan();
        }

        /**
         * Executes a supplier within this span.
         */
        public <T> T execute(Supplier<T> supplier) {
            Span span = builder.startSpan();
            try (Scope scope = span.makeCurrent()) {
                T result = supplier.get();
                span.setStatus(StatusCode.OK);
                return result;
            } catch (Exception e) {
                span.setStatus(StatusCode.ERROR, e.getMessage());
                span.recordException(e);
                throw e;
            } finally {
                span.end();
            }
        }

        /**
         * Executes a runnable within this span.
         */
        public void execute(Runnable runnable) {
            Span span = builder.startSpan();
            try (Scope scope = span.makeCurrent()) {
                runnable.run();
                span.setStatus(StatusCode.OK);
            } catch (Exception e) {
                span.setStatus(StatusCode.ERROR, e.getMessage());
                span.recordException(e);
                throw e;
            } finally {
                span.end();
            }
        }
    }
}
