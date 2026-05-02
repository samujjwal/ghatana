/*
 * Copyright (c) 2026 Ghatana Inc. 
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

    private AgentPackage buildPackage(String agentId, String version, 
                                      AgentPackage.ReleaseState state) {
        AgentCapabilityManifest manifest = AgentCapabilityManifest.standalone(agentId, version, "tenant-1"); 
        return AgentPackage.builder() 
                .packageId(agentId + "-" + version) 
                .manifest(manifest) 
                .implementationClass("com.example.Agent")
                .source(AgentPackageSource.BUILT_IN) 
                .releaseState(state) 
                .registeredAt(Instant.now()) 
                .metadata(Map.of()) 
                .build(); 
    }

    @BeforeEach
    void setUp() { 
        loader = new AgentPackageLoader(); 
        swap = new AgentSwapCoordinator(loader); 
    }

    // ─── AgentPackageLoader ──────────────────────────────────────────────────

    @Nested
    @DisplayName("AgentPackageLoader")
    class LoaderTests {

        @Test
        @DisplayName("STABLE package loads successfully")
        void stableLoadSucceeds() { 
            AgentPackage pkg = buildPackage("agent-1", "1.0.0", AgentPackage.ReleaseState.STABLE); 
            AgentPackageLoader.LoadResult result = runPromise(() -> loader.load(pkg)); 
            assertThat(result.isSuccess()).isTrue(); 
            assertThat(loader.isLoaded("agent-1")).isTrue();
        }

        @Test
        @DisplayName("DRAFT package is rejected")
        void draftIsRejected() { 
            AgentPackage pkg = buildPackage("agent-2", "0.1.0", AgentPackage.ReleaseState.DRAFT); 
            AgentPackageLoader.LoadResult result = runPromise(() -> loader.load(pkg)); 
            assertThat(result).isInstanceOf(AgentPackageLoader.LoadResult.Rejected.class); 
            assertThat(loader.isLoaded("agent-2")).isFalse();
        }

        @Test
        @DisplayName("loading the same version twice is idempotent")
        void idempotentLoad() { 
            AgentPackage pkg = buildPackage("agent-3", "2.0.0", AgentPackage.ReleaseState.STABLE); 
            runPromise(() -> loader.load(pkg)); 
            AgentPackageLoader.LoadResult second = runPromise(() -> loader.load(pkg)); 
            assertThat(second).isInstanceOf(AgentPackageLoader.LoadResult.AlreadyLoaded.class); 
        }

        @Test
        @DisplayName("loading a different version for same agent is rejected")
        void differentVersionRejected() { 
            runPromise(() -> loader.load(buildPackage("agent-4", "1.0.0", AgentPackage.ReleaseState.STABLE))); 
            AgentPackageLoader.LoadResult result =
                    runPromise(() -> loader.load(buildPackage("agent-4", "2.0.0", AgentPackage.ReleaseState.STABLE))); 
            assertThat(result).isInstanceOf(AgentPackageLoader.LoadResult.Rejected.class); 
        }
    }

    // ─── AgentSwapCoordinator ────────────────────────────────────────────────

    @Nested
    @DisplayName("AgentSwapCoordinator")
    class SwapTests {

        @Test
        @DisplayName("swaps a loaded agent to a new version")
        void swapsToNewVersion() { 
            runPromise(() -> loader.load(buildPackage("agent-x", "1.0.0", AgentPackage.ReleaseState.STABLE))); 
            AgentSwapCoordinator.SwapHandle handle =
                    runPromise(() -> swap.swap(buildPackage("agent-x", "2.0.0", AgentPackage.ReleaseState.STABLE))); 
            assertThat(handle.isSuccess()).isTrue(); 
            assertThat(loader.getLoaded("agent-x").agentVersion()).isEqualTo("2.0.0");
        }

        @Test
        @DisplayName("swapping the same version is a no-op")
        void sameVersionIsNoOp() { 
            runPromise(() -> loader.load(buildPackage("agent-y", "1.0.0", AgentPackage.ReleaseState.STABLE))); 
            AgentSwapCoordinator.SwapHandle handle =
                    runPromise(() -> swap.swap(buildPackage("agent-y", "1.0.0", AgentPackage.ReleaseState.STABLE))); 
            assertThat(handle).isInstanceOf(AgentSwapCoordinator.SwapHandle.NoOp.class); 
        }

        @Test
        @DisplayName("swap with DRAFT incoming package fails and rolls back")
        void draftSwapFails() { 
            runPromise(() -> loader.load(buildPackage("agent-z", "1.0.0", AgentPackage.ReleaseState.STABLE))); 
            AgentSwapCoordinator.SwapHandle handle =
                    runPromise(() -> swap.swap(buildPackage("agent-z", "2.0.0", AgentPackage.ReleaseState.DRAFT))); 
            assertThat(handle).isInstanceOf(AgentSwapCoordinator.SwapHandle.Failed.class); 
            // Rollback: old version should be restored
            assertThat(loader.isLoaded("agent-z")).isTrue();
            assertThat(loader.getLoaded("agent-z").agentVersion()).isEqualTo("1.0.0");
        }
    }
}
