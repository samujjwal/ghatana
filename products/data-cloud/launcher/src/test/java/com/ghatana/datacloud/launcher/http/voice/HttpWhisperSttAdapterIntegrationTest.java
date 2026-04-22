package com.ghatana.datacloud.launcher.http.voice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HttpWhisperSttAdapter Integration [GH-90000]")
class HttpWhisperSttAdapterIntegrationTest extends EventloopTestBase {

    private HttpServer server;

    @AfterEach
    void tearDown() { // GH-90000
        if (server != null) { // GH-90000
            server.stop(0); // GH-90000
        }
    }

    @Test
    @DisplayName("transcription completes within two seconds against a responsive STT endpoint [GH-90000]")
    void transcriptionCompletesWithinTwoSeconds() throws Exception { // GH-90000
        server = HttpServer.create(new InetSocketAddress(0), 0); // GH-90000
        server.createContext("/v1/audio/transcriptions", this::handleTranscriptionRequest); // GH-90000
        server.setExecutor(Executors.newCachedThreadPool()); // GH-90000
        server.start(); // GH-90000

        HttpWhisperSttAdapter adapter = new HttpWhisperSttAdapter( // GH-90000
            new WhisperSttConfig(true, "http://127.0.0.1:" + server.getAddress().getPort(), null, "whisper-1", 1024 * 1024), // GH-90000
            new ObjectMapper(), // GH-90000
            Executors.newVirtualThreadPerTaskExecutor() // GH-90000
        );

        Instant startedAt = Instant.now(); // GH-90000
        SttTranscription result = runPromise(() -> adapter.transcribe("audio".getBytes(StandardCharsets.UTF_8), "audio/wav", "en")); // GH-90000
        long elapsedMillis = Duration.between(startedAt, Instant.now()).toMillis(); // GH-90000

        assertThat(result.text()).isEqualTo("list pipelines [GH-90000]");
        assertThat(result.fallback()).isFalse(); // GH-90000
        assertThat(elapsedMillis).isLessThan(2_000L); // GH-90000
    }

    private void handleTranscriptionRequest(HttpExchange exchange) throws IOException { // GH-90000
        byte[] response = "{\"text\":\"list pipelines\",\"segments\":[{\"no_speech_prob\":0.01}]}".getBytes(StandardCharsets.UTF_8); // GH-90000
        exchange.getResponseHeaders().add("Content-Type", "application/json"); // GH-90000
        exchange.sendResponseHeaders(200, response.length); // GH-90000
        try (OutputStream output = exchange.getResponseBody()) { // GH-90000
            output.write(response); // GH-90000
        }
    }
}