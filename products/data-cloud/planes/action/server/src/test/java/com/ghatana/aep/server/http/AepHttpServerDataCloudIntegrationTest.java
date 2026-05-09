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
    @DisplayName("run history persists across restart with sovereign Data-Cloud and stays queryable through /api/v1/runs")
    void runHistoryPersistsAcrossRestartWithSovereignDataCloud() throws Exception { 
        DataCloudConfig config = sovereignConfig("runs-persist");
        dataCloud = DataCloud.create(config); 

        int firstPort = findFreePort(); 
        engine = Aep.forTesting(); 
        server = new AepHttpServer(engine, firstPort, null, dataCloud); 
        server.start(); 
        waitForServerReady(firstPort); 

        HttpResponse<String> processResponse = post(firstPort, "/api/v1/events", mapper.writeValueAsString(Map.of( 
            "tenantId", "tenant-runs",
            "type", "purchase.completed",
            "payload", Map.of("orderId", "order-1", "amount", 42) 
        )));

        assertThat(processResponse.statusCode()).isEqualTo(200); 
        Map<?, ?> processBody = mapper.readValue(processResponse.body(), Map.class); 
        String runId = String.valueOf(processBody.get("eventId"));

        assertThat(waitForRunCount(firstPort, "tenant-runs", 1).body()).contains(runId); 
        assertDurableDeepHealth(firstPort, "sovereign"); 
        // Allow time for the sovereign DataCloud's async write-behind to flush to disk
        // before stopping the server, ensuring run history is durable for the restart check.
        Thread.sleep(500);

        server.stop(); 
        engine.close(); 
        dataCloud.close(); 
        server = null;
        engine = null;
        dataCloud = null;

        dataCloud = DataCloud.create(config); 
        int secondPort = findFreePort(); 
        engine = Aep.forTesting(); 
        server = new AepHttpServer(engine, secondPort, null, dataCloud); 
        server.start(); 
        waitForServerReady(secondPort); 

        HttpResponse<String> runsResponse = waitForRunCount(secondPort, "tenant-runs", 1); 
        assertThat(runsResponse.statusCode()).isEqualTo(200); 
        Map<?, ?> runsBody = mapper.readValue(runsResponse.body(), Map.class); 
        assertThat(((Number) runsBody.get("count")).intValue()).isEqualTo(1);
        assertThat(((List<?>) runsBody.get("runs")).toString()).contains(runId, "SUCCEEDED");
        assertDurableDeepHealth(secondPort, "sovereign"); 

        HttpResponse<String> detailResponse = get(secondPort, "/api/v1/runs/" + runId, "tenant-runs"); 
        assertThat(detailResponse.statusCode()).isEqualTo(200); 
        Map<?, ?> detailBody = mapper.readValue(detailResponse.body(), Map.class); 
        assertThat(detailBody.get("runId")).isEqualTo(runId);
        assertThat(detailBody.get("tenantId")).isEqualTo("tenant-runs");
        assertThat(detailBody.get("status")).isEqualTo("SUCCEEDED");
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

    @Test
    @DisplayName("pipeline update without any version token returns 428 PIPELINE_VERSION_REQUIRED")
    void pipelineUpdateWithoutVersionTokenReturns428() throws Exception {
        dataCloud = DataCloud.embedded();

        String pipelineId = "pipeline-no-version-1";
        DataCloudPipelineStore pipelineStore = new DataCloudPipelineStore(dataCloud);
        Pipeline existing = new Pipeline();
        existing.setId(pipelineId);
        existing.setTenantId(TenantId.of("tenant-pipeline"));
        existing.setName("no-version-pipeline");
        existing.setVersion(2);
        existing.setActive(true);
        existing.setConfig("{\"stages\":[{\"name\":\"step1\"}]}");
        existing.setCreatedBy("test");
        existing.setUpdatedBy("test");
        pipelineStore.save(existing).getResult();

        int port = findFreePort();
        engine = Aep.forTesting();
        server = new AepHttpServer(engine, port, null, dataCloud);
        server.start();
        waitForServerReady(port);

        // PUT with no If-Match header and no version/expectedVersion in the body
        HttpResponse<String> updateResp = put(
            port,
            "/api/v1/pipelines/" + pipelineId,
            mapper.writeValueAsString(Map.of(
                "id", pipelineId,
                "tenantId", "tenant-pipeline",
                "name", "no-version-pipeline-updated",
                "stages", List.of()
            )),
            "tenant-pipeline",
            null);  // no If-Match

        assertThat(updateResp.statusCode()).isEqualTo(428);
        Map<?, ?> body = mapper.readValue(updateResp.body(), Map.class);
        assertThat(body.get("errorCode")).isEqualTo("PIPELINE_VERSION_REQUIRED");
        assertThat(((Number) body.get("currentVersion")).intValue()).isEqualTo(2);

        // Original pipeline must be unchanged
        HttpResponse<String> getPipeline = get(port, "/api/v1/pipelines/" + pipelineId, "tenant-pipeline");
        assertThat(getPipeline.statusCode()).isEqualTo(200);
        Map<?, ?> pipelineBody = mapper.readValue(getPipeline.body(), Map.class);
        assertThat(((Number) pipelineBody.get("version")).intValue()).isEqualTo(2);
        assertThat(pipelineBody.get("name")).isEqualTo("no-version-pipeline");
    }

    @Test
    @DisplayName("pipeline updates reject stale versions with a structured 409 conflict")
    void pipelineUpdateRejectsStaleVersion() throws Exception { 
        dataCloud = DataCloud.embedded(); 

        String pipelineId = "pipeline-conflict-1";
        DataCloudPipelineStore pipelineStore = new DataCloudPipelineStore(dataCloud); 
        Pipeline existing = new Pipeline(); 
        existing.setId(pipelineId); 
        existing.setTenantId(TenantId.of("tenant-pipeline"));
        existing.setName("conflict-pipeline");
        existing.setVersion(3); 
        existing.setActive(true); 
        existing.setConfig("{\"stages\":[{\"name\":\"step1\"}]}"); 
        existing.setCreatedBy("test");
        existing.setUpdatedBy("test");
        pipelineStore.save(existing).getResult(); 

        int port = findFreePort(); 
        engine = Aep.forTesting(); 
        server = new AepHttpServer(engine, port, null, dataCloud); 
        server.start(); 
        waitForServerReady(port); 

        HttpResponse<String> updateResp = put( 
            port,
            "/api/v1/pipelines/" + pipelineId,
            mapper.writeValueAsString(Map.of( 
                "id", pipelineId,
                "tenantId", "tenant-pipeline",
                "name", "conflict-pipeline-updated",
                "version", 2,
                "stages", List.of() 
            )),
            "tenant-pipeline",
            "\"2\"");

        assertThat(updateResp.statusCode()).isEqualTo(409); 
        Map<?, ?> conflictBody = mapper.readValue(updateResp.body(), Map.class); 
        assertThat(conflictBody.get("errorCode")).isEqualTo("PIPELINE_VERSION_CONFLICT");
        assertThat(((Number) conflictBody.get("expectedVersion")).intValue()).isEqualTo(2);
        assertThat(((Number) conflictBody.get("currentVersion")).intValue()).isEqualTo(3);

        HttpResponse<String> getPipeline = get(port, 
            "/api/v1/pipelines/" + pipelineId,
            "tenant-pipeline");
        assertThat(getPipeline.statusCode()).isEqualTo(200); 
        Map<?, ?> pipelineBody = mapper.readValue(getPipeline.body(), Map.class); 
        assertThat(((Number) pipelineBody.get("version")).intValue()).isEqualTo(3);
        assertThat(pipelineBody.get("name")).isEqualTo("conflict-pipeline");
    }

    @Test
    @DisplayName("pipeline updates succeed when the caller supplies the current version")
    void pipelineUpdateSucceedsWithCurrentVersion() throws Exception { 
        dataCloud = DataCloud.embedded(); 

        String pipelineId = "pipeline-conflict-2";
        DataCloudPipelineStore pipelineStore = new DataCloudPipelineStore(dataCloud); 
        Pipeline existing = new Pipeline(); 
        existing.setId(pipelineId); 
        existing.setTenantId(TenantId.of("tenant-pipeline"));
        existing.setName("current-pipeline");
        existing.setVersion(3); 
        existing.setActive(true); 
        existing.setConfig("{\"stages\":[{\"name\":\"step1\"}]}"); 
        existing.setCreatedBy("test");
        existing.setUpdatedBy("test");
        pipelineStore.save(existing).getResult(); 

        int port = findFreePort(); 
        engine = Aep.forTesting(); 
        server = new AepHttpServer(engine, port, null, dataCloud); 
        server.start(); 
        waitForServerReady(port); 

        HttpResponse<String> existingResp = get(port, 
            "/api/v1/pipelines/" + pipelineId,
            "tenant-pipeline");
        assertThat(existingResp.statusCode()).isEqualTo(200); 
        @SuppressWarnings("unchecked")
        Map<String, Object> updatePayload = mapper.readValue(existingResp.body(), Map.class); 
        updatePayload.put("description", "Current pipeline description updated"); 

        HttpResponse<String> updateResp = put( 
            port,
            "/api/v1/pipelines/" + pipelineId,
            mapper.writeValueAsString(updatePayload), 
            "tenant-pipeline",
            "\"3\"");

        assertThat(updateResp.statusCode()).isEqualTo(200); 
        Map<?, ?> updatedBody = mapper.readValue(updateResp.body(), Map.class); 
        assertThat(((Number) updatedBody.get("version")).intValue()).isEqualTo(4);
        assertThat(updatedBody.get("description")).isEqualTo("Current pipeline description updated");
    }

    @Test
    @DisplayName("pending HITL reviews persist across server restart when Data-Cloud is configured")
    void pendingHitlReviewsPersistAcrossServerRestart() throws Exception { 
        dataCloud = DataCloud.embedded(); 
        DataCloudHumanReviewQueue initialQueue = new DataCloudHumanReviewQueue(dataCloud); 
        ReviewItem pending = ReviewItem.builder() 
            .reviewId("review-persist-1")
            .tenantId("tenant-hitl")
            .skillId("skill-review")
            .proposedVersion("v1")
            .confidenceScore(0.52) 
            .build(); 
        initialQueue.enqueue(pending).getResult(); 

        int firstPort = findFreePort(); 
        engine = Aep.forTesting(); 
        server = new AepHttpServer(engine, firstPort, initialQueue, dataCloud); 
        server.start(); 
        waitForServerReady(firstPort); 

        HttpResponse<String> firstPending = get(firstPort, "/api/v1/hitl/pending?tenantId=tenant-hitl"); 
        assertThat(firstPending.statusCode()).isEqualTo(200); 
        Map<?, ?> firstBody = mapper.readValue(firstPending.body(), Map.class); 
        assertThat(((Number) firstBody.get("count")).intValue()).isEqualTo(1);

        server.stop(); 
        engine.close(); 
        server = null;
        engine = null;

        DataCloudHumanReviewQueue restartedQueue = new DataCloudHumanReviewQueue(dataCloud); 
        int secondPort = findFreePort(); 
        engine = Aep.forTesting(); 
        server = new AepHttpServer(engine, secondPort, restartedQueue, dataCloud); 
        server.start(); 
        waitForServerReady(secondPort); 

        HttpResponse<String> secondPending = get(secondPort, "/api/v1/hitl/pending?tenantId=tenant-hitl"); 
        assertThat(secondPending.statusCode()).isEqualTo(200); 
        Map<?, ?> secondBody = mapper.readValue(secondPending.body(), Map.class); 
        assertThat(((Number) secondBody.get("count")).intValue()).isEqualTo(1);
        assertThat(((List<?>) secondBody.get("pending")).toString()).contains("review-persist-1");
    }

    @Test
    @DisplayName("GDPR erasure removes subject records from embedded Data-Cloud collections")
    void gdprErasureRemovesSubjectRecordsFromEmbeddedDataCloud() throws Exception { 
        dataCloud = DataCloud.embedded(); 

        String tenantId = "tenant-gdpr";
        String subjectId = "subject-erase-1";
        List<String> collections = List.of( 
            "aep_patterns",
            "aep_pipelines",
            "agent-registry",
            "dc_memory",
            "aep_audit"
        );

        for (String collection : collections) { 
            dataCloud.save(tenantId, collection, Map.of( 
                "id", collection + "-record",
                "name", "record-" + collection,
                "_subjectId", subjectId,
                "collection", collection
            )).getResult(); 
        }

        int port = findFreePort(); 
        engine = Aep.forTesting(); 
        server = new AepHttpServer(engine, port, null, dataCloud); 
        server.start(); 
        waitForServerReady(port); 

        HttpResponse<String> eraseResponse = post(port, "/api/v1/compliance/gdpr/erasure", mapper.writeValueAsString(Map.of( 
            "tenantId", tenantId,
            "subjectId", subjectId
        )));

        assertThat(eraseResponse.statusCode()).isEqualTo(200); 
        Map<?, ?> report = mapper.readValue(eraseResponse.body(), Map.class); 
        assertThat(report.get("operationType")).isEqualTo("GDPR_ERASURE");
        assertThat(report.get("success")).isEqualTo(true);
        assertThat(((Number) report.get("total")).longValue()).isEqualTo(collections.size());

        @SuppressWarnings("unchecked")
        Map<String, Number> breakdown = (Map<String, Number>) report.get("breakdown");
        for (String collection : collections) { 
            assertThat(breakdown).containsKey(collection); 
            assertThat(breakdown.get(collection).longValue()).isEqualTo(1L); 

            List<DataCloudClient.Entity> remaining = dataCloud.query( 
                tenantId,
                collection,
                DataCloudClient.Query.builder() 
                    .filter(DataCloudClient.Filter.eq("_subjectId", subjectId)) 
                    .limit(10) 
                    .build() 
            ).getResult(); 
            assertThat(remaining).isEmpty(); 
        }
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

    private HttpResponse<String> put(int port, String path, String body, String tenantId, String ifMatch) throws Exception { 
        HttpRequest.Builder builder = HttpRequest.newBuilder() 
            .uri(URI.create("http://127.0.0.1:" + port + path)) 
            .header("Content-Type", "application/json"); 
        if (tenantId != null) { 
            builder.header("X-Tenant-Id", tenantId); 
        }
        if (ifMatch != null) { 
            builder.header("If-Match", ifMatch); 
        }
        return httpClient.send( 
            builder.PUT(HttpRequest.BodyPublishers.ofString(body)).build(), 
            HttpResponse.BodyHandlers.ofString()); 
    }

    private DataCloudConfig sovereignConfig(String directoryName) { 
        return DataCloudConfig.builder() 
            .profile(DataCloudProfile.SOVEREIGN) 
            .customConfig(Map.of("sovereign.dataDir", tempDir.resolve(directoryName).toString())) 
            .build(); 
    }

    private void assertDurableDeepHealth(int port, String expectedStorage) throws Exception { 
        HttpResponse<String> response = get(port, "/health/deep"); 
        assertThat(response.statusCode()).isEqualTo(200); 

        Map<?, ?> body = mapper.readValue(response.body(), Map.class); 
        @SuppressWarnings("unchecked")
        Map<String, Object> durability = (Map<String, Object>) body.get("durability");
        assertThat(durability).containsEntry("mode", "durable"); 
        assertThat(durability).containsEntry("dataCloudStorage", expectedStorage); 
        assertThat(durability).containsEntry("executionHistory", "durable"); 
        assertThat(durability).containsEntry("pipelineStorage", "durable"); 
        assertThat(durability).containsEntry("memoryPersistence", "durable"); 
    }

    private HttpResponse<String> waitForRunCount(int port, String tenantId, int expectedCount) throws Exception { 
        long deadline = System.currentTimeMillis() + 5_000; 
        HttpResponse<String> lastResponse = null;
        while (System.currentTimeMillis() < deadline) { 
            lastResponse = get(port, "/api/v1/runs", tenantId); 
            if (lastResponse.statusCode() == 200) { 
                Map<?, ?> body = mapper.readValue(lastResponse.body(), Map.class); 
                if (((Number) body.get("count")).intValue() >= expectedCount) {
                    return lastResponse;
                }
            }
            Thread.sleep(50); 
        }
        throw new AssertionError("Run history did not reach expected count for tenant " + tenantId 
            + "; last response=" + (lastResponse != null ? lastResponse.body() : "<none>")); 
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
