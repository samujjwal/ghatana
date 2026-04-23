package com.ghatana.datacloud.launcher.http.voice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Voice TTS Layer Tests")
class VoiceTtsLayerTest extends EventloopTestBase {

    private HttpServer server;

    @AfterEach
    void tearDown() { // GH-90000
        if (server != null) { // GH-90000
            server.stop(0); // GH-90000
        }
    }

    @Nested
    @DisplayName("NopVoiceTtsAdapter")
    class NopVoiceTtsAdapterTests {

        @Test
        @DisplayName("isAvailable returns false and synthesize returns empty audio")
        void nopAdapterReturnsEmptyAudio() { // GH-90000
            assertThat(NopVoiceTtsAdapter.INSTANCE.isAvailable()).isFalse(); // GH-90000
            byte[] audio = runPromise(() -> NopVoiceTtsAdapter.INSTANCE.synthesize("hello", "en")); // GH-90000
            assertThat(audio).isEmpty(); // GH-90000
        }
    }

    @Test
    @DisplayName("HTTP TTS adapter synthesizes audio bytes from a configured endpoint")
    void httpTtsAdapterSynthesizesAudioBytes() throws Exception { // GH-90000
        byte[] expectedAudio = "RIFFdemo-audio".getBytes(StandardCharsets.UTF_8); // GH-90000
        server = HttpServer.create(new InetSocketAddress(0), 0); // GH-90000
        server.createContext("/v1/audio/speech", exchange -> handleSpeechRequest(exchange, expectedAudio)); // GH-90000
        server.setExecutor(Executors.newCachedThreadPool()); // GH-90000
        server.start(); // GH-90000

        HttpSpeechTtsAdapter adapter = new HttpSpeechTtsAdapter( // GH-90000
            new VoiceTtsConfig(true, "http://127.0.0.1:" + server.getAddress().getPort(), null, "demo-tts", "alloy", "wav"), // GH-90000
            new ObjectMapper(), // GH-90000
            Executors.newVirtualThreadPerTaskExecutor() // GH-90000
        );

        byte[] audio = runPromise(() -> adapter.synthesize("Found 2 entities.", "en-US")); // GH-90000

        assertThat(adapter.isAvailable()).isTrue(); // GH-90000
        assertThat(audio).isEqualTo(expectedAudio); // GH-90000
    }

    private void handleSpeechRequest(HttpExchange exchange, byte[] audio) throws IOException { // GH-90000
        exchange.getResponseHeaders().add("Content-Type", "audio/wav"); // GH-90000
        exchange.sendResponseHeaders(200, audio.length); // GH-90000
        try (OutputStream output = exchange.getResponseBody()) { // GH-90000
            output.write(audio); // GH-90000
        }
    }
}