/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.pipeline.registry.validation;

import com.ghatana.pipeline.registry.model.PipelineRegistration;
import com.ghatana.platform.domain.auth.TenantId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Group 4 / DC-AP-004: Unit tests for typed node/operator validation and edge-arity constraints
 * in {@link PipelineValidator}.
 *
 * <p>Verifies that:
 * <ul>
 *   <li>Stages with unknown types are rejected at save time.</li>
 *   <li>All canonical operator/flow types are accepted.</li>
 *   <li>Terminal (single-output) node types that fan-out to multiple successors are rejected.</li>
 *   <li>Pipelines without a 'stages' section are rejected.</li>
 *   <li>Empty stage name is rejected.</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Typed DAG node/operator validation and edge-arity constraints
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PipelineValidator - Typed Node and Edge Constraint Tests")
@Tag("dag-validation")
class PipelineValidatorTypedNodeTest {

    private final PipelineValidator validator = new PipelineValidator();

    // Helpers

    private PipelineRegistration pipeline(String config) {
        return PipelineRegistration.builder()
            .name("test-pipeline")
            .tenantId(TenantId.of("tenant-1"))
            .config(config)
            .version(1)
            .build();
    }

    private List<String> validate(String config) {
        return validator.validate(pipeline(config), null);
    }

    // Valid types

    @Test
    @DisplayName("DC-AP-004: AGENT type is accepted")
    void agentTypeIsAccepted() {
        String config = """
            stages:
              - name: run-agent
                type: AGENT
                agentId: agent-xyz
            """;
        assertThat(validate(config)).isEmpty();
    }

    @Test
    @DisplayName("DC-AP-004: TRANSFORM type is accepted")
    void transformTypeIsAccepted() {
        String config = """
            stages:
              - name: enrich
                type: TRANSFORM
            """;
        assertThat(validate(config)).isEmpty();
    }

    @Test
    @DisplayName("DC-AP-004: SEQ pattern operator type is accepted")
    void seqPatternOperatorTypeIsAccepted() {
        String config = """
            stages:
              - name: detect-sequence
                type: SEQ
            """;
        assertThat(validate(config)).isEmpty();
    }

    @Test
    @DisplayName("DC-AP-004: Type comparison is case-insensitive")
    void typeComparisonIsCaseInsensitive() {
        String config = """
            stages:
              - name: my-sink
                type: sink
            """;
        assertThat(validate(config)).isEmpty();
    }

    @Test
    @DisplayName("DC-AP-004: Stage without a 'type' field is valid (type is optional)")
    void stageWithoutTypeIsValid() {
        String config = """
            stages:
              - name: untyped-stage
            """;
        assertThat(validate(config)).isEmpty();
    }

    // Unknown types

    @Test
    @DisplayName("DC-AP-004: Unknown stage type is rejected")
    void unknownStageTypeIsRejected() {
        String config = """
            stages:
              - name: bad-stage
                type: FOOBAR
            """;
        List<String> errors = validate(config);
        assertThat(errors).anyMatch(e -> e.contains("unknown type") && e.contains("FOOBAR"));
    }

    @Test
    @DisplayName("DC-AP-004: Multiple unknown types produce one error per stage")
    void multipleUnknownTypesProduceOneErrorEach() {
        String config = """
            stages:
              - name: stage-a
                type: UNKNOWN_TYPE_A
              - name: stage-b
                type: UNKNOWN_TYPE_B
            """;
        List<String> errors = validate(config);
        assertThat(errors.stream().filter(e -> e.contains("unknown type"))).hasSize(2);
    }

    // Edge arity constraints (single-output types)

    @Test
    @DisplayName("DC-AP-004: SINK stage with one successor is valid")
    void sinkStageWithOneSuccessorIsValid() {
        String config = """
            stages:
              - name: load-data
                type: SINK
              - name: audit-it
                type: AUDIT_CHECKPOINT
                dependsOn:
                  - load-data
            """;
        assertThat(validate(config)).isEmpty();
    }

    @Test
    @DisplayName("DC-AP-004: SINK stage with multiple successors is rejected")
    void sinkStageWithMultipleSuccessorsIsRejected() {
        String config = """
            stages:
              - name: persist
                type: SINK
              - name: next-a
                dependsOn:
                  - persist
              - name: next-b
                dependsOn:
                  - persist
            """;
        List<String> errors = validate(config);
        assertThat(errors).anyMatch(e -> e.contains("persist") && e.contains("fan-out"));
    }

    @Test
    @DisplayName("DC-AP-004: EVENT_EMIT stage with multiple successors is rejected")
    void eventEmitStageWithMultipleSuccessorsIsRejected() {
        String config = """
            stages:
              - name: emit
                type: EVENT_EMIT
              - name: branch-a
                after: emit
              - name: branch-b
                after: emit
            """;
        List<String> errors = validate(config);
        assertThat(errors).anyMatch(e -> e.contains("emit") && e.contains("fan-out"));
    }

    // Structural validation

    @Test
    @DisplayName("DC-AP-004: Pipeline with no 'stages' section is rejected")
    void pipelineWithNoSectionIsRejected() {
        String config = """
            name: no-stages
            version: 1
            """;
        List<String> errors = validate(config);
        assertThat(errors).anyMatch(e -> e.contains("stages"));
    }

    @Test
    @DisplayName("DC-AP-004: Stage with blank name is rejected")
    void stageWithBlankNameIsRejected() {
        String config = """
            stages:
              - name: ""
                type: AGENT
            """;
        List<String> errors = validate(config);
        assertThat(errors).anyMatch(e -> e.contains("non-blank") || e.contains("name"));
    }

    @Test
    @DisplayName("DC-AP-004: Empty stages array is rejected")
    void emptyStagesArrayIsRejected() {
        String config = "stages: []";
        List<String> errors = validate(config);
        assertThat(errors).anyMatch(e -> e.contains("at least one stage"));
    }
}
