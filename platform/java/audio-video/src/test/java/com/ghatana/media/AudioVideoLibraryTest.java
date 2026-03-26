/**
 * Unit tests for the Audio-Video Library.
 */
package com.ghatana.media;

import com.ghatana.media.common.*;
import com.ghatana.media.config.*;
import com.ghatana.media.stt.api.*;
import com.ghatana.media.tts.api.*;
import com.ghatana.media.vision.api.*;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Comprehensive tests for the AudioVideoLibrary.
 */
public class AudioVideoLibraryTest {

    private AudioVideoLibrary library;

    @BeforeEach
    void setUp() {
        library = AudioVideoLibrary.builder()
            .withSttConfig(SttConfig.builder()
                .modelPath(Paths.get("/models/whisper-base.onnx"))
                .modelId("whisper-base")
                .build())
            .withTtsConfig(TtsConfig.builder()
                .voiceModelPath(Paths.get("/models/piper-en.onnx"))
                .defaultVoiceId("piper-en")
                .build())
            .withVisionConfig(VisionConfig.builder()
                .modelPath(Paths.get("/models/yolov8n.onnx"))
                .modelId("yolov8n")
                .build())
            .build();
    }

    @AfterEach
    void tearDown() {
        if (library != null) {
            library.close();
        }
    }

    @Test
    void testLibraryBuilderRequiresAtLeastOneEngine() {
        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
            AudioVideoLibrary.builder().build()
        );
        assertTrue(exception.getMessage().contains("At least one engine"));
    }

    @Test
    void testLibraryReportsEnabledEngines() {
        assertTrue(library.isSttEnabled());
        assertTrue(library.isTtsEnabled());
        assertTrue(library.isVisionEnabled());
    }

    @Test
    void testGetSttEngineReturnsSameInstance() {
        SttEngine engine1 = library.getSttEngine();
        SttEngine engine2 = library.getSttEngine();
        assertSame(engine1, engine2, "Engine should be cached");
    }

    @Test
    void testGetTtsEngineReturnsSameInstance() {
        TtsEngine engine1 = library.getTtsEngine();
        TtsEngine engine2 = library.getTtsEngine();
        assertSame(engine1, engine2, "Engine should be cached");
    }

    @Test
    void testGetVisionEngineReturnsSameInstance() {
        VisionEngine engine1 = library.getVisionEngine();
        VisionEngine engine2 = library.getVisionEngine();
        assertSame(engine1, engine2, "Engine should be cached");
    }

    @Test
    void testLibraryStatusHealthyAfterInitialization() {
        AudioVideoLibrary.LibraryStatus status = library.getStatus();
        assertTrue(status.healthy());
    }

    @Test
    void testEngineStatusReporting() {
        // Warm up engines
        library.getSttEngine().warmup();
        library.getTtsEngine().warmup();
        library.getVisionEngine().warmup();

        AudioVideoLibrary.LibraryStatus status = library.getStatus();
        assertNotNull(status.sttStatus());
        assertNotNull(status.ttsStatus());
        assertNotNull(status.visionStatus());
    }

    @Test
    void testLibraryCloseShutsDownAllEngines() {
        // Get engines
        SttEngine stt = library.getSttEngine();
        TtsEngine tts = library.getTtsEngine();
        VisionEngine vision = library.getVisionEngine();

        // Close library
        library.close();

        // All engines should report closed
        assertEquals(EngineStatus.State.CLOSED, stt.getStatus().state());
        assertEquals(EngineStatus.State.CLOSED, tts.getStatus().state());
        assertEquals(EngineStatus.State.CLOSED, vision.getStatus().state());
    }

    @Test
    void testLibraryCannotBeUsedAfterClose() {
        library.close();
        assertThrows(IllegalStateException.class, () -> library.getSttEngine());
    }

    @Test
    void testAsyncInitialization() throws Exception {
        var future = library.initializeAsync().toCompletableFuture();
        AudioVideoLibrary.LibraryStatus status = future.get();
        assertNotNull(status);
    }
}

/**
 * Tests for STT Engine functionality.
 */
class SttEngineTest {

    private AudioVideoLibrary library;
    private SttEngine engine;

    @BeforeEach
    void setUp() {
        library = AudioVideoLibrary.builder()
            .withSttConfig(SttConfig.builder()
                .modelId("test-model")
                .maxConcurrentRequests(5)
                .maxAudioLengthSeconds(300)
                .build())
            .build();
        engine = library.getSttEngine();
    }

    @AfterEach
    void tearDown() {
        library.close();
    }

    @Test
    void testTranscribeWithNullAudioThrowsValidationError() {
        assertThrows(ValidationError.class, () ->
            engine.transcribe(null, TranscriptionOptions.defaults())
        );
    }

    @Test
    void testTranscribeReturnsResult() {
        AudioData audio = AudioData.builder()
            .data(new byte[16000 * 2]) // 1 second at 16kHz
            .sampleRate(16000)
            .channels(1)
            .bitsPerSample(16)
            .build();

        TranscriptionResult result = engine.transcribe(audio);

        assertNotNull(result);
        assertNotNull(result.getText());
        assertTrue(result.confidence() >= 0 && result.confidence() <= 1);
    }

    @Test
    void testTranscribeWithOptions() {
        AudioData audio = AudioData.builder()
            .data(new byte[16000 * 2])
            .sampleRate(16000)
            .channels(1)
            .bitsPerSample(16)
            .build();

        TranscriptionOptions options = TranscriptionOptions.builder()
            .language(Locale.ENGLISH)
            .enableTimestamps(true)
            .maxAlternatives(3)
            .build();

        TranscriptionResult result = engine.transcribe(audio, options);

        assertNotNull(result);
        assertNotNull(result.language());
    }

    @Test
    void testStreamingSession() {
        AtomicReference<StreamingTranscription> received = new AtomicReference<>();

        StreamingSession session = engine.createStreamingSession();
        session.onTranscription(received::set);

        // Feed audio chunks
        for (int i = 0; i < 10; i++) {
            session.feedAudio(new AudioChunk(
                new byte[1600], // 100ms chunk
                i,
                i == 9,
                System.currentTimeMillis()
            ));
        }

        session.endStream();

        // Should have received transcriptions
        assertNotNull(received.get());
    }

    @Test
    void testProfileManagement() {
        UserProfile profile = engine.createProfile("test-profile", List.of());

        assertNotNull(profile);
        assertEquals("test-profile", profile.profileId());

        // Save and load
        engine.saveProfile(profile);
        var loaded = engine.loadProfile("test-profile");
        assertTrue(loaded.isPresent());
    }

    @Test
    void testGetAvailableModels() {
        List<ModelInfo> models = engine.getAvailableModels();
        assertFalse(models.isEmpty());
    }

    @Test
    void testWarmup() {
        assertDoesNotThrow(() -> engine.warmup());
    }

    @Test
    void testMetricsAfterOperations() {
        // Perform operations
        AudioData audio = AudioData.builder()
            .data(new byte[16000 * 2])
            .sampleRate(16000)
            .channels(1)
            .bitsPerSample(16)
            .build();

        engine.transcribe(audio);

        EngineMetrics metrics = engine.getMetrics();
        assertTrue(metrics.requestCount() > 0);
    }
}

/**
 * Tests for TTS Engine functionality.
 */
class TtsEngineTest {

    private AudioVideoLibrary library;
    private TtsEngine engine;

    @BeforeEach
    void setUp() {
        library = AudioVideoLibrary.builder()
            .withTtsConfig(TtsConfig.builder()
                .defaultVoiceId("test-voice")
                .sampleRate(22050)
                .maxTextLength(5000)
                .enableProsody(true)
                .build())
            .build();
        engine = library.getTtsEngine();
    }

    @AfterEach
    void tearDown() {
        library.close();
    }

    @Test
    void testSynthesizeWithNullTextThrowsValidationError() {
        assertThrows(ValidationError.class, () ->
            engine.synthesize(null, SynthesisOptions.defaults())
        );
    }

    @Test
    void testSynthesizeWithEmptyTextThrowsValidationError() {
        assertThrows(ValidationError.class, () ->
            engine.synthesize("", SynthesisOptions.defaults())
        );
    }

    @Test
    void testSynthesizeReturnsAudio() {
        AudioData audio = engine.synthesize("Hello, world!");

        assertNotNull(audio);
        assertTrue(audio.data().length > 0);
        assertEquals(22050, audio.sampleRate());
    }

    @Test
    void testSynthesizeWithProsodyOptions() {
        SynthesisOptions options = SynthesisOptions.builder()
            .speed(0.9)
            .pitch(1.1)
            .volume(1.2)
            .emotion(Emotion.PROFESSIONAL)
            .build();

        AudioData audio = engine.synthesize("Test with prosody", options);

        assertNotNull(audio);
    }

    @Test
    void testStreamingSynthesis() {
        List<AudioChunk> chunks = new java.util.ArrayList<>();

        engine.synthesizeStreaming(
            "Hello for streaming",
            SynthesisOptions.defaults(),
            chunks::add
        );

        assertFalse(chunks.isEmpty());
        assertTrue(chunks.get(chunks.size() - 1).isLast());
    }

    @Test
    void testGetAvailableVoices() {
        List<VoiceInfo> voices = engine.getAvailableVoices();
        assertFalse(voices.isEmpty());
    }

    @Test
    void testVoiceSwitching() {
        VoiceInfo voice1 = engine.getActiveVoice();
        engine.setActiveVoice("new-voice");
        VoiceInfo voice2 = engine.getActiveVoice();

        assertEquals("new-voice", voice2.voiceId());
    }

    @Test
    void testProfileManagement() {
        TtsProfile profile = engine.createProfile("tts-profile", "Test User", ProfileSettings.builder().build());

        assertNotNull(profile);
        assertEquals("tts-profile", profile.profileId());

        engine.saveProfile(profile);
        var loaded = engine.loadProfile("tts-profile");
        assertTrue(loaded.isPresent());
    }

    @Test
    void testVoiceCloningRequiresEnabledConfig() {
        // Voice cloning is disabled by default
        assertThrows(UnsupportedOperationException.class, () ->
            engine.cloneVoice("test-voice", List.of(), CloneOptions.defaults())
        );
    }
}

/**
 * Tests for Vision Engine functionality.
 */
class VisionEngineTest {

    private AudioVideoLibrary library;
    private VisionEngine engine;

    @BeforeEach
    void setUp() {
        library = AudioVideoLibrary.builder()
            .withVisionConfig(VisionConfig.builder()
                .modelId("yolov8n")
                .defaultConfidenceThreshold(0.5)
                .defaultMaxDetections(100)
                .inputSize(640)
                .build())
            .build();
        engine = library.getVisionEngine();
    }

    @AfterEach
    void tearDown() {
        library.close();
    }

    @Test
    void testDetectWithNullImageThrowsValidationError() {
        assertThrows(ValidationError.class, () ->
            engine.detect(null, DetectionOptions.defaults())
        );
    }

    @Test
    void testInvalidImageValueObjectRejectsZeroSize() {
        assertThrows(IllegalArgumentException.class, () -> ImageData.builder()
            .data(new byte[0])
            .width(0)
            .height(0)
            .format(ImageFormat.RAW)
            .build());
    }

    @Test
    void testDetectReturnsResult() {
        ImageData image = ImageData.builder()
            .data(new byte[640 * 480 * 3])
            .width(640)
            .height(480)
            .format(ImageFormat.RAW)
            .build();

        DetectionResult result = engine.detect(image);

        assertNotNull(result);
        assertNotNull(result.objects());
    }

    @Test
    void testDetectWithOptions() {
        ImageData image = ImageData.builder()
            .data(new byte[640 * 480 * 3])
            .width(640)
            .height(480)
            .format(ImageFormat.RAW)
            .build();

        DetectionOptions options = DetectionOptions.builder()
            .confidenceThreshold(0.7)
            .maxDetections(10)
            .classFilter(List.of("person", "car"))
            .build();

        DetectionResult result = engine.detect(image, options);
        assertNotNull(result);
    }

    @Test
    void testClassification() {
        ImageData image = ImageData.builder()
            .data(new byte[640 * 480 * 3])
            .width(640)
            .height(480)
            .format(ImageFormat.RAW)
            .build();

        List<Classification> classifications = engine.classify(image, 5);
        assertNotNull(classifications);
    }

    @Test
    void testCaptionGeneration() {
        ImageData image = ImageData.builder()
            .data(new byte[640 * 480 * 3])
            .width(640)
            .height(480)
            .format(ImageFormat.RAW)
            .build();

        String caption = engine.caption(image);
        assertNotNull(caption);
        assertFalse(caption.isEmpty());
    }

    @Test
    void testGetAvailableModels() {
        List<DetectionModelInfo> models = engine.getAvailableModels();
        assertFalse(models.isEmpty());
    }

    @Test
    void testBoundingBoxOperations() {
        BoundingBox box1 = new BoundingBox(10, 10, 100, 100, 0.9);
        BoundingBox box2 = new BoundingBox(50, 50, 100, 100, 0.8);

        assertTrue(box1.intersects(box2));
        assertTrue(box1.iou(box2) > 0);

        assertEquals(60, box1.centerX());
        assertEquals(60, box1.centerY());
        assertEquals(10000, box1.area());
    }
}
