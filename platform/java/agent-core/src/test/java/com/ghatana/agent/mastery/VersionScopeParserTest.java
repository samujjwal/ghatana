/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.mastery;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

/**
 * Tests for VersionScopeParser.
 * Phase 6 FIX: Tests for typed version scope parser.
 *
 * @doc.type class
 * @doc.purpose Tests for VersionScopeParser
 * @doc.layer agent-core
 * @doc.pattern Test
 */
@DisplayName("VersionScopeParser Tests")
class VersionScopeParserTest {

    @Test
    @DisplayName("Should parse version scope from JSON")
    void shouldParseVersionScopeFromJson() {
        String json = """
            {
                "active": [{"kind": "runtimeVersion", "name": "java", "range": "17..21", "ecosystem": "jvm"}],
                "maintenance": [],
                "obsolete": []
            }
            """;

        VersionScope fallback = VersionScope.empty();
        VersionScope result = VersionScopeParser.fromJson(json, fallback);

        assertNotNull(result);
        assertFalse(result.active().isEmpty());
        assertEquals(1, result.active().size());
    }

    @Test
    @DisplayName("Should return fallback when JSON is invalid")
    void shouldReturnFallbackWhenJsonIsInvalid() {
        String invalidJson = "{ invalid json";

        VersionScope fallback = VersionScope.empty();
        VersionScope result = VersionScopeParser.fromJson(invalidJson, fallback);

        assertSame(fallback, result);
    }

    @Test
    @DisplayName("Should parse version scope from Map")
    void shouldParseVersionScopeFromMap() {
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        java.util.List<java.util.Map<String, Object>> active = new java.util.ArrayList<>();
        java.util.Map<String, Object> constraint = new java.util.HashMap<>();
        constraint.put("kind", "runtimeVersion");
        constraint.put("name", "java");
        constraint.put("range", "17..21");
        constraint.put("ecosystem", "jvm");
        active.add(constraint);
        map.put("active", active);
        map.put("maintenance", new java.util.ArrayList<>());
        map.put("obsolete", new java.util.ArrayList<>());

        VersionScope fallback = VersionScope.empty();
        VersionScope result = VersionScopeParser.fromMap(map, fallback);

        assertNotNull(result);
        assertFalse(result.active().isEmpty());
    }

    @Test
    @DisplayName("Should serialize version scope to JSON")
    void shouldSerializeVersionScopeToJson() {
        VersionScope scope = new VersionScope(
                List.of(VersionConstraint.runtimeVersion("java", "17..21", "jvm")),
                List.of(),
                List.of()
        );

        String json = VersionScopeParser.toJson(scope);

        assertNotNull(json);
        assertFalse(json.isBlank());
        assertTrue(json.contains("java"));
        assertTrue(json.contains("17..21"));
    }
}
