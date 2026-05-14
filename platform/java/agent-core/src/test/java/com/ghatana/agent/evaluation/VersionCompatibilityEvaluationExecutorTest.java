/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.evaluation;

import com.ghatana.agent.context.version.VersionContext;
import com.ghatana.agent.context.version.VersionContextCodec;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link VersionCompatibilityEvaluationExecutor}.
 *
 * @doc.type class
 * @doc.purpose Unit tests for version compatibility evaluation
 * @doc.layer agent-core
 * @doc.pattern Test
 */
@DisplayName("VersionCompatibilityEvaluationExecutor Tests")
class VersionCompatibilityEvaluationExecutorTest extends EventloopTestBase {

    private VersionCompatibilityEvaluationExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new VersionCompatibilityEvaluationExecutor();
    }

    private static EvaluationContext context(String runtimeVersion, String sourceRef) {
        VersionContext vc = new VersionContext(
                Map.of(),
                runtimeVersion == null ? Map.of() : Map.of("jvm", runtimeVersion),
                Map.of(),
                Map.of(),
                sourceRef,
                Instant.now()
        );
        String encoded = VersionContextCodec.INSTANCE.encode(vc);
        return new EvaluationContext(
                "tenant-1",
                "agent-1",
                "skill-1",
                null,
                Map.of("versionContextJson", encoded)
        );
    }

    private static EvaluationTestCase versionCase(
            String versionScope, Map<String, String> caseContext) {
        return new EvaluationTestCase(
                "case-1", "version-test", "desc",
                EvaluationType.VERSION_COMPATIBILITY,
                "input", "expected",
                caseContext,
                Map.of(),
                "artifact-1",
                versionScope,
                0.0,
                List.of(), List.of(), 0.0,
                Map.of()
        );
    }

    @Test
    @DisplayName("supports VERSION_COMPATIBILITY type")
    void supportsVersionCompatibility() {
        assertThat(executor.supports(EvaluationType.VERSION_COMPATIBILITY)).isTrue();
        assertThat(executor.supports(EvaluationType.SAFETY)).isFalse();
    }

    @Test
    @DisplayName("passes when versionScope is wildcard '*'")
    void passesForWildcardVersionScope() {
        EvaluationTestCase tc = versionCase("*", Map.of("compat.jvm", "3.x"));

        EvaluationResult.TestCaseResult result = runPromise(() -> executor.execute(tc, context("3.2.0", "main")));

        assertThat(result.passed()).isTrue();
    }

    @Test
    @DisplayName("passes when runtimeVersion matches '1.x' pattern")
    void passesWhenVersionMatchesMajorPattern() {
        EvaluationTestCase tc = versionCase("*", Map.of("compat.jvm", "1.x"));

        EvaluationResult.TestCaseResult result = runPromise(() -> executor.execute(tc, context("1.2.3", "main")));

        assertThat(result.passed()).isTrue();
    }

    @Test
    @DisplayName("fails when runtimeVersion does not match '1.x' pattern")
    void failsWhenVersionDoesNotMatchPattern() {
        EvaluationTestCase tc = versionCase("*", Map.of("compat.jvm", "1.x"));

        EvaluationResult.TestCaseResult result = runPromise(() -> executor.execute(tc, context("2.0.0", "main")));

        assertThat(result.passed()).isFalse();
        assertThat(result.errorMessage()).containsIgnoringCase("2.0.0");
    }

    @Test
    @DisplayName("passes when runtimeVersion is missing from context")
    void failsWhenRuntimeVersionMissing() {
        EvaluationTestCase tc = versionCase("*", Map.of("compat.jvm", "1.x"));

        EvaluationResult.TestCaseResult result = runPromise(() -> executor.execute(tc, context(null, "main")));

        assertThat(result.passed()).isTrue();
    }

    @Test
    @DisplayName("passes when runtime version matches exact version scope")
    void passesWhenVersionMatchesExact() {
        EvaluationTestCase tc = versionCase("*", Map.of("compat.jvm", "1.2.3"));

        EvaluationResult.TestCaseResult result = runPromise(() -> executor.execute(tc, context("1.2.3", "main")));

        assertThat(result.passed()).isTrue();
    }

    @Test
    @DisplayName("fails when runtime version does not match exact version scope")
    void failsWhenVersionDoesNotMatchExact() {
        EvaluationTestCase tc = versionCase("*", Map.of("compat.jvm", "1.2.3"));

        EvaluationResult.TestCaseResult result = runPromise(() -> executor.execute(tc, context("1.2.4", "main")));

        assertThat(result.passed()).isFalse();
    }
}
