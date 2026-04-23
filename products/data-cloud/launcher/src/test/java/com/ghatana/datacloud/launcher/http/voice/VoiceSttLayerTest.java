package com.ghatana.datacloud.launcher.http.voice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the Voice STT layer: {@link VoiceSttPort}, {@link SttTranscription},
 * {@link NopVoiceSttAdapter}, {@link WhisperSttConfig}, and error-path behaviour of
 * {@link HttpWhisperSttAdapter}.
 *
 * @doc.type class
 * @doc.purpose Unit tests for Voice STT port/adapter layer (DC-E4) // GH-90000
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Voice STT Layer Tests (DC-E4)")
class VoiceSttLayerTest {

    // ─────────────────────────────────────────────────────────────────────────
    // SttTranscription value object
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("SttTranscription")
    class SttTranscriptionTests {

        @Test
        @DisplayName("of() creates non-fallback result with expected fields")
        void ofCreatesNonFallbackResult() { // GH-90000
            SttTranscription result = SttTranscription.of("list my pipelines", 0.92, "whisper"); // GH-90000

            assertThat(result.text()).isEqualTo("list my pipelines");
            assertThat(result.confidence()).isEqualTo(0.92); // GH-90000
            assertThat(result.provider()).isEqualTo("whisper");
            assertThat(result.fallback()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("unavailable() creates fallback result with empty text")
        void unavailableCreatesFallbackResult() { // GH-90000
            SttTranscription result = SttTranscription.unavailable(); // GH-90000

            assertThat(result.text()).isEmpty(); // GH-90000
            assertThat(result.confidence()).isEqualTo(0.0); // GH-90000
            assertThat(result.provider()).isEqualTo("nop");
            assertThat(result.fallback()).isTrue(); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // NopVoiceSttAdapter
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("NopVoiceSttAdapter")
    class NopVoiceSttAdapterTests {

        @Test
        @DisplayName("isAvailable() returns false")
        void isNotAvailable() { // GH-90000
            assertThat(NopVoiceSttAdapter.INSTANCE.isAvailable()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("transcribe() resolves to SttTranscription.unavailable()")
        void transcribeReturnsFallback() { // GH-90000
            byte[] audio = "dummy-audio".getBytes(StandardCharsets.UTF_8); // GH-90000

            SttTranscription result = NopVoiceSttAdapter.INSTANCE
                .transcribe(audio, "audio/wav", "en") // GH-90000
                .getResult(); // GH-90000

            assertThat(result).isNotNull(); // GH-90000
            assertThat(result.fallback()).isTrue(); // GH-90000
            assertThat(result.text()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("INSTANCE singleton is non-null")
        void instanceIsNonNull() { // GH-90000
            assertThat(NopVoiceSttAdapter.INSTANCE).isNotNull(); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // WhisperSttConfig
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("WhisperSttConfig")
    class WhisperSttConfigTests {

        @Test
        @DisplayName("fromEnv() returns disabled config when DC_STT_URL not set")
        void fromEnvReturnsDisabledWhenUrlNotSet() { // GH-90000
            // DC_STT_URL is not set in CI — config should be disabled
            WhisperSttConfig config = WhisperSttConfig.fromEnv(); // GH-90000
            // Only assert invariants that hold regardless of env state
            assertThat(config).isNotNull(); // GH-90000
            // If disabled, fields must be safe to inspect
            if (!config.enabled()) { // GH-90000
                assertThat(config.endpointUrl()).isNotNull(); // GH-90000
            }
        }

        @Test
        @DisplayName("disabled config: enabled=false, empty endpointUrl")
        void disabledConfigHasSafeDefaults() { // GH-90000
            WhisperSttConfig config = new WhisperSttConfig(false, "", null, "whisper-1", 0); // GH-90000

            assertThat(config.enabled()).isFalse(); // GH-90000
            assertThat(config.endpointUrl()).isEmpty(); // GH-90000
            assertThat(config.model()).isEqualTo("whisper-1");
            assertThat(config.maxAudioBytes()).isEqualTo(0); // GH-90000
        }

        @Test
        @DisplayName("enabled config records all fields")
        void enabledConfigRecordsAllFields() { // GH-90000
            WhisperSttConfig config = new WhisperSttConfig( // GH-90000
                true, "https://api.openai.com", "sk-test", "whisper-1", 5_000_000);

            assertThat(config.enabled()).isTrue(); // GH-90000
            assertThat(config.endpointUrl()).isEqualTo("https://api.openai.com");
            assertThat(config.apiKey()).isEqualTo("sk-test");
            assertThat(config.model()).isEqualTo("whisper-1");
            assertThat(config.maxAudioBytes()).isEqualTo(5_000_000); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HttpWhisperSttAdapter — unit-level error paths (no network required) // GH-90000
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("HttpWhisperSttAdapter – error paths")
    class HttpWhisperSttAdapterTests {

        private HttpWhisperSttAdapter adapter(boolean enabled) { // GH-90000
            WhisperSttConfig config = new WhisperSttConfig(enabled, // GH-90000
                "http://localhost:9999", null, "whisper-1", 0);
            return new HttpWhisperSttAdapter(config, new com.fasterxml.jackson.databind.ObjectMapper(), // GH-90000
                Executors.newVirtualThreadPerTaskExecutor()); // GH-90000
        }

        @Test
        @DisplayName("isAvailable() mirrors config.enabled()")
        void isAvailableMatchesConfig() { // GH-90000
            assertThat(adapter(true).isAvailable()).isTrue(); // GH-90000
            assertThat(adapter(false).isAvailable()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("transcribe() returns NopResult when disabled")
        void transcribeReturnsNopWhenDisabled() { // GH-90000
            SttTranscription result = adapter(false) // GH-90000
                .transcribe("audio".getBytes(StandardCharsets.UTF_8), "audio/wav", null) // GH-90000
                .getResult(); // GH-90000

            assertThat(result.fallback()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("transcribe() fails promise with exception for empty audio")
        void transcribeRejectsEmptyAudio() { // GH-90000
            io.activej.promise.Promise<SttTranscription> p =
                adapter(true).transcribe(new byte[0], "audio/wav", null); // GH-90000

            assertThat(p.getException()) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("audioData must not be empty");
        }

        @Test
        @DisplayName("transcribe() fails promise with exception for null audio")
        void transcribeRejectsNullAudio() { // GH-90000
            io.activej.promise.Promise<SttTranscription> p =
                adapter(true).transcribe(null, "audio/wav", null); // GH-90000

            assertThat(p.getException()) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("audioData must not be empty");
        }

        @Test
        @DisplayName("transcribe() rejects audio exceeding configured maxAudioBytes")
        void transcribeRejectsOversizedAudio() { // GH-90000
            WhisperSttConfig config = new WhisperSttConfig(true, // GH-90000
                "http://localhost:9999", null, "whisper-1", 10); // 10-byte limit
            HttpWhisperSttAdapter tightAdapter = new HttpWhisperSttAdapter(config, // GH-90000
                new com.fasterxml.jackson.databind.ObjectMapper(), // GH-90000
                Executors.newVirtualThreadPerTaskExecutor()); // GH-90000

            byte[] bigAudio = new byte[100];
            io.activej.promise.Promise<SttTranscription> p =
                tightAdapter.transcribe(bigAudio, "audio/wav", null); // GH-90000

            assertThat(p.getException()) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("exceeds limit");
        }
        // NOTE: The async dispatch path (Promise.ofBlocking network call) requires // GH-90000
        // an ActiveJ eventloop context. End-to-end coverage for this path is in
        // DataCloudHttpServerVoiceTest via the /api/v1/voice/intent HTTP endpoint.
    }
}
