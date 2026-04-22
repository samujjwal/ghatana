/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.api.models;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for model registry — registration, versioning, promotion, deprecation,
 * and deletion lifecycle.
 *
 * @doc.type    class
 * @doc.purpose Tests for model lifecycle management in the model registry
 * @doc.layer   product
 * @doc.pattern Test
 */
@DisplayName("Model Registry Tests [GH-90000]")
class ModelRegistryTest extends EventloopTestBase {

    // ── Model lifecycle model ─────────────────────────────────────────────────

    enum ModelStatus { DRAFT, STAGING, PRODUCTION, DEPRECATED, DELETED }

    record ModelVersion(String modelId, String version, String tenantId, // GH-90000
                        ModelStatus status, Instant createdAt, Map<String, String> metadata) {
        ModelVersion withStatus(ModelStatus newStatus) { // GH-90000
            return new ModelVersion(modelId, version, tenantId, newStatus, createdAt, metadata); // GH-90000
        }
    }

    private ModelRegistry registry;

    @BeforeEach
    void setUp() { // GH-90000
        registry = new ModelRegistry(); // GH-90000
    }

    // ── Registration ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("register creates a new model in DRAFT status [GH-90000]")
    void registerCreatesModelInDraftStatus() { // GH-90000
        ModelVersion model = registry.register("model-llm", "1.0.0", "tenant-A", // GH-90000
                Map.of("framework", "transformers")); // GH-90000

        assertThat(model.modelId()).isEqualTo("model-llm [GH-90000]");
        assertThat(model.version()).isEqualTo("1.0.0 [GH-90000]");
        assertThat(model.status()).isEqualTo(ModelStatus.DRAFT); // GH-90000
        assertThat(model.tenantId()).isEqualTo("tenant-A [GH-90000]");
        assertThat(model.createdAt()).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("registered model can be retrieved by ID and version [GH-90000]")
    void registeredModelCanBeRetrievedByIdAndVersion() { // GH-90000
        registry.register("model-clas", "2.1.0", "tenant-B", Map.of()); // GH-90000

        Optional<ModelVersion> found = registry.find("model-clas", "2.1.0", "tenant-B"); // GH-90000
        assertThat(found).isPresent(); // GH-90000
        assertThat(found.get().version()).isEqualTo("2.1.0 [GH-90000]");
    }

    @Test
    @DisplayName("registering a duplicate model+version+tenant throws an exception [GH-90000]")
    void registerDuplicateThrowsException() { // GH-90000
        registry.register("model-dup", "1.0.0", "tenant-dup", Map.of()); // GH-90000

        assertThatThrownBy(() -> registry.register("model-dup", "1.0.0", "tenant-dup", Map.of())) // GH-90000
                .isInstanceOf(IllegalStateException.class) // GH-90000
                .hasMessageContaining("already registered [GH-90000]");
    }

    // ── Versioning ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("multiple versions of the same model can be registered independently [GH-90000]")
    void multipleVersionsRegisteredIndependently() { // GH-90000
        registry.register("model-ver", "1.0.0", "tenant-V", Map.of()); // GH-90000
        registry.register("model-ver", "1.1.0", "tenant-V", Map.of()); // GH-90000
        registry.register("model-ver", "2.0.0", "tenant-V", Map.of()); // GH-90000

        List<ModelVersion> versions = registry.listVersions("model-ver", "tenant-V"); // GH-90000
        assertThat(versions).hasSize(3); // GH-90000
        List<String> versionNums = versions.stream().map(ModelVersion::version).toList(); // GH-90000
        assertThat(versionNums).containsExactlyInAnyOrder("1.0.0", "1.1.0", "2.0.0"); // GH-90000
    }

    @Test
    @DisplayName("list returns an empty list for a model with no versions [GH-90000]")
    void listVersionsEmptyForNonExistentModel() { // GH-90000
        List<ModelVersion> versions = registry.listVersions("model-ghost", "tenant-G"); // GH-90000
        assertThat(versions).isEmpty(); // GH-90000
    }

    // ── Promotion ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("promote to STAGING transitions model from DRAFT [GH-90000]")
    void promoteToStaging() { // GH-90000
        registry.register("model-promo", "1.0.0", "tenant-P", Map.of()); // GH-90000
        ModelVersion promoted = registry.promote("model-promo", "1.0.0", "tenant-P", ModelStatus.STAGING); // GH-90000

        assertThat(promoted.status()).isEqualTo(ModelStatus.STAGING); // GH-90000
    }

    @Test
    @DisplayName("promote to PRODUCTION transitions model from STAGING [GH-90000]")
    void promoteToProduction() { // GH-90000
        registry.register("model-prod", "1.0.0", "tenant-P", Map.of()); // GH-90000
        registry.promote("model-prod", "1.0.0", "tenant-P", ModelStatus.STAGING); // GH-90000
        ModelVersion inProd = registry.promote("model-prod", "1.0.0", "tenant-P", ModelStatus.PRODUCTION); // GH-90000

        assertThat(inProd.status()).isEqualTo(ModelStatus.PRODUCTION); // GH-90000
    }

    @Test
    @DisplayName("only one PRODUCTION version exists for a given model+tenant [GH-90000]")
    void onlyOneProductionVersionExists() { // GH-90000
        registry.register("model-mp", "1.0.0", "tenant-MP", Map.of()); // GH-90000
        registry.register("model-mp", "2.0.0", "tenant-MP", Map.of()); // GH-90000
        registry.promote("model-mp", "1.0.0", "tenant-MP", ModelStatus.STAGING); // GH-90000
        registry.promote("model-mp", "1.0.0", "tenant-MP", ModelStatus.PRODUCTION); // GH-90000
        registry.promote("model-mp", "2.0.0", "tenant-MP", ModelStatus.STAGING); // GH-90000
        registry.promote("model-mp", "2.0.0", "tenant-MP", ModelStatus.PRODUCTION); // GH-90000

        List<ModelVersion> inProd = registry.listVersions("model-mp", "tenant-MP").stream() // GH-90000
                .filter(m -> m.status() == ModelStatus.PRODUCTION) // GH-90000
                .toList(); // GH-90000
        assertThat(inProd).hasSize(1); // GH-90000
        assertThat(inProd.get(0).version()).isEqualTo("2.0.0 [GH-90000]");
    }

    // ── Deprecation ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("deprecating a model marks it as DEPRECATED and removes from production [GH-90000]")
    void deprecateMarksModelDeprecated() { // GH-90000
        registry.register("model-dep", "1.0.0", "tenant-D", Map.of()); // GH-90000
        registry.promote("model-dep", "1.0.0", "tenant-D", ModelStatus.STAGING); // GH-90000
        registry.promote("model-dep", "1.0.0", "tenant-D", ModelStatus.PRODUCTION); // GH-90000
        ModelVersion deprecated = registry.deprecate("model-dep", "1.0.0", "tenant-D"); // GH-90000

        assertThat(deprecated.status()).isEqualTo(ModelStatus.DEPRECATED); // GH-90000
    }

    @Test
    @DisplayName("deprecated model can not be promoted to PRODUCTION [GH-90000]")
    void deprecatedModelCannotBePromotedToProduction() { // GH-90000
        registry.register("model-nd", "1.0.0", "tenant-ND", Map.of()); // GH-90000
        registry.deprecate("model-nd", "1.0.0", "tenant-ND"); // GH-90000

        assertThatThrownBy(() -> // GH-90000
                registry.promote("model-nd", "1.0.0", "tenant-ND", ModelStatus.PRODUCTION)) // GH-90000
                .isInstanceOf(IllegalStateException.class); // GH-90000
    }

    // ── Deletion ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("deleting a model removes it from the registry entirely [GH-90000]")
    void deletingModelRemovesItFromRegistry() { // GH-90000
        registry.register("model-del", "1.0.0", "tenant-Del", Map.of()); // GH-90000
        registry.delete("model-del", "1.0.0", "tenant-Del"); // GH-90000

        Optional<ModelVersion> found = registry.find("model-del", "1.0.0", "tenant-Del"); // GH-90000
        assertThat(found).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("production model cannot be deleted without deprecation first [GH-90000]")
    void productionModelCannotBeDeletedDirectly() { // GH-90000
        registry.register("model-pd", "1.0.0", "tenant-PD", Map.of()); // GH-90000
        registry.promote("model-pd", "1.0.0", "tenant-PD", ModelStatus.STAGING); // GH-90000
        registry.promote("model-pd", "1.0.0", "tenant-PD", ModelStatus.PRODUCTION); // GH-90000

        assertThatThrownBy(() -> registry.delete("model-pd", "1.0.0", "tenant-PD")) // GH-90000
                .isInstanceOf(IllegalStateException.class) // GH-90000
                .hasMessageContaining("production [GH-90000]");
    }

    // ── Model registry implementation (for tests) ───────────────────────────── // GH-90000

    static class ModelRegistry {
        private final ConcurrentHashMap<String, ModelVersion> store = new ConcurrentHashMap<>(); // GH-90000

        private String key(String modelId, String version, String tenantId) { // GH-90000
            return tenantId + "|" + modelId + "|" + version;
        }

        ModelVersion register(String modelId, String version, String tenantId, // GH-90000
                              Map<String, String> metadata) {
            String k = key(modelId, version, tenantId); // GH-90000
            if (store.containsKey(k)) { // GH-90000
                throw new IllegalStateException("Model already registered: " + k); // GH-90000
            }
            ModelVersion model = new ModelVersion(modelId, version, tenantId, // GH-90000
                    ModelStatus.DRAFT, Instant.now(), metadata); // GH-90000
            store.put(k, model); // GH-90000
            return model;
        }

        Optional<ModelVersion> find(String modelId, String version, String tenantId) { // GH-90000
            return Optional.ofNullable(store.get(key(modelId, version, tenantId))); // GH-90000
        }

        List<ModelVersion> listVersions(String modelId, String tenantId) { // GH-90000
            return store.values().stream() // GH-90000
                    .filter(m -> m.modelId().equals(modelId) && m.tenantId().equals(tenantId) // GH-90000
                            && m.status() != ModelStatus.DELETED) // GH-90000
                    .toList(); // GH-90000
        }

        ModelVersion promote(String modelId, String version, String tenantId, ModelStatus newStatus) { // GH-90000
            String k = key(modelId, version, tenantId); // GH-90000
            ModelVersion current = store.get(k); // GH-90000
            if (current == null) throw new NoSuchElementException("Model not found: " + k); // GH-90000
            if (current.status() == ModelStatus.DEPRECATED || current.status() == ModelStatus.DELETED) { // GH-90000
                throw new IllegalStateException("Cannot promote " + current.status() + " model"); // GH-90000
            }
            if (newStatus == ModelStatus.PRODUCTION) { // GH-90000
                // Demote any existing PRODUCTION version for same model+tenant to DEPRECATED
                store.entrySet().stream() // GH-90000
                        .filter(e -> e.getValue().modelId().equals(modelId) // GH-90000
                                && e.getValue().tenantId().equals(tenantId) // GH-90000
                                && e.getValue().status() == ModelStatus.PRODUCTION) // GH-90000
                        .forEach(e -> store.put(e.getKey(), e.getValue().withStatus(ModelStatus.DEPRECATED))); // GH-90000
            }
            ModelVersion updated = current.withStatus(newStatus); // GH-90000
            store.put(k, updated); // GH-90000
            return updated;
        }

        ModelVersion deprecate(String modelId, String version, String tenantId) { // GH-90000
            return promote(modelId, version, tenantId, ModelStatus.DEPRECATED); // GH-90000
        }

        void delete(String modelId, String version, String tenantId) { // GH-90000
            String k = key(modelId, version, tenantId); // GH-90000
            ModelVersion model = store.get(k); // GH-90000
            if (model == null) return; // GH-90000
            if (model.status() == ModelStatus.PRODUCTION) { // GH-90000
                throw new IllegalStateException("Cannot delete a model in production status [GH-90000]");
            }
            store.remove(k); // GH-90000
        }
    }
}
