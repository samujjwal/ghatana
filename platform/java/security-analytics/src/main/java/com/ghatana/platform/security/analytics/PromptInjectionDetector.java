/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.security.analytics;

import io.activej.promise.Promise;

/**
 * Detects prompt-injection attempts in user-supplied or agent-generated text inputs.
 *
 * <p>Prompt injection is the practice of embedding crafted instructions in data
 * fields to override an agent's system prompt and hijack its behaviour. This
 * interface provides early-warning detection before text is forwarded to an LLM.
 *
 * @doc.type interface
 * @doc.purpose Detect prompt-injection patterns in text before LLM submission
 * @doc.layer platform
 * @doc.pattern Service
 */
public interface PromptInjectionDetector {

    /**
     * Analyse the provided text for prompt-injection signatures.
     *
     * @param tenantId owning tenant
     * @param input    the raw text to analyse (tool input, user message, data field, etc.)
     * @return promise resolving to a {@link DetectionResult} indicating whether injection was found
     */
    Promise<DetectionResult> detect(String tenantId, String input);

    /**
     * The result of a prompt-injection analysis.
     *
     * @param injectionDetected {@code true} if an injection pattern was found
     * @param matchedPattern    the first pattern that triggered detection, or {@code null} if none
     * @param confidence        confidence score 0.0–1.0 (1.0 = highly confident)
     *
     * @doc.type record
     * @doc.purpose Immutable result of a prompt-injection detection analysis
     * @doc.layer platform
     * @doc.pattern ValueObject
     */
    record DetectionResult(boolean injectionDetected, String matchedPattern, double confidence) {
        /** Convenience factory: safe input, no injection detected. */
        public static DetectionResult safe() {
            return new DetectionResult(false, null, 0.0);
        }

        /** Convenience factory: injection detected. */
        public static DetectionResult detected(String pattern, double confidence) {
            return new DetectionResult(true, pattern, confidence);
        }
    }
}
