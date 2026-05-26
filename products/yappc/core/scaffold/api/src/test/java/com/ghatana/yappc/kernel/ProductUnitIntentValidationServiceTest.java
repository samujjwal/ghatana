/*
 * Copyright (c) 2026 Ghatana Platform Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.ghatana.yappc.kernel;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for ProductUnitIntentValidationService.
 *
 * @doc.type test
 * @doc.purpose Verifies ProductUnitIntent validation uses canonical Kernel contract shape
 * @doc.layer product
 * @doc.pattern Test
 */
class ProductUnitIntentValidationServiceTest {

    @Test
    void validatesValidProductUnitIntent() {
        ProductUnitIntentValidationService validator = new ProductUnitIntentValidationService();

        ProductUnitIntentValidationService.ValidationResult result = validator.validate(validIntent());

        assertThat(result.isValid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void rejectsMissingCanonicalIntentType() {
        ProductUnitIntentValidationService validator = new ProductUnitIntentValidationService();
        Map<String, Object> intent = new java.util.HashMap<>(validIntent());
        intent.remove("intentType");

        ProductUnitIntentValidationService.ValidationResult result = validator.validate(intent);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).contains("intentType is required");
    }

    @Test
    void rejectsMissingScope() {
        ProductUnitIntentValidationService validator = new ProductUnitIntentValidationService();
        Map<String, Object> intent = new java.util.HashMap<>(validIntent());
        intent.remove("scope");

        ProductUnitIntentValidationService.ValidationResult result = validator.validate(intent);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).contains("scope is required");
    }

    @Test
    void rejectsUnknownProvider() {
        ProductUnitIntentValidationService validator = new ProductUnitIntentValidationService();
        Map<String, Object> intent = validIntentWithTarget(Map.of(
                "registryProvider", "ghatana-kernel",
                "sourceProvider", "ghatana-file-registry"));

        ProductUnitIntentValidationService.ValidationResult result = validator.validate(intent);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).contains(
                "target.registryProvider 'ghatana-kernel' is not a known provider. Mark as 'external' if intentional.");
    }

    @Test
    void rejectsUnknownProductUnitKind() {
        ProductUnitIntentValidationService validator = new ProductUnitIntentValidationService();
        Map<String, Object> productUnit = new java.util.HashMap<>(productUnit());
        productUnit.put("kind", "unknown-kind");
        Map<String, Object> intent = validIntentWithProductUnit(productUnit);

        ProductUnitIntentValidationService.ValidationResult result = validator.validate(intent);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).contains("productUnit.kind 'unknown-kind' is not a known Kernel ProductUnit kind");
    }

    @Test
    void rejectsUnknownSurfaceType() {
        ProductUnitIntentValidationService validator = new ProductUnitIntentValidationService();
        Map<String, Object> productUnit = new java.util.HashMap<>(productUnit());
        productUnit.put("surfaces", List.of(surface("product-unknown", "frontend", "planned")));
        Map<String, Object> intent = validIntentWithProductUnit(productUnit);

        ProductUnitIntentValidationService.ValidationResult result = validator.validate(intent);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).contains("productUnit.surfaces contains unknown surface type: frontend");
    }

    @Test
    void rejectsUnknownImplementationStatus() {
        ProductUnitIntentValidationService validator = new ProductUnitIntentValidationService();
        Map<String, Object> productUnit = new java.util.HashMap<>(productUnit());
        productUnit.put("surfaces", List.of(surface("product-web", "web", "done-ish")));
        Map<String, Object> intent = validIntentWithProductUnit(productUnit);

        ProductUnitIntentValidationService.ValidationResult result = validator.validate(intent);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).contains("productUnit.surfaces contains unknown implementation status: done-ish");
    }

    @Test
    void rejectsLegacyStringSurfaceShape() {
        ProductUnitIntentValidationService validator = new ProductUnitIntentValidationService();
        Map<String, Object> productUnit = new java.util.HashMap<>(productUnit());
        productUnit.put("surfaces", List.of("web"));
        Map<String, Object> intent = validIntentWithProductUnit(productUnit);

        ProductUnitIntentValidationService.ValidationResult result = validator.validate(intent);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).contains("productUnit.surfaces entries must be Kernel ProductUnitSurface objects");
    }

    @Test
    void rejectsKernelFilePathsInMetadataWhenNotFileRegistry() {
        ProductUnitIntentValidationService validator = new ProductUnitIntentValidationService();
        Map<String, Object> productUnit = new java.util.HashMap<>(productUnit());
        productUnit.put("metadata", Map.of("configPath", "config/canonical-product-registry.json"));
        Map<String, Object> intent = validIntentWithTargetAndProductUnit(
                Map.of("registryProvider", "external", "sourceProvider", "external-git"),
                productUnit);

        ProductUnitIntentValidationService.ValidationResult result = validator.validate(intent);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).anyMatch(error -> error.contains("Kernel-specific file path"));
    }

    @Test
    void allowsKernelFilePathsInMetadataWhenFileRegistry() {
        ProductUnitIntentValidationService validator = new ProductUnitIntentValidationService();
        Map<String, Object> productUnit = new java.util.HashMap<>(productUnit());
        productUnit.put("metadata", Map.of("configPath", "config/canonical-product-registry.json"));
        Map<String, Object> intent = validIntentWithProductUnit(productUnit);

        ProductUnitIntentValidationService.ValidationResult result = validator.validate(intent);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void exposesRecognizedLifecycleProfiles() {
        ProductUnitIntentValidationService validator = new ProductUnitIntentValidationService();

        assertThat(validator.isRecognizedLifecycleProfile("standard-web-api-product")).isTrue();
        assertThat(validator.isRecognizedLifecycleProfile("unknown")).isFalse();
        assertThat(validator.getRecognizedLifecycleProfiles())
                .contains("standard-web-api-product", "backend-only-java-service");
    }

    private static Map<String, Object> validIntent() {
        return validIntentWithProductUnit(productUnit());
    }

    private static Map<String, Object> validIntentWithProductUnit(Map<String, Object> productUnit) {
        return validIntentWithTargetAndProductUnit(
                Map.of("registryProvider", "ghatana-file-registry", "sourceProvider", "ghatana-file-registry"),
                productUnit);
    }

    private static Map<String, Object> validIntentWithTarget(Map<String, Object> target) {
        return validIntentWithTargetAndProductUnit(target, productUnit());
    }

    private static Map<String, Object> validIntentWithTargetAndProductUnit(
            Map<String, Object> target,
            Map<String, Object> productUnit
    ) {
        return Map.of(
                "schemaVersion", "1.0.0",
                "intentId", "intent-123",
                "intentType", "create",
                "scope", Map.of(
                        "tenantId", "tenant-123",
                        "workspaceId", "workspace-123",
                        "projectId", "digital-marketing-campaign"),
                "producer", Map.of(
                        "id", "yappc:workspace-123",
                        "type", "yappc",
                        "correlationId", "corr-123"),
                "target", target,
                "productUnit", productUnit);
    }

    private static Map<String, Object> productUnit() {
        return Map.of(
                "id", "digital-marketing-campaign",
                "name", "Digital Marketing Campaign",
                "kind", "business-product",
                "surfaces", List.of(
                        surface("digital-marketing-campaign-backend-api", "backend-api", "planned"),
                        surface("digital-marketing-campaign-web", "web", "planned")),
                "lifecycleProfile", "standard-web-api-product");
    }

    private static Map<String, Object> surface(String id, String type, String implementationStatus) {
        return Map.of(
                "id", id,
                "type", type,
                "implementationStatus", implementationStatus);
    }
}
