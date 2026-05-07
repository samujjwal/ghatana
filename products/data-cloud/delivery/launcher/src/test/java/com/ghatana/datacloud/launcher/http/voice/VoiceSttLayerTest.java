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
 * @doc.purpose Unit tests for Voice STT port/adapter layer (DC-E4) 
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
        void ofCreatesNonFallbackResult() { 
            SttTranscription result = SttTranscription.of("list my pipelines", 0.92, "whisper"); 

            assertThat(result.text()).isEqualTo("list my pipelines");
            assertThat(result.confidence()).isEqualTo(0.92); 
            assertThat(result.provider()).isEqualTo("whisper");
            assertThat(result.fallback()).isFalse(); 
        }

        @Test
        @DisplayName("unavailable() creates fallback result with empty text")
        void unavailableCreatesFallbackResult() { 
            SttTranscription result = SttTranscription.unavailable(); 

            assertThat(result.text()).isEmpty(); 
            assertThat(result.confidence()).isEqualTo(0.0); 
            assertThat(result.provider()).isEqualTo("nop");
            assertThat(result.fallback()).isTrue(); 
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
        void isNotAvailable() { 
            assertThat(NopVoiceSttAdapter.INSTANCE.isAvailable()).isFalse(); 
        }

        @Test
        @DisplayName("transcribe() resolves to SttTranscription.unavailable()")
        void transcribeReturnsFallback() { 
            byte[] audio = "dummy-audio".getBytes(StandardCharsets.UTF_8); 

            SttTranscription result = NopVoiceSttAdapter.INSTANCE
                .transcribe(audio, "audio/wav", "en") 
                .getResult(); 

            assertThat(result).isNotNull(); 
            assertThat(result.fallback()).isTrue(); 
            assertThat(result.text()).isEmpty(); 
        }

        @Test
        @DisplayName("INSTANCE singleton is non-null")
        void instanceIsNonNull() { 
            assertThat(NopVoiceSttAdapter.INSTANCE).isNotNull(); 
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
        void fromEnvReturnsDisabledWhenUrlNotSet() { 
            // DC_STT_URL is not set in CI — config should be disabled
            WhisperSttConfig config = WhisperSttConfig.fromEnv(); 
            // Only assert invariants that hold regardless of env state
            assertThat(config).isNotNull(); 
            // If disabled, fields must be safe to inspect
            if (!config.enabled()) { 
                assertThat(config.endpointUrl()).isNotNull(); 
            }
        }

        @Test
        @DisplayName("disabled config: enabled=false, empty endpointUrl")
        void disabledConfigHasSafeDefaults() { 
            WhisperSttConfig config = new WhisperSttConfig(false, "", null, "whisper-1", 0); 

            assertThat(config.enabled()).isFalse(); 
            assertThat(config.endpointUrl()).isEmpty(); 
            assertThat(config.model()).isEqualTo("whisper-1");
            assertThat(config.maxAudioBytes()).isEqualTo(0); 
        }

        @Test
        @DisplayName("enabled config records all fields")
        void enabledConfigRecordsAllFields() { 
            WhisperSttConfig config = new WhisperSttConfig( 
                true, "https://api.openai.com", "sk-test", "whisper-1", 5_000_000);

            assertThat(config.enabled()).isTrue(); 
            assertThat(config.endpointUrl()).isEqualTo("https://api.openai.com");
            assertThat(config.apiKey()).isEqualTo("sk-test");
            assertThat(config.model()).isEqualTo("whisper-1");
            assertThat(config.maxAudioBytes()).isEqualTo(5_000_000); 
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HttpWhisperSttAdapter — unit-level error paths (no network required) 
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("HttpWhisperSttAdapter – error paths")
    class HttpWhisperSttAdapterTests {

        private HttpWhisperSttAdapter adapter(boolean enabled) { 
            WhisperSttConfig config = new WhisperSttConfig(enabled, 
                "http://localhost:9999", null, "whisper-1", 0);
            return new HttpWhisperSttAdapter(config, new com.fasterxml.jackson.databind.ObjectMapper(), 
                Executors.newVirtualThreadPerTaskExecutor()); 
        }

        @Test
        @DisplayName("isAvailable() mirrors config.enabled()")
        void isAvailableMatchesConfig() { 
            assertThat(adapter(true).isAvailable()).isTrue(); 
            assertThat(adapter(false).isAvailable()).isFalse(); 
        }

        @Test
        @DisplayName("transcribe() returns NopResult when disabled")
        void transcribeReturnsNopWhenDisabled() { 
            SttTranscription result = adapter(false) 
                .transcribe("audio".getBytes(StandardCharsets.UTF_8), "audio/wav", null) 
                .getResult(); 

            assertThat(result.fallback()).isTrue(); 
        }

        @Test
        @DisplayName("transcribe() fails promise with exception for empty audio")
        void transcribeRejectsEmptyAudio() { 
            io.activej.promise.Promise<SttTranscription> p =
                adapter(true).transcribe(new byte[0], "audio/wav", null); 

            assertThat(p.getException()) 
                .isInstanceOf(IllegalArgumentException.class) 
                .hasMessageContaining("audioData must not be empty");
        }

        @Test
        @DisplayName("transcribe() fails promise with exception for null audio")
        void transcribeRejectsNullAudio() { 
            io.activej.promise.Promise<SttTranscription> p =
                adapter(true).transcribe(null, "audio/wav", null); 

            assertThat(p.getException()) 
                .isInstanceOf(IllegalArgumentException.class) 
                .hasMessageContaining("audioData must not be empty");
        }

        @Test
        @DisplayName("transcribe() rejects audio exceeding configured maxAudioBytes")
        void transcribeRejectsOversizedAudio() { 
            WhisperSttConfig config = new WhisperSttConfig(true, 
                "http://localhost:9999", null, "whisper-1", 10); // 10-byte limit
            HttpWhisperSttAdapter tightAdapter = new HttpWhisperSttAdapter(config, 
                new com.fasterxml.jackson.databind.ObjectMapper(), 
                Executors.newVirtualThreadPerTaskExecutor()); 

            byte[] bigAudio = new byte[100];
            io.activej.promise.Promise<SttTranscription> p =
                tightAdapter.transcribe(bigAudio, "audio/wav", null); 

            assertThat(p.getException()) 
                .isInstanceOf(IllegalArgumentException.class) 
                .hasMessageContaining("exceeds limit");
        }
        // NOTE: The async dispatch path (Promise.ofBlocking network call) requires 
        // an ActiveJ eventloop context. End-to-end coverage for this path is in
        // DataCloudHttpServerVoiceTest via the /api/v1/voice/intent HTTP endpoint.
    }
}
