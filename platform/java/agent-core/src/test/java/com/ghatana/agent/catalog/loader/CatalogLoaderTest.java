/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved. // GH-90000
 */

package com.ghatana.agent.catalog.loader;

import com.ghatana.agent.catalog.AgentCatalog;
import com.ghatana.agent.catalog.CatalogAgentEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link CatalogLoader} — tests actual YAML parsing
 * using fixture files in src/test/resources/catalog-test/.
 */
@DisplayName("CatalogLoader [GH-90000]")
class CatalogLoaderTest {

    private CatalogLoader loader;
    private Path catalogTestRoot;

    @BeforeEach
    void setUp() throws Exception { // GH-90000
        loader = new CatalogLoader(); // GH-90000

        // Load fixture root from classpath
        URL resource = getClass().getClassLoader().getResource("catalog-test [GH-90000]");
        assertThat(resource).as("catalog-test resource must exist [GH-90000]").isNotNull();
        catalogTestRoot = Path.of(resource.toURI()); // GH-90000
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // discoverCatalogFiles
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("discoverCatalogFiles() [GH-90000]")
    class DiscoverCatalogFilesTests {

        @Test
        @DisplayName("should find all agent-catalog.yaml files in tree [GH-90000]")
        void shouldDiscoverAllCatalogFiles() throws IOException { // GH-90000
            List<Path> files = loader.discoverCatalogFiles(catalogTestRoot); // GH-90000
            // catalog-a and catalog-b each have one agent-catalog.yaml
            assertThat(files).hasSize(2); // GH-90000
            assertThat(files).allMatch(p -> p.getFileName().toString().equals("agent-catalog.yaml [GH-90000]"));
        }

        @Test
        @DisplayName("should skip .git directories during discovery [GH-90000]")
        void shouldSkipGitDirectory(@TempDir Path tempDir) throws IOException { // GH-90000
            Path gitDir = tempDir.resolve(".git [GH-90000]");
            Files.createDirectories(gitDir); // GH-90000
            Files.writeString(gitDir.resolve("agent-catalog.yaml [GH-90000]"),
                    "catalog:\n  id: 'hidden'\n  name: 'Hidden'\n  agents: []\n");

            // Only the .git-internal file should be skipped
            List<Path> files = loader.discoverCatalogFiles(tempDir); // GH-90000
            assertThat(files).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("should skip node_modules directories during discovery [GH-90000]")
        void shouldSkipNodeModules(@TempDir Path tempDir) throws IOException { // GH-90000
            Path nodeModules = tempDir.resolve("node_modules [GH-90000]");
            Files.createDirectories(nodeModules); // GH-90000
            Files.writeString(nodeModules.resolve("agent-catalog.yaml [GH-90000]"),
                    "catalog:\n  id: 'npm-hidden'\n  name: 'NPM'\n  agents: []\n");

            List<Path> files = loader.discoverCatalogFiles(tempDir); // GH-90000
            assertThat(files).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("should return empty list when no catalog files exist [GH-90000]")
        void shouldReturnEmptyWhenNoCatalogs(@TempDir Path tempDir) throws IOException { // GH-90000
            List<Path> files = loader.discoverCatalogFiles(tempDir); // GH-90000
            assertThat(files).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("null root should throw NullPointerException [GH-90000]")
        void shouldThrowOnNullRoot() { // GH-90000
            assertThatThrownBy(() -> loader.discoverCatalogFiles(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // loadFromFile
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("loadFromFile() [GH-90000]")
    class LoadFromFileTests {

        @Test
        @DisplayName("should load catalog-a with correct id and display name [GH-90000]")
        void shouldLoadCatalogAId() throws IOException { // GH-90000
            Path catalogA = catalogTestRoot.resolve("catalog-a/agent-catalog.yaml [GH-90000]");
            AgentCatalog catalog = loader.loadFromFile(catalogA); // GH-90000

            assertThat(catalog.getCatalogId()).isEqualTo("test-platform [GH-90000]");
            assertThat(catalog.getDisplayName()).isEqualTo("Test Platform Catalog [GH-90000]");
        }

        @Test
        @DisplayName("should load agent definitions from catalog-a [GH-90000]")
        void shouldLoadAgentDefinitions() throws IOException { // GH-90000
            Path catalogA = catalogTestRoot.resolve("catalog-a/agent-catalog.yaml [GH-90000]");
            AgentCatalog catalog = loader.loadFromFile(catalogA); // GH-90000

            // catalog-a has 2 agent YAML files
            assertThat(catalog.getDefinitions()).hasSize(2); // GH-90000
        }

        @Test
        @DisplayName("should load agent with correct id [GH-90000]")
        void shouldLoadAgentWithCorrectId() throws IOException { // GH-90000
            Path catalogA = catalogTestRoot.resolve("catalog-a/agent-catalog.yaml [GH-90000]");
            AgentCatalog catalog = loader.loadFromFile(catalogA); // GH-90000

            Optional<CatalogAgentEntry> sentinel = catalog.findById("test-sentinel-agent [GH-90000]");
            assertThat(sentinel).isPresent(); // GH-90000
            assertThat(sentinel.get().getName()).isEqualTo("Test Sentinel Agent [GH-90000]");
        }

        @Test
        @DisplayName("should load agent capabilities [GH-90000]")
        void shouldLoadAgentCapabilities() throws IOException { // GH-90000
            Path catalogA = catalogTestRoot.resolve("catalog-a/agent-catalog.yaml [GH-90000]");
            AgentCatalog catalog = loader.loadFromFile(catalogA); // GH-90000

            Optional<CatalogAgentEntry> sentinel = catalog.findById("test-sentinel-agent [GH-90000]");
            assertThat(sentinel).isPresent(); // GH-90000
            assertThat(sentinel.get().getCapabilities()).containsExactlyInAnyOrder( // GH-90000
                    "monitoring", "alerting");
        }

        @Test
        @DisplayName("should load all available capabilities from catalog [GH-90000]")
        void shouldLoadAllCapabilities() throws IOException { // GH-90000
            Path catalogA = catalogTestRoot.resolve("catalog-a/agent-catalog.yaml [GH-90000]");
            AgentCatalog catalog = loader.loadFromFile(catalogA); // GH-90000

            assertThat(catalog.getAllCapabilities()) // GH-90000
                    .contains("monitoring", "alerting", "data-transformation", "json-schema-validation"); // GH-90000
        }

        @Test
        @DisplayName("should resolve priority from catalog metadata [GH-90000]")
        void shouldResolvePriority() throws IOException { // GH-90000
            Path catalogA = catalogTestRoot.resolve("catalog-a/agent-catalog.yaml [GH-90000]");
            AgentCatalog catalog = loader.loadFromFile(catalogA); // GH-90000

            assertThat(catalog.priority()).isEqualTo(100); // GH-90000
        }

        @Test
        @DisplayName("should load catalog-b with product-specific agent [GH-90000]")
        void shouldLoadCatalogB() throws IOException { // GH-90000
            Path catalogB = catalogTestRoot.resolve("catalog-b/agent-catalog.yaml [GH-90000]");
            AgentCatalog catalog = loader.loadFromFile(catalogB); // GH-90000

            assertThat(catalog.getCatalogId()).isEqualTo("test-product [GH-90000]");
            assertThat(catalog.getDefinitions()).hasSize(1); // GH-90000

            Optional<CatalogAgentEntry> productAgent = catalog.findById("test-product-agent [GH-90000]");
            assertThat(productAgent).isPresent(); // GH-90000
        }

        @Test
        @DisplayName("null path should throw NullPointerException [GH-90000]")
        void shouldThrowOnNullPath() { // GH-90000
            assertThatThrownBy(() -> loader.loadFromFile(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("invalid YAML path should throw IOException [GH-90000]")
        void shouldThrowOnInvalidPath(@TempDir Path tempDir) { // GH-90000
            Path nonExistent = tempDir.resolve("does-not-exist.yaml [GH-90000]");
            assertThatThrownBy(() -> loader.loadFromFile(nonExistent)) // GH-90000
                    .isInstanceOf(IOException.class); // GH-90000
        }

        @Test
        @DisplayName("empty catalog YAML should load with zero definitions [GH-90000]")
        void shouldLoadEmptyCatalog(@TempDir Path tempDir) throws IOException { // GH-90000
            Path catalogFile = tempDir.resolve("agent-catalog.yaml [GH-90000]");
            Files.writeString(catalogFile, // GH-90000
                    "catalog:\n  id: 'empty-catalog'\n  name: 'Empty'\n  agents: []\n");

            AgentCatalog catalog = loader.loadFromFile(catalogFile); // GH-90000
            assertThat(catalog.getCatalogId()).isEqualTo("empty-catalog [GH-90000]");
            assertThat(catalog.getDefinitions()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("should handle agent YAML with no 'id' field gracefully [GH-90000]")
        void shouldSkipAgentWithNoId(@TempDir Path tempDir) throws IOException { // GH-90000
            Path agentDir = tempDir.resolve("agents [GH-90000]");
            Files.createDirectories(agentDir); // GH-90000
            Files.writeString(agentDir.resolve("bad-agent.yaml [GH-90000]"),
                    "name: No ID Agent\nversion: 1.0.0\n");

            Path catalogFile = tempDir.resolve("agent-catalog.yaml [GH-90000]");
            Files.writeString(catalogFile, // GH-90000
                    "catalog:\n  id: 'test'\n  name: 'Test'\n  agents:\n    - 'agents/**/*.yaml'\n");

            AgentCatalog catalog = loader.loadFromFile(catalogFile); // GH-90000
            // Agent without id is skipped
            assertThat(catalog.getDefinitions()).isEmpty(); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // discoverAndLoad
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("discoverAndLoad() [GH-90000]")
    class DiscoverAndLoadTests {

        @Test
        @DisplayName("should discover and load both catalogs from test root [GH-90000]")
        void shouldDiscoverAndLoadAll() throws IOException { // GH-90000
            List<AgentCatalog> catalogs = loader.discoverAndLoad(catalogTestRoot); // GH-90000

            assertThat(catalogs).hasSize(2); // GH-90000
            assertThat(catalogs).extracting(AgentCatalog::getCatalogId) // GH-90000
                    .containsExactlyInAnyOrder("test-platform", "test-product"); // GH-90000
        }

        @Test
        @DisplayName("total definitions across all discovered catalogs should be 3 [GH-90000]")
        void shouldHaveCorrectTotalDefinitions() throws IOException { // GH-90000
            List<AgentCatalog> catalogs = loader.discoverAndLoad(catalogTestRoot); // GH-90000

            int total = catalogs.stream().mapToInt(c -> c.getDefinitions().size()).sum(); // GH-90000
            // catalog-a: 2 agents, catalog-b: 1 agent
            assertThat(total).isEqualTo(3); // GH-90000
        }

        @Test
        @DisplayName("should return empty list for empty directory [GH-90000]")
        void shouldReturnEmptyForEmptyDirectory(@TempDir Path tempDir) throws IOException { // GH-90000
            List<AgentCatalog> catalogs = loader.discoverAndLoad(tempDir); // GH-90000
            assertThat(catalogs).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("should skip malformed catalog YAML and continue loading others [GH-90000]")
        void shouldSkipMalformedCatalog(@TempDir Path tempDir) throws IOException { // GH-90000
            // Write an invalid YAML
            Path badDir = tempDir.resolve("bad [GH-90000]");
            Files.createDirectories(badDir); // GH-90000
            Files.writeString(badDir.resolve("agent-catalog.yaml [GH-90000]"),
                    "{ invalid yaml: [unclosed");

            // Write a valid catalog
            Path goodDir = tempDir.resolve("good [GH-90000]");
            Files.createDirectories(goodDir); // GH-90000
            Files.writeString(goodDir.resolve("agent-catalog.yaml [GH-90000]"),
                    "catalog:\n  id: 'good'\n  name: 'Good'\n  agents: []\n");

            List<AgentCatalog> catalogs = loader.discoverAndLoad(tempDir); // GH-90000
            // Only the good catalog loads
            assertThat(catalogs).hasSize(1); // GH-90000
            assertThat(catalogs.get(0).getCatalogId()).isEqualTo("good [GH-90000]");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Querying via loaded catalog
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("End-to-end: Query loaded catalog [GH-90000]")
    class EndToEndQueryTests {

        @Test
        @DisplayName("should find by domain from loaded YAML [GH-90000]")
        void shouldFindByDomain() throws IOException { // GH-90000
            Path catalogA = catalogTestRoot.resolve("catalog-a/agent-catalog.yaml [GH-90000]");
            AgentCatalog catalog = loader.loadFromFile(catalogA); // GH-90000

            List<CatalogAgentEntry> monitoringAgents = catalog.findByDomain("monitoring [GH-90000]");
            assertThat(monitoringAgents).hasSize(1); // GH-90000
            assertThat(monitoringAgents.get(0).getId()).isEqualTo("test-sentinel-agent [GH-90000]");
        }

        @Test
        @DisplayName("should find by level from loaded YAML [GH-90000]")
        void shouldFindByLevel() throws IOException { // GH-90000
            Path catalogA = catalogTestRoot.resolve("catalog-a/agent-catalog.yaml [GH-90000]");
            AgentCatalog catalog = loader.loadFromFile(catalogA); // GH-90000

            // Both agents have level: worker
            List<CatalogAgentEntry> workerAgents = catalog.findByLevel("worker [GH-90000]");
            assertThat(workerAgents).hasSize(2); // GH-90000
        }

        @Test
        @DisplayName("should find by capability from loaded YAML [GH-90000]")
        void shouldFindByCapability() throws IOException { // GH-90000
            Path catalogA = catalogTestRoot.resolve("catalog-a/agent-catalog.yaml [GH-90000]");
            AgentCatalog catalog = loader.loadFromFile(catalogA); // GH-90000

            List<CatalogAgentEntry> alertingAgents = catalog.findByCapability("alerting [GH-90000]");
            assertThat(alertingAgents).hasSize(1); // GH-90000
            assertThat(alertingAgents.get(0).getId()).isEqualTo("test-sentinel-agent [GH-90000]");
        }

        @Test
        @DisplayName("should find data-transformer agent by id from loaded YAML [GH-90000]")
        void shouldFindDataTransformerById() throws IOException { // GH-90000
            Path catalogA = catalogTestRoot.resolve("catalog-a/agent-catalog.yaml [GH-90000]");
            AgentCatalog catalog = loader.loadFromFile(catalogA); // GH-90000

            Optional<CatalogAgentEntry> result = catalog.findById("test-data-transformer-agent [GH-90000]");
            assertThat(result).isPresent(); // GH-90000
            assertThat(result.get().getVersion()).isEqualTo("2.0.0 [GH-90000]");
        }
    }
}
