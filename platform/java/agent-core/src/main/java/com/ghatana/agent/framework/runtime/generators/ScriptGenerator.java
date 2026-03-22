package com.ghatana.agent.framework.runtime.generators;

import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.api.GeneratorMetadata;
import com.ghatana.agent.framework.api.OutputGenerator;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * OutputGenerator that executes scripts (Python, JavaScript, etc.).
 * 
 * <p><b>Use Cases:</b>
 * <ul>
 *   <li>Data transformation with Python/Pandas</li>
 *   <li>Statistical analysis with R</li>
 *   <li>Custom algorithms not in Java</li>
 *   <li>Integration with legacy scripts</li>
 * </ul>
 * 
 * <p><b>Security Note:</b> Script execution should be sandboxed and validated.
 * Never execute untrusted scripts.
 * 
 * <p><b>Example:</b>
 * <pre>{@code
 * ScriptGenerator<DataSet, Analysis> analyzer = new ScriptGenerator<>(
 *     scriptEngine,
 *     "python",
 *     "analyze.py",
 *     data -> Map.of("data", data.toJson()),
 *     Analysis::fromJson
 * );
 * }</pre>
 * 
 * @param <TInput> Input type
 * @param <TOutput> Output type
 * 
 * @doc.type class
 * @doc.purpose Script execution output generation
 * @doc.layer framework
 * @doc.pattern Strategy + Interpreter
 */
public final class ScriptGenerator<TInput, TOutput> implements OutputGenerator<TInput, TOutput> {
    
    private final ScriptEngine scriptEngine;
    private final String language;
    private final String scriptPath;
    private final Function<TInput, Map<String, Object>> inputMapper;
    private final Function<String, TOutput> outputParser;
    private final GeneratorMetadata metadata;
    
    /**
     * Creates a new ScriptGenerator.
     * 
     * @param scriptEngine Script execution engine
     * @param language Script language (python, javascript, etc.)
     * @param scriptPath Path to script file
     * @param inputMapper Function to map input to script parameters
     * @param outputParser Function to parse script output to result
     */
    public ScriptGenerator(
            @NotNull ScriptEngine scriptEngine,
            @NotNull String language,
            @NotNull String scriptPath,
            @NotNull Function<TInput, Map<String, Object>> inputMapper,
            @NotNull Function<String, TOutput> outputParser) {
        this.scriptEngine = Objects.requireNonNull(scriptEngine, "scriptEngine cannot be null");
        this.language = Objects.requireNonNull(language, "language cannot be null");
        this.scriptPath = Objects.requireNonNull(scriptPath, "scriptPath cannot be null");
        this.inputMapper = Objects.requireNonNull(inputMapper, "inputMapper cannot be null");
        this.outputParser = Objects.requireNonNull(outputParser, "outputParser cannot be null");
        this.metadata = GeneratorMetadata.builder()
            .name("ScriptGenerator")
            .type("script")
            .description(String.format("%s script execution", language))
            .property("language", language)
            .property("scriptPath", scriptPath)
            .build();
    }
    
    @Override
    @NotNull
    public Promise<TOutput> generate(@NotNull TInput input, @NotNull AgentContext context) {
        Objects.requireNonNull(input, "input cannot be null");
        Objects.requireNonNull(context, "context cannot be null");
        
        try {
            // 1. Map input to script parameters
            Map<String, Object> params = inputMapper.apply(input);
            
            context.getLogger().debug("Executing {} script: {}", language, scriptPath);
            context.addTraceTag("script.language", language);
            context.addTraceTag("script.path", scriptPath);
            
            // 2. Execute script
            long startTime = System.currentTimeMillis();
            return scriptEngine.execute(scriptPath, params, context)
                .map(scriptOutput -> {
                    // 3. Parse output
                    TOutput output = outputParser.apply(scriptOutput);
                    
                    // 4. Record metrics
                    long duration = System.currentTimeMillis() - startTime;
                    context.recordMetric("script.execution.duration", duration);
                    context.recordMetric("script.execution.success", 1);
                    
                    context.getLogger().debug("Script executed in {}ms", duration);
                    
                    return output;
                })
                .whenException(ex -> {
                    context.getLogger().error("Script execution failed", ex);
                    context.recordMetric("script.execution.failure", 1);
                });
            
        } catch (Exception ex) {
            context.getLogger().error("Failed to map input to script parameters", ex);
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
        // Script execution typically has no per-invocation cost
        // (unless calling external APIs within script)
        return Promise.of(0.0);
    }
    
    /**
     * Script execution engine interface.
     */
    public interface ScriptEngine {
        
        /**
         * Executes a script with given parameters.
         * 
         * @param scriptPath Path to script file
         * @param parameters Script parameters
         * @param context Execution context
         * @return Promise of script output as string
         */
        @NotNull
        Promise<String> execute(
            @NotNull String scriptPath,
            @NotNull Map<String, Object> parameters,
            @NotNull AgentContext context);
    }
}
