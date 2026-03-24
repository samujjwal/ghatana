package com.ghatana.yappc.plugin;

import io.activej.promise.Promise;

/**
 * Specialized plugin for SDLC agent operations.
 * 
 * <p>Agent plugins implement workflow steps in the SDLC process,
 * such as requirements analysis, design, implementation, testing, etc.
 * 
 * <p>Example implementation:
 * <pre>{@code
 * public class IntakeAgentPlugin implements AgentPlugin {
 *     
 *     @Override
 *     public <I, O> Promise<StepResult<O>> execute(I input, StepContext ctx) {
 *         return Promise.of(input)
 *             .map(in -> processIntake((IntakeInput) in))
 *             .map(out -> StepResult.<O>builder()
 *                 .stepName(getMetadata().getId())
 *                 .output((O) out)
 *                 .build());
 *     }
 *     
 *     @Override
 *     public String getPhase() {
 *         return "architecture";
 *     }
 *     
 *     @Override
 *     public PluginMetadata getMetadata() {
 *         return PluginMetadata.builder()
 *             .id("architecture.intake")
 *             .name("Intake Specialist")
 *             .version("1.0.0")
 *             .category("architecture")
 *             .build();
 *     }
 * }
 * }</pre>
 * 
 * @author YAPPC Team
 * @version 1.0.0
 * @since 1.0.0
 
 * @doc.type interface
 * @doc.purpose Defines the contract for agent plugin
 * @doc.layer core
 * @doc.pattern Plugin
*/
public interface AgentPlugin extends YAPPCPlugin {
    
    /**
     * Executes the agent workflow step.
     * 
     * @param <I> the input type
     * @param <O> the output type
     * @param input the step input
     * @param ctx the step context
     * @return a Promise containing the step result
     */
    <I, O> Promise<StepResult<O>> execute(I input, StepContext ctx);
    
    /**
     * Returns the SDLC phase this agent belongs to.
     * 
     * @return the phase (e.g., "architecture", "implementation", "testing", "operations")
     */
    String getSdlcPhase();
    
    /**
     * Returns the step name.
     * 
     * @return the step name (e.g., "architecture.intake")
     */
    default String getStepName() {
        return getMetadata().getId();
    }
}
