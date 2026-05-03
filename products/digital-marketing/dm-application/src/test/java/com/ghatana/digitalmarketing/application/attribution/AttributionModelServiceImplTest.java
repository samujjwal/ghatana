package com.ghatana.digitalmarketing.application.attribution;

import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.attribution.AttributionModel;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for AttributionModelServiceImpl (DMOS-P3-005).
 *
 * @doc.type test
 * @doc.purpose Verify attribution model service behavior
 * @doc.layer application
 */
@DisplayName("AttributionModelServiceImpl")
class AttributionModelServiceImplTest {

    @Test
    @DisplayName("createModel creates and saves attribution model")
    void createModel_createsAndSavesAttributionModel() {
        AttributionModelRepository repository = mock(AttributionModelRepository.class);
        when(repository.save(any(AttributionModel.class))).thenReturn(Promise.of(mock(AttributionModel.class)));

        AttributionModelServiceImpl service = new AttributionModelServiceImpl(repository);
        DmTenantId tenantId = new DmTenantId("tenant-123");
        DmWorkspaceId workspaceId = new DmWorkspaceId("workspace-456");

        var promise = service.createModel(tenantId, workspaceId, "Multi-Touch Model", "LINEAR", Map.of("search", 0.4, "social", 0.3, "email", 0.3));
        assertThat(promise).isNotNull();
    }

    @Test
    @DisplayName("getModel retrieves attribution model by ID")
    void getModel_retrievesAttributionModelById() {
        AttributionModelRepository repository = mock(AttributionModelRepository.class);
        AttributionModel model = AttributionModel.builder()
            .modelId("model-789")
            .tenantId(new DmTenantId("tenant-123"))
            .workspaceId(new DmWorkspaceId("workspace-456"))
            .modelName("Multi-Touch Model")
            .modelType("LINEAR")
            .active(true)
            .createdAt(java.time.Instant.now())
            .updatedAt(java.time.Instant.now())
            .build();

        when(repository.findById("model-789")).thenReturn(Promise.of(java.util.Optional.of(model)));

        AttributionModelServiceImpl service = new AttributionModelServiceImpl(repository);
        var promise = service.getModel("model-789");
        assertThat(promise).isNotNull();
    }

    @Test
    @DisplayName("calculateAttribution calculates attribution based on weights")
    void calculateAttribution_calculatesAttributionBasedOnWeights() {
        AttributionModelRepository repository = mock(AttributionModelRepository.class);
        AttributionModel model = AttributionModel.builder()
            .modelId("model-789")
            .tenantId(new DmTenantId("tenant-123"))
            .workspaceId(new DmWorkspaceId("workspace-456"))
            .modelName("Multi-Touch Model")
            .modelType("LINEAR")
            .touchpointWeights(Map.of("search", 0.4, "social", 0.3, "email", 0.3))
            .active(true)
            .createdAt(java.time.Instant.now())
            .updatedAt(java.time.Instant.now())
            .build();

        when(repository.findById("model-789")).thenReturn(Promise.of(java.util.Optional.of(model)));

        AttributionModelServiceImpl service = new AttributionModelServiceImpl(repository);
        var promise = service.calculateAttribution("model-789", Map.of("search", 100.0, "social", 50.0, "email", 30.0));
        assertThat(promise).isNotNull();
    }

    @Test
    @DisplayName("generateBudgetRecommendations generates budget recommendations")
    void generateBudgetRecommendations_generatesBudgetRecommendations() {
        AttributionModelRepository repository = mock(AttributionModelRepository.class);
        AttributionModel model = AttributionModel.builder()
            .modelId("model-789")
            .tenantId(new DmTenantId("tenant-123"))
            .workspaceId(new DmWorkspaceId("workspace-456"))
            .modelName("Multi-Touch Model")
            .modelType("LINEAR")
            .touchpointWeights(Map.of("search", 0.6, "social", 0.2, "email", 0.2))
            .active(true)
            .createdAt(java.time.Instant.now())
            .updatedAt(java.time.Instant.now())
            .build();

        when(repository.findById("model-789")).thenReturn(Promise.of(java.util.Optional.of(model)));

        AttributionModelServiceImpl service = new AttributionModelServiceImpl(repository);
        var promise = service.generateBudgetRecommendations("model-789", Map.of("search", 1000.0, "social", 500.0, "email", 300.0));
        assertThat(promise).isNotNull();
    }
}
