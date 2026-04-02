package com.ghatana.audiovideo.observability.instrumentation;

import com.ghatana.audiovideo.observability.tracing.AudioVideoTracingService;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @doc.type class
 * @doc.purpose Instrumentation for Audio-Video gRPC service operations with distributed tracing
 * @doc.layer observability
 * @doc.pattern Instrumentation
 */
public class AudioVideoGrpcInstrumentation {

    private static final Logger log = LoggerFactory.getLogger(AudioVideoGrpcInstrumentation.class);
    private final AudioVideoTracingService tracingService;

    public AudioVideoGrpcInstrumentation(AudioVideoTracingService tracingService) {
        this.tracingService = tracingService;
    }

    /**
     * Instrument gRPC streaming operation with tracing
     */
    public <T> T instrumentGrpcStreaming(
            String methodName,
            String tenantId,
            GrpcStreamingTask<T> task) throws Exception {

        Span span = tracingService.startGrpcStreamingSpan(methodName, tenantId);
        long startTime = System.currentTimeMillis();

        try (Scope scope = tracingService.createScope(span)) {
            log.info("Starting gRPC streaming: {}", methodName);

            T result = task.execute();

            long duration = System.currentTimeMillis() - startTime;
            tracingService.recordSuccess(span, duration);

            log.info("gRPC streaming completed in {}ms", duration);
            return result;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            tracingService.recordError(span, e, duration);
            throw e;
        } finally {
            tracingService.clearContext();
        }
    }

    /**
     * Instrument video processing operation with tracing
     */
    public <T> T instrumentVideoProcessing(
            String processId,
            String tenantId,
            VideoProcessingTask<T> task) throws Exception {

        Span span = tracingService.startVideoProcessingSpan(processId, tenantId);
        long startTime = System.currentTimeMillis();

        try (Scope scope = tracingService.createScope(span)) {
            log.info("Starting video processing: {}", processId);

            T result = task.execute();

            long duration = System.currentTimeMillis() - startTime;
            tracingService.recordSuccess(span, duration);

            log.info("Video processing completed in {}ms", duration);
            return result;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            tracingService.recordError(span, e, duration);
            throw e;
        } finally {
            tracingService.clearContext();
        }
    }

    /**
     * Instrument audio processing operation with tracing
     */
    public <T> T instrumentAudioProcessing(
            String processId,
            String tenantId,
            AudioProcessingTask<T> task) throws Exception {

        Span span = tracingService.startAudioProcessingSpan(processId, tenantId);
        long startTime = System.currentTimeMillis();

        try (Scope scope = tracingService.createScope(span)) {
            log.info("Starting audio processing: {}", processId);

            T result = task.execute();

            long duration = System.currentTimeMillis() - startTime;
            tracingService.recordSuccess(span, duration);

            log.info("Audio processing completed in {}ms", duration);
            return result;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            tracingService.recordError(span, e, duration);
            throw e;
        } finally {
            tracingService.clearContext();
        }
    }

    /**
     * Instrument synthesis operation with tracing
     */
    public <T> T instrumentSynthesis(
            String synthesisId,
            String tenantId,
            SynthesisTask<T> task) throws Exception {

        Span span = tracingService.startSynthesisSpan(synthesisId, tenantId);
        long startTime = System.currentTimeMillis();

        try (Scope scope = tracingService.createScope(span)) {
            log.info("Starting synthesis: {}", synthesisId);

            T result = task.execute();

            long duration = System.currentTimeMillis() - startTime;
            tracingService.recordSuccess(span, duration);

            log.info("Synthesis completed in {}ms", duration);
            return result;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            tracingService.recordError(span, e, duration);
            throw e;
        } finally {
            tracingService.clearContext();
        }
    }

    /**
     * Functional interface for gRPC streaming task
     */
    @FunctionalInterface
    public interface GrpcStreamingTask<T> {
        T execute() throws Exception;
    }

    /**
     * Functional interface for video processing task
     */
    @FunctionalInterface
    public interface VideoProcessingTask<T> {
        T execute() throws Exception;
    }

    /**
     * Functional interface for audio processing task
     */
    @FunctionalInterface
    public interface AudioProcessingTask<T> {
        T execute() throws Exception;
    }

    /**
     * Functional interface for synthesis task
     */
    @FunctionalInterface
    public interface SynthesisTask<T> {
        T execute() throws Exception;
    }
}
