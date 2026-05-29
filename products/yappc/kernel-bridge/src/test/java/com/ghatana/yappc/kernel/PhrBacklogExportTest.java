package com.ghatana.yappc.kernel;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * G10-009: PHR Backlog Export Tests
 *
 * Tests for PHR backlog export grouped by verification pass.
 *
 * @doc.type class
 * @doc.purpose Test PHR backlog export functionality
 * @doc.layer integration
 * @doc.pattern Unit Test
 */
@DisplayName("PHR Backlog Export Tests")
class PhrBacklogExportTest extends EventloopTestBase {

    private PhrBacklogExport backlogExport;

    @BeforeEach
    void setUp() {
        backlogExport = new PhrBacklogExport();
        backlogExport.setProductId("phr");
        backlogExport.setVersion("1.0.0");
    }

    @AfterEach
    void tearDown() {
        backlogExport = null;
    }

    @Test
    @DisplayName("GIVEN backlog items WHEN generating markdown THEN produces formatted output")
    void backlogItems_whenGeneratingMarkdown_producesFormattedOutput() {
        PhrBacklogExport.BacklogItem item1 = new PhrBacklogExport.BacklogItem();
        item1.setId("G1-001");
        item1.setTitle("Add route factory");
        item1.setStatus("pending");
        item1.setPriority("high");

        backlogExport.addItem(PhrBacklogExport.VerificationPass.V1.getDisplayName(), item1);

        String markdown = backlogExport.generateMarkdownExport();

        assertThat(markdown).contains("# PHR Backlog Export");
        assertThat(markdown).contains("Product: phr");
        assertThat(markdown).contains("Verification Pass V1");
        assertThat(markdown).contains("| G1-001 | Add route factory | pending | high |");
    }

    @Test
    @DisplayName("GIVEN backlog items WHEN generating JSON THEN produces valid JSON")
    void backlogItems_whenGeneratingJson_producesValidJson() {
        PhrBacklogExport.BacklogItem item1 = new PhrBacklogExport.BacklogItem();
        item1.setId("G1-001");
        item1.setTitle("Add route factory");
        item1.setStatus("pending");
        item1.setPriority("high");

        backlogExport.addItem(PhrBacklogExport.VerificationPass.V1.getDisplayName(), item1);

        String json = backlogExport.generateJsonExport();

        assertThat(json).contains("\"productId\": \"phr\"");
        assertThat(json).contains("\"id\": \"G1-001\"");
        assertThat(json).contains("\"title\": \"Add route factory\"");
    }

    @Test
    @DisplayName("GIVEN multiple verification passes WHEN getting summary THEN counts correctly")
    void multipleVerificationPasses_whenGettingSummary_countsCorrectly() {
        PhrBacklogExport.BacklogItem item1 = new PhrBacklogExport.BacklogItem();
        item1.setId("G1-001");
        item1.setTitle("Add route factory");
        item1.setStatus("pending");
        item1.setPriority("high");

        PhrBacklogExport.BacklogItem item2 = new PhrBacklogExport.BacklogItem();
        item2.setId("G2-001");
        item2.setTitle("Add policy gate");
        item2.setStatus("pending");
        item2.setPriority("high");

        backlogExport.addItem(PhrBacklogExport.VerificationPass.V1.getDisplayName(), item1);
        backlogExport.addItem(PhrBacklogExport.VerificationPass.V2.getDisplayName(), item2);

        var summary = backlogExport.getSummary();

        assertThat(summary.get("totalItems")).isEqualTo(2);
        assertThat(summary.get("totalPasses")).isEqualTo(2);
        assertThat(summary.get("Verification Pass V1_count")).isEqualTo(1);
        assertThat(summary.get("Verification Pass V2_count")).isEqualTo(1);
    }
}
