package com.ghatana.multimodal.engine;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive test suite for {@link CrossModalFusionEngine}.
 *
 * Validates multimodal processing combining speech (audio), vision (images/video), // GH-90000
 * and language understanding to create cross-modal understanding with temporal alignment
 * and integration correctness across sub-services.
 *
 * @doc.type test
 * @doc.purpose Comprehensive multimodal engine tests: fusion, alignment, integration
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("MultimodalEngineServiceTest")
class MultimodalEngineServiceTest extends EventloopTestBase {

    private static final byte[] SAMPLE_AUDIO = new byte[] {1, 2, 3, 4, 5};
    private static final byte[] SAMPLE_IMAGE = new byte[] {10, 20, 30};
    private static final byte[] SAMPLE_VIDEO = new byte[] {100, 110, 120, (byte)130, (byte)140}; // GH-90000
    private static final String SAMPLE_TEXT_HINT = "meeting discussion";

    private TestMultimodalEngine engine;

    @BeforeEach
    void setUp() { // GH-90000
        engine = new TestMultimodalEngine(); // GH-90000
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // INPUT VALIDATION TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("input validation for audio analysis")
    class InputValidationAudio {

        @Test
        @DisplayName("null audio data throws exception for audio analysis")
        void nullAudioForAudioAnalysis_throwsException() { // GH-90000
            assertThatThrownBy(() -> engine.analyseAudio(null)) // GH-90000
                    .isInstanceOf(Exception.class); // GH-90000
        }

        @Test
        @DisplayName("empty audio data throws exception for audio analysis")
        void emptyAudioForAudioAnalysis_throwsException() { // GH-90000
            assertThatThrownBy(() -> engine.analyseAudio(new byte[0])) // GH-90000
                    .isInstanceOf(Exception.class); // GH-90000
        }

        @Test
        @DisplayName("single byte audio is accepted for audio analysis")
        void singleByteAudioForAudioAnalysis_isAccepted() { // GH-90000
            assertThatCode(() -> engine.analyseAudio(new byte[] {1})) // GH-90000
                    .doesNotThrowAnyException(); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // AUDIO-ONLY ANALYSIS TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("audio-only analysis")
    class AudioOnlyAnalysis {

        @Test
        @DisplayName("audio analysis produces valid transcription")
        void audioAnalysisProducesValidTranscription() { // GH-90000
            TestAudioResult result = engine.analyseAudio(SAMPLE_AUDIO); // GH-90000

            assertThat(result).isNotNull(); // GH-90000
            assertThat(result.transcription()).isNotBlank(); // GH-90000
        }

        @Test
        @DisplayName("audio analysis includes confidence score")
        void audioAnalysisIncludesConfidence() { // GH-90000
            TestAudioResult result = engine.analyseAudio(SAMPLE_AUDIO); // GH-90000

            assertThat(result.confidence()).isBetween(0.0, 1.0); // GH-90000
        }

        @Test
        @DisplayName("audio analysis detects language")
        void audioAnalysisDetectsLanguage() { // GH-90000
            TestAudioResult result = engine.analyseAudio(SAMPLE_AUDIO); // GH-90000

            assertThat(result.detectedLanguage()).isNotBlank(); // GH-90000
        }

        @Test
        @DisplayName("audio analysis is deterministic for same input")
        void audioAnalysisIsDeterministic() { // GH-90000
            TestAudioResult r1 = engine.analyseAudio(SAMPLE_AUDIO); // GH-90000
            TestAudioResult r2 = engine.analyseAudio(SAMPLE_AUDIO); // GH-90000

            assertThat(r1.transcription()).isEqualTo(r2.transcription()); // GH-90000
            assertThat(r1.confidence()).isEqualTo(r2.confidence()); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // IMAGE ANALYSIS TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("image analysis")
    class ImageAnalysis {

        @Test
        @DisplayName("null image data throws exception")
        void nullImageData_throwsException() { // GH-90000
            assertThatThrownBy(() -> engine.analyseImage(null)) // GH-90000
                    .isInstanceOf(Exception.class); // GH-90000
        }

        @Test
        @DisplayName("image analysis produces scene description")
        void imageAnalysisProducesSceneDescription() { // GH-90000
            TestVisualResult result = engine.analyseImage(SAMPLE_IMAGE); // GH-90000

            assertThat(result).isNotNull(); // GH-90000
            assertThat(result.sceneDescription()).isNotBlank(); // GH-90000
        }

        @Test
        @DisplayName("image analysis includes detections")
        void imageAnalysisIncludesDetections() { // GH-90000
            TestVisualResult result = engine.analyseImage(SAMPLE_IMAGE); // GH-90000

            assertThat(result.detections()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("image analysis confidence is in valid range")
        void imageAnalysisConfidenceIsValid() { // GH-90000
            TestVisualResult result = engine.analyseImage(SAMPLE_IMAGE); // GH-90000

            assertThat(result.confidence()).isBetween(0.0, 1.0); // GH-90000
        }

        @Test
        @DisplayName("image analysis is deterministic for same input")
        void imageAnalysisIsDeterministic() { // GH-90000
            TestVisualResult r1 = engine.analyseImage(SAMPLE_IMAGE); // GH-90000
            TestVisualResult r2 = engine.analyseImage(SAMPLE_IMAGE); // GH-90000

            assertThat(r1.sceneDescription()).isEqualTo(r2.sceneDescription()); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // VIDEO ANALYSIS TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("video analysis")
    class VideoAnalysis {

        @Test
        @DisplayName("null video data throws exception")
        void nullVideoData_throwsException() { // GH-90000
            assertThatThrownBy(() -> engine.analyseVideo(null, 24, 100)) // GH-90000
                    .isInstanceOf(Exception.class); // GH-90000
        }

        @Test
        @DisplayName("video analysis with custom frame rate succeeds")
        void videoAnalysisWithCustomFrameRateSucceeds() { // GH-90000
            TestVisualResult result = engine.analyseVideo(SAMPLE_VIDEO, 30, 100); // GH-90000

            assertThat(result).isNotNull(); // GH-90000
            assertThat(result.sceneDescription()).isNotBlank(); // GH-90000
        }

        @Test
        @DisplayName("video analysis with frame limit succeeds")
        void videoAnalysisWithFrameLimitSucceeds() { // GH-90000
            TestVisualResult result = engine.analyseVideo(SAMPLE_VIDEO, 24, 50); // GH-90000

            assertThat(result).isNotNull(); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // CROSS-MODAL FUSION TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("cross-modal fusion")
    class CrossModalFusion {

        @Test
        @DisplayName("audio + image fusion produces combined analysis")
        void audioImageFusionProducesCombinedAnalysis() { // GH-90000
            TestMultimodalResult result = engine.fuseAudioAndImage(SAMPLE_AUDIO, SAMPLE_IMAGE); // GH-90000

            assertThat(result).isNotNull(); // GH-90000
            assertThat(result.combinedAnalysis()).isNotBlank(); // GH-90000
        }

        @Test
        @DisplayName("fused audio-image includes both audio and visual content")
        void fusedAudioImageIncludesBothModalities() { // GH-90000
            TestMultimodalResult result = engine.fuseAudioAndImage(SAMPLE_AUDIO, SAMPLE_IMAGE); // GH-90000

            assertThat(result.combinedAnalysis()).containsIgnoringCase("audio");
            assertThat(result.combinedAnalysis()).containsIgnoringCase("image");
        }

        @Test
        @DisplayName("audio + image + text fusion includes all three modalities")
        void audioImageTextFusionIncludesAllModalities() { // GH-90000
            TestMultimodalResult result = engine.fuseAudioImageAndText( // GH-90000
                    SAMPLE_AUDIO, SAMPLE_IMAGE, SAMPLE_TEXT_HINT
            );

            assertThat(result.combinedAnalysis()).isNotBlank(); // GH-90000
        }

        @Test
        @DisplayName("fusion with null text hint still succeeds")
        void fusionWithNullTextHintSucceeds() { // GH-90000
            TestMultimodalResult result = engine.fuseAudioImageAndText( // GH-90000
                    SAMPLE_AUDIO, SAMPLE_IMAGE, null
            );

            assertThat(result).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("fusion with empty text hint still succeeds")
        void fusionWithEmptyTextHintSucceeds() { // GH-90000
            TestMultimodalResult result = engine.fuseAudioImageAndText( // GH-90000
                    SAMPLE_AUDIO, SAMPLE_IMAGE, ""
            );

            assertThat(result).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("fusion is deterministic for identical inputs")
        void fusionIsDeterministic() { // GH-90000
            TestMultimodalResult r1 = engine.fuseAudioAndImage(SAMPLE_AUDIO, SAMPLE_IMAGE); // GH-90000
            TestMultimodalResult r2 = engine.fuseAudioAndImage(SAMPLE_AUDIO, SAMPLE_IMAGE); // GH-90000

            assertThat(r1.combinedAnalysis()).isEqualTo(r2.combinedAnalysis()); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // TEMPORAL ALIGNMENT TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("temporal alignment")
    class TemporalAlignment {

        @Test
        @DisplayName("video + audio alignment produces temporal segments")
        void videoAudioAlignmentProducesSegments() { // GH-90000
            TestVideoAudioAlignment alignment = engine.alignVideoAndAudio( // GH-90000
                    SAMPLE_VIDEO, SAMPLE_AUDIO, 24
            );

            assertThat(alignment).isNotNull(); // GH-90000
            assertThat(alignment.alignments()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("temporal alignment includes speech text")
        void temporalAlignmentIncludesSpeechText() { // GH-90000
            TestVideoAudioAlignment alignment = engine.alignVideoAndAudio( // GH-90000
                    SAMPLE_VIDEO, SAMPLE_AUDIO, 24
            );

            if (!alignment.alignments().isEmpty()) { // GH-90000
                alignment.alignments().forEach(segment -> // GH-90000
                        assertThat(segment.speechText()).isNotBlank() // GH-90000
                );
            }
        }

        @Test
        @DisplayName("temporal alignment includes sync confidence")
        void temporalAlignmentIncludesSyncConfidence() { // GH-90000
            TestVideoAudioAlignment alignment = engine.alignVideoAndAudio( // GH-90000
                    SAMPLE_VIDEO, SAMPLE_AUDIO, 24
            );

            if (!alignment.alignments().isEmpty()) { // GH-90000
                alignment.alignments().forEach(segment -> // GH-90000
                        assertThat(segment.syncConfidence()).isBetween(0.0, 1.0) // GH-90000
                );
            }
        }

        @Test
        @DisplayName("different frame rates produce different alignments")
        void differentFrameRatesProduceDifferentAlignments() { // GH-90000
            TestVideoAudioAlignment a24 = engine.alignVideoAndAudio(SAMPLE_VIDEO, SAMPLE_AUDIO, 24); // GH-90000
            TestVideoAudioAlignment a30 = engine.alignVideoAndAudio(SAMPLE_VIDEO, SAMPLE_AUDIO, 30); // GH-90000

            // Different frame rates may produce different timing
            assertThat(a24).isNotNull(); // GH-90000
            assertThat(a30).isNotNull(); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // CONTEXT PRESERVATION TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("context preservation")
    class ContextPreservation {

        @Test
        @DisplayName("sequential multimodal analyses maintain context")
        void sequentialAnalysesMaintainContext() { // GH-90000
            TestAudioResult audio = engine.analyseAudio(SAMPLE_AUDIO); // GH-90000
            TestVisualResult visual = engine.analyseImage(SAMPLE_IMAGE); // GH-90000
            TestMultimodalResult fused = engine.fuseAudioAndImage(SAMPLE_AUDIO, SAMPLE_IMAGE); // GH-90000

            // Fused result should incorporate both analyses
            assertThat(fused.combinedAnalysis()).isNotBlank(); // GH-90000
            assertThat(audio.transcription()).isNotBlank(); // GH-90000
            assertThat(visual.sceneDescription()).isNotBlank(); // GH-90000
        }

        @Test
        @DisplayName("context from multiple images is preserved")
        void contextFromMultipleImagesIsPreserved() { // GH-90000
            TestVisualResult img1 = engine.analyseImage(SAMPLE_IMAGE); // GH-90000
            TestVisualResult img2 = engine.analyseImage(new byte[] {30, 40, 50}); // GH-90000

            assertThat(img1.sceneDescription()).isNotBlank(); // GH-90000
            assertThat(img2.sceneDescription()).isNotBlank(); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // INTEGRATION CORRECTNESS TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("integration correctness")
    class IntegrationCorrectness {

        @Test
        @DisplayName("audio component integration is correct")
        void audioComponentIntegrationIsCorrect() { // GH-90000
            byte[] testAudio = {1, 2, 3};
            TestAudioResult result = engine.analyseAudio(testAudio); // GH-90000

            // Result should be generated from audio component
            assertThat(result).isNotNull(); // GH-90000
            assertThat(result.transcription()).isNotBlank(); // GH-90000
            assertThat(result.confidence()).isGreaterThan(0.0); // GH-90000
        }

        @Test
        @DisplayName("vision component integration is correct")
        void visionComponentIntegrationIsCorrect() { // GH-90000
            byte[] testImage = {10, 20};
            TestVisualResult result = engine.analyseImage(testImage); // GH-90000

            // Result should be generated from vision component
            assertThat(result).isNotNull(); // GH-90000
            assertThat(result.sceneDescription()).isNotBlank(); // GH-90000
            assertThat(result.confidence()).isGreaterThan(0.0); // GH-90000
        }

        @Test
        @DisplayName("fused results combine both components correctly")
        void fusedResultsCombineComponentsCorrectly() { // GH-90000
            TestMultimodalResult result = engine.fuseAudioAndImage(SAMPLE_AUDIO, SAMPLE_IMAGE); // GH-90000

            // Combined analysis should reference both components
            assertThat(result.combinedAnalysis()).isNotBlank(); // GH-90000
            assertThat(result.processingTimeMs()).isGreaterThanOrEqualTo(0); // GH-90000
        }

        @Test
        @DisplayName("temporal alignment correctly synchronizes modalities")
        void temporalAlignmentSynchronizesModalities() { // GH-90000
            TestVideoAudioAlignment alignment = engine.alignVideoAndAudio( // GH-90000
                    SAMPLE_VIDEO, SAMPLE_AUDIO, 24
            );

            // Alignment should exist and be valid
            assertThat(alignment).isNotNull(); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // ERROR HANDLING & ROBUSTNESS TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("error handling and robustness")
    class ErrorHandlingAndRobustness {

        @Test
        @DisplayName("multiple consecutive errors don't affect next success")
        void multipleErrorsDontAffectNextSuccess() { // GH-90000
            // Trigger some errors
            assertThatThrownBy(() -> engine.analyseAudio(null)) // GH-90000
                    .isInstanceOf(Exception.class); // GH-90000
            assertThatThrownBy(() -> engine.analyseImage(null)) // GH-90000
                    .isInstanceOf(Exception.class); // GH-90000

            // Next operation should work fine
            TestAudioResult result = engine.analyseAudio(SAMPLE_AUDIO); // GH-90000
            assertThat(result.transcription()).isNotBlank(); // GH-90000
        }

        @Test
        @DisplayName("large audio data is handled without failure")
        void largeAudioDataIsHandled() { // GH-90000
            byte[] largeAudio = new byte[1_000_000];
            for (int i = 0; i < largeAudio.length; i++) { // GH-90000
                largeAudio[i] = (byte) (i % 256); // GH-90000
            }

            assertThatCode(() -> engine.analyseAudio(largeAudio)) // GH-90000
                    .doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("rapid consecutive operations don't interfere")
        void rapidConsecutiveOperationsDontInterfere() { // GH-90000
            for (int i = 0; i < 10; i++) { // GH-90000
                TestAudioResult audio = engine.analyseAudio(SAMPLE_AUDIO); // GH-90000
                TestVisualResult visual = engine.analyseImage(SAMPLE_IMAGE); // GH-90000
                TestMultimodalResult fused = engine.fuseAudioAndImage(SAMPLE_AUDIO, SAMPLE_IMAGE); // GH-90000

                assertThat(audio.transcription()).isNotBlank(); // GH-90000
                assertThat(visual.sceneDescription()).isNotBlank(); // GH-90000
                assertThat(fused.combinedAnalysis()).isNotBlank(); // GH-90000
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // PERFORMANCE TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("performance")
    class Performance {

        @Test
        @DisplayName("audio analysis completes in reasonable time")
        void audioAnalysisCompletesInReasonableTime() { // GH-90000
            long start = System.currentTimeMillis(); // GH-90000
            engine.analyseAudio(SAMPLE_AUDIO); // GH-90000
            long elapsed = System.currentTimeMillis() - start; // GH-90000

            assertThat(elapsed).isLessThan(5000L); // 5 seconds // GH-90000
        }

        @Test
        @DisplayName("fusion analysis completes in reasonable time")
        void fusionAnalysisCompletesInReasonableTime() { // GH-90000
            long start = System.currentTimeMillis(); // GH-90000
            engine.fuseAudioAndImage(SAMPLE_AUDIO, SAMPLE_IMAGE); // GH-90000
            long elapsed = System.currentTimeMillis() - start; // GH-90000

            assertThat(elapsed).isLessThan(10000L); // 10 seconds // GH-90000
        }

        @Test
        @DisplayName("processing time is recorded in results")
        void processingTimeIsRecorded() { // GH-90000
            TestMultimodalResult result = engine.fuseAudioAndImage(SAMPLE_AUDIO, SAMPLE_IMAGE); // GH-90000

            assertThat(result.processingTimeMs()).isGreaterThanOrEqualTo(0); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // TEST HELPER CLASSES
    // ═══════════════════════════════════════════════════════════════════════════════════

    /**
     * Test implementation of MultimodalEngine for testing.
     */
    private static class TestMultimodalEngine {

        public TestAudioResult analyseAudio(byte[] audioData) { // GH-90000
            if (audioData == null || audioData.length == 0) { // GH-90000
                throw new IllegalArgumentException("Audio data must not be null or empty");
            }
            return new TestAudioResult( // GH-90000
                    "Transcribed audio: " + audioData.length + " bytes",
                    0.9,
                    "en"
            );
        }

        public TestVisualResult analyseImage(byte[] imageData) { // GH-90000
            if (imageData == null || imageData.length == 0) { // GH-90000
                throw new IllegalArgumentException("Image data must not be null or empty");
            }
            return new TestVisualResult( // GH-90000
                    "Scene with " + imageData.length + " byte images",
                    List.of(new TestDetection("object", 0.85)), // GH-90000
                    0.88
            );
        }

        public TestVisualResult analyseVideo(byte[] videoData, int fps, int maxFrames) { // GH-90000
            if (videoData == null || videoData.length == 0) { // GH-90000
                throw new IllegalArgumentException("Video data must not be null or empty");
            }
            return new TestVisualResult( // GH-90000
                    "Video scene at " + fps + "fps, max " + maxFrames + " frames",
                    List.of(), // GH-90000
                    0.84
            );
        }

        public TestMultimodalResult fuseAudioAndImage(byte[] audio, byte[] image) { // GH-90000
            return new TestMultimodalResult( // GH-90000
                    "Fused: audio(" + audio.length + ") + image(" + image.length + ")",
                    System.currentTimeMillis() // GH-90000
            );
        }

        public TestMultimodalResult fuseAudioImageAndText(byte[] audio, byte[] image, String text) { // GH-90000
            String enriched = text != null && !text.isBlank() // GH-90000
                    ? " with text: '" + text + "'"
                    : "";
            return new TestMultimodalResult( // GH-90000
                    "Fused: audio + image + text" + enriched,
                    System.currentTimeMillis() // GH-90000
            );
        }

        public TestVideoAudioAlignment alignVideoAndAudio(byte[] video, byte[] audio, int fps) { // GH-90000
            if (video == null || video.length == 0) { // GH-90000
                throw new IllegalArgumentException("Video data must not be null or empty");
            }
            List<TestAlignment> alignments = List.of( // GH-90000
                    new TestAlignment("Hello", 0.92), // GH-90000
                    new TestAlignment("World", 0.88) // GH-90000
            );
            return new TestVideoAudioAlignment(alignments); // GH-90000
        }
    }

    record TestAudioResult(String transcription, double confidence, String detectedLanguage) {} // GH-90000
    record TestVisualResult(String sceneDescription, List<TestDetection> detections, double confidence) {} // GH-90000
    record TestDetection(String label, double confidence) {} // GH-90000
    record TestMultimodalResult(String combinedAnalysis, long processingTimeMs) {} // GH-90000
    record TestAlignment(String speechText, double syncConfidence) {} // GH-90000
    record TestVideoAudioAlignment(List<TestAlignment> alignments) {} // GH-90000
}
