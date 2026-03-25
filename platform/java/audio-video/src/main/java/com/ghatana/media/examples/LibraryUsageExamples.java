/**
 * Audio-Video Library Usage Examples
 *
 * <p>This file demonstrates how to use the audio-video library
 * in embedded mode within Java applications.
 *
 * @doc.type examples
 * @doc.purpose Library usage demonstrations
 */
package com.ghatana.media.examples;

import com.ghatana.media.AudioVideoLibrary;
import com.ghatana.media.common.*;
import com.ghatana.media.config.*;
import com.ghatana.media.stt.api.*;
import com.ghatana.media.tts.api.*;
import com.ghatana.media.vision.api.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * Examples of using the Audio-Video Library in embedded mode.
 */
public final class LibraryUsageExamples {

    private LibraryUsageExamples() {}

    // ====================================================================================
    // Example 1: Basic STT Usage
    // ====================================================================================

    /**
     * Example: Basic speech-to-text transcription.
     */
    public static void basicSttExample() {
        // Create library with STT configuration
        AudioVideoLibrary library = AudioVideoLibrary.builder()
            .withSttConfig(SttConfig.builder()
                .modelPath(Paths.get("/models/whisper-base.onnx"))
                .modelId("whisper-base")
                .useGpu(false)
                .maxAudioLengthSeconds(300)
                .build())
            .build();

        // Use the STT engine
        try (SttEngine stt = library.getSttEngine()) {
            // Warm up for lower latency on first request
            stt.warmup();

            // Create audio data (normally from microphone or file)
            AudioData audio = createAudioDataFromFile("/path/to/audio.wav");

            // Transcribe with default options
            TranscriptionResult result = stt.transcribe(audio);
            System.out.println("Transcription: " + result.getText());
            System.out.println("Confidence: " + result.confidence());

            // Transcribe with custom options
            TranscriptionResult result2 = stt.transcribe(audio, TranscriptionOptions.builder()
                .language(Locale.ENGLISH)
                .enableTimestamps(true)
                .maxAlternatives(3)
                .build());

            // Print word-level timestamps
            for (WordTiming word : result2.words()) {
                System.out.printf("%s: %.2f - %.2f%n",
                    word.word(), word.startSec(), word.endSec());
            }
        }

        // Library auto-closes when done
        library.close();
    }

    // ====================================================================================
    // Example 2: Streaming STT Usage
    // ====================================================================================

    /**
     * Example: Real-time streaming transcription.
     */
    public static void streamingSttExample() {
        AudioVideoLibrary library = AudioVideoLibrary.builder()
            .withSttConfig(SttConfig.builder()
                .modelPath(Paths.get("/models/whisper-tiny.onnx"))
                .modelId("whisper-tiny") // Use tiny for lower latency
                .build())
            .build();

        try (SttEngine stt = library.getSttEngine()) {
            // Create streaming session
            StreamingSession session = stt.createStreamingSession();

            // Set up result handler
            session.onTranscription(transcription -> {
                if (transcription.isFinal()) {
                    System.out.println("Final: " + transcription.text());
                } else {
                    System.out.println("Partial: " + transcription.text());
                }
            });

            session.onError(error -> {
                System.err.println("Streaming error: " + error.getMessage());
            });

            // Simulate feeding audio chunks from microphone
            for (int i = 0; i < 100; i++) {
                AudioChunk chunk = captureAudioFromMicrophone(i);
                session.feedAudio(chunk);
            }

            // Signal end of stream
            session.endStream();
        }

        library.close();
    }

    // ====================================================================================
    // Example 3: TTS Usage
    // ====================================================================================

    /**
     * Example: Text-to-speech synthesis.
     */
    public static void ttsExample() {
        AudioVideoLibrary library = AudioVideoLibrary.builder()
            .withTtsConfig(TtsConfig.builder()
                .voiceModelPath(Paths.get("/models/piper-en.onnx"))
                .defaultVoiceId("piper-en")
                .sampleRate(22050)
                .enableProsody(true)
                .build())
            .build();

        try (TtsEngine tts = library.getTtsEngine()) {
            // Simple synthesis
            AudioData audio = tts.synthesize("Hello, world!");
            playAudio(audio);

            // Synthesis with prosody control
            AudioData audio2 = tts.synthesize(
                "This is an important announcement.",
                SynthesisOptions.builder()
                    .speed(0.9)      // Slightly slower
                    .pitch(1.1)      // Slightly higher pitch
                    .volume(1.2)     // Louder
                    .emotion(Emotion.PROFESSIONAL)
                    .build()
            );
            playAudio(audio2);

            // Streaming synthesis (lower latency)
            tts.synthesizeStreaming(
                "This is a long text that will be synthesized in chunks...",
                SynthesisOptions.defaults(),
                chunk -> {
                    // Play each chunk as it arrives
                    playAudioChunk(chunk);
                }
            );
        }

        library.close();
    }

    // ====================================================================================
    // Example 4: Vision Usage
    // ====================================================================================

    /**
     * Example: Object detection with vision engine.
     */
    public static void visionExample() {
        AudioVideoLibrary library = AudioVideoLibrary.builder()
            .withVisionConfig(VisionConfig.builder()
                .modelPath(Paths.get("/models/yolov8n.onnx"))
                .modelId("yolov8n")
                .useGpu(true)
                .defaultConfidenceThreshold(0.5)
                .build())
            .build();

        try (VisionEngine vision = library.getVisionEngine()) {
            // Load image
            ImageData image = loadImageFromFile("/path/to/image.jpg");

            // Detect objects
            DetectionResult result = vision.detect(image);

            System.out.println("Detected " + result.count() + " objects:");
            for (DetectedObject obj : result.objects()) {
                System.out.printf("  - %s (%.2f%%) at [%.0f, %.0f, %.0f, %.0f]%n",
                    obj.className(),
                    obj.confidence() * 100,
                    obj.bbox().x(),
                    obj.bbox().y(),
                    obj.bbox().width(),
                    obj.bbox().height());
            }

            // Detect with custom options
            DetectionResult result2 = vision.detect(image, DetectionOptions.builder()
                .confidenceThreshold(0.7)  // Only high-confidence detections
                .maxDetections(5)          // Limit to top 5
                .classFilter(List.of("person", "car"))  // Only detect people and cars
                .build());

            // Image classification
            List<Classification> classifications = vision.classify(image, 3);
            System.out.println("Top classifications:");
            for (Classification c : classifications) {
                System.out.printf("  - %s: %.2f%%%n", c.className(), c.confidence() * 100);
            }
        }

        library.close();
    }

    // ====================================================================================
    // Example 5: Multi-Engine Usage
    // ====================================================================================

    /**
     * Example: Using multiple engines together.
     */
    public static void multiEngineExample() {
        // Configure all three engines
        AudioVideoLibrary library = AudioVideoLibrary.builder()
            .withSttConfig(SttConfig.builder()
                .modelPath(Paths.get("/models/whisper-base.onnx"))
                .build())
            .withTtsConfig(TtsConfig.builder()
                .voiceModelPath(Paths.get("/models/piper-en.onnx"))
                .build())
            .withVisionConfig(VisionConfig.builder()
                .modelPath(Paths.get("/models/yolov8n.onnx"))
                .build())
            .build();

        // Initialize all engines eagerly (parallel)
        library.initializeAsync().whenResult(status -> {
            System.out.println("All engines initialized!");
            System.out.println("STT: " + status.sttStatus());
            System.out.println("TTS: " + status.ttsStatus());
            System.out.println("Vision: " + status.visionStatus());
        });

        // Use engines together
        try (SttEngine stt = library.getSttEngine();
             TtsEngine tts = library.getTtsEngine();
             VisionEngine vision = library.getVisionEngine()) {

            // Example: Voice-controlled vision system
            // 1. Listen for voice command
            AudioData commandAudio = captureAudioFromMicrophone();
            TranscriptionResult command = stt.transcribe(commandAudio);

            // 2. If command is "what do you see?", analyze image
            if (command.getText().toLowerCase().contains("see")) {
                ImageData image = captureImageFromCamera();
                DetectionResult detection = vision.detect(image);

                // 3. Describe findings via TTS
                String description = generateDescription(detection);
                AudioData response = tts.synthesize("I see " + description);
                playAudio(response);
            }
        }

        library.close();
    }

    // ====================================================================================
    // Example 6: Error Handling
    // ====================================================================================

    /**
     * Example: Proper error handling.
     */
    public static void errorHandlingExample() {
        AudioVideoLibrary library = AudioVideoLibrary.builder()
            .withSttConfig(SttConfig.builder()
                .modelPath(Paths.get("/models/whisper-base.onnx"))
                .build())
            .build();

        try (SttEngine stt = library.getSttEngine()) {
            try {
                AudioData audio = loadLargeAudioFile();
                TranscriptionResult result = stt.transcribe(audio);
                System.out.println(result.getText());
            } catch (ValidationError e) {
                // Input validation failed (e.g., audio too long)
                System.err.println("Invalid input: " + e.getMessage());
            } catch (ModelLoadingError e) {
                // Model couldn't be loaded
                System.err.println("Model error: " + e.getMessage());
            } catch (InferenceError e) {
                // Transcription failed
                if (e.isRetryable()) {
                    // Could retry with backoff
                    System.err.println("Transient error, retryable: " + e.getMessage());
                } else {
                    System.err.println("Non-retryable error: " + e.getMessage());
                }
            } catch (ResourceExhaustedError e) {
                // Too many concurrent requests
                System.err.println("Resource exhausted: " + e.getMessage());
            }
        }

        library.close();
    }

    // ====================================================================================
    // Example 7: Cloud Fallback
    // ====================================================================================

    /**
     * Example: Using cloud fallback for resilience.
     */
    public static void cloudFallbackExample() {
        AudioVideoLibrary library = AudioVideoLibrary.builder()
            .withSttConfig(SttConfig.builder()
                .modelPath(Paths.get("/models/whisper-base.onnx"))
                .cloudFallback(CloudFallbackConfig.builder()
                    .endpoint("https://api.ghatana.ai/v1/stt")
                    .apiKey(System.getenv("GHATANA_API_KEY"))
                    .timeout(Duration.ofSeconds(10))
                    .maxRetries(3)
                    .build())
                .build())
            .build();

        try (SttEngine stt = library.getSttEngine()) {
            // If local model fails, automatically falls back to cloud API
            AudioData audio = captureAudioFromMicrophone();
            TranscriptionResult result = stt.transcribe(audio);
            System.out.println(result.getText());
        }

        library.close();
    }

    // ====================================================================================
    // Helper Methods (stubs for demonstration)
    // ====================================================================================

    private static AudioData createAudioDataFromFile(String path) {
        // Stub - would load actual audio file
        return AudioData.builder()
            .data(new byte[16000 * 2]) // 1 second of 16kHz audio
            .sampleRate(16000)
            .channels(1)
            .bitsPerSample(16)
            .build();
    }

    private static AudioChunk captureAudioFromMicrophone(int sequence) {
        // Stub - would capture from microphone
        return new AudioChunk(
            new byte[1600 * 2], // 100ms chunk
            sequence,
            false,
            System.currentTimeMillis()
        );
    }

    private static void playAudio(AudioData audio) {
        // Stub - would play audio
        System.out.println("Playing audio: " + audio.data().length + " bytes");
    }

    private static void playAudioChunk(AudioChunk chunk) {
        // Stub - would play audio chunk
        System.out.println("Playing chunk: " + chunk.sequenceNumber());
    }

    private static ImageData loadImageFromFile(String path) {
        // Stub - would load actual image
        return ImageData.builder()
            .data(new byte[1920 * 1080 * 3])
            .width(1920)
            .height(1080)
            .format(ImageFormat.JPEG)
            .build();
    }

    private static ImageData captureImageFromCamera() {
        // Stub - would capture from camera
        return loadImageFromFile("camera");
    }

    private static AudioData loadLargeAudioFile() {
        return createAudioDataFromFile("/path/to/large/file.wav");
    }

    private static AudioData captureAudioFromMicrophone() {
        return createAudioDataFromFile("microphone");
    }

    private static String generateDescription(DetectionResult detection) {
        return detection.count() + " objects";
    }
}
