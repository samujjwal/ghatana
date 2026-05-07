package com.ghatana.yappc.api;

import com.ghatana.yappc.common.JsonMapper;
import com.ghatana.yappc.domain.run.ObservationConfig;
import com.ghatana.yappc.domain.run.RunResult;
import com.ghatana.yappc.domain.run.RunSpec;
import com.ghatana.yappc.domain.run.RunStatus;
import com.ghatana.yappc.services.run.RunService;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.bytebuf.ByteBuf;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
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
    private RunApiController controller;

    @BeforeEach
    void setUp() {
        runService = new InMemoryRunService();
        controller = new RunApiController(runService);
    }

    @Test
    @DisplayName("rollback requires deployment and target version")
    void rollbackRequiresDeploymentScope() throws Exception {
        HttpResponse response = runPromise(() -> controller.rollback(post("/api/v1/yappc/run/rollback", Map.of("deploymentId", "deploy-1"))));

        assertThat(response.getCode()).isEqualTo(400);
        assertThat(runService.rollbackCallCount()).isEqualTo(0);
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
    }

    private HttpRequest post(String path, Object body) throws Exception {
        return HttpRequest.post("http://localhost" + path)
            .withBody(ByteBuf.wrapForReading(JsonMapper.toJson(body).getBytes(StandardCharsets.UTF_8)))
            .build();
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
