/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.di;

import com.ghatana.aep.agent.AepContextBridge;
import com.ghatana.aep.config.EnvConfig;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.agent.memory.model.working.WorkingMemoryConfig;
import com.ghatana.agent.memory.persistence.JdbcMemoryItemRepository;
import com.ghatana.agent.memory.persistence.JdbcTaskStateRepository;
import com.ghatana.agent.memory.persistence.MemoryItemRepository;
import com.ghatana.agent.memory.persistence.MemoryStoreAdapter;
import com.ghatana.agent.memory.persistence.PersistentMemoryPlane;
import com.ghatana.agent.memory.store.MemoryPlane;
import com.ghatana.agent.memory.store.taskstate.JdbcTaskStateStore;
import com.ghatana.agent.memory.store.taskstate.TaskStateStore;
import com.ghatana.core.database.config.JpaConfig;
import com.ghatana.statestore.checkpoint.CheckpointStorage;
import com.ghatana.statestore.checkpoint.PostgresCheckpointStorage;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;

import javax.sql.DataSource;

/**
 * ActiveJ DI module for AEP agent lifecycle components.
 *
 * <p>Provides the GAA agent-framework bindings required for running agents
 * through the PERCEIVE → REASON → ACT → CAPTURE → REFLECT pipeline:
 * <ul>
 *   <li>{@link DataSource} — HikariCP JDBC pool for the AEP PostgreSQL database;
 *       configured from {@code AEP_DB_*} environment variables</li>
 *   <li>{@link CheckpointStorage} → {@link PostgresCheckpointStorage} — durable
 *       agent-lifecycle checkpoint store backed by the {@code aep_checkpoints} table</li>
 *   <li>{@link MemoryItemRepository} → {@link JdbcMemoryItemRepository} — JDBC-backed
 *       store for episodic, semantic, and procedural memory items</li>
 *   <li>{@link TaskStateStore} → {@link JdbcTaskStateStore} — task-state persistence</li>
 *   <li>{@link WorkingMemoryConfig} — bounded working-memory configuration (1000 items / 10 MB)</li>
 *   <li>{@link MemoryPlane} → {@link PersistentMemoryPlane} — multi-tier agent memory plane</li>
 *   <li>{@link MemoryStore} → {@link MemoryStoreAdapter} — bridge from MemoryPlane to the
 *       agent-framework MemoryStore SPI</li>
 *   <li>{@link AepContextBridge} — translates AEP {@code AgentExecutionContext} into the
 *       richer {@link com.ghatana.agent.framework.api.AgentContext} expected by BaseAgent</li>
 * </ul>
 *
 * <h2>Required Environment Variables</h2>
 * <table border="1">
 *   <tr><th>Variable</th><th>Default</th><th>Description</th></tr>
 *   <tr><td>AEP_DB_PASSWORD</td><td><i>required</i></td><td>AEP database password</td></tr>
 *   <tr><td>AEP_DB_URL</td><td>jdbc:postgresql://localhost:5432/aep</td><td>JDBC URL</td></tr>
 *   <tr><td>AEP_DB_USERNAME</td><td>aep</td><td>Database username</td></tr>
 *   <tr><td>AEP_DB_POOL_SIZE</td><td>10</td><td>HikariCP max pool size</td></tr>
 * </table>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * Injector injector = Injector.of(
 *     new AepCoreModule(),
 *     new AepObservabilityModule(),
 *     new AepAgentModule()
 * );
 * AepContextBridge bridge = injector.getInstance(AepContextBridge.class);
 * CheckpointStorage checkpoints = injector.getInstance(CheckpointStorage.class);
 * MemoryStore memory = injector.getInstance(MemoryStore.class);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose ActiveJ DI module for GAA agent lifecycle components (DataSource, CheckpointStorage, MemoryStore)
 * @doc.layer product
 * @doc.pattern Module
 * @doc.gaa.lifecycle perceive|reason|act|capture|reflect
 * @see AepContextBridge
 * @see PostgresCheckpointStorage
 * @see PersistentMemoryPlane
 * @see MemoryStoreAdapter
 */
public class AepAgentModule extends AbstractModule {

    /**
     * Provides the AEP JDBC {@link DataSource} using HikariCP.
     *
     * <p>Configuration is read from {@code AEP_DB_*} environment variables.
     * {@code AEP_DB_PASSWORD} is mandatory and will cause a fast-fail
     * {@link IllegalStateException} at startup if absent.
     *
     * @return configured HikariCP data source
     */
    @Provides
    DataSource dataSource() {
        EnvConfig env = EnvConfig.fromSystem();
        return JpaConfig.builder()
                .jdbcUrl(env.aepDbUrl())
                .username(env.aepDbUsername())
                .password(env.aepDbPassword())
                .entityPackages("com.ghatana.aep")         // required by JpaConfig but unused (JDBC only)
                .poolSize(env.aepDbPoolSize())
                .ddlAuto("none")
                .showSql(false)
                .build()
                .createDataSource();
    }

    /**
     * Provides the {@link CheckpointStorage} implementation bound to
     * {@link PostgresCheckpointStorage}.
     *
     * <p>Backed by the {@code aep_checkpoints} table (V007 migration).
     * Uses a virtual-thread executor for off-loop JDBC operations.
     *
     * @param dataSource the AEP data source
     * @return durable checkpoint storage
     */
    @Provides
    CheckpointStorage checkpointStorage(DataSource dataSource) {
        return new PostgresCheckpointStorage(dataSource);
    }

    /**
     * Provides the {@link MemoryItemRepository} implementation bound to
     * {@link JdbcMemoryItemRepository}.
     *
     * <p>Backed by the {@code agent_memory_items} table. Uses the shared AEP data source.
     *
     * @param dataSource the AEP data source
     * @return JDBC-backed memory item repository
     */
    @Provides
    MemoryItemRepository memoryItemRepository(DataSource dataSource) {
        return new JdbcMemoryItemRepository(dataSource);
    }

    /**
     * Provides the {@link TaskStateStore} implementation bound to {@link JdbcTaskStateStore}.
     *
     * <p>Initialized without an EventCloud – event sourcing for task state is not required
     * in the AEP standalone deployment. Enable by passing a DataCloudClient when available.
     *
     * @return JDBC-backed task state store
     */
    @Provides
    TaskStateStore taskStateStore() {
        return new JdbcTaskStateStore(new JdbcTaskStateRepository());
    }

    /**
     * Provides the {@link WorkingMemoryConfig} for bounded in-process working memory.
     *
     * <p>Defaults: 1000 entries, 10 MB, LRU eviction.
     *
     * @return working memory configuration
     */
    @Provides
    WorkingMemoryConfig workingMemoryConfig() {
        return WorkingMemoryConfig.builder().build();
    }

    /**
     * Provides the {@link MemoryPlane} implementation bound to {@link PersistentMemoryPlane}.
     *
     * <p>Wires together {@link MemoryItemRepository}, {@link TaskStateStore}, and
     * {@link WorkingMemoryConfig}. EventCloud archival is omitted here — the AEP
     * event pipeline handles durability at the infrastructure level.
     *
     * @param itemRepository     JDBC-backed item store
     * @param taskStateStore     task state persistence
     * @param workingMemoryConfig bounded-memory configuration
     * @return persistent multi-tier memory plane
     */
    @Provides
    MemoryPlane memoryPlane(MemoryItemRepository itemRepository,
                            TaskStateStore taskStateStore,
                            WorkingMemoryConfig workingMemoryConfig) {
        return new PersistentMemoryPlane(itemRepository, taskStateStore, workingMemoryConfig);
    }

    /**
     * Provides the {@link MemoryStore} bound to {@link MemoryStoreAdapter}.
     *
     * <p>Adapts the {@link MemoryPlane} to the {@link MemoryStore} SPI expected by the
     * GAA agent-framework, enabling agents to read and write episodic, semantic, and
     * procedural memory across turns.
     *
     * @param memoryPlane the persistent memory plane
     * @return memory store adapter
     */
    @Provides
    MemoryStore memoryStore(MemoryPlane memoryPlane) {
        return new MemoryStoreAdapter(memoryPlane);
    }

    /**
     * Provides the {@link AepContextBridge} singleton.
     *
     * <p>The bridge translates the thin AEP {@code AgentExecutionContext} (which
     * carries only {@code tenantId}) into the rich GAA {@link com.ghatana.agent.framework.api.AgentContext}
     * (which carries turnId, traceId, startTime, memoryStore, and logger).
     *
     * @param memoryStore memory store to attach to every agent context
     * @return configured context bridge
     */
    @Provides
    AepContextBridge aepContextBridge(MemoryStore memoryStore) {
        return new AepContextBridge(memoryStore);
    }
}
