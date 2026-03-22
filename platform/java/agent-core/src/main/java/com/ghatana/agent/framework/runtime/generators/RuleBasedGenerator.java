package com.ghatana.agent.framework.runtime.generators;

import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.api.GeneratorMetadata;
import com.ghatana.agent.framework.api.OutputGenerator;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * OutputGenerator that uses rule-based evaluation.
 * Applies deterministic rules to input and maps results to output.
 * 
 * <p>This generator is:
 * <ul>
 *   <li><b>Deterministic</b>: Same input always produces same output</li>
 *   <li><b>Fast</b>: No external API calls or LLM inference</li>
 *   <li><b>Predictable</b>: Rules are explicit and testable</li>
 *   <li><b>Cost-free</b>: No per-invocation costs</li>
 * </ul>
 * 
 * <p><b>Use Cases:</b>
 * <ul>
 *   <li>Policy validation and compliance checking</li>
 *   <li>Data validation and transformation</li>
 *   <li>Business rule evaluation</li>
 *   <li>Workflow routing decisions</li>
 * </ul>
 * 
 * <p><b>Example:</b>
 * <pre>{@code
 * // Create rule-based validator
 * RuleBasedGenerator<Requirements, ValidationResult> validator = 
 *     new RuleBasedGenerator<>(
 *         ruleEngine,
 *         req -> loadComplianceRules(req.getDomain()),
 *         result -> new ValidationResult(result.getViolations())
 *     );
 * 
 * // Use in agent
 * Promise<ValidationResult> result = validator.generate(requirements, context);
 * }</pre>
 * 
 * @param <TInput> Input type for rule evaluation
 * @param <TOutput> Output type after rule application
 * 
 * @doc.type class
 * @doc.purpose Rule-based deterministic output generation
 * @doc.layer framework
 * @doc.pattern Strategy
 */
public final class RuleBasedGenerator<TInput, TOutput> implements OutputGenerator<TInput, TOutput> {
    
    private final RuleEngine ruleEngine;
    private final Function<TInput, List<Rule>> ruleSelector;
    private final Function<RuleResult, TOutput> resultMapper;
    private final GeneratorMetadata metadata;
    
    /**
     * Creates a new RuleBasedGenerator.
     * 
     * @param ruleEngine Rule engine for evaluating rules
     * @param ruleSelector Function to select rules for given input
     * @param resultMapper Function to map rule results to output
     * @throws NullPointerException if any parameter is null
     */
    public RuleBasedGenerator(
            @NotNull RuleEngine ruleEngine,
            @NotNull Function<TInput, List<Rule>> ruleSelector,
            @NotNull Function<RuleResult, TOutput> resultMapper) {
        this.ruleEngine = Objects.requireNonNull(ruleEngine, "ruleEngine cannot be null");
        this.ruleSelector = Objects.requireNonNull(ruleSelector, "ruleSelector cannot be null");
        this.resultMapper = Objects.requireNonNull(resultMapper, "resultMapper cannot be null");
        this.metadata = GeneratorMetadata.builder()
            .name("RuleBasedGenerator")
            .type("rule")
            .description("Deterministic rule-based output generation")
            .property("ruleEngine", ruleEngine.getClass().getSimpleName())
            .build();
    }
    
    @Override
    @NotNull
    public Promise<TOutput> generate(@NotNull TInput input, @NotNull AgentContext context) {
        Objects.requireNonNull(input, "input cannot be null");
        Objects.requireNonNull(context, "context cannot be null");
        
        try {
            // 1. Select rules for this input
            List<Rule> rules = ruleSelector.apply(input);
            
            if (rules == null || rules.isEmpty()) {
                context.getLogger().warn("No rules selected for input type: {}", 
                    input.getClass().getSimpleName());
                return Promise.ofException(
                    new IllegalStateException("No rules available for input"));
            }
            
            // 2. Evaluate rules
            return ruleEngine.evaluate(rules, input, context)
                .map(result -> {
                    // 3. Map result to output
                    TOutput output = resultMapper.apply(result);
                    
                    // 4. Record metrics
                    context.recordMetric("rule.evaluation.count", rules.size());
                    context.recordMetric("rule.evaluation.success", 1);
                    
                    return output;
                })
                .whenException(ex -> {
                    context.getLogger().error("Rule evaluation failed", ex);
                    context.recordMetric("rule.evaluation.failure", 1);
                });
            
        } catch (Exception ex) {
            context.getLogger().error("Failed to select rules for input", ex);
            return Promise.ofException(ex);
        }
    }
    
    @Override
    @NotNull
    public GeneratorMetadata getMetadata() {
        return metadata;
    }
    
    @Override
    @NotNull
    public Promise<Double> estimateCost(@NotNull TInput input, @NotNull AgentContext context) {
        // Rule-based generation has no per-invocation cost
        return Promise.of(0.0);
    }
    
    /**
     * Rule engine interface for evaluating rules.
     * Implementations can use any rule engine (Drools, custom, etc.).
     */
    public interface RuleEngine {
        
        /**
         * Evaluates rules against input.
         * 
         * @param rules Rules to evaluate
         * @param input Input data
         * @param context Execution context
         * @return Promise of rule evaluation result
         */
        @NotNull
        <T> Promise<RuleResult> evaluate(
            @NotNull List<Rule> rules, 
            @NotNull T input, 
            @NotNull AgentContext context);
    }
    
    /**
     * Represents a single rule.
     */
    public interface Rule {
        
        /**
         * Gets the rule ID.
         * @return Rule ID (never null)
         */
        @NotNull
        String getId();
        
        /**
         * Gets the rule description.
         * @return Description (never null)
         */
        @NotNull
        String getDescription();
        
        /**
         * Evaluates this rule against input.
         * 
         * @param input Input to evaluate
         * @param context Execution context
         * @return true if rule passes, false if violated
         */
        <T> boolean evaluate(@NotNull T input, @NotNull AgentContext context);
    }
    
    /**
     * Result of rule evaluation.
     */
    public interface RuleResult {
        
        /**
         * Gets all rule violations.
         * @return List of violations (empty if all rules passed)
         */
        @NotNull
        List<RuleViolation> getViolations();
        
        /**
         * Checks if all rules passed.
         * @return true if no violations
         */
        default boolean isSuccess() {
            return getViolations().isEmpty();
        }
        
        /**
         * Gets metadata about rule execution.
         * @return Execution metadata
         */
        @NotNull
        RuleExecutionMetadata getMetadata();
    }
    
    /**
     * Represents a rule violation.
     */
    public interface RuleViolation {
        
        /**
         * Gets the violated rule ID.
         * @return Rule ID
         */
        @NotNull
        String getRuleId();
        
        /**
         * Gets the violation message.
         * @return Message describing the violation
         */
        @NotNull
        String getMessage();
        
        /**
         * Gets the severity level.
         * @return Severity (ERROR, WARNING, INFO)
         */
        @NotNull
        Severity getSeverity();
    }
    
    /**
     * Metadata about rule execution.
     */
    public interface RuleExecutionMetadata {
        
        /**
         * Gets the number of rules evaluated.
         * @return Rule count
         */
        int getRulesEvaluated();
        
        /**
         * Gets the execution duration in milliseconds.
         * @return Duration
         */
        long getDurationMillis();
    }
    
    /**
     * Violation severity levels.
     */
    public enum Severity {
        /** Critical error that blocks execution */
        ERROR,
        /** Warning that should be addressed */
        WARNING,
        /** Informational notice */
        INFO
    }
}
