/*
 * Copyright (c) 2026 Ghatana Inc. 
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
@ExtendWith(MockitoExtension.class) 
class DataCloudAgentReleaseRepositoryTest extends EventloopTestBase {

    private static final String TENANT_ID = "tenant-test-001";

    @Mock
    private DataCloudClient dataCloud;

    @Mock
    private EntityInterface mockEntity;

    private DataCloudAgentReleaseRepository repo;

    @BeforeEach
    void setUp() { 
        repo = new DataCloudAgentReleaseRepository(dataCloud, TENANT_ID); 
    }

    // ─────────────────── helpers ────────────────────────────────────────────

    private AgentRelease minimalRelease() { 
        return new AgentReleaseBuilder() 
                .agentId("agent-001")
                .tenantId(TENANT_ID)
                .releaseVersion("1.0.0")
                .redactionProfileId("rp-test")
                .threatModelId("tm-test")
                .addPermittedPurpose("agent.inference")
                .capabilityMaturityProfile("L1")
                .build(); 
    }

    /** Builds the data map that {@code save()} would pass to createEntity. */ 
    private Map<String, Object> minimalDataMap(AgentRelease r) { 
        Map<String, Object> m = new HashMap<>(); 
        m.put("agentReleaseId", r.agentReleaseId()); 
        m.put("agentId", r.agentId()); 
        m.put("tenantId", r.tenantId());
        m.put("specVersion", r.specVersion()); 
        m.put("releaseVersion", r.releaseVersion()); 
        m.put("state", r.state().name()); 
        m.put("compatibleRuntimeVersions", List.of()); 
        m.put("dataClassesHandled", List.of()); 
        m.put("permittedPurposes", List.of(r.permittedPurposes().iterator().next())); 
        m.put("redactionProfileId", r.redactionProfileId()); 
        m.put("threatModelId", r.threatModelId()); 
        m.put("capabilityMaturityProfile", r.capabilityMaturityProfile()); 
        m.put("createdAt", r.createdAt().toString()); 
        m.put("updatedAt", r.updatedAt().toString()); 
        return m;
    }

    private void stubCreateEntity() { 
        UUID id = UUID.randomUUID(); 
        when(mockEntity.getId()).thenReturn(id); 
        when(dataCloud.createEntity(any(), any(), any())) 
                .thenReturn(Promise.of(mockEntity)); 
    }

    // ─────────────────── save ───────────────────────────────────────────────

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("delegates to dataCloud.createEntity with correct collection")
        void delegatesToDataCloud() { 
            stubCreateEntity(); 
            AgentRelease release = minimalRelease(); 

            AgentRelease result = runPromise(() -> repo.save(release)); 

            assertThat(result.agentReleaseId()).isEqualTo(release.agentReleaseId()); 
            verify(dataCloud).createEntity(eq(TENANT_ID), eq(DataCloudAgentReleaseRepository.COLLECTION), any()); 
        }

        @Test
        @DisplayName("propagates DataCloud failure")
        void propagatesFailure() { 
            when(dataCloud.createEntity(any(), any(), any())) 
                    .thenReturn(Promise.ofException(new RuntimeException("DC unavailable")));

            assertThatThrownBy(() -> runPromise(() -> repo.save(minimalRelease()))) 
                    .hasMessageContaining("DC unavailable");
        }
    }

    // ─────────────────── findById ────────────────────────────────────────────

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("returns release when entity found")
        void returnsReleaseWhenFound() { 
            AgentRelease release = minimalRelease(); 
            Map<String, Object> data = minimalDataMap(release); 
            EntityInterface entity = mock(EntityInterface.class); 
            when(entity.getData()).thenReturn(data); 
            when(dataCloud.queryEntities(any(), any(), any())) 
                    .thenReturn(Promise.of(List.of(entity))); 

            Optional<AgentRelease> found = runPromise(() -> repo.findById(release.agentReleaseId())); 

            assertThat(found).isPresent(); 
            assertThat(found.get().agentReleaseId()).isEqualTo(release.agentReleaseId()); 
        }

        @Test
        @DisplayName("returns empty when entity not found")
        void returnsEmptyWhenNotFound() { 
            when(dataCloud.queryEntities(any(), any(), any())) 
                    .thenReturn(Promise.of(List.of())); 

            Optional<AgentRelease> found = runPromise(() -> repo.findById("no-such-id"));

            assertThat(found).isEmpty(); 
        }
    }

    // ─────────────────── findByState ────────────────────────────────────────

    @Test
    @DisplayName("findByState queries collection with state filter")
    void findByStateQueriesCollection() { 
        AgentRelease active = new AgentReleaseBuilder() 
                .agentId("agent-001")
                .tenantId(TENANT_ID)
                .releaseVersion("1.0.0")
                .state(AgentReleaseState.ACTIVE) 
                .redactionProfileId("rp-test")
                .threatModelId("tm-test")
                .addPermittedPurpose("agent.inference")
                .capabilityMaturityProfile("L1")
                .build(); 
        Map<String, Object> data = minimalDataMap(active); 
        EntityInterface entity = mock(EntityInterface.class); 
        when(entity.getData()).thenReturn(data); 
        when(dataCloud.queryEntities(eq(TENANT_ID), eq(DataCloudAgentReleaseRepository.COLLECTION), any())) 
                .thenReturn(Promise.of(List.of(entity))); 

        List<AgentRelease> results = runPromise(() -> repo.findByState(AgentReleaseState.ACTIVE)); 

        assertThat(results).hasSize(1); 
        verify(dataCloud).queryEntities(eq(TENANT_ID), eq(DataCloudAgentReleaseRepository.COLLECTION), any()); 
    }

    // ─────────────────── transition ─────────────────────────────────────────

    @Test
    @DisplayName("transition saves updated state")
    void transitionSavesUpdatedState() { 
        AgentRelease draft = minimalRelease(); 
        Map<String, Object> draftData = minimalDataMap(draft); 
        EntityInterface entity = mock(EntityInterface.class); 
        when(entity.getData()).thenReturn(draftData); 

        // findById stub → returns one entity
        when(dataCloud.queryEntities(any(), any(), any())) 
                .thenReturn(Promise.of(List.of(entity))); 
        // save stub → returns mock entity
        stubCreateEntity(); 

        AgentRelease validated = runPromise(() -> 
                repo.transition(draft.agentReleaseId(), AgentReleaseState.VALIDATED, "admin@test.com")); 

        assertThat(validated.state()).isEqualTo(AgentReleaseState.VALIDATED); 
    }

    @Test
    @DisplayName("transition fails when release not found")
    void transitionFailsWhenNotFound() { 
        when(dataCloud.queryEntities(any(), any(), any())) 
                .thenReturn(Promise.of(List.of())); 

        assertThatThrownBy(() -> runPromise(() -> 
                repo.transition("no-such-id", AgentReleaseState.VALIDATED, "admin@test.com"))) 
                .hasMessageContaining("no-such-id");
    }
}
