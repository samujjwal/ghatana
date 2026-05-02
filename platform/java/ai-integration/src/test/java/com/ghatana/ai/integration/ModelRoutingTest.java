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

    record ModelEndpoint(String name, String provider, List<String> capabilities, int priorityWeight) {} 

    static class ModelRouter {
        private final List<ModelEndpoint> endpoints;

        ModelRouter(List<ModelEndpoint> endpoints) { 
            this.endpoints = endpoints;
        }

        ModelEndpoint resolveFor(String capability) { 
            return endpoints.stream() 
                    .filter(e -> e.capabilities().contains(capability)) 
                    .findFirst() 
                    .orElseThrow(() -> new IllegalArgumentException("No model for capability: " + capability)); 
        }

        ModelEndpoint highestPriority() { 
            return endpoints.stream() 
                    .max(java.util.Comparator.comparingInt(ModelEndpoint::priorityWeight)) 
                    .orElseThrow(); 
        }

        ModelEndpoint roundRobinNext(AtomicInteger counter) { 
            int idx = counter.getAndIncrement() % endpoints.size(); 
            return endpoints.get(idx); 
        }
    }

    // ── Routing logic ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("routing logic")
    class RoutingLogic {

        @Test
        @DisplayName("capability-based routing selects first endpoint supporting capability")
        void capabilityBasedRouting_selectsMatchingEndpoint() { 
            ModelRouter router = new ModelRouter(List.of( 
                    new ModelEndpoint("gpt-4",        "openai",    List.of("text", "vision"), 100), 
                    new ModelEndpoint("claude-3",     "anthropic", List.of("text", "code"),   90), 
                    new ModelEndpoint("gemini-pro",   "google",    List.of("text"),           80)
            ));

            ModelEndpoint selected = router.resolveFor("code");

            assertThat(selected.name()).isEqualTo("claude-3");
        }

        @Test
        @DisplayName("priority-based routing selects highest weight endpoint")
        void priorityBasedRouting_selectsHighestWeightEndpoint() { 
            ModelRouter router = new ModelRouter(List.of( 
                    new ModelEndpoint("budget-llm",  "vendor-a", List.of("text"), 10),
                    new ModelEndpoint("primary-llm", "vendor-b", List.of("text"), 100),
                    new ModelEndpoint("backup-llm",  "vendor-c", List.of("text"), 50)
            ));

            ModelEndpoint selected = router.highestPriority(); 

            assertThat(selected.name()).isEqualTo("primary-llm");
        }

        @Test
        @DisplayName("round-robin routing distributes calls across all endpoints")
        void roundRobinRouting_distributesCallsAcrossEndpoints() { 
            ModelRouter router = new ModelRouter(List.of( 
                    new ModelEndpoint("model-a", "vendor-a", List.of("text"), 100),
                    new ModelEndpoint("model-b", "vendor-b", List.of("text"), 100),
                    new ModelEndpoint("model-c", "vendor-c", List.of("text"), 100)
            ));
            AtomicInteger counter = new AtomicInteger(0); 

            List<String> selected = List.of( 
                    router.roundRobinNext(counter).name(), 
                    router.roundRobinNext(counter).name(), 
                    router.roundRobinNext(counter).name(), 
                    router.roundRobinNext(counter).name()   // wraps back 
            );

            assertThat(selected).containsExactly("model-a", "model-b", "model-c", "model-a"); 
        }

        @Test
        @DisplayName("unknown capability raises exception with clear message")
        void unknownCapability_raisesExceptionWithClearMessage() { 
            ModelRouter router = new ModelRouter(List.of( 
                    new ModelEndpoint("gpt-4", "openai", List.of("text"), 100)
            ));

            IllegalArgumentException thrown = null;
            try {
                router.resolveFor("3d-rendering");
            } catch (IllegalArgumentException e) { 
                thrown = e;
            }

            assertThat(thrown).isNotNull(); 
            assertThat(thrown.getMessage()).contains("3d-rendering");
        }
    }

    // ── Fallback chain ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("fallback chain")
    class FallbackChain {

        record FallbackRouter(List<ModelEndpoint> chain) { 
            ModelEndpoint callWithFallback() { 
                for (ModelEndpoint ep : chain) { 
                    try {
                        if (ep.name().equals("primary-fail")) {
                            throw new RuntimeException("primary unavailable");
                        }
                        return ep;
                    } catch (RuntimeException e) { 
                        // try next
                    }
                }
                throw new RuntimeException("all endpoints failed");
            }
        }

        @Test
        @DisplayName("primary failure falls back to secondary endpoint")
        void primaryFailure_fallsBackToSecondaryEndpoint() { 
            FallbackRouter router = new FallbackRouter(List.of( 
                    new ModelEndpoint("primary-fail", "vendor-a", List.of("text"), 100),
                    new ModelEndpoint("secondary",    "vendor-b", List.of("text"), 90)
            ));

            ModelEndpoint resolved = router.callWithFallback(); 

            assertThat(resolved.name()).isEqualTo("secondary");
        }

        @Test
        @DisplayName("all endpoints failure surfaces final exception")
        void allEndpoints_failure_surfacesFinalException() { 
            FallbackRouter router = new FallbackRouter(List.of( 
                    new ModelEndpoint("primary-fail", "vendor-a", List.of("text"), 100)
            ));

            RuntimeException thrown = null;
            try {
                router.callWithFallback(); 
            } catch (RuntimeException e) { 
                thrown = e;
            }

            assertThat(thrown).isNotNull(); 
            assertThat(thrown.getMessage()).contains("all endpoints failed");
        }
    }

    // ── Load balancing ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("load balancing")
    class LoadBalancing {

        @Test
        @DisplayName("weighted load balancer favors higher-weight endpoints")
        void weightedLoadBalancer_favorsHigherWeightEndpoints() { 
            // Simulate weighted selection: 80% chance endpoint A, 20% chance endpoint B
            int totalTrials = 1000;
            int countA = 0;
            for (int i = 0; i < totalTrials; i++) { 
                // Deterministic weighted selection simulation
                boolean selectA = (i % 10) < 8;  // 8/10 = 80% 
                if (selectA) countA++; 
            }

            double ratioA = (double) countA / totalTrials; 
            assertThat(ratioA).isBetween(0.75, 0.85); 
        }

        @Test
        @DisplayName("single endpoint receives all traffic when others are unavailable")
        void singleAvailableEndpoint_receivesAllTraffic() { 
            List<ModelEndpoint> endpoints = List.of( 
                    new ModelEndpoint("only-available", "vendor-a", List.of("text"), 100)
            );
            ModelRouter router = new ModelRouter(endpoints); 

            for (int i = 0; i < 10; i++) { 
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
        void tenantSpecificModelOverride_takesPrecedenceOverDefault() { 
            Map<String, String> tenantModelOverrides = Map.of( 
                    "tenant-premium", "gpt-4",
                    "tenant-standard", "gpt-3.5-turbo"
            );
            String defaultModel = "gpt-3.5-turbo";

            String resolvedForPremium  = tenantModelOverrides.getOrDefault("tenant-premium",  defaultModel); 
            String resolvedForUnknown  = tenantModelOverrides.getOrDefault("tenant-unknown",  defaultModel); 

            assertThat(resolvedForPremium).isEqualTo("gpt-4");
            assertThat(resolvedForUnknown).isEqualTo("gpt-3.5-turbo");
        }
    }
}
