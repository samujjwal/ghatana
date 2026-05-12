/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.mastery;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Binding configuration for mastery registry integration.
 *
 * @doc.type record
 * @doc.purpose Binding configuration for mastery registry
 * @doc.layer agent-core
 * @doc.pattern Record
 */
public record MasteryBinding(
        @NotNull String namespace,
        @NotNull String registryRef,
        @Nullable String freshnessPolicyRef,
        @Nullable String versionCompatibilityPolicyRef,
        @Nullable String obsolescencePolicyRef
) {
    public MasteryBinding {
        Objects.requireNonNull(namespace, "namespace must not be null");
        Objects.requireNonNull(registryRef, "registryRef must not be null");
        if (namespace.isBlank()) {
            throw new IllegalArgumentException("namespace must not be blank");
        }
        if (registryRef.isBlank()) {
            throw new IllegalArgumentException("registryRef must not be blank");
        }
    }

    /**
     * Creates a mastery binding with only required fields.
     *
     * @param namespace namespace for the mastery registry
     * @param registryRef reference to the mastery registry
     * @return mastery binding
     */
    @NotNull
    public static MasteryBinding of(
            @NotNull String namespace,
            @NotNull String registryRef
    ) {
        return new MasteryBinding(namespace, registryRef, null, null, null);
    }

    /**
     * Creates a mastery binding with all policy references.
     *
     * @param namespace namespace for the mastery registry
     * @param registryRef reference to the mastery registry
     * @param freshnessPolicyRef reference to freshness policy
     * @param versionCompatibilityPolicyRef reference to version compatibility policy
     * @param obsolescencePolicyRef reference to obsolescence policy
     * @return mastery binding
     */
    @NotNull
    public static MasteryBinding of(
            @NotNull String namespace,
            @NotNull String registryRef,
            @Nullable String freshnessPolicyRef,
            @Nullable String versionCompatibilityPolicyRef,
            @Nullable String obsolescencePolicyRef
    ) {
        return new MasteryBinding(namespace, registryRef, freshnessPolicyRef, versionCompatibilityPolicyRef, obsolescencePolicyRef);
    }
}
