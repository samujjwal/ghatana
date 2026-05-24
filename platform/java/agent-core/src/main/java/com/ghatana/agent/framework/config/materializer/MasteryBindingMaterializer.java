/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.config.materializer;

import com.ghatana.agent.framework.config.AgentDefinition;
import com.ghatana.agent.mastery.MasteryBinding;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Materializes {@link MasteryBinding} instances from an {@link AgentDefinition}.
 *
 * <p>This class is responsible for converting the mastery bindings map from
 * the agent definition into typed MasteryBinding objects.
 *
 * @doc.type class
 * @doc.purpose Materializes MasteryBinding from AgentDefinition
 * @doc.layer platform
 * @doc.pattern Materializer
 */
public final class MasteryBindingMaterializer {

    private MasteryBindingMaterializer() {
        // Utility class - prevent instantiation
    }

    /**
     * Materializes typed MasteryBindings from an agent definition's mastery bindings.
     *
     * <p>Supports multiple mastery bindings for agents that need to interact with
     * multiple mastery registries or have multiple binding configurations.
     *
     * @param definition the agent definition
     * @return list of typed MasteryBindings (empty if not configured)
     */
    @NotNull
    public static List<MasteryBinding> materialize(@NotNull AgentDefinition definition) {
        Map<String, Object> masteryBindings = definition.getMasteryBindings();
        if (masteryBindings.isEmpty()) {
            return List.of();
        }

        // Check if masteryBindings is a list (multiple bindings) or a map (single binding)
        if (masteryBindings.containsKey("namespace")) {
            // Single binding as a map - backward compatible format
            return List.of(parseSingleBinding(masteryBindings));
        }

        // Multiple bindings as a list
        Object bindingsListObj = definition.getMetadata().get("masteryBindingsList");
        if (bindingsListObj instanceof List<?> bindingsList) {
            return bindingsList.stream()
                    .map(MasteryBindingMaterializer::asStringObjectMap)
                    .map(MasteryBindingMaterializer::parseSingleBinding)
                    .toList();
        }

        return List.of();
    }

    private static Map<String, Object> asStringObjectMap(Object value) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            throw new IllegalStateException("Each mastery binding entry must be a map");
        }

        return rawMap.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        entry -> {
                            if (!(entry.getKey() instanceof String key)) {
                                throw new IllegalStateException("Mastery binding keys must be strings");
                            }
                            return key;
                        },
                        Map.Entry::getValue));
    }

    /**
     * Materializes a single typed MasteryBinding from an agent definition's mastery bindings.
     *
     * <p>Legacy method for backward compatibility. Returns the first binding if multiple exist.
     *
     * @param definition the agent definition
     * @return typed MasteryBinding
     * @throws IllegalStateException if required mastery binding fields are missing
     * @deprecated Use {@link #materialize(AgentDefinition)} to get all bindings
     */
    @Deprecated
    @NotNull
    public static MasteryBinding materializeSingle(@NotNull AgentDefinition definition) {
        List<MasteryBinding> bindings = materialize(definition);
        if (bindings.isEmpty()) {
            throw new IllegalStateException("Mastery bindings are not configured for this agent");
        }
        return bindings.get(0);
    }

    /**
     * Parses a single mastery binding from a map.
     *
     * @param bindingMap the binding configuration map
     * @return typed MasteryBinding
     * @throws IllegalStateException if required fields are missing
     */
    @NotNull
    private static MasteryBinding parseSingleBinding(@NotNull Map<String, Object> bindingMap) {
        String namespace = (String) bindingMap.get("namespace");
        String registryRef = (String) bindingMap.get("registryRef");
        String freshnessPolicyRef = (String) bindingMap.get("freshnessPolicyRef");
        String versionCompatibilityPolicyRef = (String) bindingMap.get("versionCompatibilityPolicyRef");
        String obsolescencePolicyRef = (String) bindingMap.get("obsolescencePolicyRef");

        if (namespace == null || namespace.isBlank()) {
            throw new IllegalStateException("Mastery binding 'namespace' is required");
        }
        if (registryRef == null || registryRef.isBlank()) {
            throw new IllegalStateException("Mastery binding 'registryRef' is required");
        }

        return new MasteryBinding(
                namespace,
                registryRef,
                freshnessPolicyRef,
                versionCompatibilityPolicyRef,
                obsolescencePolicyRef
        );
    }
}
