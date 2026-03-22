package com.ghatana.agent.framework.runtime.generators;

import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.api.GeneratorMetadata;
import com.ghatana.agent.framework.api.OutputGenerator;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * OutputGenerator that uses template rendering.
 * Supports any template engine (FreeMarker, Velocity, Mustache, etc.).
 * 
 * <p><b>Use Cases:</b>
 * <ul>
 *   <li>Code generation from templates</li>
 *   <li>Document generation (API docs, reports)</li>
 *   <li>Configuration file generation</li>
 *   <li>Email/message formatting</li>
 * </ul>
 * 
 * <p><b>Example:</b>
 * <pre>{@code
 * TemplateGenerator<ServiceSpec, String> codeGen = new TemplateGenerator<>(
 *     templateEngine,
 *     "service.java.ftl", // FreeMarker template
 *     spec -> Map.of(
 *         "serviceName", spec.getName(),
 *         "methods", spec.getMethods()
 *     )
 * );
 * }</pre>
 * 
 * @param <TInput> Input type
 * @param <TOutput> Output type (typically String)
 * 
 * @doc.type class
 * @doc.purpose Template-based output generation
 * @doc.layer framework
 * @doc.pattern Strategy
 */
public final class TemplateGenerator<TInput, TOutput> implements OutputGenerator<TInput, TOutput> {
    
    private final TemplateEngine templateEngine;
    private final String templateName;
    private final java.util.function.Function<TInput, java.util.Map<String, Object>> dataMapper;
    private final java.util.function.Function<String, TOutput> resultConverter;
    private final GeneratorMetadata metadata;
    
    /**
     * Creates a new TemplateGenerator that produces String output.
     * 
     * @param templateEngine Template engine
     * @param templateName Template name/path
     * @param dataMapper Function to map input to template data model
     */
    @SuppressWarnings("unchecked")
    public TemplateGenerator(
            @NotNull TemplateEngine templateEngine,
            @NotNull String templateName,
            @NotNull java.util.function.Function<TInput, java.util.Map<String, Object>> dataMapper) {
        this(templateEngine, templateName, dataMapper, (java.util.function.Function<String, TOutput>) (java.util.function.Function<?, ?>) (s -> s));
    }
    
    /**
     * Creates a new TemplateGenerator with custom result converter.
     * 
     * @param templateEngine Template engine
     * @param templateName Template name/path
     * @param dataMapper Function to map input to template data model
     * @param resultConverter Function to convert rendered string to output type
     */
    public TemplateGenerator(
            @NotNull TemplateEngine templateEngine,
            @NotNull String templateName,
            @NotNull java.util.function.Function<TInput, java.util.Map<String, Object>> dataMapper,
            @NotNull java.util.function.Function<String, TOutput> resultConverter) {
        this.templateEngine = Objects.requireNonNull(templateEngine, "templateEngine cannot be null");
        this.templateName = Objects.requireNonNull(templateName, "templateName cannot be null");
        this.dataMapper = Objects.requireNonNull(dataMapper, "dataMapper cannot be null");
        this.resultConverter = Objects.requireNonNull(resultConverter, "resultConverter cannot be null");
        this.metadata = GeneratorMetadata.builder()
            .name("TemplateGenerator")
            .type("template")
            .description("Template-based output generation")
            .property("templateEngine", templateEngine.getClass().getSimpleName())
            .property("templateName", templateName)
            .build();
    }
    
    @Override
    @NotNull
    public Promise<TOutput> generate(@NotNull TInput input, @NotNull AgentContext context) {
        Objects.requireNonNull(input, "input cannot be null");
        Objects.requireNonNull(context, "context cannot be null");
        
        try {
            // 1. Map input to template data model
            java.util.Map<String, Object> dataModel = dataMapper.apply(input);
            
            context.getLogger().debug("Rendering template: {}", templateName);
            context.addTraceTag("template.name", templateName);
            
            // 2. Render template
            long startTime = System.currentTimeMillis();
            return templateEngine.render(templateName, dataModel, context)
                .map(rendered -> {
                    // 3. Convert result
                    TOutput output = resultConverter.apply(rendered);
                    
                    // 4. Record metrics
                    long duration = System.currentTimeMillis() - startTime;
                    context.recordMetric("template.render.duration", duration);
                    context.recordMetric("template.output.length", rendered.length());
                    
                    context.getLogger().debug("Template rendered: {} chars in {}ms", 
                        rendered.length(), duration);
                    
                    return output;
                })
                .whenException(ex -> {
                    context.getLogger().error("Template rendering failed", ex);
                    context.recordMetric("template.render.failure", 1);
                });
            
        } catch (Exception ex) {
            context.getLogger().error("Failed to map input to template data model", ex);
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
        // Template rendering has no per-invocation cost
        return Promise.of(0.0);
    }
    
    /**
     * Template engine interface.
     * Implementations can use any template engine (FreeMarker, Velocity, etc.).
     */
    public interface TemplateEngine {
        
        /**
         * Renders a template with the given data model.
         * 
         * @param templateName Template name/path
         * @param dataModel Data model for template
         * @param context Execution context
         * @return Promise of rendered output
         */
        @NotNull
        Promise<String> render(
            @NotNull String templateName, 
            @NotNull java.util.Map<String, Object> dataModel,
            @NotNull AgentContext context);
    }
}
