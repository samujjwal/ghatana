/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.ghatana.agent.dispatch.tier;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

/**
 * SPI for LLM provider invocation used by Tier-L execution.
 *
 * <p>Implementations bridge to specific LLM providers (Anthropic, OpenAI, etc.)
 * via the platform {@code ai-integration} module.
 *
 * @doc.type interface
 * @doc.purpose LLM provider abstraction for Tier-L dispatch
 * @doc.layer framework
 * @doc.pattern SPI, Adapter
 *
 * @author Ghatana AI Platform
 * @since 2.2.0
 */
public interface LlmProvider {

    /**
     * Invokes an LLM model with the given prompt.
     *
     * @param provider    provider name (e.g. "ANTHROPIC", "OPENAI")
     * @param model       model identifier (e.g. "claude-3-5-sonnet-20241022")
     * @param prompt      the fully-hydrated prompt text
     * @param temperature sampling temperature [0.0, 1.0]
     * @param maxTokens   maximum output tokens
     * @return a Promise of the LLM response as a parsed Object (typically Map or String)
     */
    @NotNull
    Promise<Object> invoke(
            @NotNull String provider,
            @NotNull String model,
            @NotNull String prompt,
            double temperature,
            int maxTokens);
}
