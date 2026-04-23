package com.ghatana.audio.video.vision.analysis;

import com.ghatana.audio.video.vision.analysis.VideoAnalysisService.VideoAnalysisResult;
import com.ghatana.audio.video.vision.detection.VisionDetector;
import com.ghatana.audio.video.vision.model.BoundingBox;
import com.ghatana.audio.video.vision.model.DetectedObject;
import com.ghatana.audio.video.vision.model.DetectionOptions;
import com.ghatana.audio.video.vision.video.VideoFrameExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link VideoAnalysisService} (AV-009.4). // GH-90000
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("VideoAnalysisService — AV-009.4")
class VideoAnalysisServiceTest {

    @Mock
    private VisionDetector detector;

    @Mock
    private VideoFrameExtractor frameExtractor;

    @TempDir
    Path tempDir;

    private VideoAnalysisService service;
    private DetectionOptions defaultOptions;

    @BeforeEach
    void setUp() throws IOException { // GH-90000
        service = VideoAnalysisService.builder() // GH-90000
                .detector(detector) // GH-90000
                .frameExtractor(frameExtractor) // GH-90000
                .iouThreshold(0.5) // GH-90000
                .build(); // GH-90000
        defaultOptions = DetectionOptions.builder().confidenceThreshold(0.5).build(); // GH-90000

        lenient().when(detector.isInitialized()).thenReturn(true); // GH-90000
    }

    @Test
    @DisplayName("analyze processes frames and returns result with correct frame count")
    void analyzeReturnsCorrectFrameCount() throws IOException { // GH-90000
        // Arrange: 3 frames in temp dir
        Path frame1 = tempDir.resolve("frame_0001.jpg");
        Path frame2 = tempDir.resolve("frame_0002.jpg");
        Path frame3 = tempDir.resolve("frame_0003.jpg");
        Files.write(frame1, new byte[]{0x01}); // GH-90000
        Files.write(frame2, new byte[]{0x02}); // GH-90000
        Files.write(frame3, new byte[]{0x03}); // GH-90000

        List<VideoFrameExtractor.ExtractedFrame> frames = List.of( // GH-90000
                new VideoFrameExtractor.ExtractedFrame(frame1, 0L, 0), // GH-90000
                new VideoFrameExtractor.ExtractedFrame(frame2, 1000L, 1), // GH-90000
                new VideoFrameExtractor.ExtractedFrame(frame3, 2000L, 2) // GH-90000
        );

        Path fakeVideo = tempDir.resolve("test.mp4");
        Files.write(fakeVideo, new byte[]{0x00}); // GH-90000

        when(frameExtractor.extractFrames(eq(fakeVideo), any(), any())).thenReturn(frames); // GH-90000
        when(detector.detectObjects(any(), any())).thenReturn(List.of( // GH-90000
                buildDetection("person", 0.90, 10, 10, 50, 100) // GH-90000
        ));

        VideoFrameExtractor.ExtractionConfig config = VideoFrameExtractor.ExtractionConfig.builder() // GH-90000
                .fps(1).maxFrames(3).build(); // GH-90000

        // Act
        VideoAnalysisResult result = service.analyze(fakeVideo, config, defaultOptions); // GH-90000

        // Assert
        assertThat(result.framesAnalysed()).isEqualTo(3); // GH-90000
        assertThat(result.frameDetections()).hasSize(3); // GH-90000
        assertThat(result.processingTime()).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("classCounts returns aggregated object class frequencies")
    void classCountsAggregated() throws IOException { // GH-90000
        Path frame1 = tempDir.resolve("frame_0001.jpg");
        Files.write(frame1, new byte[]{0x01}); // GH-90000
        Path fakeVideo = tempDir.resolve("v.mp4");
        Files.write(fakeVideo, new byte[]{0x00}); // GH-90000

        when(frameExtractor.extractFrames(any(), any(), any())).thenReturn(List.of( // GH-90000
                new VideoFrameExtractor.ExtractedFrame(frame1, 0L, 0) // GH-90000
        ));
        when(detector.detectObjects(any(), any())).thenReturn(List.of( // GH-90000
                buildDetection("person", 0.9, 0, 0, 50, 100), // GH-90000
                buildDetection("car", 0.8, 100, 0, 50, 50) // GH-90000
        ));

        VideoFrameExtractor.ExtractionConfig config = VideoFrameExtractor.ExtractionConfig.builder().fps(1).build(); // GH-90000
        VideoAnalysisResult result = service.analyze(fakeVideo, config, defaultOptions); // GH-90000

        Map<String, Long> counts = result.classCounts(); // GH-90000
        assertThat(counts).containsKey("person");
        assertThat(counts).containsKey("car");
    }

    @Test
    @DisplayName("analyze with no detections returns result with 0 tracks")
    void analyzeNoDetections() throws IOException { // GH-90000
        Path frame1 = tempDir.resolve("frame_0001.jpg");
        Files.write(frame1, new byte[]{0x01}); // GH-90000
        Path fakeVideo = tempDir.resolve("v.mp4");
        Files.write(fakeVideo, new byte[]{0x00}); // GH-90000

        when(frameExtractor.extractFrames(any(), any(), any())).thenReturn(List.of( // GH-90000
                new VideoFrameExtractor.ExtractedFrame(frame1, 0L, 0) // GH-90000
        ));
        when(detector.detectObjects(any(), any())).thenReturn(List.of()); // GH-90000

        VideoFrameExtractor.ExtractionConfig config = VideoFrameExtractor.ExtractionConfig.builder().fps(1).build(); // GH-90000
        VideoAnalysisResult result = service.analyze(fakeVideo, config, defaultOptions); // GH-90000

        assertThat(result.uniqueTracks()).isEqualTo(0); // GH-90000
    }

    @Test
    @DisplayName("Builder rejects null detector")
    void builderRejectsNullDetector() { // GH-90000
        assertThatThrownBy(() -> VideoAnalysisService.builder() // GH-90000
                .frameExtractor(frameExtractor) // GH-90000
                .build()) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }

    @Test
    @DisplayName("Builder rejects invalid IoU threshold")
    void builderRejectsInvalidIou() { // GH-90000
        assertThatThrownBy(() -> VideoAnalysisService.builder().iouThreshold(1.5)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private DetectedObject buildDetection(String className, double confidence, // GH-90000
                                           double x, double y, double w, double h) {
        BoundingBox bbox = BoundingBox.builder().x(x).y(y).width(w).height(h).build(); // GH-90000
        return DetectedObject.builder() // GH-90000
                .className(className) // GH-90000
                .confidence(confidence) // GH-90000
                .boundingBox(bbox) // GH-90000
                .build(); // GH-90000
    }
}
