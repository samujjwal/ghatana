package com.ghatana.yappc.api;

import com.ghatana.audit.AuditLogger;
import com.ghatana.platform.governance.security.Principal;
import com.ghatana.yappc.common.JsonMapper;
import com.ghatana.yappc.domain.run.ObservationConfig;
import com.ghatana.yappc.domain.run.RunResult;
import com.ghatana.yappc.domain.run.RunSpec;
import com.ghatana.yappc.domain.run.RunStatus;
import com.ghatana.yappc.services.run.RunService;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.bytebuf.ByteBuf;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Verify Run API rollback, promote, and observation request contracts
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("RunApiController")
class RunApiControllerTest extends EventloopTestBase {

    private InMemoryRunService runService;
    private RecordingAuditLogger auditLogger;
    private RunApiController controller;

    @BeforeEach
    void setUp() {
        runService = new InMemoryRunService();
        auditLogger = new RecordingAuditLogger();
        controller = new RunApiController(runService, auditLogger);
    }

    @Test
    @DisplayName("rollback requires deployment and target version")
    void rollbackRequiresDeploymentScope() throws Exception {
        HttpResponse response = runPromise(() -> controller.rollback(post("/api/v1/yappc/run/rollback", Map.of("deploymentId", "deploy-1"))));

        assertThat(response.getCode()).isEqualTo(400);
        assertThat(runService.rollbackCallCount()).isEqualTo(0);
        assertThat(auditLogger.events()).hasSize(1);
        assertThat(auditLogger.events().get(0))
            .containsEntry("type", "run.rollback.request")
            .containsEntry("outcome", "rejected")
            .containsEntry("runId", "deploy-1");
        Map<?, ?> metadata = (Map<?, ?>) auditLogger.events().get(0).get("metadata");
        assertThat(metadata.get("route")).isEqualTo("run-rollback");
        assertThat(metadata.get("reason")).isEqualTo("deploymentId and targetVersion are required");
    }

    @Test
    @DisplayName("rollback delegates deployment id and target version")
    void rollbackDelegatesDeploymentScope() throws Exception {
        HttpResponse response = runPromise(() -> controller.rollback(post(
            "/api/v1/yappc/run/rollback",
            Map.of("deploymentId", "deploy-1", "targetVersion", "previous-stable")
        )));

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(runService.rollbackCallCount()).isEqualTo(1);
        assertThat(runService.lastDeploymentId()).isEqualTo("deploy-1");
        assertThat(runService.lastTargetVersion()).isEqualTo("previous-stable");
        assertThat(auditLogger.events()).hasSize(1);
        assertThat(auditLogger.events().get(0))
            .containsEntry("type", "run.rollback.request")
            .containsEntry("outcome", "succeeded")
            .containsEntry("runId", "deploy-1");
    }

    @Test
    @DisplayName("promote delegates deployment id and target environment")
    void promoteDelegatesDeploymentScope() throws Exception {
        HttpResponse response = runPromise(() -> controller.promote(post(
            "/api/v1/yappc/run/promote",
            Map.of("deploymentId", "deploy-1", "targetEnvironment", "staging")
        )));

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(runService.promoteCallCount()).isEqualTo(1);
        assertThat(runService.lastDeploymentId()).isEqualTo("deploy-1");
        assertThat(runService.lastTargetEnvironment()).isEqualTo("staging");
        assertThat(auditLogger.events()).hasSize(1);
        assertThat(auditLogger.events().get(0))
            .containsEntry("type", "run.promote.request")
            .containsEntry("outcome", "succeeded")
            .containsEntry("environment", "staging");
    }

    @Test
    @DisplayName("run with observation uses default observation config when omitted")
    void runWithObservationUsesDefaultConfig() throws Exception {
        HttpResponse response = runPromise(() -> controller.executeRunWithObservation(post(
            "/api/v1/yappc/run/with-observation",
            Map.of("runSpec", Map.of("id", "run-1", "environment", "staging"))
        )));

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(runService.executeWithObservationCallCount()).isEqualTo(1);
        assertThat(runService.lastObservationConfig()).isEqualTo(ObservationConfig.defaultConfig());
        assertThat(auditLogger.events()).hasSize(1);
        assertThat(auditLogger.events().get(0))
            .containsEntry("type", "run.observation.request")
            .containsEntry("outcome", "succeeded")
            .containsEntry("runId", "run-1")
            .containsEntry("environment", "staging");
    }

    @Test
    @DisplayName("run request audit includes actor tenant workspace project and correlation metadata")
    void runRequestAuditIncludesScopeAndCorrelation() throws Exception {
        HttpRequest request = post(
            "/api/v1/yappc/run",
            Map.of("id", "run-1", "environment", "preview")
        );
        request.attach(Principal.class, new Principal("runner-1", List.of("operator"), "tenant-1"));

        HttpResponse response = runPromise(() -> controller.executeRun(request));

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(auditLogger.events()).hasSize(1);
        assertThat(auditLogger.events().get(0))
            .containsEntry("type", "run.execute.request")
            .containsEntry("outcome", "succeeded")
            .containsEntry("actor", "runner-1")
            .containsEntry("tenantId", "tenant-1")
            .containsEntry("workspaceId", "workspace-1")
            .containsEntry("projectId", "project-1")
            .containsEntry("runId", "run-1")
            .containsEntry("environment", "preview")
            .containsEntry("correlationId", "corr-1");
        Map<?, ?> metadata = (Map<?, ?>) auditLogger.events().get(0).get("metadata");
        assertThat(metadata.get("route")).isEqualTo("run");
        assertThat(metadata.get("runSpecId")).isEqualTo("run-1");
        assertThat(metadata.get("status")).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("run audit failure does not block successful run response")
    void runAuditFailureDoesNotBlockResponse() throws Exception {
        auditLogger.failNext();

        HttpResponse response = runPromise(() -> controller.promote(post(
            "/api/v1/yappc/run/promote",
            Map.of("deploymentId", "deploy-1", "targetEnvironment", "production")
        )));

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(runService.promoteCallCount()).isEqualTo(1);
        assertThat(auditLogger.events()).hasSize(1);
    }

    private HttpRequest post(String path, Object body) throws Exception {
        return HttpRequest.post("http://localhost" + path)
            .withHeader(HttpHeaders.of("X-Workspace-Id"), "workspace-1")
            .withHeader(HttpHeaders.of("X-Project-Id"), "project-1")
            .withHeader(HttpHeaders.of("X-Correlation-ID"), "corr-1")
            .withBody(ByteBuf.wrapForReading(JsonMapper.toJson(body).getBytes(StandardCharsets.UTF_8)))
            .build();
    }

    private static final class RecordingAuditLogger implements AuditLogger {
        private final List<Map<String, Object>> events = new ArrayList<>();
        private boolean failNext;

        @Override
        public Promise<Void> log(Map<String, Object> event) {
            events.add(event);
            if (failNext) {
                failNext = false;
                return Promise.ofException(new IllegalStateException("audit unavailable"));
            }
            return Promise.complete();
        }

        List<Map<String, Object>> events() {
            return events;
        }

        void failNext() {
            failNext = true;
        }
    }

    private static final class InMemoryRunService implements RunService {
        private int rollbackCallCount;
        private int promoteCallCount;
        private int executeWithObservationCallCount;
        private String lastDeploymentId;
        private String lastTargetVersion;
        private String lastTargetEnvironment;
        private ObservationConfig lastObservationConfig;

        int rollbackCallCount() {
            return rollbackCallCount;
        }

        int promoteCallCount() {
            return promoteCallCount;
        }

        int executeWithObservationCallCount() {
            return executeWithObservationCallCount;
        }

        String lastDeploymentId() {
            return lastDeploymentId;
        }

        String lastTargetVersion() {
            return lastTargetVersion;
        }

        String lastTargetEnvironment() {
            return lastTargetEnvironment;
        }

        ObservationConfig lastObservationConfig() {
            return lastObservationConfig;
        }

        @Override
        public Promise<RunResult> execute(RunSpec spec) {
            return Promise.of(result(spec.id(), RunStatus.SUCCESS));
        }

        @Override
        public Promise<RunResult> executeWithObservation(RunSpec spec, ObservationConfig config) {
            executeWithObservationCallCount++;
            lastObservationConfig = config;
            return Promise.of(result(spec.id(), RunStatus.SUCCESS));
        }

        @Override
        public Promise<RunResult> rollback(String deploymentId, String targetVersion) {
            rollbackCallCount++;
            lastDeploymentId = deploymentId;
            lastTargetVersion = targetVersion;
            return Promise.of(result(deploymentId, RunStatus.SUCCESS));
        }

        @Override
        public Promise<RunResult> promote(String deploymentId, String targetEnvironment) {
            promoteCallCount++;
            lastDeploymentId = deploymentId;
            lastTargetEnvironment = targetEnvironment;
            return Promise.of(result(deploymentId, RunStatus.SUCCESS));
        }

        private RunResult result(String id, RunStatus status) {
            return new RunResult(
                "result-" + id,
                id,
                status,
                List.of(),
                Instant.parse("2026-04-21T11:10:00Z"),
                Instant.parse("2026-04-21T11:11:00Z"),
                Map.of()
            );
        }
    }
}
