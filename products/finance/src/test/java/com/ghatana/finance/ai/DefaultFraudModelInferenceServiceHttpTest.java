package com.ghatana.finance.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @doc.type class
 * @doc.purpose Verifies finance fraud inference against a real local HTTP model server
 * @doc.layer product
 * @doc.pattern Test
 */
class DefaultFraudModelInferenceServiceHttpTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void performsRealHttpInferenceAndPreservesRemoteMetadata() throws Exception {
        AtomicReference<Map<String, Object>> requestPayload = new AtomicReference<>();
        HttpServer server = createServer(exchange -> {
            requestPayload.set(readJson(exchange.getRequestBody()));
            byte[] responseBody = objectMapper.writeValueAsBytes(Map.of(
                "fraudScore", 0.87,
                "riskLevel", "HIGH",
                "fraudulent", true,
                "confidence", 0.98,
                "accuracy", 0.97,
                "fraudType", "remote-anomaly",
                "metadata", Map.of(
                    "model_version", "2026.04",
                    "latency_ms", 27
                )
            ));
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBody.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(responseBody);
            }
        });

        try {
            DefaultFraudModelInferenceService service = new DefaultFraudModelInferenceService(configuredRepository(server));

            FraudModelPrediction prediction = service.predict("fraud-detection-v2", Map.of(
                "amount_factor", 0.42,
                "merchant_category", "CRYPTO_EXCHANGE"
            ));

            assertEquals("fraud-detection-v2", requestPayload.get().get("modelId"));
            assertEquals(Map.of(
                "amount_factor", 0.42,
                "merchant_category", "CRYPTO_EXCHANGE"
            ), requestPayload.get().get("features"));
            assertEquals(0.87, prediction.getFraudScore());
            assertEquals("REMOTE", prediction.getInferenceSource());
            assertEquals("2026.04", prediction.getModelVersion());
            assertEquals(27L, prediction.getLatencyMs());
            assertEquals("remote-anomaly", prediction.getFraudType());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void fallsBackWhenRealHttpResponsePayloadIsInvalid() throws Exception {
        HttpServer server = createServer(exchange -> {
            byte[] responseBody = "{\"riskLevel\":\"HIGH\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBody.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(responseBody);
            }
        });

        try {
            DefaultFraudModelInferenceService service = new DefaultFraudModelInferenceService(configuredRepository(server));

            FraudModelPrediction prediction = service.predict("fraud-detection-v2", Map.of(
                "amount_factor", 0.08,
                "velocity_score", 1.0,
                "geolocation_risk", 0.04,
                "time_risk", 0.03
            ));

            assertEquals("FALLBACK", prediction.getInferenceSource());
            assertEquals("2026.04", prediction.getModelVersion());
            assertEquals("LOW", prediction.getRiskLevel());
            assertTrue(prediction.getFraudScore() < 0.45);
        } finally {
            server.stop(0);
        }
    }

    private ModelRepository configuredRepository(HttpServer server) {
        ModelRepository repository = new ModelRepository();
        ModelRecord model = new ModelRecord();
        model.setModelId("fraud-detection-v2");
        model.setMetadata(Map.of(
            "endpoint", "http://127.0.0.1:" + server.getAddress().getPort() + "/predict",
            "timeout_ms", 1500,
            "model_version", "2026.04"
        ));
        repository.save(model);
        return repository;
    }

    private HttpServer createServer(com.sun.net.httpserver.HttpHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/predict", handler);
        server.start();
        return server;
    }

    private Map<String, Object> readJson(InputStream inputStream) throws IOException {
        return objectMapper.readValue(inputStream, new TypeReference<>() { });
    }
}