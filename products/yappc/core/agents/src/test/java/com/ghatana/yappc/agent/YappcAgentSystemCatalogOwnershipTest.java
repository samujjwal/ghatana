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
@DisplayName("YappcAgentSystem Catalog Ownership Tests")
class YappcAgentSystemCatalogOwnershipTest extends EventloopTestBase {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("initialize reports runtime-backed and planning-only catalog entries")
    void initializeReportsRuntimeBackedAndPlanningOnlyCatalogEntries() throws Exception {
        writeCatalogFixture(
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

        YappcAgentSystem system = newStubSystem();

        runPromise(system::initialize);

        YappcAgentSystem.CatalogOwnershipReport report = system.getCatalogOwnershipReport();
        assertThat(report.runtimeBackedCatalogIds()).containsExactly("intake-specialist");
        assertThat(report.planningOnlyCatalogIds()).containsExactly("planner-only-agent");
        assertThat(report.catalogOnlyCatalogIds()).isEmpty();
        assertThat(report.unownedRuntimeAgents()).isNotEmpty();
    }

    @Test
    @DisplayName("initialize reports obsolete runtime ownership bindings")
    void initializeReportsObsoleteRuntimeOwnershipBindings() throws Exception {
        writeCatalogFixture(
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

        YappcAgentSystem system = newStubSystem();

        runPromise(system::initialize);

        YappcAgentSystem.CatalogOwnershipReport report = system.getCatalogOwnershipReport();
        assertThat(report.obsoleteRuntimeBindings())
                .containsExactly("unknown-catalog-agent -> IntakeSpecialistAgent (architecture.intake)");
    }

    @Test
    @DisplayName("initialize treats catalog-only entries as ownership gaps")
    void initializeTreatsCatalogOnlyEntriesAsOwnershipGaps() throws Exception {
        writeCatalogFixture(
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

        YappcAgentSystem system = newStubSystem();

        runPromise(system::initialize);

        YappcAgentSystem.CatalogOwnershipReport report = system.getCatalogOwnershipReport();
        assertThat(report.catalogOnlyCatalogIds()).containsExactly("catalog-only-agent");
        assertThat(report.hasOwnershipGaps()).isTrue();
        assertThat(report.hasRuntimeDrift()).isFalse();
    }

    private YappcAgentSystem newStubSystem() {
        MemoryStore memoryStore = mock(MemoryStore.class);
        return YappcAgentSystem.builder()
                .eventloop(eventloop())
                .memoryStore(memoryStore)
                .aiRuntimeMode(YappcAgentSystem.AiRuntimeMode.STUB)
                .configBasePath(tempDir.toString())
                .build();
    }

    private void writeCatalogFixture(String lifecycleCatalogYaml, String runtimeOwnershipYaml)
            throws IOException {
        Files.writeString(
                tempDir.resolve("_index.yaml"),
                """
                spec:
                  catalogs:
                    - name: lifecycle
                      file: lifecycle-catalog.yaml
                """);
        Files.writeString(tempDir.resolve("lifecycle-catalog.yaml"), lifecycleCatalogYaml);
        Files.writeString(tempDir.resolve("runtime-ownership.yaml"), runtimeOwnershipYaml);
    }
}
