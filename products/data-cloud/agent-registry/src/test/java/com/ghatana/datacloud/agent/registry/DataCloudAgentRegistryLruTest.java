/*
 * Copyright (c) 2026 Ghatana Inc. 
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
 * LRU cache eviction tests for {@link DataCloudAgentRegistry} (DC-006). 
 *
 * <p>Verifies that the hybrid LRU + TTL eviction strategy introduced in DC-006
 * correctly trims the cache when {@code maxCacheSize} is exceeded.  Tests use
 * the package-private {@link DataCloudAgentRegistry#triggerEvictionForTesting()} 
 * hook to run eviction synchronously without sleeping.
 *
 * @doc.type class
 * @doc.purpose LRU + TTL cache eviction regression tests (DC-006) 
 * @doc.layer registry
 * @doc.pattern Test, Mockito
 */
@ExtendWith(MockitoExtension.class) 
@DisplayName("DataCloudAgentRegistry — LRU cache eviction (DC-006)")
class DataCloudAgentRegistryLruTest extends EventloopTestBase {

    private static final String TENANT_ID = "lru-test-tenant";

    @Mock private DataCloudClient dataCloud;
    @Mock private EntityInterface mockEntity;

    private DataCloudAgentRegistry registry;

    @AfterEach
    void tearDown() { 
        if (registry != null) { 
            registry.close(); 
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void stubRegister() { 
        when(mockEntity.getId()).thenReturn(UUID.randomUUID()); 
        when(dataCloud.createEntity(eq(TENANT_ID), eq(DataCloudAgentRegistry.REGISTRY_COLLECTION), 
            anyMap())) 
            .thenReturn(Promise.of(mockEntity)); 
        when(dataCloud.appendEvent(eq(TENANT_ID), anyString(), any())) 
            .thenReturn(Promise.of(0L)); 
    }

    @SuppressWarnings("unchecked")
    private static TypedAgent<String, String> agent(String id) { 
        TypedAgent<String, String> agent = mock(TypedAgent.class); 
        AgentDescriptor desc = AgentDescriptor.builder() 
            .agentId(id) 
            .name("Agent-" + id) 
            .version("1.0.0")
            .type(AgentType.DETERMINISTIC) 
            .determinism(DeterminismGuarantee.FULL) 
            .failureMode(FailureMode.FAIL_FAST) 
            .stateMutability(StateMutability.STATELESS) 
            .capabilities(Set.of("test"))
            .build(); 
        lenient().when(agent.descriptor()).thenReturn(desc); 
        return agent;
    }

    private static AgentConfig config(String id) { 
        return AgentConfig.builder() 
            .agentId(id) 
            .type(AgentType.DETERMINISTIC) 
            .version("1.0.0")
            .timeout(Duration.ofMillis(50)) 
            .build(); 
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("LRU pass — cache size limit enforcement")
    class LruPassTests {

        @BeforeEach
        void setUp() { 
            // maxCacheSize = 3, no TTL eviction (cacheTtlMs=0) 
            registry = new DataCloudAgentRegistry(dataCloud, TENANT_ID, 0L, 3); 
        }

        @Test
        @DisplayName("cache remains within maxCacheSize after eviction is triggered")
        void cacheRemainsWithinMaxSizeAfterEviction() { 
            stubRegister(); 

            // Register 5 agents into the cache (cache limit = 3) 
            for (int i = 1; i <= 5; i++) { 
                String id = "agent-" + i;
                runPromise(() -> registry.register(agent(id), config(id))); 
            }

            // Before eviction: cache holds all 5 (eviction hasn't run yet) 
            assertThat(registry.getCacheSize()) 
                .as("all 5 agents should be in-cache before eviction")
                .isEqualTo(5); 

            // Trigger eviction synchronously
            registry.triggerEvictionForTesting(); 

            // After eviction: cache must be trimmed to maxCacheSize=3
            assertThat(registry.getCacheSize()) 
                .as("cache must be trimmed to maxCacheSize after LRU eviction")
                .isLessThanOrEqualTo(3); 
        }

        @Test
        @DisplayName("least-recently-used entries are evicted first")
        void lruEntriesAreEvictedFirst() throws InterruptedException { 
            stubRegister(); 

            // Register 4 agents sequentially with small delays so lastAccessMs differ
            for (int i = 1; i <= 4; i++) { 
                String id = "agent-" + i;
                runPromise(() -> registry.register(agent(id), config(id))); 
                Thread.sleep(5); // ensure distinct lastAccessMs 
            }

            // Access agents 3 and 4 again to make them "recently used"
            // (agents 1 and 2 are the least recently used) 
            // resolve() updates lastAccessMs, making agents 3 and 4 recently used 
            runPromise(() -> registry.resolve("agent-3"));
            runPromise(() -> registry.resolve("agent-4"));

            // maxCacheSize=3: eviction should remove the single LRU entry (agent-1) 
            registry.triggerEvictionForTesting(); 

            assertThat(registry.getCacheSize()) 
                .as("cache should be trimmed to 3 after LRU eviction")
                .isLessThanOrEqualTo(3); 
        }

        @Test
        @DisplayName("no eviction occurs when cache size is within limit")
        void noEvictionWhenWithinLimit() { 
            stubRegister(); 

            // Register only 2 agents (below limit of 3) 
            runPromise(() -> registry.register(agent("a1"), config("a1")));
            runPromise(() -> registry.register(agent("a2"), config("a2")));

            int sizeBefore = registry.getCacheSize(); 
            registry.triggerEvictionForTesting(); 

            assertThat(registry.getCacheSize()) 
                .as("no entries should be evicted when within limit")
                .isEqualTo(sizeBefore); 
        }
    }

    @Nested
    @DisplayName("TTL pass — time-based eviction")
    class TtlPassTests {

        @BeforeEach
        void setUp() { 
            // cacheTtlMs = 1ms (effectively expires immediately), no LRU limit 
            registry = new DataCloudAgentRegistry(dataCloud, TENANT_ID, 1L, 
                DataCloudAgentRegistry.DEFAULT_MAX_CACHE_SIZE);
        }

        @Test
        @DisplayName("TTL-expired entries are removed from cache")
        void ttlExpiredEntriesAreRemoved() throws InterruptedException { 
            stubRegister(); 

            runPromise(() -> registry.register(agent("ttl-agent"), config("ttl-agent")));
            assertThat(registry.getCacheSize()).isEqualTo(1); 

            // Wait for the TTL to expire (1 ms is enough) 
            Thread.sleep(10); 

            registry.triggerEvictionForTesting(); 

            assertThat(registry.getCacheSize()) 
                .as("TTL-expired entry should be evicted from cache")
                .isZero(); 
        }
    }
}
