/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.agent.safety;

import com.ghatana.agent.safety.SafetyPolicy;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Mapper for converting between {@link SafetyPolicy} and data maps for EntityRepository persistence.
 *
 * @doc.type class
 * @doc.purpose Maps SafetyPolicy to/from data maps
 * @doc.layer data-cloud
 * @doc.pattern Mapper
 */
public final class SafetyPolicyMapper {

    private SafetyPolicyMapper() {
        // Utility class - prevent instantiation
    }

    /**
     * Converts a SafetyPolicy to a data map for persistence.
     *
     * @param policy the safety policy
     * @return data map representation
     */
    @NotNull
    public static Map<String, Object> toDataMap(@NotNull SafetyPolicy policy) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("policyId", policy.policyId());
        map.put("tenantId", policy.tenantId());
        map.put("name", policy.name());
        map.put("description", policy.description());
        map.put("forbiddenTools", new ArrayList<>(policy.forbiddenTools()));
        map.put("forbiddenPatterns", new ArrayList<>(policy.forbiddenPatterns()));
        map.put("requiredFlags", new LinkedHashMap<>(policy.requiredFlags()));
        map.put("allowSideEffects", policy.allowSideEffects());
        map.put("promptInjectionPatterns", new ArrayList<>(policy.promptInjectionPatterns()));
        map.put("active", policy.active());
        return map;
    }

    /**
     * Converts a data map to a SafetyPolicy.
     *
     * @param data the data map
     * @return safety policy
     */
    @NotNull
    public static SafetyPolicy fromDataMap(@NotNull Map<String, Object> data) {
        String policyId = (String) data.get("policyId");
        String tenantId = (String) data.get("tenantId");
        String name = (String) data.get("name");
        String description = (String) data.get("description");

        @SuppressWarnings("unchecked")
        List<String> forbiddenToolsList = (List<String>) data.get("forbiddenTools");
        Set<String> forbiddenTools = forbiddenToolsList != null ? Set.copyOf(forbiddenToolsList) : Set.of();

        @SuppressWarnings("unchecked")
        List<String> forbiddenPatterns = (List<String>) data.get("forbiddenPatterns");
        List<String> patterns = forbiddenPatterns != null ? List.copyOf(forbiddenPatterns) : List.of();

        @SuppressWarnings("unchecked")
        Map<String, String> requiredFlags = (Map<String, String>) data.get("requiredFlags");
        Map<String, String> flags = requiredFlags != null ? Map.copyOf(requiredFlags) : Map.of();

        Boolean allowSideEffectsObj = (Boolean) data.get("allowSideEffects");
        boolean allowSideEffects = allowSideEffectsObj != null ? allowSideEffectsObj : false;

        @SuppressWarnings("unchecked")
        List<String> promptInjectionPatternsList = (List<String>) data.get("promptInjectionPatterns");
        List<String> injectionPatterns = promptInjectionPatternsList != null ? List.copyOf(promptInjectionPatternsList) : List.of();

        Boolean activeObj = (Boolean) data.get("active");
        boolean active = activeObj != null ? activeObj : true;

        return new SafetyPolicy(
                policyId,
                tenantId,
                name,
                description,
                forbiddenTools,
                patterns,
                flags,
                allowSideEffects,
                injectionPatterns,
                active
        );
    }
}
