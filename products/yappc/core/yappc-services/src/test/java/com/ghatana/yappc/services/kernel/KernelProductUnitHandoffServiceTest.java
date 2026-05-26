package com.ghatana.yappc.services.kernel;

import com.ghatana.yappc.kernel.ProductUnitIntentExporter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @doc.type class
 * @doc.purpose Verifies backend Kernel ProductUnitIntent handoff service behavior independent of CLI/HTTP
 * @doc.layer product
 * @doc.pattern Unit Test
 */
@DisplayName("KernelProductUnitHandoffService")
class KernelProductUnitHandoffServiceTest {

    @Test
    @DisplayName("generates validated Kernel ProductUnitIntent from backend handoff request")
    void generatesValidatedKernelProductUnitIntent() throws Exception {
        KernelProductUnitHandoffService service = new KernelProductUnitHandoffService();

        KernelProductUnitHandoffService.HandoffResult result = service.generate(new KernelProductUnitHandoffService.HandoffRequest(
                "tenant-1",
                "workspace-1",
                "project-1",
                "Project One",
                List.of("web", "backend-api"),
                "ghatana-file-registry",
                "standard-web-api-product",
                "generate",
                Map.of("shapeId", "shape-1"),
                "corr-1"));

        assertThat(result.valid()).isTrue();
        assertThat(result.correlationId()).isEqualTo("corr-1");
        assertThat(result.validationErrors()).isEmpty();
        assertThat(result.productUnitIntent())
                .containsEntry("schemaVersion", "1.0.0")
                .containsEntry("intentType", "create");

        Map<?, ?> scope = (Map<?, ?>) result.productUnitIntent().get("scope");
        assertThat(scope.get("tenantId")).isEqualTo("tenant-1");
        assertThat(scope.get("workspaceId")).isEqualTo("workspace-1");

        Map<?, ?> productUnit = (Map<?, ?>) result.productUnitIntent().get("productUnit");
        assertThat(productUnit.get("id")).isEqualTo("project-1");
        assertThat(productUnit.get("name")).isEqualTo("Project One");
        assertThat(productUnit.get("lifecycleProfile")).isEqualTo("standard-web-api-product");
        assertThat(productUnit.get("surfaces").toString()).contains("web", "backend-api");

        Map<?, ?> metadata = (Map<?, ?>) productUnit.get("metadata");
        assertThat(metadata.get("shapeId")).isEqualTo("shape-1");
        assertThat(metadata.get("correlationId")).isEqualTo("corr-1");
    }

    @Test
    @DisplayName("fails closed for invalid Kernel contract values")
    void rejectsInvalidKernelContractValues() {
        KernelProductUnitHandoffService service = new KernelProductUnitHandoffService();

        assertThatThrownBy(() -> service.generate(new KernelProductUnitHandoffService.HandoffRequest(
                "tenant-1",
                "workspace-1",
                "project-1",
                "Project One",
                List.of("not-a-surface"),
                "ghatana-file-registry",
                "standard-web-api-product",
                "generate",
                Map.of(),
                "corr-1")))
                .isInstanceOf(ProductUnitIntentExporter.ExportException.class)
                .hasMessageContaining("Unknown ProductUnit surface");
    }

    @Test
    @DisplayName("requires backend tenant workspace project and product identity")
    void requiresBackendScope() {
        KernelProductUnitHandoffService service = new KernelProductUnitHandoffService();

        assertThatThrownBy(() -> service.generate(new KernelProductUnitHandoffService.HandoffRequest(
                "",
                "workspace-1",
                "project-1",
                "Project One",
                List.of("web"),
                null,
                null,
                null,
                Map.of(),
                null)))
                .isInstanceOf(ProductUnitIntentExporter.ExportException.class)
                .hasMessage("tenantId is required");
    }
}
