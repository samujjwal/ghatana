package com.ghatana.aep.server.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.aep.Aep;
import com.ghatana.aep.AepEngine;
import com.ghatana.agent.learning.review.DataCloudHumanReviewQueue;
import com.ghatana.agent.learning.review.ReviewItem;
import com.ghatana.aep.server.store.DataCloudPipelineStore;
import com.ghatana.datacloud.DataCloud;
import com.ghatana.datacloud.DataCloud.DataCloudConfig;
import com.ghatana.datacloud.DataCloud.DataCloudConfig.DataCloudProfile;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.pipeline.registry.model.Pipeline;
import com.ghatana.pipeline.registry.model.PipelineVersionStatus;
import com.ghatana.platform.domain.auth.TenantId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests covering AEP's runtime Data-Cloud wiring.
 *
 * @doc.type class
 * @doc.purpose Regression coverage for durable pattern CRUD and persisted analytics/query integration
 * @doc.layer product
 * @doc.pattern Test
 */
@Tag("local-network")
@DisplayName("AepHttpServer – Data-Cloud Integration")
class AepHttpServerDataCloudIntegrationTest {

    @TempDir
    Path tempDir;

    private final ObjectMapper mapper = new ObjectMapper(); // GH-90000
    private final HttpClient httpClient = HttpClient.newHttpClient(); // GH-90000

    private AepEngine engine;
    private AepHttpServer server;
    private DataCloudClient dataCloud;

    @AfterEach
    void tearDown() { // GH-90000
        if (server != null) { // GH-90000
            server.stop(); // GH-90000
        }
        if (engine != null) { // GH-90000
            engine.close(); // GH-90000
        }
        if (dataCloud != null) { // GH-90000
            dataCloud.close(); // GH-90000
        }
    }

    @Test
    @DisplayName("patterns persist across server restart when Data-Cloud is configured")
    void patternsPersistAcrossServerRestart() throws Exception { // GH-90000
        dataCloud = DataCloud.embedded(); // GH-90000

        int firstPort = findFreePort(); // GH-90000
        engine = Aep.forTesting(); // GH-90000
        server = new AepHttpServer(engine, firstPort, null, dataCloud); // GH-90000
        server.start(); // GH-90000
        waitForServerReady(firstPort); // GH-90000

        HttpResponse<String> create = post(firstPort, "/api/v1/patterns", mapper.writeValueAsString(Map.of( // GH-90000
            "tenantId", "tenant-persist",
            "name", "Persisted Pattern",
            "description", "Durable pattern",
            "type", "CUSTOM",
            "config", Map.of("eventType", "purchase.completed") // GH-90000
        )));

        assertThat(create.statusCode()).isEqualTo(200); // GH-90000
        String createdId = String.valueOf(((Map<?, ?>) mapper.readValue(create.body(), Map.class).get("pattern")).get("id"));

        server.stop(); // GH-90000
        engine.close(); // GH-90000
        server = null;
        engine = null;

        int secondPort = findFreePort(); // GH-90000
        engine = Aep.forTesting(); // GH-90000
        server = new AepHttpServer(engine, secondPort, null, dataCloud); // GH-90000
        server.start(); // GH-90000
        waitForServerReady(secondPort); // GH-90000

        HttpResponse<String> list = get(secondPort, "/api/v1/patterns?tenantId=tenant-persist"); // GH-90000
        HttpResponse<String> get = get(secondPort, "/api/v1/patterns/" + createdId + "?tenantId=tenant-persist"); // GH-90000

        assertThat(list.statusCode()).isEqualTo(200); // GH-90000
        Map<?, ?> listBody = mapper.readValue(list.body(), Map.class); // GH-90000
        assertThat(((Number) listBody.get("count")).intValue()).isGreaterThanOrEqualTo(1);
        assertThat(((List<?>) listBody.get("patterns")).toString()).contains("Persisted Pattern");

        assertThat(get.statusCode()).isEqualTo(200); // GH-90000
        Map<?, ?> getBody = mapper.readValue(get.body(), Map.class); // GH-90000
        assertThat(((Map<?, ?>) getBody.get("pattern")).get("id")).isEqualTo(createdId);
    }

    @Test
    @DisplayName("run history persists across restart with sovereign Data-Cloud and stays queryable through /api/v1/runs")
    void runHistoryPersistsAcrossRestartWithSovereignDataCloud() throws Exception { // GH-90000
        DataCloudConfig config = sovereignConfig("runs-persist");
        dataCloud = DataCloud.create(config); // GH-90000

        int firstPort = findFreePort(); // GH-90000
        engine = Aep.forTesting(); // GH-90000
        server = new AepHttpServer(engine, firstPort, null, dataCloud); // GH-90000
        server.start(); // GH-90000
        waitForServerReady(firstPort); // GH-90000

        HttpResponse<String> processResponse = post(firstPort, "/api/v1/events", mapper.writeValueAsString(Map.of( // GH-90000
            "tenantId", "tenant-runs",
            "type", "purchase.completed",
            "payload", Map.of("orderId", "order-1", "amount", 42) // GH-90000
        )));

        assertThat(processResponse.statusCode()).isEqualTo(200); // GH-90000
        Map<?, ?> processBody = mapper.readValue(processResponse.body(), Map.class); // GH-90000
        String runId = String.valueOf(processBody.get("eventId"));

        assertThat(waitForRunCount(firstPort, "tenant-runs", 1).body()).contains(runId); // GH-90000
        assertDurableDeepHealth(firstPort, "sovereign"); // GH-90000

        server.stop(); // GH-90000
        engine.close(); // GH-90000
        dataCloud.close(); // GH-90000
        server = null;
        engine = null;
        dataCloud = null;

        dataCloud = DataCloud.create(config); // GH-90000
        int secondPort = findFreePort(); // GH-90000
        engine = Aep.forTesting(); // GH-90000
        server = new AepHttpServer(engine, secondPort, null, dataCloud); // GH-90000
        server.start(); // GH-90000
        waitForServerReady(secondPort); // GH-90000

        HttpResponse<String> runsResponse = waitForRunCount(secondPort, "tenant-runs", 1); // GH-90000
        assertThat(runsResponse.statusCode()).isEqualTo(200); // GH-90000
        Map<?, ?> runsBody = mapper.readValue(runsResponse.body(), Map.class); // GH-90000
        assertThat(((Number) runsBody.get("count")).intValue()).isEqualTo(1);
        assertThat(((List<?>) runsBody.get("runs")).toString()).contains(runId, "SUCCEEDED");
        assertDurableDeepHealth(secondPort, "sovereign"); // GH-90000

        HttpResponse<String> detailResponse = get(secondPort, "/api/v1/runs/" + runId, "tenant-runs"); // GH-90000
        assertThat(detailResponse.statusCode()).isEqualTo(200); // GH-90000
        Map<?, ?> detailBody = mapper.readValue(detailResponse.body(), Map.class); // GH-90000
        assertThat(detailBody.get("runId")).isEqualTo(runId);
        assertThat(detailBody.get("tenantId")).isEqualTo("tenant-runs");
        assertThat(detailBody.get("status")).isEqualTo("SUCCEEDED");
    }

    @Test
    @DisplayName("analytics endpoints persist anomalies and KPIs and expose query/report data")
    void analyticsPersistenceAndQueryEndpointsWork() throws Exception { // GH-90000
        dataCloud = DataCloud.embedded(); // GH-90000
        int port = findFreePort(); // GH-90000
        engine = Aep.forTesting(); // GH-90000
        server = new AepHttpServer(engine, port, null, dataCloud); // GH-90000
        server.start(); // GH-90000
        waitForServerReady(port); // GH-90000

        HttpResponse<String> anomalyResp = post(port, "/api/v1/analytics/anomalies", mapper.writeValueAsString(Map.of( // GH-90000
            "tenantId", "tenant-analytics",
            "events", List.of( // GH-90000
                Map.of("type", "cpu_spike", "payload", Map.of("anomaly_score", 0.97, "value", 99.5)) // GH-90000
            )
        )));
        assertThat(anomalyResp.statusCode()).isEqualTo(200); // GH-90000

        HttpResponse<String> kpiResp = post(port, "/api/v1/analytics/kpis", mapper.writeValueAsString(Map.of( // GH-90000
            "tenantId", "tenant-analytics",
            "kpiName", "throughput",
            "value", 42.5,
            "unit", "req/s",
            "tags", List.of("prod", "api") // GH-90000
        )));
        assertThat(kpiResp.statusCode()).isEqualTo(200); // GH-90000

        HttpResponse<String> queryResp = post(port, "/api/v1/analytics/query", mapper.writeValueAsString(Map.of( // GH-90000
            "tenantId", "tenant-analytics",
            "collection", "anomalies",
            "limit", 10,
            "filters", List.of(Map.of("field", "severity", "operator", "eq", "value", "HIGH")) // GH-90000
        )));
        assertThat(queryResp.statusCode()).isEqualTo(200); // GH-90000
        Map<?, ?> queryBody = mapper.readValue(queryResp.body(), Map.class); // GH-90000
        Map<?, ?> queryResult = (Map<?, ?>) queryBody.get("result");
        assertThat(((Number) queryResult.get("count")).intValue()).isEqualTo(1);

        HttpResponse<String> listAnomalies = get(port, "/api/v1/analytics/anomalies?tenantId=tenant-analytics"); // GH-90000
        assertThat(listAnomalies.statusCode()).isEqualTo(200); // GH-90000
        Map<?, ?> anomaliesBody = mapper.readValue(listAnomalies.body(), Map.class); // GH-90000
        assertThat(((Number) anomaliesBody.get("count")).intValue()).isEqualTo(1);

        HttpResponse<String> reportResp = post(port, "/api/v1/reports", mapper.writeValueAsString(Map.of( // GH-90000
            "tenantId", "tenant-analytics",
            "reportType", "TENANT_USAGE"
        )));
        assertThat(reportResp.statusCode()).isEqualTo(200); // GH-90000
        Map<?, ?> reportBody = mapper.readValue(reportResp.body(), Map.class); // GH-90000
        assertThat(reportBody.get("report").toString()).contains("tenant-analytics");

        List<DataCloudClient.Event> events = dataCloud.queryEvents( // GH-90000
            "tenant-analytics",
            DataCloudClient.EventQuery.byType("aep.anomaly", "aep.kpi") // GH-90000
        ).getResult(); // GH-90000
        assertThat(events).extracting(DataCloudClient.Event::type) // GH-90000
            .contains("aep.anomaly", "aep.kpi"); // GH-90000
    }

    @Test
    @DisplayName("pipeline version metadata and snapshots persist across server restart when Data-Cloud is configured")
    void pipelineVersioningPersistsAcrossServerRestart() throws Exception { // GH-90000
        dataCloud = DataCloud.embedded(); // GH-90000

        String pipelineId = "pipeline-durable-1";
        DataCloudPipelineStore pipelineStore = new DataCloudPipelineStore(dataCloud); // GH-90000
        Pipeline published = new Pipeline(); // GH-90000
        published.setId(pipelineId); // GH-90000
        published.setTenantId(TenantId.of("tenant-pipeline"));
        published.setName("Durable Pipeline");
        published.setVersion(1); // GH-90000
        published.setActive(true); // GH-90000
        published.setConfig("{\"stages\":[{\"name\":\"step1\",\"type\":\"transform\"}]}"); // GH-90000
        published.setCreatedBy("test");
        published.setUpdatedBy("test");
        published.setVersionLabel("release-1");
        published.setVersionStatus(PipelineVersionStatus.PUBLISHED); // GH-90000
        pipelineStore.save(published).getResult(); // GH-90000
        pipelineStore.saveVersionSnapshot(pipelineId, published).getResult(); // GH-90000

        int firstPort = findFreePort(); // GH-90000
        engine = Aep.forTesting(); // GH-90000
        server = new AepHttpServer(engine, firstPort, null, dataCloud); // GH-90000
        server.start(); // GH-90000
        waitForServerReady(firstPort); // GH-90000

        HttpResponse<String> firstGet = get(firstPort, // GH-90000
            "/api/v1/pipelines/" + pipelineId,
            "tenant-pipeline");
        assertThat(firstGet.statusCode()).isEqualTo(200); // GH-90000

        HttpResponse<String> firstHistory = get(firstPort, // GH-90000
            "/api/v1/pipelines/" + pipelineId + "/versions",
            "tenant-pipeline");
        assertThat(firstHistory.statusCode()).isEqualTo(200); // GH-90000

        server.stop(); // GH-90000
        engine.close(); // GH-90000
        server = null;
        engine = null;

        int secondPort = findFreePort(); // GH-90000
        engine = Aep.forTesting(); // GH-90000
        server = new AepHttpServer(engine, secondPort, null, dataCloud); // GH-90000
        server.start(); // GH-90000
        waitForServerReady(secondPort); // GH-90000

        HttpResponse<String> getPipeline = get(secondPort, // GH-90000
            "/api/v1/pipelines/" + pipelineId,
            "tenant-pipeline");
        assertThat(getPipeline.statusCode()).isEqualTo(200); // GH-90000
        Map<?, ?> pipelineBody = mapper.readValue(getPipeline.body(), Map.class); // GH-90000
        assertThat(pipelineBody.get("versionLabel")).isEqualTo("release-1");
        assertThat(pipelineBody.get("versionStatus")).isEqualTo("PUBLISHED");

        HttpResponse<String> history = get(secondPort, // GH-90000
            "/api/v1/pipelines/" + pipelineId + "/versions",
            "tenant-pipeline");
        assertThat(history.statusCode()).isEqualTo(200); // GH-90000
        Map<?, ?> historyBody = mapper.readValue(history.body(), Map.class); // GH-90000
        assertThat(((Number) historyBody.get("count")).intValue()).isEqualTo(1);
        assertThat(((List<?>) historyBody.get("versions")).toString()).contains("release-1");
    }

    @Test
    @DisplayName("pipeline updates reject stale versions with a structured 409 conflict")
    void pipelineUpdateRejectsStaleVersion() throws Exception { // GH-90000
        dataCloud = DataCloud.embedded(); // GH-90000

        String pipelineId = "pipeline-conflict-1";
        DataCloudPipelineStore pipelineStore = new DataCloudPipelineStore(dataCloud); // GH-90000
        Pipeline existing = new Pipeline(); // GH-90000
        existing.setId(pipelineId); // GH-90000
        existing.setTenantId(TenantId.of("tenant-pipeline"));
        existing.setName("conflict-pipeline");
        existing.setVersion(3); // GH-90000
        existing.setActive(true); // GH-90000
        existing.setConfig("{\"stages\":[{\"name\":\"step1\"}]}"); // GH-90000
        existing.setCreatedBy("test");
        existing.setUpdatedBy("test");
        pipelineStore.save(existing).getResult(); // GH-90000

        int port = findFreePort(); // GH-90000
        engine = Aep.forTesting(); // GH-90000
        server = new AepHttpServer(engine, port, null, dataCloud); // GH-90000
        server.start(); // GH-90000
        waitForServerReady(port); // GH-90000

        HttpResponse<String> updateResp = put( // GH-90000
            port,
            "/api/v1/pipelines/" + pipelineId,
            mapper.writeValueAsString(Map.of( // GH-90000
                "id", pipelineId,
                "tenantId", "tenant-pipeline",
                "name", "conflict-pipeline-updated",
                "version", 2,
                "stages", List.of() // GH-90000
            )),
            "tenant-pipeline",
            "\"2\"");

        assertThat(updateResp.statusCode()).isEqualTo(409); // GH-90000
        Map<?, ?> conflictBody = mapper.readValue(updateResp.body(), Map.class); // GH-90000
        assertThat(conflictBody.get("errorCode")).isEqualTo("PIPELINE_VERSION_CONFLICT");
        assertThat(((Number) conflictBody.get("expectedVersion")).intValue()).isEqualTo(2);
        assertThat(((Number) conflictBody.get("currentVersion")).intValue()).isEqualTo(3);

        HttpResponse<String> getPipeline = get(port, // GH-90000
            "/api/v1/pipelines/" + pipelineId,
            "tenant-pipeline");
        assertThat(getPipeline.statusCode()).isEqualTo(200); // GH-90000
        Map<?, ?> pipelineBody = mapper.readValue(getPipeline.body(), Map.class); // GH-90000
        assertThat(((Number) pipelineBody.get("version")).intValue()).isEqualTo(3);
        assertThat(pipelineBody.get("name")).isEqualTo("conflict-pipeline");
    }

    @Test
    @DisplayName("pipeline updates succeed when the caller supplies the current version")
    void pipelineUpdateSucceedsWithCurrentVersion() throws Exception { // GH-90000
        dataCloud = DataCloud.embedded(); // GH-90000

        String pipelineId = "pipeline-conflict-2";
        DataCloudPipelineStore pipelineStore = new DataCloudPipelineStore(dataCloud); // GH-90000
        Pipeline existing = new Pipeline(); // GH-90000
        existing.setId(pipelineId); // GH-90000
        existing.setTenantId(TenantId.of("tenant-pipeline"));
        existing.setName("current-pipeline");
        existing.setVersion(3); // GH-90000
        existing.setActive(true); // GH-90000
        existing.setConfig("{\"stages\":[{\"name\":\"step1\"}]}"); // GH-90000
        existing.setCreatedBy("test");
        existing.setUpdatedBy("test");
        pipelineStore.save(existing).getResult(); // GH-90000

        int port = findFreePort(); // GH-90000
        engine = Aep.forTesting(); // GH-90000
        server = new AepHttpServer(engine, port, null, dataCloud); // GH-90000
        server.start(); // GH-90000
        waitForServerReady(port); // GH-90000

        HttpResponse<String> existingResp = get(port, // GH-90000
            "/api/v1/pipelines/" + pipelineId,
            "tenant-pipeline");
        assertThat(existingResp.statusCode()).isEqualTo(200); // GH-90000
        @SuppressWarnings("unchecked")
        Map<String, Object> updatePayload = mapper.readValue(existingResp.body(), Map.class); // GH-90000
        updatePayload.put("description", "Current pipeline description updated"); // GH-90000

        HttpResponse<String> updateResp = put( // GH-90000
            port,
            "/api/v1/pipelines/" + pipelineId,
            mapper.writeValueAsString(updatePayload), // GH-90000
            "tenant-pipeline",
            "\"3\"");

        assertThat(updateResp.statusCode()).isEqualTo(200); // GH-90000
        Map<?, ?> updatedBody = mapper.readValue(updateResp.body(), Map.class); // GH-90000
        assertThat(((Number) updatedBody.get("version")).intValue()).isEqualTo(4);
        assertThat(updatedBody.get("description")).isEqualTo("Current pipeline description updated");
    }

    @Test
    @DisplayName("pending HITL reviews persist across server restart when Data-Cloud is configured")
    void pendingHitlReviewsPersistAcrossServerRestart() throws Exception { // GH-90000
        dataCloud = DataCloud.embedded(); // GH-90000
        DataCloudHumanReviewQueue initialQueue = new DataCloudHumanReviewQueue(dataCloud); // GH-90000
        ReviewItem pending = ReviewItem.builder() // GH-90000
            .reviewId("review-persist-1")
            .tenantId("tenant-hitl")
            .skillId("skill-review")
            .proposedVersion("v1")
            .confidenceScore(0.52) // GH-90000
            .build(); // GH-90000
        initialQueue.enqueue(pending).getResult(); // GH-90000

        int firstPort = findFreePort(); // GH-90000
        engine = Aep.forTesting(); // GH-90000
        server = new AepHttpServer(engine, firstPort, initialQueue, dataCloud); // GH-90000
        server.start(); // GH-90000
        waitForServerReady(firstPort); // GH-90000

        HttpResponse<String> firstPending = get(firstPort, "/api/v1/hitl/pending?tenantId=tenant-hitl"); // GH-90000
        assertThat(firstPending.statusCode()).isEqualTo(200); // GH-90000
        Map<?, ?> firstBody = mapper.readValue(firstPending.body(), Map.class); // GH-90000
        assertThat(((Number) firstBody.get("count")).intValue()).isEqualTo(1);

        server.stop(); // GH-90000
        engine.close(); // GH-90000
        server = null;
        engine = null;

        DataCloudHumanReviewQueue restartedQueue = new DataCloudHumanReviewQueue(dataCloud); // GH-90000
        int secondPort = findFreePort(); // GH-90000
        engine = Aep.forTesting(); // GH-90000
        server = new AepHttpServer(engine, secondPort, restartedQueue, dataCloud); // GH-90000
        server.start(); // GH-90000
        waitForServerReady(secondPort); // GH-90000

        HttpResponse<String> secondPending = get(secondPort, "/api/v1/hitl/pending?tenantId=tenant-hitl"); // GH-90000
        assertThat(secondPending.statusCode()).isEqualTo(200); // GH-90000
        Map<?, ?> secondBody = mapper.readValue(secondPending.body(), Map.class); // GH-90000
        assertThat(((Number) secondBody.get("count")).intValue()).isEqualTo(1);
        assertThat(((List<?>) secondBody.get("pending")).toString()).contains("review-persist-1");
    }

    @Test
    @DisplayName("GDPR erasure removes subject records from embedded Data-Cloud collections")
    void gdprErasureRemovesSubjectRecordsFromEmbeddedDataCloud() throws Exception { // GH-90000
        dataCloud = DataCloud.embedded(); // GH-90000

        String tenantId = "tenant-gdpr";
        String subjectId = "subject-erase-1";
        List<String> collections = List.of( // GH-90000
            "aep_patterns",
            "aep_pipelines",
            "agent-registry",
            "dc_memory",
            "aep_audit"
        );

        for (String collection : collections) { // GH-90000
            dataCloud.save(tenantId, collection, Map.of( // GH-90000
                "id", collection + "-record",
                "name", "record-" + collection,
                "_subjectId", subjectId,
                "collection", collection
            )).getResult(); // GH-90000
        }

        int port = findFreePort(); // GH-90000
        engine = Aep.forTesting(); // GH-90000
        server = new AepHttpServer(engine, port, null, dataCloud); // GH-90000
        server.start(); // GH-90000
        waitForServerReady(port); // GH-90000

        HttpResponse<String> eraseResponse = post(port, "/api/v1/compliance/gdpr/erasure", mapper.writeValueAsString(Map.of( // GH-90000
            "tenantId", tenantId,
            "subjectId", subjectId
        )));

        assertThat(eraseResponse.statusCode()).isEqualTo(200); // GH-90000
        Map<?, ?> report = mapper.readValue(eraseResponse.body(), Map.class); // GH-90000
        assertThat(report.get("operationType")).isEqualTo("GDPR_ERASURE");
        assertThat(report.get("success")).isEqualTo(true);
        assertThat(((Number) report.get("total")).longValue()).isEqualTo(collections.size());

        @SuppressWarnings("unchecked")
        Map<String, Number> breakdown = (Map<String, Number>) report.get("breakdown");
        for (String collection : collections) { // GH-90000
            assertThat(breakdown).containsKey(collection); // GH-90000
            assertThat(breakdown.get(collection).longValue()).isEqualTo(1L); // GH-90000

            List<DataCloudClient.Entity> remaining = dataCloud.query( // GH-90000
                tenantId,
                collection,
                DataCloudClient.Query.builder() // GH-90000
                    .filter(DataCloudClient.Filter.eq("_subjectId", subjectId)) // GH-90000
                    .limit(10) // GH-90000
                    .build() // GH-90000
            ).getResult(); // GH-90000
            assertThat(remaining).isEmpty(); // GH-90000
        }
    }

    private HttpResponse<String> get(int port, String path) throws Exception { // GH-90000
        return get(port, path, null); // GH-90000
    }

    private HttpResponse<String> get(int port, String path, String tenantId) throws Exception { // GH-90000
        HttpRequest.Builder builder = HttpRequest.newBuilder() // GH-90000
            .uri(URI.create("http://127.0.0.1:" + port + path)) // GH-90000
            .GET(); // GH-90000
        if (tenantId != null) { // GH-90000
            builder.header("X-Tenant-Id", tenantId); // GH-90000
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString()); // GH-90000
    }

    private HttpResponse<String> post(int port, String path, String body) throws Exception { // GH-90000
        return post(port, path, body, null); // GH-90000
    }

    private HttpResponse<String> post(int port, String path, String body, String tenantId) throws Exception { // GH-90000
        HttpRequest.Builder builder = HttpRequest.newBuilder() // GH-90000
            .uri(URI.create("http://127.0.0.1:" + port + path)) // GH-90000
            .header("Content-Type", "application/json"); // GH-90000
        if (tenantId != null) { // GH-90000
            builder.header("X-Tenant-Id", tenantId); // GH-90000
        }
        return httpClient.send( // GH-90000
            builder.POST(HttpRequest.BodyPublishers.ofString(body)).build(), // GH-90000
            HttpResponse.BodyHandlers.ofString()); // GH-90000
    }

    private HttpResponse<String> put(int port, String path, String body, String tenantId, String ifMatch) throws Exception { // GH-90000
        HttpRequest.Builder builder = HttpRequest.newBuilder() // GH-90000
            .uri(URI.create("http://127.0.0.1:" + port + path)) // GH-90000
            .header("Content-Type", "application/json"); // GH-90000
        if (tenantId != null) { // GH-90000
            builder.header("X-Tenant-Id", tenantId); // GH-90000
        }
        if (ifMatch != null) { // GH-90000
            builder.header("If-Match", ifMatch); // GH-90000
        }
        return httpClient.send( // GH-90000
            builder.PUT(HttpRequest.BodyPublishers.ofString(body)).build(), // GH-90000
            HttpResponse.BodyHandlers.ofString()); // GH-90000
    }

    private DataCloudConfig sovereignConfig(String directoryName) { // GH-90000
        return DataCloudConfig.builder() // GH-90000
            .profile(DataCloudProfile.SOVEREIGN) // GH-90000
            .customConfig(Map.of("sovereign.dataDir", tempDir.resolve(directoryName).toString())) // GH-90000
            .build(); // GH-90000
    }

    private void assertDurableDeepHealth(int port, String expectedStorage) throws Exception { // GH-90000
        HttpResponse<String> response = get(port, "/health/deep"); // GH-90000
        assertThat(response.statusCode()).isEqualTo(200); // GH-90000

        Map<?, ?> body = mapper.readValue(response.body(), Map.class); // GH-90000
        @SuppressWarnings("unchecked")
        Map<String, Object> durability = (Map<String, Object>) body.get("durability");
        assertThat(durability).containsEntry("mode", "durable"); // GH-90000
        assertThat(durability).containsEntry("dataCloudStorage", expectedStorage); // GH-90000
        assertThat(durability).containsEntry("executionHistory", "durable"); // GH-90000
        assertThat(durability).containsEntry("pipelineStorage", "durable"); // GH-90000
        assertThat(durability).containsEntry("memoryPersistence", "durable"); // GH-90000
    }

    private HttpResponse<String> waitForRunCount(int port, String tenantId, int expectedCount) throws Exception { // GH-90000
        long deadline = System.currentTimeMillis() + 5_000; // GH-90000
        HttpResponse<String> lastResponse = null;
        while (System.currentTimeMillis() < deadline) { // GH-90000
            lastResponse = get(port, "/api/v1/runs", tenantId); // GH-90000
            if (lastResponse.statusCode() == 200) { // GH-90000
                Map<?, ?> body = mapper.readValue(lastResponse.body(), Map.class); // GH-90000
                if (((Number) body.get("count")).intValue() >= expectedCount) {
                    return lastResponse;
                }
            }
            Thread.sleep(50); // GH-90000
        }
        throw new AssertionError("Run history did not reach expected count for tenant " + tenantId // GH-90000
            + "; last response=" + (lastResponse != null ? lastResponse.body() : "<none>")); // GH-90000
    }

    private static int findFreePort() throws IOException { // GH-90000
        try (ServerSocket ss = new ServerSocket(0)) { // GH-90000
            return ss.getLocalPort(); // GH-90000
        }
    }

    private static void waitForServerReady(int port) throws Exception { // GH-90000
        long deadline = System.currentTimeMillis() + 5_000; // GH-90000
        while (System.currentTimeMillis() < deadline) { // GH-90000
            try {
                new Socket("127.0.0.1", port).close(); // GH-90000
                return;
            } catch (IOException ignored) { // GH-90000
                Thread.sleep(50); // GH-90000
            }
        }
        throw new AssertionError("Server did not start on port " + port + " within 5 s"); // GH-90000
    }
}
