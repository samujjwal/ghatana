/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.agent.runtime.pluggability;

import com.ghatana.agent.pluggability.AgentCapabilityManifest;
import com.ghatana.agent.pluggability.AgentPackage;
import com.ghatana.agent.pluggability.AgentPackageSource;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("AgentPackageLoader + AgentSwapCoordinator")
class AgentPackageLoaderTest extends EventloopTestBase {

    private AgentPackageLoader loader;
    private AgentSwapCoordinator swap;

    private AgentPackage buildPackage(String agentId, String version, // GH-90000
                                      AgentPackage.ReleaseState state) {
        AgentCapabilityManifest manifest = AgentCapabilityManifest.standalone(agentId, version, "tenant-1"); // GH-90000
        return AgentPackage.builder() // GH-90000
                .packageId(agentId + "-" + version) // GH-90000
                .manifest(manifest) // GH-90000
                .implementationClass("com.example.Agent")
                .source(AgentPackageSource.BUILT_IN) // GH-90000
                .releaseState(state) // GH-90000
                .registeredAt(Instant.now()) // GH-90000
                .metadata(Map.of()) // GH-90000
                .build(); // GH-90000
    }

    @BeforeEach
    void setUp() { // GH-90000
        loader = new AgentPackageLoader(); // GH-90000
        swap = new AgentSwapCoordinator(loader); // GH-90000
    }

    // ─── AgentPackageLoader ──────────────────────────────────────────────────

    @Nested
    @DisplayName("AgentPackageLoader")
    class LoaderTests {

        @Test
        @DisplayName("STABLE package loads successfully")
        void stableLoadSucceeds() { // GH-90000
            AgentPackage pkg = buildPackage("agent-1", "1.0.0", AgentPackage.ReleaseState.STABLE); // GH-90000
            AgentPackageLoader.LoadResult result = runPromise(() -> loader.load(pkg)); // GH-90000
            assertThat(result.isSuccess()).isTrue(); // GH-90000
            assertThat(loader.isLoaded("agent-1")).isTrue();
        }

        @Test
        @DisplayName("DRAFT package is rejected")
        void draftIsRejected() { // GH-90000
            AgentPackage pkg = buildPackage("agent-2", "0.1.0", AgentPackage.ReleaseState.DRAFT); // GH-90000
            AgentPackageLoader.LoadResult result = runPromise(() -> loader.load(pkg)); // GH-90000
            assertThat(result).isInstanceOf(AgentPackageLoader.LoadResult.Rejected.class); // GH-90000
            assertThat(loader.isLoaded("agent-2")).isFalse();
        }

        @Test
        @DisplayName("loading the same version twice is idempotent")
        void idempotentLoad() { // GH-90000
            AgentPackage pkg = buildPackage("agent-3", "2.0.0", AgentPackage.ReleaseState.STABLE); // GH-90000
            runPromise(() -> loader.load(pkg)); // GH-90000
            AgentPackageLoader.LoadResult second = runPromise(() -> loader.load(pkg)); // GH-90000
            assertThat(second).isInstanceOf(AgentPackageLoader.LoadResult.AlreadyLoaded.class); // GH-90000
        }

        @Test
        @DisplayName("loading a different version for same agent is rejected")
        void differentVersionRejected() { // GH-90000
            runPromise(() -> loader.load(buildPackage("agent-4", "1.0.0", AgentPackage.ReleaseState.STABLE))); // GH-90000
            AgentPackageLoader.LoadResult result =
                    runPromise(() -> loader.load(buildPackage("agent-4", "2.0.0", AgentPackage.ReleaseState.STABLE))); // GH-90000
            assertThat(result).isInstanceOf(AgentPackageLoader.LoadResult.Rejected.class); // GH-90000
        }
    }

    // ─── AgentSwapCoordinator ────────────────────────────────────────────────

    @Nested
    @DisplayName("AgentSwapCoordinator")
    class SwapTests {

        @Test
        @DisplayName("swaps a loaded agent to a new version")
        void swapsToNewVersion() { // GH-90000
            runPromise(() -> loader.load(buildPackage("agent-x", "1.0.0", AgentPackage.ReleaseState.STABLE))); // GH-90000
            AgentSwapCoordinator.SwapHandle handle =
                    runPromise(() -> swap.swap(buildPackage("agent-x", "2.0.0", AgentPackage.ReleaseState.STABLE))); // GH-90000
            assertThat(handle.isSuccess()).isTrue(); // GH-90000
            assertThat(loader.getLoaded("agent-x").agentVersion()).isEqualTo("2.0.0");
        }

        @Test
        @DisplayName("swapping the same version is a no-op")
        void sameVersionIsNoOp() { // GH-90000
            runPromise(() -> loader.load(buildPackage("agent-y", "1.0.0", AgentPackage.ReleaseState.STABLE))); // GH-90000
            AgentSwapCoordinator.SwapHandle handle =
                    runPromise(() -> swap.swap(buildPackage("agent-y", "1.0.0", AgentPackage.ReleaseState.STABLE))); // GH-90000
            assertThat(handle).isInstanceOf(AgentSwapCoordinator.SwapHandle.NoOp.class); // GH-90000
        }

        @Test
        @DisplayName("swap with DRAFT incoming package fails and rolls back")
        void draftSwapFails() { // GH-90000
            runPromise(() -> loader.load(buildPackage("agent-z", "1.0.0", AgentPackage.ReleaseState.STABLE))); // GH-90000
            AgentSwapCoordinator.SwapHandle handle =
                    runPromise(() -> swap.swap(buildPackage("agent-z", "2.0.0", AgentPackage.ReleaseState.DRAFT))); // GH-90000
            assertThat(handle).isInstanceOf(AgentSwapCoordinator.SwapHandle.Failed.class); // GH-90000
            // Rollback: old version should be restored
            assertThat(loader.isLoaded("agent-z")).isTrue();
            assertThat(loader.getLoaded("agent-z").agentVersion()).isEqualTo("1.0.0");
        }
    }
}
