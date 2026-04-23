package com.ghatana.datacloud.plugins.enterprise.documentation;

import com.ghatana.datacloud.plugins.enterprise.documentation.AutoDocumentationGenerator.DatasetDocumentation;
import com.ghatana.datacloud.plugins.enterprise.documentation.AutoDocumentationGenerator.DocumentationVersion;
import com.ghatana.datacloud.plugins.enterprise.documentation.AutoDocumentationGenerator.SchemaColumn;
import com.ghatana.datacloud.plugins.enterprise.documentation.AutoDocumentationGenerator.SchemaDefinition;
import com.ghatana.datacloud.plugins.enterprise.documentation.AutoDocumentationGenerator.SearchResult;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link AutoDocumentationGenerator}.
 *
 * @doc.type test
 * @doc.purpose Validate dataset documentation generation, versioning, search, and export
 * @doc.layer product
 */
@DisplayName("AutoDocumentationGenerator Tests")
class AutoDocumentationGeneratorTest extends EventloopTestBase {

    private AutoDocumentationGenerator generator;

    private SchemaDefinition sampleSchema;

    @BeforeEach
    void setUp() { // GH-90000
        generator = new AutoDocumentationGenerator(); // GH-90000
        sampleSchema = SchemaDefinition.builder() // GH-90000
                .columns(List.of( // GH-90000
                        SchemaColumn.builder().name("id").dataType("STRING").build(),
                        SchemaColumn.builder().name("amount").dataType("DECIMAL").build()
                ))
                .build(); // GH-90000
    }

    // =========================================================================
    // CONSTRUCTION
    // =========================================================================

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("should create generator without errors")
        void shouldCreateGenerator() { // GH-90000
            assertThatCode(() -> new AutoDocumentationGenerator()).doesNotThrowAnyException(); // GH-90000
        }
    }

    // =========================================================================
    // GENERATE DOCUMENTATION
    // =========================================================================

    @Nested
    @DisplayName("generateDocumentation")
    class GenerateDocumentation {

        @Test
        @DisplayName("should generate documentation for a valid dataset schema")
        void shouldGenerateDocumentation() { // GH-90000
            DatasetDocumentation doc = runPromise(() -> // GH-90000
                    generator.generateDocumentation("dataset-1", "Orders", sampleSchema, Map.of())); // GH-90000

            assertThat(doc).isNotNull(); // GH-90000
            assertThat(doc.getDatasetId()).isEqualTo("dataset-1");
        }

        @Test
        @DisplayName("should include columns in generated documentation")
        void shouldIncludeColumns() { // GH-90000
            DatasetDocumentation doc = runPromise(() -> // GH-90000
                    generator.generateDocumentation("dataset-2", "Orders", sampleSchema, Map.of())); // GH-90000

            assertThat(doc.getColumns()).isNotEmpty(); // GH-90000
        }

        @Test
        @DisplayName("should set version to 1 on initial generation")
        void shouldSetInitialVersion() { // GH-90000
            DatasetDocumentation doc = runPromise(() -> // GH-90000
                    generator.generateDocumentation("dataset-3", "Orders", sampleSchema, Map.of())); // GH-90000

            assertThat(doc.getVersion()).isEqualTo(1); // GH-90000
        }
    }

    // =========================================================================
    // GET DOCUMENTATION
    // =========================================================================

    @Nested
    @DisplayName("getDocumentation")
    class GetDocumentation {

        @Test
        @DisplayName("should retrieve previously generated documentation")
        void shouldRetrievePreviouslyGeneratedDoc() { // GH-90000
            runPromise(() -> generator.generateDocumentation("dataset-g", "Products", sampleSchema, Map.of())); // GH-90000

            DatasetDocumentation doc = runPromise(() -> generator.getDocumentation("dataset-g"));
            assertThat(doc).isNotNull(); // GH-90000
            assertThat(doc.getDatasetId()).isEqualTo("dataset-g");
        }

        @Test
        @DisplayName("should return null when documentation does not exist")
        void shouldReturnNullForUnknownDataset() { // GH-90000
            DatasetDocumentation doc = runPromise(() -> // GH-90000
                    generator.getDocumentation("unknown-dataset"));
            assertThat(doc).isNull(); // GH-90000
        }
    }

    // =========================================================================
    // VERSION HISTORY
    // =========================================================================

    @Nested
    @DisplayName("getVersionHistory")
    class GetVersionHistory {

        @Test
        @DisplayName("should return version history after documentation is created")
        void shouldReturnVersionHistory() { // GH-90000
            runPromise(() -> generator.generateDocumentation("dataset-h", "Orders", sampleSchema, Map.of())); // GH-90000

            List<DocumentationVersion> history = runPromise(() -> // GH-90000
                    generator.getVersionHistory("dataset-h"));
            assertThat(history).isNotEmpty(); // GH-90000
        }
    }

    // =========================================================================
    // SEARCH
    // =========================================================================

    @Nested
    @DisplayName("search")
    class Search {

        @Test
        @DisplayName("should return matching datasets for query")
        void shouldReturnMatchingDatasets() { // GH-90000
            runPromise(() -> generator.generateDocumentation("orders-2026", "Orders 2026", sampleSchema, Map.of())); // GH-90000

            List<SearchResult> results = runPromise(() -> // GH-90000
                    generator.search("orders", 10)); // GH-90000
            assertThat(results).isNotEmpty(); // GH-90000
        }

        @Test
        @DisplayName("should return empty list when no match found")
        void shouldReturnEmptyForNoMatch() { // GH-90000
            List<SearchResult> results = runPromise(() -> // GH-90000
                    generator.search("zzz-nonexistent-xyz", 10)); // GH-90000
            assertThat(results).isEmpty(); // GH-90000
        }
    }

    // =========================================================================
    // EXPORT AS MARKDOWN
    // =========================================================================

    @Nested
    @DisplayName("exportAsMarkdown")
    class ExportAsMarkdown {

        @Test
        @DisplayName("should export documentation as non-empty Markdown string")
        void shouldExportAsMarkdown() { // GH-90000
            runPromise(() -> generator.generateDocumentation("dataset-md", "Orders", sampleSchema, Map.of())); // GH-90000

            String markdown = runPromise(() -> generator.exportAsMarkdown("dataset-md"));
            assertThat(markdown).isNotBlank(); // GH-90000
        }
    }
}
