package com.ghatana.agent.framework.api;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

/**
 * Generic interface for generating agent output from input.
 * Implementations can use ANY computation strategy:
 * <ul>
 *   <li>Rule engines (Drools, custom rules)</li>
 *   <li>LLM inference (GPT, Claude, etc.)</li>
 *   <li>Template rendering (FreeMarker, Velocity)</li>
 *   <li>External service calls (HTTP, gRPC)</li>
 *   <li>Script execution (Python, JavaScript)</li>
 *   <li>Statistical models</li>
 *   <li>Graph algorithms</li>
 *   <li>Heuristics</li>
 *   <li>etc.</li>
 * </ul>
 * 
 * <p>This abstraction enables:
 * <ul>
 *   <li><b>Flexibility</b>: Support any output generation strategy</li>
 *   <li><b>Composability</b>: Chain multiple generators (pipelines)</li>
 *   <li><b>Testability</b>: Easy to mock and test independently</li>
 *   <li><b>Product-agnostic</b>: Works across all products</li>
 * </ul>
 * 
 * <p><b>Example Usage:</b>
 * <pre>{@code
 * // Rule-based generator
 * OutputGenerator<ValidationTask, ValidationResult> validator = 
 *     new RuleBasedGenerator<>(ruleEngine, rules, mapper);
 * 
 * // LLM-based generator
 * OutputGenerator<CodeGenTask, Code> codeGen = 
 *     new LLMGenerator<>(llmGateway, promptBuilder, parser);
 * 
 * // Pipeline combining multiple generators
 * OutputGenerator<Task, Result> pipeline = new PipelineGenerator<>(
 *     List.of(llmGen, templateGen, validationGen)
 * );
 * 
 * // Execute
 * Promise<Result> result = generator.generate(input, context);
 * }</pre>
 * 
 * @param <TInput> The input type this generator accepts
 * @param <TOutput> The output type this generator produces
 * 
 * @doc.type interface
 * @doc.purpose Generic output generation abstraction for agents
 * @doc.layer framework
 * @doc.pattern Strategy
 */
public interface OutputGenerator<TInput, TOutput> {
    
    /**
     * Generates output from input using this generator's strategy.
     * 
     * <p>Implementations MUST:
     * <ul>
     *   <li>Be asynchronous (return Promise)</li>
     *   <li>Use {@code Promise.ofBlocking} for blocking operations</li>
     *   <li>Handle errors gracefully (return Promise.ofException)</li>
     *   <li>Be stateless (no side effects between calls)</li>
     *   <li>Respect context configuration (timeouts, limits)</li>
     * </ul>
     * 
     * @param input The input data to process
     * @param context Execution context with memory, config, metrics, etc.
     * @return Promise of generated output
     * @throws NullPointerException if input or context is null
     * 
     * @see AgentContext
     */
    @NotNull
    Promise<TOutput> generate(@NotNull TInput input, @NotNull AgentContext context);
    
    /**
     * Returns metadata about this generator.
     * Used for observability, debugging, and configuration validation.
     * 
     * <p>Default implementation provides basic metadata from class name.
     * Implementations should override to provide richer metadata.
     * 
     * @return Generator metadata (never null)
     */
    @NotNull
    default GeneratorMetadata getMetadata() {
        return GeneratorMetadata.builder()
            .name(this.getClass().getSimpleName())
            .type("unknown")
            .description("No description provided")
            .build();
    }
    
    /**
     * Validates whether this generator can handle the given input type.
     * Used during agent configuration validation.
     * 
     * <p>Default implementation returns true (accepts all inputs).
     * Implementations should override for stricter validation.
     * 
     * @param inputType The input type to validate
     * @return true if this generator can handle the input type
     */
    default boolean canHandle(@NotNull Class<?> inputType) {
        return true;
    }
    
    /**
     * Estimates the cost of generating output for the given input.
     * Used for cost control and budget tracking.
     * 
     * <p>Default implementation returns zero (no cost).
     * LLM generators should override to provide token-based cost estimates.
     * 
     * @param input The input to estimate cost for
     * @param context Execution context
     * @return Promise of estimated cost in USD
     */
    @NotNull
    default Promise<Double> estimateCost(@NotNull TInput input, @NotNull AgentContext context) {
        return Promise.of(0.0);
    }
}
