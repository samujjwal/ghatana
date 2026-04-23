package com.ghatana.audio.video.integration;

import com.ghatana.stt.diarization.SpeakerDiarizationService;
import com.ghatana.stt.detection.LanguageDetectionService;
import com.ghatana.stt.vocabulary.CustomVocabularyManager;
import com.ghatana.tts.ssml.SsmlProcessor;
import com.ghatana.audio.video.vision.ocr.OcrService;
import com.ghatana.audio.video.vision.recognition.FacialRecognitionService;
import com.ghatana.audio.video.vision.scene.SceneUnderstandingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cross-service integration tests for the Audio-Video module (AV-012). // GH-90000
 *
 * <p>Verifies that the advanced STT, TTS, and Vision services interact correctly
 * when composed in a multi-step media analysis workflow without requiring the
 * underlying native AI libraries (all models are stubbed). // GH-90000
 *
 * @doc.type class
 * @doc.purpose Cross-service integration tests for advanced audio-video features
 * @doc.layer product
 * @doc.pattern Test
 */
@Tag("integration")
@DisplayName("Audio-Video Service Integration Tests — AV-012")
class AudioVideoAdvancedServicesIntegrationTest {

    // ─── STT advanced feature integration ────────────────────────────────────

    @Nested
    @DisplayName("STT Advanced Features Integration (AV-007)")
    class SttAdvancedIntegration {

        @Test
        @DisplayName("diarizer → language detector → vocabulary manager pipeline works end-to-end")
        void sttPipeline_diarizationToVocabulary_endToEnd() { // GH-90000
            // Step 1: Diarize
            SpeakerDiarizationService diarizer = SpeakerDiarizationService.builder() // GH-90000
                    .embeddingExtractor(bytes -> new float[]{1.0f, 0.0f}) // GH-90000
                    .maxSpeakers(3) // GH-90000
                    .similarityThreshold(0.80) // GH-90000
                    .build(); // GH-90000

            var segments = List.of( // GH-90000
                    new SpeakerDiarizationService.AudioSegment(new byte[]{1, 2}, 0, 500, "Ghatana is awesome"), // GH-90000
                    new SpeakerDiarizationService.AudioSegment(new byte[]{1, 2}, 500, 1000, "ActiveJ rocks") // GH-90000
            );
            List<SpeakerDiarizationService.SpeakerTurn> turns = diarizer.diarize(segments); // GH-90000
            assertThat(turns).hasSize(2); // GH-90000
            assertThat(turns.get(0).speakerId()).isNotNull(); // GH-90000

            // Step 2: Language detection
            LanguageDetectionService langDetector = LanguageDetectionService.of( // GH-90000
                    textSample -> List.of(LanguageDetectionService.LanguageCandidate.of("en", 0.98))); // GH-90000
            LanguageDetectionService.DetectionResult langResult = langDetector.detect(turns.get(0).text()); // GH-90000
            assertThat(langResult.topLanguageTag()).contains("en");

            // Step 3: Vocabulary enhancement
            CustomVocabularyManager vocab = CustomVocabularyManager.of(List.of("Ghatana", "ActiveJ")); // GH-90000
            String enhanced = vocab.apply(turns.get(0).text()); // GH-90000
            assertThat(enhanced).isEqualTo("Ghatana is awesome");
            assertThat(vocab.contains("ghatana")).isTrue();
        }

        @Test
        @DisplayName("multiple speakers with distinct audio signatures are correctly identified")
        void sttIntegration_multipleSpeakers_isolatedLabels() { // GH-90000
            SpeakerDiarizationService diarizer = SpeakerDiarizationService.builder() // GH-90000
                    .embeddingExtractor(bytes -> { // GH-90000
                        float[] emb = new float[8];
                        emb[bytes.length % 8] = 1.0f;
                        return emb;
                    })
                    .maxSpeakers(5) // GH-90000
                    .similarityThreshold(0.85) // GH-90000
                    .build(); // GH-90000

            var segments = List.of( // GH-90000
                    new SpeakerDiarizationService.AudioSegment(new byte[3], 0, 1000, "Hello"), // GH-90000
                    new SpeakerDiarizationService.AudioSegment(new byte[4], 1000, 2000, "Hi"), // GH-90000
                    new SpeakerDiarizationService.AudioSegment(new byte[3], 2000, 3000, "Back") // GH-90000
            );

            List<SpeakerDiarizationService.SpeakerTurn> turns = diarizer.diarize(segments); // GH-90000

            assertThat(turns.get(0).speakerId()) // GH-90000
                    .isEqualTo(turns.get(2).speakerId()); // same speaker // GH-90000
            assertThat(turns.get(0).speakerId()) // GH-90000
                    .isNotEqualTo(turns.get(1).speakerId()); // different speaker // GH-90000
        }
    }

    // ─── TTS SSML integration ─────────────────────────────────────────────────

    @Nested
    @DisplayName("TTS SSML Integration (AV-008)")
    class TtsAdvancedIntegration {

        @Test
        @DisplayName("complex SSML document is correctly parsed into ordered segments")
        void ssmlIntegration_complexDocument_correctSegments() { // GH-90000
            SsmlProcessor processor = SsmlProcessor.create(); // GH-90000
            String ssml = "<speak>"
                    + "Welcome to Ghatana."
                    + "<break time=\"500ms\"/>"
                    + "<prosody rate=\"80%\">Processing your request.</prosody>"
                    + "<break time=\"200ms\"/>"
                    + "<emphasis level=\"strong\">Done!</emphasis>"
                    + "</speak>";

            List<SsmlProcessor.SsmlSegment> segments = processor.parse(ssml); // GH-90000

            assertThat(segments).hasSizeGreaterThanOrEqualTo(4); // GH-90000
            assertThat(segments.stream() // GH-90000
                    .filter(s -> s.type() == SsmlProcessor.SsmlSegment.SegmentType.PAUSE) // GH-90000
                    .count()).isEqualTo(2); // GH-90000
            assertThat(segments.stream() // GH-90000
                    .filter(s -> s.type() == SsmlProcessor.SsmlSegment.SegmentType.PROSODY) // GH-90000
                    .count()).isEqualTo(1); // GH-90000
            assertThat(segments.stream() // GH-90000
                    .filter(s -> s.type() == SsmlProcessor.SsmlSegment.SegmentType.EMPHASIS) // GH-90000
                    .count()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("toPlainText strips all markup for fallback text-only rendering")
        void ssmlIntegration_plainText_fallback() { // GH-90000
            SsmlProcessor processor = SsmlProcessor.create(); // GH-90000
            String ssml = "<speak>Hello <emphasis level=\"strong\">World</emphasis></speak>";
            assertThat(processor.toPlainText(ssml)).isEqualTo("Hello World");
        }
    }

    // ─── Vision advanced features integration ────────────────────────────────

    @Nested
    @DisplayName("Vision Advanced Features Integration (AV-009)")
    class VisionAdvancedIntegration {

        @Test
        @DisplayName("facial recognition and scene understanding run on same image bytes")
        void visionIntegration_faceAndScene_parallel() { // GH-90000
            byte[] imageBytes = new byte[512]; // dummy image

            FacialRecognitionService faceService = FacialRecognitionService.of( // GH-90000
                    bytes -> List.of(FacialRecognitionService.FaceDetection.of( // GH-90000
                            new FacialRecognitionService.BoundingBox(0.1, 0.1, 0.3, 0.3), // GH-90000
                            0.97, new float[]{0.9f, 0.1f})));

            SceneUnderstandingService sceneService = SceneUnderstandingService.of( // GH-90000
                    bytes -> List.of(new SceneUnderstandingService.SceneLabel("office/meeting-room", 0.91))); // GH-90000

            var faces = faceService.detect(imageBytes); // GH-90000
            var scene = sceneService.classify(imageBytes); // GH-90000

            assertThat(faces).hasSize(1); // GH-90000
            assertThat(faces.get(0).confidence()).isGreaterThan(0.90); // GH-90000
            assertThat(scene.hasTopScene()).isTrue(); // GH-90000
            assertThat(scene.topSceneLabel()).isEqualTo("office/meeting-room");
        }

        @Test
        @DisplayName("OCR and facial recognition on same image return independent results")
        void visionIntegration_ocrAndFace_independent() { // GH-90000
            byte[] imageBytes = new byte[256];

            OcrService ocrService = OcrService.of(bytes -> List.of( // GH-90000
                    new OcrService.TextRegion("Meeting Agenda", new OcrService.BoundingBox(0, 0, 1, 0.1), 0.96, 0))); // GH-90000

            FacialRecognitionService faceService = FacialRecognitionService.of( // GH-90000
                    bytes -> List.of(FacialRecognitionService.FaceDetection.of( // GH-90000
                            new FacialRecognitionService.BoundingBox(0, 0.1, 0.3, 0.3), // GH-90000
                            0.95, new float[]{0.8f})));

            assertThat(ocrService.extractText(imageBytes)).isEqualTo("Meeting Agenda");
            assertThat(faceService.detect(imageBytes)).hasSize(1); // GH-90000
        }

        @Test
        @DisplayName("identity identification with enrolled embeddings works for known person")
        void visionIntegration_identityRecognition_knownPerson() { // GH-90000
            float[] enrolledEmbedding = new float[]{0.7f, 0.7f, 0.0f};

            FacialRecognitionService service = FacialRecognitionService.of( // GH-90000
                    bytes -> List.of(FacialRecognitionService.FaceDetection.of( // GH-90000
                            new FacialRecognitionService.BoundingBox(0, 0, 1, 1), // GH-90000
                            0.99, enrolledEmbedding)));

            var faces = service.detect(new byte[]{1}); // GH-90000
            assertThat(faces).hasSize(1); // GH-90000

            var match = service.identify( // GH-90000
                    faces.get(0).embedding(), // GH-90000
                    Map.of("alice", enrolledEmbedding)); // GH-90000
            assertThat(match).isPresent(); // GH-90000
            assertThat(match.get().identityId()).isEqualTo("alice");
            assertThat(match.get().similarity()).isGreaterThan(0.99); // GH-90000
        }
    }
}

