package com.ghatana.core.pipeline;

import com.ghatana.core.operator.OperatorConfig;
import com.ghatana.core.operator.OperatorId;
import com.ghatana.core.operator.OperatorResult;
import com.ghatana.core.operator.OperatorState;
import com.ghatana.core.operator.OperatorType;
import com.ghatana.core.operator.UnifiedOperator;
import com.ghatana.core.operator.catalog.UnifiedOperatorCatalog;
import com.ghatana.platform.domain.event.Event;
import io.activej.promise.Promise;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PipelineStageExecutorAdmissionTest {

    @Test
    void rejectsUnapprovedOperatorBeforeRuntimeProcessing() {
        UnifiedOperatorCatalog catalog = new UnifiedOperatorCatalog();
        StubOperator operator = new StubOperator(Map.of("owner", "platform"));
        catalog.register(operator);
        PipelineStageExecutor executor = new PipelineStageExecutor();

        StageExecutionResult result = executor.executeSingleStage(
            "stage-1",
            PipelineStage.of("stage-1", operator.getId()),
            List.of(inputEvent()),
            context(catalog)).getResult();

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("Operator admission rejected");
        assertThat(operator.invoked).isFalse();
    }

    @Test
    void allowsApprovedOperatorToRun() {
        UnifiedOperatorCatalog catalog = new UnifiedOperatorCatalog();
        StubOperator operator = new StubOperator(Map.of("owner", "platform", "approvalStatus", "approved"));
        catalog.register(operator);
        PipelineStageExecutor executor = new PipelineStageExecutor();

        StageExecutionResult result = executor.executeSingleStage(
            "stage-1",
            PipelineStage.of("stage-1", operator.getId()),
            List.of(inputEvent()),
            context(catalog)).getResult();

        assertThat(result.success()).isTrue();
        assertThat(operator.invoked).isTrue();
    }

    private static PipelineExecutionContext context(UnifiedOperatorCatalog catalog) {
        return PipelineExecutionContext.builder()
            .pipelineId("pipeline-1")
            .tenantId("tenant-a")
            .operatorCatalog(catalog)
            .deadline(Duration.ofSeconds(5))
            .build();
    }

    private static Event inputEvent() {
        return Event.builder()
            .typeTenantVersion("tenant-a", "deploy.started", "v1")
            .addPayload("service", "checkout")
            .build();
    }

    private static final class StubOperator implements UnifiedOperator {

        private final OperatorId id = OperatorId.of("tenant-a", "stream", "approved-filter", "1.0.0");
        private final Map<String, String> metadata;
        private boolean invoked;

        private StubOperator(Map<String, String> metadata) {
            this.metadata = metadata;
        }

        @Override
        public OperatorId getId() {
            return id;
        }

        @Override
        public String getName() {
            return "Approved filter";
        }

        @Override
        public OperatorType getType() {
            return OperatorType.STREAM;
        }

        @Override
        public String getVersion() {
            return "1.0.0";
        }

        @Override
        public String getDescription() {
            return "Test operator";
        }

        @Override
        public List<String> getCapabilities() {
            return List.of("stream.filter");
        }

        @Override
        public Promise<OperatorResult> process(Event event) {
            invoked = true;
            return Promise.of(OperatorResult.empty());
        }

        @Override
        public Promise<Void> initialize(OperatorConfig config) {
            return Promise.complete();
        }

        @Override
        public Promise<Void> start() {
            return Promise.complete();
        }

        @Override
        public Promise<Void> stop() {
            return Promise.complete();
        }

        @Override
        public boolean isHealthy() {
            return true;
        }

        @Override
        public OperatorState getState() {
            return OperatorState.RUNNING;
        }

        @Override
        public Event toEvent() {
            return null;
        }

        @Override
        public Map<String, Object> getMetrics() {
            return Map.of();
        }

        @Override
        public Map<String, Object> getInternalState() {
            return Map.of();
        }

        @Override
        public OperatorConfig getConfig() {
            return OperatorConfig.empty();
        }

        @Override
        public Map<String, String> getMetadata() {
            return metadata;
        }
    }
}
