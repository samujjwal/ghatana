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

@DisplayName("HttpWhisperSttAdapter Integration")
class HttpWhisperSttAdapterIntegrationTest extends EventloopTestBase {

    private HttpServer server;

    @AfterEach
    void tearDown() { 
        if (server != null) { 
            server.stop(0); 
        }
    }

    @Test
    @DisplayName("transcription completes within two seconds against a responsive STT endpoint")
    void transcriptionCompletesWithinTwoSeconds() throws Exception { 
        server = HttpServer.create(new InetSocketAddress(0), 0); 
        server.createContext("/v1/audio/transcriptions", this::handleTranscriptionRequest); 
        server.setExecutor(Executors.newCachedThreadPool()); 
        server.start(); 

        HttpWhisperSttAdapter adapter = new HttpWhisperSttAdapter( 
            new WhisperSttConfig(true, "http://127.0.0.1:" + server.getAddress().getPort(), null, "whisper-1", 1024 * 1024), 
            new ObjectMapper(), 
            Executors.newVirtualThreadPerTaskExecutor() 
        );

        Instant startedAt = Instant.now(); 
        SttTranscription result = runPromise(() -> adapter.transcribe("audio".getBytes(StandardCharsets.UTF_8), "audio/wav", "en")); 
        long elapsedMillis = Duration.between(startedAt, Instant.now()).toMillis(); 

        assertThat(result.text()).isEqualTo("list pipelines");
        assertThat(result.fallback()).isFalse(); 
        assertThat(elapsedMillis).isLessThan(2_000L); 
    }

    private void handleTranscriptionRequest(HttpExchange exchange) throws IOException { 
        byte[] response = "{\"text\":\"list pipelines\",\"segments\":[{\"no_speech_prob\":0.01}]}".getBytes(StandardCharsets.UTF_8); 
        exchange.getResponseHeaders().add("Content-Type", "application/json"); 
        exchange.sendResponseHeaders(200, response.length); 
        try (OutputStream output = exchange.getResponseBody()) { 
            output.write(response); 
        }
    }
}