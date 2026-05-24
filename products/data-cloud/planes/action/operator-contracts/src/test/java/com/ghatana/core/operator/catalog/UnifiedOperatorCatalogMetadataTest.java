package com.ghatana.core.operator.catalog;

import com.ghatana.aep.agent.capability.CapabilityDescriptor;
import com.ghatana.aep.agent.capability.CapabilityId;
import com.ghatana.aep.agent.capability.CapabilityInvocation;
import com.ghatana.aep.agent.capability.CapabilityKind;
import com.ghatana.aep.agent.capability.CapabilityResult;
import com.ghatana.aep.agent.capability.EventOperatorCapability;
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
import com.ghatana.core.operator.agent.AgentCapabilityRole;
import com.ghatana.core.operator.agent.AgentSideEffectProfile;
import com.ghatana.platform.domain.event.Event;
import io.activej.promise.Promise;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UnifiedOperatorCatalogMetadataTest {

    @Test
    void indexesAgentCapabilityMetadataForGovernanceQueries() {
        UnifiedOperatorCatalog catalog = new UnifiedOperatorCatalog();
        StubAgentCapability operator = new StubAgentCapability();

        catalog.register(operator);

        List<OperatorCatalogEntry> entries = catalog.search(OperatorCatalogQuery.agentKind(
            AgentCapabilityRole.AGENT_ACTION));
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).sideEffectProfile())
            .contains(AgentSideEffectProfile.SIDE_EFFECTING);
        assertThat(entries.get(0).outputSchema()).isEqualTo("ActionResult");
        assertThat(entries.get(0).replayProfile()).isEqualTo("recorded_output");
    }

    @Test
    void searchFiltersBySideEffectProfileAndCapability() {
        UnifiedOperatorCatalog catalog = new UnifiedOperatorCatalog();
        catalog.register(new StubAgentCapability());

        List<OperatorCatalogEntry> entries = catalog.search(new OperatorCatalogQuery(
            Optional.of(OperatorType.AGENT),
            Optional.empty(),
            Optional.of(AgentSideEffectProfile.SIDE_EFFECTING),
            Optional.of("agent.action")));

        assertThat(entries).hasSize(1);
    }

    @Test
    void requireApprovedReturnsApprovedOperatorMetadata() {
        UnifiedOperatorCatalog catalog = new UnifiedOperatorCatalog();
        StubAgentCapability operator =
            new StubAgentCapability(Map.of("approvalStatus", "approved", "owner", "sre-platform"));
        catalog.register(operator);

        OperatorCatalogEntry entry = catalog.requireApproved(operator.getId());

        assertThat(entry.operatorId()).isEqualTo(operator.getId());
        assertThat(entry.metadata())
            .containsEntry("approvalStatus", "approved")
            .containsEntry("toolPolicyDeclared", "true");
    }

    @Test
    void requireApprovedRejectsUnknownAndUnapprovedOperators() {
        UnifiedOperatorCatalog catalog = new UnifiedOperatorCatalog();
        StubAgentCapability operator = new StubAgentCapability();
        catalog.register(operator);

        assertThatThrownBy(() -> catalog.requireApproved(OperatorId.of("tenant-a", "agent", "missing", "1.0.0")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Unknown operator");
        assertThatThrownBy(() -> catalog.requireApproved(operator.getId()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("not approved");
    }

    private static final class StubAgentCapability
            implements EventOperatorCapability<Map<String, Object>, Map<String, Object>>,
            com.ghatana.core.operator.UnifiedOperator {

        private final OperatorId id = OperatorId.of("tenant-a", "agent", "incident-action", "1.0.0");
        private final Map<String, String> metadata;

        private StubAgentCapability() {
            this(Map.of("owner", "sre-platform"));
        }

        private StubAgentCapability(Map<String, String> metadata) {
            this.metadata = metadata;
        }

        @Override
        public CapabilityId capabilityId() {
            return CapabilityId.of("agents/incident-action@1.0.0/capabilities/action");
        }

        @Override
        public CapabilityDescriptor descriptor() {
            return new CapabilityDescriptor(
                capabilityId(),
                CapabilityKind.EVENT_OPERATOR,
                "agents/incident-action@1.0.0",
                Optional.empty(),
                "ActionRequest",
                "ActionResult",
                AgentSideEffectProfile.SIDE_EFFECTING,
                List.of("agent.action"),
                Map.of(
                    "modelPolicy", Map.of("model", "gpt-5.1"),
                    "toolPolicy", Map.of("allowedTools", List.of("pagerduty.incident.create")),
                    "replayPolicy", Map.of("mode", "recorded_output"),
                    "uncertaintyPolicy", Map.of("minConfidence", 0.8),
                    "humanReviewPolicy", Map.of("approvalPolicy", "human_required"),
                    "observabilityPolicy", Map.of("metrics", true, "tracing", true)),
                metadata);
        }

        @Override
        public Promise<CapabilityResult<EventOperatorResult<Map<String, Object>>>> invoke(
                CapabilityInvocation<EventContext<Map<String, Object>>> invocation) {
            return process(invocation.input(), null)
                .map(result -> CapabilityResult.success(result, 1.0, java.time.Duration.ZERO, result.evidence()));
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
            return metadata;
        }
    }
}
