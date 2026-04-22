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
@DisplayName("DeploymentHttpAdapter [GH-90000]")
class DeploymentHttpAdapterTest extends EventloopTestBase {

    @Test
    void handleDeploymentRequestUsesOrchestratorAndPublishesEvent() { // GH-90000
        RecordingDeploymentEventPublisher publisher = new RecordingDeploymentEventPublisher(); // GH-90000
        DeploymentOrchestrator orchestrator = new DeploymentOrchestrator(publisher, new NoopMetricsCollector()); // GH-90000
        DeploymentHttpAdapter adapter = new DeploymentHttpAdapter(orchestrator); // GH-90000

        DeploymentRequest request = DeploymentRequest.builder() // GH-90000
                .pipelineId("pipeline-a [GH-90000]")
                .tenantId("tenant-a [GH-90000]")
                .environment("prod [GH-90000]")
                .build(); // GH-90000

        DeploymentResponse response = runPromise(() -> adapter.handleDeploymentRequest(request)); // GH-90000

        assertThat(response.getStatus()).isEqualTo("DEPLOYED [GH-90000]");
        assertThat(response.getPipelineId()).isEqualTo("pipeline-a [GH-90000]");
        assertThat(response.getTenantId()).isEqualTo("tenant-a [GH-90000]");
        assertThat(response.getDeploymentId()).isNotBlank(); // GH-90000
        assertThat(publisher.events).hasSize(1); // GH-90000
        assertThat(publisher.events.getFirst().eventType).isEqualTo("pipeline.deploy.requested [GH-90000]");
    }

    @Test
    void handleUpdateRequestUsesOrchestratorAndPublishesEvent() { // GH-90000
        RecordingDeploymentEventPublisher publisher = new RecordingDeploymentEventPublisher(); // GH-90000
        DeploymentOrchestrator orchestrator = new DeploymentOrchestrator(publisher, new NoopMetricsCollector()); // GH-90000
        DeploymentHttpAdapter adapter = new DeploymentHttpAdapter(orchestrator); // GH-90000

        DeploymentRequest request = DeploymentRequest.builder() // GH-90000
                .pipelineId("pipeline-b [GH-90000]")
                .tenantId("tenant-b [GH-90000]")
                .environment("prod [GH-90000]")
                .build(); // GH-90000

        DeploymentResponse response = runPromise(() -> adapter.handleUpdateRequest("deploy-123", request)); // GH-90000

        assertThat(response.getStatus()).isEqualTo("UPDATED [GH-90000]");
        assertThat(response.getDeploymentId()).isEqualTo("deploy-123 [GH-90000]");
        assertThat(publisher.events).hasSize(1); // GH-90000
        assertThat(publisher.events.getFirst().eventType).isEqualTo("pipeline.update.requested [GH-90000]");
    }

    @Test
    void handleUndeployRequestUsesOrchestratorAndPublishesEvent() { // GH-90000
        RecordingDeploymentEventPublisher publisher = new RecordingDeploymentEventPublisher(); // GH-90000
        DeploymentOrchestrator orchestrator = new DeploymentOrchestrator(publisher, new NoopMetricsCollector()); // GH-90000
        DeploymentHttpAdapter adapter = new DeploymentHttpAdapter(orchestrator); // GH-90000

        DeploymentResponse response = runPromise(() -> adapter.handleUndeployRequest("deploy-456", "tenant-c")); // GH-90000

        assertThat(response.getStatus()).isEqualTo("UNDEPLOYED [GH-90000]");
        assertThat(response.getDeploymentId()).isEqualTo("deploy-456 [GH-90000]");
        assertThat(response.getTenantId()).isEqualTo("tenant-c [GH-90000]");
        assertThat(publisher.events).hasSize(1); // GH-90000
        assertThat(publisher.events.getFirst().eventType).isEqualTo("pipeline.undeploy.requested [GH-90000]");
    }

    private static final class RecordingDeploymentEventPublisher implements DeploymentEventPublisher {
        private final List<PublishedEvent> events = new ArrayList<>(); // GH-90000

        @Override
        public Promise<Void> publishDeploymentEvent(String eventType, String deploymentId, DeploymentRequest request) { // GH-90000
            events.add(new PublishedEvent(eventType, deploymentId, request)); // GH-90000
            return Promise.complete(); // GH-90000
        }
    }

    private record PublishedEvent(String eventType, String deploymentId, DeploymentRequest request) {} // GH-90000
}
