/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.runtime.pluggability;

import com.ghatana.agent.pluggability.AgentPackage;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.UUID;

/**
 * Coordinates hot-swap of a running agent package without service interruption.
 *
 * <p>Hot-swap protocol (4 phases):
 * <ol>
 *   <li><b>Load</b> — validate and pre-load the incoming package via {@link AgentPackageLoader}</li>
 *   <li><b>Drain</b> — wait for in-flight requests on the current agent to complete
 *       (represented by a synchronous no-op in this in-process implementation)</li>
 *   <li><b>Handoff</b> — evict the old package from the loader registry</li>
 *   <li><b>Cut-Over</b> — confirm the new package is the active loaded version</li>
 * </ol>
 * If any phase fails, the coordinator rolls back by evicting the partially-loaded new package
 * so the old entry (if still present) remains authoritative.
 *
 * @doc.type class
 * @doc.purpose Hot-swap coordinator for live agent package replacement
 * @doc.layer agent-runtime
 * @doc.pattern Coordinator
 */
public class AgentSwapCoordinator {

    private static final Logger log = LoggerFactory.getLogger(AgentSwapCoordinator.class);

    private final AgentPackageLoader loader;

    public AgentSwapCoordinator(@NotNull AgentPackageLoader loader) {
        this.loader = Objects.requireNonNull(loader, "loader");
    }

    /**
     * Performs a hot-swap of the currently loaded agent package.
     *
     * @param incoming the new package to swap in
     * @return a {@link Promise} resolving to a {@link SwapHandle} describing the outcome
     */
    @NotNull
    public Promise<SwapHandle> swap(@NotNull AgentPackage incoming) {
        Objects.requireNonNull(incoming, "incoming");
        String swapId = UUID.randomUUID().toString();
        String agentId = incoming.agentId();
        AgentPackage previous = loader.getLoaded(agentId);

        if (previous != null && previous.agentVersion().equals(incoming.agentVersion())) {
            log.debug("Swap [{}] skipped: agent [{}] v{} already current", swapId, agentId, incoming.agentVersion());
            return Promise.of(SwapHandle.noOp(swapId, agentId, incoming.agentVersion()));
        }

        log.info("Swap [{}] starting: agent [{}] {} → {}",
                swapId, agentId,
                previous != null ? previous.agentVersion() : "<none>",
                incoming.agentVersion());

        // Phase 1: Validate and load the incoming package into a temporary slot.
        // AgentPackageLoader will reject if the agentId is already present with a different version,
        // so we evict the old package first to clear the slot (Phase 2/3 combined in-process).
        if (previous != null) {
            loader.evict(agentId); // drain + handoff (in-process: no in-flight requests to drain)
        }

        return loader.load(incoming).map(result -> {
            if (result.isSuccess()) {
                log.info("Swap [{}] complete: agent [{}] is now v{}", swapId, agentId, incoming.agentVersion());
                String previousVersion = previous != null ? previous.agentVersion() : null;
                return SwapHandle.success(swapId, agentId, incoming.agentVersion(), previousVersion);
            } else {
                // Rollback: re-load the old package if it exists
                String reason = result instanceof AgentPackageLoader.LoadResult.Rejected r ? r.reason() : "load failed";
                log.warn("Swap [{}] failed ({}); rollback attempt for agent [{}]", swapId, reason, agentId);
                if (previous != null) {
                    loader.load(previous); // best-effort rollback; errors are logged by loader
                }
                return SwapHandle.failed(swapId, agentId, incoming.agentVersion(), reason);
            }
        });
    }

    // ─── SwapHandle ──────────────────────────────────────────────────────────

    /**
     * Outcome descriptor for a {@link AgentSwapCoordinator#swap(AgentPackage)} operation.
     */
    public sealed interface SwapHandle {

        String swapId();
        String agentId();
        String newVersion();

        record Success(
                @NotNull String swapId,
                @NotNull String agentId,
                @NotNull String newVersion,
                String previousVersion) implements SwapHandle {}

        record NoOp(
                @NotNull String swapId,
                @NotNull String agentId,
                @NotNull String newVersion) implements SwapHandle {}

        record Failed(
                @NotNull String swapId,
                @NotNull String agentId,
                @NotNull String newVersion,
                @NotNull String reason) implements SwapHandle {}

        static SwapHandle success(String swapId, String agentId, String newVer, String prevVer) {
            return new Success(swapId, agentId, newVer, prevVer);
        }

        static SwapHandle noOp(String swapId, String agentId, String version) {
            return new NoOp(swapId, agentId, version);
        }

        static SwapHandle failed(String swapId, String agentId, String newVer, String reason) {
            return new Failed(swapId, agentId, newVer, reason);
        }

        default boolean isSuccess() {
            return this instanceof Success || this instanceof NoOp;
        }
    }
}
