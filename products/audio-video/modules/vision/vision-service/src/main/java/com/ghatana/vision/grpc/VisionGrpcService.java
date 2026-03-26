package com.ghatana.vision.grpc;

/**
 * Deprecated stub — the active implementation is in
 * {@code com.ghatana.audio.video.vision.grpc.VisionGrpcService}.
 *
 * <p>This class exists only to satisfy the source-tree scan; it has no
 * runtime behaviour and will be removed in a future refactoring pass.
 *
 * @deprecated Use {@code com.ghatana.audio.video.vision.grpc.VisionGrpcService} instead.
 * @doc.type class
 * @doc.purpose Legacy stub — redirects to canonical vision gRPC service
 * @doc.layer product
 * @doc.pattern Stub
 */
@Deprecated(since = "2026.3", forRemoval = true)
@SuppressWarnings("unused")
final class VisionGrpcServiceStub {
    private VisionGrpcServiceStub() {}
}

/**
 * Vision gRPC Service using Platform Library.
 * 
 * <p>Migrated from legacy implementation to use platform library:
 * <ul>
 *   <li>Image validation (size, format, dimensions)</li>
 *   <li>Object detection with confidence thresholds</li>
 *   <li>Streaming detection for video frames</li>
 *   <li>Performance metrics (FPS tracking)</li>
 * </ul>
 */
public class VisionGrpcService extends VisionServiceGrpc.VisionServiceImplBase {
    
    private static final Logger LOG = LoggerFactory.getLogger(VisionGrpcService.class);
    
    private final AudioVideoLibrary library;
    private final MeterRegistry metrics;
    
    private final Timer detectTimer;
    private final Timer streamingTimer;
    
    public VisionGrpcService(MeterRegistry metrics) {
        this.metrics = metrics;
        
        VisionConfig config = VisionConfig.builder()
            .modelPath(Paths.get(System.getenv().getOrDefault("VISION_MODEL_PATH", "/models/yolov8n.onnx")))
            .modelId(System.getenv().getOrDefault("VISION_MODEL_ID", "yolov8n"))
            .useGpu(Boolean.parseBoolean(System.getenv().getOrDefault("VISION_USE_GPU", "true")))
            .confidenceThreshold(0.5f)
            .maxDetections(100)
            .build();
        
        this.library = AudioVideoLibrary.builder()
            .withVisionConfig(config)
            .build();
        
        this.detectTimer = Timer.builder("vision.detect")
            .description("Detection latency")
            .register(metrics);
        
        this.streamingTimer = Timer.builder("vision.detect.streaming")
            .description("Streaming detection latency")
            .register(metrics);
        
        LOG.info("Vision Service initialized with model: {}", config.modelId());
    }
    
    @Override
    public void detect(DetectRequest request, StreamObserver<DetectResponse> responseObserver) {
        String correlationId = generateCorrelationId();
        
        detectTimer.record(() -> {
            try {
                LOG.info("[{}] Detection request: {}x{}, format={}",
                    correlationId,
                    request.getImage().getWidth(),
                    request.getImage().getHeight(),
                    request.getImage().getFormat()
                );
                
                // Validate image
                ValidationResult validation = validateImage(request.getImage());
                if (!validation.valid()) {
                    LOG.warn("[{}] Validation failed: {}", correlationId, validation.error());
                    responseObserver.onError(
                        io.grpc.Status.INVALID_ARGUMENT
                            .withDescription(validation.error())
                            .asRuntimeException()
                    );
                    return;
                }
                
                ImageData image = ImageData.builder()
                    .data(request.getImage().getData().toByteArray())
                    .width(request.getImage().getWidth())
                    .height(request.getImage().getHeight())
                    .format(ImageFormat.valueOf(request.getImage().getFormat().name()))
                    .build();
                
                DetectionOptions options = DetectionOptions.builder()
                    .confidenceThreshold(request.getOptions().getConfidenceThreshold())
                    .maxDetections(request.getOptions().getMaxDetections())
                    .enableTracking(request.getOptions().getEnableTracking())
                    .build();
                
                DetectionResult result;
                try (VisionEngine vision = library.getVisionEngine()) {
                    result = vision.detect(image, options);
                }
                
                // Calculate effective FPS
                double fps = 1000.0 / result.processingTimeMs();
                
                LOG.info("[{}] Detection completed: {} objects, latency={}ms, FPS={:.1f}",
                    correlationId,
                    result.objects().size(),
                    result.processingTimeMs(),
                    fps
                );
                
                DetectResponse response = DetectResponse.newBuilder()
                    .setImageWidth(result.imageWidth())
                    .setImageHeight(result.imageHeight())
                    .setProcessingTimeMs(result.processingTimeMs())
                    .setModelId(result.modelId())
                    .addAllObjects(result.objects().stream()
                        .map(o -> DetectedObjectProto.newBuilder()
                            .setClassName(o.className())
                            .setConfidence(o.confidence())
                            .setBbox(BoundingBoxProto.newBuilder()
                                .setX(o.bbox().x())
                                .setY(o.bbox().y())
                                .setWidth(o.bbox().width())
                                .setHeight(o.bbox().height())
                                .build()
                            )
                            .build()
                        )
                        .toList()
                    )
                    .build();
                
                responseObserver.onNext(response);
                responseObserver.onCompleted();
                
            } catch (ValidationError e) {
                LOG.warn("[{}] Validation error: {}", correlationId, e.getMessage());
                responseObserver.onError(
                    io.grpc.Status.INVALID_ARGUMENT
                        .withDescription(e.getMessage())
                        .asRuntimeException()
                );
            } catch (InferenceError e) {
                LOG.error("[{}] Inference error: {}", correlationId, e.getMessage(), e);
                io.grpc.Status status = e.isRetryable()
                    ? io.grpc.Status.UNAVAILABLE
                    : io.grpc.Status.INTERNAL;
                responseObserver.onError(
                    status.withDescription(e.getMessage()).asRuntimeException()
                );
            } catch (Exception e) {
                LOG.error("[{}] Unexpected error: {}", correlationId, e.getMessage(), e);
                responseObserver.onError(
                    io.grpc.Status.INTERNAL
                        .withDescription("Detection failed")
                        .asRuntimeException()
                );
            }
        });
    }
    
    @Override
    public StreamObserver<StreamingDetectRequest> streamingDetect(
        StreamObserver<StreamingDetectResponse> responseObserver) {
        
        String sessionId = generateCorrelationId();
        LOG.info("[{}] Streaming detection session started", sessionId);
        
        VisionEngine vision = library.getVisionEngine();
        
        StreamingDetectionSession session = vision.createStreamingSession(
            DetectionOptions.defaults(),
            result -> {
                StreamingDetectResponse response = StreamingDetectResponse.newBuilder()
                    .setResult(DetectResponse.newBuilder()
                        .setImageWidth(result.imageWidth())
                        .setImageHeight(result.imageHeight())
                        .setProcessingTimeMs(result.processingTimeMs())
                        .addAllObjects(result.objects().stream()
                            .map(o -> DetectedObjectProto.newBuilder()
                                .setClassName(o.className())
                                .setConfidence(o.confidence())
                                .build()
                            )
                            .toList()
                        )
                        .build()
                    )
                    .build();
                
                responseObserver.onNext(response);
            }
        );
        
        return new StreamObserver<>() {
            private int frameCount = 0;
            private long totalLatency = 0;
            
            @Override
            public void onNext(StreamingDetectRequest request) {
                try {
                    long startTime = System.currentTimeMillis();
                    
                    ImageData frame = ImageData.builder()
                        .data(request.getFrame().getData().toByteArray())
                        .width(request.getFrame().getWidth())
                        .height(request.getFrame().getHeight())
                        .format(ImageFormat.valueOf(request.getFrame().getFormat().name()))
                        .build();
                    
                    session.feedFrame(frame, request.getFrameNumber());
                    
                    long latency = System.currentTimeMillis() - startTime;
                    totalLatency += latency;
                    frameCount++;
                    
                    if (request.getIsLast()) {
                        double avgLatency = frameCount > 0 ? (double) totalLatency / frameCount : 0;
                        double avgFps = avgLatency > 0 ? 1000.0 / avgLatency : 0;
                        
                        LOG.info("[{}] Streaming session completed: {} frames, avg_latency={}ms, avg_FPS={:.1f}",
                            sessionId, frameCount, (int) avgLatency, avgFps);
                        
                        session.endStream();
                    }
                } catch (Exception e) {
                    LOG.error("[{}] Error processing frame {}: {}", sessionId, frameCount, e.getMessage());
                }
            }
            
            @Override
            public void onError(Throwable t) {
                LOG.error("[{}] Client error: {}", sessionId, t.getMessage());
                session.close();
                responseObserver.onError(t);
            }
            
            @Override
            public void onCompleted() {
                LOG.info("[{}] Client completed stream", sessionId);
                session.endStream();
                responseObserver.onCompleted();
            }
        };
    }
    
    @Override
    public void getModels(GetModelsRequest request, StreamObserver<GetModelsResponse> responseObserver) {
        try (VisionEngine vision = library.getVisionEngine()) {
            List<DetectionModelInfo> models = vision.getAvailableModels();
            
            GetModelsResponse.Builder response = GetModelsResponse.newBuilder();
            for (DetectionModelInfo model : models) {
                response.addModels(DetectionModelInfoProto.newBuilder()
                    .setModelId(model.modelId())
                    .setName(model.name())
                    .setVersion(model.version())
                    .setSizeBytes(model.sizeBytes())
                    .setSupportsGpu(model.supportsGpu())
                    .setInputWidth(model.inputWidth())
                    .setInputHeight(model.inputHeight())
                    .build()
                );
            }
            
            responseObserver.onNext(response.build());
            responseObserver.onCompleted();
            
        } catch (Exception e) {
            LOG.error("Error listing models: {}", e.getMessage(), e);
            responseObserver.onError(
                io.grpc.Status.INTERNAL
                    .withDescription("Failed to list models")
                    .asRuntimeException()
            );
        }
    }
    
    private ValidationResult validateImage(ImageDataProto proto) {
        if (proto.getData().isEmpty()) {
            return ValidationResult.fail("Image data is empty");
        }
        
        // Max 50 MB
        long maxSize = 50 * 1024 * 1024;
        if (proto.getData().size() > maxSize) {
            return ValidationResult.fail("Image exceeds maximum size of 50 MB");
        }
        
        // Max dimensions
        if (proto.getWidth() > 4096 || proto.getHeight() > 4096) {
            return ValidationResult.fail("Image dimensions exceed maximum (4096x4096)");
        }
        
        if (proto.getWidth() <= 0 || proto.getHeight() <= 0) {
            return ValidationResult.fail("Invalid image dimensions");
        }
        
        return ValidationResult.ok();
    }
    
    private String generateCorrelationId() {
        return java.util.UUID.randomUUID().toString().substring(0, 8);
    }
    
    private record ValidationResult(boolean valid, String error) {
        static ValidationResult ok() {
            return new ValidationResult(true, null);
        }
        
        static ValidationResult fail(String error) {
            return new ValidationResult(false, error);
        }
    }
}
