package com.ghatana.integration.crossservice;

import com.ghatana.aep.integration.registry.DataCloudPipelineRegistryClientImpl;
import com.ghatana.orchestrator.models.OrchestratorPipelineEntity;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AEP Data Cloud pipeline registry contract")
class AepDataCloudPipelineRegistryContractTest extends EventloopTestBase {

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/v1/pipelines", this::handlePipelines);
        server.createContext("/api/v1/pipelines/pipeline-1", this::handlePipelineById);
        server.createContext("/health", exchange -> writeJson(exchange, 200, "{}"));
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("maps Data Cloud pipeline list payloads")
    void mapsPipelineListPayloads() {
        DataCloudPipelineRegistryClientImpl client = new DataCloudPipelineRegistryClientImpl(baseUrl);

        List<OrchestratorPipelineEntity> pipelines = runPromise(client::listAllPipelines);

        assertThat(pipelines).hasSize(1);
        assertThat(pipelines.get(0).id).isEqualTo("pipeline-1");
        assertThat(pipelines.get(0).tenantId).isEqualTo("tenant-a");
        assertThat(pipelines.get(0).status).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("maps Data Cloud pipeline detail payloads")
    void mapsPipelineDetailPayloads() {
        DataCloudPipelineRegistryClientImpl client = new DataCloudPipelineRegistryClientImpl(baseUrl);

        Optional<OrchestratorPipelineEntity> pipeline = runPromise(() -> client.getPipeline("pipeline-1"));

        assertThat(pipeline).isPresent();
        assertThat(pipeline.orElseThrow().name).isEqualTo("Customer Intake");
        assertThat(pipeline.orElseThrow().config).isEqualTo("{\"version\":1}");
    }

    private void handlePipelines(HttpExchange exchange) throws IOException {
        String response = """
                {
                  "pipelines": [
                    {
                      "id": "pipeline-1",
                      "name": "Customer Intake",
                      "description": "Loads customer events",
                      "config": "{\\\"version\\\":1}",
                      "version": "1.2.0",
                      "createdBy": "system",
                      "status": "ACTIVE",
                      "tenantId": "tenant-a",
                      "createdAt": "2026-04-16T10:15:30Z",
                      "updatedAt": "2026-04-16T10:16:30Z"
                    }
                  ]
                }
                """;
        writeJson(exchange, 200, response);
    }

    private void handlePipelineById(HttpExchange exchange) throws IOException {
        String response = """
                {
                  "id": "pipeline-1",
                  "name": "Customer Intake",
                  "description": "Loads customer events",
                  "config": "{\\\"version\\\":1}",
                  "version": "1.2.0",
                  "createdBy": "system",
                  "status": "ACTIVE",
                  "tenantId": "tenant-a",
                  "createdAt": "2026-04-16T10:15:30Z",
                  "updatedAt": "2026-04-16T10:16:30Z"
                }
                """;
        writeJson(exchange, 200, response);
    }

    private void writeJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }
}