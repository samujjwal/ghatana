package com.ghatana.tts.core.api;

import com.ghatana.tts.core.config.EngineConfig;
import com.ghatana.tts.core.inference.AiInferenceTtsEngine;
import com.ghatana.tts.core.pipeline.DefaultTtsEngine;

import java.util.logging.Logger;

/**
 * Factory for creating TTS engine instances.
 *
 * <p>Selection strategy:
 * <ol>
 *   <li>Try {@link DefaultTtsEngine} (ONNX Runtime + Coqui TTS native libs).</li>
 *   <li>If the native engine fails to reach {@link EngineState#READY} — typically
 *       because the Coqui native library is absent — fall back to
 *       {@link AiInferenceTtsEngine}, which delegates synthesis to the Ghatana
 *       AI Inference Service over HTTP ({@code POST /ai/infer/tts}).</li>
 * </ol>
 *
 * @doc.type class
 * @doc.purpose TTS engine factory with AI Inference Service fallback
 * @doc.layer product
 * @doc.pattern Factory
 */
public final class TtsEngineFactory {

    private static final Logger LOG = Logger.getLogger(TtsEngineFactory.class.getName());

    private TtsEngineFactory() {
        // Prevent instantiation
    }

    /**
     * Create a TTS engine with default configuration.
     *
     * <p>Falls back to {@link AiInferenceTtsEngine} automatically when the
     * native ONNX/Coqui engine cannot initialise.
     *
     * @return a ready TTS engine instance
     */
    public static TtsEngine create() {
        return create(EngineConfig.defaults());
    }

    /**
     * Create a TTS engine with the specified configuration.
     *
     * <p>Falls back to {@link AiInferenceTtsEngine} automatically when the
     * native ONNX/Coqui engine cannot initialise.
     *
     * @param config the engine configuration
     * @return a ready TTS engine instance
     */
    public static TtsEngine create(EngineConfig config) {
        try {
            DefaultTtsEngine nativeEngine = new DefaultTtsEngine(config);
            if (nativeEngine.getStatus().state() == EngineState.READY) {
                LOG.info("[TtsEngineFactory] Using DefaultTtsEngine (native ONNX/Coqui)");
                return nativeEngine;
            }
            // Native engine initialised but is not READY — fall through to fallback
            LOG.warning("[TtsEngineFactory] DefaultTtsEngine state="
                    + nativeEngine.getStatus().state()
                    + " — switching to AiInferenceTtsEngine fallback");
            closeQuietly(nativeEngine);
        } catch (Exception e) {
            LOG.warning("[TtsEngineFactory] DefaultTtsEngine init failed ("
                    + e.getMessage() + ") — switching to AiInferenceTtsEngine fallback");
        }

        AiInferenceTtsEngine fallback = new AiInferenceTtsEngine();
        LOG.info("[TtsEngineFactory] AiInferenceTtsEngine active (state="
                + fallback.getStatus().state() + ")");
        return fallback;
    }

    private static void closeQuietly(AutoCloseable closeable) {
        try {
            closeable.close();
        } catch (Exception ignored) {
            // best-effort shutdown of a failed engine — ignore
        }
    }
}

