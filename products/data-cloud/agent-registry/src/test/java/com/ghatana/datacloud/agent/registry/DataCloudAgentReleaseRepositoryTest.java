/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.agent.registry;

import com.ghatana.agent.release.AgentRelease;
import com.ghatana.agent.release.AgentReleaseBuilder;
import com.ghatana.agent.release.AgentReleaseState;
import com.ghatana.datacloud.client.DataCloudClient;
import com.ghatana.datacloud.entity.EntityInterface;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DataCloudAgentReleaseRepository}.
 *
 * <p>Verifies serialization/deserialization, delegation to {@link DataCloudClient},
 * and state-transition behavior through the repository.
 *
 * @doc.type class
 * @doc.purpose Tests for DataCloudAgentReleaseRepository
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudAgentReleaseRepository")
@ExtendWith(MockitoExtension.class) // GH-90000
class DataCloudAgentReleaseRepositoryTest extends EventloopTestBase {

    private static final String TENANT_ID = "tenant-test-001";

    @Mock
    private DataCloudClient dataCloud;

    @Mock
    private EntityInterface mockEntity;

    private DataCloudAgentReleaseRepository repo;

    @BeforeEach
    void setUp() { // GH-90000
        repo = new DataCloudAgentReleaseRepository(dataCloud, TENANT_ID); // GH-90000
    }

    // ─────────────────── helpers ────────────────────────────────────────────

    private AgentRelease minimalRelease() { // GH-90000
        return new AgentReleaseBuilder() // GH-90000
                .agentId("agent-001")
                .releaseVersion("1.0.0")
                .redactionProfileId("rp-test")
                .threatModelId("tm-test")
                .addPermittedPurpose("agent.inference")
                .capabilityMaturityProfile("L1")
                .build(); // GH-90000
    }

    /** Builds the data map that {@code save()} would pass to createEntity. */ // GH-90000
    private Map<String, Object> minimalDataMap(AgentRelease r) { // GH-90000
        Map<String, Object> m = new HashMap<>(); // GH-90000
        m.put("agentReleaseId", r.agentReleaseId()); // GH-90000
        m.put("agentId", r.agentId()); // GH-90000
        m.put("specVersion", r.specVersion()); // GH-90000
        m.put("releaseVersion", r.releaseVersion()); // GH-90000
        m.put("state", r.state().name()); // GH-90000
        m.put("compatibleRuntimeVersions", List.of()); // GH-90000
        m.put("dataClassesHandled", List.of()); // GH-90000
        m.put("permittedPurposes", List.of(r.permittedPurposes().iterator().next())); // GH-90000
        m.put("redactionProfileId", r.redactionProfileId()); // GH-90000
        m.put("threatModelId", r.threatModelId()); // GH-90000
        m.put("capabilityMaturityProfile", r.capabilityMaturityProfile()); // GH-90000
        m.put("createdAt", r.createdAt().toString()); // GH-90000
        m.put("updatedAt", r.updatedAt().toString()); // GH-90000
        return m;
    }

    private void stubCreateEntity() { // GH-90000
        UUID id = UUID.randomUUID(); // GH-90000
        when(mockEntity.getId()).thenReturn(id); // GH-90000
        when(dataCloud.createEntity(any(), any(), any())) // GH-90000
                .thenReturn(Promise.of(mockEntity)); // GH-90000
    }

    // ─────────────────── save ───────────────────────────────────────────────

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("delegates to dataCloud.createEntity with correct collection")
        void delegatesToDataCloud() { // GH-90000
            stubCreateEntity(); // GH-90000
            AgentRelease release = minimalRelease(); // GH-90000

            AgentRelease result = runPromise(() -> repo.save(release)); // GH-90000

            assertThat(result.agentReleaseId()).isEqualTo(release.agentReleaseId()); // GH-90000
            verify(dataCloud).createEntity(eq(TENANT_ID), eq(DataCloudAgentReleaseRepository.COLLECTION), any()); // GH-90000
        }

        @Test
        @DisplayName("propagates DataCloud failure")
        void propagatesFailure() { // GH-90000
            when(dataCloud.createEntity(any(), any(), any())) // GH-90000
                    .thenReturn(Promise.ofException(new RuntimeException("DC unavailable")));

            assertThatThrownBy(() -> runPromise(() -> repo.save(minimalRelease()))) // GH-90000
                    .hasMessageContaining("DC unavailable");
        }
    }

    // ─────────────────── findById ────────────────────────────────────────────

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("returns release when entity found")
        void returnsReleaseWhenFound() { // GH-90000
            AgentRelease release = minimalRelease(); // GH-90000
            Map<String, Object> data = minimalDataMap(release); // GH-90000
            EntityInterface entity = mock(EntityInterface.class); // GH-90000
            when(entity.getData()).thenReturn(data); // GH-90000
            when(dataCloud.queryEntities(any(), any(), any())) // GH-90000
                    .thenReturn(Promise.of(List.of(entity))); // GH-90000

            Optional<AgentRelease> found = runPromise(() -> repo.findById(release.agentReleaseId())); // GH-90000

            assertThat(found).isPresent(); // GH-90000
            assertThat(found.get().agentReleaseId()).isEqualTo(release.agentReleaseId()); // GH-90000
        }

        @Test
        @DisplayName("returns empty when entity not found")
        void returnsEmptyWhenNotFound() { // GH-90000
            when(dataCloud.queryEntities(any(), any(), any())) // GH-90000
                    .thenReturn(Promise.of(List.of())); // GH-90000

            Optional<AgentRelease> found = runPromise(() -> repo.findById("no-such-id"));

            assertThat(found).isEmpty(); // GH-90000
        }
    }

    // ─────────────────── findByState ────────────────────────────────────────

    @Test
    @DisplayName("findByState queries collection with state filter")
    void findByStateQueriesCollection() { // GH-90000
        AgentRelease active = new AgentReleaseBuilder() // GH-90000
                .agentId("agent-001")
                .releaseVersion("1.0.0")
                .state(AgentReleaseState.ACTIVE) // GH-90000
                .redactionProfileId("rp-test")
                .threatModelId("tm-test")
                .addPermittedPurpose("agent.inference")
                .capabilityMaturityProfile("L1")
                .build(); // GH-90000
        Map<String, Object> data = minimalDataMap(active); // GH-90000
        EntityInterface entity = mock(EntityInterface.class); // GH-90000
        when(entity.getData()).thenReturn(data); // GH-90000
        when(dataCloud.queryEntities(eq(TENANT_ID), eq(DataCloudAgentReleaseRepository.COLLECTION), any())) // GH-90000
                .thenReturn(Promise.of(List.of(entity))); // GH-90000

        List<AgentRelease> results = runPromise(() -> repo.findByState(AgentReleaseState.ACTIVE)); // GH-90000

        assertThat(results).hasSize(1); // GH-90000
        verify(dataCloud).queryEntities(eq(TENANT_ID), eq(DataCloudAgentReleaseRepository.COLLECTION), any()); // GH-90000
    }

    // ─────────────────── transition ─────────────────────────────────────────

    @Test
    @DisplayName("transition saves updated state")
    void transitionSavesUpdatedState() { // GH-90000
        AgentRelease draft = minimalRelease(); // GH-90000
        Map<String, Object> draftData = minimalDataMap(draft); // GH-90000
        EntityInterface entity = mock(EntityInterface.class); // GH-90000
        when(entity.getData()).thenReturn(draftData); // GH-90000

        // findById stub → returns one entity
        when(dataCloud.queryEntities(any(), any(), any())) // GH-90000
                .thenReturn(Promise.of(List.of(entity))); // GH-90000
        // save stub → returns mock entity
        stubCreateEntity(); // GH-90000

        AgentRelease validated = runPromise(() -> // GH-90000
                repo.transition(draft.agentReleaseId(), AgentReleaseState.VALIDATED, "admin@test.com")); // GH-90000

        assertThat(validated.state()).isEqualTo(AgentReleaseState.VALIDATED); // GH-90000
    }

    @Test
    @DisplayName("transition fails when release not found")
    void transitionFailsWhenNotFound() { // GH-90000
        when(dataCloud.queryEntities(any(), any(), any())) // GH-90000
                .thenReturn(Promise.of(List.of())); // GH-90000

        assertThatThrownBy(() -> runPromise(() -> // GH-90000
                repo.transition("no-such-id", AgentReleaseState.VALIDATED, "admin@test.com"))) // GH-90000
                .hasMessageContaining("no-such-id");
    }
}
