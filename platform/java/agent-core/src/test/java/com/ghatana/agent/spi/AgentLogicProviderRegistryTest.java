package com.ghatana.agent.spi;

import com.ghatana.agent.AgentConfig;
import com.ghatana.agent.AgentType;
import com.ghatana.agent.TypedAgent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link AgentLogicProviderRegistry} implementationRef resolution.
 *
 * @doc.type class
 * @doc.purpose Verify provider resolution logic
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("AgentLogicProviderRegistry — Provider Resolution [GH-90000]")
class AgentLogicProviderRegistryTest {

    private AgentLogicProviderRegistry registry;

    @BeforeEach
    void setUp() { // GH-90000
        registry = AgentLogicProviderRegistry.create(); // GH-90000
    }

    // ── Stub Helpers ─────────────────────────────────────────────────────

    /** Minimal stub provider for test purposes. */
    static class StubProvider implements AgentLogicProvider {
        private final String id;
        private final Set<String> supportedRefs;
        private final int prio;
        private final boolean enabled;

        StubProvider(String id, Set<String> supportedRefs, int priority, boolean enabled) { // GH-90000
            this.id = id;
            this.supportedRefs = supportedRefs;
            this.prio = priority;
            this.enabled = enabled;
        }

        StubProvider(String id, Set<String> supportedRefs) { // GH-90000
            this(id, supportedRefs, 1000, true); // GH-90000
        }

        @Override public String getProviderId() { return id; } // GH-90000
        @Override public String getProviderName() { return id + " provider"; } // GH-90000
        @Override public Set<String> getSupportedRefs() { return supportedRefs; } // GH-90000
        @Override public int priority() { return prio; } // GH-90000
        @Override public boolean isEnabled() { return enabled; } // GH-90000

        @Override
        public TypedAgent<?, ?> createAgent(String implementationRef, AgentConfig config) { // GH-90000
            // Return null — we only test resolution, not agent creation
            return null;
        }
    }

    private static AgentConfig dummyConfig() { // GH-90000
        return AgentConfig.builder() // GH-90000
                .agentId("test-agent [GH-90000]")
                .type(AgentType.DETERMINISTIC) // GH-90000
                .implementationRef("stub:test-agent [GH-90000]")
                .build(); // GH-90000
    }

    // ── Tests ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("resolve() [GH-90000]")
    class Resolve {

        @Test
        @DisplayName("resolves by provider-id prefix (fast path) [GH-90000]")
        void resolvesByPrefix() { // GH-90000
            var provider = new StubProvider("yappc-java", Set.of()); // GH-90000
            registry.register(provider); // GH-90000

            var result = registry.resolve("yappc-java:agent.java-expert [GH-90000]");
            assertThat(result).isPresent(); // GH-90000
            assertThat(result.get().getProviderId()).isEqualTo("yappc-java [GH-90000]");
        }

        @Test
        @DisplayName("resolves by supported-ref set (slow path) [GH-90000]")
        void resolvesBySupportedRefSet() { // GH-90000
            var provider = new StubProvider("custom", Set.of("my-special-agent [GH-90000]"));
            registry.register(provider); // GH-90000

            var result = registry.resolve("my-special-agent [GH-90000]");
            assertThat(result).isPresent(); // GH-90000
            assertThat(result.get().getProviderId()).isEqualTo("custom [GH-90000]");
        }

        @Test
        @DisplayName("returns empty for unknown ref [GH-90000]")
        void returnsEmptyForUnknownRef() { // GH-90000
            var provider = new StubProvider("known-provider", Set.of("known-ref [GH-90000]"));
            registry.register(provider); // GH-90000

            assertThat(registry.resolve("unknown-provider:agent [GH-90000]")).isEmpty();
        }

        @Test
        @DisplayName("returns empty for null ref [GH-90000]")
        void returnsEmptyForNull() { // GH-90000
            assertThat(registry.resolve(null)).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("returns empty for blank ref [GH-90000]")
        void returnsEmptyForBlank() { // GH-90000
            assertThat(registry.resolve("   [GH-90000]")).isEmpty();
        }

        @Test
        @DisplayName("selects lower-priority provider when multiple match [GH-90000]")
        void selectsLowerPriorityProvider() { // GH-90000
            var lowPrio = new StubProvider("aep", Set.of("shared-agent [GH-90000]"), 100, true);
            var highPrio = new StubProvider("aep-premium", Set.of("shared-agent [GH-90000]"), 500, true);
            registry.register(lowPrio); // GH-90000
            registry.register(highPrio); // GH-90000

            // "shared-agent" has no colon prefix → slow path, both match
            var result = registry.resolve("shared-agent [GH-90000]");
            assertThat(result).isPresent(); // GH-90000
            assertThat(result.get().getProviderId()).isEqualTo("aep [GH-90000]"); // priority 100 < 500
        }
    }

    @Nested
    @DisplayName("register() [GH-90000]")
    class Register {

        @Test
        @DisplayName("replaces same-id provider when priority is lower [GH-90000]")
        void replacesWhenLowerPriority() { // GH-90000
            var original = new StubProvider("test", Set.of(), 500, true); // GH-90000
            var replacement = new StubProvider("test", Set.of(), 100, true); // GH-90000
            registry.register(original); // GH-90000
            registry.register(replacement); // GH-90000

            assertThat(registry.getProviderIds()).containsExactly("test [GH-90000]");
            // Replacement should win
            var resolved = registry.resolve("test:any-agent [GH-90000]");
            assertThat(resolved).isPresent(); // GH-90000
            assertThat(resolved.get().priority()).isEqualTo(100); // GH-90000
        }

        @Test
        @DisplayName("keeps existing provider when same-id has higher priority [GH-90000]")
        void keepsWhenHigherPriority() { // GH-90000
            var original = new StubProvider("test", Set.of(), 100, true); // GH-90000
            var incoming = new StubProvider("test", Set.of(), 500, true); // GH-90000
            registry.register(original); // GH-90000
            registry.register(incoming); // GH-90000

            var resolved = registry.resolve("test:any-agent [GH-90000]");
            assertThat(resolved).isPresent(); // GH-90000
            assertThat(resolved.get().priority()).isEqualTo(100); // GH-90000
        }
    }

    @Nested
    @DisplayName("createAgent() [GH-90000]")
    class CreateAgent {

        @Test
        @DisplayName("throws for unresolvable ref [GH-90000]")
        void throwsForUnresolvableRef() { // GH-90000
            assertThatIllegalArgumentException() // GH-90000
                    .isThrownBy(() -> registry.createAgent("nonexistent:agent", dummyConfig())) // GH-90000
                    .withMessageContaining("No AgentLogicProvider found [GH-90000]");
        }

        @Test
        @DisplayName("delegates to resolved provider [GH-90000]")
        void delegatesToProvider() { // GH-90000
            var provider = new StubProvider("stub", Set.of()); // GH-90000
            registry.register(provider); // GH-90000

            // StubProvider.createAgent returns null — just verify no exception
            var agent = registry.createAgent("stub:test-agent", dummyConfig()); // GH-90000
            assertThat(agent).isNull(); // GH-90000
        }
    }

    @Nested
    @DisplayName("getProviderIds() [GH-90000]")
    class GetProviderIds {

        @Test
        @DisplayName("returns empty set for empty registry [GH-90000]")
        void emptyRegistry() { // GH-90000
            assertThat(registry.getProviderIds()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("returns all registered provider IDs [GH-90000]")
        void returnsAllIds() { // GH-90000
            registry.register(new StubProvider("alpha", Set.of())); // GH-90000
            registry.register(new StubProvider("beta", Set.of())); // GH-90000
            registry.register(new StubProvider("gamma", Set.of())); // GH-90000

            assertThat(registry.getProviderIds()).containsExactlyInAnyOrder("alpha", "beta", "gamma"); // GH-90000
        }
    }
}
