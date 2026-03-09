package com.ghatana.platform.plugin.spi;

import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.ai.llm.LLMGateway;
import com.ghatana.platform.plugin.Plugin;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

/**
 * SPI for AI Model Plugins.
 * Extends LLMGateway to provide standard AI capabilities.
 *
 * @doc.type interface
 * @doc.purpose AI Model abstraction
 * @doc.layer core
 */
public interface AIModelPlugin extends Plugin, LLMGateway {

    /**
     * Default implementation delegating to the gateway interface.
     * Plugins should override this if they have specific initialization logic
     * or need to handle the request differently.
     */
    @Override
    @NotNull
    default Promise<CompletionResult> complete(CompletionRequest request) {
        // This default is just to satisfy the interface if the plugin
        // implements LLMGateway logic directly in the class.
        // In practice, the implementation will provide the logic.
        return Promise.ofException(new UnsupportedOperationException("Not implemented"));
    }
}
