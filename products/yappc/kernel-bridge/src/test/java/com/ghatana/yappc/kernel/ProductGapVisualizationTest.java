package com.ghatana.yappc.kernel;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * G10-007: Product Gap Visualization Tests
 *
 * Tests for Product gap visualization functionality.
 *
 * @doc.type class
 * @doc.purpose Test Product gap visualization functionality
 * @doc.layer integration
 * @doc.pattern Unit Test
 */
@DisplayName("Product Gap Visualization Tests")
class ProductGapVisualizationTest extends EventloopTestBase {

    private ProductGapVisualization visualization;

    @BeforeEach
    void setUp() {
        visualization = new ProductGapVisualization();
        visualization.setProductId("sample-product");
        visualization.setVersion("1.0.0");
    }

    @AfterEach
    void tearDown() {
        visualization = null;
    }

    @Test
    @DisplayName("GIVEN gap items WHEN generating report THEN produces formatted output")
    void gapItems_whenGeneratingReport_producesFormattedOutput() {
        ProductGapVisualization.GapVisualizationItem item1 = new ProductGapVisualization.GapVisualizationItem();
        item1.setRouteId("dashboard");
        item1.setRoutePath("/dashboard");
        item1.setStatus(ProductGapVisualization.GapStatus.STABLE_IMPLEMENTED.getDisplayName());
        item1.setDescription("Dashboard route implemented");

        ProductGapVisualization.GapVisualizationItem item2 = new ProductGapVisualization.GapVisualizationItem();
        item2.setRouteId("records");
        item2.setRoutePath("/records");
        item2.setStatus(ProductGapVisualization.GapStatus.STABLE_MISSING_API.getDisplayName());
        item2.setDescription("Records API missing");

        visualization.addItem(item1);
        visualization.addItem(item2);

        String report = visualization.generateVisualizationReport();

        assertThat(report).contains("Product Gap Visualization Report");
        assertThat(report).contains("Product: sample-product");
        assertThat(report).contains("Total Items: 2");
        assertThat(report).contains("Stable Implemented");
        assertThat(report).contains("Stable Missing API");
    }

    @Test
    @DisplayName("GIVEN gap items WHEN generating markdown table THEN produces table format")
    void gapItems_whenGeneratingMarkdownTable_producesTableFormat() {
        ProductGapVisualization.GapVisualizationItem item = new ProductGapVisualization.GapVisualizationItem();
        item.setRouteId("dashboard");
        item.setRoutePath("/dashboard");
        item.setStatus(ProductGapVisualization.GapStatus.STABLE_IMPLEMENTED.getDisplayName());
        item.setDescription("Dashboard route implemented");

        visualization.addItem(item);

        String table = visualization.generateMarkdownTable();

        assertThat(table).contains("| Route ID | Path | Status | Description |");
        assertThat(table).contains("| dashboard | /dashboard |");
    }

    @Test
    @DisplayName("GIVEN gap items WHEN getting summary THEN counts by status")
    void gapItems_whenGettingSummary_countsByStatus() {
        ProductGapVisualization.GapVisualizationItem item1 = new ProductGapVisualization.GapVisualizationItem();
        item1.setStatus(ProductGapVisualization.GapStatus.STABLE_IMPLEMENTED.getDisplayName());

        ProductGapVisualization.GapVisualizationItem item2 = new ProductGapVisualization.GapVisualizationItem();
        item2.setStatus(ProductGapVisualization.GapStatus.STABLE_IMPLEMENTED.getDisplayName());

        ProductGapVisualization.GapVisualizationItem item3 = new ProductGapVisualization.GapVisualizationItem();
        item3.setStatus(ProductGapVisualization.GapStatus.TEST_MISSING.getDisplayName());

        visualization.addItem(item1);
        visualization.addItem(item2);
        visualization.addItem(item3);

        var summary = visualization.getSummary();

        assertThat(summary.get(ProductGapVisualization.GapStatus.STABLE_IMPLEMENTED.getDisplayName())).isEqualTo(2);
        assertThat(summary.get(ProductGapVisualization.GapStatus.TEST_MISSING.getDisplayName())).isEqualTo(1);
    }
}
