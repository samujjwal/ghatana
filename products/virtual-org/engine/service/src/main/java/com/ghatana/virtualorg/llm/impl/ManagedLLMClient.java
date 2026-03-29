package com.ghatana.virtualorg.llm.impl;

import com.ghatana.platform.core.client.ManagedAsyncClient;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

/**
 * Shared lifecycle support for Virtual Org LLM clients.
 *
 * <p>LLM adapters are ready immediately after construction because their backing
 * provider models are fully configured up front, but they still benefit from a
 * consistent start/stop/readiness contract aligned with the platform async
 * client model.</p>
 *
 * @doc.type class
 * @doc.purpose Shared AsyncClient lifecycle support for Virtual Org LLM adapters
 * @doc.layer product
 * @doc.pattern Template Method, Adapter
 */
public abstract class ManagedLLMClient extends ManagedAsyncClient {

    protected ManagedLLMClient() {
        super(true);
    }

    @Override
    @NotNull
    public Promise<Void> start() {
        setRunning(true);
        return Promise.complete();
    }

    @Override
    @NotNull
    public Promise<Void> stop() {
        setRunning(false);
        return Promise.complete();
    }

    @Override
    @NotNull
    public Promise<Boolean> healthCheck() {
        return Promise.of(isRunning());
    }

    protected final <T> Promise<T> rejectIfStopped(@NotNull String operation) {
        if (isRunning()) {
            return null;
        }
        return Promise.ofException(new IllegalStateException(
                "LLM client is stopped and cannot execute operation: " + operation));
    }
}