/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.plugin.runtime;

import com.ghatana.appplatform.plugin.domain.PluginManifest;
import com.ghatana.appplatform.plugin.domain.PluginTier;
import com.ghatana.appplatform.rules.sandbox.T2RuleSandbox;
import com.ghatana.appplatform.rules.sandbox.SandboxApiSurface;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * Executes Tier-2 plugins inside the rule sandbox runtime (STORY-K04-004).
 *
 * <p>T2 plugins are scripted rules that run with the same isolation guarantees as
 * {@link T2RuleSandbox}: time-bounded execution, read-only API surface, and no
 * network or filesystem access. The plugin supplies a {@link Function} (loaded by
 * the rule-engine DSL compiler) which is applied to an input map within the sandbox.
 *
 * @doc.type  class
 * @doc.purpose Executes T2 plugin logic inside the rule sandbox; enforces isolation (K04-004)
 * @doc.layer kernel
 * @doc.pattern Adapter
 */
public final class T2SandboxPluginRuntime {

    private static final Logger log = LoggerFactory.getLogger(T2SandboxPluginRuntime.class);

    private final T2RuleSandbox sandbox;
    private final SandboxApiSurface apiSurface;
    private final Executor executor;

    public T2SandboxPluginRuntime(T2RuleSandbox sandbox,
                                   SandboxApiSurface apiSurface,
                                   Executor executor) {
        this.sandbox    = Objects.requireNonNull(sandbox,    "sandbox");
        this.apiSurface = Objects.requireNonNull(apiSurface, "apiSurface");
        this.executor   = Objects.requireNonNull(executor,   "executor");
    }

    /**
     * Executes a T2 plugin rule function in the sandbox.
     *
     * @param manifest  the plugin manifest (must declare tier = T2)
     * @param ruleLogic the compiled rule function
     * @param input     evaluation input
     * @return promise resolving to the rule evaluation result map
     */
    public Promise<Map<String, Object>> execute(PluginManifest manifest,
                                                 Function<SandboxInput, Map<String, Object>> ruleLogic,
                                                 Map<String, Object> input) {
        Objects.requireNonNull(manifest,  "manifest");
        Objects.requireNonNull(ruleLogic, "ruleLogic");
        Objects.requireNonNull(input,     "input");

        return Promise.ofBlocking(executor, () -> {
            enforceT2Tier(manifest);

            String policyKey = manifest.name() + "@" + manifest.version();
            SandboxInput sandboxInput = new SandboxInput(input, apiSurface);

            return sandbox.execute(policyKey, () -> ruleLogic.apply(sandboxInput));
        });
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void enforceT2Tier(PluginManifest manifest) {
        if (manifest.tier() != PluginTier.T2) {
            throw new IllegalArgumentException(
                    "T2SandboxPluginRuntime only accepts T2 plugins, got: " + manifest.tier());
        }
    }

    /**
     * Wrapper passed into the rule logic function, giving it access to the read-only
     * sandbox API alongside the raw evaluation input.
     */
    public record SandboxInput(
            Map<String, Object> input,
            SandboxApiSurface api
    ) {}
}
