package com.ghatana.tts.core.inference;

import com.ghatana.audio.video.common.platform.AiInferenceClient;
import com.ghatana.tts.core.api.*;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.*;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link AiInferenceTtsEngine}.
 *
 * <p>A lightweight JDK {@link HttpServer} is started on an ephemeral port to
 * simulate the Ghatana AI Inference Service without requiring a real deployment.
 * Each test configures the stubbed server to return specific payloads, allowing
 * deterministic assertions over engine behaviour.
 *
 * @doc.type class
 * @doc.purpose Unit tests for AI Inference Service TTS fallback engine
 * @doc.layer test
 * @doc.pattern TestCase
 */
@DisplayName("AiInferenceTtsEngine Tests")
class AiInferenceTtsEngineTest {

    /** Minimal silent 16-bit PCM at 22050 Hz for ~100 ms. */
    private static final byte[] SILENCE_100MS = new byte[22050 / 10 * 2]; // 2205 samples × 2 bytes

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> nextTtsResponse = new AtomicReference<>();
    private final AtomicReference<Integer> nextStatusCode = new AtomicReference<>(200);

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);

        // /health — always returns 200
        server.createContext("/health", exchange -> respond(exchange, 200, "OK"));

        // /ai/infer/tts — returns whatever nextTtsResponse holds
        server.createContext("/ai/infer/tts", exchange -> {
            int status = nextStatusCode.get();
            String body = nextTtsResponse.get();
            if (body != null) {
                respond(exchange, status, body);
            } else {
                respond(exchange, 503, "{\"error\":\"not configured\"}");
            }
        });

        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Should report READY status when AI Inference Service is reachable")
    void shouldBeReadyWhenServiceIsReachable() {
        AiInferenceTtsEngine engine = engineWith(baseUrl);

        assertThat(engine.getStatus().state()).isEqualTo(EngineState.READY);
        assertThat(engine.getStatus().activeVoice()).isEqualTo("piper-en-us-amy-low");
    }

    @Test
    @DisplayName("Should report ERROR status when AI Inference Service is unreachable")
    void shouldBeErrorWhenServiceUnreachable() {
        // Point engine at an unbound port
        AiInferenceTtsEngine engine = engineWith("http://localhost:1");

        assertThat(engine.getStatus().state()).isEqualTo(EngineState.ERROR);
    }

    @Test
    @DisplayName("synthesize() should decode base64 audio and return correct SynthesisResult")
    void shouldSynthesizeFromServiceResponse() {
        String audioB64 = Base64.getEncoder().encodeToString(SILENCE_100MS);
        nextTtsResponse.set(
                "{\"audio_b64\":\"" + audioB64 + "\",\"sample_rate\":22050,\"duration_ms\":100}"
        );

        AiInferenceTtsEngine engine = engineWith(baseUrl);
        SynthesisOptions options = SynthesisOptions.builder().voiceId("piper-en-us-amy-low").build();

        SynthesisResult result = engine.synthesize("Hello world", options);

        assertThat(result.audioData()).isEqualTo(SILENCE_100MS);
        assertThat(result.sampleRate()).isEqualTo(22050);
        assertThat(result.durationMs()).isEqualTo(100L);
        assertThat(result.voiceUsed()).isEqualTo("piper-en-us-amy-low");
    }

    @Test
    @DisplayName("synthesize() should return silence when service returns no audio_b64 field")
    void shouldReturnSilenceWhenResponseMissingAudioField() {
        nextTtsResponse.set("{\"error\":\"model_error\",\"detail\":\"GPU OOM\"}");

        AiInferenceTtsEngine engine = engineWith(baseUrl);
        SynthesisResult result = engine.synthesize("Hello", SynthesisOptions.builder().build());

        // Engine should not throw — returns silence gracefully
        assertThat(result).isNotNull();
        assertThat(result.audioData()).isNotEmpty();
        assertThat(result.sampleRate()).isEqualTo(22050);
    }

    @Test
    @DisplayName("synthesize() should return silence when service returns HTTP error")
    void shouldReturnSilenceWhenServiceReturnsHttpError() {
        nextStatusCode.set(500);
        nextTtsResponse.set("{\"error\":\"internal\"}");

        AiInferenceTtsEngine engine = engineWith(baseUrl);
        SynthesisResult result = engine.synthesize("Hello", SynthesisOptions.builder().build());

        assertThat(result).isNotNull();
        assertThat(result.audioData()).isNotEmpty();
    }

    @Test
    @DisplayName("synthesize() should throw on blank text")
    void shouldThrowOnBlankText() {
        AiInferenceTtsEngine engine = engineWith(baseUrl);
        SynthesisOptions options = SynthesisOptions.builder().build();

        assertThatThrownBy(() -> engine.synthesize("  ", options))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("text must not be blank");
    }

    @Test
    @DisplayName("synthesizeStreaming() should deliver chunks summing to full audio")
    void shouldStreamAllAudioChunks() {
        // Provide 2 seconds of silence (2 × 22050 samples × 2 bytes)
        byte[] twoSecondAudio = new byte[22050 * 2 * 2];
        String audioB64 = Base64.getEncoder().encodeToString(twoSecondAudio);
        nextTtsResponse.set(
                "{\"audio_b64\":\"" + audioB64 + "\",\"sample_rate\":22050,\"duration_ms\":2000}"
        );

        AiInferenceTtsEngine engine = engineWith(baseUrl);
        SynthesisOptions options = SynthesisOptions.builder().build();

        List<AudioChunk> chunks = new java.util.ArrayList<>();
        engine.synthesizeStreaming("Long text", options, chunks::add);

        // All chunks should cover the full audio payload
        int totalBytes = chunks.stream().mapToInt(c -> c.audioData().length).sum();
        assertThat(totalBytes).isEqualTo(twoSecondAudio.length);

        // Last chunk must be marked final
        assertThat(chunks.getLast().isFinal()).isTrue();

        // All preceding chunks must not be marked final
        chunks.subList(0, chunks.size() - 1).forEach(c ->
                assertThat(c.isFinal()).isFalse()
        );
    }

    @Test
    @DisplayName("getAvailableVoices() should return the default Piper voice")
    void shouldReturnDefaultVoice() {
        AiInferenceTtsEngine engine = engineWith(baseUrl);

        List<VoiceInfo> voices = engine.getAvailableVoices(null);

        assertThat(voices).hasSize(1);
        assertThat(voices.getFirst().voiceId()).isEqualTo("piper-en-us-amy-low");
        assertThat(voices.getFirst().isLoaded()).isTrue();
    }

    @Test
    @DisplayName("loadVoice() should update active voice")
    void shouldUpdateActiveVoiceOnLoad() {
        AiInferenceTtsEngine engine = engineWith(baseUrl);

        VoiceInfo info = engine.loadVoice("piper-es-mls-alta");

        assertThat(info.voiceId()).isEqualTo("piper-es-mls-alta");
        assertThat(engine.getStatus().activeVoice()).isEqualTo("piper-es-mls-alta");
    }

    @Test
    @DisplayName("cloneVoice() should return failure result without throwing")
    void shouldReturnFailureForCloneVoice() {
        AiInferenceTtsEngine engine = engineWith(baseUrl);

        CloneResult result = engine.cloneVoice("my-voice", List.of(new byte[]{0}), 10, 0.001f);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isNotBlank();
    }

    @Test
    @DisplayName("getMetrics() should reflect synthesis count")
    void shouldAccumulateMetrics() {
        String audioB64 = Base64.getEncoder().encodeToString(SILENCE_100MS);
        nextTtsResponse.set(
                "{\"audio_b64\":\"" + audioB64 + "\",\"sample_rate\":22050,\"duration_ms\":100}"
        );

        AiInferenceTtsEngine engine = engineWith(baseUrl);
        SynthesisOptions options = SynthesisOptions.builder().build();

        engine.synthesize("First", options);
        engine.synthesize("Second", options);

        EngineMetrics metrics = engine.getMetrics();
        assertThat(metrics.totalSyntheses()).isEqualTo(2L);
        assertThat(metrics.averageLatencyMs()).isGreaterThanOrEqualTo(0f);
    }

    @Test
    @DisplayName("close() should set engine state to SHUTDOWN")
    void shouldShutdownCleanly() {
        AiInferenceTtsEngine engine = engineWith(baseUrl);

        engine.close();

        assertThat(engine.getStatus().state()).isEqualTo(EngineState.SHUTDOWN);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Create an {@link AiInferenceTtsEngine} pointing at the given base URL.
     * Uses reflection to bypass the private {@link AiInferenceClient} constructor.
     */
    private static AiInferenceTtsEngine engineWith(String url) {
        try {
            Constructor<AiInferenceClient> ctor =
                    AiInferenceClient.class.getDeclaredConstructor(String.class);
            ctor.setAccessible(true);
            AiInferenceClient client = ctor.newInstance(url);
            return new AiInferenceTtsEngine(client);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Cannot instantiate AiInferenceClient for test", e);
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
