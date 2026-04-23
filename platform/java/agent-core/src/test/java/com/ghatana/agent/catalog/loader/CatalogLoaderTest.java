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
@DisplayName("CatalogLoader")
class CatalogLoaderTest {

    private CatalogLoader loader;
    private Path catalogTestRoot;

    @BeforeEach
    void setUp() throws Exception { // GH-90000
        loader = new CatalogLoader(); // GH-90000

        // Load fixture root from classpath
        URL resource = getClass().getClassLoader().getResource("catalog-test");
        assertThat(resource).as("catalog-test resource must exist").isNotNull();
        catalogTestRoot = Path.of(resource.toURI()); // GH-90000
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // discoverCatalogFiles
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("discoverCatalogFiles()")
    class DiscoverCatalogFilesTests {

        @Test
        @DisplayName("should find all agent-catalog.yaml files in tree")
        void shouldDiscoverAllCatalogFiles() throws IOException { // GH-90000
            List<Path> files = loader.discoverCatalogFiles(catalogTestRoot); // GH-90000
            // catalog-a and catalog-b each have one agent-catalog.yaml
            assertThat(files).hasSize(2); // GH-90000
            assertThat(files).allMatch(p -> p.getFileName().toString().equals("agent-catalog.yaml"));
        }

        @Test
        @DisplayName("should skip .git directories during discovery")
        void shouldSkipGitDirectory(@TempDir Path tempDir) throws IOException { // GH-90000
            Path gitDir = tempDir.resolve(".git");
            Files.createDirectories(gitDir); // GH-90000
            Files.writeString(gitDir.resolve("agent-catalog.yaml"),
                    "catalog:\n  id: 'hidden'\n  name: 'Hidden'\n  agents: []\n");

            // Only the .git-internal file should be skipped
            List<Path> files = loader.discoverCatalogFiles(tempDir); // GH-90000
            assertThat(files).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("should skip node_modules directories during discovery")
        void shouldSkipNodeModules(@TempDir Path tempDir) throws IOException { // GH-90000
            Path nodeModules = tempDir.resolve("node_modules");
            Files.createDirectories(nodeModules); // GH-90000
            Files.writeString(nodeModules.resolve("agent-catalog.yaml"),
                    "catalog:\n  id: 'npm-hidden'\n  name: 'NPM'\n  agents: []\n");

            List<Path> files = loader.discoverCatalogFiles(tempDir); // GH-90000
            assertThat(files).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("should return empty list when no catalog files exist")
        void shouldReturnEmptyWhenNoCatalogs(@TempDir Path tempDir) throws IOException { // GH-90000
            List<Path> files = loader.discoverCatalogFiles(tempDir); // GH-90000
            assertThat(files).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("null root should throw NullPointerException")
        void shouldThrowOnNullRoot() { // GH-90000
            assertThatThrownBy(() -> loader.discoverCatalogFiles(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // loadFromFile
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("loadFromFile()")
    class LoadFromFileTests {

        @Test
        @DisplayName("should load catalog-a with correct id and display name")
        void shouldLoadCatalogAId() throws IOException { // GH-90000
            Path catalogA = catalogTestRoot.resolve("catalog-a/agent-catalog.yaml");
            AgentCatalog catalog = loader.loadFromFile(catalogA); // GH-90000

            assertThat(catalog.getCatalogId()).isEqualTo("test-platform");
            assertThat(catalog.getDisplayName()).isEqualTo("Test Platform Catalog");
        }

        @Test
        @DisplayName("should load agent definitions from catalog-a")
        void shouldLoadAgentDefinitions() throws IOException { // GH-90000
            Path catalogA = catalogTestRoot.resolve("catalog-a/agent-catalog.yaml");
            AgentCatalog catalog = loader.loadFromFile(catalogA); // GH-90000

            // catalog-a has 2 agent YAML files
            assertThat(catalog.getDefinitions()).hasSize(2); // GH-90000
        }

        @Test
        @DisplayName("should load agent with correct id")
        void shouldLoadAgentWithCorrectId() throws IOException { // GH-90000
            Path catalogA = catalogTestRoot.resolve("catalog-a/agent-catalog.yaml");
            AgentCatalog catalog = loader.loadFromFile(catalogA); // GH-90000

            Optional<CatalogAgentEntry> sentinel = catalog.findById("test-sentinel-agent");
            assertThat(sentinel).isPresent(); // GH-90000
            assertThat(sentinel.get().getName()).isEqualTo("Test Sentinel Agent");
        }

        @Test
        @DisplayName("should load agent capabilities")
        void shouldLoadAgentCapabilities() throws IOException { // GH-90000
            Path catalogA = catalogTestRoot.resolve("catalog-a/agent-catalog.yaml");
            AgentCatalog catalog = loader.loadFromFile(catalogA); // GH-90000

            Optional<CatalogAgentEntry> sentinel = catalog.findById("test-sentinel-agent");
            assertThat(sentinel).isPresent(); // GH-90000
            assertThat(sentinel.get().getCapabilities()).containsExactlyInAnyOrder( // GH-90000
                    "monitoring", "alerting");
        }

        @Test
        @DisplayName("should load all available capabilities from catalog")
        void shouldLoadAllCapabilities() throws IOException { // GH-90000
            Path catalogA = catalogTestRoot.resolve("catalog-a/agent-catalog.yaml");
            AgentCatalog catalog = loader.loadFromFile(catalogA); // GH-90000

            assertThat(catalog.getAllCapabilities()) // GH-90000
                    .contains("monitoring", "alerting", "data-transformation", "json-schema-validation"); // GH-90000
        }

        @Test
        @DisplayName("should resolve priority from catalog metadata")
        void shouldResolvePriority() throws IOException { // GH-90000
            Path catalogA = catalogTestRoot.resolve("catalog-a/agent-catalog.yaml");
            AgentCatalog catalog = loader.loadFromFile(catalogA); // GH-90000

            assertThat(catalog.priority()).isEqualTo(100); // GH-90000
        }

        @Test
        @DisplayName("should load catalog-b with product-specific agent")
        void shouldLoadCatalogB() throws IOException { // GH-90000
            Path catalogB = catalogTestRoot.resolve("catalog-b/agent-catalog.yaml");
            AgentCatalog catalog = loader.loadFromFile(catalogB); // GH-90000

            assertThat(catalog.getCatalogId()).isEqualTo("test-product");
            assertThat(catalog.getDefinitions()).hasSize(1); // GH-90000

            Optional<CatalogAgentEntry> productAgent = catalog.findById("test-product-agent");
            assertThat(productAgent).isPresent(); // GH-90000
        }

        @Test
        @DisplayName("null path should throw NullPointerException")
        void shouldThrowOnNullPath() { // GH-90000
            assertThatThrownBy(() -> loader.loadFromFile(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("invalid YAML path should throw IOException")
        void shouldThrowOnInvalidPath(@TempDir Path tempDir) { // GH-90000
            Path nonExistent = tempDir.resolve("does-not-exist.yaml");
            assertThatThrownBy(() -> loader.loadFromFile(nonExistent)) // GH-90000
                    .isInstanceOf(IOException.class); // GH-90000
        }

        @Test
        @DisplayName("empty catalog YAML should load with zero definitions")
        void shouldLoadEmptyCatalog(@TempDir Path tempDir) throws IOException { // GH-90000
            Path catalogFile = tempDir.resolve("agent-catalog.yaml");
            Files.writeString(catalogFile, // GH-90000
                    "catalog:\n  id: 'empty-catalog'\n  name: 'Empty'\n  agents: []\n");

            AgentCatalog catalog = loader.loadFromFile(catalogFile); // GH-90000
            assertThat(catalog.getCatalogId()).isEqualTo("empty-catalog");
            assertThat(catalog.getDefinitions()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("should handle agent YAML with no 'id' field gracefully")
        void shouldSkipAgentWithNoId(@TempDir Path tempDir) throws IOException { // GH-90000
            Path agentDir = tempDir.resolve("agents");
            Files.createDirectories(agentDir); // GH-90000
            Files.writeString(agentDir.resolve("bad-agent.yaml"),
                    "name: No ID Agent\nversion: 1.0.0\n");

            Path catalogFile = tempDir.resolve("agent-catalog.yaml");
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
    @DisplayName("discoverAndLoad()")
    class DiscoverAndLoadTests {

        @Test
        @DisplayName("should discover and load both catalogs from test root")
        void shouldDiscoverAndLoadAll() throws IOException { // GH-90000
            List<AgentCatalog> catalogs = loader.discoverAndLoad(catalogTestRoot); // GH-90000

            assertThat(catalogs).hasSize(2); // GH-90000
            assertThat(catalogs).extracting(AgentCatalog::getCatalogId) // GH-90000
                    .containsExactlyInAnyOrder("test-platform", "test-product"); // GH-90000
        }

        @Test
        @DisplayName("total definitions across all discovered catalogs should be 3")
        void shouldHaveCorrectTotalDefinitions() throws IOException { // GH-90000
            List<AgentCatalog> catalogs = loader.discoverAndLoad(catalogTestRoot); // GH-90000

            int total = catalogs.stream().mapToInt(c -> c.getDefinitions().size()).sum(); // GH-90000
            // catalog-a: 2 agents, catalog-b: 1 agent
            assertThat(total).isEqualTo(3); // GH-90000
        }

        @Test
        @DisplayName("should return empty list for empty directory")
        void shouldReturnEmptyForEmptyDirectory(@TempDir Path tempDir) throws IOException { // GH-90000
            List<AgentCatalog> catalogs = loader.discoverAndLoad(tempDir); // GH-90000
            assertThat(catalogs).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("should skip malformed catalog YAML and continue loading others")
        void shouldSkipMalformedCatalog(@TempDir Path tempDir) throws IOException { // GH-90000
            // Write an invalid YAML
            Path badDir = tempDir.resolve("bad");
            Files.createDirectories(badDir); // GH-90000
            Files.writeString(badDir.resolve("agent-catalog.yaml"),
                    "{ invalid yaml: [unclosed");

            // Write a valid catalog
            Path goodDir = tempDir.resolve("good");
            Files.createDirectories(goodDir); // GH-90000
            Files.writeString(goodDir.resolve("agent-catalog.yaml"),
                    "catalog:\n  id: 'good'\n  name: 'Good'\n  agents: []\n");

            List<AgentCatalog> catalogs = loader.discoverAndLoad(tempDir); // GH-90000
            // Only the good catalog loads
            assertThat(catalogs).hasSize(1); // GH-90000
            assertThat(catalogs.get(0).getCatalogId()).isEqualTo("good");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Querying via loaded catalog
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("End-to-end: Query loaded catalog")
    class EndToEndQueryTests {

        @Test
        @DisplayName("should find by domain from loaded YAML")
        void shouldFindByDomain() throws IOException { // GH-90000
            Path catalogA = catalogTestRoot.resolve("catalog-a/agent-catalog.yaml");
            AgentCatalog catalog = loader.loadFromFile(catalogA); // GH-90000

            List<CatalogAgentEntry> monitoringAgents = catalog.findByDomain("monitoring");
            assertThat(monitoringAgents).hasSize(1); // GH-90000
            assertThat(monitoringAgents.get(0).getId()).isEqualTo("test-sentinel-agent");
        }

        @Test
        @DisplayName("should find by level from loaded YAML")
        void shouldFindByLevel() throws IOException { // GH-90000
            Path catalogA = catalogTestRoot.resolve("catalog-a/agent-catalog.yaml");
            AgentCatalog catalog = loader.loadFromFile(catalogA); // GH-90000

            // Both agents have level: worker
            List<CatalogAgentEntry> workerAgents = catalog.findByLevel("worker");
            assertThat(workerAgents).hasSize(2); // GH-90000
        }

        @Test
        @DisplayName("should find by capability from loaded YAML")
        void shouldFindByCapability() throws IOException { // GH-90000
            Path catalogA = catalogTestRoot.resolve("catalog-a/agent-catalog.yaml");
            AgentCatalog catalog = loader.loadFromFile(catalogA); // GH-90000

            List<CatalogAgentEntry> alertingAgents = catalog.findByCapability("alerting");
            assertThat(alertingAgents).hasSize(1); // GH-90000
            assertThat(alertingAgents.get(0).getId()).isEqualTo("test-sentinel-agent");
        }

        @Test
        @DisplayName("should find data-transformer agent by id from loaded YAML")
        void shouldFindDataTransformerById() throws IOException { // GH-90000
            Path catalogA = catalogTestRoot.resolve("catalog-a/agent-catalog.yaml");
            AgentCatalog catalog = loader.loadFromFile(catalogA); // GH-90000

            Optional<CatalogAgentEntry> result = catalog.findById("test-data-transformer-agent");
            assertThat(result).isPresent(); // GH-90000
            assertThat(result.get().getVersion()).isEqualTo("2.0.0");
        }
    }
}
