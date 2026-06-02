package com.ghatana.agent.mastery;

import com.ghatana.agent.memory.governance.GovernedMemoryPlane;
import com.ghatana.agent.memory.model.fact.EnhancedFact;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose E2E-style mastery governance validation against current memory APIs
 * @doc.layer test
 * @doc.pattern Test
 */
@DisplayName("GAA Mastery Lifecycle E2E")
class GaaMasteryLifecycleE2ETest extends EventloopTestBase {

    @Test
    @DisplayName("obsolete procedures are excluded by default and included when requested")
    void obsoleteFilteringLifecycleWorks() {
        GovernedMemoryPlane governed = createGovernedPlane();

        EnhancedProcedure practiced = procedure("skill-practiced", "PRACTICED");
        EnhancedProcedure obsolete = procedure("skill-obsolete", "OBSOLETE");

        runPromise(() -> governed.storeProcedure(practiced));
        runPromise(() -> governed.storeProcedure(obsolete));

        List<EnhancedProcedure> activeOnly = runPromise(() -> governed.queryProcedures(
                MemoryQuery.builder()
                        .tenantId("test-tenant")
                        .agentId("test-agent")
                        .includeObsolete(false)
                        .build()
        ));
        assertEquals(1, activeOnly.size());
        assertEquals("skill-practiced", activeOnly.get(0).getId());

        List<EnhancedProcedure> withObsolete = runPromise(() -> governed.queryProcedures(
                MemoryQuery.builder()
                        .tenantId("test-tenant")
                        .agentId("test-agent")
                        .includeObsolete(true)
                        .build()
        ));
        assertEquals(2, withObsolete.size());
    }

    @Test
    @DisplayName("negative knowledge requires evidence and justification")
    void negativeKnowledgeValidationIsEnforced() {
        GovernedMemoryPlane governed = createGovernedPlane();

        EnhancedFact invalid = EnhancedFact.builder()
                .id("fact-invalid")
                .tenantId("test-tenant")
                .agentId("test-agent")
                .subject("endpoint")
                .predicate("status")
                .object("deprecated")
                .labels(Map.of(
                        "learningTarget", "NEGATIVE_KNOWLEDGE",
                        "validationState", "VALIDATED"
                ))
                .build();

        Exception error = assertThrows(Exception.class, () -> runPromise(() -> governed.storeFact(invalid)));
        assertTrue(error.getMessage().contains("negative knowledge requires")
                || (error.getCause() != null && error.getCause().getMessage().contains("negative knowledge requires")));

        EnhancedFact valid = EnhancedFact.builder()
                .id("fact-valid")
                .tenantId("test-tenant")
                .agentId("test-agent")
                .subject("endpoint")
                .predicate("status")
                .object("deprecated")
                .labels(Map.of(
                        "learningTarget", "NEGATIVE_KNOWLEDGE",
                        "validationState", "VALIDATED",
                        "evidenceRef", "ticket-123",
                        "justification", "Deprecated in API v2"
                ))
                .build();

        EnhancedFact stored = runPromise(() -> governed.storeFact(valid));
        assertEquals("fact-valid", stored.getId());
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
