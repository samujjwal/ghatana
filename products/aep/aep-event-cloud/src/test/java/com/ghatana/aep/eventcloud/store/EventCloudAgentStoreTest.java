/*
 * Copyright (c) 2026 Ghatana Inc.
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
@DisplayName("EventCloudAgentStore")
@ExtendWith(MockitoExtension.class)
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
    void setUp() {
        agentStore = new EventCloudAgentStore(entityStore);
    }

    @Test
    void shouldSaveAgent() {
        // GIVEN
        Entity savedEntity = new Entity(
            EntityId.of(AGENT_ID), EventCloudAgentStore.COLLECTION,
            Map.of("id", AGENT_ID, "name", "TestAgent"), null);
        when(entityStore.save(any(TenantContext.class), any(Entity.class)))
            .thenReturn(Promise.of(savedEntity));

        Map<String, Object> agentData = Map.of(
            "name", "TestAgent",
            "type", "REACTIVE",
            "version", "1.0"
        );

        // WHEN
        Entity result = runPromise(() -> agentStore.save(TENANT, AGENT_ID, agentData));

        // THEN
        assertThat(result).isNotNull();
        verify(entityStore).save(any(TenantContext.class), entityCaptor.capture());
        Entity captured = entityCaptor.getValue();
        assertThat(captured.collection()).isEqualTo("aep_agents");
        assertThat(captured.data().get("id")).isEqualTo(AGENT_ID);
        assertThat(captured.data().get("name")).isEqualTo("TestAgent");
        assertThat(captured.data().get("type")).isEqualTo("REACTIVE");
        assertThat(captured.data().get("status")).isEqualTo("ACTIVE");
    }

    @Test
    void shouldFindAgentById() {
        // GIVEN
        Entity entity = new Entity(
            EntityId.of(AGENT_ID), EventCloudAgentStore.COLLECTION,
            Map.of("id", AGENT_ID, "name", "TestAgent"), null);
        when(entityStore.findById(any(TenantContext.class), eq(EntityId.of(AGENT_ID))))
            .thenReturn(Promise.of(Optional.of(entity)));

        // WHEN
        Optional<Entity> result = runPromise(() -> agentStore.findById(TENANT, AGENT_ID));

        // THEN
        assertThat(result).isPresent();
        assertThat(result.get().data().get("name")).isEqualTo("TestAgent");
    }

    @Test
    void shouldReturnEmptyWhenAgentNotFound() {
        // GIVEN
        when(entityStore.findById(any(TenantContext.class), eq(EntityId.of("nonexistent"))))
            .thenReturn(Promise.of(Optional.empty()));

        // WHEN
        Optional<Entity> result = runPromise(() -> agentStore.findById(TENANT, "nonexistent"));

        // THEN
        assertThat(result).isEmpty();
    }

    @Test
    void shouldListAgents() {
        // GIVEN
        var entities = List.of(
            new Entity(EntityId.of("a1"), EventCloudAgentStore.COLLECTION,
                Map.of("name", "Agent1"), null),
            new Entity(EntityId.of("a2"), EventCloudAgentStore.COLLECTION,
                Map.of("name", "Agent2"), null));
        QueryResult queryResult = new QueryResult(entities, 2L, false);
        when(entityStore.query(any(TenantContext.class), any(QuerySpec.class)))
            .thenReturn(Promise.of(queryResult));

        // WHEN
        List<Entity> result = runPromise(() -> agentStore.listAgents(TENANT, 100));

        // THEN
        assertThat(result).hasSize(2);
        verify(entityStore).query(any(TenantContext.class), queryCaptor.capture());
        assertThat(queryCaptor.getValue().collection()).isEqualTo("aep_agents");
    }

    @Test
    void shouldListByType() {
        // GIVEN
        var entities = List.of(
            new Entity(EntityId.of("a1"), EventCloudAgentStore.COLLECTION,
                Map.of("type", "REACTIVE"), null),
            new Entity(EntityId.of("a2"), EventCloudAgentStore.COLLECTION,
                Map.of("type", "DELIBERATIVE"), null),
            new Entity(EntityId.of("a3"), EventCloudAgentStore.COLLECTION,
                Map.of("type", "REACTIVE"), null));
        QueryResult queryResult = new QueryResult(entities, 3L, false);
        when(entityStore.query(any(TenantContext.class), any(QuerySpec.class)))
            .thenReturn(Promise.of(queryResult));

        // WHEN
        List<Entity> result = runPromise(() ->
            agentStore.listByType(TENANT, "REACTIVE", 100));

        // THEN
        assertThat(result).hasSize(2)
            .allSatisfy(e -> assertThat(e.data().get("type")).isEqualTo("REACTIVE"));
    }

    @Test
    void shouldDeleteAgent() {
        // GIVEN
        when(entityStore.delete(any(TenantContext.class), eq(EntityId.of(AGENT_ID))))
            .thenReturn(Promise.of(null));

        // WHEN
        runPromise(() -> agentStore.delete(TENANT, AGENT_ID));

        // THEN
        verify(entityStore).delete(any(), eq(EntityId.of(AGENT_ID)));
    }

    @Test
    void shouldCountAgents() {
        // GIVEN
        when(entityStore.count(any(TenantContext.class), any(QuerySpec.class)))
            .thenReturn(Promise.of(5L));

        // WHEN
        Long count = runPromise(() -> agentStore.count(TENANT));

        // THEN
        assertThat(count).isEqualTo(5L);
    }

    @Test
    void shouldCheckExistence() {
        // GIVEN
        when(entityStore.exists(any(TenantContext.class), eq(EntityId.of(AGENT_ID))))
            .thenReturn(Promise.of(true));

        // WHEN
        Boolean exists = runPromise(() -> agentStore.exists(TENANT, AGENT_ID));

        // THEN
        assertThat(exists).isTrue();
    }

    @Test
    void shouldSetDefaultStatusOnSave() {
        // GIVEN
        when(entityStore.save(any(TenantContext.class), any(Entity.class)))
            .thenReturn(Promise.of(new Entity(EntityId.of(AGENT_ID), "aep_agents", Map.of(), null)));

        // WHEN - save without explicit status
        runPromise(() -> agentStore.save(TENANT, AGENT_ID, Map.of("name", "New")));

        // THEN
        verify(entityStore).save(any(), entityCaptor.capture());
        assertThat(entityCaptor.getValue().data().get("status")).isEqualTo("ACTIVE");
    }

    @Test
    void shouldPreserveExplicitStatus() {
        // GIVEN
        when(entityStore.save(any(TenantContext.class), any(Entity.class)))
            .thenReturn(Promise.of(new Entity(EntityId.of(AGENT_ID), "aep_agents", Map.of(), null)));

        // WHEN
        runPromise(() -> agentStore.save(TENANT, AGENT_ID,
            Map.of("name", "Paused", "status", "PAUSED")));

        // THEN
        verify(entityStore).save(any(), entityCaptor.capture());
        assertThat(entityCaptor.getValue().data().get("status")).isEqualTo("PAUSED");
    }
}
