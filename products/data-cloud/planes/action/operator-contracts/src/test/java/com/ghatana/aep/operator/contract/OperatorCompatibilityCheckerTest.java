/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.operator.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Phase 5: Contract tests for OperatorCompatibilityChecker.
 *
 * <p>These tests enforce:
 * <ul>
 *   <li>Operator compatibility validation</li>
 *   <li>Schema compatibility checks</li>
 *   <li>Operator specification validation</li>
 * </ul>
 */
@DisplayName("Operator Compatibility Checker Tests (Phase 5)")
class OperatorCompatibilityCheckerTest {

    private final OperatorCompatibilityChecker checker = new OperatorCompatibilityChecker();

    // =========================================================================
    //  Operator Compatibility Checks
    // =========================================================================

    @Nested
    @DisplayName("Operator Compatibility Checks")
    class CompatibilityTests {

        @Test
        @DisplayName("compatible operators return empty issues list")
        void compatibleOperatorsReturnEmptyIssuesList() {
            OperatorSpec source = new OperatorSpec(
                "op-1",
                OperatorKind.FILTER,
                "schema-1",
                "schema-2",
                Map.of(),
                Map.of());

            OperatorSpec target = new OperatorSpec(
                "op-2",
                OperatorKind.TRANSFORM,
                "schema-2",
                "schema-3",
                Map.of(),
                Map.of());

            List<OperatorCompatibilityChecker.CompatibilityIssue> issues =
                checker.checkCompatibility(source, target);

            assertThat(issues).isEmpty();
        }

        @Test
        @DisplayName("schema mismatch is detected")
        void schemaMismatchIsDetected() {
            OperatorSpec source = new OperatorSpec(
                "op-1",
                OperatorKind.FILTER,
                "schema-1",
                "schema-2",
                Map.of(),
                Map.of());

            OperatorSpec target = new OperatorSpec(
                "op-2",
                OperatorKind.TRANSFORM,
                "schema-3", // Mismatch
                "schema-4",
                Map.of(),
                Map.of());

            List<OperatorCompatibilityChecker.CompatibilityIssue> issues =
                checker.checkCompatibility(source, target);

            assertThat(issues).hasSize(1);
            assertThat(issues.get(0).issue()).isEqualTo(
                OperatorCompatibilityChecker.IssueType.SCHEMA_MISMATCH);
        }

        @Test
        @DisplayName("requires non-null source operator")
        void requiresNonNullSourceOperator() {
            OperatorSpec target = new OperatorSpec(
                "op-2",
                OperatorKind.TRANSFORM,
                "schema-2",
                "schema-3",
                Map.of(),
                Map.of());

            assertThatNullPointerException()
                .isThrownBy(() -> checker.checkCompatibility(null, target))
                .withMessageContaining("source must not be null");
        }

        @Test
        @DisplayName("requires non-null target operator")
        void requiresNonNullTargetOperator() {
            OperatorSpec source = new OperatorSpec(
                "op-1",
                OperatorKind.FILTER,
                "schema-1",
                "schema-2",
                Map.of(),
                Map.of());

            assertThatNullPointerException()
                .isThrownBy(() -> checker.checkCompatibility(source, null))
                .withMessageContaining("target must not be null");
        }

        @Test
        @DisplayName("issue includes operator IDs in record fields")
        void issueIncludesOperatorIdsInRecordFields() {
            OperatorSpec source = new OperatorSpec(
                "op-1",
                OperatorKind.FILTER,
                "schema-1",
                "schema-2",
                Map.of(),
                Map.of());

            OperatorSpec target = new OperatorSpec(
                "op-2",
                OperatorKind.TRANSFORM,
                "schema-3",
                "schema-4",
                Map.of(),
                Map.of());

            List<OperatorCompatibilityChecker.CompatibilityIssue> issues =
                checker.checkCompatibility(source, target);

            assertThat(issues).isNotEmpty();
            assertThat(issues.get(0).sourceOperator()).isEqualTo("op-1");
            assertThat(issues.get(0).targetOperator()).isEqualTo("op-2");
        }
    }

    // =========================================================================
    //  Operator Specification Validation
    // =========================================================================

    @Nested
    @DisplayName("Operator Specification Validation")
    class ValidationTests {

        @Test
        @DisplayName("valid operator spec returns empty issues list")
        void validOperatorSpecReturnsEmptyIssuesList() {
            OperatorSpec spec = new OperatorSpec(
                "op-1",
                OperatorKind.FILTER,
                "schema-1",
                "schema-2",
                Map.of(),
                Map.of());

            List<OperatorCompatibilityChecker.CompatibilityIssue> issues =
                checker.validateOperatorSpec(spec);

            assertThat(issues).isEmpty();
        }

        @Test
        @DisplayName("requires non-null operator spec")
        void requiresNonNullOperatorSpec() {
            assertThatNullPointerException()
                .isThrownBy(() -> checker.validateOperatorSpec(null))
                .withMessageContaining("spec must not be null");
        }

        @Test
        @DisplayName("validation checks operator kind requirements")
        void validationChecksOperatorKindRequirements() {
            OperatorSpec spec = new OperatorSpec(
                "op-1",
                OperatorKind.AGENT_ACTION,
                "schema-1",
                "schema-2",
                Map.of(),
                Map.of());

            List<OperatorCompatibilityChecker.CompatibilityIssue> issues =
                checker.validateOperatorSpec(spec);

            assertThat(issues)
                .extracting(OperatorCompatibilityChecker.CompatibilityIssue::issue)
                .contains(OperatorCompatibilityChecker.IssueType.MISSING_REQUIRED_PARAMETER);
            assertThat(issues)
                .extracting(OperatorCompatibilityChecker.CompatibilityIssue::description)
                .anyMatch(description -> description.contains("agentRef"));
        }

        @Test
        @DisplayName("agent action is terminal and cannot feed another operator")
        void agentActionIsTerminalAndCannotFeedAnotherOperator() {
            OperatorSpec source = new OperatorSpec(
                "op-action",
                OperatorKind.AGENT_ACTION,
                "schema-1",
                "schema-2",
                Map.of(
                    "agentRef", "agent://approve",
                    "toolPolicy", "restricted",
                    "approvalPolicy", "human-required",
                    "auditPolicy", "required"),
                Map.of());

            OperatorSpec target = new OperatorSpec(
                "op-target",
                OperatorKind.TRANSFORM,
                "schema-2",
                "schema-3",
                Map.of(),
                Map.of());

            List<OperatorCompatibilityChecker.CompatibilityIssue> issues =
                checker.checkCompatibility(source, target);

            assertThat(issues)
                .extracting(OperatorCompatibilityChecker.CompatibilityIssue::issue)
                .contains(OperatorCompatibilityChecker.IssueType.KIND_INCOMPATIBILITY);
        }
    }

    // =========================================================================
    //  Compatibility Issue Records
    // =========================================================================

    @Nested
    @DisplayName("Compatibility Issue Records")
    class IssueRecordTests {

        @Test
        @DisplayName("issue record contains all fields")
        void issueRecordContainsAllFields() {
            OperatorCompatibilityChecker.CompatibilityIssue issue =
                new OperatorCompatibilityChecker.CompatibilityIssue(
                    "op-1",
                    "op-2",
                    OperatorCompatibilityChecker.IssueType.SCHEMA_MISMATCH,
                    "Schema mismatch");

            assertThat(issue.sourceOperator()).isEqualTo("op-1");
            assertThat(issue.targetOperator()).isEqualTo("op-2");
            assertThat(issue.issue()).isEqualTo(OperatorCompatibilityChecker.IssueType.SCHEMA_MISMATCH);
            assertThat(issue.description()).isEqualTo("Schema mismatch");
        }
    }
}
