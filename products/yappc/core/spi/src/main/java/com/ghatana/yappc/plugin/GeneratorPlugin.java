package com.ghatana.yappc.plugin;

import io.activej.promise.Promise;
import java.util.Set;

/**
 * Specialized plugin for code generation operations.
 * 
 * <p>Generator plugins produce code, documentation, or other artifacts
 * from YAPPC canvases, templates, or specifications.
 * 
 * <p>Example implementation:
 * <pre>{@code
 * public class RestApiGeneratorPlugin implements GeneratorPlugin {
 *     
 *     @Override
 *     public Promise<GenerationResult> generate(GenerationContext ctx) {
 *         return Promise.of(ctx.getSource())
 *             .map(source -> generateRestApi(source))
 *             .map(artifacts -> GenerationResult.builder()
 *                 .generatorId(getMetadata().getId())
 *                 .artifacts(artifacts)
 *                 .build());
 *     }
 *     
 *     @Override
 *     public Set<String> getSupportedLanguages() {
 *         return Set.of("java", "typescript", "python");
 *     }
 *     
 *     @Override
 *     public PluginMetadata getMetadata() {
 *         return PluginMetadata.builder()
 *             .id("rest-api-generator")
 *             .name("REST API Generator")
 *             .version("1.0.0")
 *             .category("backend")
 *             .build();
 *     }
 * }
 * }</pre>
 * 
 * @author YAPPC Team
 * @version 1.0.0
 * @since 1.0.0
 
 * @doc.type interface
 * @doc.purpose Defines the contract for generator plugin
 * @doc.layer core
 * @doc.pattern Plugin
*/
public interface GeneratorPlugin extends YAPPCPlugin {
    
    /**
     * Generates artifacts from the source in the given context.
     * 
     * @param ctx the generation context
     * @return a Promise containing the generation result
     */
    Promise<GenerationResult> generate(GenerationContext ctx);
    
    /**
     * Returns the generator ID.
     * 
     * @return the generator ID
     */
    default String getGeneratorId() {
        return getMetadata().getId();
    }
    
    /**
     * Returns the set of supported programming languages.
     * 
     * @return the supported languages
     */
    Set<String> getSupportedLanguages();
}
