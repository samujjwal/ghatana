package com.ghatana.aep.server.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.aep.Aep;
import com.ghatana.aep.AepEngine;
import com.ghatana.aep.server.store.DataCloudPipelineStore;
import com.ghatana.datacloud.DataCloud;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.pipeline.registry.model.Pipeline;
import com.ghatana.pipeline.registry.model.PipelineVersionStatus;
import com.ghatana.platform.domain.auth.TenantId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests covering AEP's runtime Data-Cloud wiring.
 *
 * @doc.type class
 * @doc.purpose Regression coverage for durable pattern CRUD and persisted analytics/query integration
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("AepHttpServer – Data-Cloud Integration")
class AepHttpServerDataCloudIntegrationTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private AepEngine engine;
    private AepHttpServer server;
    private DataCloudClient dataCloud;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
        if (engine != null) {
            engine.close();
        }
        if (dataCloud != null) {
            dataCloud.close();
        }
    }

    @Test
    @DisplayName("patterns persist across server restart when Data-Cloud is configured")
    void patternsPersistAcrossServerRestart() throws Exception {
        dataCloud = DataCloud.embedded();

        int firstPort = findFreePort();
        engine = Aep.forTesting();
        server = new AepHttpServer(engine, firstPort, null, dataCloud);
        server.start();
        waitForServerReady(firstPort);

        HttpResponse<String> create = post(firstPort, "/api/v1/patterns", mapper.writeValueAsString(Map.of(
            "tenantId", "tenant-persist",
            "name", "Persisted Pattern",
            "description", "Durable pattern",
            "type", "CUSTOM",
            "config", Map.of("eventType", "purchase.completed")
        )));

        assertThat(create.statusCode()).isEqualTo(200);
        String createdId = String.valueOf(((Map<?, ?>) mapper.readValue(create.body(), Map.class).get("pattern")).get("id"));

        server.stop();
        engine.close();
        server = null;
        engine = null;

        int secondPort = findFreePort();
        engine = Aep.forTesting();
        server = new AepHttpServer(engine, secondPort, null, dataCloud);
        server.start();
        waitForServerReady(secondPort);

        HttpResponse<String> list = get(secondPort, "/api/v1/patterns?tenantId=tenant-persist");
        HttpResponse<String> get = get(secondPort, "/api/v1/patterns/" + createdId + "?tenantId=tenant-persist");

        assertThat(list.statusCode()).isEqualTo(200);
        Map<?, ?> listBody = mapper.readValue(list.body(), Map.class);
        assertThat(((Number) listBody.get("count")).intValue()).isGreaterThanOrEqualTo(1);
        assertThat(((List<?>) listBody.get("patterns")).toString()).contains("Persisted Pattern");

        assertThat(get.statusCode()).isEqualTo(200);
        Map<?, ?> getBody = mapper.readValue(get.body(), Map.class);
        assertThat(((Map<?, ?>) getBody.get("pattern")).get("id")).isEqualTo(createdId);
    }

    @Test
    @DisplayName("analytics endpoints persist anomalies and KPIs and expose query/report data")
    void analyticsPersistenceAndQueryEndpointsWork() throws Exception {
        dataCloud = DataCloud.embedded();
        int port = findFreePort();
        engine = Aep.forTesting();
        server = new AepHttpServer(engine, port, null, dataCloud);
        server.start();
        waitForServerReady(port);

        HttpResponse<String> anomalyResp = post(port, "/api/v1/analytics/anomalies", mapper.writeValueAsString(Map.of(
            "tenantId", "tenant-analytics",
            "events", List.of(
                Map.of("type", "cpu_spike", "payload", Map.of("anomaly_score", 0.97, "value", 99.5))
            )
        )));
        assertThat(anomalyResp.statusCode()).isEqualTo(200);

        HttpResponse<String> kpiResp = post(port, "/api/v1/analytics/kpis", mapper.writeValueAsString(Map.of(
            "tenantId", "tenant-analytics",
            "kpiName", "throughput",
            "value", 42.5,
            "unit", "req/s",
            "tags", List.of("prod", "api")
        )));
        assertThat(kpiResp.statusCode()).isEqualTo(200);

        HttpResponse<String> queryResp = post(port, "/api/v1/analytics/query", mapper.writeValueAsString(Map.of(
            "tenantId", "tenant-analytics",
            "collection", "anomalies",
            "limit", 10,
            "filters", List.of(Map.of("field", "severity", "operator", "eq", "value", "HIGH"))
        )));
        assertThat(queryResp.statusCode()).isEqualTo(200);
        Map<?, ?> queryBody = mapper.readValue(queryResp.body(), Map.class);
        Map<?, ?> queryResult = (Map<?, ?>) queryBody.get("result");
        assertThat(((Number) queryResult.get("count")).intValue()).isEqualTo(1);

        HttpResponse<String> listAnomalies = get(port, "/api/v1/analytics/anomalies?tenantId=tenant-analytics");
        assertThat(listAnomalies.statusCode()).isEqualTo(200);
        Map<?, ?> anomaliesBody = mapper.readValue(listAnomalies.body(), Map.class);
        assertThat(((Number) anomaliesBody.get("count")).intValue()).isEqualTo(1);

        HttpResponse<String> reportResp = post(port, "/api/v1/reports", mapper.writeValueAsString(Map.of(
            "tenantId", "tenant-analytics",
            "reportType", "TENANT_USAGE"
        )));
        assertThat(reportResp.statusCode()).isEqualTo(200);
        Map<?, ?> reportBody = mapper.readValue(reportResp.body(), Map.class);
        assertThat(reportBody.get("report").toString()).contains("tenant-analytics");

        List<DataCloudClient.Event> events = dataCloud.queryEvents(
            "tenant-analytics",
            DataCloudClient.EventQuery.byType("aep.anomaly", "aep.kpi")
        ).getResult();
        assertThat(events).extracting(DataCloudClient.Event::type)
            .contains("aep.anomaly", "aep.kpi");
    }

    @Test
    @DisplayName("pipeline version metadata and snapshots persist across server restart when Data-Cloud is configured")
    void pipelineVersioningPersistsAcrossServerRestart() throws Exception {
        dataCloud = DataCloud.embedded();

        String pipelineId = "pipeline-durable-1";
        DataCloudPipelineStore pipelineStore = new DataCloudPipelineStore(dataCloud);
        Pipeline published = new Pipeline();
        published.setId(pipelineId);
        published.setTenantId(TenantId.of("tenant-pipeline"));
        published.setName("Durable Pipeline");
        published.setVersion(1);
        published.setActive(true);
        published.setConfig("{\"stages\":[{\"name\":\"step1\",\"type\":\"transform\"}]}");
        published.setCreatedBy("test");
        published.setUpdatedBy("test");
        published.setVersionLabel("release-1");
        published.setVersionStatus(PipelineVersionStatus.PUBLISHED);
        pipelineStore.save(published).getResult();
        pipelineStore.saveVersionSnapshot(pipelineId, published).getResult();

        int firstPort = findFreePort();
        engine = Aep.forTesting();
        server = new AepHttpServer(engine, firstPort, null, dataCloud);
        server.start();
        waitForServerReady(firstPort);

        HttpResponse<String> firstGet = get(firstPort,
            "/api/v1/pipelines/" + pipelineId,
            "tenant-pipeline");
        assertThat(firstGet.statusCode()).isEqualTo(200);

        HttpResponse<String> firstHistory = get(firstPort,
            "/api/v1/pipelines/" + pipelineId + "/versions",
            "tenant-pipeline");
        assertThat(firstHistory.statusCode()).isEqualTo(200);

        server.stop();
        engine.close();
        server = null;
        engine = null;

        int secondPort = findFreePort();
        engine = Aep.forTesting();
        server = new AepHttpServer(engine, secondPort, null, dataCloud);
        server.start();
        waitForServerReady(secondPort);

        HttpResponse<String> getPipeline = get(secondPort,
            "/api/v1/pipelines/" + pipelineId,
            "tenant-pipeline");
        assertThat(getPipeline.statusCode()).isEqualTo(200);
        Map<?, ?> pipelineBody = mapper.readValue(getPipeline.body(), Map.class);
        assertThat(pipelineBody.get("versionLabel")).isEqualTo("release-1");
        assertThat(pipelineBody.get("versionStatus")).isEqualTo("PUBLISHED");

        HttpResponse<String> history = get(secondPort,
            "/api/v1/pipelines/" + pipelineId + "/versions",
            "tenant-pipeline");
        assertThat(history.statusCode()).isEqualTo(200);
        Map<?, ?> historyBody = mapper.readValue(history.body(), Map.class);
        assertThat(((Number) historyBody.get("count")).intValue()).isEqualTo(1);
        assertThat(((List<?>) historyBody.get("versions")).toString()).contains("release-1");
    }

    private HttpResponse<String> get(int port, String path) throws Exception {
        return get(port, path, null);
    }

    private HttpResponse<String> get(int port, String path, String tenantId) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + path))
            .GET();
        if (tenantId != null) {
            builder.header("X-Tenant-Id", tenantId);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(int port, String path, String body) throws Exception {
        return post(port, path, body, null);
    }

    private HttpResponse<String> post(int port, String path, String body, String tenantId) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + path))
            .header("Content-Type", "application/json");
        if (tenantId != null) {
            builder.header("X-Tenant-Id", tenantId);
        }
        return httpClient.send(
            builder.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
            HttpResponse.BodyHandlers.ofString());
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket ss = new ServerSocket(0)) {
            return ss.getLocalPort();
        }
    }

    private static void waitForServerReady(int port) throws Exception {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            try {
                new Socket("127.0.0.1", port).close();
                return;
            } catch (IOException ignored) {
                Thread.sleep(50);
            }
        }
        throw new AssertionError("Server did not start on port " + port + " within 5 s");
    }
}
