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
@DisplayName("AutoDocumentationGenerator Tests [GH-90000]")
class AutoDocumentationGeneratorTest extends EventloopTestBase {

    private AutoDocumentationGenerator generator;

    private SchemaDefinition sampleSchema;

    @BeforeEach
    void setUp() { // GH-90000
        generator = new AutoDocumentationGenerator(); // GH-90000
        sampleSchema = SchemaDefinition.builder() // GH-90000
                .columns(List.of( // GH-90000
                        SchemaColumn.builder().name("id [GH-90000]").dataType("STRING [GH-90000]").build(),
                        SchemaColumn.builder().name("amount [GH-90000]").dataType("DECIMAL [GH-90000]").build()
                ))
                .build(); // GH-90000
    }

    // =========================================================================
    // CONSTRUCTION
    // =========================================================================

    @Nested
    @DisplayName("Construction [GH-90000]")
    class Construction {

        @Test
        @DisplayName("should create generator without errors [GH-90000]")
        void shouldCreateGenerator() { // GH-90000
            assertThatCode(() -> new AutoDocumentationGenerator()).doesNotThrowAnyException(); // GH-90000
        }
    }

    // =========================================================================
    // GENERATE DOCUMENTATION
    // =========================================================================

    @Nested
    @DisplayName("generateDocumentation [GH-90000]")
    class GenerateDocumentation {

        @Test
        @DisplayName("should generate documentation for a valid dataset schema [GH-90000]")
        void shouldGenerateDocumentation() { // GH-90000
            DatasetDocumentation doc = runPromise(() -> // GH-90000
                    generator.generateDocumentation("dataset-1", "Orders", sampleSchema, Map.of())); // GH-90000

            assertThat(doc).isNotNull(); // GH-90000
            assertThat(doc.getDatasetId()).isEqualTo("dataset-1 [GH-90000]");
        }

        @Test
        @DisplayName("should include columns in generated documentation [GH-90000]")
        void shouldIncludeColumns() { // GH-90000
            DatasetDocumentation doc = runPromise(() -> // GH-90000
                    generator.generateDocumentation("dataset-2", "Orders", sampleSchema, Map.of())); // GH-90000

            assertThat(doc.getColumns()).isNotEmpty(); // GH-90000
        }

        @Test
        @DisplayName("should set version to 1 on initial generation [GH-90000]")
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
    @DisplayName("getDocumentation [GH-90000]")
    class GetDocumentation {

        @Test
        @DisplayName("should retrieve previously generated documentation [GH-90000]")
        void shouldRetrievePreviouslyGeneratedDoc() { // GH-90000
            runPromise(() -> generator.generateDocumentation("dataset-g", "Products", sampleSchema, Map.of())); // GH-90000

            DatasetDocumentation doc = runPromise(() -> generator.getDocumentation("dataset-g [GH-90000]"));
            assertThat(doc).isNotNull(); // GH-90000
            assertThat(doc.getDatasetId()).isEqualTo("dataset-g [GH-90000]");
        }

        @Test
        @DisplayName("should return null when documentation does not exist [GH-90000]")
        void shouldReturnNullForUnknownDataset() { // GH-90000
            DatasetDocumentation doc = runPromise(() -> // GH-90000
                    generator.getDocumentation("unknown-dataset [GH-90000]"));
            assertThat(doc).isNull(); // GH-90000
        }
    }

    // =========================================================================
    // VERSION HISTORY
    // =========================================================================

    @Nested
    @DisplayName("getVersionHistory [GH-90000]")
    class GetVersionHistory {

        @Test
        @DisplayName("should return version history after documentation is created [GH-90000]")
        void shouldReturnVersionHistory() { // GH-90000
            runPromise(() -> generator.generateDocumentation("dataset-h", "Orders", sampleSchema, Map.of())); // GH-90000

            List<DocumentationVersion> history = runPromise(() -> // GH-90000
                    generator.getVersionHistory("dataset-h [GH-90000]"));
            assertThat(history).isNotEmpty(); // GH-90000
        }
    }

    // =========================================================================
    // SEARCH
    // =========================================================================

    @Nested
    @DisplayName("search [GH-90000]")
    class Search {

        @Test
        @DisplayName("should return matching datasets for query [GH-90000]")
        void shouldReturnMatchingDatasets() { // GH-90000
            runPromise(() -> generator.generateDocumentation("orders-2026", "Orders 2026", sampleSchema, Map.of())); // GH-90000

            List<SearchResult> results = runPromise(() -> // GH-90000
                    generator.search("orders", 10)); // GH-90000
            assertThat(results).isNotEmpty(); // GH-90000
        }

        @Test
        @DisplayName("should return empty list when no match found [GH-90000]")
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
    @DisplayName("exportAsMarkdown [GH-90000]")
    class ExportAsMarkdown {

        @Test
        @DisplayName("should export documentation as non-empty Markdown string [GH-90000]")
        void shouldExportAsMarkdown() { // GH-90000
            runPromise(() -> generator.generateDocumentation("dataset-md", "Orders", sampleSchema, Map.of())); // GH-90000

            String markdown = runPromise(() -> generator.exportAsMarkdown("dataset-md [GH-90000]"));
            assertThat(markdown).isNotBlank(); // GH-90000
        }
    }
}
