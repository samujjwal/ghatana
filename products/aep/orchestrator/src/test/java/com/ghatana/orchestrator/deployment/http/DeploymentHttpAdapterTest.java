package com.ghatana.orchestrator.deployment.http;

import static org.assertj.core.api.Assertions.assertThat;

import com.ghatana.orchestrator.deployment.contract.DeploymentRequest;
import com.ghatana.orchestrator.deployment.contract.DeploymentResponse;
import com.ghatana.orchestrator.deployment.service.DeploymentEventPublisher;
import com.ghatana.orchestrator.deployment.service.DeploymentOrchestrator;
import com.ghatana.platform.observability.NoopMetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DeploymentHttpAdapter}.
 *
 * <p>Extends {@link EventloopTestBase} because the underlying publisher returns
 * ActiveJ {@link Promise} objects that must be resolved in an event-loop context.
 */
@DisplayName("DeploymentHttpAdapter")
class DeploymentHttpAdapterTest extends EventloopTestBase {

    @Test
    void handleDeploymentRequestUsesOrchestratorAndPublishesEvent() {
        RecordingDeploymentEventPublisher publisher = new RecordingDeploymentEventPublisher();
        DeploymentOrchestrator orchestrator = new DeploymentOrchestrator(publisher, new NoopMetricsCollector());
        DeploymentHttpAdapter adapter = new DeploymentHttpAdapter(orchestrator);

        DeploymentRequest request = DeploymentRequest.builder()
                .pipelineId("pipeline-a")
                .tenantId("tenant-a")
                .environment("prod")
                .build();

        DeploymentResponse response = adapter.handleDeploymentRequest(request);

        assertThat(response.getStatus()).isEqualTo("DEPLOYED");
        assertThat(response.getPipelineId()).isEqualTo("pipeline-a");
        assertThat(response.getTenantId()).isEqualTo("tenant-a");
        assertThat(response.getDeploymentId()).isNotBlank();
        assertThat(publisher.events).hasSize(1);
        assertThat(publisher.events.getFirst().eventType).isEqualTo("pipeline.deploy.requested");
    }

    @Test
    void handleUpdateRequestUsesOrchestratorAndPublishesEvent() {
        RecordingDeploymentEventPublisher publisher = new RecordingDeploymentEventPublisher();
        DeploymentOrchestrator orchestrator = new DeploymentOrchestrator(publisher, new NoopMetricsCollector());
        DeploymentHttpAdapter adapter = new DeploymentHttpAdapter(orchestrator);

        DeploymentRequest request = DeploymentRequest.builder()
                .pipelineId("pipeline-b")
                .tenantId("tenant-b")
                .environment("prod")
                .build();

        DeploymentResponse response = adapter.handleUpdateRequest("deploy-123", request);

        assertThat(response.getStatus()).isEqualTo("UPDATED");
        assertThat(response.getDeploymentId()).isEqualTo("deploy-123");
        assertThat(publisher.events).hasSize(1);
        assertThat(publisher.events.getFirst().eventType).isEqualTo("pipeline.update.requested");
    }

    @Test
    void handleUndeployRequestUsesOrchestratorAndPublishesEvent() {
        RecordingDeploymentEventPublisher publisher = new RecordingDeploymentEventPublisher();
        DeploymentOrchestrator orchestrator = new DeploymentOrchestrator(publisher, new NoopMetricsCollector());
        DeploymentHttpAdapter adapter = new DeploymentHttpAdapter(orchestrator);

        DeploymentResponse response = adapter.handleUndeployRequest("deploy-456", "tenant-c");

        assertThat(response.getStatus()).isEqualTo("UNDEPLOYED");
        assertThat(response.getDeploymentId()).isEqualTo("deploy-456");
        assertThat(response.getTenantId()).isEqualTo("tenant-c");
        assertThat(publisher.events).hasSize(1);
        assertThat(publisher.events.getFirst().eventType).isEqualTo("pipeline.undeploy.requested");
    }

    private static final class RecordingDeploymentEventPublisher implements DeploymentEventPublisher {
        private final List<PublishedEvent> events = new ArrayList<>();

        @Override
        public Promise<Void> publishDeploymentEvent(String eventType, String deploymentId, DeploymentRequest request) {
            events.add(new PublishedEvent(eventType, deploymentId, request));
            return Promise.complete();
        }
    }

    private record PublishedEvent(String eventType, String deploymentId, DeploymentRequest request) {}
}
