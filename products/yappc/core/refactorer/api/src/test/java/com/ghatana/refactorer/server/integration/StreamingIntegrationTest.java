package com.ghatana.refactorer.server.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ghatana.refactorer.server.testutils.IntegrationTestSupport;
import com.ghatana.refactorer.server.testutils.TestJobs;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Integration tests focused on streaming transports (SSE, WebSocket) backed by the shared // GH-90000
 * JobProgressStreamer implementation.

 * @doc.type class
 * @doc.purpose Handles streaming integration test operations
 * @doc.layer core
 * @doc.pattern Test
*/
class StreamingIntegrationTest extends IntegrationTestSupport {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {}; // GH-90000

    @Test
    void sseStreamReturnsExpectedEventSequence() throws Exception { // GH-90000
        String jobId = TestJobs.submit(harness.getJobService(), "sse-seq-1").jobId(); // GH-90000

        HttpRequest request =
                HttpRequest.newBuilder() // GH-90000
                        .uri( // GH-90000
                                URI.create( // GH-90000
                                        harness.getHttpBaseUrl() // GH-90000
                                                + "/api/v1/jobs/"
                                                + jobId
                                                + "/events"))
                        .timeout(Duration.ofSeconds(5)) // GH-90000
                        .header("Accept", "text/event-stream") // GH-90000
                        .GET() // GH-90000
                        .build(); // GH-90000

        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString()); // GH-90000

        assertThat(response.statusCode()).isEqualTo(200); // GH-90000
        assertThat(response.headers().firstValue("Content-Type")).contains("text/event-stream");

        String body = response.body(); // GH-90000
        int idxConnected = body.indexOf("event: connected");
        int idxStatus = body.indexOf("event: status");
        int idxProgress = body.indexOf("event: progress");
        int idxComplete = body.indexOf("event: complete");

        assertThat(idxConnected).isGreaterThanOrEqualTo(0); // GH-90000
        assertThat(idxStatus).isGreaterThan(idxConnected); // GH-90000
        assertThat(idxProgress).isGreaterThan(idxStatus); // GH-90000
        assertThat(idxComplete).isGreaterThan(idxProgress); // GH-90000
        assertThat(body).contains("data: {\"jobId\":\"" + jobId); // GH-90000
        assertThat(body).contains("event: complete\ndata: {\"jobId\":\"" + jobId + "\"}"); // GH-90000
    }

    @Disabled("JDK HttpServer does not support WebSocket upgrade - requires alternative server impl")
    @Test
    void webSocketStreamReturnsStructuredEvents() throws Exception { // GH-90000
        String jobId = TestJobs.submit(harness.getJobService(), "ws-seq-1").jobId(); // GH-90000
        URI wsUri = URI.create("ws://localhost:" + harness.getHttpPort() + "/ws/jobs/" + jobId); // GH-90000

        List<String> payloads = new ArrayList<>(); // GH-90000
        CompletableFuture<List<String>> completed = new CompletableFuture<>(); // GH-90000

        httpClient
                .newWebSocketBuilder() // GH-90000
                .connectTimeout(Duration.ofSeconds(5)) // GH-90000
                .buildAsync( // GH-90000
                        wsUri,
                        new WebSocket.Listener() { // GH-90000
                            @Override
                            public void onOpen(WebSocket webSocket) { // GH-90000
                                webSocket.request(1); // GH-90000
                            }

                            @Override
                            public CompletionStage<?> onText( // GH-90000
                                    WebSocket webSocket, CharSequence data, boolean last) {
                                payloads.add(data.toString()); // GH-90000
                                if (payloads.size() >= 4 && !completed.isDone()) { // GH-90000
                                    completed.complete(List.copyOf(payloads)); // GH-90000
                                }
                                webSocket.request(1); // GH-90000
                                return CompletableFuture.completedFuture(null); // GH-90000
                            }

                            @Override
                            public void onError(WebSocket webSocket, Throwable error) { // GH-90000
                                completed.completeExceptionally(error); // GH-90000
                            }

                            @Override
                            public CompletionStage<?> onClose( // GH-90000
                                    WebSocket webSocket, int statusCode, String reason) {
                                if (!completed.isDone()) { // GH-90000
                                    completed.complete(List.copyOf(payloads)); // GH-90000
                                }
                                return CompletableFuture.completedFuture(null); // GH-90000
                            }
                        })
                .join(); // GH-90000

        List<String> messages = completed.get(5, TimeUnit.SECONDS); // GH-90000
        assertThat(messages).hasSizeGreaterThanOrEqualTo(4); // GH-90000

        List<Map<String, Object>> events =
                messages.stream() // GH-90000
                        .map( // GH-90000
                                message -> {
                                    try {
                                        return objectMapper.readValue(message, MAP_TYPE); // GH-90000
                                    } catch (Exception e) { // GH-90000
                                        throw new IllegalStateException( // GH-90000
                                                "Failed to parse WS payload", e);
                                    }
                                })
                        .toList(); // GH-90000

        assertThat(events.get(0).get("event")).isEqualTo("connected");
        assertThat(events.get(1).get("event")).isEqualTo("status");
        assertThat(events.get(2).get("event")).isEqualTo("progress");
        assertThat(events.get(3).get("event")).isEqualTo("complete");

        @SuppressWarnings("unchecked")
        Map<String, Object> connected = (Map<String, Object>) events.get(0).get("data");
        assertThat(connected).containsEntry("message", "Connected to job stream"); // GH-90000

        @SuppressWarnings("unchecked")
        Map<String, Object> status = (Map<String, Object>) events.get(1).get("data");
        assertThat(status.get("jobId")).isEqualTo(jobId);
        assertThat(status.get("state")).isEqualTo("QUEUED");

        @SuppressWarnings("unchecked")
        Map<String, Object> progress = (Map<String, Object>) events.get(2).get("data");
        assertThat(progress.get("jobId")).isEqualTo(jobId);
        assertThat(progress.get("eventType")).isEqualTo("progress");

        @SuppressWarnings("unchecked")
        Map<String, Object> complete = (Map<String, Object>) events.get(3).get("data");
        assertThat(complete.get("jobId")).isEqualTo(jobId);
    }
}
