package com.ghatana.agent.runtime.truth;

import com.ghatana.aep.agent.capability.CapabilityDescriptor;
import com.ghatana.aep.agent.capability.CapabilityProviderHealth;
import com.ghatana.aep.agent.capability.ExternalAgentCapabilityRegistry;
import com.ghatana.aep.agent.capability.ExternalAgentProvider;
import com.ghatana.aep.agent.capability.RemoteCapabilityInvocationClient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AgentCapabilityRuntimeTruthProviderTest {

    @Test
    void reportsHealthyWhenRegistryClientAndProvidersAreAvailable() {
        AgentCapabilityRuntimeTruthProvider truthProvider = new AgentCapabilityRuntimeTruthProvider(
            mock(ExternalAgentCapabilityRegistry.class),
            mock(RemoteCapabilityInvocationClient.class),
            List.of(new FakeProvider("provider-a", CapabilityProviderHealth.healthy("ready"))));

        AgentCapabilityRuntimeTruthProvider.RuntimeTruthSnapshot snapshot = truthProvider.snapshot();

        assertThat(snapshot.status()).isEqualTo(AgentCapabilityRuntimeTruthProvider.Status.HEALTHY);
        assertThat(snapshot.reasonCodes()).isEmpty();
        assertThat(snapshot.providers()).singleElement()
            .satisfies(provider -> assertThat(provider.providerId()).isEqualTo("provider-a"));
    }

    @Test
    void reportsUnavailableWhenRegistryOrRemoteClientIsMissing() {
        AgentCapabilityRuntimeTruthProvider truthProvider = new AgentCapabilityRuntimeTruthProvider(
            null,
            null,
            List.of(new FakeProvider("provider-a", CapabilityProviderHealth.healthy("ready"))));

        AgentCapabilityRuntimeTruthProvider.RuntimeTruthSnapshot snapshot = truthProvider.snapshot();

        assertThat(snapshot.status()).isEqualTo(AgentCapabilityRuntimeTruthProvider.Status.UNAVAILABLE);
        assertThat(snapshot.reasonCodes())
            .contains(
                "external-capability-registry-unavailable",
                "remote-capability-invocation-client-unavailable");
    }

    @Test
    void reportsProviderDegradedAndUnavailableStates() {
        AgentCapabilityRuntimeTruthProvider truthProvider = new AgentCapabilityRuntimeTruthProvider(
            mock(ExternalAgentCapabilityRegistry.class),
            mock(RemoteCapabilityInvocationClient.class),
            List.of(
                new FakeProvider("provider-a", CapabilityProviderHealth.degraded("model-registry-lagging", "model registry lagging")),
                new FakeProvider("provider-b", CapabilityProviderHealth.unavailable("provider-timeout", "provider timed out"))));

        AgentCapabilityRuntimeTruthProvider.RuntimeTruthSnapshot snapshot = truthProvider.snapshot();

        assertThat(snapshot.status()).isEqualTo(AgentCapabilityRuntimeTruthProvider.Status.UNAVAILABLE);
        assertThat(snapshot.reasonCodes())
            .contains(
                "external-capability-provider-degraded:provider-a",
                "external-capability-provider-unavailable:provider-b");
    }

    private record FakeProvider(
            String providerId,
            CapabilityProviderHealth health
    ) implements ExternalAgentProvider {
        @Override
        public List<CapabilityDescriptor> capabilities() {
            return List.of();
        }
    }
}
