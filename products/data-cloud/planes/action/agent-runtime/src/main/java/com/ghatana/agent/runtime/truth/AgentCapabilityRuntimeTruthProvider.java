package com.ghatana.agent.runtime.truth;

import com.ghatana.aep.agent.capability.CapabilityProviderHealth;
import com.ghatana.aep.agent.capability.ExternalAgentCapabilityRegistry;
import com.ghatana.aep.agent.capability.ExternalAgentProvider;
import com.ghatana.aep.agent.capability.RemoteCapabilityInvocationClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @doc.type provider
 * @doc.purpose Reports runtime truth for external agent capability dependencies
 * @doc.layer product
 * @doc.pattern Runtime truth
 */
public final class AgentCapabilityRuntimeTruthProvider {

    private final ExternalAgentCapabilityRegistry registry;
    private final RemoteCapabilityInvocationClient invocationClient;
    private final List<ExternalAgentProvider> providers;

    public AgentCapabilityRuntimeTruthProvider(
            ExternalAgentCapabilityRegistry registry,
            RemoteCapabilityInvocationClient invocationClient,
            List<ExternalAgentProvider> providers) {
        this.registry = registry;
        this.invocationClient = invocationClient;
        this.providers = providers == null ? List.of() : List.copyOf(providers);
    }

    public RuntimeTruthSnapshot snapshot() {
        List<ProviderTruth> providerTruth = new ArrayList<>();
        List<String> reasons = new ArrayList<>();
        Status status = Status.HEALTHY;

        if (registry == null) {
            status = Status.UNAVAILABLE;
            reasons.add("external-capability-registry-unavailable");
        }
        if (invocationClient == null) {
            status = Status.UNAVAILABLE;
            reasons.add("remote-capability-invocation-client-unavailable");
        }
        if (providers.isEmpty()) {
            status = worst(status, Status.DEGRADED);
            reasons.add("external-capability-provider-empty");
        }

        for (ExternalAgentProvider provider : providers) {
            CapabilityProviderHealth health = provider.health();
            providerTruth.add(new ProviderTruth(provider.providerId(), health.status(), health.reasonCodes(), health.message()));
            if (health.status() == CapabilityProviderHealth.Status.UNAVAILABLE) {
                status = Status.UNAVAILABLE;
                reasons.add("external-capability-provider-unavailable:" + provider.providerId());
            } else if (health.status() == CapabilityProviderHealth.Status.DEGRADED) {
                status = worst(status, Status.DEGRADED);
                reasons.add("external-capability-provider-degraded:" + provider.providerId());
            }
        }

        return new RuntimeTruthSnapshot(
            Instant.now(),
            status,
            List.copyOf(reasons),
            List.copyOf(providerTruth));
    }

    private static Status worst(Status current, Status candidate) {
        if (current == Status.UNAVAILABLE || candidate == Status.UNAVAILABLE) {
            return Status.UNAVAILABLE;
        }
        if (current == Status.DEGRADED || candidate == Status.DEGRADED) {
            return Status.DEGRADED;
        }
        return Status.HEALTHY;
    }

    public enum Status {
        HEALTHY,
        DEGRADED,
        UNAVAILABLE
    }

    public record ProviderTruth(
            String providerId,
            CapabilityProviderHealth.Status status,
            List<String> reasonCodes,
            String message) {
        public ProviderTruth {
            Objects.requireNonNull(providerId, "providerId is required");
            Objects.requireNonNull(status, "status is required");
            reasonCodes = List.copyOf(Objects.requireNonNull(reasonCodes, "reasonCodes are required"));
            message = Objects.requireNonNull(message, "message is required");
        }
    }

    public record RuntimeTruthSnapshot(
            Instant capturedAt,
            Status status,
            List<String> reasonCodes,
            List<ProviderTruth> providers) {
        public RuntimeTruthSnapshot {
            Objects.requireNonNull(capturedAt, "capturedAt is required");
            Objects.requireNonNull(status, "status is required");
            reasonCodes = List.copyOf(Objects.requireNonNull(reasonCodes, "reasonCodes are required"));
            providers = List.copyOf(Objects.requireNonNull(providers, "providers are required"));
        }
    }
}
