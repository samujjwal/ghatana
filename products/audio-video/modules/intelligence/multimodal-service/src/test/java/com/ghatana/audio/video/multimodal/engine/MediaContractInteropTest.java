package com.ghatana.audio.video.multimodal.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.contracts.media.v1.AudioVideoError;
import com.ghatana.contracts.media.v1.AudioVideoRuntimeConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Media contract interop tests")
class MediaContractInteropTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    @DisplayName("runtime settings round-trip through generated media contract")
    void shouldRoundTripRuntimeSettingsThroughContract() throws IOException {
        JsonNode fixture = readFixture();
        JsonNode runtime = fixture.get("runtimeConfig");

        AudioVideoRuntimeSettings settings = new AudioVideoRuntimeSettings(
                runtime.get("languageTag").asText(),
                runtime.get("sttSampleRate").asInt(),
                runtime.get("sttChannels").asInt(),
                runtime.get("sttBitsPerSample").asInt(),
                runtime.get("defaultImageWidth").asInt(),
                runtime.get("defaultImageHeight").asInt(),
                runtime.get("syncToleranceMs").asInt(),
                runtime.get("syncAudioBufferMs").asInt(),
                runtime.get("syncVideoBufferMs").asInt(),
                runtime.get("defaultVideoSampleFps").asInt(),
                runtime.get("defaultVideoMaxFrames").asInt(),
                runtime.get("sttModelId").asText(),
                runtime.get("visionModelId").asText(),
                runtime.get("ttsVoiceId").asText(),
                runtime.get("metricsEnabled").asBoolean(),
                runtime.get("maxInputStreams").asInt(),
                runtime.get("maxOutputStreams").asInt(),
                runtime.get("deviceAcquireTimeoutMs").asLong(),
                runtime.get("leakDetectionThresholdMs").asLong());

        AudioVideoRuntimeConfig contract = settings.toContract();
        AudioVideoRuntimeSettings roundTrip = AudioVideoRuntimeSettings.fromContract(contract);

        assertEquals(settings, roundTrip);
        assertEquals("en-GB", contract.getLanguageTag());
        assertEquals(4200L, contract.getDeviceAcquireTimeoutMs());
    }

    @Test
    @DisplayName("processing error round-trip through generated media contract")
    void shouldRoundTripProcessingErrorThroughContract() throws IOException {
        JsonNode error = readFixture().get("error");
        AudioVideoProcessingError processingError = new AudioVideoProcessingError(
                error.get("code").asText(),
                error.get("category").asText(),
                error.get("retryable").asBoolean(),
                error.get("message").asText());

        AudioVideoError contract = processingError.toContract();
        AudioVideoProcessingError roundTrip = AudioVideoProcessingError.fromContract(contract);

        assertEquals(processingError, roundTrip);
        assertTrue(contract.getRetryable());
        assertEquals("temporary backend outage", contract.getMessage());
    }

    private static JsonNode readFixture() throws IOException {
        return OBJECT_MAPPER.readTree(Files.readString(findRepoRoot()
                .resolve("products/audio-video/test-fixtures/media-contract-fixtures.json")));
    }

    private static Path findRepoRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("settings.gradle.kts"))
                    && Files.exists(current.resolve("pnpm-workspace.yaml"))
                    && Files.isDirectory(current.resolve("platform"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to locate repository root");
    }
}
