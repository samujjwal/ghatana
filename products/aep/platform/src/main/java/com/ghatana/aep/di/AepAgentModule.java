/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.di;

import com.ghatana.aep.agent.AepContextBridge;
import com.ghatana.aep.config.EnvConfig;
import com.ghatana.agent.framework.memory.MemoryStore;
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
 *   <li>{@link AepContextBridge} — translates AEP {@code AgentExecutionContext} into the
 *       richer {@link com.ghatana.agent.framework.api.AgentContext} expected by BaseAgent</li>
 *   <li>{@link MemoryStore} — defaults to {@code MemoryStore.noOp()} until Track 0D
 *       ({@code PersistentMemoryPlane}) is wired</li>
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
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose ActiveJ DI module for GAA agent lifecycle components (DataSource, CheckpointStorage, AepContextBridge)
 * @doc.layer product
 * @doc.pattern Module
 * @doc.gaa.lifecycle perceive|reason|act|capture|reflect
 * @see AepContextBridge
 * @see PostgresCheckpointStorage
 * @see CheckpointStorage
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
     * Provides the default {@link MemoryStore} for AEP agents.
     *
     * <p>Returns {@link MemoryStore#noOp()} as a placeholder.  Track 0D
     * ({@code PersistentMemoryPlane}) will replace this binding with a
     * fully-persistent implementation backed by the agent memory PostgreSQL tables.
     *
     * @return no-op memory store
     */
    @Provides
    MemoryStore memoryStore() {
        return MemoryStore.noOp();
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
