/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.audio.video.tools;

import com.ghatana.agent.framework.governance.ActionClass;
import com.ghatana.agent.framework.tools.ToolContract;
import com.ghatana.agent.framework.tools.ToolExecutionEnvelope;
import com.ghatana.agent.framework.tools.ToolExecutionResult;
import com.ghatana.agent.framework.tools.ToolExecutionStatus;
import com.ghatana.agent.framework.tools.ToolTransport;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for all Audio-Video ToolHandler implementations and the factory.
 */
@DisplayName("AudioVideo ToolHandlers [GH-90000]")
class AudioVideoToolHandlersTest {

    private ToolContract dummyContract;

    @BeforeEach
    void setUp() { // GH-90000
        dummyContract = new ToolContract( // GH-90000
                "test-tool", "1.0.0", "Test Tool", "description",
                ActionClass.CALL_EXTERNAL, false, true,
                Map.of(), Map.of(), Set.of(), ToolTransport.REMOTE, null, Map.of()); // GH-90000
    }

    private ToolExecutionEnvelope envelope(String toolId, Map<String, Object> input) { // GH-90000
        return ToolExecutionEnvelope.of( // GH-90000
                toolId, "1.0.0", "caller-agent", null,
                "tenant-test", ActionClass.CALL_EXTERNAL, "1.0", input);
    }

    private ToolExecutionResult await(Promise<ToolExecutionResult> p) { // GH-90000
        // ToolHandlers are synchronous — invoke directly via promise value
        try {
            return p.getResult(); // GH-90000
        } catch (Exception e) { // GH-90000
            throw new AssertionError("Promise failed unexpectedly", e); // GH-90000
        }
    }

    // =========================================================================
    // SpeechToTextToolHandler
    // =========================================================================

    @Nested
    @DisplayName("SpeechToTextToolHandler [GH-90000]")
    class SttTests {

        private SpeechToTextToolHandler handler;

        @BeforeEach
        void setUp() { // GH-90000
            handler = new SpeechToTextToolHandler(); // GH-90000
        }

        @Test
        @DisplayName("succeeds with mediaArtifactId in audioSource [GH-90000]")
        void succeedsWithArtifactSource() { // GH-90000
            Map<String, Object> audioSource = Map.of("mediaArtifactId", "artifact-42"); // GH-90000
            ToolExecutionEnvelope env = envelope("av.speech-to-text", Map.of("audioSource", audioSource)); // GH-90000
            ToolExecutionResult result = await(handler.handle(env, dummyContract)); // GH-90000
            assertThat(result.status()).isEqualTo(ToolExecutionStatus.SUCCESS); // GH-90000
        }

        @Test
        @DisplayName("succeeds with audioBytes in audioSource [GH-90000]")
        void succeedsWithBytesSource() { // GH-90000
            Map<String, Object> audioSource = Map.of("audioBytes", "BASE64=="); // GH-90000
            ToolExecutionEnvelope env = envelope("av.speech-to-text", Map.of("audioSource", audioSource)); // GH-90000
            ToolExecutionResult result = await(handler.handle(env, dummyContract)); // GH-90000
            assertThat(result.status()).isEqualTo(ToolExecutionStatus.SUCCESS); // GH-90000
        }

        @Test
        @DisplayName("fails when audioSource is missing [GH-90000]")
        void failsWhenAudioSourceMissing() { // GH-90000
            ToolExecutionEnvelope env = envelope("av.speech-to-text", Map.of()); // GH-90000
            ToolExecutionResult result = await(handler.handle(env, dummyContract)); // GH-90000
            assertThat(result.status()).isEqualTo(ToolExecutionStatus.FAILED); // GH-90000
            assertThat(result.errorMessage()).contains("audioSource [GH-90000]");
        }

        @Test
        @DisplayName("output contains required fields [GH-90000]")
        void outputContainsRequiredFields() { // GH-90000
            Map<String, Object> input = new LinkedHashMap<>(); // GH-90000
            input.put("audioSource", Map.of("mediaArtifactId", "artifact-1")); // GH-90000
            input.put("languageCode", "fr-FR"); // GH-90000
            ToolExecutionEnvelope env = envelope("av.speech-to-text", input); // GH-90000
            ToolExecutionResult result = await(handler.handle(env, dummyContract)); // GH-90000
            assertThat(result.status()).isEqualTo(ToolExecutionStatus.SUCCESS); // GH-90000
            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> output = (Map<String, Object>) result.output(); // GH-90000
            assertThat(output).containsKey("transcript [GH-90000]").containsKey("confidence [GH-90000]").containsKey("languageDetected [GH-90000]");
            assertThat(output.get("languageDetected [GH-90000]")).isEqualTo("fr-FR [GH-90000]");
        }
    }

    // =========================================================================
    // TextToSpeechToolHandler
    // =========================================================================

    @Nested
    @DisplayName("TextToSpeechToolHandler [GH-90000]")
    class TtsTests {

        private TextToSpeechToolHandler handler;

        @BeforeEach
        void setUp() { // GH-90000
            handler = new TextToSpeechToolHandler(); // GH-90000
        }

        @Test
        @DisplayName("succeeds with valid text [GH-90000]")
        void succeedsWithValidText() { // GH-90000
            ToolExecutionEnvelope env = envelope("av.text-to-speech", Map.of("text", "Hello world")); // GH-90000
            ToolExecutionResult result = await(handler.handle(env, dummyContract)); // GH-90000
            assertThat(result.status()).isEqualTo(ToolExecutionStatus.SUCCESS); // GH-90000
        }

        @Test
        @DisplayName("fails when text is missing [GH-90000]")
        void failsWhenTextMissing() { // GH-90000
            ToolExecutionEnvelope env = envelope("av.text-to-speech", Map.of()); // GH-90000
            ToolExecutionResult result = await(handler.handle(env, dummyContract)); // GH-90000
            assertThat(result.status()).isEqualTo(ToolExecutionStatus.FAILED); // GH-90000
            assertThat(result.errorMessage()).contains("text [GH-90000]");
        }

        @Test
        @DisplayName("storeAsArtifact flag is reflected in output [GH-90000]")
        void storeAsArtifactFlagReflected() { // GH-90000
            Map<String, Object> input = Map.of("text", "Synthesize this", "storeAsArtifact", true); // GH-90000
            ToolExecutionEnvelope env = envelope("av.text-to-speech", input); // GH-90000
            ToolExecutionResult result = await(handler.handle(env, dummyContract)); // GH-90000
            assertThat(result.status()).isEqualTo(ToolExecutionStatus.SUCCESS); // GH-90000
            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> output = (Map<String, Object>) result.output(); // GH-90000
            assertThat(output).containsKey("mediaArtifactId [GH-90000]").containsKey("audioEncoding [GH-90000]");
        }
    }

    // =========================================================================
    // VisionAnalysisToolHandler
    // =========================================================================

    @Nested
    @DisplayName("VisionAnalysisToolHandler [GH-90000]")
    class VisionTests {

        private VisionAnalysisToolHandler handler;

        @BeforeEach
        void setUp() { // GH-90000
            handler = new VisionAnalysisToolHandler(); // GH-90000
        }

        @Test
        @DisplayName("succeeds with mediaArtifactId source [GH-90000]")
        void succeedsWithArtifact() { // GH-90000
            Map<String, Object> input = Map.of( // GH-90000
                    "mediaSource", Map.of("mediaArtifactId", "img-artifact-1"), // GH-90000
                    "analysisTypes", List.of("OBJECT_DETECTION [GH-90000]"));
            ToolExecutionEnvelope env = envelope("av.vision-analysis", input); // GH-90000
            ToolExecutionResult result = await(handler.handle(env, dummyContract)); // GH-90000
            assertThat(result.status()).isEqualTo(ToolExecutionStatus.SUCCESS); // GH-90000
        }

        @Test
        @DisplayName("fails when mediaSource is absent [GH-90000]")
        void failsWhenMediaSourceAbsent() { // GH-90000
            ToolExecutionEnvelope env = envelope("av.vision-analysis", Map.of("analysisTypes", List.of("OCR [GH-90000]")));
            ToolExecutionResult result = await(handler.handle(env, dummyContract)); // GH-90000
            assertThat(result.status()).isEqualTo(ToolExecutionStatus.FAILED); // GH-90000
            assertThat(result.errorMessage()).contains("mediaSource [GH-90000]");
        }

        @Test
        @DisplayName("output contains requested analysis type keys [GH-90000]")
        void outputContainsRequestedAnalysisKeys() { // GH-90000
            Map<String, Object> input = Map.of( // GH-90000
                    "mediaSource", Map.of("mediaArtifactId", "img-42"), // GH-90000
                    "analysisTypes", List.of("OBJECT_DETECTION", "OCR")); // GH-90000
            ToolExecutionEnvelope env = envelope("av.vision-analysis", input); // GH-90000
            ToolExecutionResult result = await(handler.handle(env, dummyContract)); // GH-90000
            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> output = (Map<String, Object>) result.output(); // GH-90000
            assertThat(output).containsKey("objects [GH-90000]").containsKey("texts [GH-90000]");
        }
    }

    // =========================================================================
    // MultimodalInferenceToolHandler
    // =========================================================================

    @Nested
    @DisplayName("MultimodalInferenceToolHandler [GH-90000]")
    class MultimodalTests {

        private MultimodalInferenceToolHandler handler;

        @BeforeEach
        void setUp() { // GH-90000
            handler = new MultimodalInferenceToolHandler(); // GH-90000
        }

        @Test
        @DisplayName("succeeds with mediaArtifactId [GH-90000]")
        void succeedsWithArtifact() { // GH-90000
            Map<String, Object> input = Map.of("mediaArtifactId", "video-artifact-1"); // GH-90000
            ToolExecutionEnvelope env = envelope("av.multimodal-inference", input); // GH-90000
            ToolExecutionResult result = await(handler.handle(env, dummyContract)); // GH-90000
            assertThat(result.status()).isEqualTo(ToolExecutionStatus.SUCCESS); // GH-90000
        }

        @Test
        @DisplayName("fails when mediaArtifactId is missing [GH-90000]")
        void failsWhenArtifactMissing() { // GH-90000
            ToolExecutionEnvelope env = envelope("av.multimodal-inference", Map.of()); // GH-90000
            ToolExecutionResult result = await(handler.handle(env, dummyContract)); // GH-90000
            assertThat(result.status()).isEqualTo(ToolExecutionStatus.FAILED); // GH-90000
        }

        @Test
        @DisplayName("output includes processingMetadata [GH-90000]")
        void outputIncludesProcessingMetadata() { // GH-90000
            Map<String, Object> input = Map.of("mediaArtifactId", "v-1", "inferenceMode", "FULL"); // GH-90000
            ToolExecutionEnvelope env = envelope("av.multimodal-inference", input); // GH-90000
            ToolExecutionResult result = await(handler.handle(env, dummyContract)); // GH-90000
            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> output = (Map<String, Object>) result.output(); // GH-90000
            assertThat(output).containsKey("summary [GH-90000]").containsKey("processingMetadata [GH-90000]");
        }
    }

    // =========================================================================
    // AudioVideoToolHandlerFactory
    // =========================================================================

    @Nested
    @DisplayName("AudioVideoToolHandlerFactory [GH-90000]")
    class FactoryTests {

        private AudioVideoToolHandlerFactory factory;

        @BeforeEach
        void setUp() { // GH-90000
            factory = new AudioVideoToolHandlerFactory(); // GH-90000
        }

        @Test
        @DisplayName("creates STT handler [GH-90000]")
        void createsSttHandler() { // GH-90000
            assertThat(factory.create("av.speech-to-text [GH-90000]")).isInstanceOf(SpeechToTextToolHandler.class);
        }

        @Test
        @DisplayName("creates TTS handler [GH-90000]")
        void createsTtsHandler() { // GH-90000
            assertThat(factory.create("av.text-to-speech [GH-90000]")).isInstanceOf(TextToSpeechToolHandler.class);
        }

        @Test
        @DisplayName("creates vision handler [GH-90000]")
        void createsVisionHandler() { // GH-90000
            assertThat(factory.create("av.vision-analysis [GH-90000]")).isInstanceOf(VisionAnalysisToolHandler.class);
        }

        @Test
        @DisplayName("creates multimodal handler [GH-90000]")
        void createsMultimodalHandler() { // GH-90000
            assertThat(factory.create("av.multimodal-inference [GH-90000]")).isInstanceOf(MultimodalInferenceToolHandler.class);
        }

        @Test
        @DisplayName("throws for unknown toolId [GH-90000]")
        void throwsForUnknownToolId() { // GH-90000
            assertThatThrownBy(() -> factory.create("av.unknown [GH-90000]"))
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("av.unknown [GH-90000]");
        }

        @Test
        @DisplayName("toolIds returns all four capabilities [GH-90000]")
        void toolIdsReturnsAllFour() { // GH-90000
            assertThat(factory.toolIds()).containsExactlyInAnyOrder( // GH-90000
                    "av.speech-to-text", "av.text-to-speech",
                    "av.vision-analysis", "av.multimodal-inference");
        }
    }
}
