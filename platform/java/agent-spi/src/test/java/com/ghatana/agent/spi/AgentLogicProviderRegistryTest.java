package com.ghatana.agent.spi;

import com.ghatana.agent.api.AgentConfig;
import com.ghatana.agent.api.AgentType;
import com.ghatana.agent.api.TypedAgent;
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
    void setUp() {
        registry = AgentLogicProviderRegistry.create();
    }

    // ── Stub Helpers ─────────────────────────────────────────────────────

    /** Minimal stub provider for test purposes. */
    static class StubProvider implements AgentLogicProvider {
        private final String id;
        private final Set<String> supportedRefs;
        private final int prio;
        private final boolean enabled;

        StubProvider(String id, Set<String> supportedRefs, int priority, boolean enabled) {
            this.id = id;
            this.supportedRefs = supportedRefs;
            this.prio = priority;
            this.enabled = enabled;
        }

        StubProvider(String id, Set<String> supportedRefs) {
            this(id, supportedRefs, 1000, true);
        }

        @Override public String getProviderId() { return id; }
        @Override public String getProviderName() { return id + " provider"; }
        @Override public Set<String> getSupportedRefs() { return supportedRefs; }
        @Override public int priority() { return prio; }
        @Override public boolean isEnabled() { return enabled; }

        @Override
        public TypedAgent<?, ?> createAgent(String implementationRef, AgentConfig config) {
            // Return null — we only test resolution, not agent creation
            return null;
        }
    }

    private static AgentConfig dummyConfig() {
        return AgentConfig.builder()
                .agentId("test-agent")
                .type(AgentType.DETERMINISTIC)
                .implementationRef("stub:test-agent")
                .build();
    }

    // ── Tests ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("resolve()")
    class Resolve {

        @Test
        @DisplayName("resolves by provider-id prefix (fast path)")
        void resolvesByPrefix() {
            var provider = new StubProvider("yappc-java", Set.of());
            registry.register(provider);

            var result = registry.resolve("yappc-java:agent.java-expert");
            assertThat(result).isPresent();
            assertThat(result.get().getProviderId()).isEqualTo("yappc-java");
        }

        @Test
        @DisplayName("resolves by supported-ref set (slow path)")
        void resolvesBySupportedRefSet() {
            var provider = new StubProvider("custom", Set.of("my-special-agent"));
            registry.register(provider);

            var result = registry.resolve("my-special-agent");
            assertThat(result).isPresent();
            assertThat(result.get().getProviderId()).isEqualTo("custom");
        }

        @Test
        @DisplayName("returns empty for unknown ref")
        void returnsEmptyForUnknownRef() {
            var provider = new StubProvider("known-provider", Set.of("known-ref"));
            registry.register(provider);

            assertThat(registry.resolve("unknown-provider:agent")).isEmpty();
        }

        @Test
        @DisplayName("returns empty for null ref")
        void returnsEmptyForNull() {
            assertThat(registry.resolve(null)).isEmpty();
        }

        @Test
        @DisplayName("returns empty for blank ref")
        void returnsEmptyForBlank() {
            assertThat(registry.resolve("  ")).isEmpty();
        }

        @Test
        @DisplayName("selects lower-priority provider when multiple match")
        void selectsLowerPriorityProvider() {
            var lowPrio = new StubProvider("aep", Set.of("shared-agent"), 100, true);
            var highPrio = new StubProvider("aep-premium", Set.of("shared-agent"), 500, true);
            registry.register(lowPrio);
            registry.register(highPrio);

            // "shared-agent" has no colon prefix → slow path, both match
            var result = registry.resolve("shared-agent");
            assertThat(result).isPresent();
            assertThat(result.get().getProviderId()).isEqualTo("aep"); // priority 100 < 500
        }
    }

    @Nested
    @DisplayName("register()")
    class Register {

        @Test
        @DisplayName("replaces same-id provider when priority is lower")
        void replacesWhenLowerPriority() {
            var original = new StubProvider("test", Set.of(), 500, true);
            var replacement = new StubProvider("test", Set.of(), 100, true);
            registry.register(original);
            registry.register(replacement);

            assertThat(registry.getProviderIds()).containsExactly("test");
            // Replacement should win
            var resolved = registry.resolve("test:any-agent");
            assertThat(resolved).isPresent();
            assertThat(resolved.get().priority()).isEqualTo(100);
        }

        @Test
        @DisplayName("keeps existing provider when same-id has higher priority")
        void keepsWhenHigherPriority() {
            var original = new StubProvider("test", Set.of(), 100, true);
            var incoming = new StubProvider("test", Set.of(), 500, true);
            registry.register(original);
            registry.register(incoming);

            var resolved = registry.resolve("test:any-agent");
            assertThat(resolved).isPresent();
            assertThat(resolved.get().priority()).isEqualTo(100);
        }
    }

    @Nested
    @DisplayName("createAgent()")
    class CreateAgent {

        @Test
        @DisplayName("throws for unresolvable ref")
        void throwsForUnresolvableRef() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> registry.createAgent("nonexistent:agent", dummyConfig()))
                    .withMessageContaining("No AgentLogicProvider found");
        }

        @Test
        @DisplayName("delegates to resolved provider")
        void delegatesToProvider() {
            var provider = new StubProvider("stub", Set.of());
            registry.register(provider);

            // StubProvider.createAgent returns null — just verify no exception
            var agent = registry.createAgent("stub:test-agent", dummyConfig());
            assertThat(agent).isNull();
        }
    }

    @Nested
    @DisplayName("getProviderIds()")
    class GetProviderIds {

        @Test
        @DisplayName("returns empty set for empty registry")
        void emptyRegistry() {
            assertThat(registry.getProviderIds()).isEmpty();
        }

        @Test
        @DisplayName("returns all registered provider IDs")
        void returnsAllIds() {
            registry.register(new StubProvider("alpha", Set.of()));
            registry.register(new StubProvider("beta", Set.of()));
            registry.register(new StubProvider("gamma", Set.of()));

            assertThat(registry.getProviderIds()).containsExactlyInAnyOrder("alpha", "beta", "gamma");
        }
    }
}
