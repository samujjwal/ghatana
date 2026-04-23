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
@DisplayName("AgentLogicProviderRegistry — Provider Resolution")
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
                .agentId("test-agent")
                .type(AgentType.DETERMINISTIC) // GH-90000
                .implementationRef("stub:test-agent")
                .build(); // GH-90000
    }

    // ── Tests ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("resolve()")
    class Resolve {

        @Test
        @DisplayName("resolves by provider-id prefix (fast path)")
        void resolvesByPrefix() { // GH-90000
            var provider = new StubProvider("yappc-java", Set.of()); // GH-90000
            registry.register(provider); // GH-90000

            var result = registry.resolve("yappc-java:agent.java-expert");
            assertThat(result).isPresent(); // GH-90000
            assertThat(result.get().getProviderId()).isEqualTo("yappc-java");
        }

        @Test
        @DisplayName("resolves by supported-ref set (slow path)")
        void resolvesBySupportedRefSet() { // GH-90000
            var provider = new StubProvider("custom", Set.of("my-special-agent"));
            registry.register(provider); // GH-90000

            var result = registry.resolve("my-special-agent");
            assertThat(result).isPresent(); // GH-90000
            assertThat(result.get().getProviderId()).isEqualTo("custom");
        }

        @Test
        @DisplayName("returns empty for unknown ref")
        void returnsEmptyForUnknownRef() { // GH-90000
            var provider = new StubProvider("known-provider", Set.of("known-ref"));
            registry.register(provider); // GH-90000

            assertThat(registry.resolve("unknown-provider:agent")).isEmpty();
        }

        @Test
        @DisplayName("returns empty for null ref")
        void returnsEmptyForNull() { // GH-90000
            assertThat(registry.resolve(null)).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("returns empty for blank ref")
        void returnsEmptyForBlank() { // GH-90000
            assertThat(registry.resolve("  ")).isEmpty();
        }

        @Test
        @DisplayName("selects lower-priority provider when multiple match")
        void selectsLowerPriorityProvider() { // GH-90000
            var lowPrio = new StubProvider("aep", Set.of("shared-agent"), 100, true);
            var highPrio = new StubProvider("aep-premium", Set.of("shared-agent"), 500, true);
            registry.register(lowPrio); // GH-90000
            registry.register(highPrio); // GH-90000

            // "shared-agent" has no colon prefix → slow path, both match
            var result = registry.resolve("shared-agent");
            assertThat(result).isPresent(); // GH-90000
            assertThat(result.get().getProviderId()).isEqualTo("aep"); // priority 100 < 500
        }
    }

    @Nested
    @DisplayName("register()")
    class Register {

        @Test
        @DisplayName("replaces same-id provider when priority is lower")
        void replacesWhenLowerPriority() { // GH-90000
            var original = new StubProvider("test", Set.of(), 500, true); // GH-90000
            var replacement = new StubProvider("test", Set.of(), 100, true); // GH-90000
            registry.register(original); // GH-90000
            registry.register(replacement); // GH-90000

            assertThat(registry.getProviderIds()).containsExactly("test");
            // Replacement should win
            var resolved = registry.resolve("test:any-agent");
            assertThat(resolved).isPresent(); // GH-90000
            assertThat(resolved.get().priority()).isEqualTo(100); // GH-90000
        }

        @Test
        @DisplayName("keeps existing provider when same-id has higher priority")
        void keepsWhenHigherPriority() { // GH-90000
            var original = new StubProvider("test", Set.of(), 100, true); // GH-90000
            var incoming = new StubProvider("test", Set.of(), 500, true); // GH-90000
            registry.register(original); // GH-90000
            registry.register(incoming); // GH-90000

            var resolved = registry.resolve("test:any-agent");
            assertThat(resolved).isPresent(); // GH-90000
            assertThat(resolved.get().priority()).isEqualTo(100); // GH-90000
        }
    }

    @Nested
    @DisplayName("createAgent()")
    class CreateAgent {

        @Test
        @DisplayName("throws for unresolvable ref")
        void throwsForUnresolvableRef() { // GH-90000
            assertThatIllegalArgumentException() // GH-90000
                    .isThrownBy(() -> registry.createAgent("nonexistent:agent", dummyConfig())) // GH-90000
                    .withMessageContaining("No AgentLogicProvider found");
        }

        @Test
        @DisplayName("delegates to resolved provider")
        void delegatesToProvider() { // GH-90000
            var provider = new StubProvider("stub", Set.of()); // GH-90000
            registry.register(provider); // GH-90000

            // StubProvider.createAgent returns null — just verify no exception
            var agent = registry.createAgent("stub:test-agent", dummyConfig()); // GH-90000
            assertThat(agent).isNull(); // GH-90000
        }
    }

    @Nested
    @DisplayName("getProviderIds()")
    class GetProviderIds {

        @Test
        @DisplayName("returns empty set for empty registry")
        void emptyRegistry() { // GH-90000
            assertThat(registry.getProviderIds()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("returns all registered provider IDs")
        void returnsAllIds() { // GH-90000
            registry.register(new StubProvider("alpha", Set.of())); // GH-90000
            registry.register(new StubProvider("beta", Set.of())); // GH-90000
            registry.register(new StubProvider("gamma", Set.of())); // GH-90000

            assertThat(registry.getProviderIds()).containsExactlyInAnyOrder("alpha", "beta", "gamma"); // GH-90000
        }
    }
}
