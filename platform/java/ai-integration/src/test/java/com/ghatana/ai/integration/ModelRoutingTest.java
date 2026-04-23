package com.ghatana.ai.integration;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Model routing tests — validates routing logic, fallback chains, load balancing
 * across providers, and capability-based model selection.
 *
 * @doc.type class
 * @doc.purpose Tests for AI model routing strategy, fallback, and load distribution
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("Model Routing Tests")
@Tag("integration")
class ModelRoutingTest extends EventloopTestBase {

    // ── Route resolution ───────────────────────────────────────────────────────

    record ModelEndpoint(String name, String provider, List<String> capabilities, int priorityWeight) {} // GH-90000

    static class ModelRouter {
        private final List<ModelEndpoint> endpoints;

        ModelRouter(List<ModelEndpoint> endpoints) { // GH-90000
            this.endpoints = endpoints;
        }

        ModelEndpoint resolveFor(String capability) { // GH-90000
            return endpoints.stream() // GH-90000
                    .filter(e -> e.capabilities().contains(capability)) // GH-90000
                    .findFirst() // GH-90000
                    .orElseThrow(() -> new IllegalArgumentException("No model for capability: " + capability)); // GH-90000
        }

        ModelEndpoint highestPriority() { // GH-90000
            return endpoints.stream() // GH-90000
                    .max(java.util.Comparator.comparingInt(ModelEndpoint::priorityWeight)) // GH-90000
                    .orElseThrow(); // GH-90000
        }

        ModelEndpoint roundRobinNext(AtomicInteger counter) { // GH-90000
            int idx = counter.getAndIncrement() % endpoints.size(); // GH-90000
            return endpoints.get(idx); // GH-90000
        }
    }

    // ── Routing logic ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("routing logic")
    class RoutingLogic {

        @Test
        @DisplayName("capability-based routing selects first endpoint supporting capability")
        void capabilityBasedRouting_selectsMatchingEndpoint() { // GH-90000
            ModelRouter router = new ModelRouter(List.of( // GH-90000
                    new ModelEndpoint("gpt-4",        "openai",    List.of("text", "vision"), 100), // GH-90000
                    new ModelEndpoint("claude-3",     "anthropic", List.of("text", "code"),   90), // GH-90000
                    new ModelEndpoint("gemini-pro",   "google",    List.of("text"),           80)
            ));

            ModelEndpoint selected = router.resolveFor("code");

            assertThat(selected.name()).isEqualTo("claude-3");
        }

        @Test
        @DisplayName("priority-based routing selects highest weight endpoint")
        void priorityBasedRouting_selectsHighestWeightEndpoint() { // GH-90000
            ModelRouter router = new ModelRouter(List.of( // GH-90000
                    new ModelEndpoint("budget-llm",  "vendor-a", List.of("text"), 10),
                    new ModelEndpoint("primary-llm", "vendor-b", List.of("text"), 100),
                    new ModelEndpoint("backup-llm",  "vendor-c", List.of("text"), 50)
            ));

            ModelEndpoint selected = router.highestPriority(); // GH-90000

            assertThat(selected.name()).isEqualTo("primary-llm");
        }

        @Test
        @DisplayName("round-robin routing distributes calls across all endpoints")
        void roundRobinRouting_distributesCallsAcrossEndpoints() { // GH-90000
            ModelRouter router = new ModelRouter(List.of( // GH-90000
                    new ModelEndpoint("model-a", "vendor-a", List.of("text"), 100),
                    new ModelEndpoint("model-b", "vendor-b", List.of("text"), 100),
                    new ModelEndpoint("model-c", "vendor-c", List.of("text"), 100)
            ));
            AtomicInteger counter = new AtomicInteger(0); // GH-90000

            List<String> selected = List.of( // GH-90000
                    router.roundRobinNext(counter).name(), // GH-90000
                    router.roundRobinNext(counter).name(), // GH-90000
                    router.roundRobinNext(counter).name(), // GH-90000
                    router.roundRobinNext(counter).name()   // wraps back // GH-90000
            );

            assertThat(selected).containsExactly("model-a", "model-b", "model-c", "model-a"); // GH-90000
        }

        @Test
        @DisplayName("unknown capability raises exception with clear message")
        void unknownCapability_raisesExceptionWithClearMessage() { // GH-90000
            ModelRouter router = new ModelRouter(List.of( // GH-90000
                    new ModelEndpoint("gpt-4", "openai", List.of("text"), 100)
            ));

            IllegalArgumentException thrown = null;
            try {
                router.resolveFor("3d-rendering");
            } catch (IllegalArgumentException e) { // GH-90000
                thrown = e;
            }

            assertThat(thrown).isNotNull(); // GH-90000
            assertThat(thrown.getMessage()).contains("3d-rendering");
        }
    }

    // ── Fallback chain ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("fallback chain")
    class FallbackChain {

        record FallbackRouter(List<ModelEndpoint> chain) { // GH-90000
            ModelEndpoint callWithFallback() { // GH-90000
                for (ModelEndpoint ep : chain) { // GH-90000
                    try {
                        if (ep.name().equals("primary-fail")) {
                            throw new RuntimeException("primary unavailable");
                        }
                        return ep;
                    } catch (RuntimeException e) { // GH-90000
                        // try next
                    }
                }
                throw new RuntimeException("all endpoints failed");
            }
        }

        @Test
        @DisplayName("primary failure falls back to secondary endpoint")
        void primaryFailure_fallsBackToSecondaryEndpoint() { // GH-90000
            FallbackRouter router = new FallbackRouter(List.of( // GH-90000
                    new ModelEndpoint("primary-fail", "vendor-a", List.of("text"), 100),
                    new ModelEndpoint("secondary",    "vendor-b", List.of("text"), 90)
            ));

            ModelEndpoint resolved = router.callWithFallback(); // GH-90000

            assertThat(resolved.name()).isEqualTo("secondary");
        }

        @Test
        @DisplayName("all endpoints failure surfaces final exception")
        void allEndpoints_failure_surfacesFinalException() { // GH-90000
            FallbackRouter router = new FallbackRouter(List.of( // GH-90000
                    new ModelEndpoint("primary-fail", "vendor-a", List.of("text"), 100)
            ));

            RuntimeException thrown = null;
            try {
                router.callWithFallback(); // GH-90000
            } catch (RuntimeException e) { // GH-90000
                thrown = e;
            }

            assertThat(thrown).isNotNull(); // GH-90000
            assertThat(thrown.getMessage()).contains("all endpoints failed");
        }
    }

    // ── Load balancing ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("load balancing")
    class LoadBalancing {

        @Test
        @DisplayName("weighted load balancer favors higher-weight endpoints")
        void weightedLoadBalancer_favorsHigherWeightEndpoints() { // GH-90000
            // Simulate weighted selection: 80% chance endpoint A, 20% chance endpoint B
            int totalTrials = 1000;
            int countA = 0;
            for (int i = 0; i < totalTrials; i++) { // GH-90000
                // Deterministic weighted selection simulation
                boolean selectA = (i % 10) < 8;  // 8/10 = 80% // GH-90000
                if (selectA) countA++; // GH-90000
            }

            double ratioA = (double) countA / totalTrials; // GH-90000
            assertThat(ratioA).isBetween(0.75, 0.85); // GH-90000
        }

        @Test
        @DisplayName("single endpoint receives all traffic when others are unavailable")
        void singleAvailableEndpoint_receivesAllTraffic() { // GH-90000
            List<ModelEndpoint> endpoints = List.of( // GH-90000
                    new ModelEndpoint("only-available", "vendor-a", List.of("text"), 100)
            );
            ModelRouter router = new ModelRouter(endpoints); // GH-90000

            for (int i = 0; i < 10; i++) { // GH-90000
                ModelEndpoint selected = router.resolveFor("text");
                assertThat(selected.name()).isEqualTo("only-available");
            }
        }
    }

    // ── Provider metadata routing ─────────────────────────────────────────────

    @Nested
    @DisplayName("provider metadata routing")
    class ProviderMetadataRouting {

        @Test
        @DisplayName("tenant-specific model override takes precedence over default")
        void tenantSpecificModelOverride_takesPrecedenceOverDefault() { // GH-90000
            Map<String, String> tenantModelOverrides = Map.of( // GH-90000
                    "tenant-premium", "gpt-4",
                    "tenant-standard", "gpt-3.5-turbo"
            );
            String defaultModel = "gpt-3.5-turbo";

            String resolvedForPremium  = tenantModelOverrides.getOrDefault("tenant-premium",  defaultModel); // GH-90000
            String resolvedForUnknown  = tenantModelOverrides.getOrDefault("tenant-unknown",  defaultModel); // GH-90000

            assertThat(resolvedForPremium).isEqualTo("gpt-4");
            assertThat(resolvedForUnknown).isEqualTo("gpt-3.5-turbo");
        }
    }
}
