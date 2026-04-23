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

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper(); // GH-90000

    @Test
    @DisplayName("runtime settings round-trip through generated media contract")
    void shouldRoundTripRuntimeSettingsThroughContract() throws IOException { // GH-90000
        JsonNode fixture = readFixture(); // GH-90000
        JsonNode runtime = fixture.get("runtimeConfig");

        AudioVideoRuntimeSettings settings = new AudioVideoRuntimeSettings( // GH-90000
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

        AudioVideoRuntimeConfig contract = settings.toContract(); // GH-90000
        AudioVideoRuntimeSettings roundTrip = AudioVideoRuntimeSettings.fromContract(contract); // GH-90000

        assertEquals(settings, roundTrip); // GH-90000
        assertEquals("en-GB", contract.getLanguageTag()); // GH-90000
        assertEquals(4200L, contract.getDeviceAcquireTimeoutMs()); // GH-90000
    }

    @Test
    @DisplayName("processing error round-trip through generated media contract")
    void shouldRoundTripProcessingErrorThroughContract() throws IOException { // GH-90000
        JsonNode error = readFixture().get("error");
        AudioVideoProcessingError processingError = new AudioVideoProcessingError( // GH-90000
                error.get("code").asText(),
                error.get("category").asText(),
                error.get("retryable").asBoolean(),
                error.get("message").asText());

        AudioVideoError contract = processingError.toContract(); // GH-90000
        AudioVideoProcessingError roundTrip = AudioVideoProcessingError.fromContract(contract); // GH-90000

        assertEquals(processingError, roundTrip); // GH-90000
        assertTrue(contract.getRetryable()); // GH-90000
        assertEquals("temporary backend outage", contract.getMessage()); // GH-90000
    }

    private static JsonNode readFixture() throws IOException { // GH-90000
        return OBJECT_MAPPER.readTree(Files.readString(findRepoRoot() // GH-90000
                .resolve("products/audio-video/test-fixtures/media-contract-fixtures.json")));
    }

    private static Path findRepoRoot() { // GH-90000
        Path current = Path.of("").toAbsolutePath();
        while (current != null) { // GH-90000
            if (Files.exists(current.resolve("settings.gradle.kts"))
                    && Files.exists(current.resolve("pnpm-workspace.yaml"))
                    && Files.isDirectory(current.resolve("platform"))) {
                return current;
            }
            current = current.getParent(); // GH-90000
        }
        throw new IllegalStateException("Unable to locate repository root");
    }
}
