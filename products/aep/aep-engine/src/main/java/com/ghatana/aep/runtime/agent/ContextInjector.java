package com.ghatana.agent.memory.retrieval;

import com.ghatana.agent.memory.store.ScoredMemoryItem;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Formats retrieved memory items into structured context for LLM injection.
 *
 * @doc.type interface
 * @doc.purpose Context injection SPI
 * @doc.layer agent-memory
 */
public interface ContextInjector {

    /**
     * Formats scored memory items into a context string for LLM prompts.
     *
     * @param items  Retrieved items
     * @param config Injection configuration
     * @return Formatted context string
     */
    @NotNull String formatForInjection(
            @NotNull List<ScoredMemoryItem> items,
            @NotNull InjectionConfig config);
}
