package com.ghatana.digitalmarketing.domain.attribution;

import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for AttributionModel (DMOS-P3-005).
 *
 * @doc.type test
 * @doc.purpose Verify attribution model domain entity behavior
 * @doc.layer domain
 */
@DisplayName("AttributionModel")
class AttributionModelTest {

    @Test
    @DisplayName("builder creates valid attribution model")
    void builder_createsValidAttributionModel() {
        DmTenantId tenantId = new DmTenantId("tenant-123");
        DmWorkspaceId workspaceId = new DmWorkspaceId("workspace-456");
        Instant now = Instant.now();
        Map<String, Double> weights = Map.of("search", 0.4, "social", 0.3, "email", 0.3);

        AttributionModel model = AttributionModel.builder()
            .modelId("model-789")
            .tenantId(tenantId)
            .workspaceId(workspaceId)
            .modelName("Multi-Touch Model")
            .modelType("LINEAR")
            .touchpointWeights(weights)
            .confidenceIntervalLower(0.0)
            .confidenceIntervalUpper(1.0)
            .active(true)
            .createdAt(now)
            .updatedAt(now)
            .build();

        assertThat(model.getModelId()).isEqualTo("model-789");
        assertThat(model.getModelName()).isEqualTo("Multi-Touch Model");
        assertThat(model.getModelType()).isEqualTo("LINEAR");
        assertThat(model.getTouchpointWeights()).isEqualTo(weights);
        assertThat(model.isActive()).isTrue();
    }

    @Test
    @DisplayName("builder creates inactive model")
    void builder_createsInactiveModel() {
        AttributionModel model = AttributionModel.builder()
            .modelId("model-789")
            .tenantId(new DmTenantId("tenant-123"))
            .workspaceId(new DmWorkspaceId("workspace-456"))
            .modelName("Multi-Touch Model")
            .modelType("LINEAR")
            .active(false)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        assertThat(model.isActive()).isFalse();
    }

    @Test
    @DisplayName("builder handles null optional fields")
    void builder_handlesNullOptionalFields() {
        AttributionModel model = AttributionModel.builder()
            .modelId("model-789")
            .tenantId(new DmTenantId("tenant-123"))
            .workspaceId(new DmWorkspaceId("workspace-456"))
            .modelName("Multi-Touch Model")
            .modelType("LINEAR")
            .active(true)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        assertThat(model.getTouchpointWeights()).isNull();
        assertThat(model.getConfidenceIntervalLower()).isNull();
        assertThat(model.getConfidenceIntervalUpper()).isNull();
    }
}
