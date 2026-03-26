/**
 * @doc.type adapter
 * @doc.purpose gRPC service adapter for Vision Engine
 * @doc.layer platform
 * @doc.pattern Adapter
 */
package com.ghatana.media.service.grpc;

import com.ghatana.media.AudioVideoLibrary;
import com.ghatana.media.common.*;
import com.ghatana.media.vision.api.*;

import java.util.List;

/**
 * Adapter for exposing VisionEngine via gRPC.
 */
public class VisionGrpcAdapter {

    private final VisionEngine engine;

    public VisionGrpcAdapter(VisionEngine engine) {
        this.engine = engine;
    }

    /**
     * Handle detection request.
     */
    public void detect(
        com.ghatana.vision.core.grpc.proto.DetectRequest request,
        io.grpc.stub.StreamObserver<com.ghatana.vision.core.grpc.proto.DetectResponse> responseObserver) {

        try {
            ImageData image = convertImage(request.getImage());
            DetectionOptions options = convertOptions(request.getOptions());

            DetectionResult result = engine.detect(image, options);

            com.ghatana.vision.core.grpc.proto.DetectResponse response = convertResult(result);
            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            responseObserver.onError(
                io.grpc.Status.INTERNAL
                    .withDescription("Detection failed: " + e.getMessage())
                    .asRuntimeException()
            );
        }
    }

    /**
     * Handle streaming detection.
     */
    public io.grpc.stub.StreamObserver<com.ghatana.vision.core.grpc.proto.StreamingDetectRequest> streamingDetect(
        io.grpc.stub.StreamObserver<com.ghatana.vision.core.grpc.proto.DetectResponse> responseObserver) {

        // Create streaming session
        StreamingDetectionSession session = engine.createStreamingSession(
            DetectionOptions.defaults(),
            result -> {
                com.ghatana.vision.core.grpc.proto.DetectResponse response = convertResult(result);
                responseObserver.onNext(response);
            }
        );

        return new io.grpc.stub.StreamObserver<>() {
            @Override
            public void onNext(com.ghatana.vision.core.grpc.proto.StreamingDetectRequest request) {
                ImageData frame = convertImage(request.getFrame());
                session.feedFrame(frame, request.getFrameNumber());

                if (request.getIsLast()) {
                    session.endStream();
                }
            }

            @Override
            public void onError(Throwable t) {
                session.close();
                responseObserver.onError(t);
            }

            @Override
            public void onCompleted() {
                session.endStream();
                responseObserver.onCompleted();
            }
        };
    }

    /**
     * Get available models.
     */
    public void getModels(
        com.ghatana.vision.core.grpc.proto.GetModelsRequest request,
        io.grpc.stub.StreamObserver<com.ghatana.vision.core.grpc.proto.GetModelsResponse> responseObserver) {

        List<DetectionModelInfo> models = engine.getAvailableModels();

        com.ghatana.vision.core.grpc.proto.GetModelsResponse.Builder builder =
            com.ghatana.vision.core.grpc.proto.GetModelsResponse.newBuilder();

        for (DetectionModelInfo model : models) {
            builder.addModels(convertModelInfo(model));
        }

        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    // ====================================================================================
    // Type Conversions
    // ====================================================================================

    private ImageData convertImage(com.ghatana.vision.core.grpc.proto.ImageData proto) {
        return ImageData.builder()
            .data(proto.getData().toByteArray())
            .width(proto.getWidth())
            .height(proto.getHeight())
            .format(convertImageFormat(proto.getFormat()))
            .build();
    }

    private ImageFormat convertImageFormat(com.ghatana.vision.core.grpc.proto.ImageFormat format) {
        return switch (format) {
            case PNG -> ImageFormat.PNG;
            case JPEG -> ImageFormat.JPEG;
            case WEBP -> ImageFormat.WEBP;
            default -> ImageFormat.RAW;
        };
    }

    private DetectionOptions convertOptions(com.ghatana.vision.core.grpc.proto.DetectionOptions proto) {
        return DetectionOptions.builder()
            .confidenceThreshold(proto.getConfidenceThreshold())
            .maxDetections(proto.getMaxDetections())
            .classFilter(proto.getClassFilterList())
            .enableTracking(proto.getEnableTracking())
            .build();
    }

    private com.ghatana.vision.core.grpc.proto.DetectResponse convertResult(DetectionResult result) {
        com.ghatana.vision.core.grpc.proto.DetectResponse.Builder builder =
            com.ghatana.vision.core.grpc.proto.DetectResponse.newBuilder()
                .setImageWidth(result.imageWidth())
                .setImageHeight(result.imageHeight())
                .setProcessingTimeMs((int) result.processingTimeMs())
                .setModelId(result.modelId());

        for (DetectedObject obj : result.objects()) {
            builder.addObjects(convertDetectedObject(obj));
        }

        return builder.build();
    }

    private com.ghatana.vision.core.grpc.proto.DetectedObject convertDetectedObject(DetectedObject obj) {
        return com.ghatana.vision.core.grpc.proto.DetectedObject.newBuilder()
            .setClassName(obj.className())
            .setConfidence(obj.confidence())
            .setBbox(convertBoundingBox(obj.bbox()))
            .build();
    }

    private com.ghatana.vision.core.grpc.proto.BoundingBox convertBoundingBox(BoundingBox bbox) {
        return com.ghatana.vision.core.grpc.proto.BoundingBox.newBuilder()
            .setX(bbox.x())
            .setY(bbox.y())
            .setWidth(bbox.width())
            .setHeight(bbox.height())
            .setConfidence(bbox.confidence())
            .build();
    }

    private com.ghatana.vision.core.grpc.proto.DetectionModelInfo convertModelInfo(DetectionModelInfo model) {
        return com.ghatana.vision.core.grpc.proto.DetectionModelInfo.newBuilder()
            .setModelId(model.modelId())
            .setName(model.name())
            .setVersion(model.version())
            .addAllSupportedClasses(List.of(model.supportedClasses()))
            .setSizeBytes(model.sizeBytes())
            .setSupportsGpu(model.supportsGpu())
            .setInputWidth(model.inputWidth())
            .setInputHeight(model.inputHeight())
            .build();
    }
}
