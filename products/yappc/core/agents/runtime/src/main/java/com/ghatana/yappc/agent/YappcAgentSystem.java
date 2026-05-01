/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.agent;

import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.agent.framework.runtime.generators.LLMGenerator;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Unified YAPPC agent system that bootstraps and manages SDLC specialist and planner agents.
 *
 * <p>Provides a facade over the agent runtime, including:
 * <ul>
 *   <li>Agent registry and discovery</li>
 *   <li>AI runtime mode configuration (REQUIRED vs STUB)</li>
 *   <li>LLM gateway integration</li>
 *   <li>AEP event publishing for agent lifecycle events</li>
 * </ul>
 *
 * <p>Initialization is deferred — callers must invoke {@code initialize()} after construction
 * to load agent definitions and wire dependencies.
 *
 * @doc.type class
 * @doc.purpose Unified YAPPC agent system for SDLC specialist and planner agents
 * @doc.layer product
 * @doc.pattern Facade, System
 * @doc.gaa.lifecycle initialize
 */
public final class YappcAgentSystem {

    private static final Logger log = LoggerFactory.getLogger(YappcAgentSystem.class);

    /** AI runtime mode: requires provider-backed LLM or uses stub implementation. */
    public enum AiRuntimeMode {
        /** Provider-backed LLM is required; fails fast if unavailable. */
        REQUIRED,
        /** Stub mode for testing/dev; no provider-backed LLM. */
        STUB
    }

    private final Eventloop eventloop;
    private final MemoryStore memoryStore;
    private final AiRuntimeMode aiRuntimeMode;
    private final LLMGenerator.LLMGateway llmGateway;
    private final AepEventPublisher aepEventPublisher;
    private final YappcAgentRegistryAdapter sdlcRegistry;

    private boolean initialized = false;

    private YappcAgentSystem(Builder builder) {
        this.eventloop = Objects.requireNonNull(builder.eventloop, "eventloop");
        this.memoryStore = Objects.requireNonNull(builder.memoryStore, "memoryStore");
        this.aiRuntimeMode = Objects.requireNonNull(builder.aiRuntimeMode, "aiRuntimeMode");
        this.llmGateway = builder.llmGateway; // nullable for STUB mode
        this.aepEventPublisher = Objects.requireNonNull(builder.aepEventPublisher, "aepEventPublisher");
        this.sdlcRegistry = Objects.requireNonNull(builder.sdlcRegistry, "sdlcRegistry");
    }

    /**
     * Initializes the agent system by loading agent definitions and wiring dependencies.
     *
     * @return Promise that completes when initialization is finished
     */
    @NotNull
    public Promise<Void> initialize() {
        if (initialized) {
            log.warn("YappcAgentSystem already initialized");
            return Promise.complete();
        }

        log.info("Initializing YappcAgentSystem with aiRuntimeMode={}", aiRuntimeMode);
        
        // Validate configuration based on runtime mode
        if (aiRuntimeMode == AiRuntimeMode.REQUIRED && llmGateway == null) {
            return Promise.ofException(new IllegalStateException(
                "LLM gateway is required when aiRuntimeMode=REQUIRED"));
        }

        initialized = true;
        log.info("YappcAgentSystem initialized successfully");
        return Promise.complete();
    }

    /**
     * Checks if the system has been initialized.
     *
     * @return true if initialized, false otherwise
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Gets the configured AI runtime mode.
     *
     * @return the AI runtime mode
     */
    @NotNull
    public AiRuntimeMode getAiRuntimeMode() {
        return aiRuntimeMode;
    }

    /**
     * Gets the LLM gateway (may be null in STUB mode).
     *
     * @return the LLM gateway, or null if not configured
     */
    @Nullable
    public LLMGenerator.LLMGateway getLlmGateway() {
        return llmGateway;
    }

    /**
     * Gets the AEP event publisher.
     *
     * @return the AEP event publisher
     */
    @NotNull
    public AepEventPublisher getAepEventPublisher() {
        return aepEventPublisher;
    }

    /**
     * Gets the eventloop.
     *
     * @return the eventloop
     */
    @NotNull
    public Eventloop getEventloop() {
        return eventloop;
    }

    /**
     * Gets the memory store.
     *
     * @return the memory store
     */
    @NotNull
    public MemoryStore getMemoryStore() {
        return memoryStore;
    }

    /**
     * Gets the SDLC agent registry.
     *
     * @return the SDLC agent registry
     */
    @NotNull
    public YappcAgentRegistryAdapter getSdlcRegistry() {
        return sdlcRegistry;
    }

    /**
     * Gets all registered agent IDs.
     *
     * @return set of all registered agent IDs
     */
    @NotNull
    public java.util.Set<String> getAllAgentIds() {
        return sdlcRegistry.getAllStepNames();
    }

    /**
     * Creates a new builder for YappcAgentSystem.
     *
     * @return a new builder instance
     */
    @NotNull
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for YappcAgentSystem.
     */
    public static final class Builder {
        private Eventloop eventloop;
        private MemoryStore memoryStore;
        private AiRuntimeMode aiRuntimeMode;
        private LLMGenerator.LLMGateway llmGateway;
        private AepEventPublisher aepEventPublisher;
        private YappcAgentRegistryAdapter sdlcRegistry;

        private Builder() {}

        @NotNull
        public Builder eventloop(@NotNull Eventloop eventloop) {
            this.eventloop = Objects.requireNonNull(eventloop, "eventloop");
            return this;
        }

        @NotNull
        public Builder memoryStore(@NotNull MemoryStore memoryStore) {
            this.memoryStore = Objects.requireNonNull(memoryStore, "memoryStore");
            return this;
        }

        @NotNull
        public Builder aiRuntimeMode(@NotNull AiRuntimeMode aiRuntimeMode) {
            this.aiRuntimeMode = Objects.requireNonNull(aiRuntimeMode, "aiRuntimeMode");
            return this;
        }

        @NotNull
        public Builder llmGateway(@Nullable LLMGenerator.LLMGateway llmGateway) {
            this.llmGateway = llmGateway;
            return this;
        }

        @NotNull
        public Builder aepEventPublisher(@NotNull AepEventPublisher aepEventPublisher) {
            this.aepEventPublisher = Objects.requireNonNull(aepEventPublisher, "aepEventPublisher");
            return this;
        }

        @NotNull
        public Builder sdlcRegistry(@NotNull YappcAgentRegistryAdapter sdlcRegistry) {
            this.sdlcRegistry = Objects.requireNonNull(sdlcRegistry, "sdlcRegistry");
            return this;
        }

        /**
         * Builds the YappcAgentSystem instance.
         *
         * @return the configured YappcAgentSystem
         * @throws IllegalStateException if required fields are missing
         */
        @NotNull
        public YappcAgentSystem build() {
            if (eventloop == null) {
                throw new IllegalStateException("eventloop is required");
            }
            if (memoryStore == null) {
                throw new IllegalStateException("memoryStore is required");
            }
            if (aiRuntimeMode == null) {
                throw new IllegalStateException("aiRuntimeMode is required");
            }
            if (aepEventPublisher == null) {
                throw new IllegalStateException("aepEventPublisher is required");
            }
            if (sdlcRegistry == null) {
                throw new IllegalStateException("sdlcRegistry is required");
            }
            return new YappcAgentSystem(this);
        }
    }
}
