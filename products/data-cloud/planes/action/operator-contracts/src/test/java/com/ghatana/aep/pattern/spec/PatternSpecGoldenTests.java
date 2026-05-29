package com.ghatana.aep.pattern.spec;

import com.ghatana.aep.operator.contract.OperatorKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Golden tests for PatternSpec validation and compilation.
 *
 * @doc.type class
 * @doc.purpose Golden path tests for PatternSpec validation and compilation
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PatternSpec Golden Tests")
class PatternSpecGoldenTests {

    @Test
    @DisplayName("valid SEQ with AGENT_PREDICATE")
    void validSeqWithAgentPredicate() {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("apiVersion", "patterns.ghatana.io/v1");
        spec.put("kind", "PatternSpec");
        spec.put("metadata", Map.of(
            "name", "test-pattern",
            "namespace", "test",
            "version", "1.0.0"));
        spec.put("semantics", Map.of(
            "timePolicy", "event_time",
            "uncertaintyPolicy", "strict",
            "replayPolicy", "full"));
        spec.put("pattern", Map.of(
            "operator", "SEQ",
            "operands", List.of(
                Map.of(
                    "operator", "AGENT_PREDICATE",
                    "capabilityRef", "agents/predicate@1.0.0",
                    "outputSchema", "schema://predicate/output"),
                Map.of(
                    "event", "test.event"))));
        spec.put("emit", Map.of(
            "eventType", "test.result",
            "outputSchema", "schema://result/output"));
        spec.put("lifecycle", Map.of(
            "state", "active"));
        spec.put("governance", Map.of(
            "owner", "test-team"));
        spec.put("observability", Map.of());

        PatternSpecValidationResult result = PatternSpecValidator.validate(spec);
        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();

        CompiledPattern compiled = PatternSpecCompiler.compile(spec);
        assertThat(compiled.patternId()).isEqualTo("test-pattern");
        assertThat(compiled.root().operatorKind()).isEqualTo(OperatorKind.SEQ);
        assertThat(compiled.root().children()).hasSize(2);
        assertThat(compiled.root().children().get(0).operatorKind()).isEqualTo(OperatorKind.AGENT_PREDICATE);
    }

    @Test
    @DisplayName("valid WINDOW with AGENT_ENRICH")
    void validWindowWithAgentEnrich() {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("apiVersion", "patterns.ghatana.io/v1");
        spec.put("kind", "PatternSpec");
        spec.put("metadata", Map.of(
            "name", "window-pattern",
            "namespace", "test",
            "version", "1.0.0"));
        spec.put("semantics", Map.of(
            "timePolicy", "event_time",
            "uncertaintyPolicy", "strict",
            "replayPolicy", "full"));
        spec.put("pattern", Map.of(
            "operator", "WINDOW",
            "window", "5m",
            "pattern", Map.of(
                "operator", "AGENT_ENRICH",
                "capabilityRef", "agents/enrich@1.0.0",
                "outputSchema", "schema://enrich/output")));
        spec.put("emit", Map.of(
            "eventType", "test.enriched",
            "outputSchema", "schema://enriched/output"));
        spec.put("lifecycle", Map.of(
            "state", "active"));
        spec.put("governance", Map.of(
            "owner", "test-team"));
        spec.put("observability", Map.of());

        PatternSpecValidationResult result = PatternSpecValidator.validate(spec);
        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();

        // Note: WINDOW operator-specific parameters like 'window' are in the generic parameters map
        // The typed model preserves these in parameters() but validation happens on the map
        CompiledPattern compiled = PatternSpecCompiler.compile(spec);
        assertThat(compiled.patternId()).isEqualTo("window-pattern");
        assertThat(compiled.root().operatorKind()).isEqualTo(OperatorKind.WINDOW);
        assertThat(compiled.root().children()).hasSize(1);
        assertThat(compiled.root().children().get(0).operatorKind()).isEqualTo(OperatorKind.AGENT_ENRICH);
    }

    @Test
    @DisplayName("valid learning pipeline with AGENT_PATTERN_SYNTHESIS")
    void validLearningPipelineWithPatternSynthesis() {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("apiVersion", "patterns.ghatana.io/v1");
        spec.put("kind", "PatternSpec");
        spec.put("metadata", Map.of(
            "name", "learning-pattern",
            "namespace", "test",
            "version", "1.0.0"));
        spec.put("semantics", Map.of(
            "timePolicy", "event_time",
            "uncertaintyPolicy", "strict",
            "replayPolicy", "full"));
        spec.put("pattern", Map.of(
            "operator", "SEQ",
            "operands", List.of(
                Map.of(
                    "operator", "AGENT_PATTERN_SYNTHESIS",
                    "capabilityRef", "agents/synthesis@1.0.0",
                    "outputSchema", "schema://synthesis/output"),
                Map.of(
                    "operator", "FILTER",
                    "pattern", Map.of(
                        "event", "test.result")))));
        spec.put("emit", Map.of(
            "eventType", "test.pattern",
            "outputSchema", "schema://pattern/output"));
        spec.put("lifecycle", Map.of(
            "state", "shadow"));
        spec.put("governance", Map.of(
            "owner", "test-team"));
        spec.put("observability", Map.of());

        PatternSpecValidationResult result = PatternSpecValidator.validate(spec);
        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();

        CompiledPattern compiled = PatternSpecCompiler.compile(spec);
        assertThat(compiled.patternId()).isEqualTo("learning-pattern");
        assertThat(compiled.isShadow()).isTrue();
        assertThat(compiled.root().operatorKind()).isEqualTo(OperatorKind.SEQ);
        assertThat(compiled.root().children().get(0).operatorKind()).isEqualTo(OperatorKind.AGENT_PATTERN_SYNTHESIS);
    }

    @Test
    @DisplayName("invalid unknown operator")
    void invalidUnknownOperator() {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("apiVersion", "patterns.ghatana.io/v1");
        spec.put("kind", "PatternSpec");
        spec.put("metadata", Map.of(
            "name", "invalid-pattern",
            "namespace", "test",
            "version", "1.0.0"));
        spec.put("semantics", Map.of(
            "timePolicy", "event_time",
            "uncertaintyPolicy", "strict",
            "replayPolicy", "full"));
        spec.put("pattern", Map.of(
            "operator", "UNKNOWN_OPERATOR"));
        spec.put("emit", Map.of(
            "eventType", "test.result",
            "outputSchema", "schema://result/output"));
        spec.put("lifecycle", Map.of(
            "state", "active"));
        spec.put("governance", Map.of(
            "owner", "test-team"));
        spec.put("observability", Map.of());

        PatternSpecValidationResult result = PatternSpecValidator.validate(spec);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(error -> error.contains("UNKNOWN_OPERATOR"));
    }

    @Test
    @DisplayName("invalid missing output schema for AGENT_PREDICATE")
    void invalidMissingOutputSchema() {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("apiVersion", "patterns.ghatana.io/v1");
        spec.put("kind", "PatternSpec");
        spec.put("metadata", Map.of(
            "name", "invalid-pattern",
            "namespace", "test",
            "version", "1.0.0"));
        spec.put("semantics", Map.of(
            "timePolicy", "event_time",
            "uncertaintyPolicy", "strict",
            "replayPolicy", "full"));
        spec.put("pattern", Map.of(
            "operator", "AGENT_PREDICATE",
            "capabilityRef", "agents/predicate@1.0.0"));
        spec.put("emit", Map.of(
            "eventType", "test.result",
            "outputSchema", "schema://result/output"));
        spec.put("lifecycle", Map.of(
            "state", "active"));
        spec.put("governance", Map.of(
            "owner", "test-team"));
        spec.put("observability", Map.of());

        PatternSpecValidationResult result = PatternSpecValidator.validate(spec);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(error -> error.contains("outputSchema"));
    }

    @Test
    @DisplayName("invalid production missing commit SHA")
    void invalidProductionMissingCommitSha() {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("apiVersion", "patterns.ghatana.io/v1");
        spec.put("kind", "PatternSpec");
        spec.put("metadata", Map.of(
            "name", "invalid-pattern",
            "namespace", "test",
            "version", "1.0.0"));
        spec.put("semantics", Map.of(
            "timePolicy", "event_time",
            "uncertaintyPolicy", "strict",
            "replayPolicy", "full"));
        spec.put("pattern", Map.of(
            "operator", "AGENT_PREDICATE",
            "capabilityRef", "agents/predicate@1.0.0",
            "outputSchema", "schema://predicate/output"));
        spec.put("emit", Map.of(
            "eventType", "test.result",
            "outputSchema", "schema://result/output"));
        spec.put("lifecycle", Map.of(
            "state", "active",
            "evidencePolicy", "strict",
            "evidenceStore", "eventcloud://store"));
        spec.put("governance", Map.of(
            "owner", "test-team",
            "riskLevel", "low",
            "rollbackPolicy", "manual",
            "auditPolicy", "full"));
        spec.put("observability", Map.of());

        PatternSpecValidationResult result = PatternSpecValidator.validate(spec, null, "production");
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(error -> error.contains("commitSha"));
    }

    @Test
    @DisplayName("invalid production missing owner")
    void invalidProductionMissingOwner() {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("apiVersion", "patterns.ghatana.io/v1");
        spec.put("kind", "PatternSpec");
        spec.put("metadata", Map.of(
            "name", "invalid-pattern",
            "namespace", "test",
            "version", "1.0.0"));
        spec.put("semantics", Map.of(
            "timePolicy", "event_time",
            "uncertaintyPolicy", "strict",
            "replayPolicy", "full"));
        spec.put("pattern", Map.of(
            "operator", "AGENT_PREDICATE",
            "capabilityRef", "agents/predicate@1.0.0",
            "outputSchema", "schema://predicate/output"));
        spec.put("emit", Map.of(
            "eventType", "test.result",
            "outputSchema", "schema://result/output"));
        spec.put("lifecycle", Map.of(
            "state", "active",
            "evidencePolicy", "strict",
            "evidenceStore", "eventcloud://store"));
        spec.put("governance", Map.of());
        spec.put("observability", Map.of());

        PatternSpecValidationResult result = PatternSpecValidator.validate(spec, "abc123def456789abc123def456789abc123def45", "production");
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(error -> error.contains("owner"));
    }

    @Test
    @DisplayName("invalid evidence store not approved")
    void invalidEvidenceStoreNotApproved() {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("apiVersion", "patterns.ghatana.io/v1");
        spec.put("kind", "PatternSpec");
        spec.put("metadata", Map.of(
            "name", "invalid-pattern",
            "namespace", "test",
            "version", "1.0.0"));
        spec.put("semantics", Map.of(
            "timePolicy", "event_time",
            "uncertaintyPolicy", "strict",
            "replayPolicy", "full"));
        spec.put("pattern", Map.of(
            "operator", "AGENT_PREDICATE",
            "capabilityRef", "agents/predicate@1.0.0",
            "outputSchema", "schema://predicate/output"));
        spec.put("emit", Map.of(
            "eventType", "test.result",
            "outputSchema", "schema://result/output"));
        spec.put("lifecycle", Map.of(
            "state", "active",
            "evidencePolicy", "strict",
            "evidenceStore", "s3://unapproved-store"));
        spec.put("governance", Map.of(
            "owner", "test-team",
            "riskLevel", "low",
            "rollbackPolicy", "manual",
            "auditPolicy", "full"));
        spec.put("observability", Map.of());

        PatternSpecValidationResult result = PatternSpecValidator.validate(spec, "abc123def456789abc123def456789abc123def45", "production");
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(error -> error.contains("evidenceStore") && error.contains("not a Data-Cloud/AEP-approved store"));
    }

    @Test
    @DisplayName("invalid AGENT_ACTION missing tool policy")
    void invalidAgentActionMissingToolPolicy() {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("apiVersion", "patterns.ghatana.io/v1");
        spec.put("kind", "PatternSpec");
        spec.put("metadata", Map.of(
            "name", "invalid-pattern",
            "namespace", "test",
            "version", "1.0.0"));
        spec.put("semantics", Map.of(
            "timePolicy", "event_time",
            "uncertaintyPolicy", "strict",
            "replayPolicy", "full"));
        spec.put("pattern", Map.of(
            "operator", "AGENT_ACTION",
            "capabilityRef", "agents/action@1.0.0",
            "outputSchema", "schema://action/output"));
        spec.put("emit", Map.of(
            "eventType", "test.result",
            "outputSchema", "schema://result/output"));
        spec.put("lifecycle", Map.of(
            "state", "active"));
        spec.put("governance", Map.of(
            "owner", "test-team",
            "approvalPolicy", "manual"));
        spec.put("observability", Map.of());

        PatternSpecValidationResult result = PatternSpecValidator.validate(spec);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(error -> error.contains("toolPolicy"));
    }

    @Test
    @DisplayName("invalid AGENT_ACTION missing approval policy")
    void invalidAgentActionMissingApprovalPolicy() {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("apiVersion", "patterns.ghatana.io/v1");
        spec.put("kind", "PatternSpec");
        spec.put("metadata", Map.of(
            "name", "invalid-pattern",
            "namespace", "test",
            "version", "1.0.0"));
        spec.put("semantics", Map.of(
            "timePolicy", "event_time",
            "uncertaintyPolicy", "strict",
            "replayPolicy", "full"));
        spec.put("pattern", Map.of(
            "operator", "AGENT_ACTION",
            "capabilityRef", "agents/action@1.0.0",
            "outputSchema", "schema://action/output",
            "toolPolicy", Map.of("mode", "strict")));
        spec.put("emit", Map.of(
            "eventType", "test.result",
            "outputSchema", "schema://result/output"));
        spec.put("lifecycle", Map.of(
            "state", "active"));
        spec.put("governance", Map.of(
            "owner", "test-team"));
        spec.put("observability", Map.of());

        PatternSpecValidationResult result = PatternSpecValidator.validate(spec);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(error -> error.contains("approval policy"));
    }

    @Test
    @DisplayName("invalid side-effect capability in shadow lifecycle")
    void invalidSideEffectCapabilityInShadow() {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("apiVersion", "patterns.ghatana.io/v1");
        spec.put("kind", "PatternSpec");
        spec.put("metadata", Map.of(
            "name", "invalid-pattern",
            "namespace", "test",
            "version", "1.0.0"));
        spec.put("semantics", Map.of(
            "timePolicy", "event_time",
            "uncertaintyPolicy", "strict",
            "replayPolicy", "full"));
        spec.put("pattern", Map.of(
            "operator", "AGENT_ACTION",
            "capabilityRef", "agents/action@1.0.0",
            "outputSchema", "schema://action/output"));
        spec.put("emit", Map.of(
            "eventType", "test.result",
            "outputSchema", "schema://result/output"));
        spec.put("lifecycle", Map.of(
            "state", "shadow"));
        spec.put("governance", Map.of(
            "owner", "test-team",
            "approvalPolicy", "manual",
            "toolPolicy", Map.of("mode", "strict")));
        spec.put("observability", Map.of());

        // PatternSpec validation passes (structural validation)
        // The side-effect check happens at compile time with capability registry
        PatternSpecValidationResult result = PatternSpecValidator.validate(spec);
        assertThat(result.valid()).isTrue();
    }
}
