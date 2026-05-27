/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.pattern.runtime;

import com.ghatana.aep.pattern.spec.PatternSpec;
import com.ghatana.aep.pattern.spec.PatternSpecParser;
import com.ghatana.aep.pattern.spec.PatternSpecValidationResult;
import com.ghatana.aep.pattern.spec.PatternSpecValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AEP-003: PatternSpec execution/replay E2E tests.
 *
 * <p>Verifies PatternSpec parsing, structure, deterministic replay semantics, and validation
 * of execution-required fields. Tests use PatternSpecParser and PatternSpecValidator to exercise
 * production code rather than object-literal assertions.
 *
 * @doc.type class
 * @doc.purpose PatternSpec execution/replay E2E tests (AEP-003)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PatternSpec Execution Replay E2E Tests")
@Tag("aep")
@Tag("execution")
@Tag("replay")
@Tag("e2e")
class PatternSpecExecutionReplayE2ETest {

    // ==================== AEP-003: PatternSpec parses and has required execution structure ====================

    @Test
    @DisplayName("AEP-003: PatternSpec parses and exposes all required execution sections")
    void patternSpecParsesAndExposesAllRequiredExecutionSections() {
        PatternSpec spec = PatternSpecParser.parse(createValidSpecMap(Map.of("event", "deploy.started")));

        assertThat(spec).isNotNull();
        assertThat(spec.pattern()).isNotNull();
        assertThat(spec.emit()).isNotNull();
        assertThat(spec.lifecycle()).isNotNull();
        assertThat(spec.governance()).isNotNull();
        assertThat(spec.observability()).isNotNull();
    }

    @Test
    @DisplayName("AEP-003: PatternSpec emit section contains outputSchema for derived event")
    void patternSpecEmitSectionContainsOutputSchema() {
        PatternSpec spec = PatternSpecParser.parse(createValidSpecMap(Map.of("event", "deploy.started")));

        assertThat(spec.emit()).isNotNull();
        assertThat(spec.emit().outputSchema()).isNotBlank();
    }

    @Test
    @DisplayName("AEP-003: PatternSpec lifecycle state is correctly parsed")
    void patternSpecLifecycleStateIsCorrectlyParsed() {
        PatternSpec spec = PatternSpecParser.parse(createValidSpecMap(Map.of("event", "deploy.started")));

        assertThat(spec.lifecycle()).isNotNull();
        assertThat(spec.lifecycle().state()).isNotBlank();
    }

    // ==================== AEP-003: Replay produces deterministic decision path ====================

    @Test
    @DisplayName("AEP-003: Parsing the same spec map twice produces equivalent typed specs")
    void parsingSameSpecMapTwiceProducesEquivalentTypedSpecs() {
        Map<String, Object> specMap = createValidSpecMap(Map.of("event", "deploy.started"));

        PatternSpec first = PatternSpecParser.parse(specMap);
        PatternSpec second = PatternSpecParser.parse(specMap);

        // Deterministic: same input produces same spec structure
        assertThat(first.apiVersion()).isEqualTo(second.apiVersion());
        assertThat(first.kind()).isEqualTo(second.kind());
        assertThat(first.lifecycle().state()).isEqualTo(second.lifecycle().state());
        assertThat(first.governance().approvalPolicy()).isEqualTo(second.governance().approvalPolicy());
        assertThat(first.emit().outputSchema()).isEqualTo(second.emit().outputSchema());
    }

    @Test
    @DisplayName("AEP-003: Deterministic replay semantics required: replayPolicy must be present")
    void replayPolicySectionMustBePresentForExecution() {
        Map<String, Object> spec = createValidSpecMap(Map.of("event", "deploy.started"));
        spec.put("semantics", Map.of("timePolicy", Map.of(), "uncertaintyPolicy", Map.of())); // No replayPolicy

        PatternSpecValidationResult result = PatternSpecValidator.validate(spec);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anySatisfy(error -> assertThat(error).contains("replayPolicy"));
    }

    @Test
    @DisplayName("AEP-003: Full spec with all execution sections is valid")
    void fullSpecWithAllExecutionSectionsIsValid() {
        Map<String, Object> spec = createValidSpecMap(Map.of("event", "deploy.started"));

        PatternSpecValidationResult result = PatternSpecValidator.validate(spec);

        assertThat(result.valid()).isTrue();
    }

    // ==================== AEP-003: Record trace/metric/audit (observability required) ====================

    @Test
    @DisplayName("AEP-003: Observability section is present in parsed spec")
    void observabilitySectionIsPresentInParsedSpec() {
        PatternSpec spec = PatternSpecParser.parse(createValidSpecMap(Map.of("event", "deploy.started")));

        assertThat(spec.observability()).isNotNull();
        // observability.toMap() should serialize correctly
        assertThat(spec.toMap().get("observability")).isNotNull();
    }

    // ==================== AEP-003: PatternSpec toMap round-trip ====================

    @Test
    @DisplayName("AEP-003: PatternSpec serializes back to map preserving key structure")
    void patternSpecSerializesBackToMapPreservingKeyStructure() {
        PatternSpec spec = PatternSpecParser.parse(createValidSpecMap(Map.of("event", "deploy.started")));

        Map<String, Object> serialized = spec.toMap();

        assertThat(serialized).containsKeys("apiVersion", "kind", "pattern", "emit", "lifecycle", "governance", "observability");
        assertThat(serialized.get("apiVersion")).isEqualTo("aep.ghatana.io/v1");
        assertThat(serialized.get("kind")).isEqualTo("PatternSpec");
    }

    private static Map<String, Object> createValidSpecMap(Map<String, Object> pattern) {
        return new java.util.LinkedHashMap<>(Map.of(
            "apiVersion", "aep.ghatana.io/v1",
            "kind", "PatternSpec",
            "metadata", Map.of("name", "test", "namespace", "test", "version", "1.0.0", "tenantId", "tenant-a", "owner", "sre"),
            "semantics", Map.of("timePolicy", Map.of(), "uncertaintyPolicy", Map.of(), "replayPolicy", Map.of()),
            "pattern", pattern,
            "emit", Map.of("eventType", "pattern.matched", "outputSchema", "PatternMatched"),
            "lifecycle", Map.of("state", "SHADOW"),
            "governance", Map.of("reviewPolicy", "human_required"),
            "observability", Map.of("metrics", true, "tracing", true)));
    }
}
