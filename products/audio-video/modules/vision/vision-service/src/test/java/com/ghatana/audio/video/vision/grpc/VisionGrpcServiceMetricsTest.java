/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.audio.video.vision.grpc;

import com.ghatana.audio.video.common.observability.MediaProcessingMetrics;
import com.ghatana.audio.video.vision.detection.VisionDetector;
import com.ghatana.audio.video.vision.grpc.proto.*;
import com.ghatana.audio.video.vision.model.BoundingBox;
import com.ghatana.audio.video.vision.model.DetectedObject;
import com.ghatana.audio.video.vision.model.DetectionOptions;
import com.ghatana.audio.video.vision.video.VideoFrameExtractor;
import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Unit tests verifying that {@link VisionGrpcService} wires
 * {@link MediaProcessingMetrics} correctly.
 *
 * @doc.type class
 * @doc.purpose Metrics wiring regression tests for VisionGrpcService
 * @doc.layer product
 * @doc.pattern TestCase
 */
@DisplayName("VisionGrpcService metrics wiring [GH-90000]")
class VisionGrpcServiceMetricsTest {

    private LocalFakeDetector fakeDetector;
    private VisionGrpcService service;
    private MediaProcessingMetrics metrics;

    @BeforeEach
    void setUp() { // GH-90000
        fakeDetector = new LocalFakeDetector(); // GH-90000
        VideoFrameExtractor frameExtractor = mock(VideoFrameExtractor.class); // GH-90000
        metrics = MediaProcessingMetrics.create(); // GH-90000
        service = new VisionGrpcService(fakeDetector, frameExtractor, metrics); // GH-90000
    }

    @Test
    @DisplayName("detectObjects success path: records started and succeeded, not failed [GH-90000]")
    void detectObjects_success_recordsStartedAndSucceeded() { // GH-90000
        fakeDetector.results = List.of( // GH-90000
            DetectedObject.builder() // GH-90000
                .className("car [GH-90000]")
                .confidence(0.9) // GH-90000
                .boundingBox(BoundingBox.builder().x(0).y(0).width(50).height(50).build()) // GH-90000
                .timestamp(Instant.now()) // GH-90000
                .build() // GH-90000
        );
        DetectRequest request = DetectRequest.newBuilder() // GH-90000
            .setImageData(ByteString.copyFrom(new byte[]{1, 2, 3})) // GH-90000
            .build(); // GH-90000
        SimpleObserver<DetectResponse> observer = new SimpleObserver<>(); // GH-90000

        service.detectObjects(request, observer); // GH-90000

        assertThat(observer.error).isNull(); // GH-90000
        assertThat(metrics.startedCount("vision.detect [GH-90000]")).isEqualTo(1);
        assertThat(metrics.succeededCount("vision.detect [GH-90000]")).isEqualTo(1);
        assertThat(metrics.failedCount("vision.detect [GH-90000]")).isEqualTo(0);
        assertThat(metrics.latencyMsTotal("vision.detect [GH-90000]")).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("detectObjects failure path: records started and failed, not succeeded [GH-90000]")
    void detectObjects_failure_recordsStartedAndFailed() { // GH-90000
        fakeDetector.throwOn = new VisionDetector.DetectionException("YOLO model unavailable [GH-90000]");
        DetectRequest request = DetectRequest.newBuilder() // GH-90000
            .setImageData(ByteString.copyFrom(new byte[]{1, 2, 3})) // GH-90000
            .build(); // GH-90000
        SimpleObserver<DetectResponse> observer = new SimpleObserver<>(); // GH-90000

        service.detectObjects(request, observer); // GH-90000

        assertThat(observer.error).isNotNull(); // GH-90000
        assertThat(metrics.startedCount("vision.detect [GH-90000]")).isEqualTo(1);
        assertThat(metrics.failedCount("vision.detect [GH-90000]")).isEqualTo(1);
        assertThat(metrics.succeededCount("vision.detect [GH-90000]")).isEqualTo(0);
    }

    @Test
    @DisplayName("noop metrics never throws and does not affect callers [GH-90000]")
    void noopMetrics_neverThrows() { // GH-90000
        VideoFrameExtractor frameExtractor = mock(VideoFrameExtractor.class); // GH-90000
        VisionGrpcService noopService = new VisionGrpcService(fakeDetector, frameExtractor, // GH-90000
                MediaProcessingMetrics.noop()); // GH-90000
        DetectRequest request = DetectRequest.newBuilder() // GH-90000
            .setImageData(ByteString.copyFrom(new byte[]{1, 2, 3})) // GH-90000
            .build(); // GH-90000
        SimpleObserver<DetectResponse> observer = new SimpleObserver<>(); // GH-90000

        noopService.detectObjects(request, observer); // GH-90000

        assertThat(observer.error).isNull(); // GH-90000
    }

    // -------------------------------------------------------------------------
    // Local helpers — keep minimal, avoid duplicating VisionGrpcServiceTest's fakes
    // -------------------------------------------------------------------------

    /** Minimal StreamObserver that captures the last value and any error. */
    static class SimpleObserver<T> implements StreamObserver<T> {
        T value;
        Throwable error;

        @Override public void onNext(T v) { this.value = v; } // GH-90000
        @Override public void onError(Throwable t) { this.error = t; } // GH-90000
        @Override public void onCompleted() {} // GH-90000
    }

    /** Minimal fake VisionDetector that returns configurable results or throws. */
    static class LocalFakeDetector implements VisionDetector {
        List<DetectedObject> results = Collections.emptyList(); // GH-90000
        RuntimeException throwOn;

        @Override
        public List<DetectedObject> detectObjects(byte[] imageData, DetectionOptions options) { // GH-90000
            if (throwOn != null) throw throwOn; // GH-90000
            return results;
        }

        @Override
        public boolean isInitialized() { return true; } // GH-90000
    }
}
