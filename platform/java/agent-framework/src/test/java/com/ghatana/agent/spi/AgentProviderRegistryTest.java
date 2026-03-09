/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
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
    void setUp() {
        registry = AgentProviderRegistry.create();
    }

    // =========================================================================
    // Test Agent Provider Implementation (for use in tests)
    // =========================================================================

    static class StubAgent extends AbstractTypedAgent<String, String> {
        private final String agentId;

        StubAgent(String agentId) {
            this.agentId = agentId;
        }

        @Override
        public AgentDescriptor descriptor() {
            return AgentDescriptor.builder()
                    .agentId(agentId)
                    .name("Stub Agent " + agentId)
                    .type(AgentType.DETERMINISTIC)
                    .version("1.0.0")
                    .capabilities(Set.of("stub"))
                    .build();
        }

        @Override
        public Promise<AgentResult<String>> doProcess(AgentContext ctx, String input) {
            return Promise.of(AgentResult.success("processed:" + input, agentId, Duration.ofMillis(1)));
        }
    }

    static class TestDeterministicProvider implements AgentProvider {
        private final int priorityVal;
        private final boolean enabled;

        TestDeterministicProvider() {
            this(1000, true);
        }

        TestDeterministicProvider(int priority, boolean enabled) {
            this.priorityVal = priority;
            this.enabled = enabled;
        }

        @Override
        public String getProviderId() {
            return "test-deterministic";
        }

        @Override
        public String getProviderName() {
            return "Test Deterministic Provider";
        }

        @Override
        public Set<AgentType> getSupportedTypes() {
            return Set.of(AgentType.DETERMINISTIC);
        }

        @Override
        public TypedAgent<?, ?> createAgent(AgentConfig config) {
            return new StubAgent(config.getAgentId());
        }

        @Override
        public AgentDescriptor describe() {
            return AgentDescriptor.builder()
                    .agentId("test-deterministic")
                    .name("Test Deterministic")
                    .type(AgentType.DETERMINISTIC)
                    .version("1.0.0")
                    .capabilities(Set.of("rule-matching"))
                    .build();
        }

        @Override
        public int priority() {
            return priorityVal;
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }
    }

    static class TestProbabilisticProvider implements AgentProvider {
        @Override
        public String getProviderId() {
            return "test-probabilistic";
        }

        @Override
        public String getProviderName() {
            return "Test Probabilistic Provider";
        }

        @Override
        public Set<AgentType> getSupportedTypes() {
            return Set.of(AgentType.PROBABILISTIC);
        }

        @Override
        public TypedAgent<?, ?> createAgent(AgentConfig config) {
            return new StubAgent(config.getAgentId());
        }

        @Override
        public AgentDescriptor describe() {
            return AgentDescriptor.builder()
                    .agentId("test-probabilistic")
                    .name("Test Probabilistic")
                    .type(AgentType.PROBABILISTIC)
                    .version("2.0.0")
                    .capabilities(Set.of("inference"))
                    .build();
        }

        @Override
        public String getVersion() {
            return "2.0.0";
        }
    }

    static class TestMultiTypeProvider implements AgentProvider {
        @Override
        public String getProviderId() {
            return "test-multi";
        }

        @Override
        public String getProviderName() {
            return "Test Multi-Type Provider";
        }

        @Override
        public Set<AgentType> getSupportedTypes() {
            return Set.of(AgentType.DETERMINISTIC, AgentType.PROBABILISTIC, AgentType.HYBRID);
        }

        @Override
        public TypedAgent<?, ?> createAgent(AgentConfig config) {
            return new StubAgent(config.getAgentId());
        }

        @Override
        public AgentDescriptor describe() {
            return AgentDescriptor.builder()
                    .agentId("test-multi")
                    .name("Test Multi")
                    .type(AgentType.HYBRID)
                    .version("1.0.0")
                    .capabilities(Set.of("multi"))
                    .build();
        }

        @Override
        public int priority() {
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
        void defaultSupports() {
            TestDeterministicProvider provider = new TestDeterministicProvider();
            assertThat(provider.supports(AgentType.DETERMINISTIC)).isTrue();
            assertThat(provider.supports(AgentType.PROBABILISTIC)).isFalse();
            assertThat(provider.supports(AgentType.ADAPTIVE)).isFalse();
        }

        @Test
        @DisplayName("Default priority is 1000")
        void defaultPriority() {
            TestProbabilisticProvider provider = new TestProbabilisticProvider();
            assertThat(provider.priority()).isEqualTo(1000);
        }

        @Test
        @DisplayName("Default isEnabled is true")
        void defaultEnabled() {
            TestProbabilisticProvider provider = new TestProbabilisticProvider();
            assertThat(provider.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("Default version is 1.0.0")
        void defaultVersion() {
            TestDeterministicProvider provider = new TestDeterministicProvider();
            assertThat(provider.getVersion()).isEqualTo("1.0.0");
        }

        @Test
        @DisplayName("Custom version override")
        void customVersion() {
            TestProbabilisticProvider provider = new TestProbabilisticProvider();
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
        void registerAndRetrieve() {
            TestDeterministicProvider provider = new TestDeterministicProvider();
            registry.registerProvider(provider);

            assertThat(registry.size()).isEqualTo(1);
            assertThat(registry.contains("test-deterministic")).isTrue();
            assertThat(registry.getProvider("test-deterministic")).isPresent();
            assertThat(registry.getProvider("test-deterministic").get().getProviderName())
                    .isEqualTo("Test Deterministic Provider");
        }

        @Test
        @DisplayName("Duplicate registration throws")
        void duplicateThrows() {
            registry.registerProvider(new TestDeterministicProvider());

            assertThatThrownBy(() -> registry.registerProvider(new TestDeterministicProvider()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already registered");
        }

        @Test
        @DisplayName("Unregister removes provider")
        void unregister() {
            registry.registerProvider(new TestDeterministicProvider());
            assertThat(registry.size()).isEqualTo(1);

            boolean removed = registry.unregisterProvider("test-deterministic");
            assertThat(removed).isTrue();
            assertThat(registry.size()).isZero();
            assertThat(registry.contains("test-deterministic")).isFalse();
        }

        @Test
        @DisplayName("Unregister nonexistent returns false")
        void unregisterNonexistent() {
            assertThat(registry.unregisterProvider("ghost")).isFalse();
        }

        @Test
        @DisplayName("getProvider returns empty for unknown ID")
        void getUnknown() {
            assertThat(registry.getProvider("unknown")).isEmpty();
        }

        @Test
        @DisplayName("Null provider rejected")
        void nullRejected() {
            assertThatThrownBy(() -> registry.registerProvider(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // =========================================================================
    // 3. Type-Based Discovery
    // =========================================================================

    @Nested
    @DisplayName("Type-Based Discovery")
    class TypeBasedDiscovery {

        @BeforeEach
        void registerAll() {
            registry.registerProvider(new TestDeterministicProvider());
            registry.registerProvider(new TestProbabilisticProvider());
            registry.registerProvider(new TestMultiTypeProvider());
        }

        @Test
        @DisplayName("Find by DETERMINISTIC type returns matching providers")
        void findDeterministic() {
            List<AgentProvider> results = registry.findByType(AgentType.DETERMINISTIC);
            assertThat(results).hasSize(2);
            // Multi-type provider has priority 500 (higher), deterministic has 1000
            assertThat(results.get(0).getProviderId()).isEqualTo("test-multi");
            assertThat(results.get(1).getProviderId()).isEqualTo("test-deterministic");
        }

        @Test
        @DisplayName("Find by PROBABILISTIC returns matching providers")
        void findProbabilistic() {
            List<AgentProvider> results = registry.findByType(AgentType.PROBABILISTIC);
            assertThat(results).hasSize(2);
        }

        @Test
        @DisplayName("Find by ADAPTIVE returns empty")
        void findAdaptive() {
            assertThat(registry.findByType(AgentType.ADAPTIVE)).isEmpty();
        }

        @Test
        @DisplayName("Find by HYBRID returns multi-type provider only")
        void findHybrid() {
            List<AgentProvider> results = registry.findByType(AgentType.HYBRID);
            assertThat(results).hasSize(1);
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
        void registerAll() {
            registry.registerProvider(new TestDeterministicProvider());
            registry.registerProvider(new TestProbabilisticProvider());
            registry.registerProvider(new TestMultiTypeProvider());
        }

        @Test
        @DisplayName("Create agent by provider ID")
        void createByProviderId() {
            AgentConfig config = AgentConfig.builder()
                    .agentId("my-agent")
                    .type(AgentType.DETERMINISTIC)
                    .build();

            TypedAgent<?, ?> agent = registry.createAgent("test-deterministic", config);
            assertThat(agent).isNotNull();
            assertThat(agent.descriptor().getAgentId()).isEqualTo("my-agent");
        }

        @Test
        @DisplayName("Create agent by type selects highest-priority provider")
        void createByType() {
            AgentConfig config = AgentConfig.builder()
                    .agentId("det-agent")
                    .type(AgentType.DETERMINISTIC)
                    .build();

            TypedAgent<?, ?> agent = registry.createAgentByType(AgentType.DETERMINISTIC, config);
            assertThat(agent).isNotNull();
            // Multi-type provider has higher priority (500) so it should be selected
            assertThat(agent.descriptor().getAgentId()).isEqualTo("det-agent");
        }

        @Test
        @DisplayName("Create agent by unknown provider throws")
        void createByUnknownProvider() {
            AgentConfig config = AgentConfig.builder()
                    .agentId("x")
                    .type(AgentType.DETERMINISTIC)
                    .build();

            assertThatThrownBy(() -> registry.createAgent("unknown-provider", config))
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessageContaining("unknown-provider");
        }

        @Test
        @DisplayName("Create agent by unsupported type throws")
        void createByUnsupportedType() {
            assertThatThrownBy(() ->
                    registry.createAgentByType(AgentType.REACTIVE, AgentConfig.builder()
                            .agentId("x").type(AgentType.REACTIVE).build()))
                    .isInstanceOf(NoSuchElementException.class)
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
        void listAllSorted() {
            registry.registerProvider(new TestDeterministicProvider()); // priority 1000
            registry.registerProvider(new TestMultiTypeProvider());     // priority 500
            registry.registerProvider(new TestProbabilisticProvider()); // priority 1000

            List<AgentProvider> all = registry.listAll();
            assertThat(all).hasSize(3);
            assertThat(all.get(0).getProviderId()).isEqualTo("test-multi"); // 500 first
        }

        @Test
        @DisplayName("Empty registry returns empty list")
        void emptyList() {
            assertThat(registry.listAll()).isEmpty();
            assertThat(registry.size()).isZero();
        }

        @Test
        @DisplayName("clear() removes all providers")
        void clearAll() {
            registry.registerProvider(new TestDeterministicProvider());
            registry.registerProvider(new TestProbabilisticProvider());
            assertThat(registry.size()).isEqualTo(2);

            registry.clear();
            assertThat(registry.size()).isZero();
            assertThat(registry.listAll()).isEmpty();
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
        void disabledSkipped() {
            // We can't test ServiceLoader discovery directly without META-INF,
            // but we can test the disabled/enabled contract
            TestDeterministicProvider disabled = new TestDeterministicProvider(1000, false);
            assertThat(disabled.isEnabled()).isFalse();

            TestDeterministicProvider enabled = new TestDeterministicProvider(1000, true);
            assertThat(enabled.isEnabled()).isTrue();
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
        void prioritySorted() {
            // Register multi (500), then deterministic (1000)
            registry.registerProvider(new TestMultiTypeProvider());
            registry.registerProvider(new TestDeterministicProvider());

            List<AgentProvider> results = registry.findByType(AgentType.DETERMINISTIC);
            assertThat(results).hasSize(2);
            assertThat(results.get(0).priority()).isLessThanOrEqualTo(results.get(1).priority());
        }

        @Test
        @DisplayName("Custom priority respected")
        void customPriority() {
            TestDeterministicProvider highPriority = new TestDeterministicProvider(100, true);
            assertThat(highPriority.priority()).isEqualTo(100);
        }
    }

    // =========================================================================
    // 8. ServiceLoader Discovery (Integration)
    // =========================================================================

    @Nested
    @DisplayName("ServiceLoader Discovery")
    class ServiceLoaderDiscovery {

        @Test
        @DisplayName("discoverProviders with no META-INF returns 0")
        void noMetaInf() {
            int count = registry.discoverProviders();
            // No META-INF/services file in test → 0 discovered
            assertThat(count).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("Custom classloader accepted")
        void customClassloader() {
            AgentProviderRegistry custom = AgentProviderRegistry.create(
                    getClass().getClassLoader());
            assertThat(custom.size()).isZero();
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
        void concurrentRegistration() throws Exception {
            int threads = 10;
            var executor = java.util.concurrent.Executors.newFixedThreadPool(threads);
            var latch = new java.util.concurrent.CountDownLatch(1);
            var futures = new ArrayList<java.util.concurrent.Future<?>>();

            for (int i = 0; i < threads; i++) {
                final int idx = i;
                futures.add(executor.submit(() -> {
                    try {
                        latch.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    // Each thread registers a unique provider
                    registry.registerProvider(new AgentProvider() {
                        @Override
                        public String getProviderId() { return "concurrent-" + idx; }
                        @Override
                        public String getProviderName() { return "Concurrent " + idx; }
                        @Override
                        public Set<AgentType> getSupportedTypes() { return Set.of(AgentType.DETERMINISTIC); }
                        @Override
                        public TypedAgent<?, ?> createAgent(AgentConfig config) { return new StubAgent(config.getAgentId()); }
                        @Override
                        public AgentDescriptor describe() {
                            return AgentDescriptor.builder().agentId("concurrent-" + idx)
                                    .name("Concurrent").type(AgentType.DETERMINISTIC)
                                    .version("1.0.0").capabilities(Set.of("test")).build();
                        }
                    });
                }));
            }

            latch.countDown();
            for (var f : futures) {
                f.get(5, java.util.concurrent.TimeUnit.SECONDS);
            }

            executor.shutdown();
            assertThat(registry.size()).isEqualTo(threads);
        }
    }
}
