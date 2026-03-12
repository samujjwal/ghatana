/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.agent;

import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.api.OutputGenerator;
import com.ghatana.agent.framework.config.AgentDefinition;
import com.ghatana.agent.framework.memory.Episode;
import com.ghatana.agent.framework.runtime.BaseAgent;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Bridges an {@link AgentDefinition} blueprint into the GAA {@link BaseAgent} lifecycle.
 *
 * <p>An {@code AepAgentAdapter} wraps a versioned, YAML-sourced definition and makes it
 * executable through the standard PERCEIVE → REASON → ACT → CAPTURE → REFLECT pipeline.
 *
 * <h2>Lifecycle Mapping</h2>
 * <table border="1">
 *   <tr><th>GAA Phase</th><th>AepAgentAdapter behaviour</th></tr>
 *   <tr><td>PERCEIVE</td><td>Returns input enriched with episode count from MemoryStore</td></tr>
 *   <tr><td>REASON</td><td>Delegates to the injected {@link OutputGenerator}</td></tr>
 *   <tr><td>ACT</td><td>Logs output; returns it unchanged (side effects added in Phase 2)</td></tr>
 *   <tr><td>CAPTURE</td><td>Appends an {@link Episode} to the agent MemoryStore off-loop</td></tr>
 *   <tr><td>REFLECT</td><td>Fire-and-forget: logs reflection intent (EventCloud integration: AEP-P3)</td></tr>
 * </table>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * AgentDefinition def = catalog.find("fraud-detector");
 * OutputGenerator<String, String> llmGen = new LLMOutputGenerator(gateway, def.getSystemPrompt());
 * AepAgentAdapter agent = new AepAgentAdapter(def, llmGen);
 *
 * AgentContext ctx = contextBridge.toAgentContext(execCtx, def.getId(), traceId);
 * Promise<String> result = agent.executeTurn(eventJson, ctx);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Adapts AgentDefinition into a runnable GAA BaseAgent for AEP pipelines
 * @doc.layer product
 * @doc.pattern Adapter, Template Method
 * @doc.gaa.lifecycle perceive|reason|act|capture|reflect
 */
public class AepAgentAdapter extends BaseAgent<String, String> {

    private static final Logger log = LoggerFactory.getLogger(AepAgentAdapter.class);

    private final AgentDefinition definition;
    private final Executor blockingExecutor;

    /**
     * Creates an adapter using a virtual-thread executor for off-loop JDBC calls.
     *
     * @param definition      agent blueprint (id, systemPrompt, constraints, …)
     * @param outputGenerator output generator for the REASON phase (e.g., LLM gateway)
     */
    public AepAgentAdapter(
            @NotNull AgentDefinition definition,
            @NotNull OutputGenerator<String, String> outputGenerator) {
        this(definition, outputGenerator, Executors.newVirtualThreadPerTaskExecutor());
    }

    /**
     * Creates an adapter with a custom blocking executor.
     *
     * @param definition       agent blueprint
     * @param outputGenerator  output generator for the REASON phase
     * @param blockingExecutor executor used to wrap JDBC/IO calls off the event loop
     */
    public AepAgentAdapter(
            @NotNull AgentDefinition definition,
            @NotNull OutputGenerator<String, String> outputGenerator,
            @NotNull Executor blockingExecutor) {
        super(
                Objects.requireNonNull(definition, "definition cannot be null").getId(),
                Objects.requireNonNull(outputGenerator, "outputGenerator cannot be null")
        );
        this.definition       = definition;
        this.blockingExecutor = Objects.requireNonNull(blockingExecutor, "blockingExecutor cannot be null");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GAA Lifecycle Overrides
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * PERCEIVE phase — validate and contextualise the incoming event payload.
     *
     * <p>Currently returns the input unchanged. When Track 0D
     * ({@code PersistentMemoryPlane}) is wired, this will query the 5 most recent
     * episodes and prepend them to the input as context.
     */
    @Override
    @NotNull
    protected String perceive(@NotNull String input, @NotNull AgentContext context) {
        log.debug("agent={} tenant={} turn={} phase=PERCEIVE inputLen={}",
                definition.getId(), context.getTenantId(), context.getTurnId(), input.length());
        return input;
    }

    /**
     * ACT phase — logs the reasoning output; no external side effects in Phase 1.
     *
     * <p>In AEP-P2 this will dispatch operator actions (write to EventCloud,
     * invoke downstream agents, trigger workflow steps).
     */
    @Override
    @NotNull
    protected Promise<String> act(@NotNull String output, @NotNull AgentContext context) {
        log.debug("agent={} tenant={} turn={} phase=ACT outputLen={}",
                definition.getId(), context.getTenantId(), context.getTurnId(), output.length());
        return Promise.of(output);
    }

    /**
     * CAPTURE phase — stores the episode in the agent MemoryStore.
     *
     * <p>{@link com.ghatana.agent.framework.memory.MemoryStore} implementations
     * are responsible for wrapping their own blocking I/O with
     * {@code Promise.ofBlocking}; callers must not call {@code .getResult()} on the
     * returned Promise.
     */
    @Override
    @NotNull
    protected Promise<Void> capture(
            @NotNull String input,
            @NotNull String output,
            @NotNull AgentContext context) {
        Episode episode = Episode.builder()
                .agentId(definition.getId())
                .turnId(context.getTurnId())
                .timestamp(Instant.now())
                .input(input)
                .output(output)
                .context(context.getAllConfig())
                .build();
        return context.getMemoryStore().storeEpisode(episode)
                .map(stored -> {
                    log.debug("agent={} turn={} phase=CAPTURE episode={}",
                            definition.getId(), context.getTurnId(), stored.getId());
                    return null;
                });
    }

    /**
     * REFLECT phase — fire-and-forget async learning.
     *
     * <p>Currently logs reflection intent. AEP-P3 will append a
     * {@code "pattern.learning"} event to EventCloud for the learning subsystem.
     *
     * <p>This method MUST NOT block the caller — it is executed asynchronously and the
     * result is intentionally discarded.
     */
    @Override
    @NotNull
    protected Promise<Void> reflect(
            @NotNull String input,
            @NotNull String output,
            @NotNull AgentContext context) {
        // Fire-and-forget: do not await in the caller's chain.
        Promise.ofBlocking(blockingExecutor, () -> {
            log.debug("agent={} turn={} phase=REFLECT — learning event queued (EventCloud: AEP-P3)",
                    definition.getId(), context.getTurnId());
            return null;
        }).whenException(e ->
            log.warn("agent={} turn={} REFLECT error (non-fatal)",
                    definition.getId(), context.getTurnId(), e)
        );
        return Promise.complete();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Accessors
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the immutable agent definition backing this adapter.
     *
     * @return agent definition
     */
    @NotNull
    public AgentDefinition getDefinition() {
        return definition;
    }
}
