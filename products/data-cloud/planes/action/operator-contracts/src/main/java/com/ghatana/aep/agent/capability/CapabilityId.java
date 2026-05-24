package com.ghatana.aep.agent.capability;

import java.util.Objects;

/**
 * @doc.type record
 * @doc.purpose Identifies an agent capability independently from the hosting agent
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record CapabilityId(String value) {

    public CapabilityId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("capability id must not be blank");
        }
        value = value.trim();
    }

    public static CapabilityId of(String value) {
        return new CapabilityId(Objects.requireNonNull(value, "value"));
    }
}
