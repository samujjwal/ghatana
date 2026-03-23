/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.agent;

import com.ghatana.agent.AgentType;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.api.OutputGenerator;
import com.ghatana.agent.framework.config.AgentDefinition;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Unit tests for {@link AepAgentAdapter}.
 *
 * <p>Extends {@link EventloopTestBase} because {@code executeTurn()} returns a Promise.
 * All async assertions use {@code runPromise()}.
 */
@DisplayName("AepAgentAdapter")
class AepAgentAdapterTest extends EventloopTestBase {

    private static final String AGENT_ID  = "fraud-detector";
    private static final String TENANT_ID = "tenant-acme";
    private static final String TURN_ID   = "turn-0001";

    private AgentDefinition definition;
    private AepContextBridge bridge;

    @BeforeEach
    void setUpAdapter() {
        definition = AgentDefinition.builder()
                .id(AGENT_ID)
                .version("1.0.0")
                .type(AgentType.PROBABILISTIC)
                .build();

        bridge = new AepContextBridge(MemoryStore.noOp());
    }

    // ── helper ──────────────────────────────────────────────────────────────

    private AgentContext testContext() {
        return AgentContext.builder()
                .turnId(TURN_ID)
                .agentId(AGENT_ID)
                .tenantId(TENANT_ID)
                .startTime(Instant.now())
                .memoryStore(MemoryStore.noOp())
                .build();
    }

    private AepAgentAdapter adapterWith(OutputGenerator<String, String> gen) {
        return new AepAgentAdapter(definition, gen);
    }

    // ── constructor ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("constructor")
    class Constructor {

        @Test
        @DisplayName("getDefinition() returns the supplied AgentDefinition")
        void exposesDefinition() {
            AepAgentAdapter adapter = adapterWith((_i, _c) -> Promise.of("out"));
            assertThat(adapter.getDefinition()).isSameAs(definition);
        }

        @Test
        @DisplayName("throws NPE when definition is null")
        void nullDefinition() {
            assertThatNullPointerException().isThrownBy(
                    () -> new AepAgentAdapter(null, (_i, _c) -> Promise.of("out")));
        }

        @Test
        @DisplayName("throws NPE when outputGenerator is null")
        void nullOutputGenerator() {
            assertThatNullPointerException().isThrownBy(
                    () -> new AepAgentAdapter(definition, null));
        }
    }

    // ── full lifecycle ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("executeTurn() — full lifecycle")
    class FullLifecycle {

        @Test
        @DisplayName("returns the output from the OutputGenerator")
        void returnsGeneratorOutput() {
            AepAgentAdapter adapter = adapterWith(
                    (input, ctx) -> Promise.of("processed:" + input));

            String result = runPromise(() -> adapter.executeTurn("event-payload", testContext()));

            assertThat(result).isEqualTo("processed:event-payload");
        }

        @Test
        @DisplayName("OutputGenerator receives the perceived input")
        void generatorReceivesInput() {
            AtomicReference<String> capturedInput = new AtomicReference<>();

            AepAgentAdapter adapter = adapterWith((input, ctx) -> {
                capturedInput.set(input);
                return Promise.of("ok");
            });

            runPromise(() -> adapter.executeTurn("my-event", testContext()));

            assertThat(capturedInput.get()).isEqualTo("my-event");
        }

        @Test
        @DisplayName("CAPTURE phase succeeds with noOp MemoryStore")
        void captureWithNoOp() {
            // If capture() calls .getResult() (forbidden), this would throw NPE or
            // hang — completing the Promise is proof capture is implemented correctly.
            AepAgentAdapter adapter = adapterWith((_i, _c) -> Promise.of("output"));

            String result = runPromise(() -> adapter.executeTurn("input", testContext()));

            assertThat(result).isEqualTo("output");
        }

        @Test
        @DisplayName("OutputGenerator receives AgentContext with matching agentId")
        void contextHasCorrectAgentId() {
            AtomicReference<String> capturedAgentId = new AtomicReference<>();

            AepAgentAdapter adapter = adapterWith((input, ctx) -> {
                capturedAgentId.set(ctx.getAgentId());
                return Promise.of("ok");
            });

            runPromise(() -> adapter.executeTurn("evt", testContext()));

            assertThat(capturedAgentId.get()).isEqualTo(AGENT_ID);
        }

        @Test
        @DisplayName("two sequential turns return independent outputs")
        void independentTurns() {
            AepAgentAdapter adapter = adapterWith(
                    (input, ctx) -> Promise.of("echo:" + input));

            String r1 = runPromise(() -> adapter.executeTurn("first", testContext()));
            String r2 = runPromise(() -> adapter.executeTurn("second", testContext()));

            assertThat(r1).isEqualTo("echo:first");
            assertThat(r2).isEqualTo("echo:second");
        }
    }

    // ── AepContextBridge integration ─────────────────────────────────────────

    @Nested
    @DisplayName("works with AepContextBridge")
    class WithContextBridge {

        @Test
        @DisplayName("bridge-produced context flows into OutputGenerator")
        void bridgeContextFlows() {
            AtomicReference<String> capturedTenant = new AtomicReference<>();

            AepAgentAdapter adapter = adapterWith((input, ctx) -> {
                capturedTenant.set(ctx.getTenantId());
                return Promise.of("result");
            });

            AgentContext ctx = bridge.toAgentContext(() -> TENANT_ID, AGENT_ID);
            runPromise(() -> adapter.executeTurn("payload", ctx));

            assertThat(capturedTenant.get()).isEqualTo(TENANT_ID);
        }
    }
}
