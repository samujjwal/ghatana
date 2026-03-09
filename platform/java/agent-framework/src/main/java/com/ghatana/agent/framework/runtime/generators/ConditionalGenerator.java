package com.ghatana.agent.framework.runtime.generators;

import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.api.GeneratorMetadata;
import com.ghatana.agent.framework.api.OutputGenerator;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * OutputGenerator that conditionally routes to different generators based on input.
 * 
 * <p><b>Use Cases:</b>
 * <ul>
 *   <li>Simple tasks → rule-based, complex tasks → LLM</li>
 *   <li>Known patterns → template, novel cases → LLM</li>
 *   <li>High confidence → fast path, low confidence → slow path</li>
 * </ul>
 * 
 * <p><b>Example:</b>
 * <pre>{@code
 * ConditionalGenerator<Task, Result> generator = ConditionalGenerator.<Task, Result>builder()
 *     .when(task -> task.isSimple(), ruleBasedGenerator)
 *     .when(task -> task.hasTemplate(), templateGenerator)
 *     .otherwise(llmGenerator)
 *     .build();
 * }</pre>
 * 
 * @param <TInput> Input type
 * @param <TOutput> Output type
 * 
 * @doc.type class
 * @doc.purpose Conditional routing to different generators
 * @doc.layer framework
 * @doc.pattern Strategy + Chain of Responsibility
 */
public final class ConditionalGenerator<TInput, TOutput> implements OutputGenerator<TInput, TOutput> {
    
    private final java.util.List<ConditionalBranch<TInput, TOutput>> branches;
    private final OutputGenerator<TInput, TOutput> defaultGenerator;
    private final GeneratorMetadata metadata;
    
    private ConditionalGenerator(Builder<TInput, TOutput> builder) {
        this.branches = java.util.List.copyOf(builder.branches);
        this.defaultGenerator = Objects.requireNonNull(builder.defaultGenerator, 
            "defaultGenerator (otherwise) cannot be null");
        this.metadata = GeneratorMetadata.builder()
            .name("ConditionalGenerator")
            .type("conditional")
            .description(String.format("Conditional routing with %d branches", branches.size()))
            .property("branchCount", branches.size())
            .build();
    }
    
    @Override
    @NotNull
    public Promise<TOutput> generate(@NotNull TInput input, @NotNull AgentContext context) {
        Objects.requireNonNull(input, "input cannot be null");
        Objects.requireNonNull(context, "context cannot be null");
        
        context.getLogger().debug("Evaluating {} conditional branches", branches.size());
        
        // Evaluate conditions in order
        for (int i = 0; i < branches.size(); i++) {
            ConditionalBranch<TInput, TOutput> branch = branches.get(i);
            
            if (branch.condition.test(input)) {
                context.getLogger().debug("Condition {} matched, using generator: {}", 
                    i + 1, branch.generator.getMetadata().getName());
                context.addTraceTag("conditional.branch", String.valueOf(i + 1));
                context.addTraceTag("conditional.generator", branch.generator.getMetadata().getName());
                context.recordMetric("conditional.branch." + (i + 1), 1);
                
                return branch.generator.generate(input, context);
            }
        }
        
        // No condition matched, use default
        context.getLogger().debug("No condition matched, using default generator: {}", 
            defaultGenerator.getMetadata().getName());
        context.addTraceTag("conditional.branch", "default");
        context.addTraceTag("conditional.generator", defaultGenerator.getMetadata().getName());
        context.recordMetric("conditional.branch.default", 1);
        
        return defaultGenerator.generate(input, context);
    }
    
    @Override
    @NotNull
    public Promise<Double> estimateCost(@NotNull TInput input, @NotNull AgentContext context) {
        // Find which generator would be used
        for (ConditionalBranch<TInput, TOutput> branch : branches) {
            if (branch.condition.test(input)) {
                return branch.generator.estimateCost(input, context);
            }
        }
        
        // Use default
        return defaultGenerator.estimateCost(input, context);
    }
    
    @Override
    @NotNull
    public GeneratorMetadata getMetadata() {
        return metadata;
    }
    
    /**
     * Creates a new builder.
     * @param <TInput> Input type
     * @param <TOutput> Output type
     * @return New builder
     */
    @NotNull
    public static <TInput, TOutput> Builder<TInput, TOutput> builder() {
        return new Builder<>();
    }
    
    /**
     * Builder for ConditionalGenerator.
     * @param <TInput> Input type
     * @param <TOutput> Output type
     */
    public static final class Builder<TInput, TOutput> {
        private final java.util.List<ConditionalBranch<TInput, TOutput>> branches = new java.util.ArrayList<>();
        private OutputGenerator<TInput, TOutput> defaultGenerator;
        
        private Builder() {}
        
        /**
         * Adds a conditional branch.
         * @param condition Condition to test
         * @param generator Generator to use if condition is true
         * @return This builder
         */
        @NotNull
        public Builder<TInput, TOutput> when(
                @NotNull Predicate<TInput> condition,
                @NotNull OutputGenerator<TInput, TOutput> generator) {
            Objects.requireNonNull(condition, "condition cannot be null");
            Objects.requireNonNull(generator, "generator cannot be null");
            branches.add(new ConditionalBranch<>(condition, generator));
            return this;
        }
        
        /**
         * Sets the default generator (used when no conditions match).
         * @param generator Default generator
         * @return This builder
         */
        @NotNull
        public Builder<TInput, TOutput> otherwise(@NotNull OutputGenerator<TInput, TOutput> generator) {
            this.defaultGenerator = generator;
            return this;
        }
        
        /**
         * Builds the ConditionalGenerator.
         * @return New ConditionalGenerator
         * @throws NullPointerException if otherwise() was not called
         */
        @NotNull
        public ConditionalGenerator<TInput, TOutput> build() {
            return new ConditionalGenerator<>(this);
        }
    }
    
    /**
     * Represents a conditional branch.
     */
    private static final class ConditionalBranch<TInput, TOutput> {
        private final Predicate<TInput> condition;
        private final OutputGenerator<TInput, TOutput> generator;
        
        ConditionalBranch(Predicate<TInput> condition, OutputGenerator<TInput, TOutput> generator) {
            this.condition = condition;
            this.generator = generator;
        }
    }
}
