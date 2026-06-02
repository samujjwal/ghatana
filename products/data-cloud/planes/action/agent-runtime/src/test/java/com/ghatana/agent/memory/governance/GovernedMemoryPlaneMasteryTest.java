package com.ghatana.agent.memory.governance;

import com.ghatana.agent.mastery.MasteryRegistry;
import com.ghatana.agent.memory.model.procedure.EnhancedProcedure;
import com.ghatana.agent.memory.persistence.InMemoryMemoryPlane;
import com.ghatana.agent.memory.store.MemoryQuery;
import com.ghatana.agent.memory.store.taskstate.TaskStateStore;
import com.ghatana.data.governance.DataAccessBroker;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Validates mastery-aware filtering in GovernedMemoryPlane
 * @doc.layer test
 * @doc.pattern Test
 */
@DisplayName("GovernedMemoryPlane Mastery Integration Tests")
class GovernedMemoryPlaneMasteryTest extends EventloopTestBase {

    @Test
    @DisplayName("filters OBSOLETE by default")
    void filtersObsoleteByDefault() {
        GovernedMemoryPlane governed = createGovernedPlane();

        runPromise(() -> governed.storeProcedure(procedure("proc-obsolete", "OBSOLETE")));
        runPromise(() -> governed.storeProcedure(procedure("proc-normal", "PRACTICED")));

        List<EnhancedProcedure> procedures = runPromise(() -> governed.queryProcedures(
                MemoryQuery.builder()
                        .tenantId("test-tenant")
                        .agentId("test-agent")
                        .includeObsolete(false)
                        .build()
        ));

        assertEquals(1, procedures.size());
        assertEquals("proc-normal", procedures.get(0).getId());
    }

    @Test
    @DisplayName("includes OBSOLETE when requested")
    void includesObsoleteWhenRequested() {
        GovernedMemoryPlane governed = createGovernedPlane();

        runPromise(() -> governed.storeProcedure(procedure("proc-obsolete", "OBSOLETE")));
        runPromise(() -> governed.storeProcedure(procedure("proc-normal", "PRACTICED")));

        List<EnhancedProcedure> procedures = runPromise(() -> governed.queryProcedures(
                MemoryQuery.builder()
                        .tenantId("test-tenant")
                        .agentId("test-agent")
                        .includeObsolete(true)
                        .build()
        ));

        assertEquals(2, procedures.size());
    }

    @Test
    @DisplayName("filters MAINTENANCE_ONLY by default")
    void filtersMaintenanceOnlyByDefault() {
        GovernedMemoryPlane governed = createGovernedPlane();

        runPromise(() -> governed.storeProcedure(procedure("proc-maintenance", "MAINTENANCE_ONLY")));
        runPromise(() -> governed.storeProcedure(procedure("proc-normal", "PRACTICED")));

        List<EnhancedProcedure> procedures = runPromise(() -> governed.queryProcedures(
                MemoryQuery.builder()
                        .tenantId("test-tenant")
                        .agentId("test-agent")
                        .includeMaintenanceOnly(false)
                        .build()
        ));

        assertEquals(1, procedures.size());
        assertEquals("proc-normal", procedures.get(0).getId());
    }

    @Test
    @DisplayName("includes MAINTENANCE_ONLY when requested")
    void includesMaintenanceOnlyWhenRequested() {
        GovernedMemoryPlane governed = createGovernedPlane();

        runPromise(() -> governed.storeProcedure(procedure("proc-maintenance", "MAINTENANCE_ONLY")));
        runPromise(() -> governed.storeProcedure(procedure("proc-normal", "PRACTICED")));

        List<EnhancedProcedure> procedures = runPromise(() -> governed.queryProcedures(
                MemoryQuery.builder()
                        .tenantId("test-tenant")
                        .agentId("test-agent")
                        .includeMaintenanceOnly(true)
                        .build()
        ));

        assertEquals(2, procedures.size());
    }

    private GovernedMemoryPlane createGovernedPlane() {
        DataAccessBroker accessBroker = mock(DataAccessBroker.class);
        when(accessBroker.checkAccess("test-tenant", "test-subject", "agent.memory", "agent.context.hydration"))
                .thenReturn(Promise.complete());

        InMemoryMemoryPlane memoryPlane = new InMemoryMemoryPlane(mock(TaskStateStore.class));
        MasteryRegistry masteryRegistry = mock(MasteryRegistry.class);

        return new GovernedMemoryPlane(
                memoryPlane,
                accessBroker,
                "test-tenant",
                "test-subject",
                masteryRegistry
        );
    }

    private EnhancedProcedure procedure(String id, String masteryState) {
        return EnhancedProcedure.builder()
                .id(id)
                .tenantId("test-tenant")
                .agentId("test-agent")
                .situation("situation-" + id)
                .action("action-" + id)
                .confidence(0.8)
                .successRate(0.8)
                .labels(Map.of(
                        "learningTarget", "PROCEDURAL_SKILL",
                        "skillId", id,
                        "masteryState", masteryState,
                        "provenanceRequired", "true",
                        "provenance", "episode-1"
                ))
                .build();
    }
}
