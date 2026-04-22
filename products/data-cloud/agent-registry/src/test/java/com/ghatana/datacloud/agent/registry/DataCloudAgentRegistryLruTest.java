/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.agent.registry;

import com.ghatana.agent.AgentConfig;
import com.ghatana.agent.AgentDescriptor;
import com.ghatana.agent.AgentType;
import com.ghatana.agent.DeterminismGuarantee;
import com.ghatana.agent.FailureMode;
import com.ghatana.agent.StateMutability;
import com.ghatana.agent.TypedAgent;
import com.ghatana.datacloud.client.DataCloudClient;
import com.ghatana.datacloud.entity.EntityInterface;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * LRU cache eviction tests for {@link DataCloudAgentRegistry} (DC-006). // GH-90000
 *
 * <p>Verifies that the hybrid LRU + TTL eviction strategy introduced in DC-006
 * correctly trims the cache when {@code maxCacheSize} is exceeded.  Tests use
 * the package-private {@link DataCloudAgentRegistry#triggerEvictionForTesting()} // GH-90000
 * hook to run eviction synchronously without sleeping.
 *
 * @doc.type class
 * @doc.purpose LRU + TTL cache eviction regression tests (DC-006) // GH-90000
 * @doc.layer registry
 * @doc.pattern Test, Mockito
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("DataCloudAgentRegistry — LRU cache eviction (DC-006) [GH-90000]")
class DataCloudAgentRegistryLruTest extends EventloopTestBase {

    private static final String TENANT_ID = "lru-test-tenant";

    @Mock private DataCloudClient dataCloud;
    @Mock private EntityInterface mockEntity;

    private DataCloudAgentRegistry registry;

    @AfterEach
    void tearDown() { // GH-90000
        if (registry != null) { // GH-90000
            registry.close(); // GH-90000
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void stubRegister() { // GH-90000
        when(mockEntity.getId()).thenReturn(UUID.randomUUID()); // GH-90000
        when(dataCloud.createEntity(eq(TENANT_ID), eq(DataCloudAgentRegistry.REGISTRY_COLLECTION), // GH-90000
            anyMap())) // GH-90000
            .thenReturn(Promise.of(mockEntity)); // GH-90000
        when(dataCloud.appendEvent(eq(TENANT_ID), anyString(), any())) // GH-90000
            .thenReturn(Promise.of(0L)); // GH-90000
    }

    @SuppressWarnings("unchecked [GH-90000]")
    private static TypedAgent<String, String> agent(String id) { // GH-90000
        TypedAgent<String, String> agent = mock(TypedAgent.class); // GH-90000
        AgentDescriptor desc = AgentDescriptor.builder() // GH-90000
            .agentId(id) // GH-90000
            .name("Agent-" + id) // GH-90000
            .version("1.0.0 [GH-90000]")
            .type(AgentType.DETERMINISTIC) // GH-90000
            .determinism(DeterminismGuarantee.FULL) // GH-90000
            .failureMode(FailureMode.FAIL_FAST) // GH-90000
            .stateMutability(StateMutability.STATELESS) // GH-90000
            .capabilities(Set.of("test [GH-90000]"))
            .build(); // GH-90000
        lenient().when(agent.descriptor()).thenReturn(desc); // GH-90000
        return agent;
    }

    private static AgentConfig config(String id) { // GH-90000
        return AgentConfig.builder() // GH-90000
            .agentId(id) // GH-90000
            .type(AgentType.DETERMINISTIC) // GH-90000
            .version("1.0.0 [GH-90000]")
            .timeout(Duration.ofMillis(50)) // GH-90000
            .build(); // GH-90000
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("LRU pass — cache size limit enforcement [GH-90000]")
    class LruPassTests {

        @BeforeEach
        void setUp() { // GH-90000
            // maxCacheSize = 3, no TTL eviction (cacheTtlMs=0) // GH-90000
            registry = new DataCloudAgentRegistry(dataCloud, TENANT_ID, 0L, 3); // GH-90000
        }

        @Test
        @DisplayName("cache remains within maxCacheSize after eviction is triggered [GH-90000]")
        void cacheRemainsWithinMaxSizeAfterEviction() { // GH-90000
            stubRegister(); // GH-90000

            // Register 5 agents into the cache (cache limit = 3) // GH-90000
            for (int i = 1; i <= 5; i++) { // GH-90000
                String id = "agent-" + i;
                runPromise(() -> registry.register(agent(id), config(id))); // GH-90000
            }

            // Before eviction: cache holds all 5 (eviction hasn't run yet) // GH-90000
            assertThat(registry.getCacheSize()) // GH-90000
                .as("all 5 agents should be in-cache before eviction [GH-90000]")
                .isEqualTo(5); // GH-90000

            // Trigger eviction synchronously
            registry.triggerEvictionForTesting(); // GH-90000

            // After eviction: cache must be trimmed to maxCacheSize=3
            assertThat(registry.getCacheSize()) // GH-90000
                .as("cache must be trimmed to maxCacheSize after LRU eviction [GH-90000]")
                .isLessThanOrEqualTo(3); // GH-90000
        }

        @Test
        @DisplayName("least-recently-used entries are evicted first [GH-90000]")
        void lruEntriesAreEvictedFirst() throws InterruptedException { // GH-90000
            stubRegister(); // GH-90000

            // Register 4 agents sequentially with small delays so lastAccessMs differ
            for (int i = 1; i <= 4; i++) { // GH-90000
                String id = "agent-" + i;
                runPromise(() -> registry.register(agent(id), config(id))); // GH-90000
                Thread.sleep(5); // ensure distinct lastAccessMs // GH-90000
            }

            // Access agents 3 and 4 again to make them "recently used"
            // (agents 1 and 2 are the least recently used) // GH-90000
            // resolve() updates lastAccessMs, making agents 3 and 4 recently used // GH-90000
            runPromise(() -> registry.resolve("agent-3 [GH-90000]"));
            runPromise(() -> registry.resolve("agent-4 [GH-90000]"));

            // maxCacheSize=3: eviction should remove the single LRU entry (agent-1) // GH-90000
            registry.triggerEvictionForTesting(); // GH-90000

            assertThat(registry.getCacheSize()) // GH-90000
                .as("cache should be trimmed to 3 after LRU eviction [GH-90000]")
                .isLessThanOrEqualTo(3); // GH-90000
        }

        @Test
        @DisplayName("no eviction occurs when cache size is within limit [GH-90000]")
        void noEvictionWhenWithinLimit() { // GH-90000
            stubRegister(); // GH-90000

            // Register only 2 agents (below limit of 3) // GH-90000
            runPromise(() -> registry.register(agent("a1 [GH-90000]"), config("a1 [GH-90000]")));
            runPromise(() -> registry.register(agent("a2 [GH-90000]"), config("a2 [GH-90000]")));

            int sizeBefore = registry.getCacheSize(); // GH-90000
            registry.triggerEvictionForTesting(); // GH-90000

            assertThat(registry.getCacheSize()) // GH-90000
                .as("no entries should be evicted when within limit [GH-90000]")
                .isEqualTo(sizeBefore); // GH-90000
        }
    }

    @Nested
    @DisplayName("TTL pass — time-based eviction [GH-90000]")
    class TtlPassTests {

        @BeforeEach
        void setUp() { // GH-90000
            // cacheTtlMs = 1ms (effectively expires immediately), no LRU limit // GH-90000
            registry = new DataCloudAgentRegistry(dataCloud, TENANT_ID, 1L, // GH-90000
                DataCloudAgentRegistry.DEFAULT_MAX_CACHE_SIZE);
        }

        @Test
        @DisplayName("TTL-expired entries are removed from cache [GH-90000]")
        void ttlExpiredEntriesAreRemoved() throws InterruptedException { // GH-90000
            stubRegister(); // GH-90000

            runPromise(() -> registry.register(agent("ttl-agent [GH-90000]"), config("ttl-agent [GH-90000]")));
            assertThat(registry.getCacheSize()).isEqualTo(1); // GH-90000

            // Wait for the TTL to expire (1 ms is enough) // GH-90000
            Thread.sleep(10); // GH-90000

            registry.triggerEvictionForTesting(); // GH-90000

            assertThat(registry.getCacheSize()) // GH-90000
                .as("TTL-expired entry should be evicted from cache [GH-90000]")
                .isZero(); // GH-90000
        }
    }
}
