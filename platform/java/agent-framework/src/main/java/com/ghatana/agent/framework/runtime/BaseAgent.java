package com.ghatana.agent.framework.runtime;

import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.api.OutputGenerator;
import com.ghatana.agent.framework.memory.*;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;

/**
 * Base class for all GAA (Generic Adaptive Agent) agents.
 * Implements the standard agent lifecycle: PERCEIVE → REASON → ACT → CAPTURE → REFLECT.
 * 
 * <p><b>Lifecycle Phases:</b>
 * <ol>
 *   <li><b>PERCEIVE</b>: Receive and understand input</li>
 *   <li><b>REASON</b>: Use OutputGenerator to produce output</li>
 *   <li><b>ACT</b>: Execute actions based on reasoning</li>
 *   <li><b>CAPTURE</b>: Store episode in memory (event sourced)</li>
 *   <li><b>REFLECT</b>: Async learning and pattern extraction</li>
 * </ol>
 * 
 * <p><b>Usage:</b>
 * <pre>{@code
 * public class MyAgent extends BaseAgent<MyInput, MyOutput> {
 *     public MyAgent(OutputGenerator<MyInput, MyOutput> generator) {
 *         super("MyAgent", generator);
 *     }
 *     
 *     @Override
 *     protected MyInput perceive(MyInput input, AgentContext context) {
 *         // Validate, normalize, enrich input
 *         return input;
 *     }
 * }
 * }</pre>
 * 
 * @param <TInput> Agent input type
 * @param <TOutput> Agent output type
 * 
 * @doc.type class
 * @doc.purpose Base agent with GAA lifecycle
 * @doc.layer framework
 * @doc.pattern Template Method
 * @doc.gaa.lifecycle perceive|reason|act|capture|reflect
 */
public abstract class BaseAgent<TInput, TOutput> {
    
    private final String agentId;
    private final OutputGenerator<TInput, TOutput> outputGenerator;
    
    /**
     * Creates a new BaseAgent.
     * 
     * @param agentId Unique agent identifier
     * @param outputGenerator Output generator for reasoning
     */
    protected BaseAgent(
            @NotNull String agentId,
            @NotNull OutputGenerator<TInput, TOutput> outputGenerator) {
        this.agentId = Objects.requireNonNull(agentId, "agentId cannot be null");
        this.outputGenerator = Objects.requireNonNull(outputGenerator, 
            "outputGenerator cannot be null");
    }
    
    /**
     * Executes a complete agent turn.
     * Delegates to {@link AgentTurnPipeline} for lifecycle orchestration.
     * Follows GAA lifecycle: PERCEIVE → REASON → ACT → CAPTURE → REFLECT.
     * 
     * @param input Input data
     * @param context Execution context
     * @return Promise of output
     */
    @NotNull
    public final Promise<TOutput> executeTurn(@NotNull TInput input, @NotNull AgentContext context) {
        Objects.requireNonNull(input, "input cannot be null");
        Objects.requireNonNull(context, "context cannot be null");
        
        AgentTurnPipeline<TInput, TOutput> pipeline = AgentTurnPipeline.of(this);
        return pipeline.execute(input, context);
    }
    
    /**
     * Phase 1: PERCEIVE - Processes and validates input.
     * Default implementation returns input unchanged.
     * Override to add validation, normalization, enrichment.
     * 
     * @param input Raw input
     * @param context Execution context
     * @return Perceived input
     */
    @NotNull
    protected TInput perceive(@NotNull TInput input, @NotNull AgentContext context) {
        return input;
    }
    
    /**
     * Phase 3: ACT - Executes actions based on reasoning output.
     * Default implementation returns output unchanged.
     * Override to perform side effects (API calls, database updates, etc.).
     * 
     * @param output Output from reasoning
     * @param context Execution context
     * @return Promise of final output
     */
    @NotNull
    protected Promise<TOutput> act(@NotNull TOutput output, @NotNull AgentContext context) {
        return Promise.of(output);
    }
    
    /**
     * Phase 4: CAPTURE - Stores episode in memory (event sourced).
     * Override to customize what gets captured or add semantic memory.
     * 
     * @param input Turn input
     * @param output Turn output
     * @param context Execution context
     * @return Promise of completion
     */
    @NotNull
    protected Promise<Void> capture(
            @NotNull TInput input, 
            @NotNull TOutput output, 
            @NotNull AgentContext context) {
        
        // Store episode in memory
        Episode episode = Episode.builder()
            .agentId(agentId)
            .turnId(context.getTurnId())
            .timestamp(Instant.now())
            .input(input.toString())
            .output(output.toString())
            .context(context.getAllConfig())
            .build();
        
        return context.getMemoryStore().storeEpisode(episode)
            .map(stored -> {
                context.getLogger().debug("Episode captured: {}", stored.getId());
                return null;
            });
    }
    
    /**
     * Phase 5: REFLECT - Async learning and pattern extraction.
     * Runs in background, does not block user response.
     * Override to implement custom learning logic.
     * 
     * @param input Turn input
     * @param output Turn output
     * @param context Execution context
     * @return Promise of completion
     */
    @NotNull
    protected Promise<Void> reflect(
            @NotNull TInput input, 
            @NotNull TOutput output, 
            @NotNull AgentContext context) {
        
        // Default: no reflection
        // Products can override to implement learning
        return Promise.complete();
    }
    
    /**
     * Gets the agent ID.
     * @return Agent ID
     */
    @NotNull
    public String getAgentId() {
        return agentId;
    }
    
    /**
     * Gets the output generator used by this agent.
     * @return Output generator
     */
    @NotNull
    public OutputGenerator<TInput, TOutput> getOutputGenerator() {
        return outputGenerator;
    }
}
