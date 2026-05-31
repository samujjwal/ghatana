package com.ghatana.yappc.kernel;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * G10-006: Product Generation Mode Tests
 *
 * Tests for Product generation mode configuration and task emission.
 *
 * @doc.type class
 * @doc.purpose Test Product generation mode functionality
 * @doc.layer integration
 * @doc.pattern Unit Test
 */
@DisplayName("Product Generation Mode Tests")
class ProductGenerationModeTest extends EventloopTestBase {

    private ProductGenerationMode generationMode;

    @BeforeEach
    void setUp() {
        generationMode = new ProductGenerationMode();
    }

    @AfterEach
    void tearDown() {
        generationMode = null;
    }

    @Test
    @DisplayName("GIVEN standard mode WHEN adding deprecation task THEN keeps as deprecation")
    void standardMode_whenAddingDeprecationTask_keepsAsDeprecation() {
        generationMode.setMode(ProductGenerationMode.Mode.STANDARD);
        generationMode.addTask("deprecation", "/old/path", "Deprecated legacy path");

        assertThat(generationMode.getTasks()).hasSize(1);
        assertThat(generationMode.getTasks().get(0).getType()).isEqualTo("deprecation");
    }

    @Test
    @DisplayName("GIVEN cleanup mode WHEN adding deprecation task THEN converts to delete")
    void cleanupMode_whenAddingDeprecationTask_convertsToDelete() {
        generationMode.setMode(ProductGenerationMode.Mode.CLEANUP);
        generationMode.addTask("deprecation", "/old/path", "Deprecated legacy path");

        assertThat(generationMode.getTasks()).hasSize(1);
        assertThat(generationMode.getTasks().get(0).getType()).isEqualTo("delete");
        assertThat(generationMode.getTasks().get(0).getDescription()).contains("Delete duplicate legacy path");
    }

    @Test
    @DisplayName("GIVEN cleanup mode WHEN adding non-deprecation task THEN keeps original type")
    void cleanupMode_whenAddingNonDeprecationTask_keepsOriginalType() {
        generationMode.setMode(ProductGenerationMode.Mode.CLEANUP);
        generationMode.addTask("create", "/new/path", "Create new file");

        assertThat(generationMode.getTasks()).hasSize(1);
        assertThat(generationMode.getTasks().get(0).getType()).isEqualTo("create");
    }

    @Test
    @DisplayName("GIVEN tasks WHEN generating report THEN produces formatted output")
    void tasks_whenGeneratingReport_producesFormattedOutput() {
        generationMode.setMode(ProductGenerationMode.Mode.STANDARD);
        generationMode.addTask("create", "/new/path", "Create new file");
        generationMode.addTask("deprecation", "/old/path", "Deprecated legacy path");

        String report = generationMode.generateTaskReport();

        assertThat(report).contains("Product Generation Task Report");
        assertThat(report).contains("Mode: STANDARD");
        assertThat(report).contains("Total Tasks: 2");
        assertThat(report).contains("[create]");
        assertThat(report).contains("[deprecation]");
    }
}
