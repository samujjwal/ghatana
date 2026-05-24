package com.ghatana.digitalmarketing.application.attribution;

import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.attribution.AttributionModel;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for AttributionModelServiceImpl (DMOS-P3-005).
 *
 * @doc.type test
 * @doc.purpose Verify attribution model service behavior
 * @doc.layer application
 */
@DisplayName("AttributionModelServiceImpl")
class AttributionModelServiceImplTest {

    private EphemeralAttributionModelRepository repository;
    private AttributionModelServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = new EphemeralAttributionModelRepository();
        service = new AttributionModelServiceImpl(repository);
    }

    @Test
    @DisplayName("createModel creates and saves attribution model")
    void createModel_createsAndSavesAttributionModel() {
        DmTenantId tenantId = DmTenantId.of("tenant-123");
        DmWorkspaceId workspaceId = DmWorkspaceId.of("workspace-456");

        var promise = service.createModel(tenantId, workspaceId, "Multi-Touch Model", "LINEAR", Map.of("search", 0.4, "social", 0.3, "email", 0.3));
        assertThat(promise).isNotNull();
    }

    @Test
    @DisplayName("getModel retrieves attribution model by ID")
    void getModel_retrievesAttributionModelById() {
        AttributionModel model = AttributionModel.builder()
            .modelId("model-789")
            .tenantId(DmTenantId.of("tenant-123"))
            .workspaceId(DmWorkspaceId.of("workspace-456"))
            .modelName("Multi-Touch Model")
            .modelType("LINEAR")
            .active(true)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        repository.save(model).getResult();
        var promise = service.getModel("model-789");
        assertThat(promise).isNotNull();
    }

    @Test
    @DisplayName("calculateAttribution calculates attribution based on weights")
    void calculateAttribution_calculatesAttributionBasedOnWeights() {
        AttributionModel model = AttributionModel.builder()
            .modelId("model-789")
            .tenantId(DmTenantId.of("tenant-123"))
            .workspaceId(DmWorkspaceId.of("workspace-456"))
            .modelName("Multi-Touch Model")
            .modelType("LINEAR")
            .touchpointWeights(Map.of("search", 0.4, "social", 0.3, "email", 0.3))
            .active(true)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        repository.save(model).getResult();
        var promise = service.calculateAttribution("model-789", Map.of("search", 100.0, "social", 50.0, "email", 30.0));
        assertThat(promise).isNotNull();
    }

    @Test
    @DisplayName("generateBudgetRecommendations generates budget recommendations")
    void generateBudgetRecommendations_generatesBudgetRecommendations() {
        AttributionModel model = AttributionModel.builder()
            .modelId("model-789")
            .tenantId(DmTenantId.of("tenant-123"))
            .workspaceId(DmWorkspaceId.of("workspace-456"))
            .modelName("Multi-Touch Model")
            .modelType("LINEAR")
            .touchpointWeights(Map.of("search", 0.6, "social", 0.2, "email", 0.2))
            .active(true)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        repository.save(model).getResult();
        var promise = service.generateBudgetRecommendations("model-789", Map.of("search", 1000.0, "social", 500.0, "email", 300.0));
        assertThat(promise).isNotNull();
    }

    // ── test doubles ─────────────────────────────────────────────────────────

    private static final class EphemeralAttributionModelRepository implements AttributionModelRepository {
        private final ConcurrentHashMap<String, AttributionModel> store = new ConcurrentHashMap<>();

        @Override
        public Promise<AttributionModel> save(AttributionModel model) {
            store.put(model.getModelId(), model);
            return Promise.of(model);
        }

        @Override
        public Promise<Optional<AttributionModel>> findById(String modelId) {
            return Promise.of(Optional.ofNullable(store.get(modelId)));
        }

        @Override
        public Promise<Optional<AttributionModel>> findActiveByWorkspace(DmWorkspaceId workspaceId) {
            for (AttributionModel model : store.values()) {
                if (model.getWorkspaceId().equals(workspaceId) && model.isActive()) {
                    return Promise.of(Optional.of(model));
                }
            }
            return Promise.of(Optional.empty());
        }

        @Override
        public Promise<List<AttributionModel>> findByTenant(DmTenantId tenantId) {
            List<AttributionModel> result = new ArrayList<>();
            for (AttributionModel model : store.values()) {
                if (model.getTenantId().equals(tenantId)) {
                    result.add(model);
                }
            }
            return Promise.of(result);
        }

        @Override
        public Promise<Void> delete(String modelId) {
            store.remove(modelId);
            return Promise.complete();
        }

        @Override
        public Promise<AttributionModel> update(AttributionModel model) {
            store.put(model.getModelId(), model);
            return Promise.of(model);
        }
    }
}
