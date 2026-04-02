package com.ghatana.audio.video.vision.grpc;

import com.ghatana.audio.video.common.observability.MediaProcessingMetrics;
import com.ghatana.audio.video.vision.detection.VisionDetector;
import com.ghatana.audio.video.vision.grpc.proto.*;
import com.ghatana.audio.video.vision.model.*;
import com.ghatana.audio.video.vision.yolo.YoloV8Adapter;
import com.ghatana.audio.video.vision.video.VideoFrameExtractor;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * gRPC service implementation for computer vision object detection.
 *
 * <p>Exposes YOLO-based object detection capabilities over gRPC for
 * both single-frame and streaming video detection use cases.
 *
 * @doc.type class
 * @doc.purpose gRPC service for computer vision object detection
 * @doc.layer product
 * @doc.pattern Service
 */
public class VisionGrpcService extends VisionServiceGrpc.VisionServiceImplBase {
    
    private static final Logger LOG = LoggerFactory.getLogger(VisionGrpcService.class);
    
    private final VisionDetector detector;
    private final VideoFrameExtractor frameExtractor;
    private final AtomicLong requestCount = new AtomicLong(0);
    private final AtomicLong totalProcessingTime = new AtomicLong(0);
    private final MediaProcessingMetrics mediaMetrics;

    /** Package-private constructor for unit testing — inject a fake {@link VisionDetector}. */
    VisionGrpcService(VisionDetector detector, VideoFrameExtractor frameExtractor) {
        this(detector, frameExtractor, MediaProcessingMetrics.noop());
    }

    /** Package-private constructor for unit testing with metrics capture. */
    VisionGrpcService(VisionDetector detector, VideoFrameExtractor frameExtractor,
                      MediaProcessingMetrics metrics) {
        this.detector = detector;
        this.frameExtractor = frameExtractor;
        this.mediaMetrics = metrics;
    }

    public VisionGrpcService() {
        this(MediaProcessingMetrics.create());
    }

    public VisionGrpcService(MediaProcessingMetrics metrics) {
        String modelPath = System.getenv("VISION_MODEL_PATH");
        if (modelPath == null || modelPath.isEmpty()) {
            modelPath = System.getProperty("user.home") + "/.ghatana/models/vision";
        }

        double confidenceThreshold = Double.parseDouble(
            System.getenv().getOrDefault("VISION_CONFIDENCE_THRESHOLD", "0.5")
        );
        double nmsThreshold = Double.parseDouble(
            System.getenv().getOrDefault("VISION_NMS_THRESHOLD", "0.4")
        );

        YoloV8Adapter adapter = new YoloV8Adapter(
            Paths.get(modelPath),
            confidenceThreshold,
            nmsThreshold
        );
        this.frameExtractor = new VideoFrameExtractor();

        String modelName = System.getenv().getOrDefault("VISION_MODEL_NAME", "yolov8n");
        try {
            adapter.initialize(modelName);
            LOG.info("Vision service initialized with model: {}", modelName);
        } catch (Exception e) {
            LOG.error("Failed to initialize Vision service", e);
            throw new RuntimeException("Vision service initialization failed", e);
        }
        this.detector = adapter;
        this.mediaMetrics = metrics;
    }
    
    @Override
    public void detectObjects(DetectRequest request, StreamObserver<DetectResponse> responseObserver) {
        long startTime = System.currentTimeMillis();
        requestCount.incrementAndGet();
        mediaMetrics.recordStarted("vision.detect");
        
        try {
            if (request.getImageData().isEmpty()) {
                responseObserver.onError(io.grpc.Status.INVALID_ARGUMENT
                    .withDescription("Image data cannot be empty")
                    .asRuntimeException());
                mediaMetrics.recordFailed("vision.detect");
                return;
            }

            LOG.debug("Detecting objects in image ({} bytes)", request.getImageData().size());
            
            DetectionOptions options = DetectionOptions.builder()
                .targetClasses(new HashSet<>(request.getTargetClassesList()))
                .maxDetections(request.getMaxDetections() > 0 ? request.getMaxDetections() : 100)
                .confidenceThreshold(request.getConfidenceThreshold() > 0 ? request.getConfidenceThreshold() : 0.5)
                .build();
            
            List<DetectedObject> detectedObjects = detector.detectObjects(
                request.getImageData().toByteArray(),
                options
            );

            DetectResponse.Builder responseBuilder = DetectResponse.newBuilder();
            
            for (DetectedObject obj : detectedObjects) {
                Detection detection = Detection.newBuilder()
                    .setClassName(obj.getClassName())
                    .setConfidence(obj.getConfidence())
                    .setBoundingBox(com.ghatana.audio.video.vision.grpc.proto.BoundingBox.newBuilder()
                        .setX(obj.getBoundingBox().getX())
                        .setY(obj.getBoundingBox().getY())
                        .setWidth(obj.getBoundingBox().getWidth())
                        .setHeight(obj.getBoundingBox().getHeight())
                        .build())
                    .putAllAttributes(convertAttributes(obj.getAttributes()))
                    .build();
                
                responseBuilder.addDetections(detection);
            }
            
            long processingTime = System.currentTimeMillis() - startTime;
            totalProcessingTime.addAndGet(processingTime);
            mediaMetrics.recordSucceeded("vision.detect", processingTime);
            
            responseBuilder.setProcessingTimeMs(processingTime);
            
            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();
            
            LOG.info("Detected {} objects in {}ms", detectedObjects.size(), processingTime);

        } catch (VisionDetector.DetectionException e) {
            LOG.error("Object detection engine failure", e);
            mediaMetrics.recordFailed("vision.detect");
            responseObserver.onError(io.grpc.Status.INTERNAL
                .withDescription("Detection engine error: " + e.getMessage())
                .asRuntimeException());
        } catch (IllegalArgumentException e) {
            LOG.warn("Invalid detection request: {}", e.getMessage());
            mediaMetrics.recordFailed("vision.detect");
            responseObserver.onError(io.grpc.Status.INVALID_ARGUMENT
                .withDescription(e.getMessage())
                .asRuntimeException());
        } catch (Exception e) {
            LOG.error("Unexpected error during object detection", e);
            mediaMetrics.recordFailed("vision.detect");
            responseObserver.onError(io.grpc.Status.INTERNAL
                .withDescription("Internal detection error")
                .asRuntimeException());
        }
    }
    
    @Override
    public void analyzeImage(AnalyzeRequest request, StreamObserver<AnalyzeResponse> responseObserver) {
        long startTime = System.currentTimeMillis();
        mediaMetrics.recordStarted("vision.analyze");
        try {
            if (request.getImageData().isEmpty()) {
                responseObserver.onError(io.grpc.Status.INVALID_ARGUMENT
                    .withDescription("Image data cannot be empty")
                    .asRuntimeException());
                mediaMetrics.recordFailed("vision.analyze");
                return;
            }

            LOG.debug("Analyzing image ({} bytes)", request.getImageData().size());
            
            AnalyzeResponse.Builder responseBuilder = AnalyzeResponse.newBuilder();
            
            Set<String> analysisTypes = new HashSet<>(request.getAnalysisTypesList());
            
            if (analysisTypes.isEmpty() || analysisTypes.contains("scene")) {
                DetectionOptions options = DetectionOptions.builder()
                    .maxDetections(10)
                    .confidenceThreshold(0.5)
                    .build();
                
                List<DetectedObject> objects = detector.detectObjects(
                    request.getImageData().toByteArray(),
                    options
                );
                
                String sceneDescription = generateSceneDescription(objects);
                responseBuilder.setSceneDescription(sceneDescription);
            }
            
            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();
            mediaMetrics.recordSucceeded("vision.analyze", System.currentTimeMillis() - startTime);

            LOG.info("Image analysis completed");

        } catch (VisionDetector.DetectionException e) {
            LOG.error("Image analysis engine failure", e);
            mediaMetrics.recordFailed("vision.analyze");
            responseObserver.onError(io.grpc.Status.INTERNAL
                .withDescription("Analysis engine error: " + e.getMessage())
                .asRuntimeException());
        } catch (IllegalArgumentException e) {
            LOG.warn("Invalid image analysis request: {}", e.getMessage());
            mediaMetrics.recordFailed("vision.analyze");
            responseObserver.onError(io.grpc.Status.INVALID_ARGUMENT
                .withDescription(e.getMessage())
                .asRuntimeException());
        } catch (Exception e) {
            LOG.error("Unexpected error during image analysis", e);
            mediaMetrics.recordFailed("vision.analyze");
            responseObserver.onError(io.grpc.Status.INTERNAL
                .withDescription("Internal analysis error")
                .asRuntimeException());
        }
    }
    
    @Override
    public void analyzeVideoFile(VideoFileRequest request, StreamObserver<VideoFileResponse> responseObserver) {
        long startTime = System.currentTimeMillis();
        Path tempDir = null;

        try {
            LOG.info("Analyzing video file ({} bytes)", request.getVideoData().size());

            // Write video bytes to temp file for FFmpeg processing
            tempDir = Files.createTempDirectory("vision-video-");
            Path videoFile = tempDir.resolve("input.mp4");
            Files.write(videoFile, request.getVideoData().toByteArray());

            // Get video metadata
            VideoFrameExtractor.VideoMetadata metadata = frameExtractor.getVideoMetadata(videoFile);
            LOG.info("Video metadata: {}", metadata);

            // Configure frame extraction based on video duration
            int fps = request.getSampleFps() > 0 ? request.getSampleFps() : 1;
            int maxFrames = request.getMaxFrames() > 0 ? request.getMaxFrames() : 50;

            VideoFrameExtractor.ExtractionConfig extractConfig = VideoFrameExtractor.ExtractionConfig.builder()
                .fps(fps)
                .maxFrames(maxFrames)
                .resolution(640, 640)
                .format("jpg")
                .build();

            Path framesDir = tempDir.resolve("frames");
            List<VideoFrameExtractor.ExtractedFrame> frames =
                frameExtractor.extractFrames(videoFile, framesDir, extractConfig);

            LOG.info("Extracted {} frames for analysis", frames.size());

            // Configure detection
            DetectionOptions detectionOptions = DetectionOptions.builder()
                .confidenceThreshold(request.getConfidenceThreshold() > 0 ?
                    request.getConfidenceThreshold() : 0.5)
                .maxDetections(50)
                .targetClasses(request.getTargetClassesList().isEmpty() ?
                    null : new HashSet<>(request.getTargetClassesList()))
                .build();

            // Detect objects in each frame
            VideoFileResponse.Builder responseBuilder = VideoFileResponse.newBuilder();
            Map<String, Long> aggregatedCounts = new HashMap<>();

            for (VideoFrameExtractor.ExtractedFrame frame : frames) {
                byte[] frameBytes = Files.readAllBytes(frame.getPath());
                List<DetectedObject> detections = detector.detectObjects(frameBytes, detectionOptions);

                VideoFrameDetections.Builder frameResult = VideoFrameDetections.newBuilder()
                    .setTimestampMs(frame.getTimestampMs())
                    .setFrameNumber(frame.getFrameNumber());

                for (DetectedObject obj : detections) {
                    frameResult.addDetections(Detection.newBuilder()
                        .setClassName(obj.getClassName())
                        .setConfidence(obj.getConfidence())
                        .setBoundingBox(com.ghatana.audio.video.vision.grpc.proto.BoundingBox.newBuilder()
                            .setX(obj.getBoundingBox().getX())
                            .setY(obj.getBoundingBox().getY())
                            .setWidth(obj.getBoundingBox().getWidth())
                            .setHeight(obj.getBoundingBox().getHeight())
                            .build())
                        .build());
                    aggregatedCounts.merge(obj.getClassName(), 1L, Long::sum);
                }

                responseBuilder.addFrameResults(frameResult.build());
            }

            long processingTime = System.currentTimeMillis() - startTime;

            responseBuilder
                .setTotalFramesAnalyzed(frames.size())
                .setProcessingTimeMs(processingTime)
                .setVideoDurationMs((long) (metadata.getDurationSeconds() * 1000))
                .putAllObjectCounts(aggregatedCounts)
                .setSummary(generateVideoSummary(aggregatedCounts, frames.size(),
                    metadata.getDurationSeconds()));

            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();

            LOG.info("Video analysis completed: {} frames, {}ms", frames.size(), processingTime);

        } catch (IOException e) {
            LOG.error("Video analysis failed", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                .withDescription("Video analysis failed: " + e.getMessage())
                .asRuntimeException());
        } finally {
            // Cleanup temp files
            if (tempDir != null) {
                cleanupTempDirectory(tempDir);
            }
        }
    }

    @Override
    public StreamObserver<VideoFrameRequest> processVideo(StreamObserver<VideoFrameResponse> responseObserver) {
        return new StreamObserver<VideoFrameRequest>() {
            @Override
            public void onNext(VideoFrameRequest request) {
                try {
                    LOG.debug("Processing video frame {} at {}ms", 
                        request.getFrameNumber(), request.getTimestampMs());
                    
                    DetectionOptions options = DetectionOptions.builder()
                        .maxDetections(50)
                        .confidenceThreshold(0.6)
                        .build();
                    
                    List<DetectedObject> detectedObjects = detector.detectObjects(
                        request.getFrameData().toByteArray(),
                        options
                    );
                    
                    VideoFrameResponse.Builder responseBuilder = VideoFrameResponse.newBuilder()
                        .setTimestampMs(request.getTimestampMs())
                        .setFrameNumber(request.getFrameNumber());
                    
                    for (DetectedObject obj : detectedObjects) {
                        Detection detection = Detection.newBuilder()
                            .setClassName(obj.getClassName())
                            .setConfidence(obj.getConfidence())
                            .setBoundingBox(com.ghatana.audio.video.vision.grpc.proto.BoundingBox.newBuilder()
                                .setX(obj.getBoundingBox().getX())
                                .setY(obj.getBoundingBox().getY())
                                .setWidth(obj.getBoundingBox().getWidth())
                                .setHeight(obj.getBoundingBox().getHeight())
                                .build())
                            .build();
                        
                        responseBuilder.addDetections(detection);
                    }
                    
                    responseObserver.onNext(responseBuilder.build());
                    
                } catch (Exception e) {
                    LOG.error("Video frame processing failed", e);
                    responseObserver.onError(e);
                }
            }
            
            @Override
            public void onError(Throwable t) {
                LOG.error("Video processing stream error", t);
            }
            
            @Override
            public void onCompleted() {
                responseObserver.onCompleted();
                LOG.info("Video processing stream completed");
            }
        };
    }
    
    @Override
    public void getStatus(StatusRequest request, StreamObserver<StatusResponse> responseObserver) {
        try {
            long count = requestCount.get();
            long totalTime = totalProcessingTime.get();
            double avgTime = count > 0 ? (double) totalTime / count : 0.0;
            
            StatusResponse response = StatusResponse.newBuilder()
                .setStatus(detector.isInitialized() ? "healthy" : "not_initialized")
                .setModelName("YOLOv8")
                .setTotalRequests(count)
                .setAvgProcessingTimeMs(avgTime)
                .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
        } catch (Exception e) {
            LOG.error("Failed to get status", e);
            responseObserver.onError(e);
        }
    }
    
    @Override
    public void healthCheck(HealthCheckRequest request, StreamObserver<HealthCheckResponse> responseObserver) {
        try {
            boolean healthy = detector.isInitialized();
            String message = healthy ? "Vision service is healthy" : "Vision service not initialized";
            
            HealthCheckResponse response = HealthCheckResponse.newBuilder()
                .setHealthy(healthy)
                .setMessage(message)
                .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
        } catch (Exception e) {
            LOG.error("Health check failed", e);
            responseObserver.onError(e);
        }
    }
    
    private Map<String, String> convertAttributes(ObjectAttributes attributes) {
        Map<String, String> map = new HashMap<>();
        if (attributes != null) {
            if (attributes.getSize() != null) {
                map.put("size", attributes.getSize());
            }
        }
        return map;
    }
    
    private String generateVideoSummary(Map<String, Long> objectCounts, int frameCount,
                                         double durationSeconds) {
        if (objectCounts.isEmpty()) {
            return String.format("No objects detected across %d frames (%.1fs video)",
                frameCount, durationSeconds);
        }

        // Find most frequent objects
        List<Map.Entry<String, Long>> sorted = new ArrayList<>(objectCounts.entrySet());
        sorted.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Video analysis (%.1fs, %d frames): detected ", durationSeconds, frameCount));

        int limit = Math.min(5, sorted.size());
        for (int i = 0; i < limit; i++) {
            if (i > 0) sb.append(i == limit - 1 ? " and " : ", ");
            sb.append(sorted.get(i).getKey())
              .append(" (")
              .append(sorted.get(i).getValue())
              .append(" detections)");
        }

        if (sorted.size() > limit) {
            sb.append(String.format(" and %d more object types", sorted.size() - limit));
        }

        return sb.toString();
    }

    private void cleanupTempDirectory(Path dir) {
        try {
            Files.walk(dir)
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try { Files.deleteIfExists(path); }
                    catch (IOException e) { LOG.warn("Failed to delete temp file: {}", path); }
                });
        } catch (IOException e) {
            LOG.warn("Failed to cleanup temp directory: {}", dir);
        }
    }

    private String generateSceneDescription(List<DetectedObject> objects) {
        if (objects.isEmpty()) {
            return "No objects detected in the image";
        }
        
        Map<String, Long> objectCounts = new HashMap<>();
        for (DetectedObject obj : objects) {
            objectCounts.merge(obj.getClassName(), 1L, Long::sum);
        }
        
        StringBuilder description = new StringBuilder("The image contains ");
        List<String> parts = new ArrayList<>();
        
        for (Map.Entry<String, Long> entry : objectCounts.entrySet()) {
            long count = entry.getValue();
            String className = entry.getKey();
            if (count == 1) {
                parts.add("a " + className);
            } else {
                parts.add(count + " " + className + "s");
            }
        }
        
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0 && i == parts.size() - 1) {
                description.append(" and ");
            } else if (i > 0) {
                description.append(", ");
            }
            description.append(parts.get(i));
        }
        
        description.append(".");
        return description.toString();
    }
}
