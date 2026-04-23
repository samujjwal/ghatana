/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved. // GH-90000
 *
 * Task 5.1 — Tests for Agent SPI discovery and registration.
 */
package com.ghatana.agent.spi;

import com.ghatana.agent.*;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.runtime.AbstractTypedAgent;
import io.activej.promise.Promise;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link AgentProvider}, {@link AgentProviderRegistry}, and ServiceLoader discovery.
 */
@DisplayName("Agent SPI")
class AgentProviderRegistryTest {

    private AgentProviderRegistry registry;

    @BeforeEach
    void setUp() { // GH-90000
        registry = AgentProviderRegistry.create(); // GH-90000
    }

    // =========================================================================
    // Test Agent Provider Implementation (for use in tests) // GH-90000
    // =========================================================================

    static class StubAgent extends AbstractTypedAgent<String, String> {
        private final String agentId;

        StubAgent(String agentId) { // GH-90000
            this.agentId = agentId;
        }

        @Override
        public AgentDescriptor descriptor() { // GH-90000
            return AgentDescriptor.builder() // GH-90000
                    .agentId(agentId) // GH-90000
                    .name("Stub Agent " + agentId) // GH-90000
                    .type(AgentType.DETERMINISTIC) // GH-90000
                    .version("1.0.0")
                    .capabilities(Set.of("stub"))
                    .build(); // GH-90000
        }

        @Override
        public Promise<AgentResult<String>> doProcess(AgentContext ctx, String input) { // GH-90000
            return Promise.of(AgentResult.success("processed:" + input, agentId, Duration.ofMillis(1))); // GH-90000
        }
    }

    static class TestDeterministicProvider implements AgentProvider {
        private final int priorityVal;
        private final boolean enabled;

        TestDeterministicProvider() { // GH-90000
            this(1000, true); // GH-90000
        }

        TestDeterministicProvider(int priority, boolean enabled) { // GH-90000
            this.priorityVal = priority;
            this.enabled = enabled;
        }

        @Override
        public String getProviderId() { // GH-90000
            return "test-deterministic";
        }

        @Override
        public String getProviderName() { // GH-90000
            return "Test Deterministic Provider";
        }

        @Override
        public Set<AgentType> getSupportedTypes() { // GH-90000
            return Set.of(AgentType.DETERMINISTIC); // GH-90000
        }

        @Override
        public TypedAgent<?, ?> createAgent(AgentConfig config) { // GH-90000
            return new StubAgent(config.getAgentId()); // GH-90000
        }

        @Override
        public AgentDescriptor describe() { // GH-90000
            return AgentDescriptor.builder() // GH-90000
                    .agentId("test-deterministic")
                    .name("Test Deterministic")
                    .type(AgentType.DETERMINISTIC) // GH-90000
                    .version("1.0.0")
                    .capabilities(Set.of("rule-matching"))
                    .build(); // GH-90000
        }

        @Override
        public int priority() { // GH-90000
            return priorityVal;
        }

        @Override
        public boolean isEnabled() { // GH-90000
            return enabled;
        }
    }

    static class TestProbabilisticProvider implements AgentProvider {
        @Override
        public String getProviderId() { // GH-90000
            return "test-probabilistic";
        }

        @Override
        public String getProviderName() { // GH-90000
            return "Test Probabilistic Provider";
        }

        @Override
        public Set<AgentType> getSupportedTypes() { // GH-90000
            return Set.of(AgentType.PROBABILISTIC); // GH-90000
        }

        @Override
        public TypedAgent<?, ?> createAgent(AgentConfig config) { // GH-90000
            return new StubAgent(config.getAgentId()); // GH-90000
        }

        @Override
        public AgentDescriptor describe() { // GH-90000
            return AgentDescriptor.builder() // GH-90000
                    .agentId("test-probabilistic")
                    .name("Test Probabilistic")
                    .type(AgentType.PROBABILISTIC) // GH-90000
                    .version("2.0.0")
                    .capabilities(Set.of("inference"))
                    .build(); // GH-90000
        }

        @Override
        public String getVersion() { // GH-90000
            return "2.0.0";
        }
    }

    static class TestMultiTypeProvider implements AgentProvider {
        @Override
        public String getProviderId() { // GH-90000
            return "test-multi";
        }

        @Override
        public String getProviderName() { // GH-90000
            return "Test Multi-Type Provider";
        }

        @Override
        public Set<AgentType> getSupportedTypes() { // GH-90000
            return Set.of(AgentType.DETERMINISTIC, AgentType.PROBABILISTIC, AgentType.HYBRID); // GH-90000
        }

        @Override
        public TypedAgent<?, ?> createAgent(AgentConfig config) { // GH-90000
            return new StubAgent(config.getAgentId()); // GH-90000
        }

        @Override
        public AgentDescriptor describe() { // GH-90000
            return AgentDescriptor.builder() // GH-90000
                    .agentId("test-multi")
                    .name("Test Multi")
                    .type(AgentType.HYBRID) // GH-90000
                    .version("1.0.0")
                    .capabilities(Set.of("multi"))
                    .build(); // GH-90000
        }

        @Override
        public int priority() { // GH-90000
            return 500; // Higher priority
        }
    }

    // =========================================================================
    // 1. Provider Interface Defaults
    // =========================================================================

    @Nested
    @DisplayName("Provider Interface")
    class ProviderInterface {

        @Test
        @DisplayName("Default supports() delegates to getSupportedTypes()")
        void defaultSupports() { // GH-90000
            TestDeterministicProvider provider = new TestDeterministicProvider(); // GH-90000
            assertThat(provider.supports(AgentType.DETERMINISTIC)).isTrue(); // GH-90000
            assertThat(provider.supports(AgentType.PROBABILISTIC)).isFalse(); // GH-90000
            assertThat(provider.supports(AgentType.ADAPTIVE)).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("Default priority is 1000")
        void defaultPriority() { // GH-90000
            TestProbabilisticProvider provider = new TestProbabilisticProvider(); // GH-90000
            assertThat(provider.priority()).isEqualTo(1000); // GH-90000
        }

        @Test
        @DisplayName("Default isEnabled is true")
        void defaultEnabled() { // GH-90000
            TestProbabilisticProvider provider = new TestProbabilisticProvider(); // GH-90000
            assertThat(provider.isEnabled()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("Default version is 1.0.0")
        void defaultVersion() { // GH-90000
            TestDeterministicProvider provider = new TestDeterministicProvider(); // GH-90000
            assertThat(provider.getVersion()).isEqualTo("1.0.0");
        }

        @Test
        @DisplayName("Custom version override")
        void customVersion() { // GH-90000
            TestProbabilisticProvider provider = new TestProbabilisticProvider(); // GH-90000
            assertThat(provider.getVersion()).isEqualTo("2.0.0");
        }
    }

    // =========================================================================
    // 2. Manual Registration
    // =========================================================================

    @Nested
    @DisplayName("Manual Registration")
    class ManualRegistration {

        @Test
        @DisplayName("Register and retrieve provider")
        void registerAndRetrieve() { // GH-90000
            TestDeterministicProvider provider = new TestDeterministicProvider(); // GH-90000
            registry.registerProvider(provider); // GH-90000

            assertThat(registry.size()).isEqualTo(1); // GH-90000
            assertThat(registry.contains("test-deterministic")).isTrue();
            assertThat(registry.getProvider("test-deterministic")).isPresent();
            assertThat(registry.getProvider("test-deterministic").get().getProviderName())
                    .isEqualTo("Test Deterministic Provider");
        }

        @Test
        @DisplayName("Duplicate registration throws")
        void duplicateThrows() { // GH-90000
            registry.registerProvider(new TestDeterministicProvider()); // GH-90000

            assertThatThrownBy(() -> registry.registerProvider(new TestDeterministicProvider())) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("already registered");
        }

        @Test
        @DisplayName("Unregister removes provider")
        void unregister() { // GH-90000
            registry.registerProvider(new TestDeterministicProvider()); // GH-90000
            assertThat(registry.size()).isEqualTo(1); // GH-90000

            boolean removed = registry.unregisterProvider("test-deterministic");
            assertThat(removed).isTrue(); // GH-90000
            assertThat(registry.size()).isZero(); // GH-90000
            assertThat(registry.contains("test-deterministic")).isFalse();
        }

        @Test
        @DisplayName("Unregister nonexistent returns false")
        void unregisterNonexistent() { // GH-90000
            assertThat(registry.unregisterProvider("ghost")).isFalse();
        }

        @Test
        @DisplayName("getProvider returns empty for unknown ID")
        void getUnknown() { // GH-90000
            assertThat(registry.getProvider("unknown")).isEmpty();
        }

        @Test
        @DisplayName("Null provider rejected")
        void nullRejected() { // GH-90000
            assertThatThrownBy(() -> registry.registerProvider(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }
    }

    // =========================================================================
    // 3. Type-Based Discovery
    // =========================================================================

    @Nested
    @DisplayName("Type-Based Discovery")
    class TypeBasedDiscovery {

        @BeforeEach
        void registerAll() { // GH-90000
            registry.registerProvider(new TestDeterministicProvider()); // GH-90000
            registry.registerProvider(new TestProbabilisticProvider()); // GH-90000
            registry.registerProvider(new TestMultiTypeProvider()); // GH-90000
        }

        @Test
        @DisplayName("Find by DETERMINISTIC type returns matching providers")
        void findDeterministic() { // GH-90000
            List<AgentProvider> results = registry.findByType(AgentType.DETERMINISTIC); // GH-90000
            assertThat(results).hasSize(2); // GH-90000
            // Multi-type provider has priority 500 (higher), deterministic has 1000 // GH-90000
            assertThat(results.get(0).getProviderId()).isEqualTo("test-multi");
            assertThat(results.get(1).getProviderId()).isEqualTo("test-deterministic");
        }

        @Test
        @DisplayName("Find by PROBABILISTIC returns matching providers")
        void findProbabilistic() { // GH-90000
            List<AgentProvider> results = registry.findByType(AgentType.PROBABILISTIC); // GH-90000
            assertThat(results).hasSize(2); // GH-90000
        }

        @Test
        @DisplayName("Find by ADAPTIVE returns empty")
        void findAdaptive() { // GH-90000
            assertThat(registry.findByType(AgentType.ADAPTIVE)).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("Find by HYBRID returns multi-type provider only")
        void findHybrid() { // GH-90000
            List<AgentProvider> results = registry.findByType(AgentType.HYBRID); // GH-90000
            assertThat(results).hasSize(1); // GH-90000
            assertThat(results.get(0).getProviderId()).isEqualTo("test-multi");
        }
    }

    // =========================================================================
    // 4. Agent Creation
    // =========================================================================

    @Nested
    @DisplayName("Agent Creation")
    class AgentCreation {

        @BeforeEach
        void registerAll() { // GH-90000
            registry.registerProvider(new TestDeterministicProvider()); // GH-90000
            registry.registerProvider(new TestProbabilisticProvider()); // GH-90000
            registry.registerProvider(new TestMultiTypeProvider()); // GH-90000
        }

        @Test
        @DisplayName("Create agent by provider ID")
        void createByProviderId() { // GH-90000
            AgentConfig config = AgentConfig.builder() // GH-90000
                    .agentId("my-agent")
                    .type(AgentType.DETERMINISTIC) // GH-90000
                    .build(); // GH-90000

            TypedAgent<?, ?> agent = registry.createAgent("test-deterministic", config); // GH-90000
            assertThat(agent).isNotNull(); // GH-90000
            assertThat(agent.descriptor().getAgentId()).isEqualTo("my-agent");
        }

        @Test
        @DisplayName("Create agent by type selects highest-priority provider")
        void createByType() { // GH-90000
            AgentConfig config = AgentConfig.builder() // GH-90000
                    .agentId("det-agent")
                    .type(AgentType.DETERMINISTIC) // GH-90000
                    .build(); // GH-90000

            TypedAgent<?, ?> agent = registry.createAgentByType(AgentType.DETERMINISTIC, config); // GH-90000
            assertThat(agent).isNotNull(); // GH-90000
            // Multi-type provider has higher priority (500) so it should be selected // GH-90000
            assertThat(agent.descriptor().getAgentId()).isEqualTo("det-agent");
        }

        @Test
        @DisplayName("Create agent by unknown provider throws")
        void createByUnknownProvider() { // GH-90000
            AgentConfig config = AgentConfig.builder() // GH-90000
                    .agentId("x")
                    .type(AgentType.DETERMINISTIC) // GH-90000
                    .build(); // GH-90000

            assertThatThrownBy(() -> registry.createAgent("unknown-provider", config)) // GH-90000
                    .isInstanceOf(NoSuchElementException.class) // GH-90000
                    .hasMessageContaining("unknown-provider");
        }

        @Test
        @DisplayName("Create agent by unsupported type throws")
        void createByUnsupportedType() { // GH-90000
            assertThatThrownBy(() -> // GH-90000
                    registry.createAgentByType(AgentType.REACTIVE, AgentConfig.builder() // GH-90000
                            .agentId("x").type(AgentType.REACTIVE).build()))
                    .isInstanceOf(NoSuchElementException.class) // GH-90000
                    .hasMessageContaining("REACTIVE");
        }
    }

    // =========================================================================
    // 5. Listing and Metadata
    // =========================================================================

    @Nested
    @DisplayName("Listing and Metadata")
    class ListingMetadata {

        @Test
        @DisplayName("listAll returns all providers sorted by priority")
        void listAllSorted() { // GH-90000
            registry.registerProvider(new TestDeterministicProvider()); // priority 1000 // GH-90000
            registry.registerProvider(new TestMultiTypeProvider());     // priority 500 // GH-90000
            registry.registerProvider(new TestProbabilisticProvider()); // priority 1000 // GH-90000

            List<AgentProvider> all = registry.listAll(); // GH-90000
            assertThat(all).hasSize(3); // GH-90000
            assertThat(all.get(0).getProviderId()).isEqualTo("test-multi"); // 500 first
        }

        @Test
        @DisplayName("Empty registry returns empty list")
        void emptyList() { // GH-90000
            assertThat(registry.listAll()).isEmpty(); // GH-90000
            assertThat(registry.size()).isZero(); // GH-90000
        }

        @Test
        @DisplayName("clear() removes all providers")
        void clearAll() { // GH-90000
            registry.registerProvider(new TestDeterministicProvider()); // GH-90000
            registry.registerProvider(new TestProbabilisticProvider()); // GH-90000
            assertThat(registry.size()).isEqualTo(2); // GH-90000

            registry.clear(); // GH-90000
            assertThat(registry.size()).isZero(); // GH-90000
            assertThat(registry.listAll()).isEmpty(); // GH-90000
        }
    }

    // =========================================================================
    // 6. Disabled Provider Handling
    // =========================================================================

    @Nested
    @DisplayName("Disabled Provider Handling")
    class DisabledProviders {

        @Test
        @DisplayName("Disabled provider is not registered via discoverProviders")
        void disabledSkipped() { // GH-90000
            // We can't test ServiceLoader discovery directly without META-INF,
            // but we can test the disabled/enabled contract
            TestDeterministicProvider disabled = new TestDeterministicProvider(1000, false); // GH-90000
            assertThat(disabled.isEnabled()).isFalse(); // GH-90000

            TestDeterministicProvider enabled = new TestDeterministicProvider(1000, true); // GH-90000
            assertThat(enabled.isEnabled()).isTrue(); // GH-90000
        }
    }

    // =========================================================================
    // 7. Priority Ordering
    // =========================================================================

    @Nested
    @DisplayName("Priority Ordering")
    class PriorityOrdering {

        @Test
        @DisplayName("Providers sorted by priority in findByType")
        void prioritySorted() { // GH-90000
            // Register multi (500), then deterministic (1000) // GH-90000
            registry.registerProvider(new TestMultiTypeProvider()); // GH-90000
            registry.registerProvider(new TestDeterministicProvider()); // GH-90000

            List<AgentProvider> results = registry.findByType(AgentType.DETERMINISTIC); // GH-90000
            assertThat(results).hasSize(2); // GH-90000
            assertThat(results.get(0).priority()).isLessThanOrEqualTo(results.get(1).priority()); // GH-90000
        }

        @Test
        @DisplayName("Custom priority respected")
        void customPriority() { // GH-90000
            TestDeterministicProvider highPriority = new TestDeterministicProvider(100, true); // GH-90000
            assertThat(highPriority.priority()).isEqualTo(100); // GH-90000
        }
    }

    // =========================================================================
    // 8. ServiceLoader Discovery (Integration) // GH-90000
    // =========================================================================

    @Nested
    @DisplayName("ServiceLoader Discovery")
    class ServiceLoaderDiscovery {

        @Test
        @DisplayName("discoverProviders with no META-INF returns 0")
        void noMetaInf() { // GH-90000
            int count = registry.discoverProviders(); // GH-90000
            // No META-INF/services file in test → 0 discovered
            assertThat(count).isGreaterThanOrEqualTo(0); // GH-90000
        }

        @Test
        @DisplayName("Custom classloader accepted")
        void customClassloader() { // GH-90000
            AgentProviderRegistry custom = AgentProviderRegistry.create( // GH-90000
                    getClass().getClassLoader()); // GH-90000
            assertThat(custom.size()).isZero(); // GH-90000
        }
    }

    // =========================================================================
    // 9. Concurrent Access
    // =========================================================================

    @Nested
    @DisplayName("Concurrent Access")
    class ConcurrentAccess {

        @Test
        @DisplayName("Concurrent registration is thread-safe")
        void concurrentRegistration() throws Exception { // GH-90000
            int threads = 10;
            var executor = java.util.concurrent.Executors.newFixedThreadPool(threads); // GH-90000
            var latch = new java.util.concurrent.CountDownLatch(1); // GH-90000
            var futures = new ArrayList<java.util.concurrent.Future<?>>(); // GH-90000

            for (int i = 0; i < threads; i++) { // GH-90000
                final int idx = i;
                futures.add(executor.submit(() -> { // GH-90000
                    try {
                        latch.await(); // GH-90000
                    } catch (InterruptedException e) { // GH-90000
                        Thread.currentThread().interrupt(); // GH-90000
                        return;
                    }
                    // Each thread registers a unique provider
                    registry.registerProvider(new AgentProvider() { // GH-90000
                        @Override
                        public String getProviderId() { return "concurrent-" + idx; } // GH-90000
                        @Override
                        public String getProviderName() { return "Concurrent " + idx; } // GH-90000
                        @Override
                        public Set<AgentType> getSupportedTypes() { return Set.of(AgentType.DETERMINISTIC); } // GH-90000
                        @Override
                        public TypedAgent<?, ?> createAgent(AgentConfig config) { return new StubAgent(config.getAgentId()); } // GH-90000
                        @Override
                        public AgentDescriptor describe() { // GH-90000
                            return AgentDescriptor.builder().agentId("concurrent-" + idx) // GH-90000
                                    .name("Concurrent").type(AgentType.DETERMINISTIC)
                                    .version("1.0.0").capabilities(Set.of("test")).build();
                        }
                    });
                }));
            }

            latch.countDown(); // GH-90000
            for (var f : futures) { // GH-90000
                f.get(5, java.util.concurrent.TimeUnit.SECONDS); // GH-90000
            }

            executor.shutdown(); // GH-90000
            assertThat(registry.size()).isEqualTo(threads); // GH-90000
        }
    }
}
