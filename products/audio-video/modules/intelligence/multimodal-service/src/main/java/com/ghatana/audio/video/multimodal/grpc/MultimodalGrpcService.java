package com.ghatana.audio.video.multimodal.grpc;

import com.ghatana.audio.video.common.observability.MediaProcessingMetrics;
import com.ghatana.audio.video.multimodal.engine.AudioVideoProcessingError;
import com.ghatana.audio.video.multimodal.engine.MultimodalAnalysisEngine;
import com.ghatana.audio.video.multimodal.engine.PlatformMultimodalAdapter;
import com.ghatana.audio.video.multimodal.engine.MultimodalResult;
import com.ghatana.audio.video.multimodal.engine.VideoAudioResult;
import com.ghatana.audio.video.multimodal.grpc.proto.*;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * gRPC service implementation for multimodal audio+video analysis.
 *
 * @doc.type class
 * @doc.purpose gRPC service exposing multimodal STT+vision analysis over gRPC transport
 * @doc.layer product
 * @doc.pattern Service, Adapter
 */
public class MultimodalGrpcService extends MultimodalServiceGrpc.MultimodalServiceImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(MultimodalGrpcService.class);

    private final MultimodalAnalysisEngine engine;
    private final PlatformMultimodalAdapter platformAdapter;
    private final AtomicLong requestCount = new AtomicLong(0);
    private final AtomicLong totalProcessingTime = new AtomicLong(0);
    private final MediaProcessingMetrics mediaMetrics;

    public MultimodalGrpcService() {
        this(MediaProcessingMetrics.create());
    }

    public MultimodalGrpcService(MediaProcessingMetrics metrics) {
        this.platformAdapter = new PlatformMultimodalAdapter();
        this.engine = new MultimodalAnalysisEngine(platformAdapter);
        this.mediaMetrics = metrics;
        LOG.info("Multimodal service initialized against platform audio-video library");
    }

    @Override
    public void processMultimodal(MultimodalRequest request, StreamObserver<MultimodalResponse> responseObserver) {
        requestCount.incrementAndGet();
        long startTime = System.currentTimeMillis();
        mediaMetrics.recordStarted("multimodal.analyse");
        try {
            LOG.debug("Processing multimodal request with {} analysis types",
                    request.getAnalysisTypesList().size());

            // Build engine request from proto — ByteString.isEmpty() and toByteArray() are valid
            com.ghatana.audio.video.multimodal.engine.MultimodalRequest engineRequest =
                    com.ghatana.audio.video.multimodal.engine.MultimodalRequest.builder()
                            .audioData(request.getAudioData().isEmpty() ? null
                                    : request.getAudioData().toByteArray())
                            .imageData(request.getImageData().isEmpty() ? null
                                    : request.getImageData().toByteArray())
                            .videoData(request.getVideoData().isEmpty() ? null
                                    : request.getVideoData().toByteArray())
                            .text(request.getText())
                            .build();

            MultimodalResult result = engine.analyse(engineRequest);

            MultimodalResponse.Builder responseBuilder = MultimodalResponse.newBuilder()
                    .setCombinedAnalysis(result.getCombinedAnalysis())
                    .setProcessingTimeMs(result.getProcessingTimeMs());

            if (result.getAudioResult() != null && !result.getAudioResult().isError()) {
                responseBuilder.setAudioAnalysis(AudioAnalysis.newBuilder()
                        .setTranscription(result.getAudioResult().getTranscription())
                        .setConfidence(result.getAudioResult().getConfidence())
                        .build());
            }

            if (result.getVisualResult() != null && !result.getVisualResult().isError()) {
                responseBuilder.setVisualAnalysis(VisualAnalysis.newBuilder()
                        .setSceneDescription(result.getVisualResult().getSceneDescription())
                        .setConfidence(result.getVisualResult().getConfidence() != null
                                ? result.getVisualResult().getConfidence() : 0.0)
                        .build());
            }

            responseBuilder
                    .putMetadata("has_audio", String.valueOf(result.getAudioResult() != null))
                    .putMetadata("has_visual", String.valueOf(result.getVisualResult() != null))
                    .putMetadata("has_text", String.valueOf(!request.getText().isEmpty()))
                    .putMetadata("processing_backend", platformAdapter.backendName())
                    .putMetadata("platform_metrics_enabled", String.valueOf(platformAdapter.metricsEnabled()));

            totalProcessingTime.addAndGet(result.getProcessingTimeMs());
            mediaMetrics.recordSucceeded("multimodal.analyse", System.currentTimeMillis() - startTime);
            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();

            LOG.info("Multimodal processing completed in {}ms", result.getProcessingTimeMs());

        } catch (Exception e) {
            LOG.error("Multimodal processing failed", e);
            mediaMetrics.recordFailed("multimodal.analyse");
            responseObserver.onError(toStatus(e, "Multimodal processing failed").asRuntimeException());
        }
    }

    @Override
    public void analyzeVideoWithAudio(VideoAudioRequest request,
                                       StreamObserver<VideoAudioResponse> responseObserver) {
        try {
            LOG.debug("Analyzing video with audio extraction: {}", request.getExtractAudio());

            VideoAudioResult result = engine.analyseVideoWithAudio(
                    request.getVideoData().toByteArray(),
                    request.getExtractAudio(),
                    request.getAnalyzeFrames(),
                    request.getFrameSampleRate() > 0 ? request.getFrameSampleRate() : 1);

            VideoAudioResponse.Builder responseBuilder = VideoAudioResponse.newBuilder()
                    .setCombinedNarrative(result.getCombinedNarrative());

            if (result.getAudioResult() != null && !result.getAudioResult().isError()) {
                responseBuilder
                        .setAudioTranscription(result.getAudioResult().getTranscription())
                        .setVideoSummary(result.getCombinedNarrative());
            }

            if (result.getVideoResult() != null && result.getVideoResult().getFrameResults() != null) {
                for (com.ghatana.audio.video.multimodal.engine.FrameResult fr
                        : result.getVideoResult().getFrameResults()) {
                    FrameAnalysis.Builder fa = FrameAnalysis.newBuilder()
                            .setTimestampMs(fr.getTimestampMs())
                            .setDescription("Frame " + fr.getFrameNumber() + " at "
                                    + fr.getTimestampMs() + "ms");
                    for (com.ghatana.audio.video.multimodal.engine.DetectionResult d
                            : fr.getDetections()) {
                        fa.addObjects(Detection.newBuilder()
                                .setClassName(d.getClassName())
                                .setConfidence(d.getConfidence())
                                .build());
                    }
                    responseBuilder.addFrameAnalyses(fa.build());
                }
            }

            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();
            LOG.info("Video with audio analysis completed");

        } catch (Exception e) {
            LOG.error("Video with audio analysis failed", e);
            responseObserver.onError(toStatus(e, "Video-audio analysis failed").asRuntimeException());
        }
    }

    @Override
    public void generateDescription(DescriptionRequest request,
                                     StreamObserver<DescriptionResponse> responseObserver) {
        try {
            LOG.debug("Generating description with style: {}", request.getStyle());

            com.ghatana.audio.video.multimodal.engine.MultimodalRequest engineRequest =
                    com.ghatana.audio.video.multimodal.engine.MultimodalRequest.builder()
                            .audioData(request.getAudioData().isEmpty() ? null : request.getAudioData().toByteArray())
                            .imageData(request.getImageData().isEmpty() ? null : request.getImageData().toByteArray())
                            .text(request.getContext())
                            .build();

            MultimodalResult analysis = engine.analyse(engineRequest);

            List<String> keyElements = new ArrayList<>();
            if (analysis.getAudioResult() != null && !analysis.getAudioResult().isError()) {
                keyElements.add("speech");
            }
            if (analysis.getVisualResult() != null && !analysis.getVisualResult().isError()) {
                keyElements.add("visual");
            }
            if (!request.getContext().isEmpty()) {
                keyElements.add("context");
            }

            responseObserver.onNext(DescriptionResponse.newBuilder()
                    .setDescription(analysis.getCombinedAnalysis())
                    .setConfidence(keyElements.isEmpty() ? 0.0 : 0.85)
                    .addAllKeyElements(keyElements)
                    .build());
            responseObserver.onCompleted();
            LOG.info("Description generation completed");

        } catch (Exception e) {
            LOG.error("Description generation failed", e);
            responseObserver.onError(toStatus(e, "Description generation failed").asRuntimeException());
        }
    }

    @Override
    public void getStatus(StatusRequest request, StreamObserver<StatusResponse> responseObserver) {
        try {
            long count = requestCount.get();
            long totalTime = totalProcessingTime.get();
            double avgTime = count > 0 ? (double) totalTime / count : 0.0;

            responseObserver.onNext(StatusResponse.newBuilder()
                    .setStatus("healthy:" + platformAdapter.backendName())
                    .setTotalRequests(count)
                    .setAvgProcessingTimeMs(avgTime)
                    .build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            LOG.error("Failed to get status", e);
            responseObserver.onError(e);
        }
    }

    @Override
    public void healthCheck(HealthCheckRequest request,
                             StreamObserver<HealthCheckResponse> responseObserver) {
        try {
            responseObserver.onNext(HealthCheckResponse.newBuilder()
                    .setHealthy(true)
                    .setMessage("Multimodal service is healthy")
                    .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            LOG.error("Health check failed", e);
            responseObserver.onError(e);
        }
    }

    private Status toStatus(Exception exception, String context) {
        AudioVideoProcessingError error = AudioVideoProcessingError.fromThrowable(context, exception);
        Status baseStatus = error.retryable() ? Status.UNAVAILABLE : Status.INTERNAL;
        if ("validation".equals(error.category())) {
            baseStatus = Status.INVALID_ARGUMENT;
        }
        return baseStatus.withDescription(error.code() + ": " + error.message());
    }
}
