package com.ghatana.aep.agent.capability;

import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.core.operator.agent.AgentSideEffectProfile;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ExternalAgentCapabilityContractTest extends EventloopTestBase {

    @Test
    void externalProviderRegistersTypedCapabilityMetadataAndReturnsTypedResult() {
        CapabilityId capabilityId = CapabilityId.of("external/helpdesk@1.0.0/capabilities/classify");
        CapabilityDescriptor descriptor = new CapabilityDescriptor(
            capabilityId,
            CapabilityKind.EVENT_OPERATOR,
            "external/helpdesk@1.0.0",
            Optional.empty(),
            "HelpdeskTicket",
            "TicketClassification",
            AgentSideEffectProfile.PURE_INFERENCE,
            List.of("agent.external", "ticket.classify"),
            Map.of("modelPolicy", Map.of("provider", "external-helpdesk")),
            Map.of("providerId", "external-helpdesk"));

        FakeExternalCapabilityRegistry registry = new FakeExternalCapabilityRegistry();
        registry.register(new FakeExternalProvider("external-helpdesk", List.of(descriptor)));

        CapabilityInvocation<Map<String, Object>> invocation = new CapabilityInvocation<>(
            capabilityId,
            AgentContext.builder()
                .tenantId("tenant-a")
                .agentId("external/helpdesk@1.0.0")
                .turnId("turn-1")
                .memoryStore(mock(MemoryStore.class))
                .build(),
            Map.of("subject", "Cannot log in"),
            Map.of("correlationId", "corr-1"));

        FakeRemoteCapabilityInvocationClient client = new FakeRemoteCapabilityInvocationClient();
        CapabilityResult<Map<String, Object>> result = runPromise(() -> client.invoke(
            registry.find(capabilityId).orElseThrow(),
            invocation,
            mapOutputType()));

        assertThat(registry.providers()).containsExactly("external-helpdesk");
        assertThat(registry.list()).containsExactly(descriptor);
        assertThat(registry.providerHealth("external-helpdesk"))
            .isEqualTo(CapabilityProviderHealth.healthy("provider ready"));
        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains(Map.of("category", "account-access", "priority", "p2"));
        assertThat(result.confidence()).isEqualTo(0.91);
        assertThat(result.evidence())
            .containsEntry("capabilityId", capabilityId.value())
            .containsEntry("providerId", "external-helpdesk")
            .containsEntry("correlationId", "corr-1");
    }

    @SuppressWarnings("unchecked")
    private static Class<Map<String, Object>> mapOutputType() {
        return (Class<Map<String, Object>>) (Class<?>) Map.class;
    }

    private record FakeExternalProvider(
        String providerId,
        List<CapabilityDescriptor> capabilities
    ) implements ExternalAgentProvider {
        private FakeExternalProvider {
            capabilities = List.copyOf(capabilities);
        }

        @Override
        public CapabilityProviderHealth health() {
            return CapabilityProviderHealth.healthy("provider ready");
        }
    }

    private static final class FakeExternalCapabilityRegistry implements ExternalAgentCapabilityRegistry {
        private final Map<CapabilityId, CapabilityDescriptor> descriptors = new LinkedHashMap<>();
        private final Map<String, CapabilityProviderHealth> providerHealth = new LinkedHashMap<>();

        @Override
        public void register(ExternalAgentProvider provider) {
            providerHealth.put(provider.providerId(), provider.health());
            for (CapabilityDescriptor descriptor : provider.capabilities()) {
                descriptors.put(descriptor.id(), descriptor);
            }
        }

        @Override
        public Optional<CapabilityDescriptor> find(CapabilityId capabilityId) {
            return Optional.ofNullable(descriptors.get(capabilityId));
        }

        @Override
        public List<CapabilityDescriptor> list() {
            return new ArrayList<>(descriptors.values());
        }

        private List<String> providers() {
            return new ArrayList<>(providerHealth.keySet());
        }

        private CapabilityProviderHealth providerHealth(String providerId) {
            return providerHealth.get(providerId);
        }
    }

    private static final class FakeRemoteCapabilityInvocationClient implements RemoteCapabilityInvocationClient {
        @Override
        public <I, O> Promise<CapabilityResult<O>> invoke(
                CapabilityDescriptor descriptor,
                CapabilityInvocation<I> invocation,
                Class<O> outputType) {
            Map<String, Object> output = Map.of("category", "account-access", "priority", "p2");
            if (!outputType.isInstance(output)) {
                return Promise.of(CapabilityResult.failure(
                    List.of("output type mismatch"),
                    Map.of("capabilityId", descriptor.id().value())));
            }
            return Promise.of(CapabilityResult.success(
                outputType.cast(output),
                0.91,
                Duration.ofMillis(12),
                Map.of(
                    "capabilityId", descriptor.id().value(),
                    "providerId", descriptor.metadata().get("providerId"),
                    "correlationId", invocation.attributes().get("correlationId"))));
        }
    }
}
