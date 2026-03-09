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
 * Integration tests focused on streaming transports (SSE, WebSocket) backed by the shared
 * JobProgressStreamer implementation.
 
 * @doc.type class
 * @doc.purpose Handles streaming integration test operations
 * @doc.layer core
 * @doc.pattern Test
*/
class StreamingIntegrationTest extends IntegrationTestSupport {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    @Test
    void sseStreamReturnsExpectedEventSequence() throws Exception {
        String jobId = TestJobs.submit(harness.getJobService(), "sse-seq-1").jobId();

        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(
                                URI.create(
                                        harness.getHttpBaseUrl()
                                                + "/api/v1/jobs/"
                                                + jobId
                                                + "/events"))
                        .timeout(Duration.ofSeconds(5))
                        .header("Accept", "text/event-stream")
                        .GET()
                        .build();

        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type")).contains("text/event-stream");

        String body = response.body();
        int idxConnected = body.indexOf("event: connected");
        int idxStatus = body.indexOf("event: status");
        int idxProgress = body.indexOf("event: progress");
        int idxComplete = body.indexOf("event: complete");

        assertThat(idxConnected).isGreaterThanOrEqualTo(0);
        assertThat(idxStatus).isGreaterThan(idxConnected);
        assertThat(idxProgress).isGreaterThan(idxStatus);
        assertThat(idxComplete).isGreaterThan(idxProgress);
        assertThat(body).contains("data: {\"jobId\":\"" + jobId);
        assertThat(body).contains("event: complete\ndata: {\"jobId\":\"" + jobId + "\"}");
    }

    @Disabled("JDK HttpServer does not support WebSocket upgrade - requires alternative server impl")
    @Test
    void webSocketStreamReturnsStructuredEvents() throws Exception {
        String jobId = TestJobs.submit(harness.getJobService(), "ws-seq-1").jobId();
        URI wsUri = URI.create("ws://localhost:" + harness.getHttpPort() + "/ws/jobs/" + jobId);

        List<String> payloads = new ArrayList<>();
        CompletableFuture<List<String>> completed = new CompletableFuture<>();

        httpClient
                .newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .buildAsync(
                        wsUri,
                        new WebSocket.Listener() {
                            @Override
                            public void onOpen(WebSocket webSocket) {
                                webSocket.request(1);
                            }

                            @Override
                            public CompletionStage<?> onText(
                                    WebSocket webSocket, CharSequence data, boolean last) {
                                payloads.add(data.toString());
                                if (payloads.size() >= 4 && !completed.isDone()) {
                                    completed.complete(List.copyOf(payloads));
                                }
                                webSocket.request(1);
                                return CompletableFuture.completedFuture(null);
                            }

                            @Override
                            public void onError(WebSocket webSocket, Throwable error) {
                                completed.completeExceptionally(error);
                            }

                            @Override
                            public CompletionStage<?> onClose(
                                    WebSocket webSocket, int statusCode, String reason) {
                                if (!completed.isDone()) {
                                    completed.complete(List.copyOf(payloads));
                                }
                                return CompletableFuture.completedFuture(null);
                            }
                        })
                .join();

        List<String> messages = completed.get(5, TimeUnit.SECONDS);
        assertThat(messages).hasSizeGreaterThanOrEqualTo(4);

        List<Map<String, Object>> events =
                messages.stream()
                        .map(
                                message -> {
                                    try {
                                        return objectMapper.readValue(message, MAP_TYPE);
                                    } catch (Exception e) {
                                        throw new IllegalStateException(
                                                "Failed to parse WS payload", e);
                                    }
                                })
                        .toList();

        assertThat(events.get(0).get("event")).isEqualTo("connected");
        assertThat(events.get(1).get("event")).isEqualTo("status");
        assertThat(events.get(2).get("event")).isEqualTo("progress");
        assertThat(events.get(3).get("event")).isEqualTo("complete");

        @SuppressWarnings("unchecked")
        Map<String, Object> connected = (Map<String, Object>) events.get(0).get("data");
        assertThat(connected).containsEntry("message", "Connected to job stream");

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
