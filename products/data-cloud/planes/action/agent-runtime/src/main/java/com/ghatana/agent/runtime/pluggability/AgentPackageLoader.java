/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.runtime.pluggability;

import com.ghatana.agent.pluggability.AgentCapabilityManifest;
import com.ghatana.agent.pluggability.AgentCapabilityManifestValidator;
import com.ghatana.agent.pluggability.AgentCapabilityManifestValidator.ValidationResult;
import com.ghatana.agent.pluggability.AgentPackage;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Validates and registers {@link AgentPackage} instances into the in-process agent registry.
 *
 * <p>Validation steps:
 * <ol>
 *   <li>Capability manifest cross-field rules via {@link AgentCapabilityManifestValidator}</li>
 *   <li>Package release state — only {@code STABLE} packages may be loaded into production slots</li>
 *   <li>Duplicate check — loading the same agentId+version combination is idempotent; a different
 *       version for an already-loaded agentId requires a swap via {@link AgentSwapCoordinator}</li>
 * </ol>
 *
 * @doc.type class
 * @doc.purpose Validates and registers AgentPackages into the AEP runtime registry
 * @doc.layer agent-runtime
 * @doc.pattern Service
 */
public class AgentPackageLoader {

    private static final Logger log = LoggerFactory.getLogger(AgentPackageLoader.class);

    /** Packages currently loaded, keyed agentId → loaded package. */
    private final Map<String, AgentPackage> loaded = new ConcurrentHashMap<>();

    public AgentPackageLoader() {}

    /**
     * Validates and loads an {@link AgentPackage}.
     *
     * @param pkg the package to load
     * @return a {@link Promise} that resolves to a {@link LoadResult} describing the outcome
     */
    @NotNull
    public Promise<LoadResult> load(@NotNull AgentPackage pkg) {
        Objects.requireNonNull(pkg, "pkg");

        // 1. Validate capability manifest
        AgentCapabilityManifest manifest = pkg.manifest();
        ValidationResult validation = AgentCapabilityManifestValidator.validate(manifest);
        if (!validation.valid()) {
            String reason = "Manifest validation failed: " + String.join("; ", validation.errors());
            log.warn("Rejected AgentPackage [{}] v{}: {}", pkg.agentId(), pkg.agentVersion(), reason);
            return Promise.of(LoadResult.rejected(pkg.agentId(), reason));
        }

        // 2. Release state check — only STABLE packages are allowed in production
        if (pkg.releaseState() != AgentPackage.ReleaseState.STABLE) {
            String reason = "Package release state is " + pkg.releaseState()
                    + "; only STABLE packages may be loaded";
            log.warn("Rejected AgentPackage [{}] (release state): {}", pkg.agentId(), reason);
            return Promise.of(LoadResult.rejected(pkg.agentId(), reason));
        }

        // 3. Existing version check
        AgentPackage existing = loaded.get(pkg.agentId());
        if (existing != null) {
            if (existing.agentVersion().equals(pkg.agentVersion())) {
                log.debug("AgentPackage [{}] v{} already loaded; idempotent load", pkg.agentId(), pkg.agentVersion());
                return Promise.of(LoadResult.alreadyLoaded(pkg.agentId()));
            }
            String reason = "Agent [" + pkg.agentId() + "] is already loaded with version "
                    + existing.agentVersion() + "; use AgentSwapCoordinator to upgrade";
            log.warn("Rejected AgentPackage [{}] v{}: {}", pkg.agentId(), pkg.agentVersion(), reason);
            return Promise.of(LoadResult.rejected(pkg.agentId(), reason));
        }

        loaded.put(pkg.agentId(), pkg);
        log.info("AgentPackage [{}] v{} loaded successfully", pkg.agentId(), pkg.agentVersion());
        return Promise.of(LoadResult.success(pkg.agentId()));
    }

    /**
     * Returns whether the given agentId is currently loaded.
     */
    public boolean isLoaded(@NotNull String agentId) {
        return loaded.containsKey(agentId);
    }

    /**
     * Returns the currently loaded package for {@code agentId}, or {@code null} if not loaded.
     */
    public AgentPackage getLoaded(@NotNull String agentId) {
        return loaded.get(agentId);
    }

    /**
     * Removes a loaded package. Used internally by {@link AgentSwapCoordinator}.
     */
    void evict(@NotNull String agentId) {
        loaded.remove(agentId);
    }

    // ─── LoadResult ──────────────────────────────────────────────────────────

    /**
     * Sealed result type for a {@link AgentPackageLoader#load(AgentPackage)} operation.
     */
    public sealed interface LoadResult {

        String agentId();

        record Success(@NotNull String agentId) implements LoadResult {}

        record AlreadyLoaded(@NotNull String agentId) implements LoadResult {}

        record Rejected(@NotNull String agentId, @NotNull String reason) implements LoadResult {}

        static LoadResult success(String agentId)           { return new Success(agentId); }
        static LoadResult alreadyLoaded(String agentId)     { return new AlreadyLoaded(agentId); }
        static LoadResult rejected(String agentId, String r){ return new Rejected(agentId, r); }

        default boolean isSuccess() {
            return this instanceof Success;
        }
    }
}
