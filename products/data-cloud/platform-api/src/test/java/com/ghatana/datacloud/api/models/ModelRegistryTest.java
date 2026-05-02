/*
 * Copyright (c) 2026 Ghatana Inc. 
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
@DisplayName("Model Registry Tests")
class ModelRegistryTest extends EventloopTestBase {

    // ── Model lifecycle model ─────────────────────────────────────────────────

    enum ModelStatus { DRAFT, STAGING, PRODUCTION, DEPRECATED, DELETED }

    record ModelVersion(String modelId, String version, String tenantId, 
                        ModelStatus status, Instant createdAt, Map<String, String> metadata) {
        ModelVersion withStatus(ModelStatus newStatus) { 
            return new ModelVersion(modelId, version, tenantId, newStatus, createdAt, metadata); 
        }
    }

    private ModelRegistry registry;

    @BeforeEach
    void setUp() { 
        registry = new ModelRegistry(); 
    }

    // ── Registration ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("register creates a new model in DRAFT status")
    void registerCreatesModelInDraftStatus() { 
        ModelVersion model = registry.register("model-llm", "1.0.0", "tenant-A", 
                Map.of("framework", "transformers")); 

        assertThat(model.modelId()).isEqualTo("model-llm");
        assertThat(model.version()).isEqualTo("1.0.0");
        assertThat(model.status()).isEqualTo(ModelStatus.DRAFT); 
        assertThat(model.tenantId()).isEqualTo("tenant-A");
        assertThat(model.createdAt()).isNotNull(); 
    }

    @Test
    @DisplayName("registered model can be retrieved by ID and version")
    void registeredModelCanBeRetrievedByIdAndVersion() { 
        registry.register("model-clas", "2.1.0", "tenant-B", Map.of()); 

        Optional<ModelVersion> found = registry.find("model-clas", "2.1.0", "tenant-B"); 
        assertThat(found).isPresent(); 
        assertThat(found.get().version()).isEqualTo("2.1.0");
    }

    @Test
    @DisplayName("registering a duplicate model+version+tenant throws an exception")
    void registerDuplicateThrowsException() { 
        registry.register("model-dup", "1.0.0", "tenant-dup", Map.of()); 

        assertThatThrownBy(() -> registry.register("model-dup", "1.0.0", "tenant-dup", Map.of())) 
                .isInstanceOf(IllegalStateException.class) 
                .hasMessageContaining("already registered");
    }

    // ── Versioning ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("multiple versions of the same model can be registered independently")
    void multipleVersionsRegisteredIndependently() { 
        registry.register("model-ver", "1.0.0", "tenant-V", Map.of()); 
        registry.register("model-ver", "1.1.0", "tenant-V", Map.of()); 
        registry.register("model-ver", "2.0.0", "tenant-V", Map.of()); 

        List<ModelVersion> versions = registry.listVersions("model-ver", "tenant-V"); 
        assertThat(versions).hasSize(3); 
        List<String> versionNums = versions.stream().map(ModelVersion::version).toList(); 
        assertThat(versionNums).containsExactlyInAnyOrder("1.0.0", "1.1.0", "2.0.0"); 
    }

    @Test
    @DisplayName("list returns an empty list for a model with no versions")
    void listVersionsEmptyForNonExistentModel() { 
        List<ModelVersion> versions = registry.listVersions("model-ghost", "tenant-G"); 
        assertThat(versions).isEmpty(); 
    }

    // ── Promotion ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("promote to STAGING transitions model from DRAFT")
    void promoteToStaging() { 
        registry.register("model-promo", "1.0.0", "tenant-P", Map.of()); 
        ModelVersion promoted = registry.promote("model-promo", "1.0.0", "tenant-P", ModelStatus.STAGING); 

        assertThat(promoted.status()).isEqualTo(ModelStatus.STAGING); 
    }

    @Test
    @DisplayName("promote to PRODUCTION transitions model from STAGING")
    void promoteToProduction() { 
        registry.register("model-prod", "1.0.0", "tenant-P", Map.of()); 
        registry.promote("model-prod", "1.0.0", "tenant-P", ModelStatus.STAGING); 
        ModelVersion inProd = registry.promote("model-prod", "1.0.0", "tenant-P", ModelStatus.PRODUCTION); 

        assertThat(inProd.status()).isEqualTo(ModelStatus.PRODUCTION); 
    }

    @Test
    @DisplayName("only one PRODUCTION version exists for a given model+tenant")
    void onlyOneProductionVersionExists() { 
        registry.register("model-mp", "1.0.0", "tenant-MP", Map.of()); 
        registry.register("model-mp", "2.0.0", "tenant-MP", Map.of()); 
        registry.promote("model-mp", "1.0.0", "tenant-MP", ModelStatus.STAGING); 
        registry.promote("model-mp", "1.0.0", "tenant-MP", ModelStatus.PRODUCTION); 
        registry.promote("model-mp", "2.0.0", "tenant-MP", ModelStatus.STAGING); 
        registry.promote("model-mp", "2.0.0", "tenant-MP", ModelStatus.PRODUCTION); 

        List<ModelVersion> inProd = registry.listVersions("model-mp", "tenant-MP").stream() 
                .filter(m -> m.status() == ModelStatus.PRODUCTION) 
                .toList(); 
        assertThat(inProd).hasSize(1); 
        assertThat(inProd.get(0).version()).isEqualTo("2.0.0");
    }

    // ── Deprecation ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("deprecating a model marks it as DEPRECATED and removes from production")
    void deprecateMarksModelDeprecated() { 
        registry.register("model-dep", "1.0.0", "tenant-D", Map.of()); 
        registry.promote("model-dep", "1.0.0", "tenant-D", ModelStatus.STAGING); 
        registry.promote("model-dep", "1.0.0", "tenant-D", ModelStatus.PRODUCTION); 
        ModelVersion deprecated = registry.deprecate("model-dep", "1.0.0", "tenant-D"); 

        assertThat(deprecated.status()).isEqualTo(ModelStatus.DEPRECATED); 
    }

    @Test
    @DisplayName("deprecated model can not be promoted to PRODUCTION")
    void deprecatedModelCannotBePromotedToProduction() { 
        registry.register("model-nd", "1.0.0", "tenant-ND", Map.of()); 
        registry.deprecate("model-nd", "1.0.0", "tenant-ND"); 

        assertThatThrownBy(() -> 
                registry.promote("model-nd", "1.0.0", "tenant-ND", ModelStatus.PRODUCTION)) 
                .isInstanceOf(IllegalStateException.class); 
    }

    // ── Deletion ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("deleting a model removes it from the registry entirely")
    void deletingModelRemovesItFromRegistry() { 
        registry.register("model-del", "1.0.0", "tenant-Del", Map.of()); 
        registry.delete("model-del", "1.0.0", "tenant-Del"); 

        Optional<ModelVersion> found = registry.find("model-del", "1.0.0", "tenant-Del"); 
        assertThat(found).isEmpty(); 
    }

    @Test
    @DisplayName("production model cannot be deleted without deprecation first")
    void productionModelCannotBeDeletedDirectly() { 
        registry.register("model-pd", "1.0.0", "tenant-PD", Map.of()); 
        registry.promote("model-pd", "1.0.0", "tenant-PD", ModelStatus.STAGING); 
        registry.promote("model-pd", "1.0.0", "tenant-PD", ModelStatus.PRODUCTION); 

        assertThatThrownBy(() -> registry.delete("model-pd", "1.0.0", "tenant-PD")) 
                .isInstanceOf(IllegalStateException.class) 
                .hasMessageContaining("production");
    }

    // ── Model registry implementation (for tests) ───────────────────────────── 

    static class ModelRegistry {
        private final ConcurrentHashMap<String, ModelVersion> store = new ConcurrentHashMap<>(); 

        private String key(String modelId, String version, String tenantId) { 
            return tenantId + "|" + modelId + "|" + version;
        }

        ModelVersion register(String modelId, String version, String tenantId, 
                              Map<String, String> metadata) {
            String k = key(modelId, version, tenantId); 
            if (store.containsKey(k)) { 
                throw new IllegalStateException("Model already registered: " + k); 
            }
            ModelVersion model = new ModelVersion(modelId, version, tenantId, 
                    ModelStatus.DRAFT, Instant.now(), metadata); 
            store.put(k, model); 
            return model;
        }

        Optional<ModelVersion> find(String modelId, String version, String tenantId) { 
            return Optional.ofNullable(store.get(key(modelId, version, tenantId))); 
        }

        List<ModelVersion> listVersions(String modelId, String tenantId) { 
            return store.values().stream() 
                    .filter(m -> m.modelId().equals(modelId) && m.tenantId().equals(tenantId) 
                            && m.status() != ModelStatus.DELETED) 
                    .toList(); 
        }

        ModelVersion promote(String modelId, String version, String tenantId, ModelStatus newStatus) { 
            String k = key(modelId, version, tenantId); 
            ModelVersion current = store.get(k); 
            if (current == null) throw new NoSuchElementException("Model not found: " + k); 
            if (current.status() == ModelStatus.DEPRECATED || current.status() == ModelStatus.DELETED) { 
                throw new IllegalStateException("Cannot promote " + current.status() + " model"); 
            }
            if (newStatus == ModelStatus.PRODUCTION) { 
                // Demote any existing PRODUCTION version for same model+tenant to DEPRECATED
                store.entrySet().stream() 
                        .filter(e -> e.getValue().modelId().equals(modelId) 
                                && e.getValue().tenantId().equals(tenantId) 
                                && e.getValue().status() == ModelStatus.PRODUCTION) 
                        .forEach(e -> store.put(e.getKey(), e.getValue().withStatus(ModelStatus.DEPRECATED))); 
            }
            ModelVersion updated = current.withStatus(newStatus); 
            store.put(k, updated); 
            return updated;
        }

        ModelVersion deprecate(String modelId, String version, String tenantId) { 
            return promote(modelId, version, tenantId, ModelStatus.DEPRECATED); 
        }

        void delete(String modelId, String version, String tenantId) { 
            String k = key(modelId, version, tenantId); 
            ModelVersion model = store.get(k); 
            if (model == null) return; 
            if (model.status() == ModelStatus.PRODUCTION) { 
                throw new IllegalStateException("Cannot delete a model in production status");
            }
            store.remove(k); 
        }
    }
}
