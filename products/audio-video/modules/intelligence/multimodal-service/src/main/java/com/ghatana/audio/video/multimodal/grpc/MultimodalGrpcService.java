package com.ghatana.audio.video.multimodal.grpc;

import com.ghatana.audio.video.multimodal.adapter.GrpcSttClientAdapter;
import com.ghatana.audio.video.multimodal.adapter.GrpcVisionClientAdapter;
import com.ghatana.audio.video.multimodal.engine.MultimodalAnalysisEngine;
import com.ghatana.audio.video.multimodal.engine.MultimodalResult;
import com.ghatana.audio.video.multimodal.engine.VideoAudioResult;
import com.ghatana.audio.video.multimodal.grpc.proto.*;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class MultimodalGrpcService extends MultimodalServiceGrpc.MultimodalServiceImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(MultimodalGrpcService.class);

    private final MultimodalAnalysisEngine engine;
    private final GrpcSttClientAdapter sttAdapter;
    private final GrpcVisionClientAdapter visionAdapter;
    private final AtomicLong requestCount = new AtomicLong(0);
    private final AtomicLong totalProcessingTime = new AtomicLong(0);

    public MultimodalGrpcService() {
        String sttUrl = System.getenv().getOrDefault("STT_GRPC_HOST", "localhost");
        int sttPort = Integer.parseInt(System.getenv().getOrDefault("STT_GRPC_PORT", "50051"));
        String visionUrl = System.getenv().getOrDefault("VISION_GRPC_HOST", "localhost");
        int visionPort = Integer.parseInt(System.getenv().getOrDefault("VISION_GRPC_PORT", "50054"));

        this.sttAdapter = new GrpcSttClientAdapter(sttUrl, sttPort);
        this.visionAdapter = new GrpcVisionClientAdapter(visionUrl, visionPort);
        this.engine = new MultimodalAnalysisEngine(sttAdapter, visionAdapter);

        LOG.info("Multimodal service initialized (STT={}:{}, Vision={}:{})",
                sttUrl, sttPort, visionUrl, visionPort);
    }

    @Override
    public void processMultimodal(MultimodalRequest request, StreamObserver<MultimodalResponse> responseObserver) {
        requestCount.incrementAndGet();
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
                    .putMetadata("has_text", String.valueOf(!request.getText().isEmpty()));

            totalProcessingTime.addAndGet(result.getProcessingTimeMs());
            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();

            LOG.info("Multimodal processing completed in {}ms", result.getProcessingTimeMs());

        } catch (Exception e) {
            LOG.error("Multimodal processing failed", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Multimodal processing failed: " + e.getMessage())
                    .asRuntimeException());
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
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Video-audio analysis failed: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void generateDescription(DescriptionRequest request,
                                     StreamObserver<DescriptionResponse> responseObserver) {
        try {
            LOG.debug("Generating description with style: {}", request.getStyle());

            List<String> keyElements = new ArrayList<>();
            StringBuilder description = new StringBuilder();

            if (!request.getAudioData().isEmpty()) {
                com.ghatana.audio.video.multimodal.engine.AudioResult audio =
                        sttAdapter.transcribe(request.getAudioData().toByteArray());
                if (!audio.isError() && !audio.getTranscription().isEmpty()) {
                    description.append("Speech: \"").append(audio.getTranscription()).append("\". ");
                    keyElements.add("speech");
                }
            }

            if (!request.getImageData().isEmpty()) {
                com.ghatana.audio.video.multimodal.engine.VisualResult visual =
                        visionAdapter.detectObjects(request.getImageData().toByteArray());
                if (!visual.isError()) {
                    description.append("Visual: ").append(visual.getSceneDescription()).append(". ");
                    keyElements.add("visual");
                }
            }

            if (!request.getContext().isEmpty()) {
                description.append("Context: ").append(request.getContext()).append(".");
                keyElements.add("context");
            }

            if (description.length() == 0) {
                description.append("No content to describe.");
            }

            responseObserver.onNext(DescriptionResponse.newBuilder()
                    .setDescription(description.toString())
                    .setConfidence(keyElements.isEmpty() ? 0.0 : 0.85)
                    .addAllKeyElements(keyElements)
                    .build());
            responseObserver.onCompleted();
            LOG.info("Description generation completed");

        } catch (Exception e) {
            LOG.error("Description generation failed", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Description generation failed: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void getStatus(StatusRequest request, StreamObserver<StatusResponse> responseObserver) {
        try {
            long count = requestCount.get();
            long totalTime = totalProcessingTime.get();
            double avgTime = count > 0 ? (double) totalTime / count : 0.0;

            responseObserver.onNext(StatusResponse.newBuilder()
                    .setStatus("healthy")
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
}
