package com.ghatana.datacloud.launcher.http.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.launcher.http.EndpointSensitivity;
import com.ghatana.datacloud.launcher.http.VoiceIntentCatalog.VoiceIntent;
import com.ghatana.datacloud.launcher.http.voice.VoiceTtsPort;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("VoiceGatewayHandler Execution [GH-90000]")
class VoiceGatewayHandlerExecutionTest extends EventloopTestBase {

    @Mock
    private DataCloudClient client;

    @Test
    @DisplayName("query_entities executes against data cloud and includes TTS audio for the voice response [GH-90000]")
    @SuppressWarnings("unchecked [GH-90000]")
    void queryEntitiesExecutesAgainstDataCloudAndIncludesTtsAudio() { // GH-90000
        VoiceTtsPort ttsPort = new VoiceTtsPort() { // GH-90000
            @Override
            public boolean isAvailable() { // GH-90000
                return true;
            }

            @Override
            public Promise<byte[]> synthesize(String text, String languageHint) { // GH-90000
                return Promise.of("demo-audio".getBytes(java.nio.charset.StandardCharsets.UTF_8)); // GH-90000
            }
        };

        VoiceGatewayHandler handler = new VoiceGatewayHandler( // GH-90000
            client,
            null,
            null,
            new ObjectMapper(), // GH-90000
            new HttpHandlerSupport(new ObjectMapper(), "*", "GET,POST", "Content-Type,X-Tenant-Id"), // GH-90000
            Runnable::run,
            null,
            ttsPort,
            null
        );

        when(client.query(eq("tenant-a [GH-90000]"), eq("orders [GH-90000]"), any()))
            .thenReturn(Promise.of(List.of( // GH-90000
                new DataCloudClient.Entity( // GH-90000
                    "order-1",
                    "orders",
                    Map.of("status", "open", "amount", 10), // GH-90000
                    Instant.parse("2026-04-17T00:00:00Z [GH-90000]"),
                    Instant.parse("2026-04-17T00:00:00Z [GH-90000]"),
                    1L
                ),
                new DataCloudClient.Entity( // GH-90000
                    "order-2",
                    "orders",
                    Map.of("status", "closed", "amount", 20), // GH-90000
                    Instant.parse("2026-04-17T00:00:00Z [GH-90000]"),
                    Instant.parse("2026-04-17T00:00:00Z [GH-90000]"),
                    1L
                )
            )));

        VoiceIntent intent = new VoiceIntent( // GH-90000
            "query_entities",
            "GET",
            "/api/v1/entities/:collection",
            "List or filter entities in a collection",
            List.of("collection [GH-90000]"),
            List.of("limit [GH-90000]"),
            EndpointSensitivity.INTERNAL
        );

        Map<String, Object> payload = runPromise(() -> handler.executeIntentPayload( // GH-90000
            intent,
            Map.of("collection", "orders", "limit", "10"), // GH-90000
            "tenant-a",
            "en-US"));

        assertThat(payload.get("executed [GH-90000]")).isEqualTo(true);
        assertThat(payload.get("audioBase64 [GH-90000]")).isNotNull();
        assertThat(payload.get("speechSummary [GH-90000]")).isEqualTo("Found 2 entities in orders. [GH-90000]");
        Map<String, Object> result = (Map<String, Object>) payload.get("result [GH-90000]");
        assertThat(result.get("entityCount [GH-90000]")).isEqualTo(2);
        assertThat((List<Map<String, Object>>) result.get("entities [GH-90000]")).hasSize(2);
    }
}