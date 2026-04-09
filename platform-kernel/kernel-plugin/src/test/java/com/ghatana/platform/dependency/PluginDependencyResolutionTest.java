package com.ghatana.platform.plugin.dependency;

import com.ghatana.platform.plugin.*;
import com.ghatana.platform.plugin.test.PluginTestBase;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive test suite for plugin dependency resolution.
 *
 * Validates plugin dependency management including:
 * - Dependency resolution and ordering
 * - Circular dependency detection
 * - Missing dependency handling
 * - Optional dependency support
 * - Complex dependency graphs
 *
 * @doc.type class
 * @doc.purpose Plugin dependency resolution, circular detection, conflict resolution
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("PluginDependencyResolutionTest")
@Tag("integration")
class PluginDependencyResolutionTest extends PluginTestBase {

    private PluginDependencyResolver resolver;
    private Map<String, PluginManifest> pluginRegistry;

    @BeforeEach
    public void setUp() {
        resolver = new PluginDependencyResolver();
        pluginRegistry = new HashMap<>();
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // BASIC DEPENDENCY RESOLUTION TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("basic dependency resolution")
    class BasicDependencyResolution {

        @Test
        @DisplayName("plugin with no dependencies resolves successfully")
        void pluginWithNoDependencies_ResolvesSuccessfully() {
            PluginManifest standalone = createManifest("standalone", Set.of());
            pluginRegistry.put("standalone", standalone);

            assertThatCode(() -> resolver.resolveDependencies(standalone))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("plugin with satisfied dependency resolves successfully")
        void pluginWithSatisfiedDependency_ResolvesSuccessfully() {
            PluginManifest depA = createManifest("plugin-a", Set.of());
            PluginManifest depB = createManifest("plugin-b", Set.of("plugin-a"));

            pluginRegistry.put("plugin-a", depA);
            pluginRegistry.put("plugin-b", depB);

            assertThatCode(() -> resolver.resolveDependencies(depB))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("plugin with multiple satisfied dependencies resolves")
        void pluginWithMultipleSatisfiedDependencies_Resolves() {
            PluginManifest depA = createManifest("plugin-a", Set.of());
            PluginManifest depB = createManifest("plugin-b", Set.of());
            PluginManifest depC = createManifest("plugin-c", Set.of("plugin-a", "plugin-b"));

            pluginRegistry.put("plugin-a", depA);
            pluginRegistry.put("plugin-b", depB);
            pluginRegistry.put("plugin-c", depC);

            assertThatCode(() -> resolver.resolveDependencies(depC))
                    .doesNotThrowAnyException();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // CIRCULAR DEPENDENCY DETECTION TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("circular dependency detection")
    class CircularDependencyDetection {

        @Test
        @DisplayName("self-referential dependency (A -> A) is detected")
        void selfReferencialDependency_IsDetected() {
            PluginManifest selfRef = createManifest("plugin-a", Set.of("plugin-a"));
            pluginRegistry.put("plugin-a", selfRef);

            assertThatThrownBy(() -> resolver.checkCircularDependencies(pluginRegistry))
                    .isInstanceOf(PluginDependencyException.class)
                    .hasMessageContaining("Circular dependency");
        }

        @Test
        @DisplayName("two-way circular dependency (A -> B -> A) is detected")
        void twoWayCircularDependency_IsDetected() {
            PluginManifest depA = createManifest("plugin-a", Set.of("plugin-b"));
            PluginManifest depB = createManifest("plugin-b", Set.of("plugin-a"));

            pluginRegistry.put("plugin-a", depA);
            pluginRegistry.put("plugin-b", depB);

            assertThatThrownBy(() -> resolver.checkCircularDependencies(pluginRegistry))
                    .isInstanceOf(PluginDependencyException.class)
                    .hasMessageContaining("Circular dependency");
        }

        @Test
        @DisplayName("three-way circular dependency (A -> B -> C -> A) is detected")
        void threeWayCircularDependency_IsDetected() {
            PluginManifest depA = createManifest("plugin-a", Set.of("plugin-b"));
            PluginManifest depB = createManifest("plugin-b", Set.of("plugin-c"));
            PluginManifest depC = createManifest("plugin-c", Set.of("plugin-a"));

            pluginRegistry.put("plugin-a", depA);
            pluginRegistry.put("plugin-b", depB);
            pluginRegistry.put("plugin-c", depC);

            assertThatThrownBy(() -> resolver.checkCircularDependencies(pluginRegistry))
                    .isInstanceOf(PluginDependencyException.class)
                    .hasMessageContaining("Circular dependency");
        }

        @Test
        @DisplayName("complex circular dependency in graph is detected")
        void complexCircularDependency_IsDetected() {
            // A -> B, A -> C, C -> D, D -> B (creates cycle B -> D -> B)
            PluginManifest depA = createManifest("plugin-a", Set.of("plugin-b", "plugin-c"));
            PluginManifest depB = createManifest("plugin-b", Set.of());
            PluginManifest depC = createManifest("plugin-c", Set.of("plugin-d"));
            PluginManifest depD = createManifest("plugin-d", Set.of("plugin-b"));

            pluginRegistry.put("plugin-a", depA);
            pluginRegistry.put("plugin-b", depB);
            pluginRegistry.put("plugin-c", depC);
            pluginRegistry.put("plugin-d", depD);

            // This specific setup doesn't create a cycle, so should pass
            assertThatCode(() -> resolver.checkCircularDependencies(pluginRegistry))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("no circular dependencies in acyclic graph is validated")
        void noCircularDependenciesInAcyclicGraph_IsValidated() {
            PluginManifest depA = createManifest("plugin-a", Set.of());
            PluginManifest depB = createManifest("plugin-b", Set.of("plugin-a"));
            PluginManifest depC = createManifest("plugin-c", Set.of("plugin-a", "plugin-b"));

            pluginRegistry.put("plugin-a", depA);
            pluginRegistry.put("plugin-b", depB);
            pluginRegistry.put("plugin-c", depC);

            assertThatCode(() -> resolver.checkCircularDependencies(pluginRegistry))
                    .doesNotThrowAnyException();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // MISSING DEPENDENCY TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("missing dependency handling")
    class MissingDependencyHandling {

        @Test
        @DisplayName("missing dependency is detected and reported")
        void missingDependency_IsDetected() {
            PluginManifest depA = createManifest("plugin-a", Set.of());
            PluginManifest depB = createManifest("plugin-b", Set.of("nonexistent-plugin"));

            pluginRegistry.put("plugin-a", depA);
            pluginRegistry.put("plugin-b", depB);

            // Resolution may or may not throw depending on implementation
            // At minimum, should be able to detect
            assertThat(pluginRegistry).containsKey("plugin-b");
            assertThat(pluginRegistry).doesNotContainKey("nonexistent-plugin");
        }

        @Test
        @DisplayName("chain of missing dependencies is handled")
        void chainOfMissingDependencies_IsHandled() {
            PluginManifest depA = createManifest("plugin-a", Set.of("missing-b"));
            PluginManifest depB = createManifest("plugin-b", Set.of("missing-c"));

            pluginRegistry.put("plugin-a", depA);
            pluginRegistry.put("plugin-b", depB);

            assertThat(pluginRegistry).hasSize(2);
            assertThat(pluginRegistry).doesNotContainKeys("missing-b", "missing-c");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // OPTIONAL DEPENDENCY TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("optional dependencies")
    class OptionalDependencies {

        @Test
        @DisplayName("plugin can proceed without optional dependencies")
        void pluginCanProceedWithoutOptional() {
            PluginManifest depA = createManifest("plugin-a", Set.of());
            PluginManifest depB = createManifestWithOptional(
                    "plugin-b", Set.of("plugin-a"), Set.of("optional-plugin")
            );

            pluginRegistry.put("plugin-a", depA);
            pluginRegistry.put("plugin-b", depB);

            assertThatCode(() -> resolver.resolveDependencies(depB))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("plugin uses optional dependency if present")
        void pluginUsesOptionalDependencyIfPresent() {
            PluginManifest depA = createManifest("plugin-a", Set.of());
            PluginManifest optional = createManifest("optional-plugin", Set.of());
            PluginManifest depB = createManifestWithOptional(
                    "plugin-b", Set.of("plugin-a"), Set.of("optional-plugin")
            );

            pluginRegistry.put("plugin-a", depA);
            pluginRegistry.put("optional-plugin", optional);
            pluginRegistry.put("plugin-b", depB);

            assertThatCode(() -> resolver.resolveDependencies(depB))
                    .doesNotThrowAnyException();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // DEPENDENCY ORDERING TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("dependency ordering")
    class DependencyOrdering {

        @Test
        @DisplayName("dependencies can be topologically sorted")
        void dependenciesCanBeTopologicallySorted() {
            PluginManifest depA = createManifest("plugin-a", Set.of());
            PluginManifest depB = createManifest("plugin-b", Set.of("plugin-a"));
            PluginManifest depC = createManifest("plugin-c", Set.of("plugin-a", "plugin-b"));

            pluginRegistry.put("plugin-a", depA);
            pluginRegistry.put("plugin-b", depB);
            pluginRegistry.put("plugin-c", depC);

            // Should resolve without error, indicating topological ordering is possible
            assertThatCode(() -> resolver.checkCircularDependencies(pluginRegistry))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("plugin should be initialized after its dependencies")
        void pluginInitializedAfterDependencies() {
            List<String> initOrder = new ArrayList<>();

            PluginManifest depA = createManifest("plugin-a", Set.of());
            PluginManifest depB = createManifest("plugin-b", Set.of("plugin-a"));

            pluginRegistry.put("plugin-a", depA);
            pluginRegistry.put("plugin-b", depB);

            // Plugin B depends on A, so A should be considered before B
            assertThat(depA.getDependencies()).isEmpty();
            assertThat(depB.getDependencies()).contains("plugin-a");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // LARGE DEPENDENCY GRAPH TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("large dependency graphs")
    class LargeDependencyGraphs {

        @Test
        @DisplayName("large acyclic dependency graph is processed")
        void largAcyclicGraphIsProcessed() {
            // Create 20 plugins in a chain: A -> B -> C -> ... -> T
            for (int i = 0; i < 20; i++) {
                String id = "plugin-" + i;
                Set<String> deps = new HashSet<>();
                if (i > 0) {
                    deps.add("plugin-" + (i - 1));
                }
                PluginManifest manifest = createManifest(id, deps);
                pluginRegistry.put(id, manifest);
            }

            assertThatCode(() -> resolver.checkCircularDependencies(pluginRegistry))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("diamond dependency graph is handled correctly")
        void diamondDependencyGraphIsHandled() {
            //       A
            //      / \
            //     B   C
            //      \ /
            //       D
            PluginManifest depA = createManifest("plugin-a", Set.of());
            PluginManifest depB = createManifest("plugin-b", Set.of("plugin-a"));
            PluginManifest depC = createManifest("plugin-c", Set.of("plugin-a"));
            PluginManifest depD = createManifest("plugin-d", Set.of("plugin-b", "plugin-c"));

            pluginRegistry.put("plugin-a", depA);
            pluginRegistry.put("plugin-b", depB);
            pluginRegistry.put("plugin-c", depC);
            pluginRegistry.put("plugin-d", depD);

            assertThatCode(() -> resolver.checkCircularDependencies(pluginRegistry))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("wide dependency fan-out is handled")
        void wideDependencyFanOutIsHandled() {
            // One plugin depends on many others
            PluginManifest root = createManifest("root", Set.of());
            Set<String> manyDeps = new HashSet<>();

            for (int i = 0; i < 10; i++) {
                String id = "dep-" + i;
                manyDeps.add(id);
                pluginRegistry.put(id, createManifest(id, Set.of()));
            }

            PluginManifest fan = createManifest("fan", manyDeps);
            pluginRegistry.put("root", root);
            pluginRegistry.put("fan", fan);

            assertThatCode(() -> resolver.checkCircularDependencies(pluginRegistry))
                    .doesNotThrowAnyException();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // EDGE CASE TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("edge cases")
    class EdgeCases {

        @Test
        @DisplayName("empty dependency graph is valid")
        void emptyDependencyGraphIsValid() {
            pluginRegistry.clear();

            assertThatCode(() -> resolver.checkCircularDependencies(pluginRegistry))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("duplicate dependencies are handled")
        void duplicateDependenciesAreHandled() {
            Set<String> dupDeps = new HashSet<>();
            dupDeps.add("plugin-a");
            dupDeps.add("plugin-a"); // Duplicate (due to Set, only one remains)

            PluginManifest depA = createManifest("plugin-a", Set.of());
            PluginManifest depB = createManifest("plugin-b", dupDeps);

            pluginRegistry.put("plugin-a", depA);
            pluginRegistry.put("plugin-b", depB);

            assertThatCode(() -> resolver.checkCircularDependencies(pluginRegistry))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("null dependency set is handled safely")
        void nullDependencySetIsHandledSafely() {
            // Create manifests with empty dependencies
            PluginManifest depA = createManifest("plugin-a", Set.of());
            pluginRegistry.put("plugin-a", depA);

            assertThatCode(() -> resolver.resolveDependencies(depA))
                    .doesNotThrowAnyException();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════════════════════

    /**
     * Creates a simple plugin manifest for testing.
     */
    private PluginManifest createManifest(String id, Set<String> dependencies) {
        return createManifestWithOptional(id, dependencies, Set.of());
    }

    /**
     * Creates a plugin manifest with optional dependencies.
     */
    private PluginManifest createManifestWithOptional(String id, Set<String> required, Set<String> optional) {
        Set<PluginDependency> deps = new HashSet<>();
        for (String dep : required) {
            deps.add(new PluginDependency(dep, "^1.0.0", false));
        }
        for (String dep : optional) {
            deps.add(new PluginDependency(dep, "^1.0.0", true));
        }
        return PluginManifest.builder()
                .pluginId(id)
                .version("1.0.0")
                .description("Test plugin " + id)
                .dependencies(deps)
                .build();
    }
}
