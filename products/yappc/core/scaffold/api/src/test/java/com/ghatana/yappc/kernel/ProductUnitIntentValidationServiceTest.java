/*
 * Copyright (c) 2026 Ghatana Platform Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ghatana.yappc.kernel;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for ProductUnitIntentValidationService.
 */
class ProductUnitIntentValidationServiceTest {

    @Test
    void validatesValidProductUnitIntent() {
        ProductUnitIntentValidationService validator = new ProductUnitIntentValidationService();

        Map<String, Object> intent = Map.of(
                "schemaVersion", "1.0.0",
                "intentId", "intent-123",
                "producer", Map.of(
                        "id", "yappc:workspace-123",
                        "type", "yappc"
                ),
                "target", Map.of(
                        "registryProvider", "ghatana-kernel",
                        "sourceProvider", "ghatana-file-registry"
                ),
                "productUnit", Map.of(
                        "id", "digital-marketing-campaign",
                        "name", "Digital Marketing Campaign",
                        "kind", "business-product",
                        "surfaces", List.of("web-api", "frontend"),
                        "lifecycleProfile", "standard-web-api-product"
                )
        );

        ProductUnitIntentValidationService.ValidationResult result = validator.validate(intent);

        assertThat(result.isValid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void rejectsMissingSchemaVersion() {
        ProductUnitIntentValidationService validator = new ProductUnitIntentValidationService();

        Map<String, Object> intent = Map.of(
                "intentId", "intent-123",
                "producer", Map.of(
                        "id", "yappc:workspace-123",
                        "type", "yappc"
                ),
                "target", Map.of(
                        "registryProvider", "ghatana-kernel",
                        "sourceProvider", "ghatana-file-registry"
                ),
                "productUnit", Map.of(
                        "id", "digital-marketing-campaign",
                        "name", "Digital Marketing Campaign",
                        "kind", "business-product",
                        "surfaces", List.of("web-api")
                )
        );

        ProductUnitIntentValidationService.ValidationResult result = validator.validate(intent);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).contains("schemaVersion is required");
    }

    @Test
    void rejectsInvalidSchemaVersion() {
        ProductUnitIntentValidationService validator = new ProductUnitIntentValidationService();

        Map<String, Object> intent = Map.of(
                "schemaVersion", "2.0.0",
                "intentId", "intent-123",
                "producer", Map.of(
                        "id", "yappc:workspace-123",
                        "type", "yappc"
                ),
                "target", Map.of(
                        "registryProvider", "ghatana-kernel",
                        "sourceProvider", "ghatana-file-registry"
                ),
                "productUnit", Map.of(
                        "id", "digital-marketing-campaign",
                        "name", "Digital Marketing Campaign",
                        "kind", "business-product",
                        "surfaces", List.of("web-api")
                )
        );

        ProductUnitIntentValidationService.ValidationResult result = validator.validate(intent);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).contains("schemaVersion must be '1.0.0', got: 2.0.0");
    }

    @Test
    void rejectsNonKebabCaseProductId() {
        ProductUnitIntentValidationService validator = new ProductUnitIntentValidationService();

        Map<String, Object> intent = Map.of(
                "schemaVersion", "1.0.0",
                "intentId", "intent-123",
                "producer", Map.of(
                        "id", "yappc:workspace-123",
                        "type", "yappc"
                ),
                "target", Map.of(
                        "registryProvider", "ghatana-kernel",
                        "sourceProvider", "ghatana-file-registry"
                ),
                "productUnit", Map.of(
                        "id", "Digital_Marketing_Campaign",
                        "name", "Digital Marketing Campaign",
                        "kind", "business-product",
                        "surfaces", List.of("web-api")
                )
        );

        ProductUnitIntentValidationService.ValidationResult result = validator.validate(intent);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).contains("productUnit.id must be kebab-case (lowercase with hyphens), got: Digital_Marketing_Campaign");
    }

    @Test
    void rejectsEmptySurfaces() {
        ProductUnitIntentValidationService validator = new ProductUnitIntentValidationService();

        Map<String, Object> intent = Map.of(
                "schemaVersion", "1.0.0",
                "intentId", "intent-123",
                "producer", Map.of(
                        "id", "yappc:workspace-123",
                        "type", "yappc"
                ),
                "target", Map.of(
                        "registryProvider", "ghatana-kernel",
                        "sourceProvider", "ghatana-file-registry"
                ),
                "productUnit", Map.of(
                        "id", "digital-marketing-campaign",
                        "name", "Digital Marketing Campaign",
                        "kind", "business-product",
                        "surfaces", List.of()
                )
        );

        ProductUnitIntentValidationService.ValidationResult result = validator.validate(intent);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).contains("productUnit.surfaces must be non-empty");
    }

    @Test
    void rejectsUnknownLifecycleProfile() {
        ProductUnitIntentValidationService validator = new ProductUnitIntentValidationService();

        Map<String, Object> intent = Map.of(
                "schemaVersion", "1.0.0",
                "intentId", "intent-123",
                "producer", Map.of(
                        "id", "yappc:workspace-123",
                        "type", "yappc"
                ),
                "target", Map.of(
                        "registryProvider", "ghatana-kernel",
                        "sourceProvider", "ghatana-file-registry"
                ),
                "productUnit", Map.of(
                        "id", "digital-marketing-campaign",
                        "name", "Digital Marketing Campaign",
                        "kind", "business-product",
                        "surfaces", List.of("web-api"),
                        "lifecycleProfile", "unknown-profile"
                )
        );

        ProductUnitIntentValidationService.ValidationResult result = validator.validate(intent);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).contains("productUnit.lifecycleProfile 'unknown-profile' is not a recognized profile");
    }

    @Test
    void rejectsSecretKeysInMetadata() {
        ProductUnitIntentValidationService validator = new ProductUnitIntentValidationService();

        Map<String, Object> intent = Map.of(
                "schemaVersion", "1.0.0",
                "intentId", "intent-123",
                "producer", Map.of(
                        "id", "yappc:workspace-123",
                        "type", "yappc"
                ),
                "target", Map.of(
                        "registryProvider", "ghatana-kernel",
                        "sourceProvider", "ghatana-file-registry"
                ),
                "productUnit", Map.of(
                        "id", "digital-marketing-campaign",
                        "name", "Digital Marketing Campaign",
                        "kind", "business-product",
                        "surfaces", List.of("web-api"),
                        "metadata", Map.of(
                                "apiSecret", "secret-value",
                                "databasePassword", "password-value"
                        )
                )
        );

        ProductUnitIntentValidationService.ValidationResult result = validator.validate(intent);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).anyMatch(error -> error.contains("apiSecret"));
        assertThat(result.errors()).anyMatch(error -> error.contains("databasePassword"));
    }

    @Test
    void rejectsKernelFilePathsInMetadataWhenNotFileRegistry() {
        ProductUnitIntentValidationService validator = new ProductUnitIntentValidationService();

        Map<String, Object> intent = Map.of(
                "schemaVersion", "1.0.0",
                "intentId", "intent-123",
                "producer", Map.of(
                        "id", "yappc:workspace-123",
                        "type", "yappc"
                ),
                "target", Map.of(
                        "registryProvider", "ghatana-kernel",
                        "sourceProvider", "ghatana-file-registry"
                ),
                "productUnit", Map.of(
                        "id", "digital-marketing-campaign",
                        "name", "Digital Marketing Campaign",
                        "kind", "business-product",
                        "surfaces", List.of("web-api"),
                        "metadata", Map.of(
                                "configPath", "config/canonical-product-registry.json",
                                "kernelPath", "/kernel/out/products/123"
                        )
                )
        );

        ProductUnitIntentValidationService.ValidationResult result = validator.validate(intent);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).anyMatch(error -> error.contains("Kernel-specific file path"));
    }

    @Test
    void allowsKernelFilePathsInMetadataWhenFileRegistry() {
        ProductUnitIntentValidationService validator = new ProductUnitIntentValidationService();

        Map<String, Object> intent = Map.of(
                "schemaVersion", "1.0.0",
                "intentId", "intent-123",
                "producer", Map.of(
                        "id", "yappc:workspace-123",
                        "type", "yappc"
                ),
                "target", Map.of(
                        "registryProvider", "ghatana-file-registry",
                        "sourceProvider", "ghatana-file-registry"
                ),
                "productUnit", Map.of(
                        "id", "digital-marketing-campaign",
                        "name", "Digital Marketing Campaign",
                        "kind", "business-product",
                        "surfaces", List.of("web-api"),
                        "metadata", Map.of(
                                "configPath", "config/canonical-product-registry.json"
                        )
                )
        );

        ProductUnitIntentValidationService.ValidationResult result = validator.validate(intent);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void allowsExternalProvider() {
        ProductUnitIntentValidationService validator = new ProductUnitIntentValidationService();

        Map<String, Object> intent = Map.of(
                "schemaVersion", "1.0.0",
                "intentId", "intent-123",
                "producer", Map.of(
                        "id", "yappc:workspace-123",
                        "type", "yappc"
                ),
                "target", Map.of(
                        "registryProvider", "external",
                        "sourceProvider", "external-git"
                ),
                "productUnit", Map.of(
                        "id", "digital-marketing-campaign",
                        "name", "Digital Marketing Campaign",
                        "kind", "business-product",
                        "surfaces", List.of("web-api")
                )
        );

        ProductUnitIntentValidationService.ValidationResult result = validator.validate(intent);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void isKebabCase() {
        ProductUnitIntentValidationService validator = new ProductUnitIntentValidationService();

        assertThat(validator.isKebabCase("valid-name")).isTrue();
        assertThat(validator.isKebabCase("valid-name-123")).isTrue();
        assertThat(validator.isKebabCase("InvalidName")).isFalse();
        assertThat(validator.isKebabCase("invalid_name")).isFalse();
        assertThat(validator.isKebabCase("")).isFalse();
        assertThat(validator.isKebabCase(null)).isFalse();
    }

    @Test
    void isRecognizedLifecycleProfile() {
        ProductUnitIntentValidationService validator = new ProductUnitIntentValidationService();

        assertThat(validator.isRecognizedLifecycleProfile("standard-web-api-product")).isTrue();
        assertThat(validator.isRecognizedLifecycleProfile("standard-polyglot-product")).isTrue();
        assertThat(validator.isRecognizedLifecycleProfile("backend-only-java-service")).isTrue();
        assertThat(validator.isRecognizedLifecycleProfile("mobile-plus-api-product")).isTrue();
        assertThat(validator.isRecognizedLifecycleProfile("platform-provider-product")).isTrue();
        assertThat(validator.isRecognizedLifecycleProfile("unknown")).isFalse();
        assertThat(validator.isRecognizedLifecycleProfile(null)).isFalse();
    }

    @Test
    void getRecognizedLifecycleProfiles() {
        ProductUnitIntentValidationService validator = new ProductUnitIntentValidationService();

        assertThat(validator.getRecognizedLifecycleProfiles())
                .containsExactlyInAnyOrder(
                        "standard-web-api-product",
                        "standard-polyglot-product",
                        "backend-only-java-service",
                        "mobile-plus-api-product",
                        "platform-provider-product");
    }
}
