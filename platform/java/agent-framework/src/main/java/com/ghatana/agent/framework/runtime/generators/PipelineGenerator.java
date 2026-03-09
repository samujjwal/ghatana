package com.ghatana.agent.framework.runtime.generators;

import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.api.GeneratorMetadata;
import com.ghatana.agent.framework.api.OutputGenerator;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * OutputGenerator that chains multiple generators into a pipeline.
 * Output of one generator becomes input to the next.
 * 
 * <p><b>Use Cases:</b>
 * <ul>
 *   <li>Multi-stage processing (validate → transform → generate)</li>
 *   <li>LLM + template (LLM generates data, template formats it)</li>
 *   <li>Service call + validation (fetch data, validate, format)</li>
 * </ul>
 * 
 * <p><b>Example:</b>
 * <pre>{@code
 * // Pipeline: LLM generates spec → Template renders code → Validator checks
 * PipelineGenerator<Task, ValidatedCode> pipeline = new PipelineGenerator<>(
 *     List.of(
 *         llmGenerator,      // Task → Spec
 *         templateGenerator, // Spec → Code
 *         validatorGenerator // Code → ValidatedCode
 *     )
 * );
 * }</pre>
 * 
 * @param <TInput> Initial input type
 * @param <TOutput> Final output type
 * 
 * @doc.type class
 * @doc.purpose Pipeline composition of multiple generators
 * @doc.layer framework
 * @doc.pattern Chain of Responsibility
 */
public final class PipelineGenerator<TInput, TOutput> implements OutputGenerator<TInput, TOutput> {
    
    private final List<OutputGenerator<?, ?>> stages;
    private final GeneratorMetadata metadata;
    
    /**
     * Creates a new PipelineGenerator.
     * 
     * @param stages List of generators to execute in sequence
     * @throws NullPointerException if stages is null or empty
     * @throws IllegalArgumentException if stages list is empty
     */
    public PipelineGenerator(@NotNull List<? extends OutputGenerator<?, ?>> stages) {
        Objects.requireNonNull(stages, "stages cannot be null");
        if (stages.isEmpty()) {
            throw new IllegalArgumentException("Pipeline must have at least one stage");
        }
        
        this.stages = new ArrayList<>(stages);
        this.metadata = GeneratorMetadata.builder()
            .name("PipelineGenerator")
            .type("pipeline")
            .description(String.format("Pipeline of %d generators", stages.size()))
            .property("stageCount", stages.size())
            .property("stages", stages.stream()
                .map(g -> g.getMetadata().getName())
                .toList())
            .build();
    }
    
    @Override
    @NotNull
    @SuppressWarnings("unchecked")
    public Promise<TOutput> generate(@NotNull TInput input, @NotNull AgentContext context) {
        Objects.requireNonNull(input, "input cannot be null");
        Objects.requireNonNull(context, "context cannot be null");
        
        context.getLogger().debug("Starting pipeline with {} stages", stages.size());
        context.addTraceTag("pipeline.stages", String.valueOf(stages.size()));
        
        long startTime = System.currentTimeMillis();
        
        // Execute stages sequentially
        Promise<?> result = Promise.of(input);
        
        for (int i = 0; i < stages.size(); i++) {
            final int stageIndex = i;
            @SuppressWarnings("rawtypes")
            final OutputGenerator stage = stages.get(i);
            
            result = result.then(stageInput -> {
                context.getLogger().debug("Executing pipeline stage {}: {}", 
                    stageIndex + 1, stage.getMetadata().getName());
                
                long stageStart = System.currentTimeMillis();
                @SuppressWarnings("unchecked")
                Promise<?> stageResult = stage.generate(stageInput, context);
                return stageResult
                    .whenComplete((output, error) -> {
                        long stageDuration = System.currentTimeMillis() - stageStart;
                        context.recordMetric(
                            String.format("pipeline.stage.%d.duration", stageIndex + 1), 
                            stageDuration);
                        
                        if (error != null) {
                            context.getLogger().error(
                                "Pipeline stage {} failed: {}", 
                                stageIndex + 1, 
                                stage.getMetadata().getName(), 
                                error);
                            context.recordMetric(
                                String.format("pipeline.stage.%d.failure", stageIndex + 1), 
                                1);
                        }
                    });
            });
        }
        
        return ((Promise<TOutput>) result)
            .whenComplete((output, error) -> {
                long totalDuration = System.currentTimeMillis() - startTime;
                context.recordMetric("pipeline.total.duration", totalDuration);
                
                if (error == null) {
                    context.getLogger().debug("Pipeline completed successfully in {}ms", totalDuration);
                    context.recordMetric("pipeline.success", 1);
                } else {
                    context.getLogger().error("Pipeline failed after {}ms", totalDuration, error);
                    context.recordMetric("pipeline.failure", 1);
                }
            });
    }
    
    @Override
    @NotNull
    public Promise<Double> estimateCost(@NotNull TInput input, @NotNull AgentContext context) {
        // Sum costs of all stages
        Promise<Double> totalCost = Promise.of(0.0);
        
        for (OutputGenerator<?, ?> stage : stages) {
            totalCost = totalCost.then(accumulated -> {
                // Note: This is a simplified estimation. In reality, intermediate
                // outputs affect subsequent stage costs, but we don't have them yet.
                @SuppressWarnings("unchecked")
                OutputGenerator<Object, ?> typedStage = (OutputGenerator<Object, ?>) stage;
                return typedStage.estimateCost(input, context)
                    .map(stageCost -> accumulated + stageCost);
            });
        }
        
        return totalCost;
    }
    
    @Override
    @NotNull
    public GeneratorMetadata getMetadata() {
        return metadata;
    }
    
    /**
     * Gets the number of stages in this pipeline.
     * @return Stage count
     */
    public int getStageCount() {
        return stages.size();
    }
    
    /**
     * Gets metadata for a specific stage.
     * @param index Stage index (0-based)
     * @return Stage metadata
     * @throws IndexOutOfBoundsException if index is invalid
     */
    @NotNull
    public GeneratorMetadata getStageMetadata(int index) {
        return stages.get(index).getMetadata();
    }
}
