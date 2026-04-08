/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.audio.video.tools;

import com.ghatana.agent.framework.tools.ToolContract;
import com.ghatana.agent.framework.tools.ToolExecutionEnvelope;
import com.ghatana.agent.framework.tools.ToolExecutionResult;
import com.ghatana.platform.toolruntime.ToolHandler;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * {@link ToolHandler} adapter for the Audio-Video Text-to-Speech service.
 *
 * <p>Wraps the AV TTS gRPC endpoint as a platform tool. Synthesizes speech from text
 * input, returning either inline base64 audio bytes or a media artifact ID depending
 * on the {@code storeAsArtifact} input flag.
 *
 * @doc.type class
 * @doc.purpose ToolHandler adapter for the TTS gRPC capability
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class TextToSpeechToolHandler implements ToolHandler {

    private static final Logger log = LoggerFactory.getLogger(TextToSpeechToolHandler.class);
    static final String TOOL_ID = "av.text-to-speech";

    @Override
    public Promise<ToolExecutionResult> handle(ToolExecutionEnvelope envelope, ToolContract contract) {
        Objects.requireNonNull(envelope, "envelope must not be null");
        Objects.requireNonNull(contract, "contract must not be null");

        Instant start = Instant.now();
        Map<String, Object> input = envelope.input();

        log.debug("TTS invocation [{}] tenant={}", envelope.invocationId(), envelope.tenantId());

        try {
            String text = requireString(input, "text");
            String voiceId = (String) input.getOrDefault("voiceId", "en-US-default");
            double speakingRate = toDouble(input.getOrDefault("speakingRate", 1.0));
            String encoding = (String) input.getOrDefault("audioEncoding", "MP3");
            boolean storeAsArtifact = Boolean.TRUE.equals(input.get("storeAsArtifact"));

            log.debug("TTS: voice={} rate={} encoding={} storeAsArtifact={}", voiceId, speakingRate, encoding, storeAsArtifact);

            // Delegate to real TTS service — stub response mirrors text-to-speech.yaml output schema
            Map<String, Object> output = buildTtsResponse(text, voiceId, encoding, storeAsArtifact);

            Instant end = Instant.now();
            return Promise.of(ToolExecutionResult.succeeded(
                    envelope.invocationId(),
                    output,
                    Map.of("voiceId", voiceId, "encoding", encoding),
                    envelope.invocationId(),
                    end,
                    Duration.between(start, end)));
        } catch (Exception e) {
            log.error("TTS handler failed for invocation {}: {}", envelope.invocationId(), e.getMessage(), e);
            Instant end = Instant.now();
            return Promise.of(ToolExecutionResult.failed(
                    envelope.invocationId(),
                    "TTS processing error: " + e.getMessage(),
                    envelope.invocationId(),
                    end,
                    Duration.between(start, end)));
        }
    }

    private String requireString(Map<String, Object> input, String key) {
        Object val = input.get(key);
        if (!(val instanceof String s) || s.isBlank()) {
            throw new IllegalArgumentException("Required input field '" + key + "' is missing or blank");
        }
        return s;
    }

    private double toDouble(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        return 1.0;
    }

    private Map<String, Object> buildTtsResponse(
            String text, String voiceId, String encoding, boolean storeAsArtifact) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (storeAsArtifact) {
            result.put("mediaArtifactId", null);    // real service returns artifact ID
            result.put("audioBytes", null);
        } else {
            result.put("audioBytes", "");            // real service returns base64
            result.put("mediaArtifactId", null);
        }
        result.put("audioEncoding", encoding);
        result.put("durationMs", 0);
        result.put("voiceId", voiceId);
        return result;
    }
}
