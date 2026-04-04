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
    void setUp() {
        generator = new AutoDocumentationGenerator();
        sampleSchema = SchemaDefinition.builder()
                .columns(List.of(
                        SchemaColumn.builder().name("id").dataType("STRING").build(),
                        SchemaColumn.builder().name("amount").dataType("DECIMAL").build()
                ))
                .build();
    }

    // =========================================================================
    // CONSTRUCTION
    // =========================================================================

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("should create generator without errors")
        void shouldCreateGenerator() {
            assertThatCode(() -> new AutoDocumentationGenerator()).doesNotThrowAnyException();
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
        void shouldGenerateDocumentation() {
            DatasetDocumentation doc = runPromise(() ->
                    generator.generateDocumentation("dataset-1", "Orders", sampleSchema, Map.of()));

            assertThat(doc).isNotNull();
            assertThat(doc.getDatasetId()).isEqualTo("dataset-1");
        }

        @Test
        @DisplayName("should include columns in generated documentation")
        void shouldIncludeColumns() {
            DatasetDocumentation doc = runPromise(() ->
                    generator.generateDocumentation("dataset-2", "Orders", sampleSchema, Map.of()));

            assertThat(doc.getColumns()).isNotEmpty();
        }

        @Test
        @DisplayName("should set version to 1 on initial generation")
        void shouldSetInitialVersion() {
            DatasetDocumentation doc = runPromise(() ->
                    generator.generateDocumentation("dataset-3", "Orders", sampleSchema, Map.of()));

            assertThat(doc.getVersion()).isEqualTo(1);
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
        void shouldRetrievePreviouslyGeneratedDoc() {
            runPromise(() -> generator.generateDocumentation("dataset-g", "Products", sampleSchema, Map.of()));

            DatasetDocumentation doc = runPromise(() -> generator.getDocumentation("dataset-g"));
            assertThat(doc).isNotNull();
            assertThat(doc.getDatasetId()).isEqualTo("dataset-g");
        }

        @Test
        @DisplayName("should return null when documentation does not exist")
        void shouldReturnNullForUnknownDataset() {
            DatasetDocumentation doc = runPromise(() ->
                    generator.getDocumentation("unknown-dataset"));
            assertThat(doc).isNull();
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
        void shouldReturnVersionHistory() {
            runPromise(() -> generator.generateDocumentation("dataset-h", "Orders", sampleSchema, Map.of()));

            List<DocumentationVersion> history = runPromise(() ->
                    generator.getVersionHistory("dataset-h"));
            assertThat(history).isNotEmpty();
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
        void shouldReturnMatchingDatasets() {
            runPromise(() -> generator.generateDocumentation("orders-2026", "Orders 2026", sampleSchema, Map.of()));

            List<SearchResult> results = runPromise(() ->
                    generator.search("orders", 10));
            assertThat(results).isNotEmpty();
        }

        @Test
        @DisplayName("should return empty list when no match found")
        void shouldReturnEmptyForNoMatch() {
            List<SearchResult> results = runPromise(() ->
                    generator.search("zzz-nonexistent-xyz", 10));
            assertThat(results).isEmpty();
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
        void shouldExportAsMarkdown() {
            runPromise(() -> generator.generateDocumentation("dataset-md", "Orders", sampleSchema, Map.of()));

            String markdown = runPromise(() -> generator.exportAsMarkdown("dataset-md"));
            assertThat(markdown).isNotBlank();
        }
    }
}
