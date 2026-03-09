package com.ghatana.stt.core.api;

import com.ghatana.stt.core.config.EngineConfig;
import com.ghatana.stt.core.pipeline.DefaultAdaptiveSTTEngine;

/**
 * Factory for creating AdaptiveSTTEngine instances.
 * 
 * <p>This is the primary entry point for obtaining an STT engine.
 * 
 * <p>Usage:
 * <pre>{@code
 * // Create with default configuration
 * AdaptiveSTTEngine engine = AdaptiveSTTEngineFactory.create();
 * 
 * // Create with custom configuration
 * EngineConfig config = EngineConfig.builder()
 *     .modelPath("/path/to/models")
 *     .defaultModel("whisper-base")
 *     .build();
 * AdaptiveSTTEngine engine = AdaptiveSTTEngineFactory.create(config);
 * }</pre>
 * 
 * @doc.type class
 * @doc.purpose Factory for STT engine creation
 * @doc.layer api
 * @doc.pattern Factory
 */
public final class AdaptiveSTTEngineFactory {

    private AdaptiveSTTEngineFactory() {
        // Prevent instantiation
    }

    /**
     * Create an engine with default configuration.
     * 
     * @return A new AdaptiveSTTEngine instance
     */
    public static AdaptiveSTTEngine create() {
        return create(EngineConfig.defaults());
    }

    /**
     * Create an engine with the specified configuration.
     * 
     * @param config Engine configuration
     * @return A new AdaptiveSTTEngine instance
     */
    public static AdaptiveSTTEngine create(EngineConfig config) {
        return new DefaultAdaptiveSTTEngine(config);
    }

    /**
     * Create an engine with custom pipeline components.
     * 
     * @param config Engine configuration
     * @param components Custom pipeline components
     * @return A new AdaptiveSTTEngine instance
     */
    public static AdaptiveSTTEngine createWithCustomComponents(
            EngineConfig config,
            PipelineComponents components) {
        return new DefaultAdaptiveSTTEngine(config, components);
    }

    /**
     * Custom pipeline components for advanced configuration.
     */
    public interface PipelineComponents {
        // Marker interface - implementations provide custom components
    }
}
