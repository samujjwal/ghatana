/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.aep.eventcloud.store;

import com.ghatana.datacloud.spi.EntityStore;
import com.ghatana.datacloud.spi.EntityStore.Entity;
import com.ghatana.datacloud.spi.EntityStore.EntityId;
import com.ghatana.datacloud.spi.EntityStore.QueryResult;
import com.ghatana.datacloud.spi.EntityStore.QuerySpec;
import com.ghatana.datacloud.spi.TenantContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link EventCloudAgentStore}.
 */
@DisplayName("EventCloudAgentStore [GH-90000]")
@ExtendWith(MockitoExtension.class) // GH-90000
class EventCloudAgentStoreTest extends EventloopTestBase {

    private static final String TENANT = "test-tenant";
    private static final String AGENT_ID = "agent-001";

    @Mock
    private EntityStore entityStore;

    @Captor
    private ArgumentCaptor<Entity> entityCaptor;

    @Captor
    private ArgumentCaptor<QuerySpec> queryCaptor;

    private EventCloudAgentStore agentStore;

    @BeforeEach
    void setUp() { // GH-90000
        agentStore = new EventCloudAgentStore(entityStore); // GH-90000
    }

    @Test
    void shouldSaveAgent() { // GH-90000
        // GIVEN
        Entity savedEntity = new Entity( // GH-90000
            EntityId.of(AGENT_ID), EventCloudAgentStore.COLLECTION, // GH-90000
            Map.of("id", AGENT_ID, "name", "TestAgent"), null); // GH-90000
        when(entityStore.save(any(TenantContext.class), any(Entity.class))) // GH-90000
            .thenReturn(Promise.of(savedEntity)); // GH-90000

        Map<String, Object> agentData = Map.of( // GH-90000
            "name", "TestAgent",
            "type", "REACTIVE",
            "version", "1.0"
        );

        // WHEN
        Entity result = runPromise(() -> agentStore.save(TENANT, AGENT_ID, agentData)); // GH-90000

        // THEN
        assertThat(result).isNotNull(); // GH-90000
        verify(entityStore).save(any(TenantContext.class), entityCaptor.capture()); // GH-90000
        Entity captured = entityCaptor.getValue(); // GH-90000
        assertThat(captured.collection()).isEqualTo("aep_agents [GH-90000]");
        assertThat(captured.data().get("id [GH-90000]")).isEqualTo(AGENT_ID);
        assertThat(captured.data().get("name [GH-90000]")).isEqualTo("TestAgent [GH-90000]");
        assertThat(captured.data().get("type [GH-90000]")).isEqualTo("REACTIVE [GH-90000]");
        assertThat(captured.data().get("status [GH-90000]")).isEqualTo("ACTIVE [GH-90000]");
    }

    @Test
    void shouldFindAgentById() { // GH-90000
        // GIVEN
        Entity entity = new Entity( // GH-90000
            EntityId.of(AGENT_ID), EventCloudAgentStore.COLLECTION, // GH-90000
            Map.of("id", AGENT_ID, "name", "TestAgent"), null); // GH-90000
        when(entityStore.findById(any(TenantContext.class), eq(EntityId.of(AGENT_ID)))) // GH-90000
            .thenReturn(Promise.of(Optional.of(entity))); // GH-90000

        // WHEN
        Optional<Entity> result = runPromise(() -> agentStore.findById(TENANT, AGENT_ID)); // GH-90000

        // THEN
        assertThat(result).isPresent(); // GH-90000
        assertThat(result.get().data().get("name [GH-90000]")).isEqualTo("TestAgent [GH-90000]");
    }

    @Test
    void shouldReturnEmptyWhenAgentNotFound() { // GH-90000
        // GIVEN
        when(entityStore.findById(any(TenantContext.class), eq(EntityId.of("nonexistent [GH-90000]"))))
            .thenReturn(Promise.of(Optional.empty())); // GH-90000

        // WHEN
        Optional<Entity> result = runPromise(() -> agentStore.findById(TENANT, "nonexistent")); // GH-90000

        // THEN
        assertThat(result).isEmpty(); // GH-90000
    }

    @Test
    void shouldListAgents() { // GH-90000
        // GIVEN
        var entities = List.of( // GH-90000
            new Entity(EntityId.of("a1 [GH-90000]"), EventCloudAgentStore.COLLECTION,
                Map.of("name", "Agent1"), null), // GH-90000
            new Entity(EntityId.of("a2 [GH-90000]"), EventCloudAgentStore.COLLECTION,
                Map.of("name", "Agent2"), null)); // GH-90000
        QueryResult queryResult = new QueryResult(entities, 2L, false); // GH-90000
        when(entityStore.query(any(TenantContext.class), any(QuerySpec.class))) // GH-90000
            .thenReturn(Promise.of(queryResult)); // GH-90000

        // WHEN
        List<Entity> result = runPromise(() -> agentStore.listAgents(TENANT, 100)); // GH-90000

        // THEN
        assertThat(result).hasSize(2); // GH-90000
        verify(entityStore).query(any(TenantContext.class), queryCaptor.capture()); // GH-90000
        assertThat(queryCaptor.getValue().collection()).isEqualTo("aep_agents [GH-90000]");
    }

    @Test
    void shouldListByType() { // GH-90000
        // GIVEN
        var entities = List.of( // GH-90000
            new Entity(EntityId.of("a1 [GH-90000]"), EventCloudAgentStore.COLLECTION,
                Map.of("type", "REACTIVE"), null), // GH-90000
            new Entity(EntityId.of("a2 [GH-90000]"), EventCloudAgentStore.COLLECTION,
                Map.of("type", "DELIBERATIVE"), null), // GH-90000
            new Entity(EntityId.of("a3 [GH-90000]"), EventCloudAgentStore.COLLECTION,
                Map.of("type", "REACTIVE"), null)); // GH-90000
        QueryResult queryResult = new QueryResult(entities, 3L, false); // GH-90000
        when(entityStore.query(any(TenantContext.class), any(QuerySpec.class))) // GH-90000
            .thenReturn(Promise.of(queryResult)); // GH-90000

        // WHEN
        List<Entity> result = runPromise(() -> // GH-90000
            agentStore.listByType(TENANT, "REACTIVE", 100)); // GH-90000

        // THEN
        assertThat(result).hasSize(2) // GH-90000
            .allSatisfy(e -> assertThat(e.data().get("type [GH-90000]")).isEqualTo("REACTIVE [GH-90000]"));
    }

    @Test
    void shouldDeleteAgent() { // GH-90000
        // GIVEN
        when(entityStore.delete(any(TenantContext.class), eq(EntityId.of(AGENT_ID)))) // GH-90000
            .thenReturn(Promise.of(null)); // GH-90000

        // WHEN
        runPromise(() -> agentStore.delete(TENANT, AGENT_ID)); // GH-90000

        // THEN
        verify(entityStore).delete(any(), eq(EntityId.of(AGENT_ID))); // GH-90000
    }

    @Test
    void shouldCountAgents() { // GH-90000
        // GIVEN
        when(entityStore.count(any(TenantContext.class), any(QuerySpec.class))) // GH-90000
            .thenReturn(Promise.of(5L)); // GH-90000

        // WHEN
        Long count = runPromise(() -> agentStore.count(TENANT)); // GH-90000

        // THEN
        assertThat(count).isEqualTo(5L); // GH-90000
    }

    @Test
    void shouldCheckExistence() { // GH-90000
        // GIVEN
        when(entityStore.exists(any(TenantContext.class), eq(EntityId.of(AGENT_ID)))) // GH-90000
            .thenReturn(Promise.of(true)); // GH-90000

        // WHEN
        Boolean exists = runPromise(() -> agentStore.exists(TENANT, AGENT_ID)); // GH-90000

        // THEN
        assertThat(exists).isTrue(); // GH-90000
    }

    @Test
    void shouldSetDefaultStatusOnSave() { // GH-90000
        // GIVEN
        when(entityStore.save(any(TenantContext.class), any(Entity.class))) // GH-90000
            .thenReturn(Promise.of(new Entity(EntityId.of(AGENT_ID), "aep_agents", Map.of(), null))); // GH-90000

        // WHEN - save without explicit status
        runPromise(() -> agentStore.save(TENANT, AGENT_ID, Map.of("name", "New"))); // GH-90000

        // THEN
        verify(entityStore).save(any(), entityCaptor.capture()); // GH-90000
        assertThat(entityCaptor.getValue().data().get("status [GH-90000]")).isEqualTo("ACTIVE [GH-90000]");
    }

    @Test
    void shouldPreserveExplicitStatus() { // GH-90000
        // GIVEN
        when(entityStore.save(any(TenantContext.class), any(Entity.class))) // GH-90000
            .thenReturn(Promise.of(new Entity(EntityId.of(AGENT_ID), "aep_agents", Map.of(), null))); // GH-90000

        // WHEN
        runPromise(() -> agentStore.save(TENANT, AGENT_ID, // GH-90000
            Map.of("name", "Paused", "status", "PAUSED"))); // GH-90000

        // THEN
        verify(entityStore).save(any(), entityCaptor.capture()); // GH-90000
        assertThat(entityCaptor.getValue().data().get("status [GH-90000]")).isEqualTo("PAUSED [GH-90000]");
    }
}
