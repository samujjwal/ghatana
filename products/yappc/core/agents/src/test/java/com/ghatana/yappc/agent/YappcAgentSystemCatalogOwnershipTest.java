package com.ghatana.yappc.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link YappcAgentSystem} catalog/runtime ownership reconciliation.
 *
 * @doc.type class
 * @doc.purpose Verify runtime ownership reporting against temporary catalog fixtures
 * @doc.layer test
 * @doc.pattern Test
 */
@DisplayName("YappcAgentSystem Catalog Ownership Tests [GH-90000]")
class YappcAgentSystemCatalogOwnershipTest extends EventloopTestBase {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("initialize reports runtime-backed and planning-only catalog entries [GH-90000]")
    void initializeReportsRuntimeBackedAndPlanningOnlyCatalogEntries() throws Exception { // GH-90000
        writeCatalogFixture( // GH-90000
                """
                spec:
                  agents:
                    - id: intake-specialist
                      name: Intake Specialist
                      agentType: hybrid
                    - id: planner-only-agent
                      name: Planner Only Agent
                      agentType: planning
                """,
                """
                spec:
                  bindings:
                    - catalogId: intake-specialist
                      runtimeAgentId: IntakeSpecialistAgent
                      runtimeStepName: architecture.intake
                """);

        YappcAgentSystem system = newStubSystem(); // GH-90000

        runPromise(system::initialize); // GH-90000

        YappcAgentSystem.CatalogOwnershipReport report = system.getCatalogOwnershipReport(); // GH-90000
        assertThat(report.runtimeBackedCatalogIds()).containsExactly("intake-specialist [GH-90000]");
        assertThat(report.planningOnlyCatalogIds()).containsExactly("planner-only-agent [GH-90000]");
        assertThat(report.catalogOnlyCatalogIds()).isEmpty(); // GH-90000
        assertThat(report.unownedRuntimeAgents()).isNotEmpty(); // GH-90000
    }

    @Test
    @DisplayName("initialize reports obsolete runtime ownership bindings [GH-90000]")
    void initializeReportsObsoleteRuntimeOwnershipBindings() throws Exception { // GH-90000
        writeCatalogFixture( // GH-90000
                """
                spec:
                  agents:
                    - id: intake-specialist
                      name: Intake Specialist
                      agentType: hybrid
                """,
                """
                spec:
                  bindings:
                    - catalogId: unknown-catalog-agent
                      runtimeAgentId: IntakeSpecialistAgent
                      runtimeStepName: architecture.intake
                """);

        YappcAgentSystem system = newStubSystem(); // GH-90000

        runPromise(system::initialize); // GH-90000

        YappcAgentSystem.CatalogOwnershipReport report = system.getCatalogOwnershipReport(); // GH-90000
        assertThat(report.obsoleteRuntimeBindings()) // GH-90000
                .containsExactly("unknown-catalog-agent -> IntakeSpecialistAgent (architecture.intake) [GH-90000]");
    }

    @Test
    @DisplayName("initialize treats catalog-only entries as ownership gaps [GH-90000]")
    void initializeTreatsCatalogOnlyEntriesAsOwnershipGaps() throws Exception { // GH-90000
        writeCatalogFixture( // GH-90000
                """
                spec:
                  agents:
                    - id: catalog-only-agent
                      name: Catalog Only Agent
                      agentType: hybrid
                """,
                """
                spec:
                  bindings: []
                """);

        YappcAgentSystem system = newStubSystem(); // GH-90000

        runPromise(system::initialize); // GH-90000

        YappcAgentSystem.CatalogOwnershipReport report = system.getCatalogOwnershipReport(); // GH-90000
        assertThat(report.catalogOnlyCatalogIds()).containsExactly("catalog-only-agent [GH-90000]");
        assertThat(report.hasOwnershipGaps()).isTrue(); // GH-90000
        // Runtime drift is true because there are unowned SDLC agents registered
        assertThat(report.hasRuntimeDrift()).isTrue(); // GH-90000
    }

    private YappcAgentSystem newStubSystem() { // GH-90000
        MemoryStore memoryStore = mock(MemoryStore.class); // GH-90000
        return YappcAgentSystem.builder() // GH-90000
                .eventloop(eventloop()) // GH-90000
                .memoryStore(memoryStore) // GH-90000
                .aiRuntimeMode(YappcAgentSystem.AiRuntimeMode.STUB) // GH-90000
                .configBasePath(tempDir.toString()) // GH-90000
                .build(); // GH-90000
    }

    private void writeCatalogFixture(String lifecycleCatalogYaml, String runtimeOwnershipYaml) // GH-90000
            throws IOException {
        Files.writeString( // GH-90000
                tempDir.resolve("_index.yaml [GH-90000]"),
                """
                spec:
                  catalogs:
                    - name: lifecycle
                      file: lifecycle-catalog.yaml
                """);
        Files.writeString(tempDir.resolve("lifecycle-catalog.yaml [GH-90000]"), lifecycleCatalogYaml);
        Files.writeString(tempDir.resolve("runtime-ownership.yaml [GH-90000]"), runtimeOwnershipYaml);
    }
}
