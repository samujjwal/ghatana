package com.ghatana.yappc.kernel;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * G10-007: PHR Gap Visualization Tests
 *
 * Tests for PHR gap visualization functionality.
 *
 * @doc.type class
 * @doc.purpose Test PHR gap visualization functionality
 * @doc.layer integration
 * @doc.pattern Unit Test
 */
@DisplayName("PHR Gap Visualization Tests")
class PhrGapVisualizationTest extends EventloopTestBase {

    private PhrGapVisualization visualization;

    @BeforeEach
    void setUp() {
        visualization = new PhrGapVisualization();
        visualization.setProductId("phr");
        visualization.setVersion("1.0.0");
    }

    @AfterEach
    void tearDown() {
        visualization = null;
    }

    @Test
    @DisplayName("GIVEN gap items WHEN generating report THEN produces formatted output")
    void gapItems_whenGeneratingReport_producesFormattedOutput() {
        PhrGapVisualization.GapVisualizationItem item1 = new PhrGapVisualization.GapVisualizationItem();
        item1.setRouteId("dashboard");
        item1.setRoutePath("/dashboard");
        item1.setStatus(PhrGapVisualization.GapStatus.STABLE_IMPLEMENTED.getDisplayName());
        item1.setDescription("Dashboard route implemented");

        PhrGapVisualization.GapVisualizationItem item2 = new PhrGapVisualization.GapVisualizationItem();
        item2.setRouteId("records");
        item2.setRoutePath("/records");
        item2.setStatus(PhrGapVisualization.GapStatus.STABLE_MISSING_API.getDisplayName());
        item2.setDescription("Records API missing");

        visualization.addItem(item1);
        visualization.addItem(item2);

        String report = visualization.generateVisualizationReport();

        assertThat(report).contains("PHR Gap Visualization Report");
        assertThat(report).contains("Product: phr");
        assertThat(report).contains("Total Items: 2");
        assertThat(report).contains("Stable Implemented");
        assertThat(report).contains("Stable Missing API");
    }

    @Test
    @DisplayName("GIVEN gap items WHEN generating markdown table THEN produces table format")
    void gapItems_whenGeneratingMarkdownTable_producesTableFormat() {
        PhrGapVisualization.GapVisualizationItem item = new PhrGapVisualization.GapVisualizationItem();
        item.setRouteId("dashboard");
        item.setRoutePath("/dashboard");
        item.setStatus(PhrGapVisualization.GapStatus.STABLE_IMPLEMENTED.getDisplayName());
        item.setDescription("Dashboard route implemented");

        visualization.addItem(item);

        String table = visualization.generateMarkdownTable();

        assertThat(table).contains("| Route ID | Path | Status | Description |");
        assertThat(table).contains("| dashboard | /dashboard |");
    }

    @Test
    @DisplayName("GIVEN gap items WHEN getting summary THEN counts by status")
    void gapItems_whenGettingSummary_countsByStatus() {
        PhrGapVisualization.GapVisualizationItem item1 = new PhrGapVisualization.GapVisualizationItem();
        item1.setStatus(PhrGapVisualization.GapStatus.STABLE_IMPLEMENTED.getDisplayName());

        PhrGapVisualization.GapVisualizationItem item2 = new PhrGapVisualization.GapVisualizationItem();
        item2.setStatus(PhrGapVisualization.GapStatus.STABLE_IMPLEMENTED.getDisplayName());

        PhrGapVisualization.GapVisualizationItem item3 = new PhrGapVisualization.GapVisualizationItem();
        item3.setStatus(PhrGapVisualization.GapStatus.TEST_MISSING.getDisplayName());

        visualization.addItem(item1);
        visualization.addItem(item2);
        visualization.addItem(item3);

        var summary = visualization.getSummary();

        assertThat(summary.get(PhrGapVisualization.GapStatus.STABLE_IMPLEMENTED.getDisplayName())).isEqualTo(2);
        assertThat(summary.get(PhrGapVisualization.GapStatus.TEST_MISSING.getDisplayName())).isEqualTo(1);
    }
}
