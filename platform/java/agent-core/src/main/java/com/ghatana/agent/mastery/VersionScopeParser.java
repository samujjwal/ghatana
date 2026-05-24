/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.mastery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Typed parser for VersionScope from JSON and other structured formats.
 * Phase 6 FIX: Replaces string-based parsing with typed materializer.
 *
 * @doc.type class
 * @doc.purpose Typed VersionScope parser/materializer
 * @doc.layer agent-core
 * @doc.pattern Parser
 */
public final class VersionScopeParser {

    private static final Logger log = LoggerFactory.getLogger(VersionScopeParser.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private VersionScopeParser() {
        // Utility class
    }

    /**
     * Parses a VersionScope from JSON string.
     * Expected format:
     * {
     *   "active": [{"kind": "runtimeVersion", "name": "java", "range": "17..21", "ecosystem": "jvm"}],
     *   "maintenance": [],
     *   "obsolete": []
     * }
     *
     * @param json JSON string containing version scope
     * @param fallback default version scope to use if parsing fails
     * @return parsed VersionScope or fallback
     */
    @NotNull
    public static VersionScope fromJson(@NotNull String json, @NotNull VersionScope fallback) {
        try {
            Map<String, Object> root = objectMapper.readValue(json, MAP_TYPE);
            return fromMap(root, fallback);
        } catch (JsonProcessingException e) {
            log.debug("Failed to parse version scope JSON, using fallback: {}", e.getMessage());
            return fallback;
        }
    }

    /**
     * Parses a VersionScope from a Map structure.
     *
     * @param map map containing version scope data
     * @param fallback default version scope to use if parsing fails
     * @return parsed VersionScope or fallback
     */
    @NotNull
    public static VersionScope fromMap(@NotNull Map<String, Object> map, @NotNull VersionScope fallback) {
        try {
            List<VersionConstraint> active = parseConstraints(map.get("active"));
            List<VersionConstraint> maintenance = parseConstraints(map.get("maintenance"));
            List<VersionConstraint> obsolete = parseConstraints(map.get("obsolete"));

            // Validate no overlaps
            VersionScope scope = new VersionScope(active, maintenance, obsolete);
            return scope;
        } catch (Exception e) {
            log.debug("Failed to parse version scope from map, using fallback: {}", e.getMessage());
            return fallback;
        }
    }

    /**
     * Parses a list of VersionConstraint from a list structure.
     *
     * @param obj object containing constraint data
     * @return list of parsed constraints
     */
    @SuppressWarnings("unchecked")
    @NotNull
    private static List<VersionConstraint> parseConstraints(@Nullable Object obj) {
        if (obj == null) {
            return List.of();
        }

        if (!(obj instanceof List<?>)) {
            return List.of();
        }

        List<?> list = (List<?>) obj;
        List<VersionConstraint> constraints = new ArrayList<>();

        for (Object item : list) {
            if (item instanceof Map<?, ?>) {
                try {
                    Map<String, Object> constraintMap = (Map<String, Object>) item;
                    VersionConstraint constraint = parseConstraint(constraintMap);
                    constraints.add(constraint);
                } catch (Exception e) {
                    log.debug("Failed to parse constraint, skipping: {}", e.getMessage());
                }
            }
        }

        return constraints;
    }

    /**
     * Parses a single VersionConstraint from a map.
     *
     * @param map map containing constraint data
     * @return parsed constraint
     */
    @NotNull
    private static VersionConstraint parseConstraint(@NotNull Map<String, Object> map) {
        String kind = getString(map, "kind", "runtimeVersion");
        String name = getString(map, "name", "");
        String range = getString(map, "range", "");
        String ecosystem = getString(map, "ecosystem", "system");

        return switch (kind) {
            case "packageVersion" -> VersionConstraint.packageVersion(name, range, ecosystem);
            case "toolVersion" -> VersionConstraint.toolVersion(name, range, ecosystem);
            case "runtimeVersion" -> VersionConstraint.runtimeVersion(name, range, ecosystem);
            default -> VersionConstraint.runtimeVersion(name, range, ecosystem);
        };
    }

    /**
     * Gets a string value from a map with a default.
     *
     * @param map map to read from
     * @param key key to look up
     * @param defaultValue default value if key is missing
     * @return string value or default
     */
    @NotNull
    private static String getString(@NotNull Map<String, Object> map, @NotNull String key, @NotNull String defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        return String.valueOf(value);
    }

    /**
     * Serializes a VersionScope to JSON string.
     *
     * @param scope version scope to serialize
     * @return JSON string
     */
    @NotNull
    public static String toJson(@NotNull VersionScope scope) {
        try {
            return objectMapper.writeValueAsString(scope);
        } catch (JsonProcessingException e) {
            log.debug("Failed to serialize version scope to JSON: {}", e.getMessage());
            return "{}";
        }
    }
}
