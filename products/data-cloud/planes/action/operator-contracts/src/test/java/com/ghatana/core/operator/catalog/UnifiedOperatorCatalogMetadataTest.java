package com.ghatana.core.operator.catalog;

import com.ghatana.aep.model.EventContext;
import com.ghatana.aep.operator.contract.CompileContext;
import com.ghatana.aep.operator.contract.EventOperatorResult;
import com.ghatana.aep.operator.contract.OperatorKind;
import com.ghatana.aep.operator.contract.OperatorRuntimeContext;
import com.ghatana.aep.operator.contract.OperatorSpec;
import com.ghatana.aep.operator.contract.OperatorVersion;
import com.ghatana.aep.operator.contract.RuntimePlan;
import com.ghatana.aep.operator.contract.ValidationContext;
import com.ghatana.aep.operator.contract.ValidationResult;
import com.ghatana.core.operator.OperatorConfig;
import com.ghatana.core.operator.OperatorId;
import com.ghatana.core.operator.OperatorResult;
import com.ghatana.core.operator.OperatorState;
import com.ghatana.core.operator.OperatorType;
import com.ghatana.core.operator.agent.AgentOperator;
import com.ghatana.core.operator.agent.AgentOperatorKind;
import com.ghatana.core.operator.agent.AgentSideEffectProfile;
import com.ghatana.platform.domain.event.Event;
import io.activej.promise.Promise;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class UnifiedOperatorCatalogMetadataTest {

    @Test
    void indexesAgentOperatorMetadataForGovernanceQueries() {
        UnifiedOperatorCatalog catalog = new UnifiedOperatorCatalog();
        AgentOperator operator = new StubAgentOperator();

        catalog.register(operator);

        List<OperatorCatalogEntry> entries = catalog.search(OperatorCatalogQuery.agentKind(
            AgentOperatorKind.AGENT_ACTION));
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).sideEffectProfile())
            .contains(AgentSideEffectProfile.SIDE_EFFECTING);
        assertThat(entries.get(0).outputSchema()).isEqualTo("ActionResult");
        assertThat(entries.get(0).replayProfile()).isEqualTo("recorded_output");
    }

    @Test
    void searchFiltersBySideEffectProfileAndCapability() {
        UnifiedOperatorCatalog catalog = new UnifiedOperatorCatalog();
        catalog.register(new StubAgentOperator());

        List<OperatorCatalogEntry> entries = catalog.search(new OperatorCatalogQuery(
            Optional.of(OperatorType.AGENT),
            Optional.empty(),
            Optional.of(AgentSideEffectProfile.SIDE_EFFECTING),
            Optional.of("agent.action")));

        assertThat(entries).hasSize(1);
    }

    private static final class StubAgentOperator implements AgentOperator {

        private final OperatorId id = OperatorId.of("tenant-a", "agent", "incident-action", "1.0.0");

        @Override
        public String agentRef() {
            return "agents/incident-action@1.0.0";
        }

        @Override
        public AgentOperatorKind agentOperatorKind() {
            return AgentOperatorKind.AGENT_ACTION;
        }

        @Override
        public AgentSideEffectProfile sideEffectProfile() {
            return AgentSideEffectProfile.SIDE_EFFECTING;
        }

        @Override
        public String inputSchema() {
            return "ActionRequest";
        }

        @Override
        public String outputSchema() {
            return "ActionResult";
        }

        @Override
        public Map<String, Object> modelPolicy() {
            return Map.of("model", "gpt-5.1");
        }

        @Override
        public Map<String, Object> toolPolicy() {
            return Map.of("allowedTools", List.of("pagerduty.incident.create"));
        }

        @Override
        public Map<String, Object> memoryPolicy() {
            return Map.of();
        }

        @Override
        public Map<String, Object> retrievalPolicy() {
            return Map.of();
        }

        @Override
        public Map<String, Object> guardrailPolicy() {
            return Map.of();
        }

        @Override
        public Map<String, Object> replayPolicy() {
            return Map.of("mode", "recorded_output");
        }

        @Override
        public Map<String, Object> uncertaintyPolicy() {
            return Map.of("minConfidence", 0.8);
        }

        @Override
        public Map<String, Object> humanReviewPolicy() {
            return Map.of("approvalPolicy", "human_required");
        }

        @Override
        public Map<String, Object> observabilityPolicy() {
            return Map.of("metrics", true, "tracing", true);
        }

        @Override
        public OperatorId id() {
            return id;
        }

        @Override
        public OperatorKind kind() {
            return OperatorKind.AGENT_ACTION;
        }

        @Override
        public OperatorVersion version() {
            return new OperatorVersion("1.0.0");
        }

        @Override
        public ValidationResult validate(OperatorSpec spec, ValidationContext ctx) {
            return ValidationResult.ok();
        }

        @Override
        public RuntimePlan compile(OperatorSpec spec, CompileContext ctx) {
            return new RuntimePlan("plan-1", List.of(id.toString()), Map.of(), Map.of());
        }

        @Override
        public Promise<EventOperatorResult<Map<String, Object>>> process(
                EventContext<Map<String, Object>> input,
                OperatorRuntimeContext ctx) {
            return Promise.of(EventOperatorResult.success(Map.of("status", "APPROVED"), input.uncertainty()));
        }

        @Override
        public OperatorId getId() {
            return id;
        }

        @Override
        public String getName() {
            return "Incident action";
        }

        @Override
        public OperatorType getType() {
            return OperatorType.AGENT;
        }

        @Override
        public String getVersion() {
            return "1.0.0";
        }

        @Override
        public String getDescription() {
            return "Executes an approved incident action.";
        }

        @Override
        public List<String> getCapabilities() {
            return List.of("agent.action", "tool.mutating");
        }

        @Override
        public Promise<OperatorResult> process(Event event) {
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
            return Map.of("owner", "sre-platform");
        }
    }
}
