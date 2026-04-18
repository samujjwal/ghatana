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

@ExtendWith(MockitoExtension.class)
@DisplayName("VoiceGatewayHandler Execution")
class VoiceGatewayHandlerExecutionTest extends EventloopTestBase {

    @Mock
    private DataCloudClient client;

    @Test
    @DisplayName("query_entities executes against data cloud and includes TTS audio for the voice response")
    @SuppressWarnings("unchecked")
    void queryEntitiesExecutesAgainstDataCloudAndIncludesTtsAudio() {
        VoiceTtsPort ttsPort = new VoiceTtsPort() {
            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public Promise<byte[]> synthesize(String text, String languageHint) {
                return Promise.of("demo-audio".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        };

        VoiceGatewayHandler handler = new VoiceGatewayHandler(
            client,
            null,
            null,
            new ObjectMapper(),
            new HttpHandlerSupport(new ObjectMapper(), "*", "GET,POST", "Content-Type,X-Tenant-Id"),
            Runnable::run,
            null,
            ttsPort,
            null
        );

        when(client.query(eq("tenant-a"), eq("orders"), any()))
            .thenReturn(Promise.of(List.of(
                new DataCloudClient.Entity(
                    "order-1",
                    "orders",
                    Map.of("status", "open", "amount", 10),
                    Instant.parse("2026-04-17T00:00:00Z"),
                    Instant.parse("2026-04-17T00:00:00Z"),
                    1L
                ),
                new DataCloudClient.Entity(
                    "order-2",
                    "orders",
                    Map.of("status", "closed", "amount", 20),
                    Instant.parse("2026-04-17T00:00:00Z"),
                    Instant.parse("2026-04-17T00:00:00Z"),
                    1L
                )
            )));

        VoiceIntent intent = new VoiceIntent(
            "query_entities",
            "GET",
            "/api/v1/entities/:collection",
            "List or filter entities in a collection",
            List.of("collection"),
            List.of("limit"),
            EndpointSensitivity.INTERNAL
        );

        Map<String, Object> payload = runPromise(() -> handler.executeIntentPayload(
            intent,
            Map.of("collection", "orders", "limit", "10"),
            "tenant-a",
            "en-US"));

        assertThat(payload.get("executed")).isEqualTo(true);
        assertThat(payload.get("audioBase64")).isNotNull();
        assertThat(payload.get("speechSummary")).isEqualTo("Found 2 entities in orders.");
        Map<String, Object> result = (Map<String, Object>) payload.get("result");
        assertThat(result.get("entityCount")).isEqualTo(2);
        assertThat((List<Map<String, Object>>) result.get("entities")).hasSize(2);
    }
}