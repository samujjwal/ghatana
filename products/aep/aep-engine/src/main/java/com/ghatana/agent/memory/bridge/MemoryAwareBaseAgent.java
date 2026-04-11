package com.ghatana.agent.memory.bridge;

import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.api.OutputGenerator;
import com.ghatana.agent.framework.runtime.BaseAgent;
import com.ghatana.agent.memory.model.episode.EnhancedEpisode;
import com.ghatana.agent.memory.retrieval.InjectionConfig;
import com.ghatana.agent.memory.retrieval.ContextInjector;
import com.ghatana.agent.memory.retrieval.StructuredContextInjector;
import com.ghatana.agent.memory.store.MemoryPlane;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;

/**
 * Memory-aware extension of {@link BaseAgent} that enhances the standard
 * PERCEIVE → REASON → ACT → CAPTURE → REFLECT lifecycle with:
 *
 * <ul>
 *   <li><b>PERCEIVE</b>: Retrieves relevant memory and injects context</li>
 *   <li><b>CAPTURE</b>: Stores {@link EnhancedEpisode} with tool executions and artifacts</li>
 *   <li><b>REFLECT</b>: Triggers consolidation check after accumulating episodes</li>
 * </ul>
 *
 * <p>This class lives in agent-memory to avoid circular dependencies.
 * Products extend this instead of BaseAgent when they want full memory integration.
 *
 * @param <TInput>  Agent input type
 * @param <TOutput> Agent output type
 *
 * @doc.type class
 * @doc.purpose Memory-enhanced agent base class
 * @doc.layer agent-memory
 */
public abstract class MemoryAwareBaseAgent<TInput, TOutput> extends BaseAgent<TInput, TOutput> {

    private static final int DEFAULT_RETRIEVAL_K = 10;

    private final ContextInjector contextInjector;

    /**
     * Creates a memory-aware agent.
     *
     * @param agentId         Unique agent identifier
     * @param outputGenerator Output generator for reasoning
     */
    protected MemoryAwareBaseAgent(
            @NotNull String agentId,
            @NotNull OutputGenerator<TInput, TOutput> outputGenerator) {
        super(agentId, outputGenerator);
        this.contextInjector = new StructuredContextInjector();
    }

    /**
     * Enhanced PERCEIVE: retrieves relevant memory items and injects them
     * into the context metadata as structured context.
     *
     * <p>Override {@link #buildRetrievalQuery(TInput, AgentContext)} to customize
     * what gets retrieved.
     */
    @Override
    @NotNull
    protected TInput perceive(@NotNull TInput input, @NotNull AgentContext context) {
        MemoryAwareContext mac = MemoryAwareContext.from(context);
        if (mac == null) {
            // No memory plane available — fall through to standard behavior
            return input;
        }

        try {
            MemoryPlane plane = mac.getMemoryPlane();
            String query = buildRetrievalQuery(input, context);

            // Fire retrieval asynchronously — context is populated by the time reason() reads it
            plane.searchSemantic(query, null, getRetrievalK(), null, null)
                    .whenResult(retrieved -> {
                        if (!retrieved.isEmpty()) {
                            InjectionConfig injectionConfig = InjectionConfig.builder()
                                    .maxTokens(getMaxInjectionTokens())
                                    .groupByTier(true)
                                    .format(InjectionConfig.Format.MARKDOWN)
                                    .build();

                            String injectedContext = contextInjector.formatForInjection(retrieved, injectionConfig);
                            context.setMetadata("memory.context", injectedContext);
                            context.setMetadata("memory.retrieved_count", retrieved.size());
                            context.getLogger().debug("Injected {} memory items into context", retrieved.size());
                        }
                    })
                    .whenException(e -> context.getLogger().warn("Memory retrieval failed (non-fatal)", e));
        } catch (Exception e) {
            context.getLogger().warn("Memory retrieval setup failed (non-fatal)", e);
        }

        return input;
    }

    /**
     * Enhanced CAPTURE: stores an {@link EnhancedEpisode} with richer metadata.
     */
    @Override
    @NotNull
    protected Promise<Void> capture(
            @NotNull TInput input,
            @NotNull TOutput output,
            @NotNull AgentContext context) {

        MemoryAwareContext mac = MemoryAwareContext.from(context);
        if (mac == null) {
            return super.capture(input, output, context);
        }

        EnhancedEpisode episode = EnhancedEpisode.builder()
                .agentId(getAgentId())
                .turnId(context.getTurnId())
                .input(input.toString())
                .output(output.toString())
                .createdAt(Instant.now())
                .build();

        return mac.getMemoryPlane().storeEpisode(episode)
                .map(stored -> {
                    context.getLogger().debug("Enhanced episode captured: {}", stored.getId());
                    return null;
                });
    }

    /**
     * Enhanced REFLECT: checks if consolidation should be triggered.
     * Products can override to add custom learning logic.
     */
    @Override
    @NotNull
    protected Promise<Void> reflect(
            @NotNull TInput input,
            @NotNull TOutput output,
            @NotNull AgentContext context) {

        // Default: check episode count and suggest consolidation
        MemoryAwareContext mac = MemoryAwareContext.from(context);
        if (mac != null) {
            mac.getMemoryPlane().getStats()
                    .whenResult(stats -> {
                        if (stats.getEpisodeCount() > 0 && stats.getEpisodeCount() % 50 == 0) {
                            context.getLogger().info("Consolidation recommended: {} episodes accumulated",
                                    stats.getEpisodeCount());
                            context.setMetadata("memory.consolidation_recommended", true);
                        }
                    })
                    .whenException(e -> context.getLogger().debug("Stats check failed", e));
        }

        return Promise.complete();
    }

    // =========================================================================
    // Extension points
    // =========================================================================

    /**
     * Builds the semantic retrieval query from the input.
     * Default: toString() of input.
     *
     * @param input   The agent input
     * @param context The execution context
     * @return Natural language query for memory retrieval
     */
    @NotNull
    protected String buildRetrievalQuery(@NotNull TInput input, @NotNull AgentContext context) {
        return input.toString();
    }

    /**
     * Returns the number of memory items to retrieve.
     * Override to customize.
     */
    protected int getRetrievalK() {
        return DEFAULT_RETRIEVAL_K;
    }

    /**
     * Returns the maximum tokens for context injection.
     * Override to customize.
     */
    protected int getMaxInjectionTokens() {
        return 2000;
    }
}
