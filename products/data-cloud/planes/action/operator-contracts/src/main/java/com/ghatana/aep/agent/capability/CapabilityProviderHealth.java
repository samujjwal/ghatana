package com.ghatana.aep.agent.capability;

import java.util.List;
import java.util.Objects;

/**
 * @doc.type contract
 * @doc.purpose Reports runtime availability for external agent capability providers
 * @doc.layer product
 * @doc.pattern Runtime truth
 */
public record CapabilityProviderHealth(
        Status status,
        List<String> reasonCodes,
        String message) {

    public enum Status {
        HEALTHY,
        DEGRADED,
        UNAVAILABLE
    }

    public CapabilityProviderHealth {
        Objects.requireNonNull(status, "status is required");
        reasonCodes = List.copyOf(Objects.requireNonNull(reasonCodes, "reasonCodes are required"));
        message = Objects.requireNonNull(message, "message is required");
        if (message.isBlank()) {
            throw new IllegalArgumentException("message is required");
        }
        if (status != Status.HEALTHY && reasonCodes.isEmpty()) {
            throw new IllegalArgumentException("non-healthy provider health requires at least one reason code");
        }
    }

    public static CapabilityProviderHealth healthy(String message) {
        return new CapabilityProviderHealth(Status.HEALTHY, List.of(), message);
    }

    public static CapabilityProviderHealth degraded(String reasonCode, String message) {
        return new CapabilityProviderHealth(Status.DEGRADED, List.of(reasonCode), message);
    }

    public static CapabilityProviderHealth unavailable(String reasonCode, String message) {
        return new CapabilityProviderHealth(Status.UNAVAILABLE, List.of(reasonCode), message);
    }
}
