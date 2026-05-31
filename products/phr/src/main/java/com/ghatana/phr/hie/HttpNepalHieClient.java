package com.ghatana.phr.hie;

import io.activej.promise.Promise;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * @doc.type class
 * @doc.purpose Sends HL7 payloads to Nepal HIE over HTTP and parses ACK responses
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class HttpNepalHieClient implements NepalHieClient {

    @FunctionalInterface
    interface HieTransport {
        HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException;
    }

    private final NepalHieConfig config;
    private final Executor executor;
    private final HieTransport transport;

    public HttpNepalHieClient(HttpClient httpClient, Executor executor, NepalHieConfig config) {
        this(executor, config, request -> httpClient.send(request, HttpResponse.BodyHandlers.ofString()));
    }

    HttpNepalHieClient(Executor executor, NepalHieConfig config, HieTransport transport) {
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.transport = Objects.requireNonNull(transport, "transport must not be null");
    }

    @Override
    public Promise<NepalHieAck> submitMessage(String patientId, String correlationId, String hl7Message) {
        return Promise.ofBlocking(executor, () -> sendBlocking(patientId, correlationId, hl7Message));
    }

    private NepalHieAck sendBlocking(String patientId, String correlationId, String hl7Message) throws IOException, InterruptedException {
        if (config.endpoint() == null || config.endpoint().isBlank()) {
            throw new IllegalStateException("HIE endpoint is not configured");
        }
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(config.endpoint()))
            .timeout(config.requestTimeout())
            .header("Content-Type", "application/hl7-v2")
            .header("X-Ghatana-Patient-Id", patientId)
            .header("X-Ghatana-Correlation-Id", correlationId)
            .POST(HttpRequest.BodyPublishers.ofString(hl7Message));
        if (config.bearerToken() != null && !config.bearerToken().isBlank()) {
            requestBuilder.header("Authorization", "Bearer " + config.bearerToken());
        }
        HttpRequest request = requestBuilder.build();

        HttpResponse<String> response = transport.send(request);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Nepal HIE submission failed with HTTP " + response.statusCode());
        }

        return NepalHieAck.parse(response.body());
    }
}
