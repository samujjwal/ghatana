// filepath: /Users/samujjwal/Development/ghatana/products/shared-services/text-to-speech/libs/tts-core-java/src/main/java/com/ghatana/tts/core/api/TtsEngineFactory.java
package com.ghatana.tts.core.api;

import com.ghatana.tts.core.config.EngineConfig;
import com.ghatana.tts.core.pipeline.DefaultTtsEngine;

/**
 * Factory for creating TTS engine instances.
 *
 * @doc.type class
 * @doc.purpose TTS engine factory
 * @doc.layer product
 * @doc.pattern Factory
 */
public final class TtsEngineFactory {

    private TtsEngineFactory() {
        // Prevent instantiation
    }

    /**
     * Create a TTS engine with default configuration.
     *
     * @return a new TTS engine instance
     */
    public static TtsEngine create() {
        return create(EngineConfig.defaults());
    }

    /**
     * Create a TTS engine with the specified configuration.
     *
     * @param config the engine configuration
     * @return a new TTS engine instance
     */
    public static TtsEngine create(EngineConfig config) {
        return new DefaultTtsEngine(config);
    }
}

