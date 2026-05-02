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
    void tearDown() { 
        if (server != null) { 
            server.stop(0); 
        }
    }

    @Nested
    @DisplayName("NopVoiceTtsAdapter")
    class NopVoiceTtsAdapterTests {

        @Test
        @DisplayName("isAvailable returns false and synthesize returns empty audio")
        void nopAdapterReturnsEmptyAudio() { 
            assertThat(NopVoiceTtsAdapter.INSTANCE.isAvailable()).isFalse(); 
            byte[] audio = runPromise(() -> NopVoiceTtsAdapter.INSTANCE.synthesize("hello", "en")); 
            assertThat(audio).isEmpty(); 
        }
    }

    @Test
    @DisplayName("HTTP TTS adapter synthesizes audio bytes from a configured endpoint")
    void httpTtsAdapterSynthesizesAudioBytes() throws Exception { 
        byte[] expectedAudio = "RIFFdemo-audio".getBytes(StandardCharsets.UTF_8); 
        server = HttpServer.create(new InetSocketAddress(0), 0); 
        server.createContext("/v1/audio/speech", exchange -> handleSpeechRequest(exchange, expectedAudio)); 
        server.setExecutor(Executors.newCachedThreadPool()); 
        server.start(); 

        HttpSpeechTtsAdapter adapter = new HttpSpeechTtsAdapter( 
            new VoiceTtsConfig(true, "http://127.0.0.1:" + server.getAddress().getPort(), null, "demo-tts", "alloy", "wav"), 
            new ObjectMapper(), 
            Executors.newVirtualThreadPerTaskExecutor() 
        );

        byte[] audio = runPromise(() -> adapter.synthesize("Found 2 entities.", "en-US")); 

        assertThat(adapter.isAvailable()).isTrue(); 
        assertThat(audio).isEqualTo(expectedAudio); 
    }

    private void handleSpeechRequest(HttpExchange exchange, byte[] audio) throws IOException { 
        exchange.getResponseHeaders().add("Content-Type", "audio/wav"); 
        exchange.sendResponseHeaders(200, audio.length); 
        try (OutputStream output = exchange.getResponseBody()) { 
            output.write(audio); 
        }
    }
}