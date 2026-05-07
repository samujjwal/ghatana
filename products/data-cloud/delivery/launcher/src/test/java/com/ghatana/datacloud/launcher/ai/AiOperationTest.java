/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.ai;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Tests for AiOperation domain model
 * @doc.layer product
 * @doc.pattern Test
 */
class AiOperationTest {

    @Test
    void shouldCreateConfidenceBandWithClampedScore() {
        AiOperation.ConfidenceBand band = new AiOperation.ConfidenceBand(1.5, "HIGH", List.of("factor1", "factor2"));

        assertThat(band.score()).isEqualTo(1.0);
        assertThat(band.label()).isEqualTo("HIGH");
        assertThat(band.factors()).containsExactly("factor1", "factor2");
    }

    @Test
    void shouldCreateConfidenceBandWithNegativeScore() {
        AiOperation.ConfidenceBand band = new AiOperation.ConfidenceBand(-0.5, "LOW", List.of());

        assertThat(band.score()).isEqualTo(0.0);
    }

    @Test
    void shouldCreateConfidenceBandWithNullFactors() {
        AiOperation.ConfidenceBand band = new AiOperation.ConfidenceBand(0.7, "MEDIUM", null);

        assertThat(band.factors()).isEmpty();
    }

    @Test
    void shouldCreateConfidenceBandUsingFactoryMethodHigh() {
        AiOperation.ConfidenceBand band = AiOperation.ConfidenceBand.of(0.9, List.of("strong evidence"));

        assertThat(band.score()).isEqualTo(0.9);
        assertThat(band.label()).isEqualTo("HIGH");
    }

    @Test
    void shouldCreateConfidenceBandUsingFactoryMethodMedium() {
        AiOperation.ConfidenceBand band = AiOperation.ConfidenceBand.of(0.7, List.of("some evidence"));

        assertThat(band.score()).isEqualTo(0.7);
        assertThat(band.label()).isEqualTo("MEDIUM");
    }

    @Test
    void shouldCreateConfidenceBandUsingFactoryMethodLow() {
        AiOperation.ConfidenceBand band = AiOperation.ConfidenceBand.of(0.4, List.of("weak evidence"));

        assertThat(band.score()).isEqualTo(0.4);
        assertThat(band.label()).isEqualTo("LOW");
    }

    @Test
    void shouldCreateSuggestionWithNullAlternativeActions() {
        AiOperation.Suggestion suggestion = new AiOperation.Suggestion(
            "type", "content", new AiOperation.ConfidenceBand(0.8, "HIGH", List.of()), false, null
        );

        assertThat(suggestion.alternativeActions()).isEmpty();
    }

    @Test
    void shouldCreateActionWithNullParameters() {
        AiOperation.Action action = new AiOperation.Action("type", "payload", null, true);

        assertThat(action.parameters()).isEmpty();
    }

    @Test
    void shouldCreateReviewPolicyWithNullLists() {
        AiOperation.ReviewPolicy policy = new AiOperation.ReviewPolicy(
            true, 0.8, null, 2, null
        );

        assertThat(policy.requiredApprovers()).isEmpty();
        assertThat(policy.exemptSurfaces()).isEmpty();
    }

    @Test
    void shouldClampAutoApplyThresholdAboveOne() {
        AiOperation.ReviewPolicy policy = new AiOperation.ReviewPolicy(
            false, 1.5, List.of(), 1, List.of()
        );

        assertThat(policy.autoApplyThreshold()).isEqualTo(1.0);
    }

    @Test
    void shouldClampAutoApplyThresholdBelowZero() {
        AiOperation.ReviewPolicy policy = new AiOperation.ReviewPolicy(
            false, -0.5, List.of(), 1, List.of()
        );

        assertThat(policy.autoApplyThreshold()).isEqualTo(0.0);
    }

    @Test
    void shouldNotAutoApplyWhenManualReviewRequired() {
        AiOperation.ReviewPolicy policy = new AiOperation.ReviewPolicy(
            true, 0.8, List.of(), 1, List.of()
        );

        assertThat(policy.canAutoApply(0.9, "surface")).isFalse();
    }

    @Test
    void shouldAutoApplyWhenSurfaceIsExempt() {
        AiOperation.ReviewPolicy policy = new AiOperation.ReviewPolicy(
            false, 0.8, List.of(), 1, List.of("exempt-surface")
        );

        assertThat(policy.canAutoApply(0.5, "exempt-surface")).isTrue();
    }

    @Test
    void shouldAutoApplyWhenConfidenceAboveThreshold() {
        AiOperation.ReviewPolicy policy = new AiOperation.ReviewPolicy(
            false, 0.8, List.of(), 1, List.of()
        );

        assertThat(policy.canAutoApply(0.9, "surface")).isTrue();
    }

    @Test
    void shouldNotAutoApplyWhenConfidenceBelowThreshold() {
        AiOperation.ReviewPolicy policy = new AiOperation.ReviewPolicy(
            false, 0.8, List.of(), 1, List.of()
        );

        assertThat(policy.canAutoApply(0.7, "surface")).isFalse();
    }

    @Test
    void shouldBuildAiOperationUsingBuilder() {
        AiOperation.ConfidenceBand confidence = new AiOperation.ConfidenceBand(0.9, "HIGH", List.of());
        AiOperation.Suggestion suggestion = new AiOperation.Suggestion("type", "content", confidence, false, List.of());
        AiOperation.Action action = new AiOperation.Action("action-type", "payload", Map.of("key", "value"), true);
        AiOperation.Provenance provenance = new AiOperation.Provenance(
            "provider", "model", "v1", 100, 10, 20, 30, "stop", Instant.now()
        );
        AiOperation.ReviewPolicy reviewPolicy = new AiOperation.ReviewPolicy(false, 0.8, List.of(), 1, List.of());
        AiOperation.Lifecycle lifecycle = new AiOperation.Lifecycle(
            AiOperation.Lifecycle.STATUS_PENDING, null, null, null, null, null
        );
        AiOperation.InputFeature feature = new AiOperation.InputFeature("name", "type", "value", 1.0);
        AiOperation.AuditEventLink auditEvent = new AiOperation.AuditEventLink("event-id", "event-type", Instant.now());

        AiOperation operation = AiOperation.builder()
            .operationId("op-123")
            .tenantId("tenant-123")
            .targetSurface("surface")
            .suggestion(suggestion)
            .action(action)
            .provenance(provenance)
            .reviewPolicy(reviewPolicy)
            .lifecycle(lifecycle)
            .inputFeatures(List.of(feature))
            .auditEvent(auditEvent)
            .build();

        assertThat(operation.operationId()).isEqualTo("op-123");
        assertThat(operation.tenantId()).isEqualTo("tenant-123");
        assertThat(operation.targetSurface()).isEqualTo("surface");
        assertThat(operation.suggestion()).isEqualTo(suggestion);
        assertThat(operation.action()).isEqualTo(action);
        assertThat(operation.provenance()).isEqualTo(provenance);
        assertThat(operation.reviewPolicy()).isEqualTo(reviewPolicy);
        assertThat(operation.lifecycle()).isEqualTo(lifecycle);
        assertThat(operation.inputFeatures()).containsExactly(feature);
        assertThat(operation.auditEvent()).isEqualTo(auditEvent);
    }

    @Test
    void shouldBuildAiOperationWithDefaults() {
        AiOperation operation = AiOperation.builder()
            .tenantId("tenant-123")
            .targetSurface("surface")
            .build();

        assertThat(operation.operationId()).isNotEmpty();
        assertThat(operation.lifecycle().status()).isEqualTo(AiOperation.Lifecycle.STATUS_PENDING);
        assertThat(operation.inputFeatures()).isEmpty();
    }
}
